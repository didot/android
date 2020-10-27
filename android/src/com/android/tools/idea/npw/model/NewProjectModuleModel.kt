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
package com.android.tools.idea.npw.model

import com.android.SdkConstants
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createDefaultTemplateAt
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createDummyTemplate
import com.android.tools.idea.device.FormFactor
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.template.TemplateResolver
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.ObjectValueProperty
import com.android.tools.idea.observable.core.OptionalProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.wizard.model.WizardModel
import com.android.tools.idea.wizard.template.StringParameter
import com.android.tools.idea.wizard.template.Template
import org.jetbrains.android.util.AndroidBundle.message
import java.util.Locale

/**
 * Orchestrates creation of the new project. Creates three steps (Project, Model, Activity) and renders them in a proper order.
 */
class NewProjectModuleModel(private val projectModel: NewProjectModel) : WizardModel() {
  @JvmField
  val formFactor = ObjectValueProperty(FormFactor.MOBILE)
  private val newModuleModel = NewAndroidModuleModel(
    projectModel,
    createDummyTemplate(),
    formFactor
  )

  /**
   * A model which is used at the optional step after usual activity configuring. Currently only used for Android Things.
   */
  @JvmField
  val extraRenderTemplateModel = RenderTemplateModel.fromModuleModel(newModuleModel, message("android.wizard.config.activity.title"))

  @JvmField
  val newRenderTemplate = OptionalValueProperty<Template>()

  @JvmField
  val hasCompanionApp = BoolValueProperty()

  fun androidSdkInfo(): OptionalProperty<AndroidVersionsInfo.VersionItem> = newModuleModel.androidSdkInfo

  override fun handleFinished() {
    initMainModule()
    val newRenderTemplateModel = createMainRenderModel()
    val packageName = projectModel.packageName.get()
    if (hasCompanionApp.get() && newRenderTemplateModel.hasActivity) {
      val companionModuleModel = createCompanionModuleModel(projectModel)
      val companionRenderModel = createCompanionRenderModel(companionModuleModel, packageName).apply {
        newTemplate.parameters.find { it.name == "Package name" }
      }

      companionModuleModel.androidSdkInfo.value = androidSdkInfo().value

      companionModuleModel.handleFinished()
      companionRenderModel.handleFinished()
    }

    newModuleModel.handleFinished()

    if (newRenderTemplateModel == extraRenderTemplateModel) {
      return // Extra render is driven by the Wizard itself
    }

    if (newRenderTemplateModel.hasActivity) {
      addRenderDefaultTemplateValues(newRenderTemplateModel, packageName)
      newRenderTemplateModel.handleFinished()
    }
    else {
      newRenderTemplateModel.handleSkipped() // "No Activity" selected
    }
  }

  private fun initMainModule() {
    val moduleName: String = if (hasCompanionApp.get())
      getModuleName(formFactor.get())
    else
      SdkConstants.APP_PREFIX

    val projectLocation = projectModel.projectLocation.get()

    newModuleModel.moduleName.set(moduleName)
    newModuleModel.template.set(createDefaultTemplateAt(projectLocation, moduleName))
  }

  private fun createMainRenderModel(): RenderTemplateModel = when {
    projectModel.enableCppSupport.get() || !extraRenderTemplateModel.hasActivity -> {
      RenderTemplateModel.fromModuleModel(newModuleModel).apply {
        if (newRenderTemplate.isPresent.get()) {
          newTemplate = newRenderTemplate.value
        }
      }
    }
    else -> extraRenderTemplateModel // Extra Render is visible. Use it.
  }
}

internal const val EMPTY_ACTIVITY = "Empty Activity"

private fun createCompanionModuleModel(projectModel: NewProjectModel): NewAndroidModuleModel {
  // Note: The companion Module is always a Mobile app
  val moduleName = getModuleName(FormFactor.MOBILE)
  val namedModuleTemplate = createDefaultTemplateAt(projectModel.projectLocation.get(), moduleName)
  val companionModuleModel = NewAndroidModuleModel(projectModel, namedModuleTemplate)
  companionModuleModel.moduleName.set(moduleName)

  return companionModuleModel
}

private fun createCompanionRenderModel(moduleModel: NewAndroidModuleModel, packageName: String): RenderTemplateModel {
  // Note: The companion Render is always a "Empty Activity"
  val companionRenderModel = RenderTemplateModel.fromModuleModel(moduleModel).apply {
    newTemplate = TemplateResolver.getAllTemplates().first { it.name == EMPTY_ACTIVITY }
  }
  addRenderDefaultTemplateValues(companionRenderModel, packageName)

  return companionRenderModel
}

private fun addRenderDefaultTemplateValues(renderTemplateModel: RenderTemplateModel, packageName: String) =
  renderTemplateModel.newTemplate.parameters.run {
    filterIsInstance<StringParameter>().forEach { it.value = it.suggest() ?: it.value }
    val packageNameParameter = find { it.name == "Package name" } as StringParameter?
    packageNameParameter?.value = packageName
  }

private fun getModuleName(formFactor: FormFactor): String =
  // Form factors like Android Auto build upon another form factor
  (formFactor.baseFormFactor ?: formFactor).id.replace("\\s".toRegex(), "_").toLowerCase(Locale.US)

