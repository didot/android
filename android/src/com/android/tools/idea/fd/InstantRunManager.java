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
package com.android.tools.idea.fd;

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.run.AndroidSessionInfo;
import com.android.tools.idea.run.InstalledPatchCache;
import com.android.tools.ir.client.InstantRunBuildInfo;
import com.android.tools.ir.client.InstantRunClient;
import com.android.tools.ir.client.InstantRunPushFailedException;
import com.android.tools.ir.client.UpdateMode;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableSet;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.JavaExecutionStack;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XExecutionStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * The {@linkplain InstantRunManager} is responsible for handling Instant Run related functionality
 * in the IDE: determining if an app is running with the fast deploy runtime, whether it's up to date, communicating with it, etc.
 */
public final class InstantRunManager {
  public static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.toolWindowGroup("InstantRun", ToolWindowId.RUN);

  public static final Logger LOG = Logger.getInstance("#InstantRun");
  public static final ILogger ILOGGER = new LogWrapper(LOG);

  /**
   * White list of processes whose presence will not disable hotswap.
   *
   * Instant Run (hotswap) does not work with multiple processes right now. If we detect that the app uses multiple processes,
   * we always force a cold swap. However, a common scenario is where an app uses multiple processes, but just for the purpose of
   * a 3rd party library (e.g. leakcanary). In this case, we are ok doing a hotswap to just the main process (assuming that the
   * main process starts up first).
   */
  public static final ImmutableSet<String> ALLOWED_MULTI_PROCESSES =
    ImmutableSet.of(":leakcanary",
                    ":background_crash"); // firebase uses a :background_crash process for crash reporting
  public static final int MIN_IR_API_VERSION = 21;

  @NotNull private final Project myProject;

  @NotNull
  public static InstantRunManager get(@NotNull Project project) {
    return ServiceManager.getService(project, InstantRunManager.class);
  }

  private InstantRunManager(@NotNull Project project) {
    myProject = project;
  }

  @Nullable
  public static AndroidVersion getMinDeviceApiLevel(@NotNull ProcessHandler processHandler) {
    return processHandler.getUserData(AndroidSessionInfo.ANDROID_DEVICE_API_LEVEL);
  }

  /**
   * Called after a build &amp; successful push to device: updates the build id on the device to whatever the
   * build id was assigned by Gradle.
   *
   * @param device the device to push to
   * @param module a module context, normally the main app module (but if it's a library module
   *               the infrastructure will look for other app modules
   */
  public static void transferLocalIdToDeviceId(@NotNull IDevice device, @NotNull InstantRunContext context) {
    InstantRunBuildInfo buildInfo = context.getInstantRunBuildInfo();
    assert buildInfo != null;
    String localTimestamp = buildInfo.getTimeStamp();
    assert !StringUtil.isEmpty(localTimestamp) : "Unable to detect build timestamp";

    InstantRunClient.transferBuildIdToDevice(device, localTimestamp, context.getApplicationId(), ILOGGER);
  }

  /** Returns true if the device is capable of running Instant Run */
  public static boolean isInstantRunCapableDeviceVersion(@NotNull AndroidVersion version) {
    return version.getApiLevel() >= MIN_IR_API_VERSION;
  }

  public static boolean hasLocalCacheOfDeviceData(@NotNull IDevice device, @NotNull InstantRunContext context) {
    InstalledPatchCache cache = context.getInstalledPatchCache();
    return cache.getInstalledManifestResourcesHash(device, context.getApplicationId()) != null;
  }

  @Nullable
  public static InstantRunClient getInstantRunClient(@NotNull InstantRunContext context) {
    InstantRunBuildInfo buildInfo = context.getInstantRunBuildInfo();
    if (buildInfo == null) {
      // we always obtain the secret token from the build info, and if a build info doesn't exist,
      // there is no point connecting to the app, we'll be doing a clean build anyway
      return null;
    }

    return new InstantRunClient(context.getApplicationId(), ILOGGER, buildInfo.getSecretToken());
  }

  /**
   * Pushes the artifacts obtained from the {@link InstantRunContext} to the given device.
   * If the app is running, the artifacts are sent directly to the server running as part of the app.
   * Otherwise, we save it to a file on the device.
   */
  public UpdateMode pushArtifacts(@NotNull IDevice device,
                                  @NotNull InstantRunContext context,
                                  @NotNull UpdateMode updateMode) throws InstantRunPushFailedException, IOException {
    InstantRunClient client = getInstantRunClient(context);
    assert client != null;

    InstantRunBuildInfo instantRunBuildInfo = context.getInstantRunBuildInfo();
    assert instantRunBuildInfo != null;

    updateMode = client.pushPatches(device,
                                    instantRunBuildInfo,
                                    updateMode,
                                    InstantRunSettings.isRestartActivity(),
                                    InstantRunSettings.isShowToastEnabled());

    if ((updateMode == UpdateMode.HOT_SWAP || updateMode == UpdateMode.WARM_SWAP)) {
      refreshDebugger(context.getApplicationId());
    }

    return updateMode;
  }

  private void refreshDebugger(@NotNull String packageName) {
    // First we reapply the breakpoints on the new code, otherwise the breakpoints
    // remain set on the old classes and will never be hit again.
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        DebuggerManagerEx debugger = DebuggerManagerEx.getInstanceEx(myProject);
        if (!debugger.getSessions().isEmpty()) {
          List<Breakpoint> breakpoints = debugger.getBreakpointManager().getBreakpoints();
          for (Breakpoint breakpoint : breakpoints) {
            if (breakpoint.isEnabled()) {
              breakpoint.setEnabled(false);
              breakpoint.setEnabled(true);
            }
          }
        }
      }
    });

    // Now we refresh the call-stacks and the variable panes.
    DebuggerManagerEx debugger = DebuggerManagerEx.getInstanceEx(myProject);
    for (final DebuggerSession session : debugger.getSessions()) {
      Client client = session.getProcess().getProcessHandler().getUserData(AndroidSessionInfo.ANDROID_DEBUG_CLIENT);
      if (client != null && client.isValid() && StringUtil.equals(packageName, client.getClientData().getClientDescription())) {
        session.getProcess().getManagerThread().invoke(new DebuggerCommandImpl() {
          @Override
          protected void action() throws Exception {
            DebuggerContextImpl context = session.getContextManager().getContext();
            SuspendContextImpl suspendContext = context.getSuspendContext();
            if (suspendContext != null) {
              XExecutionStack stack = suspendContext.getActiveExecutionStack();
              if (stack != null) {
                ((JavaExecutionStack)stack).initTopFrame();
              }
            }
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                session.refresh(false);
                XDebugSession xSession = session.getXDebugSession();
                if (xSession != null) {
                  xSession.resume();
                }
              }
            });
          }
        });
      }
    }
  }
}
