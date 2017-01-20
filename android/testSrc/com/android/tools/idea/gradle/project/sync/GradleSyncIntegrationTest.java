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
package com.android.tools.idea.gradle.project.sync;

import com.android.builder.model.SyncIssue;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.ProjectLibraries;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.sync.idea.data.DataNodeCaches;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.android.compiler.ModuleSourceAutogenerating;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.tools.idea.gradle.plugin.AndroidPluginGeneration.ORIGINAL;
import static com.android.tools.idea.gradle.util.FilePaths.getJarFromJarUrl;
import static com.android.tools.idea.gradle.util.FilePaths.pathToIdeaUrl;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.android.tools.idea.testing.TestProjectPaths.*;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.roots.OrderRootType.SOURCES;
import static com.intellij.openapi.util.io.FileUtil.delete;
import static com.intellij.openapi.util.io.FileUtil.writeToFile;
import static com.intellij.openapi.vfs.StandardFileSystems.JAR_PROTOCOL_PREFIX;
import static com.intellij.pom.java.LanguageLevel.JDK_1_7;
import static org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED;
import static org.mockito.Mockito.*;

/**
 * Integration tests for 'Gradle Sync'.
 */
public class GradleSyncIntegrationTest extends AndroidGradleTestCase {
  private DataNodeCaches myOriginalDataNodeCaches;

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myOriginalDataNodeCaches != null) {
        IdeComponents.replaceService(getProject(), DataNodeCaches.class, myOriginalDataNodeCaches);
      }
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Project project = getProject();

    GradleProjectSettings projectSettings = new GradleProjectSettings();
    projectSettings.setDistributionType(DEFAULT_WRAPPED);
    GradleSettings.getInstance(project).setLinkedProjectsSettings(Collections.singletonList(projectSettings));
  }

  // See https://code.google.com/p/android/issues/detail?id=226802
  public void testNestedModule() throws Exception {
    // Sync must be successful.
    loadProject(NESTED_MODULE);

    Module rootModule = myModules.getModule("testNestedModule");
    GradleFacet gradleFacet = GradleFacet.getInstance(rootModule);
    // The root module should be considered a Java module.
    assertNotNull(gradleFacet);
    GradleModuleModel gradleModel = gradleFacet.getGradleModuleModel();
    assertNotNull(gradleModel);
    assertEquals(":", gradleModel.getGradlePath());
  }

  // Verifies that if syncing using cached model, and if the cached model is missing data, we fall back to a full Gradle sync.
  // See: https://code.google.com/p/android/issues/detail?id=160899
  public void testWithCacheMissingModules() throws Exception {
    Project project = getProject();
    myOriginalDataNodeCaches = DataNodeCaches.getInstance(project);

    loadSimpleApplication();

    // Simulate data node cache is missing modules.
    //noinspection unchecked
    DataNode<ProjectData> cache = mock(DataNode.class);
    DataNodeCaches dataNodeCaches = IdeComponents.replaceServiceWithMock(project, DataNodeCaches.class);
    when(dataNodeCaches.getCachedProjectData()).thenReturn(cache);
    when(dataNodeCaches.isCacheMissingModels(cache)).thenReturn(true);

    GradleSyncInvoker.Request request = new GradleSyncInvoker.Request();
    // @formatter:off
    request.setGenerateSourcesOnSuccess(false)
           .setUseCachedGradleModels(true);
    // @formatter:on

    // Sync again, and a full sync should occur, since the cache is missing modules.
    // 'waitForGradleProjectSyncToFinish' will never finish and test will time out and fail if the IDE never gets notified that the sync
    // finished.
    requestSyncAndWait(request);
  }

  // See https://code.google.com/p/android/issues/detail?id=224985
  // Disabled until the prebuilt SDK has CMake.
  public void /*test*/ExternalSystemSourceFolderSync() throws Exception {
    loadProject(HELLO_JNI);
    Module appModule = myModules.getAppModule();
  }

  // Disabled until the prebuilt Maven repo has all dependencies.
  public void testWithUserDefinedLibrarySources() throws Exception {
    if (SystemInfo.isWindows) {
      // Do not run tests on Windows (see http://b.android.com/222904)
      return;
    }

    loadSimpleApplication();

    ProjectLibraries libraries = new ProjectLibraries(getProject());
    String libraryNameRegex = "guava-.*";
    Library library = libraries.findMatchingLibrary(libraryNameRegex);
    assertNotNull(library);

    String url = "jar://$USER_HOME$/fake-dir/fake-sources.jar!/";

    // add an extra source path.
    Library.ModifiableModel libraryModel = library.getModifiableModel();
    libraryModel.addRoot(url, SOURCES);
    ApplicationManager.getApplication().runWriteAction(libraryModel::commit);

    requestSyncAndWait();

    library = libraries.findMatchingLibrary(libraryNameRegex);
    assertNotNull(library);

    String[] urls = library.getUrls(SOURCES);
    assertThat(urls).asList().contains(url);
  }

  public void testSyncShouldNotChangeDependenciesInBuildFiles() throws Exception {
    loadSimpleApplication();

    File appBuildFilePath = getBuildFilePath("app");
    long lastModified = appBuildFilePath.lastModified();

    requestSyncAndWait();

    // See https://code.google.com/p/android/issues/detail?id=78628
    assertEquals(lastModified, appBuildFilePath.lastModified());
  }

  // See https://code.google.com/p/android/issues/detail?id=76444
  public void testWithEmptyGradleSettingsFileInSingleModuleProject() throws Exception {
    loadProject(BASIC);
    createEmptyGradleSettingsFile();
    // Sync should be successful for single-module projects with an empty settings.gradle file.
    requestSyncAndWait();
  }

  private void createEmptyGradleSettingsFile() throws IOException {
    File settingsFilePath = new File(getProjectFolderPath(), FN_SETTINGS_GRADLE);
    assertTrue(delete(settingsFilePath));
    writeToFile(settingsFilePath, " ");
    assertAbout(file()).that(settingsFilePath).isFile();
    LocalFileSystem.getInstance().refresh(false /* synchronous */);
  }

  public void testModuleJavaLanguageLevel() throws Exception {
    loadProject(TRANSITIVE_DEPENDENCIES);
    Module library1Module = myModules.getModule("library1");
    LanguageLevel javaLanguageLevel = getJavaLanguageLevel(library1Module);
    assertEquals(JDK_1_7, javaLanguageLevel);
  }

  @Nullable
  private static LanguageLevel getJavaLanguageLevel(@NotNull Module module) {
    return LanguageLevelModuleExtensionImpl.getInstance(module).getLanguageLevel();
  }

  public void testSetupEventInvoked() throws Exception {
    // Verify GradleSyncState
    GradleSyncListener listener = mock(GradleSyncListener.class);
    Project project = getProject();
    GradleSyncState.subscribe(project, listener);
    loadSimpleApplication();

    verify(listener, times(1)).setupStarted(project);

    // Verify ProjectSetUpTask
    listener = mock(GradleSyncListener.class);
    GradleSyncInvoker.Request request = new GradleSyncInvoker.Request();
    GradleSyncInvoker.getInstance().requestProjectSync(project, request, listener);

    verify(listener, times(1)).setupStarted(project);
  }

  // https://code.google.com/p/android/issues/detail?id=227931
  public void testJarsFolderInExplodedAarIsExcluded() throws Exception {
    loadSimpleApplication();

    Module appModule = myModules.getAppModule();
    AndroidModuleModel androidModel = AndroidModuleModel.get(appModule);
    assertNotNull(androidModel);
    Collection<SyncIssue> issues = androidModel.getSyncIssues();
    assertThat(issues).isEmpty();

    AndroidPluginInfo pluginInfo = AndroidPluginInfo.find(getProject());
    assertNotNull(pluginInfo);
    assertEquals(pluginInfo.getPluginGeneration(), ORIGINAL);
    GradleVersion pluginVersion = pluginInfo.getPluginVersion();
    assertNotNull(pluginVersion);

    if (pluginVersion.compareIgnoringQualifiers("2.3.0") >= 0) {
      // Gradle plugin 2.3 stores exploded AARs in the user's cache. Excluding "jar" folder in the explode AAR is no longer needed, since
      // it is not inside the project.
      return;
    }

    ProjectLibraries libraries = new ProjectLibraries(getProject());
    Library appCompat = libraries.findMatchingLibrary("appcompat-v7.*");
    assertNotNull(appCompat);

    File jarsFolderPath = null;
    for (String url : appCompat.getUrls(CLASSES)) {
      if (url.startsWith(JAR_PROTOCOL_PREFIX)) {
        File jarPath = getJarFromJarUrl(url);
        assertNotNull(jarPath);
        jarsFolderPath = jarPath.getParentFile();
        break;
      }
    }
    assertNotNull(jarsFolderPath);

    ContentEntry[] contentEntries = ModuleRootManager.getInstance(appModule).getContentEntries();
    assertThat(contentEntries).hasLength(1);

    ContentEntry contentEntry = contentEntries[0];
    List<String> excludeFolderUrls = contentEntry.getExcludeFolderUrls();
    assertThat(excludeFolderUrls).contains(pathToIdeaUrl(jarsFolderPath));
  }

  public void testSourceAttachmentsForJavaLibraries() throws Exception {
    loadSimpleApplication();

    ProjectLibraries libraries = new ProjectLibraries(getProject());
    Library guava = libraries.findMatchingLibrary("guava-.*");
    assertNotNull(guava);

    String[] sources = guava.getUrls(SOURCES);
    assertThat(sources).isNotEmpty();
  }

  public void testLegacySourceGenerationIsDisabled() throws Exception {
    loadSimpleApplication();

    Module appModule = myModules.getAppModule();
    AndroidFacet facet = AndroidFacet.getInstance(appModule);
    assertNotNull(facet);

    ModuleSourceAutogenerating autogenerating = ModuleSourceAutogenerating.getInstance(facet);
    assertNull(autogenerating);
  }
}
