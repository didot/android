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
package com.android.tools.idea.gradle.dsl.model.dependencies;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ModuleDependencyModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link DependenciesModelImpl} and {@link ModuleDependencyModelImpl}.
 */
public class ModuleDependencyTest extends GradleFileModelTestCase {
  public void testParsingWithCompactNotation() throws IOException {
    String text = "dependencies {\n" +
                  "    compile project(':javalib1')\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();
    assertThat(dependencies).hasSize(1);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "compile";
    expected.path = ":javalib1";
    assertMatches(expected, dependencies.get(0));
  }

  public void testParsingWithDependencyOnRoot() throws IOException {
    String text = "dependencies {\n" +
                  "    compile project(':')\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();
    assertThat(dependencies).hasSize(1);

    ModuleDependencyModel actual = dependencies.get(0);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "compile";
    expected.path = ":";
    assertMatches(expected, actual);

    assertEquals("", actual.name());
  }

  public void testParsingWithMapNotation() throws IOException {
    String text = "dependencies {\n" +
                  "    compile project(path: ':androidlib1', configuration: 'flavor1Release')\n" +
                  "    compile project(path: ':androidlib2', configuration: 'flavor2Release')\n" +
                  "    runtime project(path: ':javalib2')\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();
    assertThat(dependencies).hasSize(3);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "compile";
    expected.path = ":androidlib1";
    expected.configuration = "flavor1Release";
    assertMatches(expected, dependencies.get(0));

    expected.reset();

    expected.configurationName = "compile";
    expected.path = ":androidlib2";
    expected.configuration = "flavor2Release";
    assertMatches(expected, dependencies.get(1));

    expected.reset();

    expected.configurationName = "runtime";
    expected.path = ":javalib2";
    assertMatches(expected, dependencies.get(2));
  }

  public void testSetNameOnCompactNotation() throws IOException {
    String text = "dependencies {\n" +
                  "    compile project(':javalib1')\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();
    ModuleDependencyModel dependency = dependencies.get(0);
    dependency.setName("newName");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = buildModel.dependencies().modules();
    assertThat(dependencies).hasSize(1);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "compile";
    expected.path = ":newName";
    assertMatches(expected, dependency);
  }

  public void testSetNameOnMapNotationWithConfiguration() throws IOException {
    String text = "dependencies {\n" +
                  "    compile project(path: ':androidlib1', configuration: 'flavor1Release')\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();
    ModuleDependencyModel dependency = dependencies.get(0);
    dependency.setName("newName");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = buildModel.dependencies().modules();
    assertThat(dependencies).hasSize(1);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "compile";
    expected.path = ":newName";
    expected.configuration = "flavor1Release";
    assertMatches(expected, dependency);
  }

  public void testSetNameOnMapNotationWithoutConfiguration() throws IOException {
    String text = "dependencies {\n" +
                  "    compile project(path: ':androidlib1')\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();
    ModuleDependencyModel dependency = dependencies.get(0);
    dependency.setName("newName");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = buildModel.dependencies().modules();
    assertThat(dependencies).hasSize(1);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "compile";
    expected.path = ":newName";
    assertMatches(expected, dependency);
  }

  public void testSetNameWithPathHavingSameSegmentNames() throws IOException {
    String text = "dependencies {\n" +
                  "    compile project(path: ':name:name')\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();
    ModuleDependencyModel dependency = dependencies.get(0);
    dependency.setName("helloWorld");

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = buildModel.dependencies().modules();
    assertThat(dependencies).hasSize(1);

    ModuleDependencyModel actual = dependencies.get(0);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "compile";
    expected.path = ":name:helloWorld";
    assertMatches(expected, actual);

    assertEquals("helloWorld", actual.name());
  }

  public void testReset() throws IOException {
    String text = "dependencies {\n" +
                  "    compile project(':javalib1')\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();
    ModuleDependencyModel dependency = dependencies.get(0);
    dependency.setName("newName");

    assertTrue(buildModel.isModified());

    buildModel.resetState();

    assertFalse(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    dependencies = buildModel.dependencies().modules();
    assertThat(dependencies).hasSize(1);

    ExpectedModuleDependency expected = new ExpectedModuleDependency();
    expected.configurationName = "compile";
    expected.path = ":javalib1";
    assertMatches(expected, dependency);
  }

  // Test for b/68188327
  public void testMultiTypeApplicationStatementDoesNotThrowException() throws IOException {
    String text = "dependencies {\n" +
                  "    implementation group: 'my.test.dep', name: 'artifact', version: 'version', {\n" +
                  "        exclude module: 'module1'\n" +
                  "        exclude module: 'module2'\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();

    List<ModuleDependencyModel> dependencies = buildModel.dependencies().modules();

    // Note: this is not correct behaviour, this tests that no exception occurs.
    // TODO(b/69115152): fix the implementation of this
    assertThat(dependencies).hasSize(0);
  }

  private static void assertMatches(@NotNull ExpectedModuleDependency expected, @NotNull ModuleDependencyModel actual) {
    assertEquals("configurationName", expected.configurationName, actual.configurationName());
    assertEquals("path", expected.path, actual.path());
    assertEquals("configuration", expected.configuration, actual.configuration());
  }
}
