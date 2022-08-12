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
package com.android.tools.idea.run.editor;

import static com.android.AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP;

import com.android.annotations.concurrency.Slow;
import com.android.ddmlib.Client;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.TestExecutionOption;
import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.debug.StartJavaDebuggerKt;
import com.android.tools.idea.run.tasks.ConnectDebuggerTask;
import com.android.tools.idea.run.tasks.ConnectJavaDebuggerTask;
import com.android.tools.idea.testartifacts.instrumented.orchestrator.OrchestratorUtilsKt;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionHelper;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.remote.RemoteConfiguration;
import com.intellij.execution.remote.RemoteConfigurationType;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.NotNullFunction;
import com.intellij.xdebugger.XDebugSession;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidJavaDebugger extends AndroidDebuggerImplBase<AndroidDebuggerState> {
  public static final String ID = "Java";
  private static final String RUN_CONFIGURATION_NAME_PATTERN = "Android Debugger (%s)";

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Java Only";
  }

  @NotNull
  @Override
  public AndroidDebuggerState createState() {
    return new AndroidDebuggerState();
  }

  @NotNull
  @Override
  public AndroidDebuggerConfigurable<AndroidDebuggerState> createConfigurable(@NotNull RunConfiguration runConfiguration) {
    return new AndroidDebuggerConfigurable<>();
  }

  @NotNull
  @Override
  public ConnectDebuggerTask getConnectDebuggerTask(@NotNull ExecutionEnvironment env,
                                                    @NotNull ApplicationIdProvider applicationIdProvider,
                                                    @NotNull AndroidFacet facet,
                                                    @NotNull AndroidDebuggerState state) {
    ConnectJavaDebuggerTask baseConnector = new ConnectJavaDebuggerTask(
      applicationIdProvider, env.getProject(),
      facet.getConfiguration().getProjectType() == PROJECT_TYPE_INSTANTAPP);
    if (env.getRunProfile() instanceof AndroidRunConfiguration) {
      return baseConnector;
    }
    TestExecutionOption executionType = Optional.ofNullable(AndroidModel.get(facet))
      .map(AndroidModel::getTestExecutionOption)
      .orElse(TestExecutionOption.HOST);
    switch (executionType) {
      case ANDROID_TEST_ORCHESTRATOR:
      case ANDROIDX_TEST_ORCHESTRATOR:
        return OrchestratorUtilsKt.createReattachingConnectDebuggerTask(baseConnector, executionType);
      default:
        return baseConnector;
    }
  }

  @Override
  public boolean supportsProject(@NotNull Project project) {
    return true;
  }

  @Slow
  @Override
  public void attachToClient(@NotNull Project project, @NotNull Client client, @Nullable AndroidDebuggerState debugState) {
    String debugPort = getClientDebugPort(client);
    String runConfigName = getRunConfigurationName(debugPort);

    // Try to find existing debug session
    if (hasExistingDebugSession(project, debugPort, runConfigName)) {
      return;
    }

    StartJavaDebuggerKt.attachJavaDebuggerToClientAndShowTab(project, client);
  }

  @NotNull
  public static String getRunConfigurationName(@NotNull String debugPort) {
    return String.format(RUN_CONFIGURATION_NAME_PATTERN, debugPort);
  }

  public static boolean hasExistingDebugSession(@NotNull Project project,
                                                @NotNull final String debugPort,
                                                @NotNull final String runConfigName) {
    Collection<RunContentDescriptor> descriptors = null;
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    Project targetProject;

    // Scan through open project to find if this port has been opened in any session.
    for (Project openProject : openProjects) {
      targetProject = openProject;

      // First check the titles of the run configurations.
      descriptors = ExecutionHelper.findRunningConsoleByTitle(targetProject, new NotNullFunction<String, Boolean>() {
        @NotNull
        @Override
        public Boolean fun(String title) {
          return runConfigName.equals(title);
        }
      });

      // If it can't find a matching title, check the debugger sessions.
      if (descriptors.isEmpty()) {
        DebuggerSession debuggerSession = findJdwpDebuggerSession(targetProject, debugPort);
        if (debuggerSession != null) {
          XDebugSession session = debuggerSession.getXDebugSession();
          if (session != null) {
            descriptors = Collections.singletonList(session.getRunContentDescriptor());
          }
          else {
            // Detach existing session.
            debuggerSession.getProcess().stop(false);
          }
        }
      }

      if (!descriptors.isEmpty()) {
        break;
      }
    }

    if (descriptors != null && !descriptors.isEmpty()) {
      return activateDebugSessionWindow(project, descriptors.iterator().next());
    }
    return false;
  }
}
