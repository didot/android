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
package com.android.tools.idea.uibuilder.property2.inspector

import com.android.tools.property.ptable2.PTableModel
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.FilteredPTableModel
import com.android.tools.property.panel.api.FilteredPTableModel.PTableModelFactory.alphabeticalSortOrder
import com.android.tools.property.panel.api.GroupSpec
import com.android.tools.property.panel.api.InspectorBuilder
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.api.PropertiesTable
import com.android.tools.property.panel.api.TableLineModel
import com.android.tools.property.panel.api.TableUIProvider
import com.android.tools.idea.uibuilder.property2.NelePropertiesModel
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.android.tools.idea.uibuilder.property2.inspector.groups.ConstraintGroup
import com.android.tools.idea.uibuilder.property2.inspector.groups.MarginGroup
import com.android.tools.idea.uibuilder.property2.inspector.groups.PaddingGroup
import com.android.tools.idea.uibuilder.property2.inspector.groups.ThemeGroup
import com.android.tools.idea.uibuilder.property2.support.NeleControlTypeProvider
import com.intellij.openapi.actionSystem.AnAction

private const val ALL_ATTRIBUTES_TITLE = "All Attributes"

class AllAttributesInspectorBuilder(private val model: NelePropertiesModel,
                               controlTypeProvider: NeleControlTypeProvider,
                               editorProvider: EditorProvider<NelePropertyItem>) : InspectorBuilder<NelePropertyItem> {

  private val allTableUIProvider = TableUIProvider.create(
    NelePropertyItem::class.java, controlTypeProvider, editorProvider)

  override fun attachToInspector(inspector: InspectorPanel, properties: PropertiesTable<NelePropertyItem>) {
    if (properties.isEmpty) {
      return
    }

    val allTableModel = FilteredPTableModel.create(model, { true }, alphabeticalSortOrder, createGroups(properties))
    addTable(inspector, ALL_ATTRIBUTES_TITLE, allTableModel, allTableUIProvider, searchable = true)
  }

  private fun createGroups(properties: PropertiesTable<NelePropertyItem>): List<GroupSpec<NelePropertyItem>> {
    val groups = mutableListOf<GroupSpec<NelePropertyItem>>()
    groups.add(PaddingGroup(properties))
    groups.add(MarginGroup(properties))
    groups.add(ConstraintGroup(properties))
    groups.add(ThemeGroup(model.facet, properties))
    return groups
  }
}

fun addTable(inspector: InspectorPanel,
             title: String,
             tableModel: PTableModel,
             uiProvider: TableUIProvider,
             vararg actions: AnAction,
             searchable: Boolean): TableLineModel {
  val titleModel = inspector.addExpandableTitle(title, false, *actions)
  return inspector.addTable(tableModel, searchable, uiProvider, titleModel)
}
