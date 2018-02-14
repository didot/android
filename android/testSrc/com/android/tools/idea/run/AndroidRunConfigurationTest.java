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

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.tasks.ActivityLaunchTask;
import com.android.tools.idea.run.util.LaunchStatus;
import com.intellij.execution.configurations.ConfigurationFactory;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mockito;

public class AndroidRunConfigurationTest extends AndroidTestCase {
  private AndroidRunConfiguration myRunConfiguration;
  private IDevice myDevice;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    ConfigurationFactory configurationFactory = AndroidRunConfigurationType.getInstance().getFactory();
    myRunConfiguration = new AndroidRunConfiguration(getProject(), configurationFactory);
    myDevice = Mockito.mock(IDevice.class);
  }

  public void testContributorsAmStartOptionsIsInlinedWithAmStartCommand() {
    myRunConfiguration.setLaunchActivity("MyActivity");

    LaunchStatus launchStatus = Mockito.mock(LaunchStatus.class);
    ActivityLaunchTask task = (ActivityLaunchTask)myRunConfiguration.getApplicationLaunchTask(new FakeApplicationIdProvider(),
                                                                                              myFacet,
                                                                                              "--start-profiling",
                                                                                              false,
                                                                                              launchStatus);

    assertEquals("am start -n \"com.example.mypackage/MyActivity\" " +
                 "-a android.intent.action.MAIN -c android.intent.category.LAUNCHER " +
                 "--start-profiling", task.getStartActivityCommand(myDevice, launchStatus, Mockito.mock(ConsolePrinter.class)));
  }

  public void testEmptyContributorsAmStartOptions() {
    myRunConfiguration.setLaunchActivity("MyActivity");

    LaunchStatus launchStatus = Mockito.mock(LaunchStatus.class);
    ActivityLaunchTask task = (ActivityLaunchTask)myRunConfiguration.getApplicationLaunchTask(new FakeApplicationIdProvider(),
                                                                                              myFacet,
                                                                                              "",
                                                                                              false,
                                                                                              launchStatus);
    assertEquals("am start -n \"com.example.mypackage/MyActivity\" " +
                 "-a android.intent.action.MAIN -c android.intent.category.LAUNCHER",
                 task.getStartActivityCommand(myDevice, launchStatus, Mockito.mock(ConsolePrinter.class)));
  }

  private static class FakeApplicationIdProvider implements ApplicationIdProvider {
    @NotNull
    @Override
    public String getPackageName() {
      return "com.example.mypackage";
    }

    @Nullable
    @Override
    public String getTestPackageName() {
      return "com.example.test.mypackage";
    }
  }
}
