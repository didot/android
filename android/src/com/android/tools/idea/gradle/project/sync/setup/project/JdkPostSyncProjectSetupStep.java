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
package com.android.tools.idea.gradle.project.sync.setup.project;

import com.android.tools.idea.gradle.compiler.PostProjectBuildTasksExecutor;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessages;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.Jdks;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.project.sync.messages.MessageType.ERROR;
import static com.android.tools.idea.gradle.project.sync.messages.SyncMessage.DEFAULT_GROUP;
import static org.jetbrains.android.util.AndroidUtils.isAndroidStudio;
import static com.intellij.pom.java.LanguageLevel.JDK_1_8;

public class JdkPostSyncProjectSetupStep extends PostSyncProjectSetupStep {
  @NotNull private final IdeSdks myIdeSdks;
  @NotNull private final Jdks myJdks;

  @SuppressWarnings("unused") // Invoked by IDEA.
  public JdkPostSyncProjectSetupStep() {
    this(IdeSdks.getInstance(), Jdks.getInstance());
  }

  @VisibleForTesting
  JdkPostSyncProjectSetupStep(@NotNull IdeSdks ideSdks, @NotNull Jdks jdks) {
    myIdeSdks = ideSdks;
    myJdks = jdks;
  }

  @Override
  public void setUpProject(@NotNull Project project,
                           @NotNull IdeModifiableModelsProvider ideModelsProvider,
                           @Nullable ProgressIndicator indicator) {
    doSetUpProject(project, isAndroidStudio());
  }

  @VisibleForTesting
  void doSetUpProject(@NotNull Project project, boolean androidStudio) {
    LanguageLevel javaLangVersion = JDK_1_8;
    Sdk projectJdk = ProjectRootManager.getInstance(project).getProjectSdk();
    Sdk ideJdk = null;

    if (androidStudio) {
      ideJdk = myIdeSdks.getJdk();
    }
    else if (projectJdk == null || !myJdks.isApplicableJdk(projectJdk, javaLangVersion)) {
      ideJdk = myJdks.chooseOrCreateJavaSdk(javaLangVersion);
    }

    if (ideJdk == null) {
      SyncMessage message = new SyncMessage(DEFAULT_GROUP, ERROR, "Unable to find a JDK");
      if (androidStudio) {
        message.add(myJdks.getWrongJdkQuickFixes(project));
      }

      SyncMessages.getInstance(project).report(message);
      GradleSyncState.getInstance(project).getSummary().setSyncErrorsFound(true);
      return;
    }

    String homePath = ideJdk.getHomePath();
    if (homePath != null) {
      myJdks.setJdk(project, ideJdk);
      PostProjectBuildTasksExecutor.getInstance(project).updateJavaLangLevelAfterBuild();
    }
  }
}
