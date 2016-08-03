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

package com.android.tools.idea.npw.importing;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.parser.GradleSettingsFile;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.google.common.base.Joiner;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

public class ArchiveToGradleModuleModelTest extends AndroidGradleImportTestCase {

  private void assertArchiveImportedCorrectly(@NotNull String newModuleGradlePath,
                                              @NotNull File archiveToImport) throws IOException {
    final Project project = getProject();

    final File defaultSubprojectLocation = GradleUtil.getModuleDefaultPath(project.getBaseDir(), newModuleGradlePath);
    final File importedArchive = new File(defaultSubprojectLocation, archiveToImport.getName());
    assertWithMessage(String.format("File %s does not exist", importedArchive)).that(importedArchive.exists()).isTrue();

    final File buildGradle = new File(defaultSubprojectLocation, SdkConstants.FN_BUILD_GRADLE);
    assertWithMessage(String.format("File %s does not exist", buildGradle)).that(buildGradle.exists()).isTrue();
    final VirtualFile vFile = VfsUtil.findFileByIoFile(buildGradle, true);
    assertThat(vFile).isNotNull();
    assertThat(CreateModuleFromArchiveAction.getBuildGradleText(archiveToImport)).isEqualTo(VfsUtilCore.loadText(vFile));

    final GradleSettingsFile settingsFile = GradleSettingsFile.get(project);
    assertThat(settingsFile).isNotNull();
    final Iterable<String> modules = settingsFile.getModules();
    assertWithMessage("{ " + Joiner.on(", ").join(modules) + " }").that(modules).contains(newModuleGradlePath);
  }

  private void assertSourceProjectCorrectlyModified(@NotNull String expectedBuildGradle, @NotNull String sourceModuleFilePath)
    throws IOException {
    final Project project = getProject();
    final VirtualFile gradleBuildFile =
      project.getBaseDir().findFileByRelativePath(sourceModuleFilePath + "/" + SdkConstants.FN_BUILD_GRADLE);
    assertThat(gradleBuildFile).isNotNull();
    final GradleBuildFile buildModel = new GradleBuildFile(gradleBuildFile, project);
    assertThat(expectedBuildGradle).isEqualTo(buildModel.getPsiFile().getText());
  }

  @NotNull
  private File createArchiveInProjectAndDoImport(@NotNull String gradleBuildSource, boolean nested, boolean move) {
    final File archiveToImport = createArchiveInModuleWithinCurrentProject(nested, gradleBuildSource);

    final ArchiveToGradleModuleModel model = new ArchiveToGradleModuleModel(getProject());
    model.archive().set(archiveToImport.getAbsolutePath());
    model.gradlePath().set(ARCHIVE_DEFAULT_GRADLE_PATH);
    model.moveArchive().set(move);
    model.handleFinished();

    return archiveToImport;
  }

  public void testImportStandaloneArchive() throws Exception {
    final File archiveToImport = getJarNotInProject();
    final ArchiveToGradleModuleModel model = new ArchiveToGradleModuleModel(getProject());
    model.archive().set(archiveToImport.getAbsolutePath());

    model.gradlePath().set(ARCHIVE_DEFAULT_GRADLE_PATH);
    model.handleFinished();

    assertArchiveImportedCorrectly(ARCHIVE_DEFAULT_GRADLE_PATH, archiveToImport);
    assertWithMessage("Source file still exists").that(archiveToImport.isFile()).isTrue();
  }

  public void testImportStandaloneArchiveWithCustomPath() throws Exception {
    final File archiveToImport = getJarNotInProject();
    final ArchiveToGradleModuleModel model = new ArchiveToGradleModuleModel(getProject());
    model.archive().set(archiveToImport.getAbsolutePath());

    final String gradlePath = ":amodulename";
    model.gradlePath().set(gradlePath);
    model.handleFinished();

    assertArchiveImportedCorrectly(gradlePath, archiveToImport);
    assertWithMessage("Source file still exists").that(archiveToImport.isFile()).isTrue();
  }

  public void testImportStandaloneArchiveWithNestedPath() throws Exception {
    final File archiveToImport = getJarNotInProject();
    final ArchiveToGradleModuleModel model = new ArchiveToGradleModuleModel(getProject());
    model.archive().set(archiveToImport.getAbsolutePath());

    final String gradlePath = ":category:module";
    model.gradlePath().set(gradlePath);
    model.handleFinished();

    assertArchiveImportedCorrectly(gradlePath, archiveToImport);
    assertWithMessage("Source file still exists").that(archiveToImport.isFile()).isTrue();
  }

  public void testMoveStandaloneArchive() throws Exception {
    final File archiveToImport = getJarNotInProject();
    final ArchiveToGradleModuleModel model = new ArchiveToGradleModuleModel(getProject());
    model.archive().set(archiveToImport.getAbsolutePath());

    model.gradlePath().set(ARCHIVE_DEFAULT_GRADLE_PATH);
    model.moveArchive().set(true);
    model.handleFinished();

    assertArchiveImportedCorrectly(ARCHIVE_DEFAULT_GRADLE_PATH, archiveToImport);
    assertWithMessage("Source file deleted").that(archiveToImport.isFile()).isFalse();
  }

  public void testImportArchiveFromModuleWithinProject() throws IOException {
    final String initialGradleBuildSource = String.format(BUILD_GRADLE_TEMPLATE, LIBS_DEPENDENCY);
    final File archiveToImport = createArchiveInProjectAndDoImport(initialGradleBuildSource, false, false);

    assertArchiveImportedCorrectly(ARCHIVE_DEFAULT_GRADLE_PATH, archiveToImport);
    assertWithMessage("Source file still exists").that(archiveToImport.isFile()).isTrue();
    assertSourceProjectCorrectlyModified(initialGradleBuildSource, SOURCE_MODULE_NAME);
  }

  public void testImportArchiveFromNestedModuleWithinProject() throws IOException {
    final String initialGradleBuildSource = String.format(BUILD_GRADLE_TEMPLATE, LIBS_DEPENDENCY);

    final File archiveToImport = createArchiveInProjectAndDoImport(initialGradleBuildSource, true, false);

    assertArchiveImportedCorrectly(ARCHIVE_DEFAULT_GRADLE_PATH, archiveToImport);
    assertWithMessage("Source file still exists").that(archiveToImport.isFile()).isTrue();
    assertSourceProjectCorrectlyModified(initialGradleBuildSource, PARENT_MODULE_NAME + "/" + SOURCE_MODULE_NAME);
  }


  public void testMoveArchiveFromModuleWithinProject() throws IOException {
    final String newModuleName = ARCHIVE_DEFAULT_GRADLE_PATH;
    final String initialGradleBuildSource = String.format(BUILD_GRADLE_TEMPLATE, LIBS_DEPENDENCY);
    final String modifiedGradleBuildSource = String.format(BUILD_GRADLE_TEMPLATE,
                                                           LIBS_DEPENDENCY + "\n    compile project('" + newModuleName + "')");

    final File archiveToImport = createArchiveInProjectAndDoImport(initialGradleBuildSource, false, true);

    assertArchiveImportedCorrectly(newModuleName, archiveToImport);
    assertWithMessage("Source file deleted").that(archiveToImport.isFile()).isFalse();
    assertSourceProjectCorrectlyModified(modifiedGradleBuildSource, SOURCE_MODULE_NAME);
  }

  public void testMoveArchiveFromNestedModuleWithinProject() throws IOException {
    final String newModuleName = ARCHIVE_DEFAULT_GRADLE_PATH;
    final String initialGradleBuildSource = String.format(BUILD_GRADLE_TEMPLATE, LIBS_DEPENDENCY);
    final String modifiedGradleBuildSource = String.format(BUILD_GRADLE_TEMPLATE,
                                                           LIBS_DEPENDENCY + "\n    compile project('" + newModuleName + "')");

    final File archiveToImport = createArchiveInProjectAndDoImport(initialGradleBuildSource, true, true);

    assertArchiveImportedCorrectly(newModuleName, archiveToImport);
    assertWithMessage("Source file deleted").that(archiveToImport.isFile()).isFalse();
    assertSourceProjectCorrectlyModified(modifiedGradleBuildSource, PARENT_MODULE_NAME + "/" + SOURCE_MODULE_NAME);
  }

  public void testMoveArchiveFromModuleWithFileDependencyWithinProject() throws IOException {
    final String initialGradleBuildSource = String.format(BUILD_GRADLE_TEMPLATE,
                                                          LIBS_DEPENDENCY + "\n    compile files('lib/" + ARCHIVE_JAR_NAME + "')");
    final String modifiedGradleBuildSource = String.format(BUILD_GRADLE_TEMPLATE,
                                                           LIBS_DEPENDENCY +
                                                           "\n    compile project('" +
                                                           ARCHIVE_DEFAULT_GRADLE_PATH +
                                                           "')");

    final File archiveToImport = createArchiveInProjectAndDoImport(initialGradleBuildSource, false, true);

    assertArchiveImportedCorrectly(ARCHIVE_DEFAULT_GRADLE_PATH, archiveToImport);
    assertWithMessage("Source file deleted").that(archiveToImport.isFile()).isFalse();
    assertSourceProjectCorrectlyModified(modifiedGradleBuildSource, SOURCE_MODULE_NAME);
  }

  public void testMoveArchiveFromModuleWithMultipleFileDependenciesWithinProject() throws IOException {
    final String initialGradleBuildSource = String.format(BUILD_GRADLE_TEMPLATE,
                                                          LIBS_DEPENDENCY +
                                                          "\n    compile files('lib/" + ARCHIVE_JAR_NAME + "', 'some/other/file.jar')");
    final String modifiedGradleBuildSource = String.format(BUILD_GRADLE_TEMPLATE,
                                                           LIBS_DEPENDENCY +
                                                           "\n    compile files('some/other/file.jar')" +
                                                           "\n    compile project('" + ARCHIVE_DEFAULT_GRADLE_PATH + "')");

    final File archiveToImport = createArchiveInProjectAndDoImport(initialGradleBuildSource, false, true);

    assertArchiveImportedCorrectly(ARCHIVE_DEFAULT_GRADLE_PATH, archiveToImport);
    assertWithMessage("Source file deleted").that(archiveToImport.isFile()).isFalse();
    assertSourceProjectCorrectlyModified(modifiedGradleBuildSource, SOURCE_MODULE_NAME);
  }

  public void testPropertiesAreStripped() {
    final String testString = "some Test String";
    final ArchiveToGradleModuleModel model = new ArchiveToGradleModuleModel(getProject());

    model.archive().set(" " + testString + " ");
    assertThat(model.archive().get()).isEqualTo(testString);

    model.gradlePath().set(" " + testString + " ");
    assertThat(model.gradlePath().get()).isEqualTo(testString);
  }
}