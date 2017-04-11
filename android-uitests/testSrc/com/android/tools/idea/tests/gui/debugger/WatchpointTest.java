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
package com.android.tools.idea.tests.gui.debugger;

import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.*;
import com.android.tools.idea.tests.gui.framework.ndk.MiscUtils;
import com.intellij.openapi.ui.JBPopupMenu;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith (GuiTestRunner.class)
public class WatchpointTest extends DebuggerTestBase {

  @Rule public final NativeDebuggerGuiTestRule guiTest = new NativeDebuggerGuiTestRule();

  /**
   * Verifies that debugger stops an app once watched variable is read and/or written.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14606206
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import BasicJniApp.
   *   2. Create an emulator.
   *   3. Set a breakpoint at the dummy variable definition in C++ code.
   *   4. Debug on the emulator.
   *   5. When the C++ breakpoint is hit, verify variables.
   *   6. In Debug window, right click on the variable and click Add Watchpoint.
   *   7. Resume program, and verify that that debugger stops an app once watched variable is read and/or written.
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.QA_UNRELIABLE)
  public void testWatchpoint() throws IOException, ClassNotFoundException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("BasicJniApp");

    final IdeFrameFixture ideFrame = guiTest.ideFrame();

    createDefaultAVD(ideFrame.invokeAvdManager());

    // Setup breakpoints
    openAndToggleBreakPoints(ideFrame, "app/src/main/jni/multifunction-jni.c", "int dummy = 1;");

    // Setup the expected patterns to match the variable values displayed in Debug windows's 'Variables' tab.
    String[] expectedPattern = {variableToSearchPattern("write", "int", "5")};

    ideFrame.debugApp(DEBUG_CONFIG_NAME)
        .selectDevice(AVD_NAME)
        .clickOk();

    DebugToolWindowFixture debugToolWindowFixture = new DebugToolWindowFixture(ideFrame);
    waitForSessionStart(debugToolWindowFixture);
    checkAppIsPaused(ideFrame, expectedPattern, false);

    DebugToolWindowFixture.ContentFixture contentFixture = debugToolWindowFixture.findContent(DEBUG_CONFIG_NAME);

    JBPopupMenu popupMenu = contentFixture.rightClickVariableInDebuggerVariables(ideFrame, "write");
    contentFixture.addWatchpoint(ideFrame, popupMenu);

    MiscUtils.invokeMenuPathOnRobotIdle(ideFrame, "Run", "Resume Program");

    String[] newExpectedPattern = {variableToSearchPattern("write", "int", "8")};
    checkAppIsPaused(ideFrame, newExpectedPattern, true);

    stopDebugSession(debugToolWindowFixture);
  }
}
