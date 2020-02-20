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
package com.android.tools.idea.templates

import com.android.annotations.concurrency.GuardedBy
import com.android.annotations.concurrency.Slow
import com.android.tools.idea.actions.NewAndroidComponentAction
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.npw.FormFactor
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.model.ProjectSyncInvoker.DefaultProjectSyncInvoker
import com.android.tools.idea.npw.model.RenderTemplateModel
import com.android.tools.idea.npw.model.RenderTemplateModel.Companion.fromFacet
import com.android.tools.idea.npw.project.getModuleTemplates
import com.android.tools.idea.npw.project.getPackageForPath
import com.android.tools.idea.npw.template.ChooseActivityTypeStep
import com.android.tools.idea.npw.template.ChooseFragmentTypeStep
import com.android.tools.idea.npw.template.TemplateResolver
import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder
import com.android.tools.idea.util.androidFacet
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.SkippableWizardStep
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.WizardUiContext
import com.google.common.collect.Table
import com.google.common.collect.TreeBasedTable
import com.intellij.ide.actions.NonEmptyActionGroup
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.LangDataKeys
import icons.AndroidIcons
import org.jetbrains.android.util.AndroidBundle.message
import org.jetbrains.annotations.PropertyKey
import com.android.tools.idea.wizard.template.Template

/**
 * Handles locating templates and providing template metadata
 */
class TemplateManager private constructor() {
  /** Lock protecting access to [_categoryTable]  */
  private val CATEGORY_TABLE_LOCK = Any()

  /** Table mapping (Category, Template Name) -> Template File  */
  @GuardedBy("CATEGORY_TABLE_LOCK")
  private var _categoryTable: Table<Category, String, Template>? = null

  @get:GuardedBy("CATEGORY_TABLE_LOCK")
  private val categoryTable: Table<Category, String, Template>?
    get() {
      if (_categoryTable == null) {
        reloadCategoryTable()
      }
      return _categoryTable
    }

  private val topGroup = DefaultActionGroup("AndroidTemplateGroup", false)

  @Slow
  fun getTemplateCreationMenu(): ActionGroup {
    refreshDynamicTemplateMenu()
    return topGroup
  }

  @Slow
  fun refreshDynamicTemplateMenu() = synchronized(CATEGORY_TABLE_LOCK) {
    topGroup.apply {
      removeAll()
      addSeparator()
    }

    val am = ActionManager.getInstance()
    reloadCategoryTable() // Force reload
    for (category in categoryTable!!.rowKeySet()) {
      // Create the menu group item
      val categoryGroup: NonEmptyActionGroup = object : NonEmptyActionGroup() {
        override fun update(e: AnActionEvent) {
          updateAction(e, category.name, childrenCount > 0, false)
        }
      }
      categoryGroup.isPopup = true
      fillCategory(categoryGroup, category, am)
      topGroup.add(categoryGroup)
      setPresentation(category, categoryGroup)
    }
  }

  @GuardedBy("CATEGORY_TABLE_LOCK")
  private fun fillCategory(categoryGroup: NonEmptyActionGroup, category: Category, am: ActionManager) {
    val categoryRow = _categoryTable!!.row(category)

    fun addCategoryGroup(category: Category, name: String, @PropertyKey(resourceBundle = "messages.AndroidBundle") messageKey: String) {
      val galleryAction: AnAction = object : AnAction() {
        override fun update(e: AnActionEvent) {
          updateAction(e, "Gallery...", true, true)
        }

        override fun actionPerformed(e: AnActionEvent) {
          showWizardDialog(e, category.name, message(messageKey, FormFactor.MOBILE.id), "New $name")
        }
      }
      categoryGroup.add(galleryAction)
      categoryGroup.addSeparator()
      setPresentation(category, galleryAction)
    }

    if (category == Category.Activity) {
      addCategoryGroup(category, "Android Activity", "android.wizard.activity.add")
    }

    if (StudioFlags.NPW_SHOW_FRAGMENT_GALLERY.get() && category == Category.Fragment) {
      addCategoryGroup(category, "Android Fragment", "android.wizard.fragment.add")
    }

    for (templateName in categoryRow.keys) {
      val template = _categoryTable!![category, templateName]
      val templateAction = NewAndroidComponentAction(category, templateName, template.minSdk, template.minCompileSdk, template.constraints)
      val actionId = ACTION_ID_PREFIX + category + templateName
      am.replaceAction(actionId, templateAction)
      categoryGroup.add(templateAction)
    }
  }

  @Slow
  @GuardedBy("CATEGORY_TABLE_LOCK")
  private fun reloadCategoryTable() {
    _categoryTable = TreeBasedTable.create()

    TemplateResolver.getAllTemplates()
      .filter { WizardUiContext.MenuEntry in it.uiContexts }
      .forEach { addTemplateToTable(it) }
  }

  @GuardedBy("CATEGORY_TABLE_LOCK")
  private fun addTemplateToTable(template: Template, userDefinedTemplate: Boolean = false) = with(template) {
    if (category == Category.Compose && !StudioFlags.COMPOSE_WIZARD_TEMPLATES.get()) {
      return
    }
    val existingTemplate = _categoryTable!![category, name]
    if (existingTemplate == null || template.revision > existingTemplate.revision) {
      _categoryTable!!.put(category, name, template)
    }
  }

  companion object {
    /**
     * A directory relative to application home folder where we can find an extra template folder. This lets us ship more up-to-date
     * templates with the application instead of waiting for SDK updates.
     */
    private const val CATEGORY_ACTIVITY = "Activity"
    private const val CATEGORY_FRAGMENT = "Fragment"
    private const val ACTION_ID_PREFIX = "template.create."

    @JvmStatic
    val instance = TemplateManager()

    private fun updateAction(event: AnActionEvent, actionText: String?, visible: Boolean, disableIfNotReady: Boolean) {
      val view = event.getData(LangDataKeys.IDE_VIEW)
      val module = event.getData(LangDataKeys.MODULE)
      val facet = module?.androidFacet
      val isProjectReady = facet != null && AndroidModel.get(facet) != null
      event.presentation.apply {
        text = actionText + (" (Project not ready)".takeUnless { isProjectReady } ?: "")
        isVisible = visible && view != null && facet != null && AndroidModel.isRequired(facet)
        isEnabled = !disableIfNotReady || isProjectReady
      }
    }

    private fun showWizardDialog(e: AnActionEvent, category: String, commandName: String, dialogTitle: String) {
      val projectSyncInvoker: ProjectSyncInvoker = DefaultProjectSyncInvoker()
      val module = LangDataKeys.MODULE.getData(e.dataContext)!!
      val targetFile = CommonDataKeys.VIRTUAL_FILE.getData(e.dataContext)!!
      var targetDirectory = targetFile
      if (!targetDirectory.isDirectory) {
        targetDirectory = targetFile.parent
        assert(targetDirectory != null)
      }
      val facet = module.androidFacet
      assert(facet != null && AndroidModel.get(facet) != null)
      val moduleTemplates = facet!!.getModuleTemplates(targetDirectory)
      assert(moduleTemplates.isNotEmpty())
      val initialPackageSuggestion = facet.getPackageForPath(moduleTemplates, targetDirectory)
      val renderModel = fromFacet(
        facet, initialPackageSuggestion, moduleTemplates[0],
        commandName, projectSyncInvoker, true
      )
      val chooseTypeStep: SkippableWizardStep<RenderTemplateModel>
      chooseTypeStep = when (category) {
        CATEGORY_ACTIVITY -> ChooseActivityTypeStep(renderModel, FormFactor.MOBILE, targetDirectory)
        CATEGORY_FRAGMENT -> ChooseFragmentTypeStep(renderModel, FormFactor.MOBILE, targetDirectory)
        else -> throw RuntimeException("Invalid category name: $category")
      }
      val wizard = ModelWizard.Builder().addStep(chooseTypeStep).build()
      StudioWizardDialogBuilder(wizard, dialogTitle).build().show()
    }

    private fun setPresentation(category: Category, categoryGroup: AnAction) {
      categoryGroup.templatePresentation.apply {
        icon = AndroidIcons.Android
        text = category.name
      }
    }
  }
}