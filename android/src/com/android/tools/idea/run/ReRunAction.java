/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.fd.InstantRunUtils;
import com.android.tools.idea.gradle.actions.AndroidStudioGradleAction;
import com.intellij.execution.Executor;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * {@link ReRunAction} will terminate and run the currently selected and running configuration.
 * Under certain circumstances (see {@link #isCleanBuild(AnActionEvent)}), it will also do a clean before the run.
 */
public class ReRunAction extends AndroidStudioGradleAction implements AnAction.TransparentUpdate {
  public ReRunAction() {
    super("Rerun", "ReRun Selected Configuration", AllIcons.Actions.Restart);
  }

  @Override
  public void doUpdate(@NotNull AnActionEvent e, @NotNull Project project) {
    Presentation presentation = e.getPresentation();
    boolean cleanBuild = isCleanBuild(e);

    RunnerAndConfigurationSettings settings = RunManagerEx.getInstanceEx(project).getSelectedConfiguration();
    ProcessHandler processHandler = getActiveProcessHandler(project, settings);

    boolean en = cleanBuild || processHandler != null;
    presentation.setEnabled(en);
    presentation.setIcon(cleanBuild ? AndroidIcons.RunIcons.CleanRerun : AllIcons.Actions.Restart);
    presentation.setText(getActionText(cleanBuild, settings, processHandler));
  }

  @NotNull
  private static String getActionText(boolean cleanBuild,
                                      @Nullable RunnerAndConfigurationSettings settings,
                                      @Nullable ProcessHandler processHandler) {
    StringBuilder text = new StringBuilder(30);

    if (cleanBuild) {
      text.append("Clean and ");
    }

    text.append("Rerun");

    if (settings != null && processHandler != null) {
      text.append('\'');
      text.append(settings.getName());
      text.append('\'');
    }

    return text.toString();
  }

  @Nullable
  private static ProcessHandler getActiveProcessHandler(@Nullable Project project, @Nullable RunnerAndConfigurationSettings settings) {
    if (project == null || settings == null) {
      return null;
    }

    AndroidSessionInfo session = getAndroidSessionInfo(project, settings);
    if (session == null) {
      return null;
    }

    ProcessHandler processHandler = session.getProcessHandler();
    if (processHandler.isProcessTerminated() || processHandler.isProcessTerminating()) {
      return null;
    }

    return processHandler;
  }

  @Override
  public void doPerform(@NotNull AnActionEvent e, @NotNull Project project) {
    RunnerAndConfigurationSettings settings = RunManagerEx.getInstanceEx(project).getSelectedConfiguration();
    if (settings == null) {
      InstantRunManager.LOG.warn("Rerun could not locate current run config settings");
      return;
    }

    AndroidSessionInfo session = getAndroidSessionInfo(project, settings);
    if (session == null) {
      InstantRunManager.LOG.warn("Rerun could not locate an existing session for selected run config.");
      // in such a case, this action behaves as if it was a "clean and run"
    }

    Executor executor = session == null ? DefaultRunExecutor.getRunExecutorInstance() : getExecutor(session.getExecutorId());
    if (executor == null) {
      InstantRunManager.LOG.warn("Rerun could not identify executor for rerun");
      return;
    }

    ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder.createOrNull(executor, settings);
    if (builder == null) {
      InstantRunManager.LOG.warn("Rerun could not construct an env");
      return;
    }
    ExecutionEnvironment env = builder.activeTarget().dataContext(e.getDataContext()).build();

    InstantRunUtils.setReRun(env, true);

    boolean cleanBuild = isCleanBuild(e);
    InstantRunUtils.setCleanReRun(env, cleanBuild);
    InstantRunManager.LOG.info("Rerun: clean? " + cleanBuild);

    ProgramRunnerUtil.executeConfiguration(env, false, true);
  }

  @Nullable
  private static AndroidSessionInfo getAndroidSessionInfo(Project project, RunnerAndConfigurationSettings settings) {
    RunConfiguration configuration = settings.getConfiguration();
    if (configuration == null) {
      return null;
    }

    AndroidSessionInfo session = AndroidSessionInfo.findOldSession(project, null, configuration.getUniqueID());
    if (session == null) {
      return null;
    }

    return session;
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

  @Override
  public boolean isDumbAware() {
    return true;
  }

  private static boolean isCleanBuild(@NotNull AnActionEvent e) {
    // invoked from the menu
    if (ActionPlaces.isMainMenuOrActionSearch(e.getPlace())) {
      // For some reason, keyboard shortcuts are also reporting place = "MainMenu"
      // so disambiguate keyboard shortcut invocations based on the input event type
      if (e.getInputEvent() instanceof KeyEvent) {
        return false;
      }

      return true;
    }

    // or Shift was pressed while clicking on this action
    if (e.getInputEvent() instanceof MouseEvent) {
      if ((e.getModifiers() & InputEvent.SHIFT_MASK) == InputEvent.SHIFT_MASK) {
        return true;
      }
    }

    return false;
  }
}
