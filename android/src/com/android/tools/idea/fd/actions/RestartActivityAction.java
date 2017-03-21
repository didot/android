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
package com.android.tools.idea.fd.actions;

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.tools.fd.client.AppState;
import com.android.tools.fd.client.InstantRunClient;
import com.android.tools.idea.fd.InstantRunContext;
import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.fd.InstantRunSettings;
import com.android.tools.idea.fd.gradle.InstantRunGradleSupport;
import com.android.tools.idea.fd.gradle.InstantRunGradleUtils;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.run.AndroidProcessHandler;
import com.android.tools.idea.run.AndroidSessionInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Action which restarts an activity in the running app
 */
public class RestartActivityAction extends AnAction {
  public RestartActivityAction() {
    super("Restart Activity", null, AndroidIcons.RunIcons.Restart);
  }

  @Override
  public void update(AnActionEvent e) {
    Module module = LangDataKeys.MODULE.getData(e.getDataContext());

    if (module == null) {
      e.getPresentation().setEnabled(false);
      e.getPresentation().setDescription(null);
      return;
    }

    if (!InstantRunSettings.isInstantRunEnabled()) {
      e.getPresentation().setEnabled(false);
      e.getPresentation().setDescription("Restart Activity (requires Instant Run to be enabled)");
      return;
    }

    AndroidModuleModel model = InstantRunGradleUtils.getAppModel(module);
    if (model == null) {
      e.getPresentation().setEnabled(false);
      return;
    }

    Project project = module.getProject();
    boolean gradleSupportsIr = InstantRunGradleUtils.getIrSupportStatus(model, null) == InstantRunGradleSupport.SUPPORTED;
    boolean hasActiveSession = !getActiveSessions(project).isEmpty();
    boolean isPausedInDebugger = isDebuggerPaused(project);
    boolean enabled = gradleSupportsIr && hasActiveSession && !isPausedInDebugger;
    e.getPresentation().setEnabled(enabled);

    if (!hasActiveSession) {
      e.getPresentation().setDescription("Restart Activity (requires an active debug session)");
    }
    else if (!isPausedInDebugger) {
      e.getPresentation().setDescription("Restart Activity (requires the app to not be stopped at a breakpoint)");
    }
  }

  private static List<ProcessHandler> getActiveSessions(@Nullable Project project) {
    if (project == null) {
      return Collections.emptyList();
    }

    List<ProcessHandler> activeHandlers = Lists.newArrayList();
    for (ProcessHandler handler : ExecutionManager.getInstance(project).getRunningProcesses()) {
      if (!handler.isProcessTerminated() && !handler.isProcessTerminating()) {
        activeHandlers.add(handler);
      }
    }
    return activeHandlers;
  }

  public static boolean isDebuggerPaused(@Nullable Project project) {
    if (project == null) {
      return false;
    }

    XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    return session != null && !session.isStopped() && session.isPaused();
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Module module = LangDataKeys.MODULE.getData(e.getDataContext());
    if (module == null) {
      return;
    }

    InstantRunContext context = InstantRunGradleUtils.createGradleProjectContext(module);
    if (context == null) {
      Logger.getInstance(RestartActivityAction.class).info("Unable to obtain instant run context for module: " + module.getName());
      return;
    }
    restartActivity(module.getProject(), context);
  }

  /**
   * Restarts the activity associated with the given module
   */
  public static void restartActivity(@NotNull Project project, @NotNull InstantRunContext instantRunContext) {
    for (IDevice device : findDevices(project)) {
      InstantRunClient instantRunClient = InstantRunManager.getInstantRunClient(instantRunContext);
      if (instantRunClient == null) {
        Logger.getInstance(RestartActivityAction.class).warn("Unable to connect to to app running on device, not restarting.");
        return;
      }

      try {
        if (instantRunClient.getAppState(device) == AppState.FOREGROUND) {
          instantRunClient.restartActivity(device);
          if (InstantRunSettings.isShowToastEnabled()) {
            showToast(device, instantRunContext, "Activity Restarted");
          }
        }
      } catch (IOException e) {
        Messages.showErrorDialog(project, "Unable to restart activity: " + e, "Instant Run");
        InstantRunManager.LOG.warn("Unable to restart activity", e);
      }
    }
  }

  /**
   * Finds the devices associated with all run configurations for the given project
   */
  @NotNull
  private static List<IDevice> findDevices(@Nullable Project project) {
    if (project == null) {
      return Collections.emptyList();
    }

    List<RunContentDescriptor> runningProcesses = ExecutionManager.getInstance(project).getContentManager().getAllDescriptors();
    if (runningProcesses.isEmpty()) {
      return Collections.emptyList();
    }
    List<IDevice> devices = Lists.newArrayList();
    for (RunContentDescriptor descriptor : runningProcesses) {
      ProcessHandler processHandler = descriptor.getProcessHandler();
      if (processHandler == null || processHandler.isProcessTerminated() || processHandler.isProcessTerminating()) {
        continue;
      }

      devices.addAll(getConnectedDevices(processHandler));
    }

    return devices;
  }

  @NotNull
  private static List<IDevice> getConnectedDevices(@NotNull ProcessHandler processHandler) {
    if (processHandler.isProcessTerminated() || processHandler.isProcessTerminating()) {
      return Collections.emptyList();
    }

    if (processHandler instanceof AndroidProcessHandler) {
      return ImmutableList.copyOf(((AndroidProcessHandler)processHandler).getDevices());
    }
    else {
      Client c = processHandler.getUserData(AndroidSessionInfo.ANDROID_DEBUG_CLIENT);
      if (c != null && c.isValid()) {
        return Collections.singletonList(c.getDevice());
      }
    }

    return Collections.emptyList();
  }

  private static void showToast(@NotNull IDevice device, @NotNull InstantRunContext context, @NotNull final String message) {
    try {
      InstantRunClient instantRunClient = InstantRunManager.getInstantRunClient(context);
      if (instantRunClient == null) {
        InstantRunManager.LOG.warn("Cannot connect to app, not showing toast");
        return;
      }
      instantRunClient.showToast(device, message);
    }
    catch (Throwable e) {
      InstantRunManager.LOG.warn(e);
    }
  }
}
