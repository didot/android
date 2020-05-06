/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.appinspection.ide.ui

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.idea.appinspection.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.ide.model.AppInspectionBundle
import com.android.tools.idea.appinspection.ide.model.AppInspectionProcessModel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBColor
import icons.StudioIcons

val NO_PROCESS_ACTION = object : AnAction(AppInspectionBundle.message("action.no.debuggable.process")) {
  override fun actionPerformed(event: AnActionEvent) {}
}.apply { templatePresentation.isEnabled = false }

private val ICON = ColoredIconGenerator.generateColoredIcon(StudioIcons.Avd.DEVICE_PHONE, JBColor(0x6E6E6E, 0xAFB1B3))

class SelectProcessAction(private val model: AppInspectionProcessModel) :
  DropDownAction(AppInspectionBundle.message("action.select.process"), AppInspectionBundle.message("action.select.process.desc"), ICON) {

  private var currentProcess: ProcessDescriptor? = null

  override fun update(event: AnActionEvent) {
    currentProcess = model.selectedProcess
    val content = currentProcess?.let {
      "${it.buildDeviceName()} > ${it.processName}"
    } ?: if (model.processes.isEmpty()) {
      AppInspectionBundle.message("no.process.available")
    } else {
      AppInspectionBundle.message("no.process.selected")
    }

    if (content != event.presentation.text) {
      event.presentation.text = content
    }
  }

  public override fun updateActions(context: DataContext): Boolean {
    removeAll()

    val serials = mutableSetOf<String>()

    // Rebuild the action tree.
    for (processDescriptor in model.processes) {
      val serial = processDescriptor.serial
      if (!serials.add(serial)) {
        continue
      }
      val deviceName = processDescriptor.buildDeviceName()
      add(DeviceAction(serial, deviceName, model))
    }
    if (childrenCount == 0) {
      val noDeviceAction = object : AnAction(AppInspectionBundle.message("action.no.devices")) {
        override fun actionPerformed(event: AnActionEvent) {}
      }
      noDeviceAction.templatePresentation.isEnabled = false
      add(noDeviceAction)
    }
    return true
  }

  override fun displayTextInToolbar() = true

  class ConnectAction(private val processDescriptor: ProcessDescriptor, private val model: AppInspectionProcessModel) :
    ToggleAction(processDescriptor.processName) {
    override fun isSelected(event: AnActionEvent): Boolean {
      return processDescriptor == model.selectedProcess
    }

    override fun setSelected(event: AnActionEvent, state: Boolean) {
      model.selectedProcess = processDescriptor
    }
  }

  class DeviceAction(serial: String,
                     deviceName: String,
                     model: AppInspectionProcessModel) : DropDownAction(deviceName, null, null) {
    override fun displayTextInToolbar() = true

    init {
      val (projectProcesses, otherProcesses) = model.processes.filter { it.serial == serial }.partition { model.isProcessPreferred(it) }

      for (process in projectProcesses) {
        add(ConnectAction(process, model))
      }
      if (projectProcesses.isNotEmpty() && otherProcesses.isNotEmpty()) {
        add(Separator.getInstance())
      }
      for (process in otherProcesses) {
        add(ConnectAction(process, model))
      }

      if (childrenCount == 0) {
        add(NO_PROCESS_ACTION)
      }
    }
  }
}

private fun ProcessDescriptor.buildDeviceName(): String {
  var displayModel = model
  val deviceNameBuilder = StringBuilder()

  // Removes possible serial suffix
  val suffix = String.format("-%s", serial)
  if (displayModel.endsWith(suffix)) {
    displayModel = displayModel.substring(0, displayModel.length - suffix.length)
  }
  if (!StringUtil.isEmpty(manufacturer)) {
    deviceNameBuilder.append(manufacturer)
    deviceNameBuilder.append(" ")
  }
  deviceNameBuilder.append(displayModel.replace('_', ' '))

  return deviceNameBuilder.toString()
}
