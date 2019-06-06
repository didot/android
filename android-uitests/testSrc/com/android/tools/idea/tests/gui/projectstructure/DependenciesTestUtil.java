/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.projectstructure;

import static org.fest.swing.core.MouseButton.RIGHT_BUTTON;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.parser.Dependency;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.structure.editors.ModuleDependenciesTableItem;
import com.android.tools.idea.gradle.structure.editors.ModuleDependenciesTableModel;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.CreateFileFromTemplateDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ProjectViewFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewModuleWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.projectstructure.DependencyTabFixture;
import com.android.tools.idea.tests.gui.framework.fixture.projectstructure.ProjectStructureDialogFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.ui.table.JBTable;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.fest.swing.exception.LocationUnavailableException;
import org.fest.swing.exception.WaitTimedOutError;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;

public class DependenciesTestUtil {

  protected static final String APP_NAME = "App";
  protected static final String MIN_SDK = "18";
  protected static final String JAVA_LIBRARY = "Java Library";
  protected static final String CLASS_NAME_1 = "ModuleA";
  protected static final String CLASS_NAME_2 = "ModuleB";
  protected static final String ANDROID_LIBRARY = "Android Library";
  protected static final String LANGUAGE_JAVA = "Java";
  protected static final String LANGUAGE_KOTLIN = "Kotlin";

  @Before
  public void setUp() {
    StudioFlags.NEW_PSD_ENABLED.override(false);
  }

  @After
  public void tearDown() {
    StudioFlags.NEW_PSD_ENABLED.clearOverride();
  }

  @NotNull
  protected static IdeFrameFixture createNewProject(@NotNull GuiTestRule guiTest,
                                                    @NotNull String appName,
                                                    @NotNull String minSdk,
                                                    @NotNull String language) {
    guiTest
      .welcomeFrame()
      .createNewProject()
      .getChooseAndroidProjectStep()
      .chooseActivity("Empty Activity")
      .wizard()
      .clickNext()
      .getConfigureNewAndroidProjectStep()
      .enterName(appName)
      .enterPackageName("android.com.app")
      .selectMinimumSdkApi(minSdk)
      .setSourceLanguage(language)
      .wizard()
      .clickFinish();

    return guiTest.ideFrame().waitForGradleProjectSyncToFinish(Wait.seconds(30));
  }

  protected static void createJavaModule(@NotNull IdeFrameFixture ideFrame) {
    ideFrame.openFromMenu(NewModuleWizardFixture::find, "File", "New", "New Module...")
      .chooseModuleType(JAVA_LIBRARY)
      .clickNextToStep(JAVA_LIBRARY)
      .clickFinish() // Use default Java Module name.
      .waitForGradleProjectSyncToFinish();
  }

  protected static void accessLibraryClassAndVerify(@NotNull IdeFrameFixture ideFrame,
                                                    @NotNull String module1,
                                                    @NotNull String modeule2) {
    // Accessing Library2 classes in Library1, and verify.
    ideFrame.getEditor().open(module1 + "/src/main/java/android/com/" + module1 + "/" + CLASS_NAME_1 + ".java")
      .moveBetween("package android.com." + module1 + ";", "")
      .enterText("\n\nimport android.com." + modeule2 + "." + CLASS_NAME_2 + ";")
      .moveBetween("public class " + CLASS_NAME_1 + " {", "")
      .enterText("\n" + CLASS_NAME_2 + " className2 = new " + CLASS_NAME_2 + "();");
    GradleInvocationResult result = ideFrame.invokeProjectMake();
    assertTrue(result.isBuildSuccessful());

    // Accessing both Library1 and Library2 classes in app module, and verify.
    ideFrame.getEditor().open("app/src/main/java/android/com/app/MainActivity.java")
      .moveBetween("import android.os.Bundle;", "")
      .enterText("\nimport android.com." + module1 + "." + CLASS_NAME_1 + ";" +
                 "\nimport android.com." + modeule2 + "." + CLASS_NAME_2 + ";")
      //.moveBetween("", "setContentView(R.layout.activity_main);")
      .moveBetween("protected void onCreate(Bundle savedInstanceState) {", "")
      .enterText("\n" + CLASS_NAME_1 + " classNameA = new " + CLASS_NAME_1 + "();")
      .enterText("\n" + CLASS_NAME_2 + " classNameB = new " + CLASS_NAME_2 + "();");
    result = ideFrame.invokeProjectMake();
    assertTrue(result.isBuildSuccessful());
  }

  protected static void addModuleDependencyUnderAnother(@NotNull IdeFrameFixture ideFrame,
                                                        @NotNull String moduleName,
                                                        @NotNull String anotherModule,
                                                        @NotNull String scope) {
    ideFrame.getProjectView()
      .selectProjectPane()
      .clickPath(RIGHT_BUTTON, APP_NAME, anotherModule);

    ideFrame.invokeMenuPath("Open Module Settings");

    String module = ":" + moduleName;
    DependencyTabFixture dependencyTabFixture = ProjectStructureDialogFixture.find(ideFrame)
      .selectDependenciesTab()
      .addModuleDependency(module);

    if (scope.equals(Dependency.Scope.API.getDisplayName())) {
      JBTable jbTable = GuiTests.waitUntilFound(ideFrame.robot(),
        dependencyTabFixture.target(),
        Matchers.byType(JBTable.class).andIsShowing());

      ModuleDependenciesTableModel tableModel = (ModuleDependenciesTableModel) jbTable.getModel();

      for (ModuleDependenciesTableItem item : tableModel.getItems()) {
        Dependency dependencyEntry = (Dependency) item.getEntry();
        if (module.equals(dependencyEntry.getValueAsString())) {
          item.setScope(Dependency.Scope.API);
          break;
        }
      }
    }

    dependencyTabFixture.clickOk();
  }

  protected static void createAndroidLibrary(@NotNull IdeFrameFixture ideFrame,
                                             @NotNull String moduleName) {
    ideFrame.openFromMenu(NewModuleWizardFixture::find, "File", "New", "New Module...")
      .chooseModuleType(ANDROID_LIBRARY)
      .clickNextToStep(ANDROID_LIBRARY)
      .setModuleName(moduleName)
      .clickFinish()
      .waitForGradleProjectSyncToFinish();
  }

  protected static void createJavaClassInModule(@NotNull IdeFrameFixture ideFrame,
                                                @NotNull String moduleName,
                                                @NotNull String className) {

    ProjectViewFixture.PaneFixture paneFixture;
    try {
      paneFixture = ideFrame.getProjectView().selectProjectPane();
    } catch(WaitTimedOutError timeout) {
      throw new RuntimeException(getUiHierarchy(ideFrame), timeout);
    }

    Wait.seconds(30).expecting("Path is loaded for clicking").until(() -> {
      try {
        paneFixture.clickPath(APP_NAME, moduleName, "src", "main", "java", "android.com." + moduleName);
        return true;
      } catch (LocationUnavailableException e) {
        return false;
      }
    });

    ideFrame.invokeMenuPath("File", "New", "Java Class");
    CreateFileFromTemplateDialogFixture dialog = CreateFileFromTemplateDialogFixture.find(ideFrame.robot());
    dialog.setName(className);
    dialog.clickOk();
  }

  @NotNull
  private static String getUiHierarchy(@NotNull IdeFrameFixture ideFrame) {
    try(
      ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
      PrintStream printBuffer = new PrintStream(outputBuffer)
    ) {
      ideFrame.robot().printer().printComponents(printBuffer);
      return outputBuffer.toString();
    } catch (java.io.IOException ignored) {
      return "Failed to print UI tree";
    }
  }
}
