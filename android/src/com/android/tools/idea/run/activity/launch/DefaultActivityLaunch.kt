/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run.activity.launch

import com.android.ddmlib.IDevice
import com.android.ddmlib.IShellOutputReceiver
import com.android.tools.deployer.model.App
import com.android.tools.deployer.model.component.AppComponent
import com.android.tools.deployer.model.component.ComponentType
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ValidationError
import com.android.tools.idea.run.activity.ActivityLocator
import com.android.tools.idea.run.activity.DefaultApkActivityLocator
import com.android.tools.idea.run.activity.StartActivityFlagsProvider
import com.android.tools.idea.run.configuration.AndroidBackgroundTaskReceiver
import com.android.tools.idea.run.editor.ProfilerState
import com.android.tools.idea.run.tasks.AppLaunchTask
import com.android.tools.idea.run.tasks.DefaultActivityLaunchTask
import com.google.common.collect.ImmutableList
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet
import javax.swing.JComponent

class DefaultActivityLaunch : ActivityLaunchOption<DefaultActivityLaunch.State>() {
  class State : ActivityLaunchOptionState() {
    override fun getLaunchTask(
      applicationId: String,
      facet: AndroidFacet,
      startActivityFlagsProvider: StartActivityFlagsProvider,
      profilerState: ProfilerState,
      apkProvider: ApkProvider
    ): AppLaunchTask {
      return DefaultActivityLaunchTask(applicationId, getActivityLocatorForLaunch(apkProvider), startActivityFlagsProvider)
    }

    override fun launch(device: IDevice,
                        app: App,
                        apkProvider: ApkProvider,
                        isDebug: Boolean,
                        extraFlags: String,
                        console: ConsoleView) {
      ProgressManager.checkCanceled()
      val mode = if (isDebug) AppComponent.Mode.DEBUG else AppComponent.Mode.RUN
      val activityQualifiedName = getActivityLocatorForLaunch(apkProvider).getQualifiedActivityName(device)
      val receiver: IShellOutputReceiver = AndroidBackgroundTaskReceiver(console)
      app.activateComponent(ComponentType.ACTIVITY, activityQualifiedName, extraFlags, mode, receiver)
    }

    override fun checkConfiguration(facet: AndroidFacet): List<ValidationError> {
      return ImmutableList.of()
    }

    companion object {
      private fun getActivityLocatorForLaunch(apkProvider: ApkProvider): ActivityLocator {
        return DefaultApkActivityLocator(apkProvider)
      }
    }
  }

  override fun getId(): String {
    return AndroidRunConfiguration.LAUNCH_DEFAULT_ACTIVITY
  }

  override fun getDisplayName(): String {
    return "Default Activity"
  }

  override fun createState(): State {
    // there is no state to save in this case
    return State()
  }

  override fun createConfigurable(project: Project, context: LaunchOptionConfigurableContext): LaunchOptionConfigurable<State?> {
    return object : LaunchOptionConfigurable<State?> {
      override fun createComponent(): JComponent? {
        return null
      }

      override fun resetFrom(state: State) {}
      override fun applyTo(state: State) {}
    }
  }

  companion object {
    @JvmField
    val INSTANCE = DefaultActivityLaunch()
  }
}