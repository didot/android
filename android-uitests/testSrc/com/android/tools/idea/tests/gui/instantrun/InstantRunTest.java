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
package com.android.tools.idea.tests.gui.instantrun;

import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.AvdEditWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.AvdManagerDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.MockAvdManagerConnection;
import com.google.common.base.Strings;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.PatternTextMatcher;
import org.jetbrains.annotations.NotNull;
import org.junit.*;
import org.junit.runner.RunWith;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRunner.class)
public class InstantRunTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private static final String APP_NAME = "app";
  private static final String AVD_NAME = "device under test";
  private static final Pattern RUN_OUTPUT =
    Pattern.compile(".*adb shell am start .*google\\.simpleapplication.*Connected to process (\\d+) .*", Pattern.DOTALL);
  private static final Pattern HOT_SWAP_OUTPUT =
    Pattern.compile(".*Hot swapped changes, activity restarted.*", Pattern.DOTALL);

  @Before
  public void setUp() throws Exception {
    MockAvdManagerConnection.inject();
    getEmulatorConnection().deleteAvdByDisplayName(AVD_NAME);
  }

  @After
  public void tearDown() throws Exception {
    getEmulatorConnection().stopRunningAvd();
    getEmulatorConnection().deleteAvdByDisplayName(AVD_NAME);
  }

  /**
   * Verifies that instant run hot swap works as expected.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14581583
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleApplication.
   *   2. Update the gradle plugin version if necessary for testing purpose.
   *   3. Create an AVD with a system image API 21 or above.
   *   4. Run on the AVD
   *   5. Verify 1.
   *   6. Edit a java file.
   *   7. Run again.
   *   8. Verify 2.
   *   Verify:
   *   1. Make sure the right app is installed and started in Run tool window.
   *   2. Make sure the instant run hot swap is applied in Run tool window.
   *   </pre>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void hotSwap() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();
    createAVD();

    ideFrameFixture
      .runApp(APP_NAME)
      .selectDevice(AVD_NAME)
      .clickOk();

    ExecutionToolWindowFixture.ContentFixture contentFixture = ideFrameFixture.getRunToolWindow().findContent(APP_NAME);
    contentFixture.waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);
    String pid = extractPidFromOutput(contentFixture.getOutput());

    ideFrameFixture
      .getEditor()
      .open("app/src/main/java/google/simpleapplication/MyActivity.java")
      .enterText(Strings.repeat("\n", 10));

    ideFrameFixture
      .waitForGradleProjectSyncToFinish()
      .findApplyChangesButton()
      .click();

    contentFixture.waitForOutput(new PatternTextMatcher(HOT_SWAP_OUTPUT), 120);
    String newPid = extractPidFromOutput(contentFixture.getOutput());
    // (Hot swap) Verify the equality of PIDs before and after IR.
    assertThat(pid).isEqualTo(newPid);
  }

  /**
   * Verifies that instant run cold swap works as expected.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14581584
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleApplication.
   *   2. Update the gradle plugin version if necessary for testing purpose.
   *   3. Create an AVD with a system image API 21 or above.
   *   4. Run on the AVD
   *   5. Verify 1.
   *   6. Edit a resource xml file.
   *   7. Run again.
   *   8. Verify 2.
   *   Verify:
   *   1. Make sure the right app is installed and started in Run tool window.
   *   2. Make sure the instant run cold swap is applied in Run tool window.
   *   </pre>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void coldSwap() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();
    createAVD();

    ideFrameFixture
      .runApp(APP_NAME)
      .selectDevice(AVD_NAME)
      .clickOk();

    ExecutionToolWindowFixture.ContentFixture contentFixture = ideFrameFixture.getRunToolWindow().findContent(APP_NAME);
    contentFixture.waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);
    String output = contentFixture.getOutput();
    String pid = extractPidFromOutput(output);

    ideFrameFixture
      .getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN)
      .getLayoutEditor(false)
      .dragComponentToSurface("Widgets", "TextView")
      .waitForRenderToFinish();

    ideFrameFixture
      .waitForGradleProjectSyncToFinish()
      .findApplyChangesButton()
      .click();

    // Studio takes a few seconds to reset Run tool window contents.
    Wait.seconds(10).expecting("Run tool window output has been reset").until(() -> !contentFixture.getOutput().contains(output));
    contentFixture.waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);
    String newPid = extractPidFromOutput(contentFixture.getOutput());
    // (Cold swap) Verify the inequality of PIDs before and after IR
    assertThat(pid).isNotEqualTo(newPid);
  }

  /**
   * Verifies that instant run works as expected when AndroidManifest is changed.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14581585
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleApplication.
   *   2. Update the gradle plugin version if necessary for testing purpose.
   *   3. Create an AVD with a system image API 21 or above.
   *   4. Run on the AVD
   *   5. Verify 1.
   *   6. Edit AndroidManifest.
   *   7. Run again.
   *   8. Verify 2.
   *   Verify:
   *   1. Make sure the right app is installed and started in Run tool window.
   *   2. Make sure the instant run is applied in EventLog tool window.
   *   </pre>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void changeManifest() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();
    createAVD();

    ideFrameFixture
      .runApp(APP_NAME)
      .selectDevice(AVD_NAME)
      .clickOk();

    ExecutionToolWindowFixture.ContentFixture contentFixture = ideFrameFixture.getRunToolWindow().findContent(APP_NAME);
    contentFixture.waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);
    String output = contentFixture.getOutput();
    String pid = extractPidFromOutput(contentFixture.getOutput());

    ideFrameFixture
      .getEditor()
      .open("app/src/main/AndroidManifest.xml", EditorFixture.Tab.EDITOR)
      .moveBetween("", "<application")
      .enterText("<uses-permission android:name=\"android.permission.INTERNET\" /\n");

    ideFrameFixture
      .waitForGradleProjectSyncToFinish()
      .findApplyChangesButton()
      .click();

    // Studio takes a few seconds to reset Run tool window contents.
    Wait.seconds(10).expecting("Run tool window output has been reset").until(() -> !contentFixture.getOutput().contains(output));
    contentFixture.waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);
    String newPid = extractPidFromOutput(contentFixture.getOutput());
    // (Cold swap) Verify the inequality of PIDs before and after IR
    assertThat(pid).isNotEqualTo(newPid);
  }

  private void createAVD() {
    AvdManagerDialogFixture avdManagerDialog = guiTest.ideFrame().invokeAvdManager();
    AvdEditWizardFixture avdEditWizard = avdManagerDialog.createNew();

    avdEditWizard.selectHardware()
      .selectHardwareProfile("Nexus 5");
    avdEditWizard.clickNext();

    avdEditWizard.getChooseSystemImageStep()
      .selectTab("x86 Images")
      .selectSystemImage("Nougat", "24", "x86", "Android 7.0");
    avdEditWizard.clickNext();

    avdEditWizard.getConfigureAvdOptionsStep()
      .setAvdName(AVD_NAME)
      .selectGraphicsSoftware();
    avdEditWizard.clickFinish();
    avdManagerDialog.close();
  }

  @NotNull
  private static MockAvdManagerConnection getEmulatorConnection() {
    return (MockAvdManagerConnection)AvdManagerConnection.getDefaultAvdManagerConnection();
  }

  @NotNull
  private static String extractPidFromOutput(@NotNull String output) {
    Matcher m = RUN_OUTPUT.matcher(output);
    String pid = null;
    if (m.find()) {
      pid = m.group(1);
    }
    return pid;
  }
}
