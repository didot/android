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

import com.android.tools.idea.tests.gui.emulator.TestWithEmulator;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.AndroidProcessChooserDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.LayoutInspectorFixture;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRunner.class)
public class LayoutInspectorTest extends TestWithEmulator {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Before
  public void setUp() throws Exception {
    guiTest.importSimpleApplication();
    createDefaultAVD(guiTest.ideFrame().invokeAvdManager());
  }

  @Test
  @RunIn(TestGroup.QA)
  public void launchLayoutInspectorViaChooser() throws Exception {
    guiTest.ideFrame().runApp("app").selectDevice(AVD_NAME).clickOk();
    // wait for background tasks to finish before requesting run tool window. otherwise run tool window won't activate.
    guiTest.waitForBackgroundTasks();
    guiTest.ideFrame().waitAndInvokeMenuPath("Tools", "Android", "Layout Inspector");
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
