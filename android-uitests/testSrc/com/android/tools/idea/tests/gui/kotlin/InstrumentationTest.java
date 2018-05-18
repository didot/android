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
package com.android.tools.idea.tests.gui.kotlin;

import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditConfigurationsDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.util.PatternTextMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@RunWith(GuiTestRemoteRunner.class)
public class InstrumentationTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule();

  private static final String APP_NAME = "app";
  private static final String INSTRUMENTED_TEST_CONF_NAME = "kotlin_instrumented_test";
  private static final String ANDROID_INSTRUMENTED_TESTS = "Android Instrumented Tests";
  private static final Pattern INSTRUMENTED_TEST_OUTPUT = Pattern.compile(
    ".*adb shell am instrument .*AndroidJUnitRunner.*Tests ran to completion.*", Pattern.DOTALL);

  /**
   * Verifies default Instrumentation test case run without any errors.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: d6dbbd96-722c-4a64-8768-cc9bd3b2af39
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import KotlinInstrumentation project and wait for project sync to finish.
   *   2. Create an avd.
   *   3. Open Run/Debug Configuration Settings.
   *   4. Click on the "+" button and select Android Instrumented Tests.
   *   5. Add a name to the test.
   *   6. Select the app module and click OK.
   *   7. Run "ExampleInstrumentedTest" with test configuration created previously.
   *   Verify:
   *   1. Test runs successfully by checking the output of running the instrumented test.
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void testInstrumentation() throws Exception {
    IdeFrameFixture ideFrameFixture =
      guiTest.importProjectAndWaitForProjectSyncToFinish("KotlinInstrumentation");
    emulator.createDefaultAVD(guiTest.ideFrame().invokeAvdManager());

    ideFrameFixture.invokeMenuPath("Run", "Edit Configurations...");
    EditConfigurationsDialogFixture.find(guiTest.robot())
      .clickAddNewConfigurationButton()
      .selectConfigurationType(ANDROID_INSTRUMENTED_TESTS)
      .enterAndroidInstrumentedTestConfigurationName(INSTRUMENTED_TEST_CONF_NAME)
      .selectModuleForAndroidInstrumentedTestsConfiguration(APP_NAME)
      .clickOk();

    ideFrameFixture.runApp(INSTRUMENTED_TEST_CONF_NAME)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    ideFrameFixture.getRunToolWindow().findContent(INSTRUMENTED_TEST_CONF_NAME)
      .waitForOutput(new PatternTextMatcher(INSTRUMENTED_TEST_OUTPUT), 120);
  }
}
