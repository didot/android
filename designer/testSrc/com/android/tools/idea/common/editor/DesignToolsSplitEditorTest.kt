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

import com.android.testutils.MockitoKt.any
import com.android.tools.idea.common.analytics.CommonUsageTracker
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.LayoutEditorEvent
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.KeyStroke

class DesignToolsSplitEditorTest : AndroidTestCase() {

  private lateinit var splitEditor : DesignToolsSplitEditor
  private lateinit var textEditor : TextEditor
  private lateinit var designerEditor : DesignerEditor

  override fun setUp() {
    super.setUp()
    StudioFlags.NELE_SPLIT_EDITOR.override(true)
    val panel = mock(DesignerEditorPanel::class.java)
    `when`(panel.surface).thenReturn(NlDesignSurface.build(project, testRootDisposable))
    designerEditor = mock(DesignerEditor::class.java)
    `when`(designerEditor.component).thenReturn(panel)
    val textEditorComponent = mock(JComponent::class.java)
    textEditor = mock(TextEditor::class.java)
    `when`(textEditor.component).thenReturn(textEditorComponent)
    `when`(textEditor.file).thenReturn(mock(VirtualFile::class.java))
    val component = mock(JComponent::class.java)
    `when`(component.getActionForKeyStroke(any(KeyStroke::class.java))).thenCallRealMethod()
    splitEditor = object : DesignToolsSplitEditor(textEditor, designerEditor, "testEditor", project) {
      // The fact that we have to call registerModeNavigationShortcuts here repeating the behavior in SplitEditor is incorrect
      // and should be fixed. However, we can not use the original getComponent method since it calls getComponent of
      // TextEditorWithPreview which fails with a NullPointerException in testing environment. This test, however, has a value
      // because we test that registerModeNavigationShortcuts does the right thing.
      // TODO(b/146150328)
      private var registeredShortcuts = false
      override fun getComponent(): JComponent {
        if (!registeredShortcuts) {
          registeredShortcuts = true
          registerModeNavigationShortcuts(component)
        }
        return component
      }
    }
    CommonUsageTracker.NOP_TRACKER.resetLastTrackedEvent()
  }

  fun testTrackingModeChange() {
    assertThat(CommonUsageTracker.NOP_TRACKER.lastTrackedEvent).isNull()
    var triggerExplicitly = false
    splitEditor.selectTextMode(triggerExplicitly)
    // We don't track change mode events when users don't trigger them explicitly
    assertThat(CommonUsageTracker.NOP_TRACKER.lastTrackedEvent).isNull()

    triggerExplicitly = true
    splitEditor.selectTextMode(triggerExplicitly)
    assertThat(CommonUsageTracker.NOP_TRACKER.lastTrackedEvent).isEqualTo(LayoutEditorEvent.LayoutEditorEventType.SELECT_TEXT_MODE)

    splitEditor.selectDesignMode(triggerExplicitly)
    assertThat(CommonUsageTracker.NOP_TRACKER.lastTrackedEvent).isEqualTo(LayoutEditorEvent.LayoutEditorEventType.SELECT_VISUAL_MODE)

    splitEditor.selectSplitMode(triggerExplicitly)
    assertThat(CommonUsageTracker.NOP_TRACKER.lastTrackedEvent).isEqualTo(LayoutEditorEvent.LayoutEditorEventType.SELECT_SPLIT_MODE)
  }

  fun testModeChange() {
    var triggerExplicitly = true
    splitEditor.selectTextMode(triggerExplicitly)
    assertThat(splitEditor.isTextMode()).isTrue()

    triggerExplicitly = false
    // We change mode even when users don't trigger it explicitly, e.g. when jumping to XML definition
    splitEditor.selectDesignMode(triggerExplicitly)
    assertThat(splitEditor.isDesignMode()).isTrue()

    splitEditor.selectSplitMode(triggerExplicitly)
    assertThat(splitEditor.isSplitMode()).isTrue()
  }

  fun testFileIsDelegateToTextEditor() {
    val splitEditorFile = splitEditor.file!!
    assertThat(splitEditorFile).isEqualTo(textEditor.file)
  }

  fun testKeyboardShortcuts() {
    val modifiers = (if (SystemInfo.isMac) InputEvent.CTRL_DOWN_MASK else InputEvent.ALT_DOWN_MASK) or InputEvent.SHIFT_DOWN_MASK
    val focusManager = mock(KeyboardFocusManager::class.java)
    val component = splitEditor.component
    `when`(focusManager.focusOwner).thenReturn(component)
    KeyboardFocusManager.setCurrentKeyboardFocusManager(focusManager)
    val dispatcher = IdeKeyEventDispatcher(null)

    splitEditor.selectSplitMode(true)
    // The circular sequence is ... Code <-> Split <-> Design <-> Code <-> Split <-> Design <-> Code ...
    dispatcher.dispatchKeyEvent(KeyEvent(splitEditor.component, KeyEvent.KEY_PRESSED, 0, modifiers, KeyEvent.VK_LEFT))
    assertThat(splitEditor.isTextMode()).isTrue()

    dispatcher.dispatchKeyEvent(KeyEvent(splitEditor.component, KeyEvent.KEY_PRESSED, 0, modifiers, KeyEvent.VK_LEFT))
    assertThat(splitEditor.isDesignMode()).isTrue()

    dispatcher.dispatchKeyEvent(KeyEvent(splitEditor.component, KeyEvent.KEY_PRESSED, 0, modifiers, KeyEvent.VK_LEFT))
    assertThat(splitEditor.isSplitMode()).isTrue()

    dispatcher.dispatchKeyEvent(KeyEvent(splitEditor.component, KeyEvent.KEY_PRESSED, 0, modifiers, KeyEvent.VK_RIGHT))
    assertThat(splitEditor.isDesignMode()).isTrue()

    dispatcher.dispatchKeyEvent(KeyEvent(splitEditor.component, KeyEvent.KEY_PRESSED, 0, modifiers, KeyEvent.VK_RIGHT))
    assertThat(splitEditor.isTextMode()).isTrue()

    dispatcher.dispatchKeyEvent(KeyEvent(splitEditor.component, KeyEvent.KEY_PRESSED, 0, modifiers, KeyEvent.VK_RIGHT))
    assertThat(splitEditor.isSplitMode()).isTrue()
  }

  override fun tearDown() {
    super.tearDown()
    StudioFlags.NELE_SPLIT_EDITOR.clearOverride()
  }
}
