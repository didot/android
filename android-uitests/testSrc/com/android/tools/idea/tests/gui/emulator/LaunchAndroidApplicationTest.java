/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.emulator;

import com.android.tools.idea.fd.InstantRunSettings;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.fixture.DeployTargetPickerDialogFixture;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.DebugToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.BrowseSamplesWizardFixture;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import org.fest.swing.util.PatternTextMatcher;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRunner.class)
public class LaunchAndroidApplicationTest extends TestWithEmulator {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private static final String APP_NAME = "app";
  private static final String APPLICATION_STARTED = ".*Application started.*";
  private static final String PROCESS_NAME = "google.simpleapplication";
  private static final Pattern LOCAL_PATH_OUTPUT = Pattern.compile(
    ".*adb shell am start .*google\\.simpleapplication.*", Pattern.DOTALL);
  private static final Pattern RUN_OUTPUT = Pattern.compile(".*Connected to process.*", Pattern.DOTALL);
  private static final Pattern DEBUG_OUTPUT = Pattern.compile(".*Connected to the target VM.*", Pattern.DOTALL);

  @RunIn(TestGroup.QA)
  @Test
  public void testRunOnEmulator() throws Exception, ClassNotFoundException {
    InstantRunSettings.setShowStatusNotifications(false);
    guiTest.importSimpleApplication();
    createDefaultAVD(guiTest.ideFrame().invokeAvdManager());

    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();

    ideFrameFixture
      .runApp(APP_NAME)
      .selectDevice(AVD_NAME)
      .clickOk();

    // Make sure the right app is being used. This also serves as the sync point for the package to get uploaded to the device/emulator.
    ideFrameFixture.getRunToolWindow().findContent(APP_NAME).waitForOutput(new PatternTextMatcher(LOCAL_PATH_OUTPUT), 120);
    ideFrameFixture.getRunToolWindow().findContent(APP_NAME).waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);

    ideFrameFixture.getAndroidToolWindow().selectDevicesTab().selectProcess(PROCESS_NAME).clickTerminateApplication();
  }

  @RunIn(TestGroup.QA)
  @Test
  public void testDebugOnEmulator() throws IOException, ClassNotFoundException, EvaluateException {
    guiTest.importSimpleApplication();
    createDefaultAVD(guiTest.ideFrame().invokeAvdManager());

    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();

    ideFrameFixture
      .debugApp(APP_NAME)
      .selectDevice(AVD_NAME)
      .clickOk();

    // Make sure the right app is being used. This also serves as the sync point for the package to get uploaded to the device/emulator.
    ideFrameFixture.getDebugToolWindow().findContent(APP_NAME).waitForOutput(new PatternTextMatcher(LOCAL_PATH_OUTPUT), 120);
    ideFrameFixture.getDebugToolWindow().findContent(APP_NAME).waitForOutput(new PatternTextMatcher(DEBUG_OUTPUT), 120);

    ideFrameFixture.getAndroidToolWindow()
      .selectDevicesTab()
      .selectProcess(PROCESS_NAME)
      .clickTerminateApplication();
  }

  /**
   * To verify NDK project compiles when running two files with same filename in different libs.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14603477
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Open Android Studio
   *   2. Import NdkDupeFilename project.
   *   3. Compile and run on the emulator.
   *   Verify:
   *   1. Application can run without errors.
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void testNdkHandlesDupeFilename() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importProjectAndWaitForProjectSyncToFinish("NdkDupeFilename");
    createDefaultAVD(guiTest.ideFrame().invokeAvdManager());
    ideFrameFixture
      .runApp(APP_NAME)
      .selectDevice(AVD_NAME)
      .clickOk();
    ExecutionToolWindowFixture.ContentFixture contentWindow = ideFrameFixture.getRunToolWindow().findContent(APP_NAME);
    contentWindow.waitForOutput(new PatternTextMatcher(Pattern.compile(APPLICATION_STARTED, Pattern.DOTALL)), 120);
    contentWindow.stop();
  }

  /**
   * To verify that sample ndk projects can be imported and breakpoints in code are hit.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14578820
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Open Android Studio
   *   2. Import Teapot from the cloned repo
   *   3. Open TeapotNativeActivity.cpp
   *   4. Click on any method and make sure it finds the method or its declaration
   *   5. Search for the symbol HandleInput and put a break point in the method
   *   6. Deploy to a device/emulator
   *   7. Wait for notification that the debugger is connected
   *   8. Teapot should show up on the device
   *   9. Touch to interact
   *   Verify:
   *   1. After step 9, the breakpoint is triggered.
   *   </pre>
   * <p>
   */
  @Ignore("http://b/30795134")
  @RunIn(TestGroup.QA)
  @Test
  public void testCppDebugOnEmulatorWithBreakpoint() throws Exception {
    BrowseSamplesWizardFixture samplesWizard = guiTest.welcomeFrame()
      .importCodeSample();
    File location = samplesWizard.selectSample("Ndk/Teapot")
      .clickNext()
      .getConfigureFormFactorStep()
      .enterApplicationName("TeapotTest")
      .getLocationInFileSystem();

    guiTest.setProjectPath(location);

    samplesWizard.clickFinish();

    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();
    ideFrameFixture
      .waitForGradleProjectSyncToFinish()
      .getEditor()
      .open("app/src/main/jni/TeapotNativeActivity.cpp")
      .moveBetween("g_engine.Draw", "Frame()")
      .invokeAction(EditorFixture.EditorAction.TOGGLE_LINE_BREAKPOINT) // First break point - First Frame is drawn
      .moveBetween("static int32_t Handle", "Input(")
      .invokeAction(EditorFixture.EditorAction.GOTO_IMPLEMENTATION)
      .invokeAction(EditorFixture.EditorAction.TOGGLE_LINE_BREAKPOINT); // Second break point - HandleInput()

    assertThat(guiTest.ideFrame().getEditor().getCurrentLine())
      .contains("int32_t Engine::HandleInput(");

    createDefaultAVD(guiTest.ideFrame().invokeAvdManager());

    ideFrameFixture
      .debugApp(APP_NAME)
      .selectDevice(AVD_NAME)
      .clickOk();

    // Wait for the UI App to be up and running, by waiting for the first Frame draw to get hit.
    expectBreakPoint("g_engine.DrawFrame()");

    // Simulate a screen touch
    getEmulatorConnection().tapRunningAvd(400, 400);

    // Wait for the Cpp HandleInput() break point to get hit.
    expectBreakPoint("Engine* eng = (Engine*)app->userData;");
  }

  private void expectBreakPoint(String lineText) {
    DebugToolWindowFixture debugToolWindow = guiTest.ideFrame()
      .getDebugToolWindow()
      .waitForBreakPointHit();

    // Check we have the right debug line
    assertThat(guiTest.ideFrame().getEditor().getCurrentLine())
      .contains(lineText);

    // Remove break point
    guiTest.ideFrame()
      .getEditor()
      .invokeAction(EditorFixture.EditorAction.TOGGLE_LINE_BREAKPOINT);

    debugToolWindow.pressResumeProgram();
  }
}
