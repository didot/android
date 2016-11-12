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
package com.android.tools.idea.tests.gui.theme;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.Assert.*;

/**
 * Unit test for the theme preview
 */
@RunIn(TestGroup.THEME)
@RunWith(GuiTestRunner.class)
public class ThemePreviewTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testPreviewAvailability() throws Exception {
    guiTest.importSimpleApplication();

    Project project = guiTest.ideFrame().getProject();
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/values/styles.xml", EditorFixture.Tab.EDITOR);

    // No style or theme selected
    assertFalse(ToolWindowManager.getInstance(project).getToolWindow("Theme Preview").isAvailable());

    editor.moveBetween("PreviewTheme", "");
    guiTest.robot().waitForIdle();
    assertTrue(ToolWindowManager.getInstance(project).getToolWindow("Theme Preview").isAvailable());

    // A style is selected but it's not a theme so the preview shouldn't be available
    editor.moveBetween("NotATheme", "");
    guiTest.robot().waitForIdle();
    assertFalse(ToolWindowManager.getInstance(project).getToolWindow("Theme Preview").isAvailable());
  }

  @Test
  public void testToolbarState() throws Exception {
    guiTest.importSimpleApplication();

    Project project = guiTest.ideFrame().getProject();
    EditorFixture editor = guiTest.ideFrame().getEditor();

    editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR);
    int savedApiLevel = editor.getLayoutPreview(true).getConfigToolbar().getApiLevel();

    editor.open("app/src/main/res/values-v19/styles.xml", EditorFixture.Tab.EDITOR);
    editor.moveBetween("PreviewTheme", "");
    guiTest.robot().waitForIdle();
    assertTrue(ToolWindowManager.getInstance(project).getToolWindow("Theme Preview").isAvailable());

    // There is also a v20 styles.xml so in order to preview v19, it has to be selected in the toolbar
    editor.getThemePreview(true).getPreviewComponent().requireApi(19);

    editor.open("app/src/main/res/values/styles.xml", EditorFixture.Tab.EDITOR);
    editor.moveBetween("PreviewTheme", "");
    guiTest.robot().waitForIdle();
    editor.getThemePreview(true).getPreviewComponent().requireApi(18);

    editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR);
    // The API level shouldn't be modified by the theme preview. Regression test for http://b.android.com/201313
    assertThat(editor.getLayoutPreview(true).getConfigToolbar().getApiLevel()).isEqualTo(savedApiLevel);
  }
}
