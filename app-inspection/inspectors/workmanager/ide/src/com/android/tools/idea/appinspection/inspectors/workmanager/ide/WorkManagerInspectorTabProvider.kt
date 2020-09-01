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
package com.android.tools.idea.appinspection.inspectors.workmanager.ide

import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.inspector.api.AppInspectorLauncher
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTab
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTabProvider
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorkManagerInspectorClient
import com.android.tools.idea.appinspection.inspectors.workmanager.view.WorkManagerInspectorTab
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.flags.StudioFlags.ENABLE_WORK_MANAGER_INSPECTOR_TAB
import com.intellij.openapi.project.Project
import icons.StudioIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import javax.swing.Icon
import javax.swing.JComponent

class WorkManagerInspectorTabProvider : AppInspectorTabProvider {
  override val inspectorId = "androidx.work.inspection"
  override val displayName = "WorkManager Inspector"
  override val icon: Icon = StudioIcons.LayoutEditor.Palette.LIST_VIEW
  override val inspectorAgentJar = AppInspectorJar("workmanager-inspection.jar",
                                                   developmentDirectory = "prebuilts/tools/common/app-inspection/androidx/work/",
                                                   releaseDirectory = "plugins/android/resources/app-inspection/")
  override val targetLibrary = AppInspectorLauncher.TargetLibrary(
    AppInspectorLauncher.LibraryArtifact("androidx.work", "work-runtime"),
    "2.5.0-alpha01")

  override fun isApplicable(): Boolean {
    return ENABLE_WORK_MANAGER_INSPECTOR_TAB.get()
  }

  override fun createTab(project: Project,
                         ideServices: AppInspectionIdeServices,
                         processDescriptor: ProcessDescriptor,
                         messenger: AppInspectorMessenger): AppInspectorTab {
    val projectScope = AndroidCoroutineScope(project)
    val moduleScope = CoroutineScope(projectScope.coroutineContext + Job(projectScope.coroutineContext[Job]))
    return object : AppInspectorTab {
      override val messenger = messenger
      private val client = WorkManagerInspectorClient(messenger, moduleScope)

      override val component: JComponent = WorkManagerInspectorTab(client, ideServices, moduleScope).component
    }
  }
}