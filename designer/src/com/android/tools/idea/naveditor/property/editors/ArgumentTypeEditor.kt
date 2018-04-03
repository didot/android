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
package com.android.tools.idea.naveditor.property.editors

import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.common.property.editors.EnumEditor
import com.android.tools.idea.uibuilder.property.editors.NlEditingListener
import com.android.tools.idea.uibuilder.property.editors.support.EnumSupport
import com.android.tools.idea.uibuilder.property.editors.support.ValueWithDisplayString

// TODO: ideally this wouldn't be a separate editor, and EnumEditor could just get the EnumSupport from the property itself.
class ArgumentTypeEditor(listener: NlEditingListener = NlEditingListener.DEFAULT_LISTENER,
                         comboBox: CustomComboBox = CustomComboBox()) : EnumEditor(listener, comboBox, null, false, false) {

  override fun getEnumSupport(property: NlProperty): EnumSupport = ArgumentTypeEnumSupport(property)

  private class ArgumentTypeEnumSupport(property: NlProperty) : EnumSupport(property) {
    // TODO: this information should come from the nav runtime library once it's included there.
    private val values: Map<String?, ValueWithDisplayString> = linkedMapOf("string" to ValueWithDisplayString("string", "string"),
                                                                           "integer" to ValueWithDisplayString("integer", "integer"),
                                                                           "reference" to ValueWithDisplayString("reference", "reference"))

    override fun getAllValues(): List<ValueWithDisplayString> {
      return values.values.toList()
    }

    override fun createValue(editorValue: String): ValueWithDisplayString {
      return values.getOrDefault(editorValue, ValueWithDisplayString("inferred", null))
    }
  }
}