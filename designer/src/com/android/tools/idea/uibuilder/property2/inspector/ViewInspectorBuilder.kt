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
package com.android.tools.idea.uibuilder.property2.inspector

import com.android.SdkConstants.*
import com.android.tools.idea.common.property2.api.*
import com.android.tools.idea.uibuilder.api.CustomPanel
import com.android.tools.idea.uibuilder.api.ViewHandler
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import javax.swing.JPanel

/**
 * An [InspectorBuilder] for all widget attributes specified in its [ViewHandler].
 *
 * First the custom panel is shown if applicable, followed by the attributes
 * defined in the [ViewHandler] of the View.
 */
class ViewInspectorBuilder(project: Project, private val editorProvider: EditorProvider<NelePropertyItem>) :
    InspectorBuilder<NelePropertyItem> {
  private val viewHandlerManager = ViewHandlerManager.get(project)
  private val cachedCustomPanels = mutableMapOf<String, CustomPanel>()

  companion object {
    private val TAG_EXCEPTIONS = listOf(TEXT_VIEW, PROGRESS_BAR)
  }

  override fun resetCache() {
    cachedCustomPanels.clear()
  }

  override fun attachToInspector(inspector: InspectorPanel, properties: PropertiesTable<NelePropertyItem>) {
    val tagName = getTagName(properties) ?: return
    if (tagName in TAG_EXCEPTIONS) return
    val handler = viewHandlerManager.getHandler(tagName) ?: return

    val attributes = handler.inspectorProperties
    val custom = setupCustomPanel(tagName, properties)
    if (attributes.isEmpty() && custom == null) return

    val titleModel = inspector.addExpandableTitle(tagName.substring(tagName.lastIndexOf('.') + 1))

    if (custom != null) {
      inspector.addComponent(custom, titleModel)
    }

    for (propertyName in attributes) {
      val property = findProperty(propertyName, properties)
      if (property != null) {
        inspector.addEditor(editorProvider.createEditor(property), titleModel)
      }
    }
  }

  private fun findProperty(propertyName: String, properties: PropertiesTable<NelePropertyItem>): NelePropertyItem? {
    // TODO: Handle other namespaces
    val property = findPropertyByName(StringUtil.trimStart(propertyName, TOOLS_NS_NAME_PREFIX), properties)
    val isDesignProperty = propertyName.startsWith(TOOLS_NS_NAME_PREFIX)
    return if (isDesignProperty) property?.designProperty else property
  }

  private fun findPropertyByName(propertyName: String, properties: PropertiesTable<NelePropertyItem>): NelePropertyItem? {
    if (propertyName == ATTR_SRC) {
      val srcCompat = properties.getOrNull(AUTO_URI, ATTR_SRC_COMPAT)
      if (srcCompat != null) {
        return srcCompat
      }
    }
    // TODO: Handle other namespaces
    return properties.getOrNull(ANDROID_URI, propertyName)
           ?: properties.getOrNull(AUTO_URI, propertyName)
           ?: properties.getOrNull("", propertyName)
  }

  private fun getTagName(properties: PropertiesTable<NelePropertyItem>): String? {
    val property = properties.first ?: return null
    val tagName = property.components.firstOrNull()?.tagName ?: return null
    return if (property.components.any { it.tagName == tagName }) tagName else null
  }

  private fun setupCustomPanel(tagName: String, properties: PropertiesTable<NelePropertyItem>): JPanel? {
    val panel = cachedCustomPanels[tagName] ?: createCustomPanel(tagName)
    if (panel == DummyCustomPanel.INSTANCE) return null

    val property = properties.first ?: return null
    val component = property.components.singleOrNull() ?: return null
    panel.useComponent(component)
    return panel.panel
  }

  private fun createCustomPanel(tagName: String): CustomPanel {
    val handler = viewHandlerManager.getHandler(tagName)
    val panel = handler?.customPanel ?: DummyCustomPanel.INSTANCE
    cachedCustomPanels[tagName] = panel
    return panel
  }
}
