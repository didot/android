/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *I
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.uibuilder.property2.inspector

import com.android.SdkConstants.*
import com.android.tools.idea.common.property2.api.*
import com.android.tools.idea.uibuilder.model.PreferenceUtils
import com.android.tools.idea.uibuilder.property2.NelePropertyItem

/**
 * An [InspectorBuilder] for the [ATTR_ID] attribute shown on top in the Nele inspector.
 */
class IdInspectorBuilder(private val editorProvider: EditorProvider<NelePropertyItem>) : InspectorBuilder<NelePropertyItem> {

  private val menuTags = setOf(TAG_GROUP, TAG_ITEM, TAG_MENU)

  override fun attachToInspector(inspector: InspectorPanel, properties: PropertiesTable<NelePropertyItem>) {
    val property = properties.getOrNull(ANDROID_URI, ATTR_ID) ?: return
    if (!isApplicable(property)) return

    inspector.addEditor(editorProvider(property))
  }

  private fun isApplicable(property: NelePropertyItem): Boolean {
    return property.components.none { it.tagName in PreferenceUtils.VALUES || it.tagName in menuTags}
  }
}
