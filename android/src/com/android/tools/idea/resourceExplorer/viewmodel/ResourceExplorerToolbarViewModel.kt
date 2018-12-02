/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.viewmodel

import com.android.tools.idea.resourceExplorer.importer.ImportersProvider
import com.android.tools.idea.resourceExplorer.importer.chooseDesignAssets
import com.android.tools.idea.resourceExplorer.model.FilterOptions
import com.android.tools.idea.resourceExplorer.plugin.ResourceImporter
import com.android.tools.idea.resourceExplorer.view.ResourceImportDialog
import com.android.tools.idea.util.androidFacet
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeView
import com.intellij.ide.util.DirectoryChooserUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.android.actions.CreateResourceFileAction
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.ResourceFolderManager

/**
 * View model for the [com.android.tools.idea.resourceExplorer.view.ResourceExplorerToolbar].
 * @param facetUpdaterCallback callback to call when a new facet is selected.
 */
class ResourceExplorerToolbarViewModel(
  facet: AndroidFacet,
  private val importersProvider: ImportersProvider,
  private val filterOptions: FilterOptions,
  private val facetUpdaterCallback: (AndroidFacet) -> Unit)
  : DataProvider, IdeView {

  var facet: AndroidFacet = facet
    set(newFacet) {
      if (field != newFacet) {
        field = newFacet
      }
    }

  /**
   * Name of the module currently selected
   */
  var currentModuleName: String = facet.name
    get() = facet.module.name
    private set

  val addActions
    get() = DefaultActionGroup().apply {
      val actionManager = ActionManager.getInstance()
      add(actionManager.getAction("NewAndroidImageAsset"))
      add(actionManager.getAction("NewAndroidVectorAsset"))
      add(CreateResourceFileAction.getInstance())
      add(Separator())
      add(ImportResourceAction())
    }

  /**
   * Returns the [AnAction] to open the available [com.android.tools.idea.resourceExplorer.plugin.ResourceImporter]s.
   */
  fun getImportersActions(): List<AnAction> {
    return customImporters.map { importer ->
      object : DumbAwareAction(importer.presentableName) {
        override fun actionPerformed(e: AnActionEvent) {
          invokeImporter(importer)
        }
      }
    }
  }

  /**
   * Open the [com.android.tools.idea.resourceExplorer.plugin.ResourceImporter] at the provided index in
   * the [ImportersProvider.importers] list.
   */
  private fun invokeImporter(importer: ResourceImporter) {
    val files = chooseFile(importer.getSupportedFileTypes(), importer.supportsBatchImport)
    importer.invokeCustomImporter(facet, files)
  }

  /**
   * Prompts user to choose a file.
   *
   * @return filePath or null if user cancels the operation
   */
  private fun chooseFile(supportedFileTypes: Set<String>, supportsBatchImport: Boolean): Collection<String> {
    val fileChooserDescriptor = FileChooserDescriptor(true, true, false, false, false, supportsBatchImport)
      .withFileFilter { file ->
        supportedFileTypes.any { Comparing.equal(file.extension, it, SystemInfo.isFileSystemCaseSensitive) }
      }
    return FileChooser.chooseFiles(fileChooserDescriptor, facet.module.project, null)
      .map(VirtualFile::getPath)
      .map(FileUtil::toSystemDependentName)
  }

  private val customImporters get() = importersProvider.importers.filter { it.hasCustomImport }

  var isShowDependencies: Boolean
    get() = filterOptions.isShowLibraries
    set(value) {
      filterOptions.isShowLibraries = value
    }

  /**
   * Implementation of [IdeView.getDirectories] that returns the resource directories of
   * the selected facet.
   * This is needed to run [CreateResourceFileAction]
   */
  override fun getDirectories() = ResourceFolderManager.getInstance(facet).folders
    .mapNotNull { runReadAction { PsiManager.getInstance(facet.module.project).findDirectory(it) } }
    .toTypedArray()

  override fun getOrChooseDirectory() = DirectoryChooserUtil.getOrChooseDirectory(this)

  /**
   * Implementation of [DataProvider] needed for [CreateResourceFileAction]
   */
  override fun getData(dataId: String): Any? = when (dataId) {
    LangDataKeys.MODULE.name -> facet.module
    LangDataKeys.IDE_VIEW.name -> this
    else -> null
  }

  /**
   * Return the [AnAction]s to switch to another module.
   * This method only returns Android modules.
   */
  fun getSelectModuleActions(): List<AnAction> {
    return ModuleManager.getInstance(facet.module.project)
      .modules
      .mapNotNull { it.androidFacet }
      .filterNot { it == facet }
      .map { androidFacet ->
        object : DumbAwareAction(androidFacet.module.name) {
          override fun actionPerformed(e: AnActionEvent) {
            facetUpdaterCallback(androidFacet)
          }
        }
      }
  }

  inner class ImportResourceAction : AnAction("Import...", "Import files from disk", AllIcons.Actions.Upload), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
      chooseDesignAssets(importersProvider) {
        ResourceImportDialog(ResourceImportDialogViewModel(facet, it, importersProvider = importersProvider)).show()
      }
    }
  }
}
