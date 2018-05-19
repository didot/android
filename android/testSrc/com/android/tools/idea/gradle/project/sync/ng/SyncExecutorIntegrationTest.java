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
package com.android.tools.idea.gradle.project.sync.ng;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.sync.common.CommandLineArgs;
import com.android.tools.idea.gradle.project.sync.errors.SyncErrorHandlerManager;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.BuildEnvironment;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.intellij.mock.MockProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.File;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.tools.idea.gradle.project.sync.Modules.createUniqueModuleId;
import static com.android.tools.idea.testing.AndroidGradleTests.replaceRegexGroup;
import static com.android.tools.idea.testing.TestProjectPaths.*;
import static com.android.utils.FileUtils.join;
import static com.android.utils.FileUtils.writeToFile;
import static com.google.common.io.Files.write;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.Collectors.toList;

/**
 * Tests for {@link SyncExecutorIntegration}.
 */
public class SyncExecutorIntegrationTest extends AndroidGradleTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    setUpIdeGradleSettings();
  }

  private void setUpIdeGradleSettings() {
    GradleProjectSettings settings = new GradleProjectSettings();
    settings.setDistributionType(DistributionType.DEFAULT_WRAPPED);
    settings.setExternalProjectPath(getProjectFolderPath().getPath());

    GradleSettings.getInstance(getProject()).setLinkedProjectsSettings(Collections.singleton(settings));
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.clearOverride();
    }
    finally {
      super.tearDown();
    }
  }

  public void testSyncProjectWithSingleVariantSync() throws Throwable {
    StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(true);

    prepareProjectForImport(SIMPLE_APPLICATION);

    Project project = getProject();

    // Simulate that "release" variant is selected in "app" module.
    SelectedVariantCollectorMock variantCollector = new SelectedVariantCollectorMock(project);
    variantCollector.setSelectedVariants("app", "release");

    SyncExecutor syncExecutor = new SyncExecutor(project, ExtraGradleSyncModelsManager.getInstance(),
                                                 new CommandLineArgs(true /* apply Java library plugin */),
                                                 new SyncErrorHandlerManager(project), variantCollector);

    SyncListener syncListener = new SyncListener();
    syncExecutor.syncProject(new MockProgressIndicator(), syncListener);
    syncListener.await();

    syncListener.propagateFailureIfAny();

    SyncProjectModels models = syncListener.getModels();
    Map<String, SyncModuleModels> modelsByModule = indexByModuleName(models.getModuleModels());
    assertThat(modelsByModule).hasSize(2);

    verifyRequestedVariants(modelsByModule.get("app"), singletonList("release"));
  }

  public void testSyncProjectWithSingleVariantSyncOnFirstTime() throws Throwable {
    StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(true);

    prepareProjectForImport(TRANSITIVE_DEPENDENCIES);

    Project project = getProject();

    SyncExecutor syncExecutor = new SyncExecutor(project);

    SyncListener syncListener = new SyncListener();
    syncExecutor.syncProject(new MockProgressIndicator(), syncListener);
    syncListener.await();

    syncListener.propagateFailureIfAny();

    SyncProjectModels models = syncListener.getModels();
    Map<String, SyncModuleModels> modelsByModule = indexByModuleName(models.getModuleModels());

    verifyRequestedVariants(modelsByModule.get("app"), singletonList("debug"));
    verifyRequestedVariants(modelsByModule.get("library1"), singletonList("debug"));
    verifyRequestedVariants(modelsByModule.get("library2"), singletonList("debug"));
  }

  public void testSyncProjectWithSingleVariantSyncWithRecursiveSelection() throws Throwable {
    StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(true);

    prepareProjectForImport(TRANSITIVE_DEPENDENCIES);

    Project project = getProject();
    // Simulate that "debug" variant is selected in "app" module.
    // "release" is selected in library1 and library2.
    SelectedVariantCollectorMock variantCollector = new SelectedVariantCollectorMock(project);
    variantCollector.setSelectedVariants("app", "debug");
    variantCollector.setSelectedVariants("library1", "release");
    variantCollector.setSelectedVariants("library2", "release");

    SyncExecutor syncExecutor = new SyncExecutor(project, ExtraGradleSyncModelsManager.getInstance(),
                                                 new CommandLineArgs(true /* apply Java library plugin */),
                                                 new SyncErrorHandlerManager(project), variantCollector);
    SyncListener syncListener = new SyncListener();
    syncExecutor.syncProject(new MockProgressIndicator(), syncListener);
    syncListener.await();

    syncListener.propagateFailureIfAny();

    SyncProjectModels models = syncListener.getModels();
    Map<String, SyncModuleModels> modelsByModule = indexByModuleName(models.getModuleModels());

    // app -> library2 -> library1
    // Verify that the variant of library1 and library2 are selected based on app.
    verifyRequestedVariants(modelsByModule.get("app"), singletonList("debug"));
    verifyRequestedVariants(modelsByModule.get("library1"), singletonList("debug"));
    verifyRequestedVariants(modelsByModule.get("library2"), singletonList("debug"));
  }

  public void testSyncProjectWithSingleVariantSyncWithSelectionConflict() throws Throwable {
    StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(true);
    prepareProjectForImport(TRANSITIVE_DEPENDENCIES);
    Project project = getProject();

    // Create a new module library3, so that library3 -> library2 -> library1.
    File projectFolderPath = getProjectFolderPath();
    File buildFile = new File(projectFolderPath, join("library3", FN_BUILD_GRADLE));
    String text = "apply plugin: 'com.android.library'\n" +
                  "android {\n" +
                  "    compileSdkVersion " + BuildEnvironment.getInstance().getCompileSdkVersion() + "\n" +
                  "}\n" +
                  "dependencies {\n" +
                  "    api project(':library2')\n" +
                  "}";
    writeToFile(buildFile, text);
    File settingsFile = new File(projectFolderPath, FN_SETTINGS_GRADLE);
    String contents = Files.toString(settingsFile, Charsets.UTF_8).trim();
    contents += ", ':library3'";
    writeToFile(settingsFile, contents);


    // Simulate that "debug" variant is selected in "app" module.
    // "release" is selected in "library3" module.
    // This will cause variant conflict because app -> library2, and library3 -> library2.
    SelectedVariantCollectorMock variantCollector = new SelectedVariantCollectorMock(project);
    variantCollector.setSelectedVariants("app", "debug");
    variantCollector.setSelectedVariants("library3", "release");

    SyncExecutor syncExecutor = new SyncExecutor(project, ExtraGradleSyncModelsManager.getInstance(),
                                                 new CommandLineArgs(true /* apply Java library plugin */),
                                                 new SyncErrorHandlerManager(project), variantCollector);
    SyncListener syncListener = new SyncListener();
    syncExecutor.syncProject(new MockProgressIndicator(), syncListener);
    syncListener.await();

    syncListener.propagateFailureIfAny();

    SyncProjectModels models = syncListener.getModels();
    Map<String, SyncModuleModels> modelsByModule = indexByModuleName(models.getModuleModels());

    // app -> library2 -> library1
    // library3 -> library2 -> library1
    // Verify that library1 and library2 have both of debug and release variants requested.
    verifyRequestedVariants(modelsByModule.get("app"), singletonList("debug"));
    verifyRequestedVariants(modelsByModule.get("library3"), singletonList("release"));
    verifyRequestedVariants(modelsByModule.get("library1"), asList("debug", "release"));
    verifyRequestedVariants(modelsByModule.get("library2"), asList("debug", "release"));
  }

  private static void verifyRequestedVariants(@NotNull SyncModuleModels moduleModels, @NotNull List<String> requestedVariants) {
    AndroidProject androidProject = moduleModels.findModel(AndroidProject.class);
    assertNotNull(androidProject);
    assertThat(androidProject.getVariants()).isEmpty();

    List<Variant> variants = moduleModels.findModels(Variant.class);
    assertNotNull(variants);
    assertThat(variants.stream().map(Variant::getName).collect(toList())).containsExactlyElementsIn(requestedVariants);
  }

  public void testSingleVariantSyncWithOldGradleVersion() throws Throwable {
    StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(true);

    // Use plugin 1.5.0 and Gradle 2.4.0
    prepareProjectForImport(PROJECT_WITH1_DOT5);
    File projectFolderPath = getProjectFolderPath();
    createGradleWrapper(projectFolderPath, "2.4");

    File topBuildFilePath = new File(projectFolderPath, "build.gradle");
    String contents = Files.toString(topBuildFilePath, Charsets.UTF_8);

    contents = replaceRegexGroup(contents, "classpath ['\"]com.android.tools.build:gradle:(.+)['\"]", "1.5.0");
    // Remove constraint-layout, which was not supported by old plugins.
    contents = replaceRegexGroup(contents, "(compile 'com.android.support.constraint:constraint-layout:\\+')", "");
    write(contents, topBuildFilePath, Charsets.UTF_8);

    Project project = getProject();

    // Simulate that "release" variant is selected in "app" module.
    SelectedVariantCollectorMock variantCollector = new SelectedVariantCollectorMock(project);
    variantCollector.setSelectedVariants("app", "release");

    SyncExecutor syncExecutor = new SyncExecutor(project, ExtraGradleSyncModelsManager.getInstance(),
                                                 new CommandLineArgs(true /* apply Java library plugin */),
                                                 new SyncErrorHandlerManager(project), variantCollector);

    SyncListener syncListener = new SyncListener();
    syncExecutor.syncProject(new MockProgressIndicator(), syncListener);
    syncListener.await();

    syncListener.propagateFailureIfAny();
    SyncProjectModels models = syncListener.getModels();
    Map<String, SyncModuleModels> modelsByModule = indexByModuleName(models.getModuleModels());
    assertThat(modelsByModule).hasSize(2);

    SyncModuleModels appModels = modelsByModule.get("app");
    AndroidProject androidProject = appModels.findModel(AndroidProject.class);
    assertNotNull(androidProject);
    Collection<Variant> variants = androidProject.getVariants();
    assertThat(variants).isNotEmpty();
    assertNull(appModels.findModel(Variant.class));
  }

  @NotNull
  private static Map<String, SyncModuleModels> indexByModuleName(@NotNull List<SyncModuleModels> allModuleModels) {
    Map<String, SyncModuleModels> modelsByModuleName = new HashMap<>();
    for (SyncModuleModels moduleModels : allModuleModels) {
      modelsByModuleName.put(moduleModels.getModuleName(), moduleModels);
    }
    return modelsByModuleName;
  }

  private static class SelectedVariantCollectorMock extends SelectedVariantCollector {
    @NotNull private final SelectedVariants mySelectedVariants = new SelectedVariants();
    @NotNull private final File myProjectFolderPath;

    SelectedVariantCollectorMock(@NotNull Project project) {
      super(project);
      myProjectFolderPath = new File(project.getBasePath());
    }

    @Override
    @NotNull
    SelectedVariants collectSelectedVariants() {
      return mySelectedVariants;
    }

    void setSelectedVariants(@NotNull String moduleName, @NotNull String selectedVariant) {
      String moduleId = createUniqueModuleId(myProjectFolderPath, ":" + moduleName);
      mySelectedVariants.addSelectedVariant(moduleId, selectedVariant);
    }
  }

  private static class SyncListener extends SyncExecutionCallback {
    @NotNull private final CountDownLatch myCountDownLatch = new CountDownLatch(1);
    private boolean myFailed;

    SyncListener() {
      doWhenDone(() -> myCountDownLatch.countDown());

      doWhenRejected(s -> {
        myFailed = true;
        myCountDownLatch.countDown();
      });
    }

    void await() throws InterruptedException {
      myCountDownLatch.await(5, MINUTES);
    }

    void propagateFailureIfAny() throws Throwable {
      if (myFailed) {
        Throwable error = getSyncError();
        if (error != null) {
          throw error;
        }
        throw new AssertionError("Sync failed - unknown cause");
      }
    }
  }
}