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
package com.android.tools.idea.gradle.project;

import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.GradleSyncSummary;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.IdeaTestCase;

import java.io.File;

import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.intellij.openapi.util.io.FileUtilRt.createIfNotExists;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link GradleProjectInfo}.
 */
public class GradleProjectInfoTest extends IdeaTestCase {
  private GradleProjectInfo myProjectInfo;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myProjectInfo = GradleProjectInfo.getInstance(getProject());
  }

  public void testHasTopLevelGradleBuildFileUsingGradleProject() {
    File projectFolderPath = getBaseDirPath(getProject());
    File buildFilePath = new File(projectFolderPath, "build.gradle");
    assertTrue("Failed to create top-level build.gradle file", createIfNotExists(buildFilePath));

    assertTrue(myProjectInfo.hasTopLevelGradleBuildFile());
  }

  public void testHasTopLevelGradleBuildFileUsingNonGradleProject() {
    File projectFolderPath = getBaseDirPath(getProject());
    File buildFilePath = new File(projectFolderPath, "build.gradle");
    if (buildFilePath.exists()) {
      assertTrue("Failed to delete top-level build.gradle file", buildFilePath.delete());
    }

    assertFalse(myProjectInfo.hasTopLevelGradleBuildFile());
  }

  public void testIsBuildWithGradleUsingGradleProject() {
    // Simulate this is a module built with Gradle
    ApplicationManager.getApplication().runWriteAction(() -> {
      FacetManager.getInstance(getModule()).addFacet(AndroidGradleFacet.getFacetType(), AndroidGradleFacet.NAME, null);
    });

    assertTrue(myProjectInfo.isBuildWithGradle());
  }

  public void testIsBuildWithGradleUsingNonGradleProject() {
    // Ensure this module is *not* build by Gradle.
    removeAndroidGradleFacetFromModule();

    assertFalse(myProjectInfo.isBuildWithGradle());
  }

  // See https://code.google.com/p/android/issues/detail?id=203384
  public void testIsBuildWithGradleUsingGradleProjectWithoutGradleModules() {
    // Ensure this module is *not* build by Gradle.
    removeAndroidGradleFacetFromModule();

    registerLastSyncTimestamp(1L);

    assertTrue(myProjectInfo.isBuildWithGradle());
  }

  // See https://code.google.com/p/android/issues/detail?id=203384
  public void testIsBuildWithGradleUsingProjectWithoutSyncTimestamp() {
    // Ensure this module is *not* build by Gradle.
    removeAndroidGradleFacetFromModule();

    registerLastSyncTimestamp(-1L);

    assertFalse(myProjectInfo.isBuildWithGradle());
  }

  public void testGetAndroidModulesUsingGradleProject() {
    // Simulate this is a module built with Gradle
    ApplicationManager.getApplication().runWriteAction(() -> {
      FacetManager.getInstance(getModule()).addFacet(AndroidGradleFacet.getFacetType(), AndroidGradleFacet.NAME, null);
    });

    assertEquals(1, myProjectInfo.getAndroidModules().size());
  }

  public void testGetAndroidModulesUsingNonGradleProject() {
    // Ensure this module is *not* build by Gradle.
    removeAndroidGradleFacetFromModule();

    assertEmpty(myProjectInfo.getAndroidModules());
  }

  public void testGetAndroidModulesUsingGradleProjectWithoutGradleModules() {
    // Ensure this module is *not* build by Gradle.
    removeAndroidGradleFacetFromModule();

    registerLastSyncTimestamp(1L);

    assertEmpty(myProjectInfo.getAndroidModules());
  }

  private void removeAndroidGradleFacetFromModule() {
    FacetManager facetManager = FacetManager.getInstance(getModule());
    AndroidGradleFacet facet = facetManager.findFacet(AndroidGradleFacet.getFacetType().getId(), AndroidGradleFacet.NAME);
    if (facet != null) {
      ApplicationManager.getApplication().runWriteAction(() -> {
        ModifiableFacetModel facetModel = facetManager.createModifiableModel();
        facetModel.removeFacet(facet);
        facetModel.commit();
      });
    }
  }

  private void registerLastSyncTimestamp(long timestamp) {
    GradleSyncSummary summary = mock(GradleSyncSummary.class);
    when(summary.getSyncTimestamp()).thenReturn(timestamp);

    GradleSyncState syncState = IdeComponents.replaceServiceWithMock(getProject(), GradleSyncState.class);
    when(syncState.getSummary()).thenReturn(summary);
  }
}