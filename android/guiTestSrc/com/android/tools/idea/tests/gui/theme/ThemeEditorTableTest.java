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

import com.android.tools.idea.rendering.ResourceHelper;
import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.ChooseResourceDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.ThemeEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.ThemeEditorTableFixture;
import org.fest.assertions.Index;
import org.fest.swing.data.TableCell;
import org.fest.swing.fixture.*;
import org.fest.swing.timing.Condition;
import org.jetbrains.annotations.NotNull;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.tests.gui.framework.TestGroup.THEME;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.data.TableCell.row;
import static org.fest.swing.timing.Pause.pause;
import static org.junit.Assert.*;

/**
 * UI tests regarding the attributes table of the theme editor
 */
@BelongsToTestGroups({THEME})
public class ThemeEditorTableTest extends GuiTestCase {
  @BeforeClass
  public static void runBeforeClass() {
    ThemeEditorTestUtils.enableThemeEditor();
  }

  @Test @IdeGuiTest
  public void testParentValueCell() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();
    ThemeEditorFixture themeEditor = ThemeEditorTestUtils.openThemeEditor(projectFrame);
    ThemeEditorTableFixture themeEditorTable = themeEditor.getPropertiesTable();

    // Cell (0,0) should be the parent editor
    TableCell parentCell = row(0).column(0);


    List<String> parentsList = themeEditorTable.getComboBoxContentsAt(parentCell);
    // The expected elements are:
    // 0. Holo Light
    // 1. -- Separator
    // 2. AppCompat Light
    // 3. AppCompat
    // 4. -- Separator
    // 5. Show all themes
    assertNotNull(parentsList);
    assertThat(parentsList)
      .hasSize(6)
      .contains("Theme.Holo.Light.DarkActionBar", Index.atIndex(0))
      .contains("Theme.AppCompat.Light.NoActionBar", Index.atIndex(2))
      .contains("Theme.AppCompat.NoActionBar", Index.atIndex(3))
      .contains("Show all themes", Index.atIndex(5));

    assertThat(parentsList.get(1)).startsWith("javax.swing.JSeparator");
    assertThat(parentsList.get(4)).startsWith("javax.swing.JSeparator");

    JTableCellFixture parentCellFixture = themeEditorTable.cell(parentCell);
    parentCellFixture.requireEditable();

    // Checks that selecting a separator does nothing
    parentCellFixture.click();
    Component parentEditor = parentCellFixture.editor();
    assertTrue(parentEditor instanceof JComponent);
    JComboBoxFixture parentComboBox = new JComboBoxFixture(myRobot, myRobot.finder().findByType((JComponent)parentEditor, JComboBox.class));
    parentComboBox.selectItem(4);
    assertEquals("Theme.Holo.Light.DarkActionBar", themeEditorTable.getComboBoxSelectionAt(parentCell));

    // Selects a new parent
    final String newParent = "Theme.AppCompat.NoActionBar";
    parentCellFixture.click();

    parentComboBox.selectItem(newParent);
    assertEquals(newParent, themeEditorTable.getComboBoxSelectionAt(parentCell));

    projectFrame.invokeMenuPathRegex("Edit", "Undo.*");
    assertEquals("Theme.Holo.Light.DarkActionBar", themeEditorTable.getComboBoxSelectionAt(parentCell));

    projectFrame.invokeMenuPathRegex("Edit", "Redo.*");
    assertEquals(newParent, themeEditorTable.getComboBoxSelectionAt(parentCell));

    pause(new Condition("Wait for potential tooltips to disappear") {
      @Override
      public boolean test() {
        return myRobot.findActivePopupMenu() == null;
      }
    });
    testParentPopup(themeEditorTable.cell(parentCell), newParent, themeEditor);

    projectFrame.invokeMenuPath("Window", "Editor Tabs", "Select Previous Tab");
    EditorFixture editor = projectFrame.getEditor();
    editor.moveTo(editor.findOffset(null, "AppTheme", true));
    assertEquals("<style name=\"^AppTheme\" parent=\"@style/Theme.AppCompat.NoActionBar\">",
                        editor.getCurrentLineContents(true, true, 0));
  }

  private static void testParentPopup(@NotNull JTableCellFixture cell, @NotNull final String parentName,
                                      @NotNull ThemeEditorFixture themeEditor) {
    JPopupMenuFixture popupMenu = cell.showPopupMenu();
    String[] menuLabels = popupMenu.menuLabels();
    assertEquals(1, menuLabels.length);
    JMenuItemFixture edit = popupMenu.menuItemWithPath("Edit parent");
    edit.requireVisible();
    edit.click();

    final JComboBoxFixture themesComboBox = themeEditor.getThemesComboBox();
    pause(new Condition("Waiting for parent to load") {
      @Override
      public boolean test() {
        // Cannot use themesComboBox.selectedItem() here
        // because the parent theme is not necessarily one of the themes present in the combobox model
        return parentName.equals(themesComboBox.target().getSelectedItem().toString());
      }
    }, GuiTests.SHORT_TIMEOUT);
  }

  @Test @IdeGuiTest
  public void testResourcePickerNameError() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();
    ThemeEditorFixture themeEditor = ThemeEditorTestUtils.openThemeEditor(projectFrame);

    JTableFixture themeEditorTable = themeEditor.getPropertiesTable();

    // Cell (1,0) should be some color
    JTableCellFixture colorCell = themeEditorTable.cell(row(1).column(0));

    // click on a color
    colorCell.click();

    final ChooseResourceDialogFixture dialog = ChooseResourceDialogFixture.find(myRobot);
    JTextComponentFixture name = dialog.getNameTextField();

    // add mistake into name field
    String badText = "(";
    name.deleteText();
    name.enterText("color" + badText);
    String text = name.text();
    assertNotNull(text);
    assertTrue(text.endsWith(badText));

    final String expectedError = "<html><font color='#ff0000'><left>'" + badText +
                                 "' is not a valid resource name character</left></b></font></html>";
    pause(new Condition("Waiting for error to update") {
      @Override
      public boolean test() {
        return dialog.getError().equals(expectedError);
      }
    }, GuiTests.SHORT_TIMEOUT);
  }

  @Test @IdeGuiTest
  public void testSettingColorAttribute() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();
    ThemeEditorFixture themeEditor = ThemeEditorTestUtils.openThemeEditor(projectFrame);
    ThemeEditorTableFixture themeEditorTable = themeEditor.getPropertiesTable();

    TableCell cell = row(1).column(0);

    Font cellFont = themeEditorTable.valueFontAt(cell);
    assertNotNull(cellFont);
    assertEquals(Font.PLAIN, cellFont.getStyle());
    assertEquals("android:colorBackground", themeEditorTable.attributeNameAt(cell));
    assertEquals("@android:color/background_holo_light", themeEditorTable.valueAt(cell));

    JTableCellFixture colorCell = themeEditorTable.cell(cell);
    colorCell.requireEditable();
    colorCell.click();

    ChooseResourceDialogFixture dialog = ChooseResourceDialogFixture.find(myRobot);
    Color color = new Color(200, 0, 0, 200);
    dialog.setColorWithIntegers(color);
    dialog.clickOK();

    cellFont = themeEditorTable.valueFontAt(cell);
    assertNotNull(cellFont);
    assertEquals(Font.BOLD, cellFont.getStyle());
    assertEquals("android:colorBackground", themeEditorTable.attributeNameAt(cell));
    assertEquals("@color/background_holo_light", themeEditorTable.valueAt(cell));

    EditorFixture editor = projectFrame.getEditor();
    editor.open("app/src/main/res/values/colors.xml");
    editor.moveTo(editor.findOffset(null, "background", true));
    assertEquals("<color name=\"^background_holo_light\">" + ResourceHelper.colorToString(color) + "</color>",
                 editor.getCurrentLineContents(true, true, 0));
  }
}
