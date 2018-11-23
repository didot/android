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
package com.android.tools.idea.gradle.dsl.model.build;

import static com.android.tools.idea.gradle.dsl.TestFileName.BUILD_SCRIPT_MODEL_ADD_DEPENDENCY;
import static com.android.tools.idea.gradle.dsl.TestFileName.BUILD_SCRIPT_MODEL_EDIT_DEPENDENCY;
import static com.android.tools.idea.gradle.dsl.TestFileName.BUILD_SCRIPT_MODEL_EXT_PROPERTIES_FROM_BUILDSCRIPT_BLOCK;
import static com.android.tools.idea.gradle.dsl.TestFileName.BUILD_SCRIPT_MODEL_EXT_PROPERTIES_FROM_BUILDSCRIPT_BLOCK_SUB;
import static com.android.tools.idea.gradle.dsl.TestFileName.BUILD_SCRIPT_MODEL_PARSE_DEPENDENCIES;
import static com.android.tools.idea.gradle.dsl.TestFileName.BUILD_SCRIPT_MODEL_PARSE_REPOSITORIES;
import static com.android.tools.idea.gradle.dsl.TestFileName.BUILD_SCRIPT_MODEL_REMOVE_REPOSITORIES_MULTIPLE_BLOCKS;
import static com.android.tools.idea.gradle.dsl.TestFileName.BUILD_SCRIPT_MODEL_REMOVE_REPOSITORIES_SINGLE_BLOCK;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.STRING;
import static com.android.tools.idea.gradle.dsl.model.repositories.GoogleDefaultRepositoryModelImpl.GOOGLE_DEFAULT_REPO_NAME;
import static com.android.tools.idea.gradle.dsl.model.repositories.GoogleDefaultRepositoryModelImpl.GOOGLE_DEFAULT_REPO_URL;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.dsl.api.BuildScriptModel;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyTest.ExpectedArtifactDependency;
import com.android.tools.idea.gradle.dsl.model.repositories.GoogleDefaultRepositoryModelImpl;
import com.android.tools.idea.gradle.dsl.model.repositories.JCenterDefaultRepositoryModel;
import java.io.IOException;
import java.util.List;
import org.junit.Test;

/**
 * Tests for {@link BuildScriptModelImpl}.
 */
public class BuildScriptModelTest extends GradleFileModelTestCase {
  @Test
  public void testParseDependencies() throws IOException {
    writeToBuildFile(BUILD_SCRIPT_MODEL_PARSE_DEPENDENCIES);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.buildscript().dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency("classpath", "gradle", "com.android.tools.build", "2.0.0-alpha2");
    expected.assertMatches(dependencies.get(0));
  }

  @Test
  public void testAddDependency() throws IOException {
    writeToBuildFile(BUILD_SCRIPT_MODEL_ADD_DEPENDENCY);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildScriptModel buildScriptModel = buildModel.buildscript();
    DependenciesModel dependenciesModel = buildScriptModel.dependencies();

    assertFalse(hasPsiElement(buildScriptModel));
    assertFalse(hasPsiElement(dependenciesModel));
    assertThat(dependenciesModel.artifacts()).isEmpty();

    dependenciesModel.addArtifact("classpath", "com.android.tools.build:gradle:2.0.0-alpha2");

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency("classpath", "gradle", "com.android.tools.build", "2.0.0-alpha2");
    expected.assertMatches(dependencies.get(0));

    assertTrue(buildModel.isModified());
    applyChanges(buildModel);
    assertFalse(buildModel.isModified());

    buildModel.reparse();
    buildScriptModel = buildModel.buildscript();
    dependenciesModel = buildScriptModel.dependencies();

    assertTrue(hasPsiElement(buildScriptModel));
    assertTrue(hasPsiElement(dependenciesModel));
    dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);
    expected.assertMatches(dependencies.get(0));
  }

  @Test
  public void testEditDependency() throws IOException {
    writeToBuildFile(BUILD_SCRIPT_MODEL_EDIT_DEPENDENCY);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.buildscript().dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency("classpath", "gradle", "com.android.tools.build", "2.0.0-alpha2");
    ArtifactDependencyModel actual = dependencies.get(0);
    expected.assertMatches(actual);

    actual.version().setValue("2.0.1");

    expected = new ExpectedArtifactDependency("classpath", "gradle", "com.android.tools.build", "2.0.1");
    expected.assertMatches(actual);

    assertTrue(buildModel.isModified());
    applyChanges(buildModel);
    assertFalse(buildModel.isModified());

    buildModel.reparse();
    dependencies = buildModel.buildscript().dependencies().artifacts();
    assertThat(dependencies).hasSize(1);
    expected.assertMatches(dependencies.get(0));
  }

  @Test
  public void testParseRepositories() throws IOException {
    writeToBuildFile(BUILD_SCRIPT_MODEL_PARSE_REPOSITORIES);

    RepositoriesModel repositoriesModel = getGradleBuildModel().buildscript().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(2);
    RepositoryModel repositoryModel = repositories.get(0);
    assertTrue(repositoryModel instanceof JCenterDefaultRepositoryModel);
    JCenterDefaultRepositoryModel repository = (JCenterDefaultRepositoryModel)repositoryModel;
    assertEquals("name", "BintrayJCenter2", repository.name());
    assertEquals("url", "https://jcenter.bintray.com/", repository.url());

    repositoryModel = repositories.get(1);
    assertTrue(repositoryModel instanceof GoogleDefaultRepositoryModelImpl);
    GoogleDefaultRepositoryModelImpl googleRepository = (GoogleDefaultRepositoryModelImpl)repositoryModel;
    assertEquals("name", GOOGLE_DEFAULT_REPO_NAME, googleRepository.name());
    assertEquals("url", GOOGLE_DEFAULT_REPO_URL, googleRepository.url());
  }

  @Test
  public void testRemoveRepositoriesSingleBlock() throws IOException {
    writeToBuildFile(BUILD_SCRIPT_MODEL_REMOVE_REPOSITORIES_SINGLE_BLOCK);
    BuildScriptModel buildscript = getGradleBuildModel().buildscript();
    List<RepositoryModel> repositories = buildscript.repositories().repositories();
    assertThat(repositories).hasSize(2);
    buildscript.removeRepositoriesBlocks();
    repositories = buildscript.repositories().repositories();
    assertThat(repositories).hasSize(0);
  }

  @Test
  public void testRemoveRepositoriesMultipleBlocks() throws IOException {
    writeToBuildFile(BUILD_SCRIPT_MODEL_REMOVE_REPOSITORIES_MULTIPLE_BLOCKS);
    BuildScriptModel buildscript = getGradleBuildModel().buildscript();
    List<RepositoryModel> repositories = buildscript.repositories().repositories();
    assertThat(repositories).hasSize(2);
    buildscript.removeRepositoriesBlocks();
    repositories = buildscript.repositories().repositories();
    assertThat(repositories).hasSize(0);
  }

  @Test
  public void testExtPropertiesFromBuildscriptBlock() throws IOException {
    writeToBuildFile(BUILD_SCRIPT_MODEL_EXT_PROPERTIES_FROM_BUILDSCRIPT_BLOCK);
    writeToSubModuleBuildFile(BUILD_SCRIPT_MODEL_EXT_PROPERTIES_FROM_BUILDSCRIPT_BLOCK_SUB);
    writeToSettingsFile(getSubModuleSettingsText());

    ProjectBuildModel projectBuildModel = ProjectBuildModel.get(myProject);
    GradleBuildModel buildModel = projectBuildModel.getModuleBuildModel(mySubModule);

    verifyPropertyModel(buildModel.android().defaultConfig().applicationId(), STRING_TYPE, "boo", STRING, PropertyType.REGULAR, 1);
  }
}
