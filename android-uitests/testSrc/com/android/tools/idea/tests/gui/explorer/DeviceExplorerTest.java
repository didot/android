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
package com.android.tools.idea.tests.gui.explorer;

import com.android.tools.idea.explorer.DeviceExplorer;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.wm.impl.StripeButton;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(GuiTestRunner.class)
public class DeviceExplorerTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  @Ignore("http://b.android.com/232911")
  public void buttonShows() throws IOException {
    DeviceExplorer.enableFeature(true);
    guiTest.importSimpleApplication()
      .robot().finder().find(Matchers.byText(StripeButton.class, "Device Explorer"));
    DeviceExplorer.enableFeature(false);
  }
}
