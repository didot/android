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
import com.android.tools.idea.common.model.NlComponent
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
class ViewInspectorBuilder(project: Project, private val editorProvider: EditorProvider<NelePropertyItem>) {
  private val viewHandlerManager = ViewHandlerManager.get(project)
  private val cachedCustomPanels = mutableMapOf<String, CustomPanel>()

  companion object {
    private val TAG_EXCEPTIONS = listOf(TEXT_VIEW, PROGRESS_BAR)
  }

  fun resetCache() {
    cachedCustomPanels.clear()
  }

  fun attachToInspector(inspector: InspectorPanel, properties: PropertiesTable<NelePropertyItem>, getTitleLine: () -> InspectorLineModel) {
    val tagName = getTagName(properties) ?: return
    if (tagName in TAG_EXCEPTIONS) return
    val firstComponent = getFirstComponent(properties) ?: return
    val handler = viewHandlerManager.getHandler(firstComponent) ?: return

    val attributes = handler.inspectorProperties
    val custom = setupCustomPanel(tagName, properties)
    if (attributes.isEmpty() && custom == null) return

    val titleLine = getTitleLine()

    if (custom != null) {
      inspector.addComponent(custom, titleLine)
    }

    val effectiveAttributes = addCommonAttributes(attributes, properties)

    for (propertyName in effectiveAttributes) {
      val property = findProperty(propertyName, properties)
      if (property != null) {
        inspector.addEditor(editorProvider.createEditor(property), titleLine)
      }
    }
  }

  private fun findProperty(propertyName: String, properties: PropertiesTable<NelePropertyItem>): NelePropertyItem? {
    // TODO: Handle other namespaces
    val attrName = StringUtil.trimStart(propertyName, TOOLS_NS_NAME_PREFIX)
    val property = findPropertyByName(attrName, properties)
    val isDesignProperty = propertyName.startsWith(TOOLS_NS_NAME_PREFIX)
    return if (isDesignProperty) property?.designProperty ?: properties.getOrNull(TOOLS_URI, attrName) else property
  }

  private fun findPropertyByName(attrName: String, properties: PropertiesTable<NelePropertyItem>): NelePropertyItem? {
    if (attrName == ATTR_SRC) {
      val srcCompat = properties.getOrNull(AUTO_URI, ATTR_SRC_COMPAT)
      if (srcCompat != null) {
        return srcCompat
      }
    }
    // TODO: Handle other namespaces
    return properties.getOrNull(ANDROID_URI, attrName)
           ?: properties.getOrNull(AUTO_URI, attrName)
           ?: properties.getOrNull("", attrName)
  }

  private fun getFirstComponent(properties: PropertiesTable<NelePropertyItem>): NlComponent? {
    return properties.first?.components?.firstOrNull()
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

  private fun addCommonAttributes(attributes: List<String>, properties: PropertiesTable<NelePropertyItem>): List<String> {
    if (attributes.contains(ATTR_VISIBILITY) && TextViewInspectorBuilder.isApplicable(properties)) {
      return attributes
    }
    val modified = mutableListOf<String>()
    modified.addAll(attributes)
    modified.add(ATTR_VISIBILITY)
    return modified
  }
}
