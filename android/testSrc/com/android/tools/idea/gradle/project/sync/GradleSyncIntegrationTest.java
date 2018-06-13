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
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.ProjectLibraries;
import com.android.tools.idea.gradle.actions.SyncProjectAction;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.project.messages.MessageType;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.testing.IdeComponents;
import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.android.compiler.ModuleSourceAutogenerating;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.COMPILE;
import static com.android.tools.idea.gradle.plugin.AndroidPluginGeneration.ORIGINAL;
import static com.android.tools.idea.gradle.project.sync.messages.SyncMessageSubject.syncMessage;
import static com.android.tools.idea.gradle.util.ContentEntries.findParentContentEntry;
import static com.android.tools.idea.io.FilePaths.*;
import static com.android.tools.idea.testing.Facets.createAndAddGradleFacet;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.android.tools.idea.testing.TestProjectPaths.*;
import static com.android.tools.idea.util.PropertiesFiles.getProperties;
import static com.android.tools.idea.util.PropertiesFiles.savePropertiesToFile;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.roots.OrderRootType.SOURCES;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.vfs.StandardFileSystems.JAR_PROTOCOL_PREFIX;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;
import static com.intellij.openapi.vfs.VfsUtilCore.urlToPath;
import static com.intellij.pom.java.LanguageLevel.JDK_1_7;
import static org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED;
import static org.mockito.Mockito.*;

/**
 * Integration tests for 'Gradle Sync'.
 */
public class GradleSyncIntegrationTest extends GradleSyncIntegrationTestCase {
  private IdeComponents myIdeComponents;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Project project = getProject();

    myIdeComponents = new IdeComponents(project);

    GradleProjectSettings projectSettings = new GradleProjectSettings();
    projectSettings.setDistributionType(DEFAULT_WRAPPED);
    GradleSettings.getInstance(project).setLinkedProjectsSettings(Collections.singletonList(projectSettings));
  }

  @Override
  protected boolean useNewSyncInfrastructure() {
    return false;
  }

  // https://code.google.com/p/android/issues/detail?id=233038
  public void testLoadPlainJavaProject() throws Exception {
    prepareProjectForImport(PURE_JAVA_PROJECT);
    Project project = getProject();
    importProject(project.getName(), getBaseDirPath(project), null);

    Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      ContentEntry[] entries = ModuleRootManager.getInstance(module).getContentEntries();
      assertThat(entries).named(module.getName() + " should have content entries").isNotEmpty();
    }
  }

  // See https://code.google.com/p/android/issues/detail?id=226802
  public void testNestedModule() throws Exception {
    // Sync must be successful.
    loadProject(NESTED_MODULE);

    Module rootModule = myModules.getModule(getName());
    GradleFacet gradleFacet = GradleFacet.getInstance(rootModule);
    // The root module should be considered a Java module.
    assertNotNull(gradleFacet);
    GradleModuleModel gradleModel = gradleFacet.getGradleModuleModel();
    assertNotNull(gradleModel);
    assertEquals(":", gradleModel.getGradlePath());
  }

  // See https://code.google.com/p/android/issues/detail?id=224985
  public void testNdkProjectSync() throws Exception {
    loadProject(HELLO_JNI);

    Module appModule = myModules.getAppModule();
    NdkFacet ndkFacet = NdkFacet.getInstance(appModule);
    assertNotNull(ndkFacet);

    ModuleRootManager rootManager = ModuleRootManager.getInstance(appModule);
    VirtualFile[] roots = rootManager.getSourceRoots(false /* do not include tests */);

    boolean cppSourceFolderFound = false;
    for (VirtualFile root : roots) {
      if (root.getName().equals("cpp")) {
        cppSourceFolderFound = true;
        break;
      }
    }

    assertTrue(cppSourceFolderFound);
  }

  public void testWithUserDefinedLibrarySources() throws Exception {
    if (SystemInfo.isWindows) {
      // Do not run tests on Windows (see http://b.android.com/222904)
      return;
    }

    loadSimpleApplication();

    ProjectLibraries libraries = new ProjectLibraries(getProject());
    String libraryNameRegex = "Gradle: com.google.guava:.*";
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
    reset(listener);

    // Verify ProjectSetUpTask
    listener = mock(GradleSyncListener.class);
    GradleSyncInvoker.Request request = GradleSyncInvoker.Request.projectModified();
    GradleSyncInvoker.getInstance().requestProjectSync(project, request, listener);

    verify(listener, times(1)).setupStarted(project);
    reset(listener);
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
    assertEquals(ORIGINAL, pluginInfo.getPluginGeneration());
    GradleVersion pluginVersion = pluginInfo.getPluginVersion();
    assertNotNull(pluginVersion);

    if (pluginVersion.compareIgnoringQualifiers("2.3.0") >= 0) {
      // Gradle plugin 2.3 stores exploded AARs in the user's cache. Excluding "jar" folder in the explode AAR is no longer needed, since
      // it is not inside the project.
      return;
    }

    ProjectLibraries libraries = new ProjectLibraries(getProject());
    Library appCompat = libraries.findMatchingLibrary("Gradle: appcompat-v7.*");
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

  public void ignore_testSourceAttachmentsForJavaLibraries() throws Exception {
    loadSimpleApplication();

    ProjectLibraries libraries = new ProjectLibraries(getProject());
    Library guava = libraries.findMatchingLibrary("Gradle: guava.*");
    assertNotNull(guava);

    String[] sources = guava.getUrls(SOURCES);
    assertThat(sources).isNotEmpty();
  }

  public void testLegacySourceGenerationIsDisabled() throws Exception {
    loadSimpleApplication();

    Module appModule = myModules.getAppModule();
    AndroidFacet facet = AndroidFacet.getInstance(appModule);
    assertNotNull(facet);

    try {
      ModuleSourceAutogenerating.getInstance(facet);
      fail("Shouldn't be able to construct a source generator for Gradle projects");
    } catch (IllegalArgumentException e) {
      assertEquals("app is built by an external build system and should not require the IDE to generate sources", e.getMessage());
    }
  }

  // Verifies that sync does not fail and user is warned when a project contains an Android module without variants.
  // See https://code.google.com/p/android/issues/detail?id=170722
  public void testWithAndroidProjectWithoutVariants() throws Exception {
    Project project = getProject();

    GradleSyncMessagesStub syncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(project);

    loadSimpleApplication();
    File appBuildFile = getBuildFilePath("app");

    // Remove all variants.
    appendToFile(appBuildFile, "android.variantFilter { variant -> variant.ignore = true }");

    requestSyncAndWait();

    // Verify user was warned.
    List<SyncMessage> messages = syncMessages.getReportedMessages();
    assertThat(messages).hasSize(1);

    SyncMessage message = messages.get(0);
    // @formatter:off
    assertAbout(syncMessage()).that(message).hasType(MessageType.ERROR)
                                            .hasMessageLine("The module 'app' is an Android project without build variants, and cannot be built.", 0);
    // @formatter:on

    // Verify AndroidFacet was removed.
    assertNull(AndroidFacet.getInstance(myModules.getAppModule()));
  }

  // See https://code.google.com/p/android/issues/detail?id=74259
  public void testWithCentralBuildDirectoryInRootModule() throws Exception {
    // In issue 74259, project sync fails because the "app" build directory is set to "CentralBuildDirectory/central/build", which is
    // outside the content root of the "app" module.
    File projectRootPath = prepareProjectForImport(CENTRAL_BUILD_DIRECTORY);

    // The bug appears only when the central build folder does not exist.
    File centralBuildDirPath = new File(projectRootPath, join("central", "build"));
    File centralBuildParentDirPath = centralBuildDirPath.getParentFile();
    delete(centralBuildParentDirPath);

    Project project = getProject();
    importProject(project.getName(), getBaseDirPath(project), null);
    Module app = myModules.getAppModule();

    // Now we have to make sure that if project import was successful, the build folder (with custom path) is excluded in the IDE (to
    // prevent unnecessary file indexing, which decreases performance.)
    File[] excludeFolderPaths = ApplicationManager.getApplication().runReadAction(
      (Computable<File[]>)() -> {
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(app);
        ModifiableRootModel rootModel = moduleRootManager.getModifiableModel();
        try {
          ContentEntry[] contentEntries = rootModel.getContentEntries();
          ContentEntry parent = findParentContentEntry(centralBuildDirPath, Arrays.stream(contentEntries));

          List<File> paths = Lists.newArrayList();

          for (ExcludeFolder excluded : parent.getExcludeFolders()) {
            String path = urlToPath(excluded.getUrl());
            if (isNotEmpty(path)) {
              paths.add(toSystemDependentPath(path));
            }
          }
          return paths.toArray(new File[paths.size()]);
        }
        finally {
          rootModel.dispose();
        }
      });

    assertThat(excludeFolderPaths).isNotEmpty();

    boolean isExcluded = false;
    for (File path : notNullize(excludeFolderPaths)) {
      if (isAncestor(centralBuildParentDirPath, path, true)) {
        isExcluded = true;
        break;
      }
    }

    assertTrue(String.format("Folder '%1$s' should be excluded", centralBuildDirPath.getPath()), isExcluded);
  }

  public void testGradleSyncActionAfterFailedSync() {
    IdeInfo ideInfo = myIdeComponents.mockApplicationService(IdeInfo.class);
    when(ideInfo.isAndroidStudio()).thenReturn(true);

    SyncProjectAction action = new SyncProjectAction();

    Presentation presentation = new Presentation();
    presentation.setEnabledAndVisible(false);
    AnActionEvent event = mock(AnActionEvent.class);
    when(event.getPresentation()).thenReturn(presentation);
    when(event.getProject()).thenReturn(getProject());

    assertFalse(GradleProjectInfo.getInstance(getProject()).isBuildWithGradle());
    action.update(event);
    assertFalse(presentation.isEnabledAndVisible());

    Module app = createModule("app");
    createAndAddGradleFacet(app);

    assertTrue(GradleProjectInfo.getInstance(getProject()).isBuildWithGradle());
    action.update(event);
    assertTrue(presentation.isEnabledAndVisible());
  }

  // Verify that sync issues were reported properly when there're unresolved dependencies
  // due to conflicts in variant attributes.
  // See b/64213214.
  public void testSyncIssueWithNonMatchingVariantAttributes() throws Exception {
    Project project = getProject();
    GradleSyncMessagesStub syncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(project);

    // DEPENDENT_MODULES project has two modules, app and lib, app module has dependency on lib module.
    loadProject(DEPENDENT_MODULES);

    // Define new buildType qa in app module.
    // This causes sync issues, because app depends on lib module, but lib module doesn't have buildType qa.
    File appBuildFile = getBuildFilePath("app");
    appendToFile(appBuildFile, "\nandroid.buildTypes { qa { } }\n");

    try {
      requestSyncAndWait();
    }
    catch (AssertionError expected) {
      // Sync issues are expected.
    }

    // Verify sync issues are reported properly.
    List<SyncMessage> messages = syncMessages.getReportedMessages();
    assertThat(messages).hasSize(4);
    SyncMessage message = messages.get(0);
    // @formatter:off
    // Verify text contains both of single line and multi-line message from SyncIssue.
    assertAbout(syncMessage()).that(message).hasType(MessageType.ERROR)
                                            .hasGroup("Unresolved dependencies")
                                            .hasMessageLine("Unable to resolve dependency for ':app@paidQa/compileClasspath': Could not resolve project :lib.", 0);
    // @formatter:on
  }

  public void testSyncWithAARDependencyAddsSources() throws Exception {
    Project project = getProject();

    loadProject(SIMPLE_APPLICATION);

    Module appModule = getModule("app");

    ApplicationManager.getApplication().invokeAndWait(() -> runWriteCommandAction(
        project, () -> {
          GradleBuildModel buildModel = GradleBuildModel.get(appModule);

          buildModel.repositories().addFlatDirRepository(getTestDataPath() + "/res/aar-lib-sources/");

          String newDependency = "com.foo.bar:bar:0.1@aar";
          buildModel.dependencies().addArtifact(COMPILE, newDependency);
          buildModel.applyChanges();
        }));

    requestSyncAndWait();

    // Verify that the library has sources.
    ProjectLibraries libraries = new ProjectLibraries(getProject());
    String libraryNameRegex = "Gradle: com.foo.bar:bar-0.1";
    Library library = libraries.findMatchingLibrary(libraryNameRegex);

    assertNotNull("Library com.foo.bar:bar-0.1 is missing", library);
    VirtualFile[] files = library.getFiles(SOURCES);
    assertThat(files).asList().hasSize(1);
  }

  // Verify that custom properties on local.properties are preserved after sync (b/70670394)
  public void testCustomLocalPropertiesPreservedAfterSync() throws Exception {
    Project project = getProject();

    loadProject(SIMPLE_APPLICATION);

    LocalProperties originalLocalProperties = new LocalProperties(project);
    Properties modified = getProperties(originalLocalProperties.getPropertiesFilePath());
    modified.setProperty("custom.property", "custom.value");
    savePropertiesToFile(modified, originalLocalProperties.getPropertiesFilePath(), null);
    LocalProperties modifiedLocalProperties = new LocalProperties(project);
    assertThat(modifiedLocalProperties.getProperty("custom.property")).isEqualTo("custom.value");

    requestSyncAndWait();

    LocalProperties afterSyncLocalProperties = new LocalProperties(project);
    assertThat(afterSyncLocalProperties.getProperty("custom.property")).isEqualTo("custom.value");
  }

  // Verify that previously reported sync issues are cleaned up as part of the next sync
  public void testSyncIssuesCleanup() throws Exception {
    loadSimpleApplication();

    Project project = getProject();
    GradleSyncMessagesStub syncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(project);
    SyncMessage oldSyncMessage = new SyncMessage(SyncMessage.DEFAULT_GROUP, MessageType.ERROR,
                                                 "A quick blown fix bumps over the lazy bug");
    syncMessages.report(oldSyncMessage);

    // Expect a successful sync, and that the old message should get cleaned up.
    requestSyncAndWait();
    List<SyncMessage> messages = syncMessages.getReportedMessages();
    assertThat(messages).isEmpty();
  }

  public void testSyncWithKotlinDsl() throws Exception {
    loadProject(KOTLIN_GRADLE_DSL);

    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    assertSize(3, modules);
    for (Module module : modules) {
      ContentEntry[] entries = ModuleRootManager.getInstance(module).getContentEntries();
      assertThat(entries).named(module.getName() + " should have content entries").isNotEmpty();
    }
  }
}
