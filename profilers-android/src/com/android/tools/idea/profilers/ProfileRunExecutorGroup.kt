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
package com.android.tools.idea.profilers

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.ExecutorIconProvider
import com.android.tools.idea.run.editor.ProfilerState
import com.android.tools.profilers.sessions.SessionsManager
import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.RunExecutorSettings
import com.intellij.execution.impl.DefaultExecutorGroup
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import icons.StudioIcons
import org.jetbrains.android.util.AndroidUtils
import javax.swing.Icon

/**
 * Executor group to support profiling app as profileable or debuggable in a dropdown menu.
 */
class ProfileRunExecutorGroup : DefaultExecutorGroup<ProfileRunExecutorGroup.Setting>(), ExecutorIconProvider {
  class Setting(val profilingMode: ProfilerState.ProfilingMode) : RunExecutorSettings {
    override val actionName: String
      get() = "Profile ${profilingMode.value}"

    override val icon: Icon
      get() = when (profilingMode) {
        ProfilerState.ProfilingMode.PROFILEABLE -> PROFILEABLE_ICON
        ProfilerState.ProfilingMode.DEBUGGABLE -> DEBUGGABLE_ICON
        else -> StudioIcons.Shell.Toolbar.PROFILER
      }

    override val startActionText = actionName
    override fun canRun(profile: RunProfile) = true
    override fun isApplicable(project: Project) = true
    override fun getStartActionText(configurationName: String) = "Profile '$configurationName' as ${profilingMode.value}"
  }

  private class GroupWrapper(actionGroup: ActionGroup) : ExecutorGroupWrapper(actionGroup) {
    override fun groupShouldBeVisible(e: AnActionEvent) = StudioFlags.PROFILEABLE_BUILDS.get()

    override fun updateDisabledActionPresentation(eventPresentation: Presentation) {
      eventPresentation.icon = PROFILEABLE_ICON
      eventPresentation.text = "Profile"
    }
  }

  init {
    registerSettings(Setting(ProfilerState.ProfilingMode.PROFILEABLE))
    registerSettings(Setting(ProfilerState.ProfilingMode.DEBUGGABLE))
  }

  override fun getIcon(): Icon = PROFILEABLE_ICON

  override fun getDisabledIcon(): Icon = toolWindowIcon

  override fun getDescription(): String = "Profile selected configuration"

  override fun getActionName(): String = "Profile"

  override fun getId(): String = EXECUTOR_ID

  override fun getStartActionText(): String = "Profile"

  override fun getContextActionId(): String = "ProfileGroupRunClass"

  override fun getHelpId(): String? = null

  override fun getExecutorIcon(project: Project, executor: Executor): Icon {
    AndroidProfilerToolWindowFactory.getProfilerToolWindow(project)?.profilers?.let {
      if (SessionsManager.isSessionAlive(it.sessionsManager.profilingSession)) {
        return ExecutionUtil.getLiveIndicator(icon)
      }
    }
    return icon
  }

  override fun isApplicable(project: Project): Boolean = AndroidUtils.hasAndroidFacets(project)

  override fun getRunToolbarActionText(param: String): String = "Profile: $param"

  override fun getRunToolbarChooserText(): String = "Profile"

  override fun getToolWindowIcon(): Icon = StudioIcons.Shell.ToolWindows.ANDROID_PROFILER

  override fun getToolWindowId(): String = ToolWindowId.RUN

  override fun createExecutorGroupWrapper(actionGroup: ActionGroup): ExecutorGroupWrapper = GroupWrapper(actionGroup)

  companion object {
    const val EXECUTOR_ID = "Android Profiler Group"
    private val PROFILEABLE_ICON = StudioIcons.Shell.Toolbar.PROFILER

    // TODO(b/213946909): replace with real icon.
    private val DEBUGGABLE_ICON = AllIcons.Actions.Profile

    @JvmStatic
    fun getInstance(): ProfileRunExecutorGroup? {
      return ExecutorRegistry.getInstance().getExecutorById(EXECUTOR_ID) as? ProfileRunExecutorGroup
    }
  }
}