/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.ui.JBColor
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Editor to manage the resources of the project
 */
class ResourceExplorerEditor : UserDataHolderBase(), FileEditor {
  private val component = JPanel()

  init {
    component.background = JBColor.green
  }

  override fun getComponent(): JComponent = component

  override fun getPreferredFocusedComponent(): JComponent? = null

  override fun getName(): String = "Resource Explorer"

  override fun setState(state: FileEditorState) {
  }

  override fun isModified(): Boolean = false

  override fun isValid(): Boolean = true

  override fun selectNotify() {}

  override fun deselectNotify() {}

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

  override fun getBackgroundHighlighter(): BackgroundEditorHighlighter? = null

  override fun getCurrentLocation(): FileEditorLocation? = null

  override fun dispose() {
  }
}
