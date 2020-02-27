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
package com.android.tools.idea.run.ui;

import com.android.tools.idea.run.util.SwapInfo;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import icons.StudioIcons;
import javax.swing.KeyStroke;

import org.jetbrains.android.util.AndroidBuildCommonUtils;
import org.jetbrains.annotations.NotNull;

public class ApplyChangesAction extends BaseAction {

  public static final String ID = "android.deploy.ApplyChanges";

  public static final String NAME = "&Apply Changes and Restart Activity";

  private static final Shortcut SHORTCUT =
    new KeyboardShortcut(KeyStroke.getKeyStroke(SystemInfo.isMac ? "control meta E" : "control F10"), null);

  private static final String DESC = "Attempt to apply resource and code changes and restart activity.";

  public ApplyChangesAction() {
    super(ID, NAME, SwapInfo.SwapType.APPLY_CHANGES, StudioIcons.Shell.Toolbar.APPLY_ALL_CHANGES, SHORTCUT, DESC);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    Project project = e.getProject();
    if (project == null) {
        return;
    }

    // Disable "Apply Changes" for any kind of test project.
    RunnerAndConfigurationSettings runConfig = RunManager.getInstance(project).getSelectedConfiguration();
    if (runConfig != null) {
      ConfigurationType type = runConfig.getType();
      String id = type.getId();
      if (AndroidBuildCommonUtils.isTestConfiguration(id) || AndroidBuildCommonUtils.isInstrumentationTestConfiguration(id)) {
        disableAction(e.getPresentation(), new DisableMessage(DisableMessage.DisableMode.DISABLED, "test project",
                                                              "the selected configuration is a test configuration"));
        return;
      }

      ProcessHandler handler = findRunningProcessHandler(project, runConfig.getConfiguration());
      if (handler != null &&
          getExecutor(handler, DefaultRunExecutor.getRunExecutorInstance()) == DefaultDebugExecutor.getDebugExecutorInstance()) {
        disableAction(e.getPresentation(), new DisableMessage(DisableMessage.DisableMode.DISABLED, "debug execution",
                                                              "it is currently not allowed during debugging"));
      }
    }
  }
}

