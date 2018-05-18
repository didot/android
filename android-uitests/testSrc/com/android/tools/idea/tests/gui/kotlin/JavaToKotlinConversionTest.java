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
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.PatternTextMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class JavaToKotlinConversionTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule();

  private static final Pattern RUN_OUTPUT =
    Pattern.compile(".*Connected to process (\\d+) .*", Pattern.DOTALL);
  private static final String APP_NAME = "app";

  /**
   * Verifies it can convert Java class to Kotlin Class.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 7adb5104-9244-4cac-a1df-7d04991c8f14
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleApplication project and wait for project sync to finish.
   *   2. Open up MyActivity.java file.
   *   3. Invoke Code > Convert Java to Kotlin
   *   4. Build and deploy the app on emulator.
   *   5. Verify 1, 2, 3.
   *   Verify:
   *   1. Ensure the code java class is converted to Kotlin.
   *   2. Check if the Activities are getting converted to Kotlin.
   *   3. App is running on emulator.
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void testJavaToKotlinConversion() throws Exception {
    IdeFrameFixture ideFrameFixture =
      guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleLocalApplication");

    EditorFixture editor = ideFrameFixture.getEditor();

    editor.open("app/src/main/java/google/simpleapplication/MyActivity.java")
      .waitUntilErrorAnalysisFinishes();

    ideFrameFixture.waitAndInvokeMenuPath("Code", "Convert Java File to Kotlin File");

    Wait.seconds(10).expecting("Wait for kt file is generated.")
      .until(() -> "MyActivity.kt".equals(editor.getCurrentFileName()));

    assertThat(editor.getCurrentFileContents()).contains("class MyActivity : Activity() {");

    emulator.createDefaultAVD(ideFrameFixture.invokeAvdManager());
    ideFrameFixture.runApp(APP_NAME)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    ideFrameFixture.getRunToolWindow().findContent(APP_NAME)
      .waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);
  }
}
