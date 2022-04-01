/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run.configuration.editors

import com.android.tools.deployer.model.component.Complication
import com.android.tools.idea.observable.collections.ObservableList
import com.android.tools.idea.run.configuration.AndroidComplicationConfiguration
import com.android.tools.idea.run.configuration.ComplicationSlot
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.layout.panel
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.Dimension
import java.awt.event.ContainerEvent
import java.awt.event.ContainerListener
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel


class AndroidComplicationConfigurationEditor(project: Project, configuration: AndroidComplicationConfiguration) :
  AndroidWearConfigurationEditor<AndroidComplicationConfiguration>(project, configuration) {
  private var allAvailableSlots: List<ComplicationSlot> = listOf()
  private var originalChosenSlots: List<AndroidComplicationConfiguration.ChosenSlot> = listOf()
  private val currentChosenSlots: ObservableList<AndroidComplicationConfiguration.ChosenSlot> = ObservableList()
  private var notChosenSlotIds: List<Int> = emptyList()
  private var sourceTypes: List<Complication.ComplicationType>? = null
  private lateinit var addSlotLink: JComponent
  private lateinit var slotsComponent: JPanel
  private var hasModule = false

  init {
    Disposer.register(project, this)
    currentChosenSlots.addListener { update() }
  }

  private fun update() {
    repaintSlotsComponent()
    addSlotLink.isEnabled = hasModule && (currentChosenSlots.size < allAvailableSlots.size)
  }

  override fun onModuleChanged(newModule: Module?) {
    hasModule = newModule != null
    update()
  }

  override fun resetEditorFrom(runConfiguration: AndroidComplicationConfiguration) {
    super.resetEditorFrom(runConfiguration)
    allAvailableSlots = runConfiguration.watchFaceInfo.complicationSlots
    originalChosenSlots = runConfiguration.chosenSlots.map { it.copy() }
    sourceTypes = runConfiguration.getTypesFromManifest()
    currentChosenSlots.apply {
      beginUpdate()
      clear()
      addAll(runConfiguration.chosenSlots.map { removeInvalidTypes(it.copy()) })
      endUpdate()
    }
    update()
  }

  private fun removeInvalidTypes(chosenSlot: AndroidComplicationConfiguration.ChosenSlot): AndroidComplicationConfiguration.ChosenSlot{
    if (sourceTypes?.contains(chosenSlot.type) != false) {
      return chosenSlot
    }
    return AndroidComplicationConfiguration.ChosenSlot(chosenSlot.id, null)
  }

  private fun substituteInvalidTypes(chosenSlot: AndroidComplicationConfiguration.ChosenSlot): AndroidComplicationConfiguration.ChosenSlot{
    val supportedTypesForNewId = filterTypes(supportedTypes(chosenSlot.id), sourceTypes)
    if (!supportedTypesForNewId.contains(chosenSlot.type)) {
      if (supportedTypesForNewId.isEmpty()) {
        chosenSlot.type = null
      } else {
        chosenSlot.type = supportedTypesForNewId.first()
      }
    }
    return chosenSlot
  }

  override fun applyEditorTo(runConfiguration: AndroidComplicationConfiguration) {
    super.applyEditorTo(runConfiguration)
    runConfiguration.chosenSlots = currentChosenSlots.map { it.copy() }
    originalChosenSlots = runConfiguration.chosenSlots.map { it.copy() }
    sourceTypes = runConfiguration.getTypesFromManifest()
  }

  override fun onComponentNameChanged(newComponent: String?) {
    super.onComponentNameChanged(newComponent)
    currentChosenSlots.clear()
    slotsComponent.removeAll()
  }

  override fun createEditor() =
    panel {
      getModuleChooser()
      getComponentCompoBox()
      getInstallFlagsTextField()

      row {
        label("Slot launch options")
      }
      row {
        slotsComponent = component(JPanel().apply {
          layout = BoxLayout(this, BoxLayout.Y_AXIS)
          addContainerListener(object : ContainerListener {
            override fun componentAdded(e: ContainerEvent?) = fireEditorStateChanged()
            override fun componentRemoved(e: ContainerEvent?) = fireEditorStateChanged()
          })
        })
          .onIsModified {
            currentChosenSlots.size != originalChosenSlots.size ||
            currentChosenSlots.union(originalChosenSlots).size != originalChosenSlots.size
          }
          .component
      }

      row {
        addSlotLink = link("+ Add Slot", style = null) {
          val nextAvailableSlot = allAvailableSlots.first { notChosenSlotIds.contains(it.slotId) }
          currentChosenSlots.add(
            substituteInvalidTypes(
              AndroidComplicationConfiguration.ChosenSlot(nextAvailableSlot.slotId, nextAvailableSlot.supportedTypes.first())))
        }.component
        addSlotLink.isEnabled = currentChosenSlots.size < allAvailableSlots.size
      }
    }

  private fun repaintSlotsComponent() {
    slotsComponent.removeAll()
    val chosenSlotsIds = currentChosenSlots.map { it.id }
    notChosenSlotIds = allAvailableSlots.filter { !chosenSlotsIds.contains(it.slotId) }.map { it.slotId }
    currentChosenSlots.forEach { slotsComponent.add(createSlot(it)) }
    slotsComponent.revalidate()
    slotsComponent.repaint()
  }

  private fun createSlot(chosenSlot: AndroidComplicationConfiguration.ChosenSlot): JPanel {

    return JPanel().apply {
      // Slot id ComboBox.
      add(JLabel("Slot").apply { border = JBUI.Borders.empty(0, 0, 0, 5) })
      add(getSlotIdComboBox(chosenSlot))

      // Slot type ComboBox.
      add(JLabel("Type").apply { border = JBUI.Borders.empty(0, 10, 0, 5) })
      add(getSlotTypeCompoBox(chosenSlot))

      // Delete button.
      add(JButton(StudioIcons.Common.CLOSE).apply {
        addActionListener {
          currentChosenSlots.remove(chosenSlot)
        }
        toolTipText = "Remove"
        isContentAreaFilled = false
        border = null
      })
    }
  }

  private fun getSlotTypeCompoBox(chosenSlot: AndroidComplicationConfiguration.ChosenSlot): ComboBox<Complication.ComplicationType> {
    val options = filterTypes(supportedTypes(chosenSlot.id), sourceTypes)
    val comboBox = ComboBox(options).apply {
      preferredSize = Dimension(250, preferredSize.height)
      item = chosenSlot.type
      addActionListener {
        chosenSlot.type = this.item
      }
    }
    if (options.isEmpty()) {
      chosenSlot.type = null
      comboBox.isEnabled = false
    }
    return comboBox
  }

  private fun filterTypes(types: Array<Complication.ComplicationType>, supported: List<Complication.ComplicationType>?) : Array<Complication.ComplicationType> {
    return types.filter {type -> supported?.any { it == type } != false}.toTypedArray()
  }

  private fun getSlotIdComboBox(chosenSlot: AndroidComplicationConfiguration.ChosenSlot): ComboBox<Int> {
    val availableSlotIds = (notChosenSlotIds + chosenSlot.id).toTypedArray()
    availableSlotIds.sort()
    return ComboBox(availableSlotIds).apply {
      renderer = SimpleListCellRenderer.create { label, value, _ -> label.text = allAvailableSlots.first { it.slotId == value }.name }
      preferredSize = Dimension(150, preferredSize.height)
      item = chosenSlot.id
      addActionListener {
        // When new slotId is chosen we are trying to save chosen type,
        // if it is applicable for new slot Id, if not we set first supported type.
        chosenSlot.id = this.item
        substituteInvalidTypes(chosenSlot)
        // We should change available slots ids in each slotIdComboBox + update supportedTypes. It's easier repaint the whole [slotsComponent].
        repaintSlotsComponent()
      }
    }
  }

  private fun supportedTypes(slotId: Int) = allAvailableSlots.first { it.slotId == slotId }.supportedTypes
}

