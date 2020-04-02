/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.appinspection.ide.model

import com.android.tools.adtui.model.stdui.DefaultCommonComboBoxModel
import com.android.tools.idea.appinspection.api.AppInspectionDiscoveryHost
import com.android.tools.idea.appinspection.api.ProcessDescriptor
import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.EdtExecutorService

// TODO(b/152215087): This text needs to live in an Android Bundle and be internationalized.
private const val DEFAULT_SELECTION_TEXT = "No Inspection Target Available"

private const val NO_SELECTION_TEXT = "No Process Selected"

//TODO(b/148546243): separate view and model code into independent modules.
/**
 * Model of [AppInspectionProcessesComboBox]. This takes an [AppInspectionDiscoveryHost] which is used to launch new inspector connections,
 * and a lambda that can dynamically determine the preferred processes of this project.
 */
class AppInspectionProcessesComboBoxModel(
  private val appInspectionDiscoveryHost: AppInspectionDiscoveryHost,
  getPreferredProcessNames: () -> List<String>
) : DefaultCommonComboBoxModel<ProcessDescriptor>(""), Disposable {
  override var editable = false

  private val processListener = object : AppInspectionDiscoveryHost.ProcessListener {
    override fun onProcessConnected(descriptor: ProcessDescriptor) {
      val preferredProcessNames = getPreferredProcessNames()
      if (preferredProcessNames.contains(descriptor.processName)) {
        insertElementAt(descriptor, 0)
      } else {
        insertElementAt(descriptor, size)
      }
      if (selectedItem !is ProcessDescriptor && preferredProcessNames.contains(descriptor.processName)) {
        selectedItem = descriptor
      }
    }

    override fun onProcessDisconnected(descriptor: ProcessDescriptor) {
      removeElement(descriptor)
    }
  }

  init {
    appInspectionDiscoveryHost.addProcessListener(EdtExecutorService.getInstance(), processListener)
  }

  override fun getSelectedItem() = super.getSelectedItem() ?: if (size == 0) DEFAULT_SELECTION_TEXT else NO_SELECTION_TEXT

  override fun dispose() {
    appInspectionDiscoveryHost.removeProcessListener(processListener)
  }
}