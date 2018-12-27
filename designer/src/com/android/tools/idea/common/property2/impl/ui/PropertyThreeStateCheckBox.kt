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
package com.android.tools.idea.common.property2.impl.ui

import com.android.SdkConstants
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.adtui.stdui.registerKeyAction
import com.android.tools.idea.common.property2.impl.model.KeyStrokes
import com.android.tools.idea.common.property2.impl.model.ThreeStateBooleanPropertyEditorModel
import com.android.tools.idea.common.property2.impl.support.EditorFocusListener
import com.intellij.util.ui.ThreeStateCheckBox
import icons.StudioIcons

/**
 * A standard control for editing a boolean property value with 3 states: on/off/unset.
 */
class PropertyThreeStateCheckBox(private val propertyModel: ThreeStateBooleanPropertyEditorModel) : ThreeStateCheckBox() {
  private var stateChangeFromModel = false

  init {
    icon = StudioIcons.LayoutEditor.Properties.TEXT_ALIGN_CENTER
    state = toThreeStateValue(propertyModel.value)
    registerKeyAction({ propertyModel.f1KeyPressed() }, KeyStrokes.f1, "help")
    registerKeyAction({ propertyModel.shiftF1KeyPressed() }, KeyStrokes.shiftF1, "help2")
    registerKeyAction({ propertyModel.browseButtonPressed() }, KeyStrokes.browse, "browse")

    propertyModel.addListener(ValueChangedListener { handleValueChanged() })
    addFocusListener(EditorFocusListener(this, propertyModel))
    addPropertyChangeListener { event ->
      if (!stateChangeFromModel && event.propertyName == THREE_STATE_CHECKBOX_STATE) {
        propertyModel.value = fromThreeStateValue(event.newValue)
      }
    }
    PropertyTextField.addBorderAtTextFieldBorderSize(this)
  }

  private fun handleValueChanged() {
    stateChangeFromModel = true
    try {
      state = toThreeStateValue(propertyModel.value)
    }
    finally {
      stateChangeFromModel = false
    }
    isVisible = propertyModel.visible
    if (propertyModel.focusRequest && !isFocusOwner) {
      requestFocusInWindow()
    }
  }

  override fun getToolTipText(): String? {
    return propertyModel.tooltip
  }

  private fun toThreeStateValue(value: String?) =
    when (value) {
      "", null -> ThreeStateCheckBox.State.DONT_CARE
      SdkConstants.VALUE_TRUE -> ThreeStateCheckBox.State.SELECTED
      else -> ThreeStateCheckBox.State.NOT_SELECTED
    }

  private fun fromThreeStateValue(value: Any?) =
    when (value) {
      ThreeStateCheckBox.State.SELECTED -> SdkConstants.VALUE_TRUE
      ThreeStateCheckBox.State.NOT_SELECTED -> SdkConstants.VALUE_FALSE
      else -> ""
    }
}
