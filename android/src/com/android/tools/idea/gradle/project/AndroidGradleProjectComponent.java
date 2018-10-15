/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.build.GradleBuildContext;
import com.android.tools.idea.gradle.project.build.JpsBuildContext;
import com.android.tools.idea.gradle.project.build.PostProjectBuildTasksExecutor;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.project.AndroidProjectBuildNotifications;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.execution.RunConfigurationProducerService;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.test.runner.AllInPackageGradleConfigurationProducer;
import org.jetbrains.plugins.gradle.execution.test.runner.TestClassGradleConfigurationProducer;
import org.jetbrains.plugins.gradle.execution.test.runner.TestMethodGradleConfigurationProducer;

import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.gradle.util.GradleProjects.canImportAsGradleProject;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_LOADED;
import static com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT;

public class AndroidGradleProjectComponent implements ProjectComponent {
  @NotNull private final Project myProject;
  @NotNull private final GradleProjectInfo myGradleProjectInfo;
  @NotNull private final AndroidProjectInfo myAndroidProjectInfo;
  @NotNull private final GradleSyncInvoker myGradleSyncInvoker;
  @NotNull private final SupportedModuleChecker mySupportedModuleChecker;
  @NotNull private final IdeInfo myIdeInfo;
  @NotNull private final LegacyAndroidProjects myLegacyAndroidProjects;

  @Nullable private Disposable myDisposable;

  @NotNull
  public static AndroidGradleProjectComponent getInstance(@NotNull Project project) {
    AndroidGradleProjectComponent component = project.getComponent(AndroidGradleProjectComponent.class);
    assert component != null;
    return component;
  }

  @SuppressWarnings("unused") // Invoked by IDEA
  public AndroidGradleProjectComponent(@NotNull Project project,
                                       @NotNull GradleProjectInfo gradleProjectInfo,
                                       @NotNull AndroidProjectInfo androidProjectInfo,
                                       @NotNull GradleSyncInvoker gradleSyncInvoker,
                                       @NotNull GradleBuildInvoker gradleBuildInvoker,
                                       @NotNull CompilerManager compilerManager,
                                       @NotNull SupportedModuleChecker supportedModuleChecker,
                                       @NotNull IdeInfo ideInfo) {
    this(project, gradleProjectInfo, androidProjectInfo, gradleSyncInvoker, gradleBuildInvoker, compilerManager,
         supportedModuleChecker, ideInfo, new LegacyAndroidProjects(project));
  }

  @VisibleForTesting
  public AndroidGradleProjectComponent(@NotNull Project project,
                                       @NotNull GradleProjectInfo gradleProjectInfo,
                                       @NotNull AndroidProjectInfo androidProjectInfo,
                                       @NotNull GradleSyncInvoker gradleSyncInvoker,
                                       @NotNull GradleBuildInvoker gradleBuildInvoker,
                                       @NotNull CompilerManager compilerManager,
                                       @NotNull SupportedModuleChecker supportedModuleChecker,
                                       @NotNull IdeInfo ideInfo,
                                       @NotNull LegacyAndroidProjects legacyAndroidProjects) {
    myProject = project;
    myGradleProjectInfo = gradleProjectInfo;
    myAndroidProjectInfo = androidProjectInfo;
    myGradleSyncInvoker = gradleSyncInvoker;
    mySupportedModuleChecker = supportedModuleChecker;
    myIdeInfo = ideInfo;
    myLegacyAndroidProjects = legacyAndroidProjects;

    // Register a task that gets notified when a Gradle-based Android project is compiled via JPS.
    compilerManager.addAfterTask(context -> {
      if (myGradleProjectInfo.isBuildWithGradle()) {
        PostProjectBuildTasksExecutor.getInstance(project).onBuildCompletion(context);

        JpsBuildContext newContext = new JpsBuildContext(context);
        AndroidProjectBuildNotifications.getInstance(myProject).notifyBuildComplete(newContext);
      }
      return true;
    });

    // Register a task that gets notified when a Gradle-based Android project is compiled via direct Gradle invocation.
    gradleBuildInvoker.add(result -> {
      if (myProject.isDisposed()) return;
      PostProjectBuildTasksExecutor.getInstance(myProject).onBuildCompletion(result);
      GradleBuildContext newContext = new GradleBuildContext(result);
      AndroidProjectBuildNotifications.getInstance(myProject).notifyBuildComplete(newContext);

      // Force VFS refresh required by any of the modules.
      if (isVfsRefreshAfterBuildRequired(myProject)) {
        ApplicationManager.getApplication().invokeLater(() -> {
          FileDocumentManager.getInstance().saveAllDocuments();
          SaveAndSyncHandler.getInstance().refreshOpenFiles();
          VirtualFileManager.getInstance().refreshWithoutFileWatcher(true /* asynchronously */);
        });
      }
    });
  }

  /**
   * @return {@code true} if any of the modules require VFS refresh after build.
   */
  private static boolean isVfsRefreshAfterBuildRequired(@NotNull Project project) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      AndroidModuleModel androidModuleModel = AndroidModuleModel.get(module);
      if (androidModuleModel != null && androidModuleModel.getFeatures().isVfsRefreshAfterBuildRequired()) {
        return true;
      }
    }
    return false;
  }

  /**
   * This method is called when a project is created and when it is opened.
   */
  @Override
  public void projectOpened() {
    boolean checkSupported = true;
    GradleSyncState syncState = GradleSyncState.getInstance(myProject);
    if (syncState.isSyncInProgress()) {
      // when opening a new project, the UI was not updated when sync started. Updating UI ("Build Variants" tool window, "Sync" toolbar
      // button and editor notifications.
      syncState.notifyStateChanged();
    }

    if (myIdeInfo.isAndroidStudio() && myAndroidProjectInfo.isLegacyIdeaAndroidProject() && !myAndroidProjectInfo.isApkProject()) {
      myLegacyAndroidProjects.trackProject();
      if (!myGradleProjectInfo.isBuildWithGradle()) {
        // Suggest that Android Studio users use Gradle instead of IDEA project builder.
        myLegacyAndroidProjects.showMigrateToGradleWarning();
        return;
      }
    }

    if (myGradleProjectInfo.isBuildWithGradle()) {
      configureGradleProject();
      if (myAndroidProjectInfo.isLegacyIdeaAndroidProject() || !myGradleProjectInfo.hasGradleFacets()) {
        // Request sync since it was not done when importing
        myGradleSyncInvoker.requestProjectSyncAndSourceGeneration(myProject, TRIGGER_PROJECT_LOADED);
        checkSupported = false;
      }
    }
    else if (myIdeInfo.isAndroidStudio() && myProject.getBaseDir() != null && canImportAsGradleProject(myProject.getBaseDir())) {
      myGradleSyncInvoker.requestProjectSyncAndSourceGeneration(myProject, TRIGGER_PROJECT_LOADED);
      checkSupported = false;
    }
    // Do not check for supported modules if sync was requested, this will be done once sync is successful
    if (checkSupported) {
      mySupportedModuleChecker.checkForSupportedModules(myProject);
    }
  }

  public void configureGradleProject() {
    if (myDisposable != null) {
      return;
    }
    myDisposable = Disposer.newDisposable();

    // Prevent IDEA from refreshing project. We will do it ourselves in AndroidGradleProjectStartupActivity.
    if (IdeInfo.getInstance().isAndroidStudio()) {
      myProject.putUserData(NEWLY_IMPORTED_PROJECT, Boolean.TRUE);
    }

    List<Class<? extends RunConfigurationProducer<?>>> runConfigurationProducerTypes = new ArrayList<>();
    runConfigurationProducerTypes.add(AllInPackageGradleConfigurationProducer.class);
    runConfigurationProducerTypes.add(TestClassGradleConfigurationProducer.class);
    runConfigurationProducerTypes.add(TestMethodGradleConfigurationProducer.class);

    RunConfigurationProducerService runConfigurationProducerManager = RunConfigurationProducerService.getInstance(myProject);
    if (myIdeInfo.isAndroidStudio()) {
      // Make sure the gradle test configurations are ignored in this project. This will modify .idea/runConfigurations.xml
      for (Class<? extends RunConfigurationProducer<?>> type : runConfigurationProducerTypes) {
        runConfigurationProducerManager.getState().ignoredProducers.add(type.getName());
      }
    }
    else {
      // Make sure the gradle test configurations are not ignored in this project, since they already work in Android gradle projects. This
      // will modify .idea/runConfigurations.xml
      for (Class<? extends RunConfigurationProducer<?>> type : runConfigurationProducerTypes) {
        runConfigurationProducerManager.getState().ignoredProducers.remove(type.getName());
      }
    }
  }

  @Override
  public void projectClosed() {
    if (myDisposable != null) {
      Disposer.dispose(myDisposable);
    }
  }
}
