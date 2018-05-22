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
package com.android.tools.idea.run.tasks;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.InstallException;
import com.android.tools.idea.fd.BuildSelection;
import com.android.tools.idea.fd.InstantRunContext;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.stats.RunStatsService;
import com.android.tools.idea.stats.RunStatsServiceImpl;
import com.android.tools.ir.client.InstantRunBuildInfo;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.android.tools.idea.fd.BuildCause.APP_NOT_INSTALLED;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

public class SplitApkDeployTaskInstantRunTest {
  private static final String PACKAGE_NAME = "com.somepackage";
  @Mock private Project myProject;
  @Mock private InstantRunContext myContext;
  @Mock private InstantRunBuildInfo myBuildInfo;
  @Mock private IDevice myDevice;
  @Mock private IDevice myEmbeddedDevice;
  @Mock private LaunchStatus myLaunchStatus;
  @Mock private ConsolePrinter myPrinter;

  @Before
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    when(myEmbeddedDevice.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(true);
    when(myContext.getInstantRunBuildInfo()).thenReturn(myBuildInfo);
    when(myContext.getApplicationId()).thenReturn(PACKAGE_NAME);
    when(myContext.getBuildSelection()).thenReturn(new BuildSelection(APP_NOT_INSTALLED, false));
    RunStatsService.setTestOverride(Mockito.mock(RunStatsService.class, Answers.RETURNS_DEEP_STUBS));
  }

  @After
  public void teardown() {
    RunStatsService.setTestOverride(null);
  }

  @Test
  public void testPerformOnEmbedded() throws Throwable {
    MyDeployContext deployContext = new MyDeployContext(myContext);
    SplitApkDeployTask task = new SplitApkDeployTask(myProject, deployContext);
    answerInstallOptions(myEmbeddedDevice, installOptions -> assertThat(installOptions).containsExactly("-t", "-g"));
    assertTrue(task.perform(myEmbeddedDevice, myLaunchStatus, myPrinter));
    assertTrue(deployContext.isNotified());
  }

  @Test
  public void testPerformOnNonEmbeddedDevice() throws Throwable {
    MyDeployContext deployContext = new MyDeployContext(myContext);
    SplitApkDeployTask task = new SplitApkDeployTask(myProject, deployContext);
    answerInstallOptions(myDevice, installOptions -> assertThat(installOptions).containsExactly("-t"));
    assertTrue(task.perform(myDevice, myLaunchStatus, myPrinter));
    assertTrue(deployContext.isNotified());
  }

  private static void answerInstallOptions(@NotNull IDevice device,
                                           @NotNull Consumer<List<String>> checkOptions) throws InstallException {
    doAnswer(new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock invocation) {
        Object[] args = invocation.getArguments();
        //noinspection unchecked
        List<String> installOptions = (List<String>)args[2];
        checkOptions.accept(installOptions);
        return null;
      }
    }).when(device).installPackages(anyList(), anyBoolean(), anyList(), anyLong(), any(TimeUnit.class));
  }

  private static class MyDeployContext extends SplitApkDeployTaskInstantRunContext {
    private boolean myNotified;

    public MyDeployContext(@NotNull InstantRunContext context) {
      super(context);
    }

    @Override
    public void notifyInstall(@NotNull Project project, @NotNull IDevice device, boolean status) {
      // Don't call the super class implementation because we did not mock InstantRunStatsService.
      myNotified = true;
    }

    public boolean isNotified() {
      return myNotified;
    }
  }
}
