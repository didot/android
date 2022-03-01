/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.common.error

import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.editor.DesignToolsSplitEditor
import com.android.tools.idea.common.editor.SplitEditor
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.type.typeOf
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.editor.multirepresentation.sourcecode.SourceCodePreview
import com.android.tools.idea.uibuilder.type.DrawableFileType
import com.android.tools.idea.uibuilder.type.LayoutFileType
import com.android.tools.idea.uibuilder.type.MenuFileType
import com.android.tools.idea.uibuilder.type.PreferenceScreenFileType
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.ide.DataManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.ColorUtil.toHtmlColor
import com.intellij.ui.content.Content
import com.intellij.util.ui.UIUtil

/**
 * A service to help to show the issues of Design Tools in IJ's Problems panel.
 */
class IssuePanelService(private val project: Project) {

  /**
   * The shared issue panel between all tools.
   * This is the temp solution to replace the nested issue panel of all design tools.
   *
   * Some design tools should have independent tab, which will be added in the feature.
   * This feature is rely on [com.android.tools.idea.flags.StudioFlags.NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS] flag.
   */
  private var sharedIssueTab: Content? = null
  private var sharedIssuePanel: DesignerCommonIssuePanel? = null

  private val initLock = Any()
  private var inited = false

  init {
    val manager = ToolWindowManager.getInstance(project)
    val problemsView = manager.getToolWindow(ProblemsView.ID)
    if (problemsView != null && !problemsView.isDisposed) {
      // ProblemsView has registered, init the tab.
      UIUtil.invokeLaterIfNeeded { initIssueTabs(problemsView) }
    }
    else {
      val connection = project.messageBus.connect()
      val listener: ToolWindowManagerListener
      listener = object : ToolWindowManagerListener {
        override fun toolWindowsRegistered(ids: MutableList<String>, toolWindowManager: ToolWindowManager) {
          if (ProblemsView.ID in ids) {
            val problemsViewToolWindow = ProblemsView.getToolWindow(project)
            if (problemsViewToolWindow != null && !problemsViewToolWindow.isDisposed) {
              initIssueTabs(problemsViewToolWindow)
              connection.disconnect()
            }
          }
        }
      }
      connection.subscribe(ToolWindowManagerListener.TOPIC, listener)
    }
  }

  fun initIssueTabs(problemsViewWindow: ToolWindow) {
    synchronized(initLock) {
      if (inited) {
        return
      }
      inited = true
    }

    // This is the only common issue panel.
    val contentManager = problemsViewWindow.contentManager
    val contentFactory = contentManager.factory

    // The shared issue panel for all design tools.
    if (StudioFlags.NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS.get()) {
      val issuePanel = DesignerCommonIssuePanel(project, project, DesignToolsIssueProvider(project))

      sharedIssuePanel = issuePanel
      contentFactory.createContent(issuePanel.getComponent(), "Design Issue", true).apply {
        sharedIssueTab = this
        isCloseable = false
        contentManager.addContent(this@apply)
      }
    }

    project.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        if (!isTabShowing(sharedIssueTab)) {
          return
        }
        // If it is current tab, remove it.
        if (!source.hasOpenFiles()) {
          removeSharedIssueTabFromProblemsPanel()
        }
      }

      override fun selectionChanged(event: FileEditorManagerEvent) {
        val surface = getDesignSurface(event.newEditor)
        if (surface == null || surface.layoutType is DrawableFileType) {
          removeSharedIssueTabFromProblemsPanel()
        }
        else {
          // Surface exists.
          addSharedIssueTabToProblemsPanel()
          setShowSurfaceIssuePanel(true, surface, false)
        }
      }
    })
  }

  private fun getDesignSurface(editor: FileEditor?): DesignSurface? {
    when (editor) {
      is DesignToolsSplitEditor -> return editor.designerEditor.component.surface
      is SplitEditor<*> -> {
        // Check if there is a design surface in the context of presentation. For example, Compose and CustomView preview.
        val component = (editor.preview as? SourceCodePreview)?.currentRepresentation?.component ?: return null
        return DataManager.getInstance().getDataContext(component).getData(DESIGN_SURFACE)
      }
      else -> return null
    }
  }

  /**
   * Remove the [sharedIssueTab] from Problems Tool Window. Return true if the [sharedIssueTab] is removed successfully, or false if
   * the [sharedIssueTab] doesn't exist or the [sharedIssueTab] is not in the Problems Tool Window (e.g. has been removed before).
   */
  private fun removeSharedIssueTabFromProblemsPanel(): Boolean {
    val tab = sharedIssueTab ?: return false
    val toolWindow = ProblemsView.getToolWindow(project) ?: return false
    toolWindow.contentManager.removeContent(tab, false)
    return true
  }

  /**
   * Add the [sharedIssueTab] into Problems Tool Window. Return true if the [sharedIssueTab] is added successfully, or false if the
   * [sharedIssueTab] doesn't exist or the [sharedIssueTab] is in the Problems Tool Window already (e.g. has been added before).
   */
  private fun addSharedIssueTabToProblemsPanel(): Boolean {
    val tab = sharedIssueTab ?: return false
    val toolWindow = ProblemsView.getToolWindow(project) ?: return false
    if (toolWindow.contentManager.contents.contains(tab)) {
      return false
    }
    toolWindow.contentManager.addContent(tab, 2)
    return true
  }

  /**
   * Return if the current issue panel of the given [DesignSurface] are showing.
   */
  fun isShowingIssuePanel(surface: DesignSurface) : Boolean {
    if (StudioFlags.NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS.get()) {
      return isTabShowing(sharedIssueTab)
    }
    return !surface.issuePanel.isMinimized
  }

  fun setShowSurfaceIssuePanel(visible: Boolean, surface: DesignSurface, userInvoked: Boolean) {
    if (StudioFlags.NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS.get()) {
      if (visible) {
        showSharedIssuePanel(surface)
      }
      else {
        hideSharedIssuePanel()
      }
    }
    else {
      if (visible) {
        surface.setShowIssuePanel(true, userInvoked)
      }
      else {
        surface.setShowIssuePanel(false, userInvoked)
      }
    }
  }

  /**
   * Show the issue panel for the given [DesignSurface].
   */
  private fun showSharedIssuePanel(surface: DesignSurface) {
    val tab = sharedIssueTab ?: return
    if (!isTabShowing(tab)) {
      showTab(tab)
    }
    val surfaceName = when (surface.name) {
      null -> {
        when (surface.models.firstOrNull()?.file?.typeOf()) {
          is LayoutFileType -> "Layout"
          is PreferenceScreenFileType -> "Preference"
          is MenuFileType -> "Menu"
          else -> "Designer"
        }
      }
      else -> surface.name
    }
    tab.displayName = createTabName(surfaceName, sharedIssuePanel?.issueProvider?.getFilteredIssues()?.count() ?: 0)
  }

  /**
   * This function will hide IJ's Problem Panel.
   * If IJ's Problem panel cannot be found, then this function does nothing.
   */
  private fun hideSharedIssuePanel() {
    val problemsViewPanel = ProblemsView.getToolWindow(project) ?: return
    problemsViewPanel.hide()
  }

  /**
   * Select the highest severity issue related to the provided [NlComponent] and scroll the viewport to issue.
   * TODO: Remove the dependency of [NlComponent]
   */
  fun showIssueForComponent(surface: DesignSurface, userInvoked: Boolean, component: NlComponent, collapseOthers: Boolean) {
    setShowSurfaceIssuePanel(true, surface, userInvoked)
    // TODO: The shared issue panel should support this feature.
    if (!StudioFlags.NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS.get()) {
      val issuePanel = surface.issuePanel
      val issueModel = surface.issueModel
      val issue: Issue = issueModel.getHighestSeverityIssue(component) ?: return
      val issueView = issuePanel.getDisplayIssueView(issue)
      if (issueView != null) {
        if (collapseOthers) {
          issueModel.issues.filter { it != issue }.mapNotNull { issuePanel.getDisplayIssueView(it) }.forEach { it.setExpanded(false) }
          issueModel.issues.mapNotNull { issuePanel.getDisplayIssueView(it) }.filter { it.issue != issue }.forEach { it.setExpanded(false) }
        }
        issuePanel.scrollToIssueView(issueView)
      }
    }
  }

  /**
   * Return the visibility of issue panel for the given [DesignSurface].
   */
  fun isIssuePanelVisible(surface: DesignSurface): Boolean {
    return if (StudioFlags.NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS.get()) {
      isTabShowing(sharedIssueTab)
    }
    else {
      !surface.issuePanel.isMinimized
    }
  }

  /**
   * Return true if IJ's problem panel is visible and selecting the given [tab], false otherwise.
   */
  private fun isTabShowing(tab: Content?): Boolean {
    if (tab == null) {
      return false
    }
    val problemsViewPanel = ProblemsView.getToolWindow(project) ?: return false
    if (!problemsViewPanel.isVisible || tab !in problemsViewPanel.contentManager.contents) {
      return false
    }
    return tab.isSelected
  }

  private fun showTab(tab: Content) {
    val problemsViewPanel = ProblemsView.getToolWindow(project) ?: return
    problemsViewPanel.show {
      tab.manager?.setSelectedContent(tab)
    }
  }

  /**
   * Get the issue panel for the given [DesignSurface], if any.
   */
  fun getIssuePanel(surface: DesignSurface): IssuePanel? {
    if (!StudioFlags.NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS.get()) {
      return surface.issuePanel
    }
    return null
  }

  fun getSelectedSharedIssuePanel(): DesignerCommonIssuePanel? {
    return if (sharedIssueTab?.isSelected == true) sharedIssuePanel else null
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): IssuePanelService = project.getService(IssuePanelService::class.java)
  }
}

/**
 * Sets the status of the issue panel.
 * @param show wether to show or hide the issue panel.
 * @param userInvoked if true, this was the direct consequence of a user action.
 */
private fun DesignSurface.setShowIssuePanel(show: Boolean, userInvoked: Boolean) {
  UIUtil.invokeLaterIfNeeded {
    issuePanel.isMinimized = !show
    if (userInvoked) {
      issuePanel.disableAutoSize()
    }
    revalidate()
    repaint()
  }
}

/**
 * This is same as [com.intellij.analysis.problemsView.toolWindow.ProblemsViewPanel.getName] for consistency.
 */
private fun createTabName(title: String, issueCount: Int?): String {
  val html = HtmlChunk.tag("font").attr("color", toHtmlColor(UIUtil.getInactiveTextColor()))
  return HtmlBuilder().append(title)
    .append(" ")
    .append(if (issueCount != null) html.addText("$issueCount") else html)
    .wrapWithHtmlBody()
    .toString()
}
