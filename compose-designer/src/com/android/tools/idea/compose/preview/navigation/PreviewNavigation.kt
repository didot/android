/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.navigation

import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.AndroidCoordinate
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.compose.preview.navigation.PreviewNavigation.LOG
import com.android.tools.idea.uibuilder.model.viewInfo
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.idea.core.util.getLineStartOffset
import java.util.WeakHashMap

private object PreviewNavigation {
  val LOG = Logger.getInstance(PreviewNavigation::class.java)
}

/**
 * Converts a [SourceLocation] into a [Navigatable]. If the [SourceLocation] does not point to a file within the project, it is not
 * possible to create a [Navigatable] and the method will return null.
 */
private fun SourceLocation.toNavigatable(project: Project): Navigatable? {
  val sourceLocationWithVirtualFile =
    if (this is SourceLocationWithVirtualFile) this else this.asSourceLocationWithVirtualFile(project) ?: return null
  val psiFile = PsiManager.getInstance(project).findFile(sourceLocationWithVirtualFile.virtualFile) ?: return null
  return PsiNavigationSupport.getInstance().createNavigatable(
    project,
    sourceLocationWithVirtualFile.virtualFile,
    psiFile.getLineStartOffset(sourceLocationWithVirtualFile.lineNumber) ?: 0)
}

/**
 * Utility method that dumps the given [ComposeViewInfo] list to the log.
 */
private fun dumpViewInfosToLog(project: Project, viewInfos: List<ComposeViewInfo>, indent: Int = 0) {
  val margin = "-".repeat(indent)
  viewInfos.forEach {
    LOG.debug("$margin $it navigatable=${it.sourceLocation.toNavigatable(project)}")
    dumpViewInfosToLog(project, it.children, indent + 1)
  }
}

/**
 * Returns a list of [SourceLocation]s that references to the source code position of the Composable at the given x, y pixel coordinates.
 * The list is sorted with the elements deeper in the hierarchy at the top.
 */
@VisibleForTesting
fun findComponentHits(project: Project, rootViewInfo: ViewInfo, @AndroidCoordinate x: Int, @AndroidCoordinate y: Int): List<SourceLocation> {
  val allViewInfos = parseViewInfo(rootViewInfo, lineNumberMapper = remapInline(project))

  if (LOG.isDebugEnabled) {
    dumpViewInfosToLog(project, allViewInfos)
  }

  return allViewInfos
    .findHitWithDepth(x, y)
    // We do not need to keep hits without source information
    .filter { !it.second.sourceLocation.isEmpty() }
    // Sort by the hit depth. Elements lower in the hierarchy, are at the top. If they are the same level, order by line number
    .sortedWith(compareByDescending<Pair<Int, ComposeViewInfo>> { it.first }.thenByDescending { it.second.sourceLocation.lineNumber })
    .map { it.second.sourceLocation }
}

/**
 * Returns a [Navigatable] that references to the source code position of the Composable at the given x, y pixel coordinates.
 */
fun findNavigatableComponentHit(project: Project,
                                rootViewInfo: ViewInfo, @AndroidCoordinate x: Int, @AndroidCoordinate y: Int): Navigatable? {
  val hits = findComponentHits(project, rootViewInfo, x, y)

  if (LOG.isDebugEnabled) {
    LOG.debug("${hits.size} hits found in")
    hits
      .filter { it.toNavigatable(project) != null }
      .forEach { LOG.debug("  Navigatable hit: ${it}") }
  }

  return hits
    .mapNotNull { it.toNavigatable(project) }
    .firstOrNull()
}

/**
 * Handles navigation for compose preview when NlDesignSurface preview is clicked.
 */
class PreviewNavigationHandler : NlDesignSurface.NavigationHandler {
  // Default location to use when components are not found
  private val defaultNavigationMap = WeakHashMap<NlModel, Navigatable>()

  /**
   * Add default navigation location for model.
   */
  fun setDefaultLocation(model: NlModel, psiFile: PsiFile, offset: Int) {
    LOG.debug { "Default location set to ${psiFile.name}:$offset" }
    defaultNavigationMap[model] = PsiNavigationSupport.getInstance().createNavigatable(model.project, psiFile.virtualFile!!, offset)
  }

  override fun handleNavigate(sceneView: SceneView,
                              sceneComponent: SceneComponent,
                              @SwingCoordinate hitX: Int,
                              @SwingCoordinate hitY: Int,
                              requestFocus: Boolean): Boolean {
    val x = Coordinates.getAndroidX(sceneView, hitX)
    val y = Coordinates.getAndroidY(sceneView, hitY)
    LOG.debug { "handleNavigate x=$x, y=$y" }

    val model = sceneView.sceneManager.model

    // Find component to navigate to
    val root = model.components[0]
    val viewInfo = root.viewInfo ?: return false
    val navigatable = findNavigatableComponentHit(model.project, viewInfo, x, y)
    if (navigatable != null) {
      navigatable.navigate(requestFocus)
      return true
    }

    val navigatedToDefault = navigateToDefault(sceneView, requestFocus)
    LOG.debug { "Navigated to default? $navigatedToDefault" }
    return navigatedToDefault
  }

  private fun navigateToDefault(sceneView: SceneView, requestFocus: Boolean): Boolean {
    defaultNavigationMap[sceneView.sceneManager.model]?.navigate(requestFocus) ?: return false
    return true
  }

  override fun dispose() {
    defaultNavigationMap.clear()
  }
}