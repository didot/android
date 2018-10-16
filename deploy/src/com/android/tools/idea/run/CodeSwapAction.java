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
package com.android.tools.idea.run;

import com.android.tools.deployer.Trace;
import com.intellij.execution.Executor;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import icons.StudioIcons;
import java.util.function.Function;
import javax.swing.KeyStroke;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CodeSwapAction extends AnAction {

  public static final String ID = "android.deploy.CodeSwap";

  public static final Key<Boolean> CODE_SWAP = Key.create(ID);

  private static final Logger LOG = Logger.getInstance(CodeSwapAction.class);

  // TODO: Control Alt F10 is almost always going to get your xserver to send you to
  //       your 10th virtual console.....
  private static final Shortcut SHORTCUT =
    new KeyboardShortcut(KeyStroke.getKeyStroke(SystemInfo.isMac ? "control meta shift R" : "control alt F10"), null);

  @NotNull private final Function<Project, Boolean> myShouldEnableCodeSwapProvider;

  public CodeSwapAction(@NotNull Function<Project, Boolean> shouldEnableCodeSwapProvider) {
    super("Code Swap", "Code Swap", StudioIcons.Shell.Toolbar.INSTANT_RUN_CODE_SWAP);
    myShouldEnableCodeSwapProvider = shouldEnableCodeSwapProvider;

    KeymapManager manager = KeymapManager.getInstance();
    if (manager != null) {
      final Keymap keymap = manager.getActiveKeymap();
      if (keymap != null) {
        keymap.addShortcut(ID, SHORTCUT);
      }
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    if (e.isFromActionToolbar()) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }

    if (e.getProject() == null) {
      presentation.setEnabled(false);
      return;
    }
    presentation.setEnabled(myShouldEnableCodeSwapProvider.apply(e.getProject()));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      LOG.warn("Code Swap action performed with no project");
      return;
    }

    RunnerAndConfigurationSettings settings = RunManager.getInstance(project).getSelectedConfiguration();
    if (settings == null) {
      LOG.warn("Code Swap  action could not locate current run config settings");
      return;
    }

    // TODO: Figure out the debugger flow. For now always use the Run executor.
    Executor executor = getExecutor(DefaultRunExecutor.EXECUTOR_ID);
    if (executor == null) {
      LOG.warn("Code Swap  action could not identify executor");
      return;
    }

    ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder.createOrNull(executor, settings);
    if (builder == null) {
      LOG.warn("Code Swap  action could not construct an env");
      return;
    }
    ExecutionEnvironment env = builder.activeTarget().dataContext(e.getDataContext()).build();

    env.putCopyableUserData(CODE_SWAP, true);
    Trace.reset();
    Trace.begin("PostCodeSwap Clicked");
    ProgramRunnerUtil.executeConfiguration(env, false, true);
  }

  @Nullable
  private static Executor getExecutor(@NotNull String executorId) {
    for (Executor executor : Executor.EXECUTOR_EXTENSION_NAME.getExtensions()) {
      if (executorId.equals(executor.getId())) {
        return executor;
      }
    }

    return null;
  }
}

