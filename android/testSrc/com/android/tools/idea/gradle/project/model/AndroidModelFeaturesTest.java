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
package com.android.tools.idea.gradle.project.model;

import com.android.ide.common.repository.GradleVersion;
import com.google.common.truth.Expect;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for {@link AndroidModelFeatures}.
 */
public class AndroidModelFeaturesTest {
  @Rule
  public Expect expect = Expect.createAndEnableStackTrace();

  @Test
  public void withoutPluginVersion() {
    AndroidModelFeatures features = new AndroidModelFeatures(null);
    assertTrue(features.isIssueReportingSupported());
    assertTrue(features.isShadersSupported());
    assertTrue(features.isTestedTargetVariantsSupported());
    assertTrue(features.isProductFlavorVersionSuffixSupported());
    assertTrue(features.isExternalBuildSupported());
    assertTrue(features.isConstraintLayoutSdkLocationSupported());
    assertFalse(features.shouldExportDependencies());
    assertFalse(features.isVfsRefreshAfterBuildRequired());
    assertFalse(features.isSingleVariantSyncSupported());
    assertFalse(features.isBuildOutputFileSupported());
  }

  @Test
  public void withPluginVersion3_1_0() {
    GradleVersion version = GradleVersion.parse("3.1.0");
    AndroidModelFeatures features = new AndroidModelFeatures(version);
    assertTrue(features.isIssueReportingSupported());
    assertTrue(features.isShadersSupported());
    assertTrue(features.isTestedTargetVariantsSupported());
    assertTrue(features.isProductFlavorVersionSuffixSupported());
    assertTrue(features.isExternalBuildSupported());
    assertTrue(features.isConstraintLayoutSdkLocationSupported());
    assertTrue(features.isPostBuildSyncSupported());
    assertFalse(features.shouldExportDependencies());
    assertFalse(features.isVfsRefreshAfterBuildRequired());
    assertFalse(features.isSingleVariantSyncSupported());
    assertFalse(features.isBuildOutputFileSupported());
  }

  @Test
  public void withPluginVersion3_2_0() {
    GradleVersion version = GradleVersion.parse("3.2.0");
    AndroidModelFeatures features = new AndroidModelFeatures(version);
    assertTrue(features.isIssueReportingSupported());
    assertTrue(features.isShadersSupported());
    assertTrue(features.isTestedTargetVariantsSupported());
    assertTrue(features.isProductFlavorVersionSuffixSupported());
    assertTrue(features.isExternalBuildSupported());
    assertTrue(features.isConstraintLayoutSdkLocationSupported());
    assertTrue(features.isPostBuildSyncSupported());
    assertFalse(features.shouldExportDependencies());
    assertFalse(features.isVfsRefreshAfterBuildRequired());
    assertFalse(features.isSingleVariantSyncSupported());
    assertFalse(features.isBuildOutputFileSupported());
  }

  @Test
  public void withPluginVersion3_3_0() {
    GradleVersion version = GradleVersion.parse("3.3.0");
    AndroidModelFeatures features = new AndroidModelFeatures(version);
    assertTrue(features.isIssueReportingSupported());
    assertTrue(features.isShadersSupported());
    assertTrue(features.isTestedTargetVariantsSupported());
    assertTrue(features.isProductFlavorVersionSuffixSupported());
    assertTrue(features.isExternalBuildSupported());
    assertTrue(features.isConstraintLayoutSdkLocationSupported());
    assertTrue(features.isPostBuildSyncSupported());
    assertFalse(features.shouldExportDependencies());
    assertFalse(features.isVfsRefreshAfterBuildRequired());
    assertTrue(features.isSingleVariantSyncSupported());
    assertFalse(features.isBuildOutputFileSupported());
  }

  @Test
  public void withPluginVersion4_0_0() {
    GradleVersion version = GradleVersion.parse("4.0.0");
    AndroidModelFeatures features = new AndroidModelFeatures(version);
    assertTrue(features.isIssueReportingSupported());
    assertTrue(features.isShadersSupported());
    assertTrue(features.isTestedTargetVariantsSupported());
    assertTrue(features.isProductFlavorVersionSuffixSupported());
    assertTrue(features.isExternalBuildSupported());
    assertTrue(features.isConstraintLayoutSdkLocationSupported());
    assertTrue(features.isPostBuildSyncSupported());
    assertFalse(features.shouldExportDependencies());
    assertFalse(features.isVfsRefreshAfterBuildRequired());
    assertTrue(features.isSingleVariantSyncSupported());
    assertFalse(features.isBuildOutputFileSupported());
  }

  @Test
  public void withPluginVersion4_1_0() {
    GradleVersion version = GradleVersion.parse("4.1.0");
    AndroidModelFeatures features = new AndroidModelFeatures(version);
    assertTrue(features.isIssueReportingSupported());
    assertTrue(features.isShadersSupported());
    assertTrue(features.isTestedTargetVariantsSupported());
    assertTrue(features.isProductFlavorVersionSuffixSupported());
    assertTrue(features.isExternalBuildSupported());
    assertTrue(features.isConstraintLayoutSdkLocationSupported());
    assertFalse(features.isPostBuildSyncSupported());
    assertFalse(features.shouldExportDependencies());
    assertFalse(features.isVfsRefreshAfterBuildRequired());
    assertTrue(features.isSingleVariantSyncSupported());
    assertTrue(features.isBuildOutputFileSupported());
  }

  private void assertFalse(boolean v) {
    expect.that(v).isFalse();
  }

  private void assertTrue(boolean v) {
    expect.that(v).isTrue();
  }
}
