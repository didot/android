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
package com.android.tools.idea.appinspection.inspectors.network.ide

import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTab
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTabProvider
import com.android.tools.idea.appinspection.inspector.ide.FrameworkInspectorLaunchParams
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorClient
import com.android.tools.idea.appinspection.inspectors.view.NetworkInspectorTab
import com.intellij.openapi.project.Project
import icons.StudioIcons
import javax.swing.Icon

class NetworkInspectorTabProvider : AppInspectorTabProvider {
  override val inspectorId = "studio.network.inspection"
  override val displayName = "Network Inspector"
  override val icon: Icon = StudioIcons.LayoutEditor.Palette.LIST_VIEW
  override val inspectorLaunchParams = FrameworkInspectorLaunchParams(
    AppInspectorJar("network-inspector.jar", developmentDirectory = "bazel-bin/tools/base/network-inspector")
  )

  override fun isApplicable(): Boolean {
    return false
  }

  override fun createTab(
    project: Project,
    ideServices: AppInspectionIdeServices,
    processDescriptor: ProcessDescriptor,
    messenger: AppInspectorMessenger
  ) = object : AppInspectorTab {
    override val messenger = messenger
    private val client = NetworkInspectorClient(messenger)

    override val component = NetworkInspectorTab(client).component
  }
}