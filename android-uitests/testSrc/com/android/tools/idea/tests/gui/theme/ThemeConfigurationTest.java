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
import com.android.tools.idea.tests.gui.framework.fixture.ActionButtonFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ThemeSelectionDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.assetstudio.AssetStudioWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.AndroidThemePreviewPanelFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.NewStyleDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.ThemeEditorFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.android.tools.idea.tests.gui.framework.fixture.theme.ThemeEditorFixture.clickPopupMenuItem;

@RunWith(GuiTestRemoteRunner.class)
public class ThemeConfigurationTest {

  @Rule public final RenderTimeoutRule timeout = new RenderTimeoutRule(60, TimeUnit.SECONDS);
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  /**
   * Tests that the theme editor deals well with themes defined only in certain configurations
   */
  @Test
  public void testThemesWithConfiguration() throws IOException {
    guiTest.importSimpleApplication()
      .getProjectView()
      .selectAndroidPane()
      .clickPath("app")
      .getEditor()
      .open("app/build.gradle")
      .select("minSdkVersion (21)") // so we see errors when defining a theme based on Material (added in 21)
      .enterText("19")
      .awaitNotification("Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.")
      .performAction("Sync Now")
      .waitForGradleProjectSyncToFinish();

    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(guiTest.ideFrame());

    JComboBoxFixture themesComboBox = themeEditor.getThemesComboBox();

    themesComboBox.selectItem("Create New Theme");
    NewStyleDialogFixture newStyleDialog = NewStyleDialogFixture.find(guiTest.robot());

    JComboBoxFixture parentComboBox = newStyleDialog.getParentComboBox();

    parentComboBox.selectItem("Show all themes");
    ThemeSelectionDialogFixture.find(guiTest.robot())
      .selectTheme("Material Dark", "android:Theme.Material")
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

    ActionButtonFixture button = themeEditor.findToolbarButton("API Version for Preview");
    button.click();
    clickPopupMenuItem("API 19", "19", button.target(), guiTest.robot());

    themePreviewPanel.requireErrorPanel();

    themesComboBox.selectItem("AppTheme");
    themePreviewPanel.requirePreviewPanel();

    themesComboBox.selectItem("MyMaterialTheme");
    themePreviewPanel.requireErrorPanel();
  }
}
