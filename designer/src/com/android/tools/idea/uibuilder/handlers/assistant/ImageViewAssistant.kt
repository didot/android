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

import com.android.ide.common.rendering.api.ResourceValue
import com.android.resources.ResourceType
import com.android.tools.adtui.model.stdui.DefaultCommonComboBoxModel
import com.android.tools.adtui.stdui.CommonComboBox
import com.android.tools.adtui.ui.ClickableLabel
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.res.SampleDataResourceItem
import com.android.tools.idea.res.SampleDataResourceItem.ContentType.IMAGE
import com.android.tools.idea.res.getDrawableResources
import com.android.tools.idea.res.getSampleDataOfType
import com.android.tools.idea.ui.resourcechooser.ChooseResourceDialog
import com.android.tools.idea.ui.resourcechooser.DrawableGrid
import com.android.tools.idea.uibuilder.handlers.ImageViewHandler
import com.android.tools.idea.uibuilder.property.assistant.AssistantPopupPanel
import com.android.tools.idea.uibuilder.property.assistant.ComponentAssistantFactory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.JBUI.Borders
import com.intellij.util.ui.JBUI.scale
import org.jetbrains.android.facet.AndroidFacet
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.*
import javax.swing.*

private const val ITEM_COUNT = 12
private val IMAGE_SIZE = scale(48)
private const val NONE_VALUE = "None"

/**
 * Assistant for the image view that display a grid of sample image that which the user
 * can select and will be applied to the provided
 * [ComponentAssistantFactory.Context.component]
 */
class ImageViewAssistant(
  private val context: ComponentAssistantFactory.Context,
  private val imageHandler: ImageViewHandler
) {
  private val nlComponent = context.component

  private var selectedSampleItem: SampleDataResourceItem? = null

  private val originalValue = imageHandler.getToolsSrc(nlComponent)

  private val itemNameLabel = assistantLabel(getSampleItemDisplayName(originalValue))

  private var itemDisplayName: String?
    get() = itemNameLabel.text
    set(itemName) {
      itemNameLabel.text = StringUtil.shortenTextWithEllipsis(itemName.orEmpty(), 20, 15, true)
    }

  private val itemList = createItemList()

  private var useAll = isSampleValueAll(originalValue)
    set(value) {
      field = value
      itemList.isEnabled = !value
      if (value) itemList.clearSelection() else itemList.selectedIndex = 0
    }

  private val useAllCheckBox = createUseAllCheckBox()

  val component = AssistantPopupPanel(content = createContent(nlComponent.model.facet))

  private fun isSampleValueAll(value: String?) = value?.endsWith(']')?.not() ?: false

  private fun createContent(facet: AndroidFacet) = JPanel(BorderLayout()).apply {
    isOpaque = false
    add(createHeaderControls(facet), BorderLayout.NORTH)
    add(itemList)
    add(createBottomBar(), BorderLayout.SOUTH)
  }

  private fun createHeaderControls(facet: AndroidFacet): JPanel {
    return JPanel(BorderLayout()).apply {
      isOpaque = false
      val sampleItems = fetchSampleItems(facet)
      add(assistantLabel("srcCompat"), BorderLayout.NORTH)
      add(createComboBox(sampleItems))
      add(useAllCheckBox, BorderLayout.EAST)
      border = Borders.emptyBottom(2)
    }
  }

  private fun createBottomBar() = Box.createHorizontalBox().apply {
    border = Borders.emptyTop(4)
    add(itemNameLabel)
    add(Box.createHorizontalGlue())
    add(ClickableLabel("Browse").apply {
      border = null
      isOpaque = false
      foreground = JBColor.link()
      addActionListener { pickFromResourceDialog() }
    })
  }

  private fun createItemList() = DrawableGrid(nlComponent.model.facet.module,
                                              DefaultListModel<ResourceValue>(),
                                              IMAGE_SIZE,
                                              ITEM_COUNT.toLong()).apply {
    isOpaque = false
    isEnabled = originalValue != null && !isSampleValueAll(originalValue)
    visibleRowCount = 3
    addListSelectionListener { _ ->
      applySampleItem(selectedSampleItem, if (useAll) -1 else selectedIndex)
    }
  }

  private fun createUseAllCheckBox() = JBCheckBox("Use as set").apply {
    setAssistantFont(this@apply)
    isSelected = useAll
    isOpaque = false
    addActionListener { event -> useAll = (event.source as JBCheckBox).isSelected }
  }

  private fun createComboBox(sampleItems: List<SampleDataResourceItem>): CommonComboBox<String, DefaultCommonComboBoxModel<String>> {
    val sampleItemsWithNull = listOf(null) + sampleItems
    val elements = sampleItemsWithNull.map { it?.name ?: NONE_VALUE }
    val selected = elements
                     .firstOrNull { originalValue?.contains(it) ?: false }
                   ?: elements.first()
    val comboBoxModel = DefaultCommonComboBoxModel(selected, elements)
    return CommonComboBox(comboBoxModel).apply {
      isEditable = false
      isOpaque = false
      selectedItem = selected
      preferredSize = Dimension(itemList.preferredSize.width - useAllCheckBox.preferredSize.width, preferredSize.height)
      selectedSampleItem = sampleItemsWithNull[selectedIndex]
      displayResourceValues(selectedSampleItem, -1)
      addActionListener { event ->
        if (selectedItem == NONE_VALUE) {
          itemList.isEnabled = false
          useAllCheckBox.isEnabled = false
        }
        else {
          itemList.isEnabled = true && !useAll
          useAllCheckBox.isEnabled = true
        }
        val selectedIndex = (event.source as JComboBox<*>).selectedIndex
        setSelectedSampleItem(sampleItemsWithNull[selectedIndex]) // -1 to account for the None value
      }
    }
  }

  private fun getSampleItemDisplayName(attributeValue: String?) = attributeValue?.substringAfterLast("/").orEmpty()

  private fun setSelectedSampleItem(item: SampleDataResourceItem?) {
    if (item == selectedSampleItem) {
      return
    }
    selectedSampleItem = item
    val selectedIndex = itemList.selectedIndex
    displayResourceValues(item, selectedIndex)
    if (itemList.selectedIndex == -1) {
      applySampleItem(selectedSampleItem, -1)
    }
  }

  private fun displayResourceValues(item: SampleDataResourceItem?, selectedIndex: Int) {
    val listModel = itemList.model as DefaultListModel<ResourceValue>
    listModel.removeAllElements()
    itemList.resetCache()
    item?.getDrawableResources()?.take(ITEM_COUNT)?.forEach {
      listModel.addElement(it)
    }
    while (listModel.size() < ITEM_COUNT) {
      listModel.addElement(null)
    }
    itemList.selectedIndex = Math.min(selectedIndex, itemList.model.size - 1)
  }

  private fun fetchSampleItems(facet: AndroidFacet) =
    ResourceRepositoryManager.getAppResources(facet).getSampleDataOfType(IMAGE).toList()

  private fun applySampleItem(item: SampleDataResourceItem?, resourceValueIndex: Int) {
    val useAll = resourceValueIndex < 0
    val itemName = if (item != null) item.name + if (useAll) "" else "[${resourceValueIndex}]" else ""
    itemDisplayName = itemName
    imageHandler.setToolsSrc(nlComponent, item, resourceValueIndex)
  }

  private fun pickFromResourceDialog() {
    val model = nlComponent.model
    val dialog = ChooseResourceDialog.builder()
      .setModule(model.module)
      .setTypes(EnumSet.of(ResourceType.DRAWABLE))
      .setConfiguration(model.configuration)
      .setShowSampleDataPicker(true)
      .build()

    if (dialog.showAndGet()) {
      imageHandler.setToolsSrc(nlComponent, dialog.resourceName)
      context.doClose(false)
    }
  }
}
