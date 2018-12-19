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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_TEXT
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.idea.common.property2.api.PropertyItem
import com.android.tools.idea.common.property2.impl.model.util.TestAsyncPropertyItem
import com.android.tools.idea.common.property2.impl.model.util.TestPropertyItem
import com.android.tools.idea.uibuilder.property2.testutils.FakeInspectorLine
import com.android.tools.idea.uibuilder.property2.testutils.LineType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class TextFieldPropertyEditorModelTest {

  private fun createModel(): Pair<TextFieldPropertyEditorModel, ValueChangedListener> {
    val property = TestPropertyItem(ANDROID_URI, "text", "hello")
    property.defaultValue = "from"
    return createModel(property)
  }

  private fun createModel(property: PropertyItem): Pair<TextFieldPropertyEditorModel, ValueChangedListener> {
    val model = TextFieldPropertyEditorModel(property, true)
    val listener = mock(ValueChangedListener::class.java)
    model.addListener(listener)
    return Pair(model, listener)
  }

  @Test
  fun testValue() {
    val (model, _) = createModel()
    assertThat(model.value).isEqualTo("hello")
    assertThat(model.placeHolderValue).isEqualTo("from")
  }

  @Test
  fun testEnter() {
    val (model, listener) = createModel()
    val line = FakeInspectorLine(LineType.PROPERTY)
    model.lineModel = line
    model.text = "world"
    model.commit()
    assertThat(model.property.value).isEqualTo("world")
    verify(listener).valueChanged()
  }

  @Test
  fun testEscape() {
    val (model, listener) = createModel()
    model.escape()
    assertThat(model.property.value).isEqualTo("hello")
    verify(listener).valueChanged()
  }

  @Test
  fun testFocusLossWillUpdateValue() {
    // setup
    val (model, listener) = createModel()
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
    val (model, listener) = createModel()
    model.focusGained()

    // test
    model.focusLost()
    assertThat(model.property.value).isEqualTo("hello")
    verify(listener, never()).valueChanged()
  }

  @Test
  fun testEnterKeyWithAsyncPropertySetterDoesNotNavigateToNextEditor() {
    // setup
    val property = TestAsyncPropertyItem(ANDROID_URI, ATTR_ID, "textView")
    val (model, listener) = createModel(property)
    val line = FakeInspectorLine(LineType.PROPERTY)
    model.lineModel = line
    model.focusGained()
    model.text = "imageView"

    // test
    model.commit()
    assertThat(property.lastUpdatedValue).isEqualTo("imageView")
    assertThat(property.updateCount).isEqualTo(1)
    verify(listener).valueChanged()
  }

  @Test
  fun testFocusLossAfterEnterKeyWithAsyncPropertySetter() {
    // setup
    val property = TestPropertyItem(ANDROID_URI, ATTR_ID, "textView")
    val (model, listener) = createModel(property)
    model.focusGained()
    model.text = "imageView"
    model.commit()

    // test
    model.focusLost()
    assertThat(property.value).isEqualTo("imageView")
    assertThat(property.updateCount).isEqualTo(1)
    verify(listener).valueChanged()
  }

  @Test
  fun testUpdateAfterPropertyChange() {
    // setup
    val property = TestPropertyItem(ANDROID_URI, ATTR_TEXT, "Hello")
    val (model, listener) = createModel(property)

    // test
    property.value = "World"
    model.refresh()
    assertThat(model.text).isEqualTo("World")
    verify(listener).valueChanged()
  }
}
