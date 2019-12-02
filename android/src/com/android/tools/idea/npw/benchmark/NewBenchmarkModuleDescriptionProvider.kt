/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.npw.benchmark

import com.android.sdklib.SdkVersionInfo.LOWEST_ACTIVE_API
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.module.ModuleDescriptionProvider
import com.android.tools.idea.npw.module.ModuleGalleryEntry
import com.android.tools.idea.templates.Template
import com.android.tools.idea.templates.TemplateManager
import com.android.tools.idea.wizard.model.SkippableWizardStep
import com.intellij.openapi.project.Project
import icons.AndroidIcons
import org.jetbrains.android.util.AndroidBundle.message
import javax.swing.Icon

class NewBenchmarkModuleDescriptionProvider : ModuleDescriptionProvider {
  override fun getDescriptions(project: Project): Collection<ModuleGalleryEntry> = listOf(BenchmarkModuleTemplateGalleryEntry())

  private class BenchmarkModuleTemplateGalleryEntry : ModuleGalleryEntry {
    override val icon: Icon = AndroidIcons.Wizards.BenchmarkModule
    override val name: String = message("android.wizard.module.new.benchmark.module.app")
    override val description: String = message("android.wizard.module.new.benchmark.module.description")
    override fun toString(): String = name
    override fun createStep(project: Project, projectSyncInvoker: ProjectSyncInvoker, moduleParent: String?): SkippableWizardStep<*> {
      val templateFile = TemplateManager.getTemplate(Template.CATEGORY_APPLICATION, "Benchmark Module")
      return ConfigureBenchmarkModuleStep(NewBenchmarkModuleModel(project, templateFile, projectSyncInvoker), name, LOWEST_ACTIVE_API)
    }
  }
}