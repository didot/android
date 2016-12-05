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

import com.android.tools.idea.gradle.project.GradleProjectSyncData;
import com.android.tools.idea.gradle.project.sync.GradleSync;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.idea.data.DataNodeCaches;
import com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.project.Project;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.refreshProject;

public class IdeaGradleSync implements GradleSync {
  private static final boolean SYNC_WITH_CACHED_MODEL_ONLY =
    SystemProperties.getBooleanProperty("studio.sync.with.cached.model.only", false);

  @Override
  public void sync(@NotNull Project project,
                   @NotNull GradleSyncInvoker.Request request,
                   @Nullable GradleSyncListener listener) {
    // Prevent IDEA from syncing with Gradle. We want to have full control of syncing.
    project.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, true);
    boolean newProject = request.isNewProject();

    if (SYNC_WITH_CACHED_MODEL_ONLY || request.isUseCachedGradleModels()) {
      GradleProjectSyncData syncData = GradleProjectSyncData.getInstance((project));
      if (syncData != null && syncData.canUseCachedProjectData()) {
        DataNodeCaches dataNodeCaches = DataNodeCaches.getInstance(project);
        DataNode<ProjectData> cache = dataNodeCaches.getCachedProjectData();
        if (cache != null && !dataNodeCaches.isCacheMissingModels(cache)) {
          PostSyncProjectSetup.Request setupRequest = new PostSyncProjectSetup.Request();

          // @formatter:off
          setupRequest.setUsingCachedGradleModels(true)
                      .setGenerateSourcesAfterSync(false)
                      .setLastSyncTimestamp(syncData.getLastGradleSyncTimestamp());
          // @formatter:on

          ProjectSetUpTask setUpTask = new ProjectSetUpTask(project, setupRequest, listener, newProject, true /* select modules */, true);
          setUpTask.onSuccess(cache);
          return;
        }
      }
    }

    PostSyncProjectSetup.Request setupRequest = new PostSyncProjectSetup.Request();

    // @formatter:off
    setupRequest.setGenerateSourcesAfterSync(request.isGenerateSourcesOnSuccess())
                .setCleanProjectAfterSync(request.isCleanProject());
    // @formatter:on

    String externalProjectPath = getBaseDirPath(project).getPath();

    ProjectSetUpTask setUpTask = new ProjectSetUpTask(project, setupRequest, listener, newProject,
                                                      newProject /* select modules if it's a new project */, false);
    ProgressExecutionMode executionMode = request.getProgressExecutionMode();
    refreshProject(project, GRADLE_SYSTEM_ID, externalProjectPath, setUpTask, false /* resolve dependencies */,
                   executionMode, true /* always report import errors */);
  }
}
