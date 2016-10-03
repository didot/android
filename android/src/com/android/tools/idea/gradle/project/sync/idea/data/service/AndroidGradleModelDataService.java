/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.project.sync.idea.data.service;

import com.android.builder.model.AndroidProject;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.android.AndroidModel;
import com.android.tools.idea.gradle.project.AndroidGradleNotification;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessages;
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.project.PostSyncProjectSetupStep;
import com.android.tools.idea.gradle.service.notification.hyperlink.FixAndroidGradlePluginVersionHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.OpenUrlHyperlink;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.RunResult;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.ANDROID_MODEL;
import static com.android.tools.idea.gradle.project.sync.messages.GroupNames.EXTRA_GENERATED_SOURCES;
import static com.android.tools.idea.gradle.project.sync.messages.GroupNames.UNHANDLED_SYNC_ISSUE_TYPE;
import static com.android.tools.idea.gradle.project.sync.messages.MessageType.INFO;
import static com.android.tools.idea.gradle.project.sync.messages.MessageType.WARNING;
import static com.android.tools.idea.gradle.util.GradleUtil.hasLayoutRenderingIssue;
import static java.util.Collections.sort;

/**
 * Service that sets an Android SDK and facets to the modules of a project that has been imported from an Android-Gradle project.
 */
public class AndroidGradleModelDataService extends AbstractProjectDataService<AndroidGradleModel, Void> {
  private static final Logger LOG = Logger.getInstance(AndroidGradleModelDataService.class);

  private final List<AndroidModuleSetupStep> mySetupSteps;

  // This constructor is called by the IDE. See this module's plugin.xml file, implementation of extension 'externalProjectDataService'.
  @SuppressWarnings("unused")
  public AndroidGradleModelDataService() {
    this(ImmutableList.copyOf(AndroidModuleSetupStep.getExtensions()));
  }

  @VisibleForTesting
  AndroidGradleModelDataService(@NotNull List<AndroidModuleSetupStep> setupSteps) {
    mySetupSteps = setupSteps;
  }

  @NotNull
  @Override
  public Key<AndroidGradleModel> getTargetDataKey() {
    return ANDROID_MODEL;
  }

  /**
   * Sets an Android SDK and facets to the modules of a project that has been imported from an Android-Gradle project.
   *
   * @param toImport contains the Android-Gradle project.
   * @param project  IDEA project to configure.
   */
  @Override
  public void importData(@NotNull Collection<DataNode<AndroidGradleModel>> toImport,
                         @Nullable ProjectData projectData,
                         @NotNull Project project,
                         @NotNull IdeModifiableModelsProvider modelsProvider) {
    if (!toImport.isEmpty()) {
      try {
        doImport(toImport, project, modelsProvider);
      }
      catch (Throwable e) {
        LOG.info(String.format("Failed to set up Android modules in project '%1$s'", project.getName()), e);
        String msg = e.getMessage();
        if (msg == null) {
          msg = e.getClass().getCanonicalName();
        }
        GradleSyncState.getInstance(project).syncFailed(msg);
      }
    }
  }

  private void doImport(@NotNull Collection<DataNode<AndroidGradleModel>> toImport,
                        @NotNull Project project,
                        @NotNull IdeModifiableModelsProvider modelsProvider) throws Throwable {
    RunResult result = new WriteCommandAction.Simple(project) {
      @Override
      protected void run() throws Throwable {
        SyncMessages messages = SyncMessages.getInstance(project);
        boolean hasExtraGeneratedFolders = false;

        Map<String, AndroidGradleModel> androidModelsByModuleName = indexByModuleName(toImport);

        Charset ideEncoding = EncodingProjectManager.getInstance(project).getDefaultCharset();
        GradleVersion oneDotTwoModelVersion = new GradleVersion(1, 2, 0);

        String nonMatchingModelEncodingFound = null;
        String modelVersionWithLayoutRenderingIssue = null;

        // Module name, build
        List<String> modulesUsingBuildTools23rc1 = Lists.newArrayList();

        for (Module module : modelsProvider.getModules()) {
          AndroidGradleModel androidModel = androidModelsByModuleName.get(module.getName());

          setUpModule(module, modelsProvider, androidModel);
          if (androidModel != null) {
            AndroidProject androidProject = androidModel.getAndroidProject();

            checkBuildToolsCompatibility(module, androidProject, modulesUsingBuildTools23rc1);

            // Verify that if Gradle is 2.4 (or newer,) the model is at least version 1.2.0.
            if (modelVersionWithLayoutRenderingIssue == null && hasLayoutRenderingIssue(androidProject)) {
              modelVersionWithLayoutRenderingIssue = androidProject.getModelVersion();
            }

            GradleVersion modelVersion = GradleVersion.parse(androidProject.getModelVersion());
            boolean isModelVersionOneDotTwoOrNewer = modelVersion.compareIgnoringQualifiers(oneDotTwoModelVersion) >= 0;

            // Verify that the encoding in the model is the same as the encoding in the IDE's project settings.
            Charset modelEncoding = null;
            if (isModelVersionOneDotTwoOrNewer) {
              try {
                modelEncoding = Charset.forName(androidProject.getJavaCompileOptions().getEncoding());
              }
              catch (UnsupportedCharsetException ignore) {
                // It's not going to happen.
              }
            }
            if (nonMatchingModelEncodingFound == null && modelEncoding != null && ideEncoding.compareTo(modelEncoding) != 0) {
              nonMatchingModelEncodingFound = modelEncoding.displayName();
            }

            // Warn users that there are generated source folders at the wrong location.
            File[] sourceFolders = androidModel.getExtraGeneratedSourceFolders();
            if (sourceFolders.length > 0) {
              hasExtraGeneratedFolders = true;
            }
            for (File folder : sourceFolders) {
              // Have to add a word before the path, otherwise IDEA won't show it.
              String[] text = {"Folder " + folder.getPath()};
              messages.report(new SyncMessage(EXTRA_GENERATED_SOURCES, WARNING, text));
            }
          }
        }

        if (!modulesUsingBuildTools23rc1.isEmpty()) {
          reportBuildTools23rc1Usage(modulesUsingBuildTools23rc1, project);
        }

        if (nonMatchingModelEncodingFound != null) {
          setIdeEncodingAndAddEncodingMismatchMessage(nonMatchingModelEncodingFound, project);
        }

        if (modelVersionWithLayoutRenderingIssue != null) {
          addLayoutRenderingIssueMessage(modelVersionWithLayoutRenderingIssue, project);
        }

        if (hasExtraGeneratedFolders) {
          messages.report(new SyncMessage(EXTRA_GENERATED_SOURCES, INFO, "3rd-party Gradle plug-ins may be the cause"));
        }

        for (PostSyncProjectSetupStep projectSetupStep : PostSyncProjectSetupStep.getExtensions()) {
          projectSetupStep.setUpProject(project, modelsProvider, null);
        }
      }
    }.execute();
    Throwable error = result.getThrowable();
    if (error != null) {
      throw error;
    }
  }

  // Build Tools 23 only works with Android plugin 1.3 or newer. Verify that the project is using compatible Build Tools/Android plugin
  // versions.
  private static void checkBuildToolsCompatibility(@NotNull Module module,
                                                   @NotNull AndroidProject project,
                                                   @NotNull List<String> moduleNames) {
    if (isOneDotThreeOrLater(project)) {
      return;
    }

    GradleBuildModel buildModel = GradleBuildModel.get(module);
    if (buildModel == null) {
      return;
    }

    AndroidModel android = buildModel.android();
    if (android == null) {
      return;
    }

    if ("23.0.0 rc1".equals(android.buildToolsVersion())) {
      moduleNames.add(module.getName());
    }
  }

  private static boolean isOneDotThreeOrLater(@NotNull AndroidProject project) {
    String modelVersion = project.getModelVersion();
    // getApiVersion doesn't work prior to 1.2, and API level must be at least 3
    return !(modelVersion.startsWith("1.0") || modelVersion.startsWith("1.1")) && project.getApiVersion() >= 3;
  }

  private static void reportBuildTools23rc1Usage(@NotNull List<String> moduleNames, @NotNull Project project) {
    if (!moduleNames.isEmpty()) {
      sort(moduleNames);

      StringBuilder msg = new StringBuilder();
      msg.append("Build Tools 23.0.0 rc1 is <b>deprecated</b>.<br>\n")
        .append("Please update these modules to use Build Tools 23.0.0 rc2 instead:");
      for (String moduleName : moduleNames) {
        msg.append("<br>\n * ").append(moduleName);
      }
      msg.append("<br>\n<br>\nOtherwise the project won't build. ");

      AndroidGradleNotification notification = AndroidGradleNotification.getInstance(project);
      notification.showBalloon("Android Build Tools", msg.toString(), NotificationType.ERROR);
    }
  }

  private static void setIdeEncodingAndAddEncodingMismatchMessage(@NotNull String newEncoding, @NotNull Project project) {
    EncodingProjectManager encodings = EncodingProjectManager.getInstance(project);
    String[] text = {String.format("The project encoding (%1$s) has been reset to the encoding specified in the Gradle build files (%2$s).",
                                   encodings.getDefaultCharset().displayName(), newEncoding),
      "Mismatching encodings can lead to serious bugs."};
    encodings.setDefaultCharsetName(newEncoding);

    SyncMessage message = new SyncMessage(UNHANDLED_SYNC_ISSUE_TYPE, INFO, text);
    message.add(new OpenUrlHyperlink("http://tools.android.com/knownissues/encoding", "More Info..."));

    SyncMessages.getInstance(project).report(message);
  }

  private static void addLayoutRenderingIssueMessage(String modelVersion, @NotNull Project project) {
    // See https://code.google.com/p/android/issues/detail?id=170841
    String text = String.format("Using an obsolete version of the Gradle plugin (%1$s);", modelVersion);
    text += " this can lead to layouts not rendering correctly.";

    SyncMessage message = new SyncMessage(UNHANDLED_SYNC_ISSUE_TYPE, WARNING, text);
    message.add(Arrays.asList(new FixAndroidGradlePluginVersionHyperlink(),
                              new OpenUrlHyperlink("https://code.google.com/p/android/issues/detail?id=170841", "More Info...")));

    SyncMessages.getInstance(project).report(message);
  }

  @NotNull
  private static Map<String, AndroidGradleModel> indexByModuleName(@NotNull Collection<DataNode<AndroidGradleModel>> dataNodes) {
    Map<String, AndroidGradleModel> index = Maps.newHashMap();
    for (DataNode<AndroidGradleModel> d : dataNodes) {
      AndroidGradleModel androidModel = d.getData();
      index.put(androidModel.getModuleName(), androidModel);
    }
    return index;
  }

  private void setUpModule(@NotNull Module module,
                           @NotNull IdeModifiableModelsProvider modelsProvider,
                           @Nullable AndroidGradleModel androidModel) {
    for (AndroidModuleSetupStep setupStep : mySetupSteps) {
      setupStep.setUpModule(module, modelsProvider, androidModel, null, null);
    }
  }
}
