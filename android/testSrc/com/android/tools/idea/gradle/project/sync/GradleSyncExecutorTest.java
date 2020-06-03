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
package com.android.tools.idea.gradle.project.sync;

import static com.android.builder.model.SyncIssue.TYPE_MISSING_SDK_PACKAGE;
import static com.android.builder.model.SyncIssue.TYPE_SDK_NOT_SET;
import static com.android.tools.idea.gradle.project.sync.setup.module.android.AndroidModuleDependenciesSetupTest.getLibraryTableModeCount;
import static com.android.tools.idea.project.messages.MessageType.ERROR;
import static com.android.tools.idea.testing.TestProjectPaths.NEW_SYNC_KOTLIN_TEST;
import static com.android.tools.idea.testing.TestProjectPaths.TRANSITIVE_DEPENDENCIES;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.vfs.VfsUtil.loadText;
import static com.intellij.openapi.vfs.VfsUtil.saveText;

import com.android.builder.model.SyncIssue;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.project.sync.idea.GradleSyncExecutor;
import com.android.tools.idea.gradle.project.sync.issues.SyncIssues;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.project.messages.SyncMessage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.rules.ProjectModelRule;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class GradleSyncExecutorTest extends GradleSyncIntegrationTestCase {
  protected GradleSyncExecutor mySyncExecutor;


  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncExecutor = new GradleSyncExecutor(getProject());
  }

  @Override
  protected boolean useSingleVariantSyncInfrastructure() {
    return true;
  }

  @Override
  protected boolean useCompoundSyncInfrastructure() {
    return true;
  }

  public void testFetchGradleModelsWithSimpleApplication() throws Exception {
    loadSimpleApplication();

    List<GradleModuleModels> models = mySyncExecutor.fetchGradleModels();
    Map<String, GradleModuleModels> modulesByModuleName = indexByModuleName(models);

    GradleModuleModels app = modulesByModuleName.get("app");
    assertNotNull(app);
    assertContainsAndroidModels(app);
  }

  public void testFetchGradleModelsWithTransitiveDependencies() throws Exception {
    loadProject(TRANSITIVE_DEPENDENCIES);

    List<GradleModuleModels> models = mySyncExecutor.fetchGradleModels();
    Map<String, GradleModuleModels> modulesByModuleName = indexByModuleName(models);

    GradleModuleModels app = modulesByModuleName.get("app");
    assertNotNull(app);
    assertContainsAndroidModels(app);

    GradleModuleModels javalib1 = modulesByModuleName.get("javalib1");
    assertNotNull(javalib1);
    assertContainsJavaModels(javalib1);
  }

  public void testLibrariesAndProjectRootsAreNotRecreatedOnSync() throws Exception {
    ProjectModelRule.ignoreTestUnderWorkspaceModel();
    loadSimpleApplication();

    long libraryTableModCount = getLibraryTableModeCount(getProject());

    // Sync again
    requestSyncAndWait();

    assertEquals(libraryTableModCount, getLibraryTableModeCount(getProject()));
  }

  // Ignored until ag/129043402 is fixed. This causes a IllegalStateException in AndroidUnitTest.java within the AndroidGradlePlugin.
  public void /*test*/MissingSdkPackageGiveCorrectError() throws Exception {
    prepareProjectForImport(NEW_SYNC_KOTLIN_TEST);

    // Set an invalid SDK location
    LocalProperties localProperties = new LocalProperties(getProjectFolderPath());
    localProperties.setAndroidSdkPath(getProjectFolderPath());
    localProperties.save();

    String failure = requestSyncAndGetExpectedFailure(request -> request.skipPreSyncChecks = true);
    assertThat(failure).contains("Sync issues found!");

    Collection<SyncIssue> syncIssues = SyncIssues.forModule(getModule("app"));
    assertThat(syncIssues).hasSize(2);
    SyncIssue syncIssue = syncIssues.iterator().next();
    assertThat(syncIssue.getType()).isAnyOf(TYPE_SDK_NOT_SET, TYPE_MISSING_SDK_PACKAGE);
    syncIssue = syncIssues.iterator().next();
    assertThat(syncIssue.getType()).isAnyOf(TYPE_SDK_NOT_SET, TYPE_MISSING_SDK_PACKAGE);
  }

  public void testNoVariantsGiveCorrectError() throws Exception {
    prepareProjectForImport(NEW_SYNC_KOTLIN_TEST);

    GradleSyncMessagesStub messagesStub = new GradleSyncMessagesStub(getProject()) {
      // Ensure we actually count this as a sync failure.
      @Override
      public int getErrorCount() {
        return getFakeErrorCount();
      }
    };
    ServiceContainerUtil.replaceService(getProject(), GradleSyncMessages.class, messagesStub, getTestRootDisposable());


    // Add a variant filter that will remove every variant.
    VirtualFile buildFile = GradleUtil.getGradleBuildFile(new File(getProjectFolderPath(), "app"));
    String content = loadText(buildFile);
    final String newContent = content.replace("android {", "android { \nvariantFilter { variant -> setIgnore(true) }\n");
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        saveText(buildFile, newContent);
      }
      catch (IOException e) {
        fail();
      }
    });

    try {
      String failure = requestSyncAndGetExpectedFailure(request -> request.skipPreSyncChecks = true);
      // New sync path
      assertThat(failure).contains("Sync issues found!");
    }
    catch (AssertionError e) {
      // Old sync path
      // Since this error is thrown in set-up it does not get caught in Old Sync, this means that even though
      // requestSyncAndGetExpectedFailure is called we still get an exception thrown.
      assertThat(e.getMessage()).contains("Sync issues found!");
    }

    SyncMessage message = messagesStub.getFirstReportedMessage();
    assertThat(message).isNotNull();
    assertThat(message.getType()).isEqualTo(ERROR);
    assertThat(message.getText()[0]).isEqualTo("The module 'app' is an Android project without build variants, and cannot be built.");
    assertThat(message.getText()[1])
      .isEqualTo("Please fix the module's configuration in the build.gradle file and sync the project again.");
  }

  @NotNull
  private static Map<String, GradleModuleModels> indexByModuleName(List<? extends GradleModuleModels> models) {
    Map<String, GradleModuleModels> modelsByName = new HashMap<>();
    for (GradleModuleModels model : models) {
      String name = model.getModuleName();
      modelsByName.put(name, model);
    }
    return modelsByName;
  }

  private static void assertContainsAndroidModels(@NotNull GradleModuleModels models) {
    assertModelsPresent(models, AndroidModuleModel.class, GradleModuleModel.class);
  }

  private static void assertContainsJavaModels(@NotNull GradleModuleModels models) {
    assertModelsPresent(models, JavaModuleModel.class, GradleModuleModel.class);
  }

  private static void assertModelsPresent(@NotNull GradleModuleModels models, @NotNull Class<?>... expectedModelTypes) {
    for (Class<?> type : expectedModelTypes) {
      assertNotNull("Failed to find model of type " + type.getSimpleName(), models.findModel(type));
    }
  }
}
