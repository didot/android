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
@file:Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE") // Renaming the "data" parameter is useful and we won't be using pass by name

package com.android.tools.idea.appinspection.inspectors.workmanager.view

import androidx.work.inspection.WorkManagerInspectorProtocol
import androidx.work.inspection.WorkManagerInspectorProtocol.WorkInfo.State
import com.android.tools.adtui.ui.HideablePanel
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorkManagerInspectorClient
import com.android.tools.idea.protobuf.ProtocolStringList
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.FlowLayout
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable

/**
 * Interface for converting some model input into a UI component.
 */
interface ComponentProvider<T> {
  fun convert(data: T): JComponent
}

/**
 * A basic provider which converts any data to its a label containing the data's `toString`
 * representation, useful as a default provider for simple cases.
 */
class ToStringProvider<T> : ComponentProvider<T> {
  override fun convert(data: T) = JBLabel(data.toString())
}

/**
 * Provides a component that represents a class name which can be navigated to.
 */
class ClassNameProvider(private val ideServices: AppInspectionIdeServices, private val scope: CoroutineScope) : ComponentProvider<String> {
  override fun convert(fqcn: String): JComponent {
    return HyperlinkLabel(fqcn).apply {
      addHyperlinkListener {
        scope.launch {
          ideServices.navigateTo(AppInspectionIdeServices.CodeLocation.forClass(fqcn))
        }
      }
    }
  }
}

/**
 * Provides a component that represents a timestamp in a human readable format.
 */
object TimeProvider : ComponentProvider<Long> {
  override fun convert(timestamp: Long) = JBLabel(timestamp.toFormattedTimeString())
}

/**
 * Provides a component that represents a state text with icon.
 */
object StateProvider : ComponentProvider<State> {
  override fun convert(state: State): JComponent {
    return JBLabel(state.capitalizedName()).apply {
      icon = state.icon()
    }
  }
}

/**
 * Provides a component that displays the location a worker was enqueued at, with the ability to
 * click on that location to navigate into the code.
 */
class EnqueuedAtProvider(private val ideServices: AppInspectionIdeServices,
                         private val scope: CoroutineScope) : ComponentProvider<WorkManagerInspectorProtocol.CallStack> {
  override fun convert(stack: WorkManagerInspectorProtocol.CallStack): JComponent {
    return if (stack.framesCount == 0) {
      JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)).apply {
        add(JBLabel("Unavailable"))
        add(Box.createHorizontalStrut(5))
        val icon = JLabel(StudioIcons.Common.HELP)
        HelpTooltip()
          .setDescription("Enqueue location is only known for workers started after opening the inspector.")
          .installOn(icon)
        add(icon)
      }
    }
    else {
      val frame0 = stack.getFrames(0)
      HyperlinkLabel("${frame0.fileName} (${frame0.lineNumber})").apply {
        addHyperlinkListener {
          scope.launch {
            ideServices.navigateTo(AppInspectionIdeServices.CodeLocation.forFile(frame0.fileName, frame0.lineNumber))
          }
        }
      }
    }
  }
}

/**
 * Provides a component that displays a list of string values.
 */
object StringListProvider : ComponentProvider<ProtocolStringList> {
  override fun convert(strings: ProtocolStringList): JComponent {
    return if (strings.isNotEmpty()) {
      JPanel(VerticalFlowLayout(0, 0)).apply {
        strings.forEach { str ->
          add(JBLabel("\"$str\""))
        }
      }
    }
    else {
      JBLabel("None")
    }
  }
}

/**
 * Provides a component that displays a list of workers, each which, when clicked, select that
 * worker in a source table.
 */
class IdListProvider(private val client: WorkManagerInspectorClient,
                     private val table: JTable,
                     private val work: WorkManagerInspectorProtocol.WorkInfo) : ComponentProvider<List<String>> {
  override fun convert(ids: List<String>): JComponent {
    val currId = work.id
    return if (ids.isNotEmpty()) {
      JPanel(VerticalFlowLayout(0, 0)).apply {
        ids.forEach { id ->
          val index = client.indexOfFirstWorkInfo { it.id == id }
          if (index >= 0 && index < table.rowCount) {
            add(HyperlinkLabel().apply {
              val suffix = if (id == currId) " (Current)" else ""
              setHyperlinkText("", id, suffix)
              addHyperlinkListener {
                table.selectionModel.setSelectionInterval(index, index)
              }
              client.getWorkInfoOrNull(index)?.run {
                setIcon(state.icon())
                if (tagsCount > 0) {
                  toolTipText = "<html><b>Tags</b><br>${tagsList.joinToString("<br>") { "\"$it\"" }}</html>"
                }
              }
            })
          }
          else {
            add(JBLabel(id))
          }
        }
      }
    }
    else {
      JBLabel("None")
    }
  }
}

/**
 * Provides a component that displays a list of constraint descriptions for some target worker.
 */
object ConstraintProvider : ComponentProvider<WorkManagerInspectorProtocol.Constraints> {
  override fun convert(constraint: WorkManagerInspectorProtocol.Constraints): JComponent {
    val constraintDescs = mutableListOf<String>()
    when (constraint.requiredNetworkType) {
      WorkManagerInspectorProtocol.Constraints.NetworkType.CONNECTED -> constraintDescs.add("Network must be connected")
      WorkManagerInspectorProtocol.Constraints.NetworkType.UNMETERED -> constraintDescs.add("Network must be unmetered")
      WorkManagerInspectorProtocol.Constraints.NetworkType.NOT_ROAMING -> constraintDescs.add("Network must not be roaming")
      WorkManagerInspectorProtocol.Constraints.NetworkType.METERED -> constraintDescs.add("Network must be metered")
      WorkManagerInspectorProtocol.Constraints.NetworkType.UNRECOGNIZED -> constraintDescs.add("Network must be recognized")
      else -> {
      }
    }

    if (constraint.requiresCharging) {
      constraintDescs.add("Requires charging")
    }
    if (constraint.requiresBatteryNotLow) {
      constraintDescs.add("Requires battery not low")
    }
    if (constraint.requiresDeviceIdle) {
      constraintDescs.add("Requires idle device")
    }
    if (constraint.requiresStorageNotLow) {
      constraintDescs.add("Requires storage not low")
    }

    return if (constraintDescs.isNotEmpty()) {
      JPanel(VerticalFlowLayout(0, 0)).apply {
        constraintDescs.forEach { desc ->
          add(JBLabel(desc))
        }
      }
    }
    else {
      JBLabel("None")
    }
  }
}

/**
 * Provides a component which displays all the key/value pairs in a worker's output data.
 */
object OutputDataProvider : ComponentProvider<WorkManagerInspectorProtocol.Data> {
  override fun convert(data: WorkManagerInspectorProtocol.Data): JComponent {
    return if (data.entriesList.isNotEmpty()) {
      val panel = JPanel(VerticalFlowLayout(0, 0)).apply {
        data.entriesList.forEach { pair ->
          val pairPanel = JPanel(HorizontalLayout(0))
          pairPanel.add(JLabel("${pair.key} = "))
          pairPanel.add(JLabel("\"${pair.value}\"").apply {
            foreground = WorkManagerInspectorColors.DATA_TEXT_COLOR
          })
          add(pairPanel)
        }
      }
      HideablePanel(HideablePanel.Builder("Data", panel)
                      .setPanelBorder(JBUI.Borders.empty())
                      .setContentBorder(JBUI.Borders.empty(0, 20, 0, 0)))
    }
    else {
      JBLabel("None")
    }
  }
}
