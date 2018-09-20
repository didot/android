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
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.DebugToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.DeployTargetPickerDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewModuleWizardFixture;
import com.android.tools.idea.tests.util.NotMatchingPatternMatcher;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.timing.Wait;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class BasicNativeDebuggerTest extends DebuggerTestBase {

  @Rule public final GuiTestRule guiTest =
    new NativeDebuggerGuiTestRule().withTimeout(7, TimeUnit.MINUTES);
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule();

  private static final String C_FILE_NAME = "app/src/main/jni/native-lib.c";
  private static final String C_BP_LINE = "return (*env)->NewStringUTF(env, message);";
  private static final String JAVA_FILE_NAME = "app/src/main/java/com/example/basiccmakeapp/MainActivity.java";
  private static final String JAVA_BP_LINE = "setContentView(tv);";

  @Before
  public void setUp() throws Exception {
    guiTest.importProject("BasicCmakeAppForUI");
    guiTest.ideFrame().waitForGradleProjectSyncToFinish(Wait.seconds(60));

    emulator.createDefaultAVD(guiTest.ideFrame().invokeAvdManager());
  }

  /**
   * <p>TT ID: TODO this test case needs a TT ID.
   *
   */
  @Test
  @RunIn(TestGroup.QA_UNRELIABLE) // b/72699808
  public void testSessionRestart() throws Exception{
    final IdeFrameFixture projectFrame = guiTest.ideFrame();
    DebuggerTestUtil.setDebuggerType(projectFrame, DebuggerTestUtil.AUTO);
    openAndToggleBreakPoints(projectFrame, C_FILE_NAME, C_BP_LINE);

    DebugToolWindowFixture debugToolWindowFixture =
      DebuggerTestUtil.debugAppAndWaitForSessionToStart(projectFrame, guiTest, DEBUG_CONFIG_NAME, emulator.getDefaultAvdName());

    projectFrame.findDebugApplicationButton().click();

    DeployTargetPickerDialogFixture deployTargetPicker = DeployTargetPickerDialogFixture.find(guiTest.robot());
    deployTargetPicker.selectDevice(emulator.getDefaultAvdName()).clickOk();

    waitUntilDebugConsoleCleared(debugToolWindowFixture);
    waitForSessionStart(debugToolWindowFixture);
    stopDebugSession(debugToolWindowFixture);
  }

  /**
   * Verify native debugger is attached to a running process.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 45e4c839-5c55-40f7-8264-4fe75ee02624
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import BasicCmakeAppForUI.
   *   2. Select Native debugger on Edit Configurations dialog.
   *   3. Set breakpoints both in Java and C++ code.
   *   4. Debug on a device running M or earlier.
   *   5. Verify that only native debugger is attached and running.
   *   6. Stop debugging.
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.QA_UNRELIABLE) // b/114304149, fast
  public void testNativeDebuggerBreakpoints() throws Exception {
    final IdeFrameFixture projectFrame = guiTest.ideFrame();

    DebuggerTestUtil.setDebuggerType(projectFrame, DebuggerTestUtil.NATIVE);

    // Set breakpoint in java code, but it wouldn't be hit when it is native debugger type.
    openAndToggleBreakPoints(projectFrame, JAVA_FILE_NAME, JAVA_BP_LINE);

    openAndToggleBreakPoints(projectFrame, C_FILE_NAME, C_BP_LINE);

    DebugToolWindowFixture debugToolWindowFixture =
      DebuggerTestUtil.debugAppAndWaitForSessionToStart(projectFrame, guiTest, DEBUG_CONFIG_NAME, emulator.getDefaultAvdName());

    String[] expectedPatterns = new String[]{
      variableToSearchPattern("sum_of_10_ints", "int", "55"),
      variableToSearchPattern("product_of_10_ints", "int", "3628800"),
      variableToSearchPattern("quotient", "int", "512")
    };
    checkAppIsPaused(projectFrame, expectedPatterns);
    assertThat(debugToolWindowFixture.getDebuggerContent(DebuggerTestUtil.JAVA_DEBUGGER_CONF_NAME)).isNull();
    stopDebugSession(debugToolWindowFixture);
  }

  @Test
  @RunIn(TestGroup.QA_UNRELIABLE)
  public void testNativeDebuggerCleanWhileDebugging() throws  Exception {
    final IdeFrameFixture projectFrame = guiTest.ideFrame();

    DebuggerTestUtil.setDebuggerType(projectFrame, DebuggerTestUtil.NATIVE);

    openAndToggleBreakPoints(projectFrame, C_FILE_NAME, C_BP_LINE);

    DebugToolWindowFixture debugToolWindowFixture =
      DebuggerTestUtil.debugAppAndWaitForSessionToStart(projectFrame, guiTest, DEBUG_CONFIG_NAME, emulator.getDefaultAvdName());

    projectFrame.invokeMenuPath("Build", "Clean Project");
    MessagesFixture messagesFixture = MessagesFixture.findByTitle(guiTest.robot(), "Terminate debugging");
    // Cancel and check that the debugging session is still happening.
    messagesFixture.clickCancel();
    checkAppIsPaused(projectFrame, new String[]{});

    projectFrame.invokeMenuPath("Build", "Clean Project");
    messagesFixture = MessagesFixture.findByTitle(guiTest.robot(), "Terminate debugging");
    // Click okay and check that the debugger has been killed.
    messagesFixture.click("Terminate");
    assertThat(debugToolWindowFixture.getDebuggerContent("app-native")).isNull();
  }

  @Test
  @RunIn(TestGroup.QA_UNRELIABLE)
  public void testNativeDebuggerNewLibraryWhileDebugging() throws Exception {
    final IdeFrameFixture projectFrame = guiTest.ideFrame();
    DebuggerTestUtil.setDebuggerType(projectFrame, DebuggerTestUtil.NATIVE);
    openAndToggleBreakPoints(projectFrame, C_FILE_NAME, C_BP_LINE);

    DebugToolWindowFixture debugToolWindowFixture =
      DebuggerTestUtil.debugAppAndWaitForSessionToStart(projectFrame, guiTest, DEBUG_CONFIG_NAME, emulator.getDefaultAvdName());

    // Add a new Android Library.  Note that this needs the path to Kotlin defined in the test's
    // JVM arguments.  See go/studio-testing-pitfalls for information.
    projectFrame.invokeMenuPath("File", "New", "New Module...");
    NewModuleWizardFixture newModuleWizardFixture = NewModuleWizardFixture.find(guiTest.ideFrame());
    newModuleWizardFixture.chooseModuleType("Android Library").clickNext().clickFinish();

    MessagesFixture messagesFixture = MessagesFixture.findByTitle(guiTest.robot(), "Terminate debugging");
    // Cancel and check that the debugging session is still happening.
    messagesFixture.clickCancel();
    checkAppIsPaused(projectFrame, new String[]{});
    stopDebugSession(debugToolWindowFixture);

    projectFrame.debugApp(DEBUG_CONFIG_NAME)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();
    debugToolWindowFixture = new DebugToolWindowFixture(projectFrame);
    waitForSessionStart(debugToolWindowFixture);

    projectFrame.invokeMenuPath("File", "New", "New Module...");
    newModuleWizardFixture = NewModuleWizardFixture.find(guiTest.ideFrame());
    newModuleWizardFixture.chooseModuleType("Android Library").clickNext().clickFinish();

    messagesFixture = MessagesFixture.findByTitle(guiTest.robot(), "Terminate debugging");
    // Click okay and check that the debugger has been killed.
    messagesFixture.click("Terminate");
    assertThat(debugToolWindowFixture.getDebuggerContent("app-native")).isNull();
  }

  private void waitUntilDebugConsoleCleared(DebugToolWindowFixture debugToolWindowFixture) {
    final ExecutionToolWindowFixture.ContentFixture contentFixture = debugToolWindowFixture.findContent(DEBUG_CONFIG_NAME);
    contentFixture.waitForOutput(new NotMatchingPatternMatcher(DEBUGGER_ATTACHED_PATTERN), 10);
  }

}
