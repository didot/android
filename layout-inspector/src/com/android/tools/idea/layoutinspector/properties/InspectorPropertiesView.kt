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
package com.android.tools.idea.layoutinspector.properties

import com.android.tools.layoutinspector.proto.LayoutInspectorProto.Property.Type
import com.android.tools.property.panel.api.ControlType
import com.android.tools.property.panel.api.ControlTypeProvider
import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumSupportProvider
import com.android.tools.property.panel.api.PropertiesView
import com.android.tools.property.panel.api.Watermark
import com.android.tools.property.ptable2.PTableItem
import org.jetbrains.android.formatter.AttributeComparator

private const val VIEW_NAME = "LayoutInspectorPropertyEditor"
private const val WATERMARK_MESSAGE = "No view selected."
private const val WATERMARK_ACTION_MESSAGE = "Select a view in the Component Tree."

/**
 * Comparator that is sorting [PTableItem] in Android sorting order.
 * This implies layout attributes first and layout_width before layout_height.
 */
val androidSortOrder: Comparator<PTableItem> = AttributeComparator { it.name }

class InspectorPropertiesView(model: InspectorPropertiesModel) : PropertiesView<InspectorPropertyItem>(VIEW_NAME, model) {

  private val enumSupportProvider = object : EnumSupportProvider<InspectorPropertyItem> {
    // TODO: Make this a 1 liner
    override fun invoke(property: InspectorPropertyItem): EnumSupport? = null
  }

  private val controlTypeProvider = object : ControlTypeProvider<InspectorPropertyItem> {
    override fun invoke(property: InspectorPropertyItem): ControlType =
      when (property.type) {
        Type.DRAWABLE,
        Type.COLOR -> ControlType.COLOR_EDITOR
        else -> ControlType.TEXT_EDITOR
      }
  }

  init {
    watermark = Watermark(WATERMARK_MESSAGE, WATERMARK_ACTION_MESSAGE, "")
    main.builders.add(SelectedViewBuilder(model))
    val tab = addTab("")
    tab.builders.add(DimensionBuilder(model))
    tab.builders.add(InspectorTableBuilder("Declared Attributes", { it.group == PropertySection.DECLARED },
                                           model, enumSupportProvider, controlTypeProvider))
    tab.builders.add(InspectorTableBuilder("Layout", { it.group == PropertySection.LAYOUT },
                                           model, enumSupportProvider, controlTypeProvider, androidSortOrder))
    tab.builders.add(InspectorTableBuilder("All Attributes", { true }, model, enumSupportProvider, controlTypeProvider, searchable = true))
  }
}
