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
package com.android.tools.idea.npw.module

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate
import com.android.tools.idea.npw.model.ExistingProjectModelData
import com.android.tools.idea.npw.model.MultiTemplateRenderer
import com.android.tools.idea.npw.model.ProjectModelData
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.model.RenderTemplateModel.Companion.getInitialSourceLanguage
import com.android.tools.idea.npw.model.doRender
import com.android.tools.idea.npw.model.render
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.template.TemplateHandle
import com.android.tools.idea.npw.template.TemplateValueInjector
import com.android.tools.idea.observable.core.ObjectValueProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.templates.ModuleTemplateDataBuilder
import com.android.tools.idea.templates.ProjectTemplateDataBuilder
import com.android.tools.idea.templates.TemplateAttributes.ATTR_IS_LIBRARY_MODULE
import com.android.tools.idea.templates.TemplateUtils.openEditors
import com.android.tools.idea.templates.recipe.DefaultRecipeExecutor2
import com.android.tools.idea.templates.recipe.FindReferencesRecipeExecutor2
import com.android.tools.idea.templates.recipe.RenderingContext.Builder
import com.android.tools.idea.templates.recipe.RenderingContext2
import com.android.tools.idea.wizard.model.WizardModel
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Recipe
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import org.jetbrains.android.util.AndroidBundle.message
import java.io.File

abstract class ModuleModel(
  project: Project,
  val templateHandle: TemplateHandle,
  projectSyncInvoker: ProjectSyncInvoker,
  moduleName: String,
  private val commandName: String = "New Module",
  val isLibrary: Boolean,
  projectModelData: ProjectModelData = ExistingProjectModelData(project, projectSyncInvoker)
) : WizardModel(), ProjectModelData by projectModelData {
  val formFactor = ObjectValueProperty(FormFactor.Generic)
  override val packageName = StringValueProperty()
  val moduleName = StringValueProperty(moduleName)
  open val androidSdkInfo = OptionalValueProperty<AndroidVersionsInfo.VersionItem>()
  override val language = OptionalValueProperty(getInitialSourceLanguage(project))
  val templateValues = mutableMapOf<String, Any>()
  val moduleTemplateDataBuilder = ModuleTemplateDataBuilder(ProjectTemplateDataBuilder(false))
  override val multiTemplateRenderer = MultiTemplateRenderer { renderer ->
    object : Task.Modal(project, message("android.compile.messages.generating.r.java.content.name"), false) {
      override fun run(indicator: ProgressIndicator) {
        renderer(project)
      }
    }.queue()
    projectSyncInvoker.syncProject(project)
  }
  protected abstract val renderer: MultiTemplateRenderer.TemplateRenderer

  public override fun handleFinished() {
    multiTemplateRenderer.requestRender(renderer)
  }

  override fun handleSkipped() {
    multiTemplateRenderer.skipRender()
  }

  protected abstract inner class ModuleTemplateRenderer : MultiTemplateRenderer.TemplateRenderer {
    /**
     * A new system recipe which will should be run from [render] if the new system is used.
     */
    protected abstract val recipe: Recipe

    @WorkerThread
    override fun init() {
      val modulePaths = GradleAndroidModuleTemplate.createDefaultTemplateAt(project.basePath!!, moduleName.get()).paths

      TemplateValueInjector(templateValues)
        .setProjectDefaults(project, false)
        .setModuleRoots(modulePaths, project.basePath!!, moduleName.get(), packageName.get())
        .setLanguage(language.value)
        .setJavaVersion(project)

      templateValues[ATTR_IS_LIBRARY_MODULE] = isLibrary

      if (StudioFlags.NPW_NEW_MODULE_TEMPLATES.get()) {
        moduleTemplateDataBuilder.apply {
          projectTemplateDataBuilder.apply {
            setProjectDefaults(project)
            language = this@ModuleModel.language.value
          }
          formFactor = this@ModuleModel.formFactor.get()
          isNew = true
          setBuildVersion(androidSdkInfo.value, project)
          setModuleRoots(modulePaths, project.basePath!!, moduleName.get(), this@ModuleModel.packageName.get())
          isLibrary = this@ModuleModel.isLibrary
        }
      }
    }

    @WorkerThread
    override fun doDryRun(): Boolean = renderTemplate(true, project)

    @WorkerThread
    override fun render() {
      renderTemplate(false, project)
    }

    protected open fun renderTemplate(dryRun: Boolean, project: Project, runFromTemplateRenderer: Boolean = false): Boolean {
      val moduleRoot = getModuleRoot(project.basePath!!, moduleName.get())

      if (StudioFlags.NPW_NEW_MODULE_TEMPLATES.get()) {
        val context = RenderingContext2(
          project = project,
          module = null,
          commandName = commandName,
          templateData = moduleTemplateDataBuilder.build(),
          moduleRoot = moduleRoot,
          dryRun = dryRun,
          showErrors = true
        )

        val executor = if (dryRun) FindReferencesRecipeExecutor2(context) else DefaultRecipeExecutor2(context)
        return recipe.doRender(context, executor)
      }

      val projectRoot = File(project.basePath!!)
      val template = templateHandle.template
      val filesToOpen = mutableListOf<File>()

      val context = Builder.newContext(template, project)
        .withCommandName(message("android.wizard.module.new.module.command"))
        .withDryRun(dryRun)
        .withShowErrors(true)
        .withOutputRoot(projectRoot)
        .withModuleRoot(moduleRoot)
        .withParams(templateValues)
        .intoOpenFiles(filesToOpen)
        .build()

      return template.render(context!!, dryRun).also {
        if (it && !dryRun) {
          // calling smartInvokeLater will make sure that files are open only when the project is ready
          DumbService.getInstance(project).smartInvokeLater { openEditors(project, filesToOpen, true) }
          // TODO(qumeric): remove after moving to moduleTemplateRenderer
          if (!runFromTemplateRenderer) {
            projectSyncInvoker.syncProject(project)
          }
        }
      }
    }
  }
}
/**
 * Module names may use ":" for sub folders. This mapping is only true when creating new modules, as the user can later customize
 * the Module Path (called Project Path in gradle world) in "settings.gradle"
 */
fun getModuleRoot(projectLocation: String, moduleName: String) = File(projectLocation, moduleName.replace(':', File.separatorChar))
