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
package com.android.tools.idea.common.property2.impl.model

import com.android.SdkConstants
import com.android.SdkConstants.ATTR_VISIBILITY
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.idea.common.property2.api.EnumSupport
import com.android.tools.idea.common.property2.impl.model.util.TestAction
import com.android.tools.idea.common.property2.impl.model.util.TestEnumSupport
import com.android.tools.idea.common.property2.impl.model.util.TestPropertyItem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property2.testutils.LineType
import com.android.tools.idea.uibuilder.property2.testutils.FakeInspectorLine
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class ComboBoxPropertyEditorModelTest {

  @JvmField @Rule
  val projectRule = AndroidProjectRule.inMemory()

  private fun createModel(): ComboBoxPropertyEditorModel {
    return createModel(TestEnumSupport("visible", "invisible", "gone"))
  }

  private fun createModel(enumSupport: EnumSupport): ComboBoxPropertyEditorModel {
    val property = TestPropertyItem(SdkConstants.ANDROID_URI, ATTR_VISIBILITY, "visible")
    return ComboBoxPropertyEditorModel(property, enumSupport, true)
  }

  private fun createModelWithListener(): Pair<ComboBoxPropertyEditorModel, ValueChangedListener> {
    val model = createModel()
    val listener = mock(ValueChangedListener::class.java)
    model.addListener(listener)
    return model to listener
  }

  @Test
  fun testSelectedItemFromInit() {
    val model = createModel()
    model.popupMenuWillBecomeVisible()
    assertThat(model.selectedItem.toString()).isEqualTo("visible")
  }

  @Test
  fun testSelectItemIsKeptAfterFocusLoss() {
    val model = createModel()
    model.isPopupVisible = true
    model.selectedItem = "gone"

    model.popupMenuWillBecomeInvisible(false)
    model.focusLost()

    assertThat(model.property.value).isEqualTo("gone")
  }

  @Test
  fun testEnter() {
    val (model, listener) = createModelWithListener()
    val line = FakeInspectorLine(LineType.PROPERTY)
    model.lineModel = line
    model.text = "gone"
    model.enterKeyPressed()
    assertThat(model.property.value).isEqualTo("gone")
    assertThat(model.isPopupVisible).isFalse()
    verify(listener).valueChanged()
  }

  @Test
  fun testEscape() {
    // setup
    val model = createModel()
    model.isPopupVisible = true
    model.selectedItem = "gone"
    val listener = mock(ValueChangedListener::class.java)
    model.addListener(listener)

    // test
    model.escapeKeyPressed()
    assertThat(model.property.value).isEqualTo("visible")
    assertThat(model.isPopupVisible).isFalse()
    verify(listener).valueChanged()
  }

  @Test
  fun testEnterInPopup() {
    // setup
    val model = createModel()
    model.isPopupVisible = true
    model.selectedItem = "gone"
    val listener = mock(ValueChangedListener::class.java)
    model.addListener(listener)

    // test
    model.popupMenuWillBecomeInvisible(false)
    assertThat(model.property.value).isEqualTo("gone")
    assertThat(model.isPopupVisible).isFalse()
    verify(listener).valueChanged()
  }

  @Test
  fun testEnterInPopupOnAction() {
    // setup
    val action = TestAction("testAction")
    val enumSupport = TestEnumSupport("visible", "invisible", action = action)
    val model = createModel(enumSupport)
    model.isPopupVisible = true
    model.selectedItem = enumSupport.values.last()
    val listener = mock(ValueChangedListener::class.java)
    model.addListener(listener)

    // test
    model.popupMenuWillBecomeInvisible(false)
    assertThat(action.actionPerformedCount).isEqualTo(1)
    assertThat(model.property.value).isEqualTo("visible")
    assertThat(model.isPopupVisible).isFalse()
    verify(listener).valueChanged()
  }

  @Test
  fun testEscapeInPopup() {
    // setup
    val model = createModel()
    model.isPopupVisible = true
    model.selectedItem = "gone"
    val listener = mock(ValueChangedListener::class.java)
    model.addListener(listener)

    // test
    model.popupMenuWillBecomeInvisible(true)
    assertThat(model.property.value).isEqualTo("visible")
    assertThat(model.isPopupVisible).isFalse()
    verifyZeroInteractions(listener)
  }

  @Test
  fun testListModel() {
    val model = createModel()
    assertThat(model.size).isEqualTo(3)
    assertThat(model.getElementAt(0).toString()).isEqualTo("visible")
    assertThat(model.getElementAt(1).toString()).isEqualTo("invisible")
    assertThat(model.getElementAt(2).toString()).isEqualTo("gone")
  }

  @Test
  fun testFocusLossWillUpdateValue() {
    // setup
    val (model, listener) = createModelWithListener()
    model.focusGained()
    model.text = "#333333"

    // test
    model.focusLost()
    assertThat(model.hasFocus).isFalse()
    assertThat(model.property.value).isEqualTo("#333333")
    verify(listener).valueChanged()
  }

  @Test
  fun testFocusLossWithUnchangedValueWillNotUpdateValue() {
    // setup
    val (model, listener) = createModelWithListener()
    model.focusGained()

    // test
    model.focusLost()
    assertThat(model.property.value).isEqualTo("visible")
    verify(listener, never()).valueChanged()
  }

  @Test
  fun testListenersAreConcurrentModificationSafe() {
    // Make sure that ConcurrentModificationException is NOT generated from the code below:
    val model = createModel()
    val listener = RecursiveListDataListener(model)
    model.addListDataListener(listener)
    model.selectedItem = "text"
    assertThat(listener.called).isTrue()
  }

  private class RecursiveListDataListener(private val model: ComboBoxPropertyEditorModel): ListDataListener {
    var called = false

    override fun intervalRemoved(event: ListDataEvent) {
    }

    override fun intervalAdded(event: ListDataEvent) {
    }

    override fun contentsChanged(event: ListDataEvent) {
      model.addListDataListener(RecursiveListDataListener(model))
      called = true
    }
  }
}
