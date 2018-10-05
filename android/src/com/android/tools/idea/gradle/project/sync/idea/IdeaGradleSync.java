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
package com.android.tools.idea.gradle.project.sync.idea;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.ProjectBuildFileChecksums;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.sync.GradleSync;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.idea.data.DataNodeCaches;
import com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup;
import com.intellij.build.DefaultBuildDescriptor;
import com.intellij.build.SyncViewManager;
import com.intellij.build.events.impl.StartBuildEventImpl;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.projectImport.ProjectOpenProcessor;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.GradleProjectOpenProcessor;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.project.sync.idea.ProjectFinder.registerAsNewProject;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getExternalRootProjectPath;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.toCanonicalPath;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.EXTERNAL_SYSTEM_TASK_ID_KEY;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.refreshProject;
import static java.lang.System.currentTimeMillis;

public class IdeaGradleSync implements GradleSync {
  private static final boolean SYNC_WITH_CACHED_MODEL_ONLY =
    SystemProperties.getBooleanProperty("studio.sync.with.cached.model.only", false);

  @NotNull private final Project myProject;
  @NotNull private final GradleProjectInfo myProjectInfo;

  public IdeaGradleSync(@NotNull Project project) {
    this(project, GradleProjectInfo.getInstance(project));
  }

  private IdeaGradleSync(@NotNull Project project, @NotNull GradleProjectInfo projectInfo) {
    myProject = project;
    myProjectInfo = projectInfo;
  }

  @Override
  public void sync(@NotNull GradleSyncInvoker.Request request, @Nullable GradleSyncListener listener) {
    if (myProjectInfo.isNewProject()) {
      registerAsNewProject(myProject);
    }

    if (SYNC_WITH_CACHED_MODEL_ONLY || request.useCachedGradleModels) {
      ProjectBuildFileChecksums buildFileChecksums = ProjectBuildFileChecksums.findFor((myProject));
      if (buildFileChecksums != null && buildFileChecksums.canUseCachedData()) {
        DataNodeCaches dataNodeCaches = DataNodeCaches.getInstance(myProject);
        DataNode<ProjectData> cache = dataNodeCaches.getCachedProjectData();
        if (cache != null && !dataNodeCaches.isCacheMissingModels(cache)) {
          PostSyncProjectSetup.Request setupRequest = new PostSyncProjectSetup.Request();
          setupRequest.usingCachedGradleModels = true;
          setupRequest.generateSourcesAfterSync = false;
          setupRequest.lastSyncTimestamp = buildFileChecksums.getLastGradleSyncTimestamp();

          setSkipAndroidPluginUpgrade(request, setupRequest);

          // Create a task for the Build View, this is needed to hang any found issue to it.
          ExternalSystemTaskId taskId = ExternalSystemTaskId.create(GRADLE_SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, myProject);
          String workingDir = toCanonicalPath(getBaseDirPath(myProject).getPath());
          DefaultBuildDescriptor buildDescriptor = new DefaultBuildDescriptor(taskId, "Project setup", workingDir, currentTimeMillis());
          SyncViewManager syncManager = ServiceManager.getService(myProject, SyncViewManager.class);
          syncManager.onEvent(new StartBuildEventImpl(buildDescriptor, "reading from cache..."));
          myProject.putUserData(EXTERNAL_SYSTEM_TASK_ID_KEY, taskId);
          ProjectSetUpTask setUpTask = new ProjectSetUpTask(myProject, setupRequest, listener, true /* sync skipped */);
          setUpTask.onSuccess(cache);
          return;
        }
      }
    }

    PostSyncProjectSetup.Request setupRequest = new PostSyncProjectSetup.Request();
    setupRequest.generateSourcesAfterSync = request.generateSourcesOnSuccess;
    setupRequest.cleanProjectAfterSync = request.cleanProject;

    setSkipAndroidPluginUpgrade(request, setupRequest);

    // the sync should be aware of multiple linked gradle project with a single IDE project
    // and a linked gradle project can be located not in the IDE Project.baseDir
    Set<String> androidProjectCandidatesPaths = new LinkedHashSet<>();
    if (myProjectInfo.isImportedProject()) {
      GradleSettings gradleSettings = GradleSettings.getInstance(myProject);
      Collection<GradleProjectSettings> projectsSettings = gradleSettings.getLinkedProjectsSettings();
      if (projectsSettings.isEmpty()) {
        GradleProjectOpenProcessor projectOpenProcessor = ProjectOpenProcessor.EXTENSION_POINT_NAME.findExtensionOrFail(GradleProjectOpenProcessor.class);
        if (myProject.getBasePath() != null && projectOpenProcessor.canOpenProject(myProject.getBaseDir())) {
          GradleProjectSettings projectSettings = new GradleProjectSettings();
          String externalProjectPath = toCanonicalPath(myProject.getBasePath());
          projectSettings.setExternalProjectPath(externalProjectPath);
          gradleSettings.setLinkedProjectsSettings(Collections.singletonList(projectSettings));
          androidProjectCandidatesPaths.add(externalProjectPath);
        }
      }
      else if (projectsSettings.size() == 1) {
        androidProjectCandidatesPaths.add(projectsSettings.iterator().next().getExternalProjectPath());
      }
    }
    else {
      for (Module module : ProjectFacetManager.getInstance(myProject).getModulesWithFacet(GradleFacet.getFacetTypeId())) {
        String projectPath = getExternalRootProjectPath(module);
        if (projectPath != null) {
          androidProjectCandidatesPaths.add(projectPath);
        }
      }
    }
    if (androidProjectCandidatesPaths.isEmpty()) {
      // try to discover the project in the IDE project base dir.
      String externalProjectPath = toCanonicalPath(getBaseDirPath(myProject).getPath());
      if (new File(externalProjectPath, SdkConstants.FN_BUILD_GRADLE).isFile() ||
          new File(externalProjectPath, SdkConstants.FN_SETTINGS_GRADLE).isFile()) {
        androidProjectCandidatesPaths.add(externalProjectPath);
      }
    }

    if (androidProjectCandidatesPaths.isEmpty()) {
      if (listener != null) {
        listener.syncSkipped(myProject);
      }
      GradleSyncState.getInstance(myProject).syncSkipped(currentTimeMillis());
      return;
    }

    for (String rootPath : androidProjectCandidatesPaths) {
      ProjectSetUpTask setUpTask = new ProjectSetUpTask(myProject, setupRequest, listener, false);
      ProgressExecutionMode executionMode = request.getProgressExecutionMode();
      refreshProject(myProject, GRADLE_SYSTEM_ID, rootPath, setUpTask, false /* resolve dependencies */,
                     executionMode, true /* always report import errors */);
    }
  }

  private static void setSkipAndroidPluginUpgrade(@NotNull GradleSyncInvoker.Request syncRequest,
                                                  @NotNull PostSyncProjectSetup.Request setupRequest) {
    if (ApplicationManager.getApplication().isUnitTestMode() && syncRequest.skipAndroidPluginUpgrade) {
      setupRequest.skipAndroidPluginUpgrade = true;
    }
  }
}
