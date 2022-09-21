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
package com.android.tools.idea.logcat.util

import com.android.annotations.concurrency.UiThread
import com.intellij.execution.impl.ConsoleViewUtil
import com.intellij.openapi.command.undo.UndoUtil
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorFactoryImpl
import com.intellij.openapi.project.Project

/**
 * Creates an Editor and initializes it.
 *
 * This code is based on [com.intellij.execution.impl.ConsoleViewImpl]
 */
fun createLogcatEditor(project: Project): EditorEx {
  val editorFactory = EditorFactory.getInstance()
  val document = (editorFactory as EditorFactoryImpl).createDocument(true)
  UndoUtil.disableUndoFor(document)
  val editor = editorFactory.createViewer(document, project, EditorKind.CONSOLE) as EditorEx
  val editorSettings = editor.settings
  editorSettings.isAllowSingleLogicalLineFolding = true
  editorSettings.isLineMarkerAreaShown = false
  editorSettings.isIndentGuidesShown = false
  editorSettings.isLineNumbersShown = false
  editorSettings.isFoldingOutlineShown = true
  editorSettings.isAdditionalPageAtBottom = false
  editorSettings.additionalColumnsCount = 0
  editorSettings.additionalLinesCount = 0
  editorSettings.isRightMarginShown = false
  editorSettings.isCaretRowShown = false
  editorSettings.isShowingSpecialChars = false
  editor.gutterComponentEx.isPaintBackground = false
  editor.colorsScheme = ConsoleViewUtil.updateConsoleColorScheme(editor.colorsScheme)

  return editor
}

/**
 * Returns true if the caret is at the bottom of the editor document.
 */
@UiThread
internal fun EditorEx.isCaretAtBottom() = document.let {
  it.getLineNumber(caretModel.offset) >= it.lineCount - 1
}

/**
 * Returns true if the editor vertical scroll position is at the bottom.
 */
@UiThread
internal fun EditorEx.isScrollAtBottom(useImmediatePosition: Boolean): Boolean {
  val scrollBar = scrollPane.verticalScrollBar
  val position = if (useImmediatePosition) scrollBar.value else scrollingModel.visibleAreaOnScrollingFinished.y
  return scrollBar.maximum - scrollBar.visibleAmount == position
}
