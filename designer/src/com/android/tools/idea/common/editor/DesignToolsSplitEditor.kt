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
package com.android.tools.idea.common.editor

import com.android.tools.idea.common.surface.DesignSurface
import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.codeHighlighting.HighlightingPass
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.Project
import org.jetbrains.android.uipreview.AndroidEditorSettings
import javax.swing.JComponent

private const val SPLIT_MODE_PROPERTY_PREFIX = "SPLIT_EDITOR_MODE"

/**
 * [SplitEditor] whose preview is a [DesignerEditor] and [getTextEditor] contains the corresponding XML file displayed in the preview.
 */
open class DesignToolsSplitEditor(textEditor: TextEditor,
                                  val designerEditor: DesignerEditor,
                                  editorName: String,
                                  private val project: Project)
  : SplitEditor<DesignerEditor>(textEditor, designerEditor, editorName, defaultLayout(designerEditor)) {

  private val propertiesComponent = PropertiesComponent.getInstance()

  private val backgroundEditorHighlighter = CompoundBackgroundHighlighter()

  private var textViewToolbarAction: MyToolBarAction? = null

  private var splitViewToolbarAction: MyToolBarAction? = null

  private var designViewToolbarAction: MyToolBarAction? = null

  private val modePropertyName: String?
    get() {
      val file = file ?: return null
      return String.format("%s_%s", SPLIT_MODE_PROPERTY_PREFIX, file.path)
    }

  private var stateRestored = false

  override fun getComponent(): JComponent {
    if (!stateRestored) {
      stateRestored = true
      restoreSurfaceState()
    }
    return super.getComponent()
  }

  private fun restoreSurfaceState() {
    (getSelectedAction() as? MyToolBarAction)?.let { designerEditor.component.surface.state = it.surfaceState }
  }

  override fun getBackgroundHighlighter(): BackgroundEditorHighlighter {
    return backgroundEditorHighlighter
  }

  override fun selectNotify() {
    super.selectNotify()
    // select/deselectNotify will be called when the user selects (clicks) or opens a new editor. However, in some cases, the editor might
    // be deselected but still visible. We first check whether we should pay attention to the select/deselect so we only do something if we
    // are visible.
    if (FileEditorManager.getInstance(project).selectedEditors.contains(this)) {
      designerEditor.component.activate()
    }
  }

  override fun deselectNotify() {
    super.deselectNotify()
    // If we are still visible but the user deselected us, do not deactivate the model since we still need to receive updates.
    if (!FileEditorManager.getInstance(project).selectedEditors.contains(this)) {
      designerEditor.component.deactivate()
    }
  }

  override fun getShowEditorAction(): SplitEditorAction {
    if (textViewToolbarAction == null) {
      textViewToolbarAction = MyToolBarAction(super.getShowEditorAction(), DesignSurface.State.DEACTIVATED)
    }
    return textViewToolbarAction!!
  }

  override fun getShowEditorAndPreviewAction(): SplitEditorAction {
    if (splitViewToolbarAction == null) {
      splitViewToolbarAction = MyToolBarAction(super.getShowEditorAndPreviewAction(), DesignSurface.State.SPLIT)
    }
    return splitViewToolbarAction!!
  }

  override fun getShowPreviewAction(): SplitEditorAction {
    if (designViewToolbarAction == null) {
      designViewToolbarAction = MyToolBarAction(super.getShowPreviewAction(), DesignSurface.State.FULL)
    }
    return designViewToolbarAction!!
  }

  /**
   * Persist the mode in order to restore it next time we open the editor.
   */
  private fun setModeProperty(state: DesignSurface.State) = modePropertyName?.let { propertiesComponent.setValue(it, state.name) }

  override fun getState(level: FileEditorStateLevel): FileEditorState {
    // Override getState to make sure getState(FileEditorStateLevel.UNDO) works properly, otherwise we'd be defaulting to the implementation
    // of TextEditorWithPreview#getState, which returns a new instance every time, causing issues in undoing the editor's state because it
    // will return a different state even if nothing relevant has changed. Consequently, we need to implement setState below to make sure we
    // restore the selected action when reopening this editor, which was previously taken care by TextEditorWithPreview#setState, but with a
    // logic too tied to its getState implementation.
    return myEditor.getState(level)
  }

  override fun setState(state: FileEditorState) {
    // Restore the surface mode persisted.
    val propertyName = modePropertyName
    var propertyValue: String? = null
    if (propertyName != null) {
      propertyValue = propertiesComponent.getValue(propertyName)
    }

    if (propertyValue == null) {
      return
    }
    // Select the action saved if the mode saved is different than the current one.
    val surfaceState = DesignSurface.State.valueOf(propertyValue)
    if (surfaceState == designerEditor.component.surface.state) {
      return
    }
    actions.firstOrNull { it is MyToolBarAction && it.surfaceState == surfaceState }?.let { selectAction(it, false) }
  }

  private inner class MyToolBarAction internal constructor(delegate: SplitEditorAction, internal val surfaceState: DesignSurface.State)
    : SplitEditor<DesignerEditor>.SplitEditorAction(delegate.name, delegate.icon, delegate.delegate) {

    override fun setSelected(e: AnActionEvent, state: Boolean, userExplicitlySelected: Boolean) {
      designerEditor.component.surface.state = surfaceState
      setModeProperty(surfaceState)
      super.setSelected(e, state, userExplicitlySelected)
    }

    override fun onUserSelectedAction() {
      // We only want to track actions when users explicitly trigger them, i.e. when they click on the action to change the mode. An example
      // of indirectly changing the mode is triggering "Go to XML" when in design-only mode, as we change the mode to text-only.
      designerEditor.component.surface.analyticsManager.trackSelectEditorMode()
    }
  }

  private inner class CompoundBackgroundHighlighter : BackgroundEditorHighlighter {
    override fun createPassesForEditor(): Array<HighlightingPass> {
      val designEditorPasses = designerEditor.backgroundHighlighter.createPassesForEditor()
      val textEditorHighlighter = myEditor.backgroundHighlighter
      val textEditorPasses = textEditorHighlighter?.createPassesForEditor() ?: HighlightingPass.EMPTY_ARRAY
      return designEditorPasses + textEditorPasses
    }

    override fun createPassesForVisibleArea(): Array<HighlightingPass> {
      // BackgroundEditorHighlighter#createPassesForVisibleArea is deprecated and not used, so we can safely return an empty array here.
      return HighlightingPass.EMPTY_ARRAY
    }
  }
}

private fun defaultLayout(designerEditor: DesignerEditor) = when(designerEditor.component.surface.state) {
  DesignSurface.State.FULL -> TextEditorWithPreview.Layout.SHOW_PREVIEW
  DesignSurface.State.SPLIT -> TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW
  DesignSurface.State.DEACTIVATED -> TextEditorWithPreview.Layout.SHOW_EDITOR
}
