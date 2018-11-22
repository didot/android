/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.project.AndroidNotification;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.intellij.notification.NotificationType.ERROR;
import static com.intellij.openapi.externalSystem.util.ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY;
import static com.intellij.openapi.util.text.StringUtil.join;

public class SupportedModuleChecker {
  @NotNull
  public static SupportedModuleChecker getInstance() {
    return ServiceManager.getService(SupportedModuleChecker.class);
  }

  /**
   * Verifies that the project, if it is an Android Gradle project, does not have any modules that are not known by Gradle. For example,
   * when adding a plain IDEA Java module.
   * Do not call this method from {@link ModuleListener#moduleAdded(Project, Module)} because the settings that this method look for are
   * not present when importing a valid Gradle-aware module, resulting in false positives.
   */
  public void checkForSupportedModules(@NotNull Project project) {
    Module[] modules = ModuleManager.getInstance(project).getModules();
    if (modules.length == 0 || !GradleProjectInfo.getInstance(project).isBuildWithGradle()) {
      return;
    }
    List<Module> unsupportedModules = new ArrayList<>();
    boolean androidGradleSeen = false;
    for (Module module : modules) {
      ModuleType moduleType = ModuleType.get(module);
      if (moduleType instanceof JavaModuleType) {
        String externalSystemId = module.getOptionValue(EXTERNAL_SYSTEM_ID_KEY);
        if (!GRADLE_SYSTEM_ID.getId().equals(externalSystemId)) {
          unsupportedModules.add(module);
        }
        else if (GradleFacet.isAppliedTo(module))
          androidGradleSeen = true;
      }
    }

    if (!androidGradleSeen || unsupportedModules.isEmpty()) {
      return;
    }
    String moduleNames = join(unsupportedModules, Module::getName, ", ");
    String text = "Compilation is not supported for following modules: " + moduleNames +
                  ". Unfortunately you can't have non-Gradle Java modules and Android-Gradle modules in one project.";
    AndroidNotification.getInstance(project).showBalloon("Unsupported Modules Detected", text, ERROR);
  }
}
