// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.android.tools.idea.run;

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidSessionInfo {
  public static final Key<AndroidSessionInfo> KEY = new Key<>("KEY");
  public static final Key<Client> ANDROID_DEBUG_CLIENT = new Key<>("ANDROID_DEBUG_CLIENT");
  public static final Key<AndroidVersion> ANDROID_DEVICE_API_LEVEL = new Key<>("ANDROID_DEVICE_API_LEVEL");

  @NotNull private final ProcessHandler myProcessHandler;
  private final RunContentDescriptor myDescriptor;
  @NotNull private final String myExecutorId;
  @NotNull private final String myExecutorActionName;
  private final int myRunConfigId;
  @NotNull private final ExecutionTarget myExecutionTarget;

  public AndroidSessionInfo(@NotNull ProcessHandler processHandler,
                            @NotNull RunContentDescriptor descriptor,
                            int runConfigId,
                            @NotNull String executorId,
                            @NotNull String executorActionName,
                            @NotNull ExecutionTarget executionTarget) {
    myProcessHandler = processHandler;
    myDescriptor = descriptor;
    myRunConfigId = runConfigId;
    myExecutorId = executorId;
    myExecutorActionName = executorActionName;
    myExecutionTarget = executionTarget;
  }

  @NotNull
  public ProcessHandler getProcessHandler() {
    return myProcessHandler;
  }

  @NotNull
  public RunContentDescriptor getDescriptor() {
    return myDescriptor;
  }

  @NotNull
  public String getExecutorId() {
    return myExecutorId;
  }

  @Nullable
  public Executor getExecutor() {
    return ExecutorRegistry.getInstance().getExecutorById(getExecutorId());
  }

  @NotNull
  public String getExecutorActionName() {
    return myExecutorActionName;
  }

  @NotNull
  public ExecutionTarget getExecutionTarget() {
    return myExecutionTarget;
  }

  public int getRunConfigurationId() {
    return myRunConfigId;
  }

  @Nullable
  public static AndroidSessionInfo findOldSession(@NotNull Project project,
                                                  @Nullable Executor executor,
                                                  int currentID,
                                                  @NotNull ExecutionTarget executionTarget) {
    // Note: There are 2 alternatives here:
    //    1. ExecutionManager.getInstance(project).getContentManager().getAllDescriptors()
    //    2. ExecutionManagerImpl.getInstance(project).getRunningDescriptors
    // The 2nd one doesn't work since its implementation relies on the same run descriptor to be alive as the one that is launched,
    // but that doesn't work for android debug sessions where we have 2 process handlers (one while installing and another while debugging)
    for (ProcessHandler handler : ExecutionManager.getInstance(project).getRunningProcesses()) {
      if (handler.isProcessTerminated() || handler.isProcessTerminating()) {
        continue;
      }

      AndroidSessionInfo info = handler.getUserData(KEY);

      if (info != null &&
          currentID == info.getRunConfigurationId() &&
          (executor == null || executor.getId().equals(info.getExecutorId())) &&
          executionTarget.equals(info.getExecutionTarget())) {
        return info;
      }
    }
    return null;
  }
}
