// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.tests.gui.java8;

import com.android.tools.idea.tests.gui.emulator.EmulatorGenerator;
import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.AndroidToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import org.fest.swing.util.PatternTextMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRunner.class)
public class CompileWithJava8Test {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule(false);

  private static final String CONF_NAME = "app";

  /**
   * Verifies that Compile a project with Java 8.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: d6dc23f3-33ff-4ffc-af80-6ab822388274
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import MinSdk24App project, in this project, build.gradle(Module:app) is enabled
   *      Java 8 for lambda expressions; and In MainActivity.java, the following statement
   *      is added to onCreate() method:
   *      new Thread(() ->{
   *          Log.d("TAG", "Hello World from Lambda Expression");
   *      }).start();
   *   2. Create an emulator.
   *   3. Run Build -> Rebuild Project (Project should build successfully).
   *   4. Run this activity on emulator.
   *   Verify:
   *   1. Verify if statement prints "D/TAG: Hello World from Lambda Expression" on logcat.
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.SANITY)
  public void compileWithJava8() throws Exception {
    IdeFrameFixture ideFrameFixture =
      guiTest.importProjectAndWaitForProjectSyncToFinish("MinSdk24App");

    String avdName = EmulatorGenerator.ensureDefaultAvdIsCreated(ideFrameFixture.invokeAvdManager());

    ideFrameFixture.runApp(CONF_NAME)
      .selectDevice(avdName)
      .clickOk();

    Pattern CONNECTED_APP_PATTERN = Pattern.compile(".*Connected to process.*", Pattern.DOTALL);
    ExecutionToolWindowFixture.ContentFixture runWindow = ideFrameFixture.getRunToolWindow().findContent(CONF_NAME);
    runWindow.waitForOutput(new PatternTextMatcher(CONNECTED_APP_PATTERN), EmulatorTestRule.DEFAULT_EMULATOR_WAIT_SECONDS);

    // Verify statement prints "D/TAG: Hello World from Lambda Expression" on logcat.
    AndroidToolWindowFixture androidToolWindow = ideFrameFixture.getAndroidToolWindow().selectDevicesTab().selectProcess("android.com.app");
    String logcatPrint = androidToolWindow.getLogcatPrint();
    assertThat(logcatPrint).contains("D/TAG: Hello World from Lambda Expression");
  }
}
