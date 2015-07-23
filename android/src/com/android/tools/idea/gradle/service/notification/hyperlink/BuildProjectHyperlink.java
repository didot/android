/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.notification.hyperlink;

import com.android.tools.idea.gradle.invoker.GradleInvoker;
import com.android.tools.idea.gradle.util.Projects;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class BuildProjectHyperlink extends NotificationHyperlink {
  public BuildProjectHyperlink() {
    super("buildProject", "Build Project");
  }

  @Override
  protected void execute(@NotNull Project project) {
    if (Projects.requiresAndroidModel(project) && Projects.isDirectGradleInvocationEnabled(project)) {
      ModuleManager moduleManager = ModuleManager.getInstance(project);
      GradleInvoker.getInstance(project).compileJava(moduleManager.getModules(), GradleInvoker.TestCompileType.NONE);
      return;
    }
    CompilerManager.getInstance(project).make(null);
  }
}
