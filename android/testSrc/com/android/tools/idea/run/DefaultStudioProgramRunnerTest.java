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
package com.android.tools.idea.run;

import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.run.deployment.AndroidExecutionTarget;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.command.impl.DummyProject;
import com.intellij.util.ThreeState;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class DefaultStudioProgramRunnerTest {
  @Mock private AndroidExecutionTarget target;
  @Mock private AndroidRunConfigurationBase runConfig;
  @Mock private RunProfile runProfile;
  @Mock private GradleSyncState syncState;

  @Before
  public void before() {
    MockitoAnnotations.initMocks(this);
  }

  /**
   * Checks if the program runner is disabled when gradle is or needs syncing.
   */
  @Test
  public void gradleCanRun() {
    Mockito.when(target.getRunningDevices()).thenReturn(Collections.emptyList());
    Mockito.when(runConfig.getProject()).thenReturn(DummyProject.getInstance());

    DefaultStudioProgramRunner runner = new DefaultStudioProgramRunner(p -> syncState, p -> target);

    // Check that the program runner doesn't support non-AndroidRunConfigurationBase profiles.
    Mockito.when(syncState.isSyncInProgress()).thenReturn(false);
    Mockito.when(syncState.isSyncNeeded()).thenReturn(ThreeState.NO);
    Assert.assertFalse(runner.canRun(DefaultRunExecutor.EXECUTOR_ID, runProfile));

    // Check that the program runner can run when Gradle is ready.
    Mockito.when(syncState.isSyncInProgress()).thenReturn(false);
    Mockito.when(syncState.isSyncNeeded()).thenReturn(ThreeState.NO);
    Assert.assertTrue(runner.canRun(DefaultRunExecutor.EXECUTOR_ID, runConfig));

    // Check that the program runner cannot run when Gradle is syncing.
    Mockito.when(syncState.isSyncInProgress()).thenReturn(true);
    Mockito.when(syncState.isSyncNeeded()).thenReturn(ThreeState.NO);
    Assert.assertFalse(runner.canRun(DefaultRunExecutor.EXECUTOR_ID, runConfig));

    // Check that the program runner cannot run when Gradle needs syncing.
    Mockito.when(syncState.isSyncInProgress()).thenReturn(false);
    Mockito.when(syncState.isSyncNeeded()).thenReturn(ThreeState.YES);
    Assert.assertFalse(runner.canRun(DefaultRunExecutor.EXECUTOR_ID, runConfig));

    // Check that the program runner cannot run when Gradle isn't sure if it needs to sync.
    Mockito.when(syncState.isSyncInProgress()).thenReturn(false);
    Mockito.when(syncState.isSyncNeeded()).thenReturn(ThreeState.UNSURE);
    Assert.assertFalse(runner.canRun(DefaultRunExecutor.EXECUTOR_ID, runConfig));

    // Check that the program runner cannot run when Gradle is completely out of whack.
    Mockito.when(syncState.isSyncInProgress()).thenReturn(true);
    Mockito.when(syncState.isSyncNeeded()).thenReturn(ThreeState.YES);
    Assert.assertFalse(runner.canRun(DefaultRunExecutor.EXECUTOR_ID, runConfig));
  }
}
