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
package com.android.tools.idea.npw.java

import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createDefaultTemplateAt
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.module.ModuleModel
import com.android.tools.idea.npw.template.TemplateHandle
import com.android.tools.idea.npw.template.TemplateValueInjector
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.templates.TemplateMetadata
import com.intellij.openapi.project.Project

class NewJavaModuleModel(
  project: Project, templateHandle: TemplateHandle, projectSyncInvoker: ProjectSyncInvoker
) : ModuleModel(project, templateHandle, projectSyncInvoker, "lib") {
  @JvmField val packageName = StringValueProperty()
  @JvmField val className = StringValueProperty("MyClass")

  override fun handleFinished() {
    val modulePaths = createDefaultTemplateAt(project.basePath!!, moduleName.get()).paths
    TemplateValueInjector(templateValues)
      .setModuleRoots(modulePaths, project.basePath!!, moduleName.get(), packageName.get())
      .setJavaVersion(project)
    templateValues[TemplateMetadata.ATTR_CLASS_NAME] = className.get()
    templateValues[TemplateMetadata.ATTR_IS_NEW_PROJECT] = true
    templateValues[TemplateMetadata.ATTR_IS_LIBRARY_MODULE] = true
    if (doDryRun()) {
      render()
    }
  }
}