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
package com.android.tools.idea.actions

import com.android.tools.idea.actions.NewAndroidComponentAction.CREATED_FILES
import com.android.tools.idea.npw.FormFactor
import com.android.tools.idea.npw.model.NewModuleModel
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.model.RenderTemplateModel
import com.android.tools.idea.npw.project.AndroidPackageUtils
import com.android.tools.idea.npw.template.ChooseFragmentTypeStep
import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder
import com.android.tools.idea.wizard.model.ModelWizard
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import icons.StudioIcons
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidBundle
import java.io.File

/**
 * An action to launch the fragment wizard to create a fragment from a template.
 */
class NewAndroidFragmentAction
  : AnAction(AndroidBundle.message("android.wizard.new.fragment.title"),
             AndroidBundle.message("android.wizard.new.fragment.title"),
             null) {

  var shouldOpenFiles = true

  init {
    templatePresentation.icon = StudioIcons.Shell.Filetree.ANDROID_FILE
  }

  override fun actionPerformed(e: AnActionEvent) {
    val dataContext = e.dataContext
    val module = LangDataKeys.MODULE.getData(dataContext) ?: return
    val facet = AndroidFacet.getInstance(module)
    if (facet == null || facet.configuration.model == null) {
      return
    }

    var targetDirectory = CommonDataKeys.VIRTUAL_FILE.getData(dataContext)
    // If the user selected a simulated folder entry (eg "Manifests"), there will be no target directory
    if (targetDirectory != null && !targetDirectory.isDirectory) {
      targetDirectory = targetDirectory.parent
    }
    val directory = targetDirectory!!

    val moduleTemplates = AndroidPackageUtils.getModuleTemplates(facet, targetDirectory)
    assert(!moduleTemplates.isEmpty())

    val initialPackageSuggestion = AndroidPackageUtils.getPackageForPath(facet, moduleTemplates, targetDirectory)
    val project = module.project

    val dialogTitle = AndroidBundle.message("android.wizard.new.fragment.title")

    val projectSyncInvoker = ProjectSyncInvoker.DefaultProjectSyncInvoker()
    val renderModel = RenderTemplateModel.fromFacet(
      facet, null, initialPackageSuggestion, moduleTemplates[0],
      AndroidBundle.message("android.wizard.fragment.add", FormFactor.MOBILE.id), projectSyncInvoker, shouldOpenFiles)

    val moduleModel = NewModuleModel(project, null, projectSyncInvoker, moduleTemplates[0])
    val fragmentTypeStep = ChooseFragmentTypeStep(moduleModel, renderModel, FormFactor.MOBILE, directory)
    val wizard = ModelWizard.Builder().addStep(fragmentTypeStep).build()

    val dialog = StudioWizardDialogBuilder(wizard, dialogTitle).setProject(project).build()
    dialog.show()
    val createdFiles = dataContext.getData(CREATED_FILES) as MutableList<File>?
    createdFiles?.addAll(renderModel.createdFiles)
  }
}

