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
import com.android.tools.idea.tests.gui.framework.fixture.layout.NlComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.application.ApplicationManager;
import org.fest.swing.finder.WindowFinder;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.fixture.JButtonFixture;
import org.intellij.lang.annotations.Language;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.awt.*;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static org.fest.swing.core.matcher.JButtonMatcher.withText;
import static org.junit.Assert.assertNotNull;

@RunIn(TestGroup.LAYOUT)
@RunWith(GuiTestRunner.class)
public class ConvertToConstraintLayoutTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Ignore("http://b.android.com/211200")
  @Test
  public void testConvert() throws Exception {
    guiTest.importSimpleApplication();

    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/layout/absolute.xml", EditorFixture.Tab.DESIGN);

    NlEditorFixture layout = editor.getLayoutEditor(false);
    assertNotNull(layout);
    layout.waitForRenderToFinish();

    // Find and click the first button
    NlComponentFixture button = layout.findView("Button", 0);
    button.invokeContextMenuAction("Convert AbsoluteLayout to ConstraintLayout");

    // Confirm dialog
    DialogFixture quickFixDialog = WindowFinder.findDialog(Matchers.byTitle(Dialog.class, "Convert to ConstraintLayout"))
      .withTimeout(TimeUnit.MINUTES.toMillis(2)).using(guiTest.robot());

    // Press OK
    JButtonFixture finish = quickFixDialog.button(withText("OK"));
    finish.click();

    // Check that we've converted to what we expected
    layout.waitForRenderToFinish();
    editor.selectEditorTab(EditorFixture.Tab.EDITOR);
    waitForScout();
    editor.invokeAction(EditorFixture.EditorAction.FORMAT);

    @Language("XML")
    String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<android.support.constraint.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
            "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
            "    android:id=\"@+id/constraintLayout\"\n" +
            "    android:layout_width=\"match_parent\"\n" +
            "    android:layout_height=\"match_parent\"\n" +
            "    tools:layout_editor_absoluteX=\"<test>\"\n" +
            "    tools:layout_editor_absoluteY=\"<test>\">\n" +
            "\n" +
            "    <Button\n" +
            "        android:id=\"@+id/button\"\n" +
            "        android:layout_width=\"wrap_content\"\n" +
            "        android:layout_height=\"wrap_content\"\n" +
            "        android:layout_marginStart=\"<test>\"\n" +
            "        android:layout_marginTop=\"<test>\"\n" +
            "        android:text=\"Button\"\n" +
            "        app:layout_constraintLeft_toLeftOf=\"@+id/constraintLayout\"\n" +
            "        app:layout_constraintTop_toTopOf=\"@+id/constraintLayout\"\n" +
            "        tools:layout_constraintLeft_creator=\"1\"\n" +
            "        tools:layout_constraintTop_creator=\"1\"\n" +
            "        tools:layout_editor_absoluteX=\"<test>\"\n" +
            "        tools:layout_editor_absoluteY=\"<test>\" />\n" +
            "\n" +
            "    <Button\n" +
            "        android:id=\"@+id/button2\"\n" +
            "        android:layout_width=\"wrap_content\"\n" +
            "        android:layout_height=\"wrap_content\"\n" +
            "        android:layout_marginStart=\"<test>\"\n" +
            "        android:text=\"Button\"\n" +
            "        app:layout_constraintLeft_toLeftOf=\"@+id/button\"\n" +
            "        app:layout_constraintTop_toBottomOf=\"@+id/button\"\n" +
            "        tools:layout_constraintLeft_creator=\"1\"\n" +
            "        tools:layout_constraintTop_creator=\"1\"\n" +
            "        tools:layout_editor_absoluteX=\"<test>\"\n" +
            "        tools:layout_editor_absoluteY=\"<test>\" />\n" +
            "\n" +
            "    <EditText\n" +
            "        android:id=\"@+id/editText\"\n" +
            "        android:layout_width=\"wrap_content\"\n" +
            "        android:layout_height=\"wrap_content\"\n" +
            "        android:layout_marginBottom=\"<test>\"\n" +
            "        android:layout_marginEnd=\"<test>\"\n" +
            "        android:layout_marginTop=\"<test>\"\n" +
            "        android:ems=\"10\"\n" +
            "        android:inputType=\"textPersonName\"\n" +
            "        android:text=\"Name\"\n" +
            "        app:layout_constraintBottom_toBottomOf=\"@+id/button6\"\n" +
            "        app:layout_constraintRight_toRightOf=\"@+id/constraintLayout\"\n" +
            "        app:layout_constraintTop_toTopOf=\"@+id/constraintLayout\"\n" +
            "        tools:layout_constraintBottom_creator=\"1\"\n" +
            "        tools:layout_constraintRight_creator=\"1\"\n" +
            "        tools:layout_constraintTop_creator=\"1\"\n" +
            "        tools:layout_editor_absoluteX=\"<test>\"\n" +
            "        tools:layout_editor_absoluteY=\"<test>\" />\n" +
            "\n" +
            "\n" +
            "    <Button\n" +
            "        android:id=\"@+id/button3\"\n" +
            "        android:layout_width=\"wrap_content\"\n" +
            "        android:layout_height=\"wrap_content\"\n" +
            "        android:layout_marginStart=\"<test>\"\n" +
            "        android:text=\"Button\"\n" +
            "        app:layout_constraintBottom_toBottomOf=\"@+id/button5\"\n" +
            "        app:layout_constraintLeft_toLeftOf=\"@+id/constraintLayout\"\n" +
            "        app:layout_constraintTop_toTopOf=\"@+id/button5\"\n" +
            "        tools:layout_constraintBottom_creator=\"1\"\n" +
            "        tools:layout_constraintLeft_creator=\"1\"\n" +
            "        tools:layout_constraintTop_creator=\"1\"\n" +
            "        tools:layout_editor_absoluteX=\"<test>\"\n" +
            "        tools:layout_editor_absoluteY=\"<test>\" />\n" +
            "\n" +
            "    <Button\n" +
            "        android:id=\"@+id/button5\"\n" +
            "        android:layout_width=\"wrap_content\"\n" +
            "        android:layout_height=\"wrap_content\"\n" +
            "        android:layout_marginBottom=\"<test>\"\n" +
            "        android:text=\"Button\"\n" +
            "        app:layout_constraintBottom_toBottomOf=\"@+id/constraintLayout\"\n" +
            "        app:layout_constraintLeft_toLeftOf=\"@+id/constraintLayout\"\n" +
            "        app:layout_constraintRight_toRightOf=\"@+id/constraintLayout\"\n" +
            "        tools:layout_constraintBottom_creator=\"1\"\n" +
            "        tools:layout_constraintLeft_creator=\"1\"\n" +
            "        tools:layout_constraintRight_creator=\"1\"\n" +
            "        tools:layout_editor_absoluteX=\"<test>\"\n" +
            "        tools:layout_editor_absoluteY=\"<test>\" />\n" +
            "\n" +
            "    <Button\n" +
            "        android:id=\"@+id/button6\"\n" +
            "        android:layout_width=\"wrap_content\"\n" +
            "        android:layout_height=\"wrap_content\"\n" +
            "        android:layout_marginEnd=\"<test>\"\n" +
            "        android:text=\"Button\"\n" +
            "        app:layout_constraintBottom_toBottomOf=\"@+id/button5\"\n" +
            "        app:layout_constraintRight_toRightOf=\"@+id/constraintLayout\"\n" +
            "        app:layout_constraintTop_toTopOf=\"@+id/button5\"\n" +
            "        tools:layout_constraintBottom_creator=\"1\"\n" +
            "        tools:layout_constraintRight_creator=\"1\"\n" +
            "        tools:layout_constraintTop_creator=\"1\"\n" +
            "        tools:layout_editor_absoluteX=\"<test>\"\n" +
            "        tools:layout_editor_absoluteY=\"<test>\" />\n" +
            "\n" +
            "</android.support.constraint.ConstraintLayout>\n" +
            "\n";
    assertThat(wipeDimensions(editor.getCurrentFileContents())).isEqualTo(wipeDimensions(xml));
  }

  private static String wipeDimensions(@Language("XML") String xml) {
    // Remove specific pixel sizes from an XML layout before pretty printing it; they may very from machine
    // to machine. It's the constraints that matter.
    xml = xml.replaceAll("tools:(.*)=\"(.*)dp\"", "tools:$1=\"<test>\"");
    xml = xml.replaceAll("android:(.*)=\"(.*)dp\"", "android:$1=\"<test>\"");
    return xml;
  }

  @Ignore("http://b.android.com/211200")
  @Test
  public void testConvert2() throws Exception {
    guiTest.importSimpleApplication();

    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/layout/frames.xml", EditorFixture.Tab.DESIGN);

    NlEditorFixture layout = editor.getLayoutEditor(false);
    assertNotNull(layout);
    layout.waitForRenderToFinish();

    // Find and click the first text View
    NlComponentFixture button = layout.findView("TextView", 0);
    button.invokeContextMenuAction("Convert LinearLayout to ConstraintLayout");

    // Confirm dialog
    DialogFixture quickFixDialog = WindowFinder.findDialog(Matchers.byTitle(Dialog.class, "Convert to ConstraintLayout"))
      .withTimeout(TimeUnit.MINUTES.toMillis(2)).using(guiTest.robot());

    // Press OK
    JButtonFixture finish = quickFixDialog.button(withText("OK"));
    finish.click();

    // Check that we've converted to what we expected
    layout.waitForRenderToFinish();
    editor.selectEditorTab(EditorFixture.Tab.EDITOR);
    waitForScout();
    editor.invokeAction(EditorFixture.EditorAction.FORMAT);

    @Language("XML")
    String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<android.support.constraint.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
            "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
            "    android:id=\"@+id/constraintLayout\"\n" +
            "    android:layout_width=\"match_parent\"\n" +
            "    android:layout_height=\"wrap_content\"\n" +
            "    android:orientation=\"vertical\"\n" +
            "    tools:layout_editor_absoluteX=\"<test>\"\n" +
            "    tools:layout_editor_absoluteY=\"<test>\">\n" +
            "\n" +
            "    <TextView\n" +
            "        android:id=\"@+id/title\"\n" +
            "        android:layout_width=\"wrap_content\"\n" +
            "        android:layout_height=\"wrap_content\"\n" +
            "        android:text=\"Welcome\"\n" +
            "        app:layout_constraintBottom_toBottomOf=\"@+id/constraintLayout\"\n" +
            "        app:layout_constraintLeft_toLeftOf=\"@+id/constraintLayout\"\n" +
            "        app:layout_constraintRight_toLeftOf=\"@+id/constraintLayout\"\n" +
            "        app:layout_constraintTop_toBottomOf=\"@+id/constraintLayout\"\n" +
            "        tools:layout_constraintBottom_creator=\"1\"\n" +
            "        tools:layout_constraintLeft_creator=\"1\"\n" +
            "        tools:layout_constraintRight_creator=\"1\"\n" +
            "        tools:layout_constraintTop_creator=\"1\"\n" +
            "        tools:layout_editor_absoluteX=\"<test>\"\n" +
            "        tools:layout_editor_absoluteY=\"<test>\" />\n" +
            "\n" +
            "    <FrameLayout\n" +
            "        android:id=\"@+id/attending_remotely\"\n" +
            "        android:layout_width=\"0dp\"\n" +
            "        android:layout_height=\"wrap_content\"\n" +
            "        android:foreground=\"?android:selectableItemBackground\"\n" +
            "        app:layout_constraintBottom_toBottomOf=\"@+id/constraintLayout\"\n" +
            "        app:layout_constraintLeft_toLeftOf=\"@+id/constraintLayout\"\n" +
            "        app:layout_constraintRight_toLeftOf=\"@+id/constraintLayout\"\n" +
            "        app:layout_constraintTop_toBottomOf=\"@+id/constraintLayout\"\n" +
            "        tools:layout_constraintBottom_creator=\"1\"\n" +
            "        tools:layout_constraintLeft_creator=\"1\"\n" +
            "        tools:layout_constraintRight_creator=\"1\"\n" +
            "        tools:layout_constraintTop_creator=\"1\"\n" +
            "        tools:layout_editor_absoluteX=\"<test>\"\n" +
            "        tools:layout_editor_absoluteY=\"<test>\">\n" +
            "\n" +
            "        <ImageView\n" +
            "            android:layout_width=\"100dp\"\n" +
            "            android:layout_height=\"100dp\"\n" +
            "            android:adjustViewBounds=\"true\"\n" +
            "            android:scaleType=\"centerInside\"\n" +
            "            app:layout_constraintLeft_toLeftOf=\"@+id/attending_remotely\"\n" +
            "            tools:layout_constraintLeft_creator=\"1\"\n" +
            "            tools:layout_editor_absoluteX=\"<test>\"\n" +
            "            tools:layout_editor_absoluteY=\"<test>\" />\n" +
            "\n" +
            "        <TextView\n" +
            "            android:layout_width=\"wrap_content\"\n" +
            "            android:layout_height=\"wrap_content\"\n" +
            "            android:layout_gravity=\"bottom|end|right\"\n" +
            "            android:text=\"Remotely\"\n" +
            "            tools:layout_editor_absoluteX=\"<test>\"\n" +
            "            tools:layout_editor_absoluteY=\"<test>\" />\n" +
            "\n" +
            "    </FrameLayout>\n" +
            "\n" +
            "    <FrameLayout\n" +
            "        android:id=\"@+id/attending_in_person\"\n" +
            "        android:layout_width=\"0dp\"\n" +
            "        android:layout_height=\"wrap_content\"\n" +
            "        android:foreground=\"?android:selectableItemBackground\"\n" +
            "        app:layout_constraintBottom_toBottomOf=\"@+id/constraintLayout\"\n" +
            "        app:layout_constraintLeft_toLeftOf=\"@+id/constraintLayout\"\n" +
            "        app:layout_constraintRight_toLeftOf=\"@+id/constraintLayout\"\n" +
            "        app:layout_constraintTop_toBottomOf=\"@+id/constraintLayout\"\n" +
            "        tools:layout_constraintBottom_creator=\"1\"\n" +
            "        tools:layout_constraintLeft_creator=\"1\"\n" +
            "        tools:layout_constraintRight_creator=\"1\"\n" +
            "        tools:layout_constraintTop_creator=\"1\"\n" +
            "        tools:layout_editor_absoluteX=\"<test>\"\n" +
            "        tools:layout_editor_absoluteY=\"<test>\">\n" +
            "\n" +
            "        <ImageView\n" +
            "            android:layout_width=\"100dp\"\n" +
            "            android:layout_height=\"100dp\"\n" +
            "            android:adjustViewBounds=\"true\"\n" +
            "            android:scaleType=\"centerInside\"\n" +
            "            app:layout_constraintLeft_toLeftOf=\"@+id/attending_in_person\"\n" +
            "            tools:layout_constraintLeft_creator=\"1\"\n" +
            "            tools:layout_editor_absoluteX=\"<test>\"\n" +
            "            tools:layout_editor_absoluteY=\"<test>\" />\n" +
            "\n" +
            "        <TextView\n" +
            "            android:layout_width=\"wrap_content\"\n" +
            "            android:layout_height=\"wrap_content\"\n" +
            "            android:layout_gravity=\"bottom|end|right\"\n" +
            "            android:text=\"In Person\"\n" +
            "            tools:layout_editor_absoluteX=\"<test>\"\n" +
            "            tools:layout_editor_absoluteY=\"<test>\" />\n" +
            "\n" +
            "    </FrameLayout>\n" +
            "\n" +
            "</android.support.constraint.ConstraintLayout>\n";
    assertThat(wipeDimensions(editor.getCurrentFileContents())).isEqualTo(wipeDimensions(xml));
  }

  private void waitForScout() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        // No op
      }
    });
    guiTest.robot().waitForIdle();
  }
}
