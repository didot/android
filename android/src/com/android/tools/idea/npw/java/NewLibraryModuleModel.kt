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

import com.android.sdklib.SdkVersionInfo
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.npw.FormFactor
import com.android.tools.idea.npw.model.ExistingProjectModelData
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.module.ModuleModel
import com.android.tools.idea.npw.module.recipes.pureLibrary.generatePureLibrary
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.templates.TemplateAttributes.ATTR_CLASS_NAME
import com.android.tools.idea.templates.TemplateAttributes.ATTR_IS_NEW_MODULE
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.Recipe
import com.android.tools.idea.wizard.template.TemplateData
import com.intellij.openapi.project.Project
import com.intellij.util.lang.JavaVersion
import java.io.File

class NewLibraryModuleModel(
  project: Project, templateFile: File, projectSyncInvoker: ProjectSyncInvoker
) : ModuleModel(templateFile, "lib", "New Library Module", true, ExistingProjectModelData(project, projectSyncInvoker)) {
  @JvmField
  val className = StringValueProperty("MyClass")

  // TODO(qumeric): will it fail if there are no SDKs installed?
  override val androidSdkInfo = OptionalValueProperty(
    AndroidVersionsInfo().apply { loadLocalVersions() }
      .getKnownTargetVersions(FormFactor.MOBILE, SdkVersionInfo.LOWEST_ACTIVE_API)
      .first() // we don't care which one do we use, we just have to pass something, it is not going to be used
  )

  override val renderer = object : ModuleTemplateRenderer() {
    override val recipe: Recipe get() = { td: TemplateData -> generatePureLibrary(td as ModuleTemplateData, className.get()) }

    override fun init() {
      super.init()

      val newValues = mutableMapOf(
        ATTR_CLASS_NAME to className.get(),
        ATTR_IS_NEW_MODULE to true
      )

      moduleTemplateValues.putAll(newValues)

      if (StudioFlags.NPW_NEW_MODULE_TEMPLATES.get()) {
        moduleTemplateDataBuilder.apply {
          projectTemplateDataBuilder.apply {
            javaVersion = JavaVersion.parse("1.7")
          }
        }
      }
    }
  }
}
