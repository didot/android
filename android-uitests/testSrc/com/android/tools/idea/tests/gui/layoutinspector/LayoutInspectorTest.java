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
package com.android.tools.idea.tests.gui.layoutinspector;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.tests.gui.emulator.DeleteAvdsRule;
import com.android.tools.idea.tests.gui.emulator.EmulatorGenerator;
import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.AndroidProcessChooserDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.LayoutInspectorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.RunToolWindowFixture;
import com.android.tools.idea.tests.util.ddmlib.AndroidDebugBridgeUtils;
import com.android.tools.idea.tests.util.ddmlib.DeviceQueries;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class LayoutInspectorTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private final EmulatorTestRule emulator = new EmulatorTestRule(false);
  @Rule public final RuleChain emulatorRules = RuleChain
    .outerRule(new DeleteAvdsRule())
    .around(emulator);

  /**
   * Verify layout inspector is a full replacement for the hierarchy viewer
   *
   * <p>TT ID: 65743195-bcf9-4127-8f4a-b60fde2b269e
   *
   * <pre>
   *   Test steps:
   *   1. Create a new project.
   *   2. Open the layout inspector by following Tools > Layout Inspector from the menu.
   *   3. Select the process running this project's application.
   *   4. Retrieve the layout's elements from the process.
   *   Verify:
   *   1. Ensure that the layout's elements contain the expected elements, which include
   *      a RelativeLayout, a TextView, and a FrameLayout.
   * </pre>
   */
  @Test
  @RunIn(TestGroup.SANITY)
  public void launchLayoutInspectorViaChooser() throws Exception {
    String appConfigName = "app";
    IdeFrameFixture ideFrame = guiTest.importSimpleLocalApplication();

    String avdName = EmulatorGenerator.ensureDefaultAvdIsCreated(ideFrame.invokeAvdManager());

    ideFrame.runApp(appConfigName).selectDevice(avdName).clickOk();
    // wait for background tasks to finish before requesting run tool window. otherwise run tool window won't activate.
    guiTest.waitForBackgroundTasks();

    // The following includes a wait for the run tool window to appear.
    // Also show the run tool window in case of failure so we have more information.
    RunToolWindowFixture runWindow = ideFrame.getRunToolWindow();
    runWindow.activate();
    emulator.waitForProcessToStart(runWindow.findContent(appConfigName));

    //Wait for emulator to launch the app
    IDevice emu = AndroidDebugBridgeUtils.getEmulator(avdName, emulator.getEmulatorConnection(), 5);
    assertThat(emu).isNotNull();
    new DeviceQueries(emu).waitUntilAppViewsAreVisible("google.simpleapplication", 5);

    guiTest.ideFrame().waitAndInvokeMenuPath("Tools", "Layout Inspector");
    // easier to select via index rather than by path string which changes depending on the api version
    AndroidProcessChooserDialogFixture.find(guiTest.robot()).selectProcess().clickOk();
    List<String> layoutElements = new LayoutInspectorFixture(guiTest.robot()).getLayoutElements();
    checkLayout(layoutElements);
  }

  private void checkLayout(List<String> layoutElements) {
    assertThat(layoutElements).contains("android.widget.RelativeLayout");
    assertThat(layoutElements).contains("android.widget.TextView");
    assertThat(layoutElements).contains("android.widget.FrameLayout");
  }
}
