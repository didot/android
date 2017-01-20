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
package com.android.tools.idea.tests.gui.layout;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.NlEditorFixture;
import org.fest.swing.fixture.JListFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.awt.*;

import static com.google.common.truth.Truth.assertThat;
import static java.awt.event.KeyEvent.VK_ESCAPE;

/**
 * UI test for the layout palette
 */
@RunWith(GuiTestRunner.class)
public class NlPaletteTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testSpeedSearchAcrossGroups() throws Exception {
    NlEditorFixture layout = guiTest.importSimpleApplication()
      .getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR)
      .getLayoutEditor(true)
      .waitForRenderToFinish();

    layout.getPaletteItemList(0).clickItem(0);
    guiTest.robot().enterText("email");
    guiTest.robot().pressKey(VK_ESCAPE);

    JListFixture selectedList = layout.getSelectedItemList();
    assertThat(selectedList).isNotNull();
    assertThat(selectedList.selection()).asList().containsExactly("E-mail");
    assertThat(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner()).isSameAs(selectedList.target());
  }
}
