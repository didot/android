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

import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.*;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class DebuggerInNdkProjectTest extends DebuggerTestBase {

  @Rule public final NativeDebuggerGuiTestRule guiTest = new NativeDebuggerGuiTestRule();
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule();

  /**
   * Verifies that Auto-debugger works fine with NDK build project.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: f7b7351f-0e76-40d6-8b17-714941cb3b54
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import NdkHelloJni and wait for sync to finish.
   *   2. Create an AVD with x86 API 24 or above.
   *   3. Select Auto debugger on Edit Configurations dialog.
   *   4. Set breakpoint in C++ code.
   *   5. Debug on the avd created above.
   *   6. When the C++ breakpoint is hit, verify variables and resume
   *   7. Stop debugging
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.QA)
  public void testAutoDebugger() throws Exception {
    processToTest(DebuggerTestUtil.AUTO);
  }

  /**
   * Verifies that Dual-debugger works fine with NDK build project.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: c7e70d91-af4a-4357-a8df-0e9e46b3484d
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import NdkHelloJni and wait for sync to finish.
   *   2. Create an AVD with x86 API 24 or above.
   *   3. Select Dual debugger on Edit Configurations dialog.
   *   4. Set breakpoints in both Java and C++ codes.
   *   5. Debug on the avd created above.
   *   6. When the C++ breakpoint is hit, verify variables and resume.
   *   7. When the Java breakpoint is hit, verify variables.
   *   8. Stop debugging
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.QA)
  public void testDualDebugger() throws Exception {
    processToTest(DebuggerTestUtil.DUAL);
  }

  /**
   * Verifies that Native-debugger works fine with NDK build project.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 904ded03-43f5-4ec7-b1e0-1c6b2dea9c59
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import NdkHelloJni and wait for sync to finish.
   *   2. Create an AVD with x86 API 24 or above.
   *   3. Select Native debugger on Edit Configurations dialog.
   *   4. Set breakpoints in both Java and C++ codes.
   *   5. Debug on the avd created above.
   *   6. When the C++ breakpoint is hit, verify variables and resume.
   *   7. Verify the Java breakpoint didn't hit.
   *   8. Stop debugging
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.QA)
  public void testNativeDebugger() throws Exception {
    processToTest(DebuggerTestUtil.NATIVE);
  }

  private void processToTest(@NotNull String debuggerType) throws Exception {
    IdeFrameFixture ideFrame =
      guiTest.importProjectAndWaitForProjectSyncToFinish("NdkHelloJni");
    emulator.createDefaultAVD(ideFrame.invokeAvdManager());
    DebuggerTestUtil.setDebuggerType(ideFrame, debuggerType);

    // Setup C++ and Java breakpoints.
    if (debuggerType.equals(DebuggerTestUtil.AUTO)) {
      // Don't set Java breakpoint.
    } else if (debuggerType.equals(DebuggerTestUtil.DUAL) || debuggerType.equals(DebuggerTestUtil.NATIVE)) {
      openAndToggleBreakPoints(ideFrame,
                               "app/src/main/java/com/example/hellojni/HelloJni.java",
                               "setContentView(tv);");
    } else {
      throw new RuntimeException("Not supported debugger type provide: " + debuggerType);
    }
    openAndToggleBreakPoints(ideFrame,
                             "app/src/main/cpp/hello-jni.c",
                             "return (*env)->NewStringUTF(env, \"ABI \" ABI \".\");");

    DebugToolWindowFixture debugToolWindowFixture =
      DebuggerTestUtil.debugAppAndWaitForSessionToStart(ideFrame, guiTest, DEBUG_CONFIG_NAME, emulator.getDefaultAvdName());

    // Setup the expected patterns to match the variable values displayed in Debug windows's
    // 'Variables' tab.
    String[] expectedPatterns = new String[]{
      variableToSearchPattern("kid_age", "int", "3"),
    };
    checkAppIsPaused(ideFrame, expectedPatterns);

    if (debuggerType.equals(DebuggerTestUtil.DUAL)) {
      resume(DEBUG_CONFIG_NAME, ideFrame);

      expectedPatterns = new String[]{
        variableToSearchPattern("s", "\"ABI x86.\""),
      };
      checkAppIsPaused(ideFrame, expectedPatterns, DebuggerTestUtil.JAVA_DEBUGGER_CONF_NAME);
    }

    if (debuggerType.equals(DebuggerTestUtil.AUTO)) {
      // Don't check Java debugger window for auto debugger here.
    } else if (debuggerType.equals(DebuggerTestUtil.DUAL)) {
      assertThat(debugToolWindowFixture.getDebuggerContent(DebuggerTestUtil.JAVA_DEBUGGER_CONF_NAME)).isNotNull();
    } else if (debuggerType.equals(DebuggerTestUtil.NATIVE)) {
      assertThat(debugToolWindowFixture.getDebuggerContent(DebuggerTestUtil.JAVA_DEBUGGER_CONF_NAME)).isNull();
    } else {
      throw new RuntimeException("Not supported debugger type provide: " + debuggerType);
    }

    if (debuggerType.equals(DebuggerTestUtil.AUTO) || debuggerType.equals(DebuggerTestUtil.NATIVE)) {
      stopDebugSession(debugToolWindowFixture);
    } else if (debuggerType.equals(DebuggerTestUtil.DUAL)) {
      // TODO: stop session.
    } else {
      throw new RuntimeException("Not supported debugger type provide: " + debuggerType);
    }
  }
}
