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

import com.android.tools.adtui.model.stdui.EditingSupport
import com.android.tools.idea.common.property2.api.NewPropertyItem
import kotlin.properties.Delegates

/**
 * Model of a text editor for editing a property name.
 */
class PropertyNameEditorModel(private val newProperty: NewPropertyItem) :
  TextFieldPropertyEditorModel(newProperty, true) {

  override var text by Delegates.observable(newProperty.name) { _, _, _ -> pendingValueChange = false }

  override var value: String
    get() = newProperty.name
    set(value) {
      newProperty.name = value
      refresh()
    }

  override val editingSupport: EditingSupport
    get() = newProperty.nameEditingSupport

  override fun isCurrentValue(text: String): Boolean {
    return newProperty.isSameProperty(text)
  }
}
