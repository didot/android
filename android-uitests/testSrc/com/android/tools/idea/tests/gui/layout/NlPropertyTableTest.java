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
package com.android.tools.idea.tests.gui.layout;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.NlComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.NlPropertyTableFixture;
import com.android.tools.idea.uibuilder.property.NlPropertiesPanel;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.fest.swing.data.TableCellInSelectedRow;
import org.fest.swing.fixture.AbstractComponentFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.swing.text.JTextComponent;
import java.awt.*;

import static com.google.common.truth.Truth.assertThat;
import static java.awt.event.KeyEvent.*;

/**
 * UI test for the layout preview window
 */
@RunWith(GuiTestRunner.class)
public class NlPropertyTableTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testScrollInViewDuringKeyboardNavigation() throws Exception {
    NlEditorFixture layout = guiTest.importSimpleApplication()
      .getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR)
      .getLayoutEditor(true)
      .waitForRenderToFinish();

    layout.findView("TextView", 0).click();
    layout.getPropertiesPanel()
      .openAsInspector()
      .adjustIdeFrameHeightFor(4, "ID")
      .focusAndWaitForFocusGainInProperty("ID", null)
      .assertPropertyShowing("text", null)
      .assertPropertyShowing("ID", null)
      .assertPropertyNotShowing("visibility", null)
      .tab() // width
      .tab() // width resource editor
      .tab() // height
      .tab() // height resource editor
      .tab().assertFocusInProperty("text", null)
      .tab() // text resource editor
      .tab() // design text
      .tab() // design text resource editor
      .tab() // contentDescription
      .tab() // contentDescription resource editor
      .tab() // textAppearance
      .tab() // fontFamily
      .tab() // typeFace
      .tab() // textSize
      .tab() // textSize resource editor
      .tab() // lineSpacingExtra
      .tab() // lineSpacingExtra resource editor
      .tab().assertFocusInProperty("textColor", null)
      .tab() // textColor resource editor
      .tab() // textStyle: bold
      .tab() // textStyle: italics
      .tab() // textStyle: AllCaps
      .tab() // textAlignment: Start
      .tab() // textAlignment: Left
      .tab() // textAlignment: Center
      .tab() // textAlignment: Right
      .tab() // textAlignment: End
      .tab().assertFocusInProperty("visibility", null)
      .tab() // View all properties link
      .assertPropertyNotShowing("text", null)
      .assertPropertyNotShowing("ID", null)
      .assertPropertyShowing("visibility", null)
      .tab().assertFocusInProperty("ID", null)
      .assertPropertyShowing("ID", null)
      .assertPropertyShowing("text", null)
      .assertPropertyNotShowing("visibility", null)
      .tabBack()
      .assertPropertyNotShowing("ID", null)
      .assertPropertyNotShowing("text", null)
      .assertPropertyShowing("visibility", null);
  }

  @RunIn(TestGroup.UNRELIABLE)  // Until this test has proven itself reliable
  @Test
  public void testSimpleKeyboardEditingInTable() throws Exception {
    // If this UI test should fail, this is the intention with the test.
    //
    // Test the following simple keyboard editing tasks in the property table:
    //  - Navigate to the row with the text property
    //  - Type a character 'a' which should bring up the editor for the text value
    //  - Type a character 'b' in the editor
    //  - Press ESC to dismiss the completion lookup hint list
    //  - Press arrow down to commit the changes and navigate to the next property: accessibilityLiveRegion
    // Verify that the text value is now "ab" and the selected row is indeed "accessibilityLiveRegion"

    IdeFrameFixture frame = guiTest.importSimpleApplication();
    Project project = frame.getProject();
    NlEditorFixture layout = frame
      .getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR)
      .getLayoutEditor(true)
      .waitForRenderToFinish();

    NlComponentFixture textView = layout.findView("TextView", 0).click();

    NlPropertyTableFixture table = layout.getPropertiesPanel().openAsTable();
    table.waitForMinimumRowCount(10, Wait.seconds(5));
    table.pressAndReleaseKeys(VK_DOWN, VK_DOWN, VK_DOWN, VK_DOWN, VK_DOWN, VK_DOWN, VK_DOWN);
    table.type('a');
    JTextComponentFixture textEditor = waitForEditorToShow(Wait.seconds(3));
    Thread.sleep(2000);
    type(textEditor, "b");
    waitForLookupToShow(project, Wait.seconds(3));
    textEditor.pressAndReleaseKeys(VK_ESCAPE);
    waitForLookupToHide(project, Wait.seconds(3));
    textEditor.pressAndReleaseKeys(VK_DOWN);

    assertThat(textView.getTextAttribute()).isEqualTo("ab");
    assertThat(table.cell(new TableCellInSelectedRow.TableCellBuilder().column(0)).value()).isEqualTo("@android:accessibilityLiveRegion");
  }

  @RunIn(TestGroup.UNRELIABLE)  // Until this test has proven itself reliable
  @Test
  public void testSelectCompletionFinishesEditingOfCell() throws Exception {
    // If this UI test should fail, this is the intention with the test.
    //
    // Test the following simple keyboard editing tasks in the property table:
    //  - Navigate to the row with the text property
    //  - Type a character 's' which should bring up the editor for the text value
    //  - Type a characters "tring/copy" in the editor
    //  - Press ENTER to accept the completion suggestion
    // Verify that the text value is now the chosen value and the focus is back on the table (we are not editing anymore).

    IdeFrameFixture frame = guiTest.importSimpleApplication();
    Project project = frame.getProject();
    NlEditorFixture layout = frame
      .getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR)
      .getLayoutEditor(true)
      .waitForRenderToFinish();

    NlComponentFixture textView = layout.findView("TextView", 0).click();
    NlPropertyTableFixture table = layout.getPropertiesPanel().openAsTable();
    table.waitForMinimumRowCount(10, Wait.seconds(5));

    table.selectRows(7);
    table.type('s');
    JTextComponentFixture textEditor = waitForEditorToShow(Wait.seconds(3));
    type(textEditor, "tring/copy");
    waitForLookupToShow(project, Wait.seconds(3));

    textEditor.pressAndReleaseKeys(VK_ENTER);

    assertThat(textView.getTextAttribute()).isEqualTo("@android:string/copy");
    assertThat(table.target()).isEqualTo(getFocusOwner());
  }

  @RunIn(TestGroup.UNRELIABLE)  // Until this test has proven itself reliable
  @Test
  public void testSimpleKeyboardNavigationInTable() throws Exception {
    IdeFrameFixture frame = guiTest.importSimpleApplication();
    NlEditorFixture layout = frame
      .getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR)
      .getLayoutEditor(true)
      .waitForRenderToFinish();

    layout.findView("TextView", 0).click();
    NlPropertyTableFixture table = layout.getPropertiesPanel().openAsTable();
    Wait.seconds(10).expecting("The table to render").until(() -> table.target().getVisibleRect().getHeight() > 0);

    // Test original window size
    int movement = (int)(table.target().getVisibleRect().getHeight() / table.target().getRowHeight());
    testNavigationInTable(table, movement);

    // Test if it still work with Filter
    NlPropertiesPanel parentPanel = table.getParentPropertiesPanel();
    // In our test case, the PropertyTable must contained by a NlPropertiesPanel.
    assertThat(parentPanel).isNotNull();
    parentPanel.setFilter("an");
    movement = (int)(table.target().getVisibleRect().getHeight() / table.target().getRowHeight());
    testNavigationInTable(table, movement);
    parentPanel.setFilter("");

    // Test another window size
    final int newRowSize = 4;
    table.adjustIdeFrameHeightToShowNumberOfRow(newRowSize);
    Wait.seconds(10).expecting("The table to resize").until(
      () -> table.target().getVisibleRect().getHeight() == table.target().getRowHeight() * newRowSize
    );
    testNavigationInTable(table, newRowSize);
  }

  private static void testNavigationInTable(NlPropertyTableFixture table, int expectedMovement) {
    // Test page down
    ApplicationManager.getApplication()
      .invokeAndWait(() -> table.target().getSelectionModel().setSelectionInterval(0, 0));
    table.pressAndReleaseKeys(VK_PAGE_DOWN);
    table.requireSelectedRows(Math.min(expectedMovement, table.rowCount() - 1));

    // Make sure page down will not effect when current selection is last one
    ApplicationManager.getApplication()
      .invokeAndWait(() -> table.target().getSelectionModel().setSelectionInterval(table.rowCount() - 1, table.rowCount() - 1));
    table.pressAndReleaseKeys(VK_PAGE_DOWN);
    table.requireSelectedRows(table.rowCount() - 1);

    // Test page up
    ApplicationManager.getApplication()
      .invokeAndWait(() -> table.target().getSelectionModel().setSelectionInterval(table.rowCount() - 1, table.rowCount() - 1));
    table.pressAndReleaseKeys(VK_PAGE_UP);
    table.requireSelectedRows(Math.max(0, table.rowCount() - 1 - expectedMovement));

    // Make sure page up will not effect when current selection is first one
    ApplicationManager.getApplication()
      .invokeAndWait(() -> table.target().getSelectionModel().setSelectionInterval(0, 0));
    table.pressAndReleaseKeys(VK_PAGE_UP);
    table.requireSelectedRows(0);
  }

  private static void waitForLookupToShow(@NotNull Project project, @NotNull Wait waitForLookup) {
    LookupManager manager = LookupManager.getInstance(project);
    waitForLookup.expecting("lookup to show").until(() -> manager.getActiveLookup() != null);
  }

  private static void waitForLookupToHide(@NotNull Project project, @NotNull Wait waitForLookup) {
    LookupManager manager = LookupManager.getInstance(project);
    waitForLookup.expecting("lookup to hide").until(() -> manager.getActiveLookup() == null);
  }

  private JTextComponentFixture waitForEditorToShow(@NotNull Wait waitForEditor) {
    waitForEditor.expecting("editor to show").until(() -> getFocusOwner() instanceof JTextComponent);
    return new JTextComponentFixture(guiTest.robot(), (JTextComponent)getFocusOwner());
  }

  private static Component getFocusOwner() {
    return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
  }

  // JTextComponentFixture.enterText doesn't work on some Mac platforms.
  // This is a workaround that does work on all platforms.
  private static void type(@NotNull AbstractComponentFixture fixture, @NotNull String value) {
    Component source = fixture.target();
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      fixture.robot().type(character, source);
    }
  }
}
