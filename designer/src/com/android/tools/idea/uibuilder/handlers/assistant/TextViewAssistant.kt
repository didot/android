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
package com.android.tools.idea.uibuilder.handlers.assistant

import com.android.SdkConstants.*
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.adtui.model.stdui.CommonComboBoxModel
import com.android.tools.adtui.model.stdui.DefaultCommonComboBoxModel
import com.android.tools.adtui.stdui.CommonComboBox
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.res.SampleDataResourceItem
import com.android.tools.idea.uibuilder.property.assistant.AssistantPopupPanel
import com.android.tools.idea.uibuilder.property.assistant.ComponentAssistantFactory.Context
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import java.awt.GridLayout
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

private const val NONE_VALUE = "None"

class TextViewAssistant(private val context: Context) : AssistantPopupPanel() {
  private val myComponent: NlComponent = context.component
  private val myOriginalTextValue: String?
  private val myAppResources = ResourceRepositoryManager.getAppResources(context.component.model.facet)

  private var myProject: Project = context.component.model.facet.module.project

  init {
    val mainPanel = JPanel(GridLayout(0, 1)).apply {
      isOpaque = false
      add(assistantLabel("Text"))

      val elements = listOf(NONE_VALUE) + myAppResources.items.get(ResourceNamespace.TOOLS, ResourceType.SAMPLE_DATA).values()
        .filterIsInstance<SampleDataResourceItem>()
        .filter {
          it.contentType == SampleDataResourceItem.ContentType.TEXT
        }
        .map {
          val reference = it.referenceToSelf
          // TODO: referenceToSelf.getResourceUrl does not return the correct prefix for the TOOLS namespace
          ResourceUrl.create(TOOLS_PREFIX, reference.resourceType, reference.name).toString()
        }
        .sorted()
        .toList()

      val existingToolsText = context.component.getAttribute(TOOLS_URI, ATTR_TEXT).orEmpty()
      val initialElement = if (existingToolsText.startsWith(TOOLS_SAMPLE_PREFIX)) {
        existingToolsText
      }
      else {
        NONE_VALUE
      }

      val model = DefaultCommonComboBoxModel(initialElement, elements)
      val combo = CommonComboBox<String, CommonComboBoxModel<String>>(model).apply {
        isOpaque = false
        isEditable = false
        selectedItem = initialElement
      }

      combo.addActionListener {
        onElementSelected(combo.selectedItem as String)
      }
      add(combo)
    }

    addContent(mainPanel)

    myOriginalTextValue = myComponent.getAttribute(TOOLS_URI, ATTR_TEXT)

    context.onClose = { cancelled: Boolean -> this.onClosed(cancelled) }
  }

  private fun onElementSelected(selectedItem: String?) {
    val attributeValue = if (NONE_VALUE == selectedItem || selectedItem.isNullOrEmpty()) null else selectedItem
    WriteCommandAction.runWriteCommandAction(myProject,  {
      myComponent.setAttribute(TOOLS_URI, ATTR_TEXT, attributeValue)
    })
    context.doClose(false)
  }

  /**
   * Method called if the user has closed the popup
   */
  private fun onClosed(cancelled: Boolean) {
    if (!cancelled) {
      return
    }

    val facet = myComponent.model.facet
    val project = facet.module.project
    // onClosed is invoked when the dialog is closed so we run the clean-up it later when the dialog has effectively closed
    ApplicationManager.getApplication().invokeLater {
      WriteCommandAction.runWriteCommandAction(project) {
        myComponent.setAttribute(TOOLS_URI, ATTR_TEXT, myOriginalTextValue)
        CommandProcessor.getInstance().addAffectedFiles(project, myComponent.tag.containingFile.virtualFile)
      }
    }
    return
  }

  companion object {
    @JvmStatic
    fun createComponent(context: Context): JComponent {
      return TextViewAssistant(context)
    }
  }
}
