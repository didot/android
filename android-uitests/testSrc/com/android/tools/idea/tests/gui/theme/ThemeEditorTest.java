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
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.HyperlinkLabelFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.ActivityChooserFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.NewStyleDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.ThemeEditorFixture;
import com.intellij.notification.EventLog;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import org.fest.swing.fixture.JComboBoxFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

/**
 * Unit test for the layout theme editor
 */
@RunIn(TestGroup.THEME)
@RunWith(GuiTestRunner.class)
public class ThemeEditorTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  /**
   * Checks that no errors are present in the event log
   */
  private void checkNoErrors() {
    guiTest.robot().waitForIdle();
    for(Notification notification : EventLog.getLogModel(guiTest.ideFrame().getProject()).getNotifications()) {
      assertThat(notification.getType()).isNotEqualTo(NotificationType.ERROR);
    }
  }

  @RunIn(TestGroup.UNRELIABLE)  // b/62703126
  @Test
  public void testOpenProject() throws IOException {
    // Test that we can open the simple application and the theme editor opens correctly
    guiTest.importSimpleApplication();
    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(guiTest.ideFrame());

    // Search is empty
    themeEditor.getSearchTextField().requireText("");

    // Check the theme combo is populated correctly
    List<String> themeList = themeEditor.getThemesList();
    // The expected elements are:
    // 0. AppTheme
    // 1. -- Separator
    // 2. AppCompat Light
    // 3. AppCompat
    // 4. Show all themes
    // 5. -- Separator
    // 6. Create New Theme
    // 7. Rename AppTheme
    assertThat(themeList).hasSize(9);
    assertThat(themeList.get(0)).isEqualTo("AppTheme");
    assertThat(themeList.get(3)).isEqualTo("Theme.AppCompat.Light.NoActionBar");
    assertThat(themeList.get(4)).isEqualTo("Theme.AppCompat.NoActionBar");
    assertThat(themeList.get(5)).isEqualTo("Show all themes");
    assertThat(themeList.get(7)).isEqualTo("Create New Theme");
    assertThat(themeList.get(8)).isEqualTo("Rename AppTheme");

    assertThat(themeList.get(2)).startsWith("javax.swing.JSeparator");
    assertThat(themeList.get(6)).startsWith("javax.swing.JSeparator");

    // Check the attributes table is populated
    assertThat(themeEditor.getPropertiesTable().rowCount()).isGreaterThan(0);

    guiTest.ideFrame().getEditor().close();
    checkNoErrors();
  }

  @RunIn(TestGroup.UNRELIABLE)
  @Test
  public void testSetThemeOnActivity() throws IOException {
    // Test that we can open the simple application and the theme editor opens correctly
    guiTest.importSimpleApplication();

    IdeFrameFixture projectFrame = guiTest.ideFrame();
    EditorFixture editor = projectFrame.getEditor().open("app/src/main/AndroidManifest.xml");
    String addedText = "        <activity\n" +
                       "            android:name=\".MyActivity\"\n" +
                       "            android:label=\"@string/app_name\"\n" +
                       "            android:theme=\"@style/MyNewTheme\">\n";
    assertThat(editor.getCurrentFileContents()).doesNotContain(addedText);

    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(guiTest.ideFrame());
    // create a new theme
    themeEditor.getThemesComboBox().selectItem("Create New Theme");
    NewStyleDialogFixture newStyleDialog = NewStyleDialogFixture.find(guiTest.robot());
    newStyleDialog.getNewNameTextField().setText("MyNewTheme");
    newStyleDialog.clickOk();
    // make this the theme for the default activity
    HyperlinkLabelFixture label = themeEditor.getThemeWarningLabel();
    assertThat(label.getText()).isEqualTo("Theme is not used anywhere fix");
    label.clickLink("fix");

    ActivityChooserFixture.find(guiTest.robot()).clickOk();

    editor.open("app/src/main/AndroidManifest.xml");
    assertThat(editor.getCurrentFileContents()).contains(addedText);
  }
}
