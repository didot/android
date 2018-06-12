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
package com.android.tools.idea.common.property2.impl.model.util

import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.ANDROID_URI
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.idea.common.property2.api.*
import com.android.tools.idea.common.property2.impl.support.ItemEnumValue
import com.android.tools.idea.common.property2.impl.ui.EnumValueListCellRenderer
import icons.StudioIcons
import javax.swing.Icon
import com.google.common.base.Joiner
import com.google.common.base.Splitter
import javax.swing.ListCellRenderer

object PropertyModelTestUtil {

  interface TestPropertyItem : PropertyItem {
    override var resolvedValue: String?
  }

  interface TestPropertyEditorModel : PropertyEditorModel {
    val focusWasRequested: Boolean
    val toggleCount: Int
  }

  fun makeProperty(namespace: String, name: String, initialValue: String?): TestPropertyItem {
    return object : TestPropertyItem {

      override val namespace: String
        get() = namespace

      override val namespaceIcon: Icon?
        get() = if (namespace == TOOLS_URI) StudioIcons.LayoutEditor.Properties.DESIGN_PROPERTY else null

      override val name: String
        get() = name

      override var value: String? = initialValue
        set(value) {
          field = value
          resolvedValue = value
        }

      override var resolvedValue: String? = initialValue

      override val isReference: Boolean
        get() = false
    }
  }

  fun makeFlagsProperty(propertyName: String, flagNames: List<String>, values: List<Int>): FlagsPropertyItem<FlagPropertyItem> {
    require(flagNames.size == values.size)
    val property = object : FlagsPropertyItem<FlagPropertyItem> {
      override val namespace = ANDROID_URI
      override val name = propertyName
      override val children = mutableListOf<FlagPropertyItem>()
      override fun flag(itemName: String): FlagPropertyItem? = children.firstOrNull { it.name == itemName }
      override var value: String? = null
      override var resolvedValue: String? = null
      override var isReference = false
      override val maskValue: Int
        get() {
          var mask = 0
          children.filter { valueAsSet.contains(it.name) }.forEach { mask = mask or it.maskValue }
          return mask
        }
      val valueAsSet: HashSet<String>
        get() {
          val value = value ?: return HashSet()
          return HashSet(Splitter.on("|").trimResults().splitToList(value))
        }
    }

    for (index in flagNames.indices) {
      property.children.add(object : FlagPropertyItem {
        override val flags = property
        override val namespace = ANDROID_URI
        override val name = flagNames[index]
        override val maskValue = values[index]
        override var actualValue: Boolean
          get() = flags.valueAsSet.contains(name)
          set(value) {
            val set = flags.valueAsSet
            if (value) {
              set.add(name)
            }
            else {
              set.remove(name)
            }
            flags.value = Joiner.on("|").join(set)
          }
        override val effectiveValue: Boolean
          get() = (flags.maskValue and maskValue) == maskValue
        override var value: String?
          get() = if (actualValue) "true" else "false"
          set(value) { actualValue = (value == "true") }
        override var resolvedValue: String? = null
        override val isReference = false
      })
    }
    return property
  }

  fun makeEnumSupport(vararg values: String): EnumSupport {
    return object: EnumSupport {
      override val values: List<EnumValue> = values.map { ItemEnumValue(it) }

      override val renderer: ListCellRenderer<EnumValue> by lazy {
        EnumValueListCellRenderer()
      }
    }
  }

  fun makePropertyEditorModel(propertyItem: PropertyItem): TestPropertyEditorModel {
    return object: TestPropertyEditorModel {
      override var property = propertyItem

      override var value: String = property.value ?: ""

      override var visible = true

      override var focusWasRequested = false
        private set

      override var toggleCount = 0
        private set

      override var onEnter = {}

      override fun cancelEditing() {}

      override fun requestFocus() {
        focusWasRequested = true
      }

      override fun toggleValue() {
        toggleCount++
      }

      override val hasFocus = false

      override var lineModel: InspectorLineModel? = null

      override fun refresh() {
        value = property.value ?: ""
      }

      override fun addListener(listener: ValueChangedListener) {
        throw NotImplementedError()
      }

      override fun removeListener(listener: ValueChangedListener) {
        throw NotImplementedError()
      }
    }
  }
}
