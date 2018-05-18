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
package com.android.tools.idea.common.property2.api

import com.android.tools.adtui.ptable2.PTableModel
import com.android.tools.idea.common.property2.impl.model.TableLineModel
import com.android.tools.idea.common.property2.impl.ui.TableEditor
import javax.swing.JComponent

/**
 * Interface of an inspector panel.
 *
 * The inspector panel is generated from [InspectorBuilder]s that each add
 * rows to the inspector. A row can be either:
 *   - Title row
 *   - Label & Editor Component
 *   - A custom panel
 *   - A separator line
 */
interface InspectorPanel {

  /**
   * Add a title line to the inspector.
   */
  fun addTitle(title: String): InspectorLineModel

  /**
   * Add an expandable title line to the inspector.
   */
  fun addExpandableTitle(title: String, initiallyExpanded: Boolean = true): InspectorLineModel {
    val line = addTitle(title)
    line.makeExpandable(initiallyExpanded)
    return line
  }

  /**
   * Add an editor component with the property name for the label to the inspector.
   *
   * Add [editor] with model [editorModel] to the inspector, and return the new [InspectorLineModel].
   */
  fun addEditor(editorModel: PropertyEditorModel, editor: JComponent): InspectorLineModel

  /**
   * Add an editor component with the property name for the label to the inspector.
   *
   * Same as [addEditor], but takes the model and editor as a pair.
   * Add an editor with a model as a [modelEditorPair] to the inspector, and return the new [InspectorLineModel].
   */
  fun addEditor(modelEditorPair: Pair<PropertyEditorModel, JComponent>): InspectorLineModel {
    return addEditor(modelEditorPair.first, modelEditorPair.second)
  }

  /**
   * Add a table of properties to the inspector.
   */
  fun addTable(tableModel: PTableModel): InspectorLineModel

  /**
   * Adds a custom panel that spans the entire width.
   *
   * Add a [component] (usually a JPanel) to the inspector, and return the new [InspectorLineModel].
   */
  fun addComponent(component: JComponent): InspectorLineModel

  /**
   * Add a separator line to the inspector.
   */
  fun addSeparator(): InspectorLineModel
}
