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
package com.android.tools.idea.tests.gui.gradle;

import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.dsl.model.dependencies.ModuleDependencyTest.ExpectedModuleDependency;
import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.GradleBuildModelFixture;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JListFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static com.android.tools.idea.gradle.dsl.model.dependencies.CommonConfigurationNames.*;
import static com.android.tools.idea.tests.gui.framework.GuiTests.waitForPopup;
import static com.android.tools.idea.tests.gui.framework.TestGroup.PROJECT_SUPPORT;
import static com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.EditorAction.SHOW_INTENTION_ACTIONS;
import static com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.EditorAction.UNDO;
import static com.intellij.lang.annotation.HighlightSeverity.ERROR;
import static org.fest.assertions.Assertions.assertThat;

@BelongsToTestGroups({PROJECT_SUPPORT})
@RunWith(GuiTestRunner.class)
public class AddGradleDependencyTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Ignore("failed in http://go/aj/job/studio-ui-test/389 and from IDEA")
  @Test
  public void testAddProdModuleDependency() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("MultiModule");

    EditorFixture editor = guiTest.ideFrame().getEditor().open("app/src/main/java/com/android/multimodule/MainActivity.java");

    String classToImport = "com.example.MyLibrary";
    addImport(editor, classToImport);
    editor.waitForCodeAnalysisHighlightCount(ERROR, 1);
    moveCaretToClassName(editor, classToImport);

    editor.invokeIntentionAction("Add dependency on module 'library3'");
    guiTest.ideFrame().waitForGradleProjectSyncToFinish();
    editor.waitForCodeAnalysisHighlightCount(ERROR, 0);

    ExpectedModuleDependency dependencyOnLibrary3 = new ExpectedModuleDependency();
    dependencyOnLibrary3.configurationName = COMPILE;
    dependencyOnLibrary3.path = ":library3";

    GradleBuildModelFixture buildModel = guiTest.ideFrame().parseBuildFileForModule("app", false);
    buildModel.requireDependency(dependencyOnLibrary3);

    verifyUndo(editor, 1);
  }

  @Test
  public void testAddTestModuleDependency() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("MultiModule");

    EditorFixture editor = guiTest.ideFrame().getEditor().open("app/src/androidTest/java/com/android/multimodule/ApplicationTest.java");

    String classToImport = "com.example.MyLibrary";
    addImport(editor, classToImport);
    editor.waitForCodeAnalysisHighlightCount(ERROR, 1);
    moveCaretToClassName(editor, classToImport);

    editor.invokeIntentionAction("Add dependency on module 'library3'");
    guiTest.ideFrame().waitForGradleProjectSyncToFinish();
    editor.waitForCodeAnalysisHighlightCount(ERROR, 0);

    ExpectedModuleDependency dependencyOnLibrary3 = new ExpectedModuleDependency();
    dependencyOnLibrary3.configurationName = ANDROID_TEST_COMPILE;
    dependencyOnLibrary3.path = ":library3";

    GradleBuildModelFixture buildModel = guiTest.ideFrame().parseBuildFileForModule("app", false);
    buildModel.requireDependency(dependencyOnLibrary3);

    verifyUndo(editor, 1);
  }

  @Ignore("failed in http://go/aj/job/studio-ui-test/389 and from IDEA")
  @Test
  public void testAddLibDependencyDeclaredInJavaProject() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("MultiModule");

    GradleBuildModelFixture library3BuildModel = guiTest.ideFrame().parseBuildFileForModule("library3", false);
    ArtifactDependencySpec gson = new ArtifactDependencySpec("gson", "com.google.code.gson", "2.4");
    library3BuildModel.getTarget().dependencies().addArtifact(COMPILE, gson);
    library3BuildModel.applyChanges();
    guiTest.ideFrame().requestProjectSync().waitForGradleProjectSyncToFinish();

    EditorFixture editor = guiTest.ideFrame().getEditor().open("app/src/main/java/com/android/multimodule/MainActivity.java");

    String classToImport = "com.google.gson.Gson";
    addImport(editor, classToImport);
    editor.waitForCodeAnalysisHighlightCount(ERROR, 1);
    moveCaretToClassName(editor, classToImport);

    editor.invokeIntentionAction("Add library 'gson-2.4' to classpath");
    guiTest.ideFrame().waitForGradleProjectSyncToFinish();
    editor.waitForCodeAnalysisHighlightCount(ERROR, 0);

    GradleBuildModelFixture appBuildModel = guiTest.ideFrame().parseBuildFileForModule("app", false);
    appBuildModel.requireDependency(COMPILE, gson);

    verifyUndo(editor, 1);
  }

  @Ignore("failed in http://go/aj/job/studio-ui-test/389 and from IDEA")
  @Test
  public void testAddLibDependencyDeclaredInAndroidProject() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("MultiModule");

    GradleBuildModelFixture appBuildModel = guiTest.ideFrame().parseBuildFileForModule("app", false);
    ArtifactDependencySpec gson = new ArtifactDependencySpec("gson", "com.google.code.gson", "2.4");
    appBuildModel.getTarget().dependencies().addArtifact(COMPILE, gson);
    appBuildModel.applyChanges();
    guiTest.ideFrame().requestProjectSync().waitForGradleProjectSyncToFinish();

    EditorFixture editor = guiTest.ideFrame().getEditor().open("library3/src/main/java/com/example/MyLibrary.java");

    String classToImport = "com.google.gson.Gson";
    addImport(editor, classToImport);
    editor.waitForCodeAnalysisHighlightCount(ERROR, 1);
    moveCaretToClassName(editor, classToImport);

    editor.invokeIntentionAction("Add library 'gson-2.4' to classpath");
    guiTest.ideFrame().waitForGradleProjectSyncToFinish();
    editor.waitForCodeAnalysisHighlightCount(ERROR, 0);

    verifyUndo(editor, 1);
  }

  @Test
  public void testNoModuleDependencyQuickfixFromJavaToAndroid() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("MultiModule");

    EditorFixture editor = guiTest.ideFrame().getEditor().open("library3/src/main/java/com/example/MyLibrary.java");
    String classToImport = "com.android.multimodule.MainActivity";
    addImport(editor, classToImport);
    editor.waitForCodeAnalysisHighlightCount(ERROR, 1);
    moveCaretToClassName(editor, classToImport);

    assertIntentionNotIncluded(editor, "Add dependency on module");
  }

  @Test
  public void testNoModuleDependencyQuickfixFromAndroidLibToApplication() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("MultiModule");

    EditorFixture editor = guiTest.ideFrame().getEditor().open("library/src/main/java/com/android/library/MainActivity.java");
    String classToImport = "com.android.multimodule.MainActivity";
    addImport(editor, classToImport);
    editor.waitForCodeAnalysisHighlightCount(ERROR, 1);
    moveCaretToClassName(editor, classToImport);

    assertIntentionNotIncluded(editor, "Add dependency on module");
  }

  private void assertIntentionNotIncluded(@NotNull EditorFixture editor, @NotNull String intention) {
    editor.invokeAction(SHOW_INTENTION_ACTIONS);
    Robot robot = guiTest.robot();
    JListFixture popup = new JListFixture(robot, waitForPopup(robot));
    String[] intentions = popup.contents();
    assertThat(intentions).excludes(intention);
  }

  @Ignore("failed in http://go/aj/job/studio-ui-test/389 and from IDEA")
  @Test
  public void testAddJUnitDependency() throws IOException {
    guiTest.importSimpleApplication();

    EditorFixture editor = guiTest.ideFrame().getEditor().open("app/src/test/java/google/simpleapplication/UnitTest.java");

    editor.waitForCodeAnalysisHighlightCount(ERROR, 6);
    editor.moveTo(editor.findOffset("@^Test"));
    editor.invokeIntentionAction("Add 'JUnit4' to classpath");

    guiTest.ideFrame().waitForGradleProjectSyncToFinish();
    editor.waitForCodeAnalysisHighlightCount(ERROR, 0);

    GradleBuildModelFixture appBuildModel = guiTest.ideFrame().parseBuildFileForModule("app", false);
    ArtifactDependencySpec expected = new ArtifactDependencySpec("junit", "junit", "4.12");
    appBuildModel.requireDependency(TEST_COMPILE, expected);

    verifyUndo(editor, 6);
  }

  @Ignore("failed in http://go/aj/job/studio-ui-test/389 and from IDEA")
  @Test
  public void testAddJetbrainsAnnotationDependency() throws IOException {
    guiTest.importSimpleApplication();

    EditorFixture editor = guiTest.ideFrame().getEditor().open("app/src/main/java/google/simpleapplication/MyActivity.java");
    editor.moveTo(editor.findOffset("onCreate(^Bundle savedInstanceState) {"));
    editor.enterText("\n@NotNull ");

    editor.waitForCodeAnalysisHighlightCount(ERROR, 1);

    editor.moveTo(editor.findOffset("@Not^Null "));
    editor.invokeIntentionAction("Add 'annotations-java5' to classpath");

    guiTest.ideFrame().waitForGradleProjectSyncToFinish();
    editor.waitForCodeAnalysisHighlightCount(ERROR, 0);

    GradleBuildModelFixture appBuildModel = guiTest.ideFrame().parseBuildFileForModule("app", false);
    ArtifactDependencySpec expected = new ArtifactDependencySpec("annotations-java5", "org.jetbrains", "15.0");
    appBuildModel.requireDependency(COMPILE, expected);

    editor.invokeAction(UNDO); // Undo the import statement first
    verifyUndo(editor, 1);
  }

  private static void addImport(@NotNull EditorFixture editor, @NotNull String classFqn) {
    String importStatement = createImportStatement(classFqn);
    // Move caret to second line (first line has 'package' declaration).
    editor.moveToLine(1);
    editor.enterText("\n" + importStatement);
  }

  private static void moveCaretToClassName(@NotNull EditorFixture editor, @NotNull String classFqn) {
    String importStatement = createImportStatement(classFqn);
    int statementLength = importStatement.length();
    int position = statementLength - 4;
    String line = importStatement.substring(0, position) + '^' + importStatement.substring(position, statementLength);
    editor.moveTo(editor.findOffset(line));
  }

  @NotNull
  private static String createImportStatement(@NotNull String classFqn) {
    return "import " + classFqn + ';';
  }

  private void verifyUndo(@NotNull EditorFixture editor, int expectedErrorCount) {
    editor.invokeAction(UNDO);
    findAndCloseUndoDialog();
    guiTest.ideFrame().waitForGradleProjectSyncToFinish();
    editor.waitForCodeAnalysisHighlightCount(ERROR, expectedErrorCount);
  }

  private void findAndCloseUndoDialog() {
    guiTest.ideFrame().findMessageDialog("Undo").clickOk();
  }
}
