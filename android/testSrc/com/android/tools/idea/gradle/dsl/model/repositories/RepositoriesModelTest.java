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
package com.android.tools.idea.gradle.dsl.model.repositories;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

import static com.android.tools.idea.gradle.dsl.model.repositories.GoogleDefaultRepositoryModelImpl.*;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link RepositoriesModelImpl}.
 */
public class RepositoriesModelTest extends GradleFileModelTestCase {


  @NotNull private static final String TEST_DIR = "hello/i/am/a/dir";
  @NotNull private static final String OTHER_TEST_DIR = "/this/is/also/a/dir";

  @Test
  public void testParseJCenterDefaultRepository() throws IOException {
    String text = "repositories {\n" +
                  "  jcenter()\n" +
                  "}";
    writeToBuildFile(text);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    verifyJCenterDefaultRepositoryModel(repositories.get(0));
  }

  @Test
  public void testParseJCenterCustomRepository() throws IOException {
    String text = "repositories {\n" +
                  "  jcenter {\n" +
                  "    url \"http://jcenter.bintray.com/\"\n" +
                  "  }\n" +
                  "}";
    writeToBuildFile(text);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    assertThat(repositoryModel).isInstanceOf(JCenterRepositoryModel.class);
    JCenterRepositoryModel repository = (JCenterRepositoryModel)repositoryModel;
    assertNull("name", repository.name().getPsiElement());
    assertEquals("name", "BintrayJCenter2", repository.name().toString());
    assertNotNull("url", repository.url().getPsiElement());
    assertEquals("url", "http://jcenter.bintray.com/", repository.url().toString());
  }

  @Test
  public void testParseMavenCentralRepository() throws IOException {
    String text = "repositories {\n" +
                  "  mavenCentral()\n" +
                  "}";
    writeToBuildFile(text);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    assertThat(repositoryModel).isInstanceOf(MavenCentralRepositoryModel.class);
    MavenCentralRepositoryModel repository = (MavenCentralRepositoryModel)repositoryModel;
    assertEquals("name", "MavenRepo", repository.name().toString());
    assertNull("url", repository.name().getPsiElement());
    assertEquals("url", "https://repo1.maven.org/maven2/", repository.url().toString());
    assertNull("url", repository.url().getPsiElement());
  }

  @Test
  public void testParseMavenCentralRepositoryWithMultipleArtifactUrls() throws IOException {
    String text = "repositories {\n" +
                  "  mavenCentral name: \"nonDefaultName\", " +
                  "artifactUrls: [\"http://www.mycompany.com/artifacts1\", \"http://www.mycompany.com/artifacts2\"]\n" +
                  "}";
    writeToBuildFile(text);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    assertThat(repositoryModel).isInstanceOf(MavenCentralRepositoryModel.class);
    MavenCentralRepositoryModel repository = (MavenCentralRepositoryModel)repositoryModel;
    assertNotNull("name", repository.name().getPsiElement());
    assertEquals("name", "nonDefaultName", repository.name().toString());
    assertNull("url", repository.url().getPsiElement());
    assertEquals("url", "https://repo1.maven.org/maven2/", repository.url().toString());
    assertEquals("artifactUrls",
                 ImmutableList.of("http://www.mycompany.com/artifacts1", "http://www.mycompany.com/artifacts2"),
                 repository.artifactUrls());
  }

  @Test
  public void testParseMavenCentralRepositoryWithSingleArtifactUrls() throws IOException {
    String text = "repositories {\n" +
                  "  mavenCentral artifactUrls: \"http://www.mycompany.com/artifacts\"\n" +
                  "}";
    writeToBuildFile(text);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    assertThat(repositoryModel).isInstanceOf(MavenCentralRepositoryModel.class);
    MavenCentralRepositoryModel repository = (MavenCentralRepositoryModel)repositoryModel;
    assertNull("name", repository.name().getPsiElement());
    assertEquals("name", "MavenRepo", repository.name().toString());
    assertNull("url", repository.url().getPsiElement());
    assertEquals("url", "https://repo1.maven.org/maven2/", repository.url().toString());
    assertEquals("artifactUrls", "http://www.mycompany.com/artifacts", repository.artifactUrls());
  }

  @Test
  public void testParseCustomMavenRepository() throws IOException {
    String text = "repositories {\n" +
                  "  maven {\n" +
                  "    name \"myRepoName\"\n" +
                  "    url \"http://repo.mycompany.com/maven2\"\n" +
                  "  }\n" +
                  "}";
    writeToBuildFile(text);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    assertThat(repositoryModel).isInstanceOf(MavenRepositoryModelImpl.class);
    MavenRepositoryModelImpl repository = (MavenRepositoryModelImpl)repositoryModel;
    assertNotNull("name", repository.name().getPsiElement());
    assertEquals("name", "myRepoName", repository.name().toString());
    assertNotNull("url", repository.url().getPsiElement());
    assertEquals("url", "http://repo.mycompany.com/maven2", repository.url().toString());
  }

  @Test
  public void testParseMavenRepositoryWithArtifactUrls() throws IOException {
    String text = "repositories {\n" +
                  "  maven {\n" +
                  "    // Look for POMs and artifacts, such as JARs, here\n" +
                  "    url \"http://repo2.mycompany.com/maven2\"\n" +
                  "    // Look for artifacts here if not found at the above location\n" +
                  "    artifactUrls \"http://repo.mycompany.com/jars\"\n" +
                  "    artifactUrls \"http://repo.mycompany.com/jars2\"\n" +
                  "  }\n" +
                  "}";
    writeToBuildFile(text);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    assertThat(repositoryModel).isInstanceOf(MavenRepositoryModelImpl.class);
    MavenRepositoryModelImpl repository = (MavenRepositoryModelImpl)repositoryModel;
    assertNull("name", repository.name().getPsiElement());
    assertEquals("name", "maven", repository.name().toString());
    assertNotNull("url", repository.url().getPsiElement());
    assertEquals("url", "http://repo2.mycompany.com/maven2", repository.url().toString());
    assertEquals("artifactUrls",
                 ImmutableList.of("http://repo.mycompany.com/jars", "http://repo.mycompany.com/jars2"),
                 repository.artifactUrls());
  }

  @Test
  public void testParseMavenRepositoryWithCredentials() throws IOException {
    String text = "repositories {\n" +
                  "  maven {\n" +
                  "    credentials {\n" +
                  "      username 'user'\n" +
                  "      password 'password123'\n" +
                  "    }\n" +
                  "    url \"http://repo.mycompany.com/maven2\"\n" +
                  "  }\n" +
                  "}";
    writeToBuildFile(text);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    assertThat(repositoryModel).isInstanceOf(MavenRepositoryModelImpl.class);
    MavenRepositoryModelImpl repository = (MavenRepositoryModelImpl)repositoryModel;
    assertNull("name", repository.name().getPsiElement());
    assertEquals("name", "maven", repository.name().toString());
    assertNotNull("url", repository.url().getPsiElement());
    assertEquals("url", "http://repo.mycompany.com/maven2", repository.url().toString());
    assertMissingProperty(repository.artifactUrls());

    MavenCredentialsModel credentials = repository.credentials();
    assertNotNull(credentials);
    assertEquals("username", "user", credentials.username());
    assertEquals("password", "password123", credentials.password());
  }

  @Test
  public void testParseFlatDirRepository() throws IOException {
    String text = "repositories {\n" +
                  "  flatDir {\n" +
                  "    dirs 'lib1', 'lib2'\n" +
                  "   }\n" +
                  "}";
    writeToBuildFile(text);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    assertThat(repositoryModel).isInstanceOf(FlatDirRepositoryModel.class);
    FlatDirRepositoryModel repository = (FlatDirRepositoryModel)repositoryModel;
    assertNull("name", repository.name().getPsiElement());
    assertEquals("name", "flatDir", repository.name().toString());
    assertEquals("dirs", ImmutableList.of("lib1", "lib2"), repository.dirs());
  }

  @Test
  public void testParseFlatDirRepositoryWithSingleDirArgument() throws IOException {
    String text = "repositories {\n" +
                  "   flatDir name: 'libs', dirs: \"libs\"\n" +
                  "}";
    writeToBuildFile(text);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    assertThat(repositoryModel).isInstanceOf(FlatDirRepositoryModel.class);
    FlatDirRepositoryModel repository = (FlatDirRepositoryModel)repositoryModel;
    assertNotNull("name", repository.name().getPsiElement());
    assertEquals("name", "libs", repository.name().toString());
    assertEquals("dirs", "libs", repository.dirs());
  }

  @Test
  public void testParseFlatDirRepositoryWithDirListArgument() throws IOException {
    String text = "repositories {\n" +
                  "   flatDir dirs: [\"libs1\", \"libs2\"]\n" +
                  "}";
    writeToBuildFile(text);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    assertThat(repositoryModel).isInstanceOf(FlatDirRepositoryModel.class);

    FlatDirRepositoryModel repository = (FlatDirRepositoryModel)repositoryModel;
    assertNull("name", repository.name().getPsiElement());
    assertEquals("name", "flatDir", repository.name().toString());
    assertEquals("dirs", ImmutableList.of("libs1", "libs2"), repository.dirs());
  }

  @Test
  public void testParseMultipleRepositories() throws IOException {
    String text = "repositories {\n" +
                  "  jcenter()\n" +
                  "  mavenCentral()\n" +
                  "}";
    writeToBuildFile(text);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(2);
    verifyJCenterDefaultRepositoryModel(repositories.get(0));

    RepositoryModel mavenCentral = repositories.get(1);
    assertThat(mavenCentral).isInstanceOf(MavenCentralRepositoryModel.class);
    MavenCentralRepositoryModel mavenCentralRepository = (MavenCentralRepositoryModel)mavenCentral;
    assertEquals("name", "MavenRepo", mavenCentralRepository.name());
    assertEquals("url", "https://repo1.maven.org/maven2/", mavenCentralRepository.url());
  }

  @Test
  public void testParseGoogleDefaultRepository() throws IOException {
    String text = "repositories {\n" +
                  "  google()\n" +
                  "}";
    writeToBuildFile(text);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    verifyGoogleDefaultRepositoryModel(repositories.get(0));
  }

  @Test
  public void testAddGoogleRepositoryByMethodCall() throws IOException {
    String text = "repositories {\n" +
                  "  jcenter()\n" +
                  "}";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    List<RepositoryModel> repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(1);
    verifyJCenterDefaultRepositoryModel(repositories.get(0));
    verifyAddGoogleRepositoryByMethodCall();
    repositories = buildModel.repositories().repositories();
    verifyJCenterDefaultRepositoryModel(repositories.get(0));
  }

  @Test
  public void testAddGoogleRepositoryByMethodCallEmpty() throws IOException {
    String text = "repositories {\n" +
                  "}";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    List<RepositoryModel> repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(0);
    verifyAddGoogleRepositoryByMethodCall();
  }

  @Test
  public void testAddGoogleRepositoryToEmptyBuildscript() throws IOException {
    String text = "buildscript {\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.buildscript().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(0);

    repositoriesModel.addRepositoryByMethodName(GOOGLE_METHOD_NAME);

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    assertFalse(buildModel.isModified());

    repositoriesModel = buildModel.buildscript().repositories();
    repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    verifyGoogleDefaultRepositoryModel(repositories.get(0));
  }

  @Test
  public void testAddGoogleRepositoryByMethodCallPresent() throws IOException {
    String text = "repositories {\n" +
                  "  google()\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    verifyGoogleDefaultRepositoryModel(repositories.get(0));

    repositoriesModel.addRepositoryByMethodName(GOOGLE_METHOD_NAME);
    assertFalse(buildModel.isModified());
  }

  @Test
  public void testAddGoogleRepositoryByUrl() throws IOException {
    String text = "repositories {\n" +
                  "  jcenter()\n" +
                  "}";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    List<RepositoryModel> repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(1);
    verifyJCenterDefaultRepositoryModel(repositories.get(0));
    verifyAddGoogleRepositoryByUrl();
    repositories = buildModel.repositories().repositories();
    verifyJCenterDefaultRepositoryModel(repositories.get(0));
  }

  @Test
  public void testAddGoogleRepositoryByUrlEmpty() throws IOException {
    String text = "repositories {\n" +
                  "}";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    List<RepositoryModel> repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(0);
    verifyAddGoogleRepositoryByUrl();
  }

  @Test
  public void testAddGoogleRepositoryByUrlPresent() throws IOException {
    String text = "repositories {\n" +
                  "  maven {\n" +
                  "    url \"" + GOOGLE_DEFAULT_REPO_URL + "\"\n" +
                  "    name \"" + GOOGLE_DEFAULT_REPO_NAME + "\"\n" +
                  "  }\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    verifyGoogleMavenRepositoryModel(repositories.get(0));

    repositoriesModel.addMavenRepositoryByUrl(GOOGLE_DEFAULT_REPO_URL, GOOGLE_DEFAULT_REPO_NAME);
    assertFalse(buildModel.isModified());
  }

  private static void verifyGoogleDefaultRepositoryModel(RepositoryModel google) {
    assertThat(google).isInstanceOf(GoogleDefaultRepositoryModelImpl.class);
    GoogleDefaultRepositoryModelImpl googleRepository = (GoogleDefaultRepositoryModelImpl)google;
    assertEquals("name", GOOGLE_DEFAULT_REPO_NAME, googleRepository.name());
    assertEquals("url", GOOGLE_DEFAULT_REPO_URL, googleRepository.url());
  }

  private static void verifyJCenterDefaultRepositoryModel(RepositoryModel jcenter) {
    assertThat(jcenter).isInstanceOf(JCenterDefaultRepositoryModel.class);
    JCenterDefaultRepositoryModel jCenterRepository = (JCenterDefaultRepositoryModel)jcenter;
    assertNull("name", jCenterRepository.name().getPsiElement());
    assertEquals("name", "BintrayJCenter2", jCenterRepository.name().toString());
    assertNull("url", jCenterRepository.url().getPsiElement());
    assertEquals("url", "https://jcenter.bintray.com/", jCenterRepository.url().toString());
  }

  private void verifyAddGoogleRepositoryByMethodCall() {
    GradleBuildModel buildModel = getGradleBuildModel();

    RepositoriesModel repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    int prevSize = repositories.size();

    repositoriesModel.addRepositoryByMethodName(GOOGLE_METHOD_NAME);
    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    assertFalse(buildModel.isModified());

    repositoriesModel = buildModel.repositories();
    repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(prevSize + 1);
    verifyGoogleDefaultRepositoryModel(repositories.get(prevSize));
  }

  private void verifyAddGoogleRepositoryByUrl() {
    GradleBuildModel buildModel = getGradleBuildModel();

    RepositoriesModel repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    int prevSize = repositories.size();

    repositoriesModel.addMavenRepositoryByUrl(GOOGLE_DEFAULT_REPO_URL, GOOGLE_DEFAULT_REPO_NAME);
    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    assertFalse(buildModel.isModified());

    repositoriesModel = buildModel.repositories();
    repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(prevSize + 1);
    verifyGoogleMavenRepositoryModel(repositories.get(prevSize));
  }

  private static void verifyGoogleMavenRepositoryModel(RepositoryModel repository) {
    assertThat(repository).isInstanceOf(MavenRepositoryModelImpl.class);
    MavenRepositoryModelImpl mavenRepository = (MavenRepositoryModelImpl)repository;
    assertNotNull("url", mavenRepository.url().getPsiElement());
    assertEquals("url", GOOGLE_DEFAULT_REPO_URL, mavenRepository.url().toString());
    assertNotNull("name", mavenRepository.name().getPsiElement());
    assertEquals("name", GOOGLE_DEFAULT_REPO_NAME, mavenRepository.name().toString());
  }

  @Test
  public void testAddFlatRepository() throws IOException {
    writeToBuildFile("repositories {\n\n}");

    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(0);

    repositoriesModel.addFlatDirRepository("/usr/local/repo");
    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    List<RepositoryModel> repos = buildModel.repositories().repositories();
    assertThat(repos).hasSize(1);
    assertThat(repos.get(0)).isInstanceOf(FlatDirRepositoryModel.class);

    FlatDirRepositoryModel flatModel = (FlatDirRepositoryModel)repos.get(0);
    assertThat(flatModel.dirs().toList()).hasSize(1);
    assertEquals("/usr/local/repo", flatModel.dirs().toList().get(0).toString());
  }

  @Test
  public void testAddFlatRepositoryFromEmpty() throws IOException {
    writeToBuildFile("");

    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(0);

    repositoriesModel.addFlatDirRepository("/usr/local/repo");
    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    List<RepositoryModel> repos = buildModel.repositories().repositories();
    assertThat(repos).hasSize(1);
    assertThat(repos.get(0)).isInstanceOf(FlatDirRepositoryModel.class);

    FlatDirRepositoryModel flatModel = (FlatDirRepositoryModel)repos.get(0);
    assertThat(flatModel.dirs().toList()).hasSize(1);
    assertEquals("/usr/local/repo", flatModel.dirs().toList().get(0).toString());
  }

  @Test
  public void testAddToExistingFlatRepository() throws IOException {
    String text = "repositories {\n" +
                  "  flatDir {\n" +
                  "    dirs '" + TEST_DIR + "'\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);

    repositoriesModel.addFlatDirRepository(OTHER_TEST_DIR);
    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    List<RepositoryModel> repos = buildModel.repositories().repositories();
    assertThat(repos).hasSize(1);
    assertThat(repos.get(0)).isInstanceOf(FlatDirRepositoryModel.class);

    FlatDirRepositoryModel flatDirRepositoryModel = (FlatDirRepositoryModel)repos.get(0);

    List<GradlePropertyModel> dirs = flatDirRepositoryModel.dirs().toList();
    dirs.sort(Comparator.comparing(e -> e.resolve().toString()));

    assertEquals(OTHER_TEST_DIR, dirs.get(0).toString());
    assertEquals(TEST_DIR, dirs.get(1).toString());
  }

  @Test
  public void testAddDuplicateToExistingFlatRepository() throws IOException {
    String text = "repositories {\n" +
                  "  flatDir.dirs '" + TEST_DIR + "'\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);

    repositoriesModel.addFlatDirRepository(TEST_DIR);
    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);

    List<RepositoryModel> repos = buildModel.repositories().repositories();
    assertThat(repos).hasSize(1);
    assertThat(repos.get(0)).isInstanceOf(FlatDirRepositoryModel.class);

    FlatDirRepositoryModel flatDirRepositoryModel = (FlatDirRepositoryModel)repos.get(0);
    assertThat(flatDirRepositoryModel.dirs().toList()).hasSize(2);
    assertEquals(TEST_DIR, flatDirRepositoryModel.dirs().toList().get(0).toString());
    assertEquals(TEST_DIR, flatDirRepositoryModel.dirs().toList().get(1).toString());
  }

  @Test
  public void testSetNameForMethodCall() throws IOException {
    String text = "repositories {\n" +
                  "  jcenter()\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    assertThat(repositories.get(0)).isInstanceOf(JCenterDefaultRepositoryModel.class);
    repositories.get(0).name().setValue("hello");

    applyChangesAndReparse(buildModel);

    repositoriesModel = buildModel.repositories();
    repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    assertThat(repositories.get(0)).isInstanceOf(JCenterRepositoryModel.class);
    assertThat(repositories.get(0).name().toString()).isEqualTo("hello");
  }

  @Test
  public void testSetUrlForMethodCall() throws IOException {
    String text = "";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).isEmpty();

    repositoriesModel.addRepositoryByMethodName("jcenter");
    repositories = repositoriesModel.repositories();
    assertSize(1, repositories);
    assertThat(repositories.get(0)).isInstanceOf(JCenterDefaultRepositoryModel.class);

    ((JCenterDefaultRepositoryModel)repositories.get(0)).url().setValue("good.url");

    applyChangesAndReparse(buildModel);

    repositoriesModel = buildModel.repositories();
    repositories = repositoriesModel.repositories();
    assertSize(1, repositories);
    assertThat(repositories.get(0)).isInstanceOf(JCenterRepositoryModel.class);
    assertThat(((JCenterRepositoryModel)repositories.get(0)).url().toString()).isEqualTo("good.url");
  }

  @Test
  public void testSetArtifactUrlsInMaven() throws IOException {
    String text = "repositories {\n" +
                  "  jcenter()\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);

    repositoriesModel.addMavenRepositoryByUrl("good.url", "Good Name");
    repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(2);
    assertThat(repositories.get(1)).isInstanceOf(MavenRepositoryModelImpl.class);

    ((MavenRepositoryModelImpl)repositories.get(1)).artifactUrls().addListValue().setValue("nice.url");
    ((MavenRepositoryModelImpl)repositories.get(1)).artifactUrls().addListValue().setValue("other.nice.url");

    applyChangesAndReparse(buildModel);

    repositoriesModel = buildModel.repositories();
    repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(2);
    assertThat(repositories.get(1)).isInstanceOf(MavenRepositoryModelImpl.class);
    MavenRepositoryModelImpl model = (MavenRepositoryModelImpl)repositories.get(1);
    assertThat(model.name().toString()).isEqualTo("Good Name");
    assertThat(model.url().toString()).isEqualTo("good.url");
    verifyListProperty(model.artifactUrls(), "repositories.maven.artifactUrls", ImmutableList.of("nice.url", "other.nice.url"));
  }
}
