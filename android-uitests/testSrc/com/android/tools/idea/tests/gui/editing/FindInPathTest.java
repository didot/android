/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.editing;

import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.FindPopupPanelFixture;
import com.google.common.collect.ImmutableList;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRunner.class)
public class FindInPathTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testResultsOnlyInGeneratedCode() throws Exception {
    ImmutableList<String> usageGroupNames = guiTest.importSimpleLocalApplication()
      .openFromMenu(FindPopupPanelFixture::find, "Edit", "Find", "Find in Path...")
      .setTextToFind("ActionBarDivider")
      .clickFind()
      .getUsageGroupNames();
    assertThat(usageGroupNames).containsExactly("Usages in generated code");
  }

  @Test
  public void testResultsInBothProductionAndGeneratedCode() throws Exception {
    ImmutableList<String> usageGroupNames = guiTest.importSimpleLocalApplication()
      .openFromMenu(FindPopupPanelFixture::find, "Edit", "Find", "Find in Path...")
      .setTextToFind("DarkActionBar")
      .clickFind()
      .getUsageGroupNames();
    assertThat(usageGroupNames).containsExactly("Usages in generated code", "Code usages");
  }
}
