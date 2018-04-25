/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.RenameRefactoringDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.RenameRefactoringDialogFixture.ConflictsDialogFixture;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.regex.Pattern;

import static com.google.common.truth.Truth.assertThat;

/** Tests the editing flow of refactoring */
@RunIn(TestGroup.EDITING)
@RunWith(GuiTestRunner.class)
public class RefactoringFlowTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private static final String VALUE_REGEX =
    "(appcompat-v7/\\d+\\.\\d+\\.\\d+/res/values-\\p{Lower}.+/values.*.xml)+";

  @Test
  public void testResourceConflict() throws IOException {
    // Try to rename a resource to an existing resource; check that
    // you get a warning in the conflicts dialog first
    guiTest.importSimpleApplication();
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/values/strings.xml");
    editor.moveBetween("hello", "_world");
    guiTest.ideFrame().invokeMenuPath("Refactor", "Rename...");

    // Rename as action_settings, which is already defined
    RenameRefactoringDialogFixture refactoringDialog = RenameRefactoringDialogFixture.find(guiTest.robot());
    refactoringDialog.setNewName("action_settings");
    refactoringDialog.clickRefactor();

    ConflictsDialogFixture conflictsDialog = ConflictsDialogFixture.find(guiTest.robot());
    assertThat(conflictsDialog.getText()).contains("Resource @string/action_settings already exists");
    conflictsDialog.clickCancel();
    refactoringDialog.clickCancel();
  }

  @Ignore("fails with Gradle plugin 2.3.0-dev")
  @Test()
  public void testWarnOverridingExternal() throws Exception {
    // Try to override a resource that is only defined in an external
    // library; check that we get an error message. Then try to override
    // a resource that is both overridden locally and externally, and
    // check that we get a warning dialog. And finally try to override
    // a resource that is only defined locally and make sure there is
    // no dialog.

    guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest");
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/values/override.xml");
    // <string name="abc_searchview_description_submit">@string/abc_searchview_description_voice</string>
    editor.moveBetween("abc_searchview_", "description_voice"); // only defined in appcompat
    guiTest.ideFrame().invokeMenuPath("Refactor", "Rename...");

    RenameRefactoringDialogFixture refactoringDialog = RenameRefactoringDialogFixture.find(guiTest.robot());
    refactoringDialog.setNewName("a");
    refactoringDialog.clickRefactor();

    ConflictsDialogFixture conflictsDialog = ConflictsDialogFixture.find(guiTest.robot());
    String text = conflictsDialog.getText().replace("\n", "");
    assertThat(text).matches(
      Pattern.quote("Resource is also only defined in external libraries and" +
                    "cannot be renamed.Unhandled references:") +
      VALUE_REGEX +
      Pattern.quote("...(Additional results truncated)"));
    conflictsDialog.clickCancel();
    refactoringDialog.clickCancel();

    // Now try to rename @string/abc_searchview_description_submit which is defined in *both* appcompat and locally
    editor.moveBetween("abc_searchview_", "description_submit"); // only defined in appcompat
    guiTest.ideFrame().invokeMenuPath("Refactor", "Rename...");

    refactoringDialog = RenameRefactoringDialogFixture.find(guiTest.robot());
    refactoringDialog.setNewName("a");
    refactoringDialog.clickRefactor();

    conflictsDialog = ConflictsDialogFixture.find(guiTest.robot());
    text = conflictsDialog.getText().replace("\n", "");
    assertThat(text).matches(
      Pattern.quote("The resource @string/abc_searchview_description_submit is" +
                    "defined outside of the project (in one of the libraries) and" +
                    "cannot be updated. This can change the behavior of the" +
                    "application.Are you sure you want to do this?Unhandled references:") +
      VALUE_REGEX +
      Pattern.quote("...(Additional results truncated)"));
    conflictsDialog.clickCancel();
    refactoringDialog.clickCancel();
  }
}
