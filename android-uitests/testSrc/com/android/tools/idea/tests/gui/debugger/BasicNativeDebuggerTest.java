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
package com.android.tools.idea.tests.gui.debugger;

import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.*;
import com.android.tools.idea.tests.util.NotMatchingPatternMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRunner.class)
public class BasicNativeDebuggerTest extends DebuggerTestBase {

  @Rule public final NativeDebuggerGuiTestRule guiTest = new NativeDebuggerGuiTestRule();
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule();

  @Test
  @RunIn(TestGroup.QA_UNRELIABLE)
  public void testSessionRestart() throws Exception{
    guiTest.importProjectAndWaitForProjectSyncToFinish("BasicCmakeAppForUI");
    emulator.createDefaultAVD(guiTest.ideFrame().invokeAvdManager());
    final IdeFrameFixture projectFrame = guiTest.ideFrame();

    projectFrame.invokeMenuPath("Run", "Edit Configurations...");
    EditConfigurationsDialogFixture.find(guiTest.robot())
      .selectDebuggerType("Auto")
      .clickOk();

    // Setup breakpoints
    openAndToggleBreakPoints(projectFrame,
                             "app/src/main/jni/native-lib.c",
                             "return (*env)->NewStringUTF(env, message);");

    projectFrame.debugApp(DEBUG_CONFIG_NAME)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    DebugToolWindowFixture debugToolWindowFixture = new DebugToolWindowFixture(projectFrame);
    waitForSessionStart(debugToolWindowFixture);

    projectFrame.findDebugApplicationButton().click();

    MessagesFixture errorMessage = MessagesFixture.findByTitle(guiTest.robot(), "Launching " + DEBUG_CONFIG_NAME);
    errorMessage.requireMessageContains("Restart App").click("Restart " + DEBUG_CONFIG_NAME);

    DeployTargetPickerDialogFixture deployTargetPicker = DeployTargetPickerDialogFixture.find(guiTest.robot());
    deployTargetPicker.selectDevice(emulator.getDefaultAvdName()).clickOk();

    waitUntilDebugConsoleCleared(debugToolWindowFixture);
    waitForSessionStart(debugToolWindowFixture);
    stopDebugSession(debugToolWindowFixture);
  }

  @Test
  @RunIn(TestGroup.QA_UNRELIABLE)
  public void testNativeDebuggerBreakpoints() throws Exception {
    guiTest.importProjectAndWaitForProjectSyncToFinish("BasicCmakeAppForUI");
    emulator.createDefaultAVD(guiTest.ideFrame().invokeAvdManager());
    final IdeFrameFixture projectFrame = guiTest.ideFrame();

    projectFrame.invokeMenuPath("Run", "Edit Configurations...");
    EditConfigurationsDialogFixture.find(guiTest.robot())
      .selectDebuggerType("Native")
      .clickOk();

    // Set breakpoint in java code, but it wouldn't be hit when it is native debugger type.
    openAndToggleBreakPoints(projectFrame,
                             "app/src/main/java/com/example/basiccmakeapp/MainActivity.java",
                             "setContentView(tv);");

    openAndToggleBreakPoints(projectFrame,
                             "app/src/main/jni/native-lib.c",
                             "return sum;",
                             "return product;",
                             "return quotient;",
                             "return (*env)->NewStringUTF(env, message);"
    );

    projectFrame.debugApp(DEBUG_CONFIG_NAME)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    DebugToolWindowFixture debugToolWindowFixture = new DebugToolWindowFixture(projectFrame);
    waitForSessionStart(debugToolWindowFixture);

    // Setup the expected patterns to match the variable values displayed in Debug windows's 'Variables' tab.
    String[] expectedPatterns = new String[]{
      variableToSearchPattern("x1", "int", "1"),
      variableToSearchPattern("x2", "int", "2"),
      variableToSearchPattern("x3", "int", "3"),
      variableToSearchPattern("x4", "int", "4"),
      variableToSearchPattern("x5", "int", "5"),
      variableToSearchPattern("x6", "int", "6"),
      variableToSearchPattern("x7", "int", "7"),
      variableToSearchPattern("x8", "int", "8"),
      variableToSearchPattern("x9", "int", "9"),
      variableToSearchPattern("x10", "int", "10"),
      variableToSearchPattern("sum", "int", "55"),
    };
    checkAppIsPaused(projectFrame, expectedPatterns);
    resume("app", projectFrame);

    expectedPatterns = new String[]{
      variableToSearchPattern("x1", "int", "1"),
      variableToSearchPattern("x2", "int", "2"),
      variableToSearchPattern("x3", "int", "3"),
      variableToSearchPattern("x4", "int", "4"),
      variableToSearchPattern("x5", "int", "5"),
      variableToSearchPattern("x6", "int", "6"),
      variableToSearchPattern("x7", "int", "7"),
      variableToSearchPattern("x8", "int", "8"),
      variableToSearchPattern("x9", "int", "9"),
      variableToSearchPattern("x10", "int", "10"),
      variableToSearchPattern("product", "int", "3628800"),
    };
    checkAppIsPaused(projectFrame, expectedPatterns);
    resume("app", projectFrame);

    expectedPatterns = new String[]{
      variableToSearchPattern("x1", "int", "1024"),
      variableToSearchPattern("x2", "int", "2"),
      variableToSearchPattern("quotient", "int", "512"),
    };
    checkAppIsPaused(projectFrame, expectedPatterns);
    resume("app", projectFrame);

    expectedPatterns = new String[]{
      variableToSearchPattern("sum_of_10_ints", "int", "55"),
      variableToSearchPattern("product_of_10_ints", "int", "3628800"),
      variableToSearchPattern("quotient", "int", "512")
    };
    checkAppIsPaused(projectFrame, expectedPatterns);
    assertThat(debugToolWindowFixture.getDebuggerContent("app-java")).isNull();
    stopDebugSession(debugToolWindowFixture);
  }

  /**
   * Verifies that instant run hot swap works as expected on a C++ support project.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 54d17691-48b8-4bf2-9ac2-9ff179327418
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import BasicCmakeAppForUI.
   *   2. Select auto debugger on Edit Configurations dialog.
   *   3. Set breakpoints both in Java and C++ code.
   *   4. Debug on a device running M or earlier.
   *   5. When the C++ breakpoint is hit, verify variables and resume
   *   6. When the Java breakpoint is hit, verify variables
   *   7. Stop debugging
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.QA_UNRELIABLE)
  public void testDualDebuggerBreakpoints() throws Exception {
    guiTest.importProjectAndWaitForProjectSyncToFinish("BasicCmakeAppForUI");
    emulator.createDefaultAVD(guiTest.ideFrame().invokeAvdManager());
    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();

    ideFrameFixture.invokeMenuPath("Run", "Edit Configurations...");
    EditConfigurationsDialogFixture.find(guiTest.robot())
      .selectDebuggerType("Dual")
      .clickOk();

    // Setup C++ and Java breakpoints.
    openAndToggleBreakPoints(ideFrameFixture, "app/src/main/jni/native-lib.c", "return (*env)->NewStringUTF(env, message);");
    openAndToggleBreakPoints(ideFrameFixture, "app/src/main/java/com/example/basiccmakeapp/MainActivity.java", "setContentView(tv);");

    ideFrameFixture.debugApp(DEBUG_CONFIG_NAME)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    DebugToolWindowFixture debugToolWindowFixture = new DebugToolWindowFixture(ideFrameFixture);
    waitForSessionStart(debugToolWindowFixture);

    // Setup the expected patterns to match the variable values displayed in Debug windows's 'Variables' tab.
    String[] expectedPatterns = new String[]{
      variableToSearchPattern("sum_of_10_ints", "int", "55"),
      variableToSearchPattern("product_of_10_ints", "int", "3628800"),
      variableToSearchPattern("quotient", "int", "512"),
    };
    checkAppIsPaused(ideFrameFixture, expectedPatterns);
    resume("app", ideFrameFixture);

    expectedPatterns = new String[]{
      variableToSearchPattern("s", "\"Success. Sum = 55, Product = 3628800, Quotient = 512\""),
    };
    checkAppIsPaused(ideFrameFixture, expectedPatterns);
    assertThat(debugToolWindowFixture.getDebuggerContent("app-java")).isNotNull();
    // TODO Stop the session.
  }

  private void waitUntilDebugConsoleCleared(DebugToolWindowFixture debugToolWindowFixture) {
    final ExecutionToolWindowFixture.ContentFixture contentFixture = debugToolWindowFixture.findContent(DEBUG_CONFIG_NAME);
    contentFixture.waitForOutput(new NotMatchingPatternMatcher(DEBUGGER_ATTACHED_PATTERN), 10);
  }

}
