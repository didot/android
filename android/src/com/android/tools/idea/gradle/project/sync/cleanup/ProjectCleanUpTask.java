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
package com.android.tools.idea.gradle.project.sync.cleanup;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.startup.AndroidStudioInitializer.isAndroidStudio;

abstract class ProjectCleanUpTask {
  @NotNull private final Project myProject;

  ProjectCleanUpTask(@NotNull Project project) {
    myProject = project;
  }

  public void execute() {
    if (!isAndroidStudio()) {
      return;
    }
    try {
      doExecute();
    }
    catch (Throwable e) {
      Logger.getInstance(getClass()).info("Failed to execute " + getClass().getSimpleName(), e);
    }
  }

  abstract void doExecute();

  @NotNull
  protected Project getProject() {
    return myProject;
  }
}
