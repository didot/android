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
package com.android.tools.idea.npw.template


import org.jetbrains.android.refactoring.isAndroidx
import org.jetbrains.android.util.AndroidBundle.message

import com.android.tools.adtui.util.FormScalingUtil
import com.android.tools.adtui.validation.ValidatorPanel
import com.android.tools.idea.model.AndroidModuleInfo
import com.android.tools.idea.npw.FormFactor
import com.android.tools.idea.npw.model.NewModuleModel
import com.android.tools.idea.npw.model.RenderTemplateModel
import com.android.tools.idea.npw.project.AndroidPackageUtils
import com.android.tools.idea.npw.ui.ActivityGallery
import com.android.tools.idea.npw.ui.WizardGallery
import com.android.tools.idea.observable.ListenerManager
import com.android.tools.idea.observable.core.ObservableBool
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.templates.TemplateMetadata
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.android.tools.idea.wizard.model.SkippableWizardStep
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Icon
import javax.swing.JComponent

/**
 * This step allows the user to select which type of component (Activity, Service, etc.) they want to create.
 * TODO: This class and ChooseModuleTypeStep looks to have a lot in common.
 *
 * Should we have something more specific than a ASGallery, that renders "Gallery items"?
 */
abstract class ChooseGalleryItemStep(moduleModel: NewModuleModel,
                            private val renderModel: RenderTemplateModel,
                            formFactor: FormFactor,
                            private val moduleTemplates: List<NamedModuleTemplate>,
                            private val messageKeys: WizardGalleryItemsStepMessageKeys,
                            private val emptyItemLabel: String)
  : SkippableWizardStep<NewModuleModel>(moduleModel, message(messageKeys.addMessage, formFactor.id), formFactor.icon) {

  abstract val templateRenders: List<TemplateRenderer>
  private val itemGallery = WizardGallery(title, { t: TemplateRenderer? -> t!!.icon }, { t: TemplateRenderer? -> t!!.label })
  private val validatorPanel = ValidatorPanel(this, JBScrollPane(itemGallery)).also {
      FormScalingUtil.scaleComponentTree(this.javaClass, it)
    }

  private val invalidParameterMessage = StringValueProperty()
  private val listeners = ListenerManager()

  protected val isNewModule: Boolean
    get() = renderModel.module == null

  constructor(moduleModel: NewModuleModel,
              renderModel: RenderTemplateModel,
              formFactor: FormFactor,
              targetDirectory: VirtualFile,
              messageKeys: WizardGalleryItemsStepMessageKeys,
              emptyItemLabel: String)
    : this(moduleModel, renderModel, formFactor,
           AndroidPackageUtils.getModuleTemplates(renderModel.androidFacet!!, targetDirectory),
           messageKeys, emptyItemLabel)

  override fun getComponent(): JComponent = validatorPanel

  override fun getPreferredFocusComponent(): JComponent? = itemGallery

  public override fun createDependentSteps(): Collection<ModelWizardStep<*>> =
    arrayListOf(ConfigureTemplateParametersStep(renderModel, message(messageKeys.stepTitle), moduleTemplates))

  override fun dispose() = listeners.releaseAll()

  override fun onWizardStarting(wizard: ModelWizard.Facade) {
    validatorPanel.registerMessageSource(invalidParameterMessage)

    itemGallery.setDefaultAction(object : AbstractAction() {
      override fun actionPerformed(actionEvent: ActionEvent) {
        wizard.goForward()
      }
    })

    itemGallery.addListSelectionListener {
      itemGallery.selectedElement?.run {
        renderModel.templateHandle = this.template
        wizard.updateNavigationProperties()
      }
      validateTemplate()
    }

    itemGallery.run {
      model = JBList.createDefaultListModel(templateRenders)
      selectedIndex = getDefaultSelectedTemplateIndex(templateRenders, emptyItemLabel)
    }
  }

  override fun canGoForward(): ObservableBool = validatorPanel.hasErrors().not()

  override fun onEntering() = validateTemplate()

  override fun onProceeding() {
    // TODO: From David: Can we look into moving this logic into handleFinished?
    // There should be multiple hashtables that a model points to, which gets merged at the last second.
    // That way, we can clear one of the hashtables.

    val moduleModel = model
    val project = moduleModel.project.valueOrNull
    if (renderModel.templateHandle == null) { // "Add No Activity" selected
      moduleModel.setDefaultRenderTemplateValues(renderModel, project)
    }
    else {
      moduleModel.renderTemplateValues.setValue(renderModel.templateValues)
    }

    TemplateValueInjector(moduleModel.templateValues).setProjectDefaults(project, moduleModel.applicationName.get())
  }


  /**
   * See also [com.android.tools.idea.actions.NewAndroidComponentAction.update]
   */
  private fun validateTemplate() {
    val template = renderModel.templateHandle
    val templateData = template?.metadata
    val androidSdkInfo = renderModel.androidSdkInfo.valueOrNull
    val facet = renderModel.androidFacet

    // Start by assuming API levels are great enough for the Template
    var moduleApiLevel = Integer.MAX_VALUE
    var moduleBuildApiLevel = Integer.MAX_VALUE
    if (androidSdkInfo != null) {
      moduleApiLevel = androidSdkInfo.minApiLevel
      moduleBuildApiLevel = androidSdkInfo.buildApiLevel
    }
    else if (facet != null) {
      val moduleInfo = AndroidModuleInfo.getInstance(facet)
      moduleApiLevel = moduleInfo.minSdkVersion.featureLevel
      if (moduleInfo.buildSdkVersion != null) {
        moduleBuildApiLevel = moduleInfo.buildSdkVersion!!.featureLevel
      }
    }

    val project = model.project.valueOrNull
    val isAndroidxProject = project != null && project.isAndroidx()
    invalidParameterMessage.set(validateTemplate(
      templateData, moduleApiLevel, moduleBuildApiLevel, isNewModule, isAndroidxProject, messageKeys))
  }

  class TemplateRenderer(internal val template: TemplateHandle?) {
    internal val label: String
      get() = ActivityGallery.getTemplateImageLabel(template, false)

    /**
     * Return the image associated with the current template, if it specifies one, or null otherwise.
     */
    internal val icon: Icon?
      get() = ActivityGallery.getTemplateIcon(template, false)

    override fun toString(): String = label
  }

  companion object {
    private fun getDefaultSelectedTemplateIndex(templateRenderers: List<TemplateRenderer>, emptyItemLabel: String): Int {
      val emptyItemIndex = templateRenderers.indexOfFirst { it.label == emptyItemLabel }

      if (emptyItemIndex != -1) {
        return emptyItemIndex
      }

      val firstValidTemplateIndex = templateRenderers.indexOfFirst { it.template != null }

      assert(firstValidTemplateIndex != -1)

      return firstValidTemplateIndex
    }
  }
}

@VisibleForTesting
fun validateTemplate(template: TemplateMetadata?,
                     moduleApiLevel: Int,
                     moduleBuildApiLevel: Int,
                     isNewModule: Boolean,
                     isAndroidxProject: Boolean,
                     messageKeys: WizardGalleryItemsStepMessageKeys): String =
  if (template == null) {
    if (isNewModule) "" else message(messageKeys.itemNotFound)
  }
  else if (moduleApiLevel < template.minSdk) {
    message(messageKeys.invalidMinSdk, template.minSdk)
  }
  else if (moduleBuildApiLevel < template.minBuildApi) {
    message(messageKeys.invalidMinBuild, template.minBuildApi)
  }
  else if (template.androidXRequired && !isAndroidxProject) {
    message(messageKeys.invalidAndroidX)
  }
  else ""

interface WizardGalleryItemsStepMessageKeys {

  val addMessage: String
  val stepTitle: String
  val itemNotFound: String
  val invalidMinSdk: String
  val invalidMinBuild: String
  val invalidAndroidX: String
}
