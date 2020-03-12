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

import static com.android.tools.idea.testing.TestProjectPaths.DEPENDENT_MODULES;
import static com.android.tools.idea.testing.TestProjectPaths.HELLO_JNI;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.appendToFile;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.io.FileUtil.writeToFile;
import static java.util.stream.Collectors.toList;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.testing.AndroidGradleTests.SyncIssuesPresentError;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;

public class SingleVariantSyncIntegrationTest extends GradleSyncIntegrationTest {

  @Override
  protected boolean useSingleVariantSyncInfrastructure() {
    return true;
  }

  @Override
  public void testSyncIssueWithNonMatchingVariantAttributes() throws Exception {
    Project project = getProject();
    GradleSyncMessagesStub syncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(project);

    // DEPENDENT_MODULES project has two modules, app and lib, app module has dependency on lib module.
    loadProject(DEPENDENT_MODULES);

    // Define new buildType qa in app module.
    // This causes sync issues, because app depends on lib module, but lib module doesn't have buildType qa.
    File appBuildFile = getBuildFilePath("app");
    appendToFile(appBuildFile, "\nandroid.buildTypes { qa { } }\n");

    // Make paidQa the selected variant, because only the selected variant is requested in Single-Variant sync.
    Module appModule = getModule("app");
    AndroidFacet facet = AndroidFacet.getInstance(appModule);
    facet.getProperties().SELECTED_BUILD_VARIANT = "paidQa";

    try {
      requestSyncAndWait();
    } catch (SyncIssuesPresentError expected) {
      // Sync Issues are expected.
    }

    // Verify sync issues are reported properly.
    List<NotificationData> messages = syncMessages.getNotifications();
    List<NotificationData> relevantMessages = messages.stream()
      .filter(m -> m.getTitle().equals("Unresolved dependencies") &&
                   m.getMessage().contains(
                     "Unable to resolve dependency for ':app@paidQa/compileClasspath': Could not resolve project :lib.\nAffected Modules:"))
      .collect(toList());
    assertThat(relevantMessages).isNotEmpty();
  }

  public void testSingleVariantSyncAfterFailedIdeaSync() throws Exception {
    StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(false);
    GradleExperimentalSettings.getInstance().USE_SINGLE_VARIANT_SYNC = false;
    loadProject(HELLO_JNI);

    // Write empty CMakeLists file to force empty variants models from AGP.
    File cmakeFile = new File(getProjectFolderPath(), join("app", "src", "main", "cpp", "CMakeLists.txt"));
    writeToFile(cmakeFile, "");
    requestSyncAndWait();
    // Verify Ndk model only contains one dummy variant.
    NdkModuleModel ndkModuleModel = NdkModuleModel.get(getModule("app"));
    assertThat(ndkModuleModel.getVariants()).hasSize(1);
    assertThat(ndkModuleModel.getNdkVariantNames()).contains(NdkModuleModel.DummyNdkVariant.variantNameWithAbi);

    // Switch to single-variant sync, and verify sync is succeeded.
    StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(true);
    GradleExperimentalSettings.getInstance().USE_SINGLE_VARIANT_SYNC = true;
    requestSyncAndWait();
    ndkModuleModel = NdkModuleModel.get(getModule("app"));
    // Verify Single-variant sync is able to retrieve variant names with empty CMakeList.
    assertThat(ndkModuleModel.getNdkVariantNames().size()).isGreaterThan(1);
    assertThat(ndkModuleModel.getNdkVariantNames()).doesNotContain(NdkModuleModel.DummyNdkVariant.variantNameWithAbi);
  }
}
