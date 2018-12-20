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
package com.android.tools.idea.resourceExplorer.editor

import com.android.tools.idea.resourceExplorer.importer.ImportersProvider
import com.android.tools.idea.resourceExplorer.view.ResourceExplorerToolbar
import com.android.tools.idea.resourceExplorer.view.ResourceExplorerView
import com.android.tools.idea.resourceExplorer.view.ResourceImportDragTarget
import com.android.tools.idea.resourceExplorer.viewmodel.ProjectResourcesBrowserViewModel
import com.android.tools.idea.resourceExplorer.viewmodel.ResourceExplorerToolbarViewModel
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.android.facet.AndroidFacet
import java.awt.BorderLayout
import javax.swing.JPanel
import kotlin.properties.Delegates

internal val RESOURCE_DEBUG = System.getProperty("res.manag.debug", "false")?.toBoolean() ?: false

/**
 * The resource explorer lets the user browse resources from the provided [AndroidFacet]
 */
class ResourceExplorer private constructor(facet: AndroidFacet)
  : JPanel(BorderLayout()), Disposable {

  var facet by Delegates.observable(facet) { _, _, newValue -> updateFacet(newValue) }


  private val importersProvider = ImportersProvider()

  private val projectResourcesBrowserViewModel = ProjectResourcesBrowserViewModel(facet)
  private val toolbarViewModel = ResourceExplorerToolbarViewModel(
    facet,
    importersProvider,
    projectResourcesBrowserViewModel.filterOptions
  ) { newFacet -> this.facet = newFacet }

  private val resourceImportDragTarget = ResourceImportDragTarget(facet, importersProvider)
  private val toolbar = ResourceExplorerToolbar(toolbarViewModel)
  private val resourceExplorerView = ResourceExplorerView(projectResourcesBrowserViewModel, resourceImportDragTarget)

  companion object {

    /**
     * Create a new instance of [ResourceExplorer] optimized to be used in a [com.intellij.openapi.wm.ToolWindow]
     */
    @JvmStatic
    fun createForToolWindow(facet: AndroidFacet): ResourceExplorer = ResourceExplorer(facet)
  }

  init {
    val centerContainer = JPanel(BorderLayout())

    centerContainer.add(toolbar, BorderLayout.NORTH)
    centerContainer.add(resourceExplorerView)
    add(centerContainer, BorderLayout.CENTER)
    Disposer.register(this, projectResourcesBrowserViewModel)
    Disposer.register(this, resourceExplorerView)
  }

  private fun updateFacet(facet: AndroidFacet) {
    projectResourcesBrowserViewModel.facet = facet
    resourceImportDragTarget.facet = facet
    toolbarViewModel.facet = facet
  }

  override fun dispose() {
  }
}