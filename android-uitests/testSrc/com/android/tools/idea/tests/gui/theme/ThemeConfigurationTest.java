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
package com.android.tools.idea.tests.gui.theme;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.ActionButtonFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ThemeSelectionDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.AndroidThemePreviewPanelFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.NewStyleDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.ThemeEditorFixture;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.android.tools.idea.tests.gui.framework.fixture.theme.ThemeEditorFixture.clickPopupMenuItem;

@RunIn(TestGroup.UNRELIABLE)  // b/71855356
@RunWith(GuiTestRunner.class)
public class ThemeConfigurationTest {

  @Rule public final RenderTimeoutRule timeout = new RenderTimeoutRule(60, TimeUnit.SECONDS);
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  /**
   * Tests that the theme editor deals well with themes defined only in certain configurations
   */
  @Test
  public void testThemesWithConfiguration() throws IOException {
    guiTest.importSimpleApplication();
    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(guiTest.ideFrame());

    JComboBoxFixture themesComboBox = themeEditor.getThemesComboBox();

    themesComboBox.selectItem("Create New Theme");
    NewStyleDialogFixture newStyleDialog = NewStyleDialogFixture.find(guiTest.robot());

    JComboBoxFixture parentComboBox = newStyleDialog.getParentComboBox();

    parentComboBox.selectItem("Show all themes");
    ThemeSelectionDialogFixture.find(guiTest.robot())
      .selectsTheme("Material Dark", "android:Theme.Material")
      .clickOk();
    parentComboBox.requireSelection("android:Theme.Material");

    JTextComponentFixture newNameTextField = newStyleDialog.getNewNameTextField();
    newNameTextField.click();
    newNameTextField.deleteText();
    newNameTextField.enterText("MyMaterialTheme");

    newStyleDialog.clickOk();
    themeEditor.waitForThemeSelection("MyMaterialTheme");
    AndroidThemePreviewPanelFixture themePreviewPanel = themeEditor.getPreviewComponent().getThemePreviewPanel();
    themePreviewPanel.requirePreviewPanel();

    ActionButtonFixture button = themeEditor.findToolbarButton("API Version in Editor");
    button.click();
    clickPopupMenuItem("API 19", "19", button.target(), guiTest.robot());

    themePreviewPanel.requireErrorPanel();

    themesComboBox.selectItem("AppTheme");
    themePreviewPanel.requirePreviewPanel();

    themesComboBox.selectItem("MyMaterialTheme");
    themePreviewPanel.requireErrorPanel();
  }
}
