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
package com.android.tools.idea.gradle.startup;

import com.android.tools.idea.gradle.GradleImportNotificationListener;
import com.android.tools.idea.gradle.util.Projects;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/**
 * Sets up any Gradle-related state when the IDE starts.
 */
public class GradleStartupActivity implements StartupActivity, DumbAware {
  @Override
  public void runActivity(@NotNull Project project) {
    if (Projects.isGradleProject(project)) {
      GradleImportNotificationListener.attachToManager();
      Projects.setBuildAction(project, Projects.BuildAction.COMPILE);
    }
  }
}
