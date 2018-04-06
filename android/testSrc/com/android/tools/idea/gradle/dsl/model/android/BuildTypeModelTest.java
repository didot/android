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
package com.android.tools.idea.gradle.dsl.model.android;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.BuildTypeModel;
import com.android.tools.idea.gradle.dsl.api.android.SigningConfigModel;
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.api.ext.SigningConfigPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.List;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.*;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.CUSTOM;
import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for {@link BuildTypeModelImpl}.
 */
public class BuildTypeModelTest extends GradleFileModelTestCase {
  @Test
  public void testBuildTypeBlockWithApplicationStatements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      applicationIdSuffix \"mySuffix\"\n" +
                  "      buildConfigField \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "      consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      debuggable true\n" +
                  "      embedMicroApp true\n" +
                  "      jniDebuggable true\n" +
                  "      manifestPlaceholders activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"\n" +
                  "      minifyEnabled true\n" +
                  "      multiDexEnabled true\n" +
                  "      proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      pseudoLocalesEnabled true\n" +
                  "      renderscriptDebuggable true\n" +
                  "      renderscriptOptimLevel 1\n" +
                  "      resValue \"mnop\", \"qrst\", \"uvwx\"\n" +
                  "      shrinkResources true\n" +
                  "      testCoverageEnabled true\n" +
                  "      useJack true\n" +
                  "      versionNameSuffix \"abc\"\n" +
                  "      zipAlignEnabled true\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    BuildTypeModel buildType = getXyzBuildType(getGradleBuildModel());
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("debuggable", Boolean.TRUE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.TRUE, buildType.jniDebuggable());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.TRUE, buildType.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.TRUE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(1), buildType.renderscriptOptimLevel());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.TRUE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "abc", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.TRUE, buildType.zipAlignEnabled());
  }

  @Test
  public void testBuildTypeBlockWithAssignmentStatements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      applicationIdSuffix = \"mySuffix\"\n" +
                  "      consumerProguardFiles = ['proguard-android.txt', 'proguard-rules.pro']\n" +
                  "      debuggable = true\n" +
                  "      embedMicroApp = true\n" +
                  "      jniDebuggable = true\n" +
                  "      manifestPlaceholders = [activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"]\n" +
                  "      minifyEnabled = true\n" +
                  "      multiDexEnabled = true\n" +
                  "      proguardFiles = ['proguard-android.txt', 'proguard-rules.pro']\n" +
                  "      pseudoLocalesEnabled = true\n" +
                  "      renderscriptDebuggable = true\n" +
                  "      renderscriptOptimLevel = 1\n" +
                  "      shrinkResources = true\n" +
                  "      testCoverageEnabled = true\n" +
                  "      useJack = true\n" +
                  "      versionNameSuffix = \"abc\"\n" +
                  "      zipAlignEnabled = true\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    BuildTypeModel buildType = getXyzBuildType(getGradleBuildModel());
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("debuggable", Boolean.TRUE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.TRUE, buildType.jniDebuggable());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.TRUE, buildType.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.TRUE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(1), buildType.renderscriptOptimLevel());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.TRUE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "abc", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.TRUE, buildType.zipAlignEnabled());
  }

  @Test
  public void testBuildTypeApplicationStatements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}\n" +
                  "android.buildTypes.xyz.applicationIdSuffix \"mySuffix\"\n" +
                  "android.buildTypes.xyz.buildConfigField \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "android.buildTypes.xyz.consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "android.buildTypes.xyz.debuggable true\n" +
                  "android.buildTypes.xyz.embedMicroApp true\n" +
                  "android.buildTypes.xyz.jniDebuggable true\n" +
                  "android.buildTypes.xyz.manifestPlaceholders activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"\n" +
                  "android.buildTypes.xyz.minifyEnabled true\n" +
                  "android.buildTypes.xyz.multiDexEnabled true\n" +
                  "android.buildTypes.xyz.proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "android.buildTypes.xyz.pseudoLocalesEnabled true\n" +
                  "android.buildTypes.xyz.renderscriptDebuggable true\n" +
                  "android.buildTypes.xyz.renderscriptOptimLevel 1\n" +
                  "android.buildTypes.xyz.resValue \"mnop\", \"qrst\", \"uvwx\"\n" +
                  "android.buildTypes.xyz.shrinkResources true\n" +
                  "android.buildTypes.xyz.testCoverageEnabled true\n" +
                  "android.buildTypes.xyz.useJack true\n" +
                  "android.buildTypes.xyz.versionNameSuffix \"abc\"\n" +
                  "android.buildTypes.xyz.zipAlignEnabled true";

    writeToBuildFile(text);

    BuildTypeModel buildType = getXyzBuildType(getGradleBuildModel());
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("debuggable", Boolean.TRUE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.TRUE, buildType.jniDebuggable());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.TRUE, buildType.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.TRUE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(1), buildType.renderscriptOptimLevel());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.TRUE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "abc", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.TRUE, buildType.zipAlignEnabled());
  }

  @Test
  public void testBuildTypeAssignmentStatements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}\n" +
                  "android.buildTypes.xyz.applicationIdSuffix = \"mySuffix\"\n" +
                  "android.buildTypes.xyz.consumerProguardFiles = ['proguard-android.txt', 'proguard-rules.pro']\n" +
                  "android.buildTypes.xyz.debuggable = true\n" +
                  "android.buildTypes.xyz.embedMicroApp = true\n" +
                  "android.buildTypes.xyz.jniDebuggable = true\n" +
                  "android.buildTypes.xyz.manifestPlaceholders = [activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"]\n" +
                  "android.buildTypes.xyz.minifyEnabled = true\n" +
                  "android.buildTypes.xyz.multiDexEnabled = true\n" +
                  "android.buildTypes.xyz.proguardFiles = ['proguard-android.txt', 'proguard-rules.pro']\n" +
                  "android.buildTypes.xyz.pseudoLocalesEnabled = true\n" +
                  "android.buildTypes.xyz.renderscriptDebuggable = true\n" +
                  "android.buildTypes.xyz.renderscriptOptimLevel = 1\n" +
                  "android.buildTypes.xyz.shrinkResources = true\n" +
                  "android.buildTypes.xyz.testCoverageEnabled = true\n" +
                  "android.buildTypes.xyz.useJack = true\n" +
                  "android.buildTypes.xyz.versionNameSuffix = \"abc\"\n" +
                  "android.buildTypes.xyz.zipAlignEnabled = true";

    writeToBuildFile(text);

    BuildTypeModel buildType = getXyzBuildType(getGradleBuildModel());
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("debuggable", Boolean.TRUE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.TRUE, buildType.jniDebuggable());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.TRUE, buildType.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.TRUE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(1), buildType.renderscriptOptimLevel());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.TRUE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "abc", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.TRUE, buildType.zipAlignEnabled());
  }

  @Test
  public void testBuildTypeBlockWithOverrideStatements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      applicationIdSuffix \"mySuffix\"\n" +
                  "      consumerProguardFiles = ['proguard-android.txt', 'proguard-rules.pro']\n" +
                  "      debuggable true\n" +
                  "      embedMicroApp = false\n" +
                  "      jniDebuggable true\n" +
                  "      manifestPlaceholders = [activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"]\n" +
                  "      minifyEnabled false\n" +
                  "      multiDexEnabled = true\n" +
                  "      proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      pseudoLocalesEnabled = false\n" +
                  "      renderscriptDebuggable true\n" +
                  "      renderscriptOptimLevel = 1\n" +
                  "      shrinkResources false\n" +
                  "      testCoverageEnabled = true\n" +
                  "      useJack false\n" +
                  "      versionNameSuffix = \"abc\"\n" +
                  "      zipAlignEnabled true\n" +
                  "    }\n" +
                  "  }\n" +
                  "}\n" +
                  "android.buildTypes.xyz {\n" +
                  "  applicationIdSuffix = \"mySuffix-1\"\n" +
                  "  consumerProguardFiles 'proguard-android-1.txt', 'proguard-rules-1.pro'\n" +
                  "  debuggable = false\n" +
                  "  embedMicroApp true\n" +
                  "  jniDebuggable = false\n" +
                  "  manifestPlaceholders activityLabel3:\"defaultName3\", activityLabel4:\"defaultName4\"\n" +
                  "  minifyEnabled = true\n" +
                  "  multiDexEnabled false\n" +
                  "  proguardFiles = ['proguard-android-1.txt', 'proguard-rules-1.pro']\n" +
                  "  pseudoLocalesEnabled true\n" +
                  "  renderscriptDebuggable = false\n" +
                  "  renderscriptOptimLevel 2\n" +
                  "  shrinkResources = true\n" +
                  "  testCoverageEnabled false\n" +
                  "  useJack true\n" +
                  "  versionNameSuffix = \"abc-1\"\n" +
                  "  zipAlignEnabled = false\n" +
                  "}\n" +
                  "android.buildTypes.xyz.applicationIdSuffix = \"mySuffix-3\"";

    writeToBuildFile(text);

    BuildTypeModel buildType = getXyzBuildType(getGradleBuildModel());
    assertEquals("applicationIdSuffix", "mySuffix-3", buildType.applicationIdSuffix());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules-1.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("debuggable", Boolean.FALSE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.FALSE, buildType.jniDebuggable());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel3", "defaultName3", "activityLabel4", "defaultName4"),
                 buildType.manifestPlaceholders());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.FALSE, buildType.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules-1.pro"), buildType.proguardFiles());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.FALSE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(2), buildType.renderscriptOptimLevel());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.FALSE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "abc-1", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.FALSE, buildType.zipAlignEnabled());
  }

  @Test
  public void testBuildTypeBlockWithAppendStatements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      buildConfigField \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "      proguardFiles 'pro-1.txt', 'pro-2.txt'\n" +
                  "      resValue \"mnop\", \"qrst\", \"uvwx\"\n" +
                  "    }\n" +
                  "  }\n" +
                  "}\n" +
                  "android.buildTypes.xyz {\n" +
                  "  buildConfigField \"cdef\", \"ghij\", \"klmn\"\n" +
                  "  manifestPlaceholders activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"\n" +
                  "  proguardFile 'pro-3.txt'\n" +
                  "  resValue \"opqr\", \"stuv\", \"wxyz\"\n" +
                  "}\n" +
                  "android.buildTypes.xyz.manifestPlaceholders.activityLabel3 \"defaultName3\"\n" +
                  "android.buildTypes.xyz.manifestPlaceholders.activityLabel4 = \"defaultName4\"\n" +
                  "android.buildTypes.xyz.proguardFiles 'pro-4.txt', 'pro-5.txt'\n";

    writeToBuildFile(text);

    BuildTypeModel buildType = getXyzBuildType(getGradleBuildModel());
    verifyFlavorType("buildConfigFields",
                 ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl"), Lists.newArrayList("cdef", "ghij", "klmn")),
                 buildType.buildConfigFields());
    assertEquals("manifestPlaceholders",
                 ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2", "activityLabel3", "defaultName3",
                                 "activityLabel4", "defaultName4"),
                 buildType.manifestPlaceholders());
    assertEquals("proguardFiles",
                 ImmutableList.of("pro-1.txt", "pro-2.txt", "pro-3.txt", "pro-4.txt", "pro-5.txt"),
                 buildType.proguardFiles());
    verifyFlavorType("resValues",
                 ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx"), Lists.newArrayList("opqr", "stuv", "wxyz")),
                 buildType.resValues());
  }

  @Test
  public void testBuildTypeMapStatements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}\n" +
                  "android.buildTypes.xyz.manifestPlaceholders.activityLabel1 \"defaultName1\"\n" +
                  "android.buildTypes.xyz.manifestPlaceholders.activityLabel2 = \"defaultName2\"\n";

    writeToBuildFile(text);
    BuildTypeModel buildType = getXyzBuildType(getGradleBuildModel());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());
  }

  @Test
  public void testRemoveAndResetElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      applicationIdSuffix \"mySuffix\"\n" +
                  "      buildConfigField \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "      consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      debuggable true\n" +
                  "      embedMicroApp true\n" +
                  "      jniDebuggable true\n" +
                  "      manifestPlaceholders activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"\n" +
                  "      minifyEnabled true\n" +
                  "      multiDexEnabled true\n" +
                  "      proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      pseudoLocalesEnabled true\n" +
                  "      renderscriptDebuggable true\n" +
                  "      renderscriptOptimLevel 1\n" +
                  "      resValue \"mnop\", \"qrst\", \"uvwx\"\n" +
                  "      shrinkResources true\n" +
                  "      testCoverageEnabled true\n" +
                  "      useJack true\n" +
                  "      versionNameSuffix \"abc\"\n" +
                  "      zipAlignEnabled true\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("debuggable", Boolean.TRUE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.TRUE, buildType.jniDebuggable());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.TRUE, buildType.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.TRUE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(1), buildType.renderscriptOptimLevel());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.TRUE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "abc", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.TRUE, buildType.zipAlignEnabled());

    buildType.applicationIdSuffix().delete();
    buildType.removeAllBuildConfigFields();
    buildType.consumerProguardFiles().delete();
    buildType.debuggable().delete();
    buildType.embedMicroApp().delete();
    buildType.jniDebuggable().delete();
    buildType.manifestPlaceholders().delete();
    buildType.minifyEnabled().delete();
    buildType.multiDexEnabled().delete();
    buildType.proguardFiles().delete();
    buildType.pseudoLocalesEnabled().delete();
    buildType.renderscriptDebuggable().delete();
    buildType.renderscriptOptimLevel().delete();
    buildType.removeAllResValues();
    buildType.shrinkResources().delete();
    buildType.testCoverageEnabled().delete();
    buildType.useJack().delete();
    buildType.versionNameSuffix().delete();
    buildType.zipAlignEnabled().delete();
    assertMissingProperty("applicationIdSuffix", buildType.applicationIdSuffix());
    assertEmpty("buildConfigFields", buildType.buildConfigFields());
    assertMissingProperty("consumerProguardFiles", buildType.consumerProguardFiles());
    assertMissingProperty("debuggable", buildType.debuggable());
    assertMissingProperty("embedMicroApp", buildType.embedMicroApp());
    assertMissingProperty("jniDebuggable", buildType.jniDebuggable());
    assertMissingProperty("manifestPlaceholders", buildType.manifestPlaceholders());
    assertMissingProperty("minifyEnabled", buildType.minifyEnabled());
    assertMissingProperty("multiDexEnabled", buildType.multiDexEnabled());
    assertMissingProperty("proguardFiles", buildType.proguardFiles());
    assertMissingProperty("pseudoLocalesEnabled", buildType.pseudoLocalesEnabled());
    assertMissingProperty("renderscriptDebuggable", buildType.renderscriptDebuggable());
    assertMissingProperty("renderscriptOptimLevel", buildType.renderscriptOptimLevel());
    assertEmpty("resValues", buildType.resValues());
    assertMissingProperty("shrinkResources", buildType.shrinkResources());
    assertMissingProperty("testCoverageEnabled", buildType.testCoverageEnabled());
    assertMissingProperty("useJack", buildType.useJack());
    assertMissingProperty("versionNameSuffix", buildType.versionNameSuffix());
    assertMissingProperty("zipAlignEnabled", buildType.zipAlignEnabled());

    buildModel.resetState();
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("debuggable", Boolean.TRUE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.TRUE, buildType.jniDebuggable());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.TRUE, buildType.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.TRUE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(1), buildType.renderscriptOptimLevel());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.TRUE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "abc", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.TRUE, buildType.zipAlignEnabled());
  }

  @Test
  public void testEditAndResetLiteralElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      applicationIdSuffix \"mySuffix\"\n" +
                  "      debuggable true\n" +
                  "      embedMicroApp false\n" +
                  "      jniDebuggable true\n" +
                  "      minifyEnabled false\n" +
                  "      multiDexEnabled true\n" +
                  "      pseudoLocalesEnabled false\n" +
                  "      renderscriptDebuggable true\n" +
                  "      renderscriptOptimLevel 1\n" +
                  "      shrinkResources false\n" +
                  "      testCoverageEnabled true\n" +
                  "      useJack false\n" +
                  "      versionNameSuffix \"abc\"\n" +
                  "      zipAlignEnabled true\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    assertEquals("debuggable", Boolean.TRUE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.FALSE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.TRUE, buildType.jniDebuggable());
    assertEquals("minifyEnabled", Boolean.FALSE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.TRUE, buildType.multiDexEnabled());
    assertEquals("pseudoLocalesEnabled", Boolean.FALSE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.TRUE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(1), buildType.renderscriptOptimLevel());
    assertEquals("shrinkResources", Boolean.FALSE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.TRUE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.FALSE, buildType.useJack());
    assertEquals("versionNameSuffix", "abc", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.TRUE, buildType.zipAlignEnabled());

    buildType.applicationIdSuffix().setValue("mySuffix-1");
    buildType.debuggable().setValue(false);
    buildType.embedMicroApp().setValue(true);
    buildType.jniDebuggable().setValue(false);
    buildType.minifyEnabled().setValue(true);
    buildType.multiDexEnabled().setValue(false);
    buildType.pseudoLocalesEnabled().setValue(true);
    buildType.renderscriptDebuggable().setValue(false);
    buildType.renderscriptOptimLevel().setValue(2);
    buildType.shrinkResources().setValue(true);
    buildType.testCoverageEnabled().setValue(false);
    buildType.useJack().setValue(true);
    buildType.versionNameSuffix().setValue("def");
    buildType.zipAlignEnabled().setValue(false);

    assertEquals("applicationIdSuffix", "mySuffix-1", buildType.applicationIdSuffix());
    assertEquals("debuggable", Boolean.FALSE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.FALSE, buildType.jniDebuggable());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.FALSE, buildType.multiDexEnabled());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.FALSE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(2), buildType.renderscriptOptimLevel());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.FALSE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "def", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.FALSE, buildType.zipAlignEnabled());

    buildModel.resetState();
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    assertEquals("debuggable", Boolean.TRUE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.FALSE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.TRUE, buildType.jniDebuggable());
    assertEquals("minifyEnabled", Boolean.FALSE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.TRUE, buildType.multiDexEnabled());
    assertEquals("pseudoLocalesEnabled", Boolean.FALSE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.TRUE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(1), buildType.renderscriptOptimLevel());
    assertEquals("shrinkResources", Boolean.FALSE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.TRUE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.FALSE, buildType.useJack());
    assertEquals("versionNameSuffix", "abc", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.TRUE, buildType.zipAlignEnabled());
  }

  @Test
  public void testAddAndResetLiteralElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertMissingProperty("applicationIdSuffix", buildType.applicationIdSuffix());
    assertEmpty("buildConfigFields", buildType.buildConfigFields());
    assertMissingProperty("consumerProguardFiles", buildType.consumerProguardFiles());
    assertMissingProperty("debuggable", buildType.debuggable());
    assertMissingProperty("embedMicroApp", buildType.embedMicroApp());
    assertMissingProperty("jniDebuggable", buildType.jniDebuggable());
    assertMissingProperty("manifestPlaceholders", buildType.manifestPlaceholders());
    assertMissingProperty("minifyEnabled", buildType.minifyEnabled());
    assertMissingProperty("multiDexEnabled", buildType.multiDexEnabled());
    assertMissingProperty("proguardFiles", buildType.proguardFiles());
    assertMissingProperty("pseudoLocalesEnabled", buildType.pseudoLocalesEnabled());
    assertMissingProperty("renderscriptDebuggable", buildType.renderscriptDebuggable());
    assertMissingProperty("renderscriptOptimLevel", buildType.renderscriptOptimLevel());
    assertEmpty("resValues", buildType.resValues());
    assertMissingProperty("shrinkResources", buildType.shrinkResources());
    assertMissingProperty("testCoverageEnabled", buildType.testCoverageEnabled());
    assertMissingProperty("useJack", buildType.useJack());
    assertMissingProperty("versionNameSuffix", buildType.versionNameSuffix());
    assertMissingProperty("zipAlignEnabled", buildType.zipAlignEnabled());

    buildType.applicationIdSuffix().setValue("mySuffix-1");
    buildType.debuggable().setValue(false);
    buildType.embedMicroApp().setValue(true);
    buildType.jniDebuggable().setValue(false);
    buildType.minifyEnabled().setValue(true);
    buildType.multiDexEnabled().setValue(false);
    buildType.pseudoLocalesEnabled().setValue(true);
    buildType.renderscriptDebuggable().setValue(false);
    buildType.renderscriptOptimLevel().setValue(2);
    buildType.shrinkResources().setValue(true);
    buildType.testCoverageEnabled().setValue(false);
    buildType.useJack().setValue(true);
    buildType.versionNameSuffix().setValue("def");
    buildType.zipAlignEnabled().setValue(false);

    assertEquals("applicationIdSuffix", "mySuffix-1", buildType.applicationIdSuffix());
    assertEquals("debuggable", Boolean.FALSE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.FALSE, buildType.jniDebuggable());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.FALSE, buildType.multiDexEnabled());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.FALSE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(2), buildType.renderscriptOptimLevel());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.FALSE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "def", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.FALSE, buildType.zipAlignEnabled());

    buildModel.resetState();
    assertMissingProperty("applicationIdSuffix", buildType.applicationIdSuffix());
    assertEmpty("buildConfigFields", buildType.buildConfigFields());
    assertMissingProperty("consumerProguardFiles", buildType.consumerProguardFiles());
    assertMissingProperty("debuggable", buildType.debuggable());
    assertMissingProperty("embedMicroApp", buildType.embedMicroApp());
    assertMissingProperty("jniDebuggable", buildType.jniDebuggable());
    assertMissingProperty("manifestPlaceholders", buildType.manifestPlaceholders());
    assertMissingProperty("minifyEnabled", buildType.minifyEnabled());
    assertMissingProperty("multiDexEnabled", buildType.multiDexEnabled());
    assertMissingProperty("proguardFiles", buildType.proguardFiles());
    assertMissingProperty("pseudoLocalesEnabled", buildType.pseudoLocalesEnabled());
    assertMissingProperty("renderscriptDebuggable", buildType.renderscriptDebuggable());
    assertMissingProperty("renderscriptOptimLevel", buildType.renderscriptOptimLevel());
    assertEmpty("resValues", buildType.resValues());
    assertMissingProperty("shrinkResources", buildType.shrinkResources());
    assertMissingProperty("testCoverageEnabled", buildType.testCoverageEnabled());
    assertMissingProperty("useJack", buildType.useJack());
    assertMissingProperty("versionNameSuffix", buildType.versionNameSuffix());
    assertMissingProperty("zipAlignEnabled", buildType.zipAlignEnabled());
  }

  @Test
  public void testReplaceAndResetListElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      buildConfigField \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "      consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      resValue \"mnop\", \"qrst\", \"uvwx\"\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());

    buildType.replaceBuildConfigField("abcd", "efgh", "ijkl","abcd", "mnop", "qrst");
    replaceListValue(buildType.consumerProguardFiles(), "proguard-android.txt", "proguard-android-1.txt");
    replaceListValue(buildType.proguardFiles(), "proguard-android.txt", "proguard-android-1.txt");
    buildType.replaceResValue("mnop", "qrst", "uvwx","mnop", "efgh", "ijkl");
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "mnop", "qrst")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "efgh", "ijkl")), buildType.resValues());

    buildModel.resetState();
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());
  }

  @Test
  public void testAddAndResetListElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEmpty("buildConfigFields", buildType.buildConfigFields());
    assertMissingProperty("consumerProguardFiles", buildType.consumerProguardFiles());
    assertMissingProperty("proguardFiles", buildType.proguardFiles());
    assertEmpty("resValues", buildType.resValues());

    buildType.addBuildConfigField("abcd", "efgh", "ijkl");
    buildType.consumerProguardFiles().addListValue().setValue("proguard-android.txt");
    buildType.proguardFiles().addListValue().setValue("proguard-android.txt");
    buildType.addResValue("mnop", "qrst", "uvwx");
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());

    buildModel.resetState();
    assertEmpty("buildConfigFields", buildType.buildConfigFields());
    assertMissingProperty("consumerProguardFiles", buildType.consumerProguardFiles());
    assertMissingProperty("proguardFiles", buildType.proguardFiles());
    assertEmpty("resValues", buildType.resValues());
  }

  @Test
  public void testAddToAndResetListElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      buildConfigField \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "      consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      resValue \"mnop\", \"qrst\", \"uvwx\"\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());

    buildType.addBuildConfigField("cdef", "ghij", "klmn");
    buildType.consumerProguardFiles().addListValue().setValue("proguard-android-1.txt");
    buildType.proguardFiles().addListValue().setValue("proguard-android-1.txt");
    buildType.addResValue("opqr", "stuv", "wxyz");
    verifyFlavorType("buildConfigFields",
                 ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl"), Lists.newArrayList("cdef", "ghij", "klmn")),
                 buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx"), Lists.newArrayList("opqr", "stuv", "wxyz")),
                 buildType.resValues());

    buildModel.resetState();
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());
  }

  @Test
  public void testRemoveFromAndResetListElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      buildConfigField \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "      buildConfigField \"cdef\", \"ghij\", \"klmn\"\n" +
                  "      consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      resValue \"mnop\", \"qrst\", \"uvwx\"\n" +
                  "      resValue \"opqr\", \"stuv\", \"wxyz\"\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    verifyFlavorType("buildConfigFields",
                 ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl"), Lists.newArrayList("cdef", "ghij", "klmn")),
                 buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx"), Lists.newArrayList("opqr", "stuv", "wxyz")),
                 buildType.resValues());

    buildType.removeBuildConfigField("abcd", "efgh", "ijkl");
    removeListValue(buildType.consumerProguardFiles(), "proguard-rules.pro");
    removeListValue(buildType.proguardFiles(), "proguard-rules.pro");
    buildType.removeResValue("opqr", "stuv", "wxyz");
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("cdef", "ghij", "klmn")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());

    buildModel.resetState();
    verifyFlavorType("buildConfigFields",
                 ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl"), Lists.newArrayList("cdef", "ghij", "klmn")),
                 buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx"), Lists.newArrayList("opqr", "stuv", "wxyz")),
                 buildType.resValues());
  }

  @Test
  public void testSetAndResetMapElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      manifestPlaceholders key1:\"value1\", key2:\"value2\"\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEquals("manifestPlaceholders", ImmutableMap.of("key1", "value1", "key2", "value2"), buildType.manifestPlaceholders());

    buildType.manifestPlaceholders().getMapValue("key1").setValue(12345);
    buildType.manifestPlaceholders().getMapValue("key3").setValue(true);
    assertEquals("manifestPlaceholders", ImmutableMap.of("key1", 12345, "key2", "value2", "key3", true),
                 buildType.manifestPlaceholders());

    buildModel.resetState();
    assertEquals("manifestPlaceholders", ImmutableMap.of("key1", "value1", "key2", "value2"), buildType.manifestPlaceholders());
  }

  @Test
  public void testAddAndResetMapElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertMissingProperty("manifestPlaceholders", buildType.manifestPlaceholders());

    buildType.manifestPlaceholders().getMapValue("activityLabel1").setValue("newName1");
    buildType.manifestPlaceholders().getMapValue("activityLabel2").setValue("newName2");
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "newName1", "activityLabel2", "newName2"),
                 buildType.manifestPlaceholders());

    buildModel.resetState();
    assertMissingProperty("manifestPlaceholders", buildType.manifestPlaceholders());
  }

  @Test
  public void testRemoveAndResetMapElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      manifestPlaceholders activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());

    buildType.manifestPlaceholders().getValue(MAP_TYPE).get("activityLabel1").delete();
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());

    buildModel.resetState();
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());
  }

  @Test
  public void testRemoveAndApplyElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      applicationIdSuffix \"mySuffix\"\n" +
                  "      buildConfigField \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "      consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      debuggable true\n" +
                  "      embedMicroApp true\n" +
                  "      jniDebuggable true\n" +
                  "      manifestPlaceholders activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"\n" +
                  "      minifyEnabled true\n" +
                  "      multiDexEnabled true\n" +
                  "      proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      pseudoLocalesEnabled true\n" +
                  "      renderscriptDebuggable true\n" +
                  "      renderscriptOptimLevel 1\n" +
                  "      resValue \"mnop\", \"qrst\", \"uvwx\"\n" +
                  "      shrinkResources true\n" +
                  "      testCoverageEnabled true\n" +
                  "      useJack true\n" +
                  "      versionNameSuffix \"abc\"\n" +
                  "      zipAlignEnabled true\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    assertThat(android, instanceOf(AndroidModelImpl.class));
    assertTrue(((AndroidModelImpl)android).hasValidPsiElement());

    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertThat(buildType, instanceOf(BuildTypeModelImpl.class));
    assertTrue(((BuildTypeModelImpl)buildType).hasValidPsiElement());
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.consumerProguardFiles());
    assertEquals("debuggable", Boolean.TRUE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.TRUE, buildType.jniDebuggable());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.TRUE, buildType.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.TRUE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(1), buildType.renderscriptOptimLevel());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.TRUE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "abc", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.TRUE, buildType.zipAlignEnabled());

    // Remove all the properties except the applicationIdSuffix.
    buildType.removeAllBuildConfigFields();
    buildType.consumerProguardFiles().delete();
    buildType.debuggable().delete();
    buildType.embedMicroApp().delete();
    buildType.jniDebuggable().delete();
    buildType.manifestPlaceholders().delete();
    buildType.minifyEnabled().delete();
    buildType.multiDexEnabled().delete();
    buildType.proguardFiles().delete();
    buildType.pseudoLocalesEnabled().delete();
    buildType.renderscriptDebuggable().delete();
    buildType.renderscriptOptimLevel().delete();
    buildType.removeAllResValues();
    buildType.shrinkResources().delete();
    buildType.testCoverageEnabled().delete();
    buildType.useJack().delete();
    buildType.versionNameSuffix().delete();
    buildType.zipAlignEnabled().delete();
    assertThat(android, instanceOf(AndroidModelImpl.class));
    assertTrue(((AndroidModelImpl)android).hasValidPsiElement());
    assertThat(buildType, instanceOf(BuildTypeModelImpl.class));
    assertTrue(((BuildTypeModelImpl)buildType).hasValidPsiElement());
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    assertEmpty("buildConfigFields", buildType.buildConfigFields());
    assertMissingProperty("consumerProguardFiles", buildType.consumerProguardFiles());
    assertMissingProperty("debuggable", buildType.debuggable());
    assertMissingProperty("embedMicroApp", buildType.embedMicroApp());
    assertMissingProperty("jniDebuggable", buildType.jniDebuggable());
    assertMissingProperty("manifestPlaceholders", buildType.manifestPlaceholders());
    assertMissingProperty("minifyEnabled", buildType.minifyEnabled());
    assertMissingProperty("multiDexEnabled", buildType.multiDexEnabled());
    assertMissingProperty("proguardFiles", buildType.proguardFiles());
    assertMissingProperty("pseudoLocalesEnabled", buildType.pseudoLocalesEnabled());
    assertMissingProperty("renderscriptDebuggable", buildType.renderscriptDebuggable());
    assertMissingProperty("renderscriptOptimLevel", buildType.renderscriptOptimLevel());
    assertEmpty("resValues", buildType.resValues());
    assertMissingProperty("shrinkResources", buildType.shrinkResources());
    assertMissingProperty("testCoverageEnabled", buildType.testCoverageEnabled());
    assertMissingProperty("useJack", buildType.useJack());
    assertMissingProperty("versionNameSuffix", buildType.versionNameSuffix());
    assertMissingProperty("zipAlignEnabled", buildType.zipAlignEnabled());

    applyChanges(buildModel);
    assertThat(android, instanceOf(AndroidModelImpl.class));
    assertTrue(((AndroidModelImpl)android).hasValidPsiElement());
    assertThat(buildType, instanceOf(BuildTypeModelImpl.class));
    assertTrue(((BuildTypeModelImpl)buildType).hasValidPsiElement());
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    assertEmpty("buildConfigFields", buildType.buildConfigFields());
    assertMissingProperty("consumerProguardFiles", buildType.consumerProguardFiles());
    assertMissingProperty("debuggable", buildType.debuggable());
    assertMissingProperty("embedMicroApp", buildType.embedMicroApp());
    assertMissingProperty("jniDebuggable", buildType.jniDebuggable());
    assertMissingProperty("manifestPlaceholders", buildType.manifestPlaceholders());
    assertMissingProperty("minifyEnabled", buildType.minifyEnabled());
    assertMissingProperty("multiDexEnabled", buildType.multiDexEnabled());
    assertMissingProperty("proguardFiles", buildType.proguardFiles());
    assertMissingProperty("pseudoLocalesEnabled", buildType.pseudoLocalesEnabled());
    assertMissingProperty("renderscriptDebuggable", buildType.renderscriptDebuggable());
    assertMissingProperty("renderscriptOptimLevel", buildType.renderscriptOptimLevel());
    assertEmpty("resValues", buildType.resValues());
    assertMissingProperty("shrinkResources", buildType.shrinkResources());
    assertMissingProperty("testCoverageEnabled", buildType.testCoverageEnabled());
    assertMissingProperty("useJack", buildType.useJack());
    assertMissingProperty("versionNameSuffix", buildType.versionNameSuffix());
    assertMissingProperty("zipAlignEnabled", buildType.zipAlignEnabled());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);
    assertThat(android, instanceOf(AndroidModelImpl.class));
    assertTrue(((AndroidModelImpl)android).hasValidPsiElement());
    buildType = getXyzBuildType(buildModel);
    assertThat(buildType, instanceOf(BuildTypeModelImpl.class));
    assertTrue(((BuildTypeModelImpl)buildType).hasValidPsiElement());
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    assertEmpty("buildConfigFields", buildType.buildConfigFields());
    assertMissingProperty("consumerProguardFiles", buildType.consumerProguardFiles());
    assertMissingProperty("debuggable", buildType.debuggable());
    assertMissingProperty("embedMicroApp", buildType.embedMicroApp());
    assertMissingProperty("jniDebuggable", buildType.jniDebuggable());
    assertMissingProperty("manifestPlaceholders", buildType.manifestPlaceholders());
    assertMissingProperty("minifyEnabled", buildType.minifyEnabled());
    assertMissingProperty("multiDexEnabled", buildType.multiDexEnabled());
    assertMissingProperty("proguardFiles", buildType.proguardFiles());
    assertMissingProperty("pseudoLocalesEnabled", buildType.pseudoLocalesEnabled());
    assertMissingProperty("renderscriptDebuggable", buildType.renderscriptDebuggable());
    assertMissingProperty("renderscriptOptimLevel", buildType.renderscriptOptimLevel());
    assertEmpty("resValues", buildType.resValues());
    assertMissingProperty("shrinkResources", buildType.shrinkResources());
    assertMissingProperty("testCoverageEnabled", buildType.testCoverageEnabled());
    assertMissingProperty("useJack", buildType.useJack());
    assertMissingProperty("versionNameSuffix", buildType.versionNameSuffix());
    assertMissingProperty("zipAlignEnabled", buildType.zipAlignEnabled());

    // Now remove the applicationIdSuffix also and see the whole android block is removed as it would be an empty block.

    buildType.applicationIdSuffix().delete();
    assertThat(android, instanceOf(AndroidModelImpl.class));
    assertTrue(((AndroidModelImpl)android).hasValidPsiElement());
    assertThat(buildType, instanceOf(BuildTypeModelImpl.class));
    assertTrue(((BuildTypeModelImpl)buildType).hasValidPsiElement());
    assertMissingProperty("applicationIdSuffix", buildType.applicationIdSuffix());

    applyChanges(buildModel);
    assertThat(android, instanceOf(AndroidModelImpl.class));
    assertFalse(((AndroidModelImpl)android).hasValidPsiElement());
    assertThat(buildType, instanceOf(BuildTypeModelImpl.class));
    assertFalse(((BuildTypeModelImpl)buildType).hasValidPsiElement());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);
    assertThat(android, instanceOf(AndroidModelImpl.class));
    assertFalse(((AndroidModelImpl)android).hasValidPsiElement());
    assertTrue(android.buildTypes().isEmpty());
  }

  @Test
  public void testEditAndApplyLiteralElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      applicationIdSuffix \"mySuffix\"\n" +
                  "      debuggable true\n" +
                  "      embedMicroApp false\n" +
                  "      jniDebuggable true\n" +
                  "      minifyEnabled false\n" +
                  "      multiDexEnabled true\n" +
                  "      pseudoLocalesEnabled false\n" +
                  "      renderscriptDebuggable true\n" +
                  "      renderscriptOptimLevel 1\n" +
                  "      shrinkResources false\n" +
                  "      testCoverageEnabled true\n" +
                  "      useJack false\n" +
                  "      versionNameSuffix \"abc\"\n" +
                  "      zipAlignEnabled true\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    assertEquals("debuggable", Boolean.TRUE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.FALSE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.TRUE, buildType.jniDebuggable());
    assertEquals("minifyEnabled", Boolean.FALSE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.TRUE, buildType.multiDexEnabled());
    assertEquals("pseudoLocalesEnabled", Boolean.FALSE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.TRUE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(1), buildType.renderscriptOptimLevel());
    assertEquals("shrinkResources", Boolean.FALSE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.TRUE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.FALSE, buildType.useJack());
    assertEquals("versionNameSuffix", "abc", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.TRUE, buildType.zipAlignEnabled());

    buildType.applicationIdSuffix().setValue("mySuffix-1");
    buildType.debuggable().setValue(false);
    buildType.embedMicroApp().setValue(true);
    buildType.jniDebuggable().setValue(false);
    buildType.minifyEnabled().setValue(true);
    buildType.multiDexEnabled().setValue(false);
    buildType.pseudoLocalesEnabled().setValue(true);
    buildType.renderscriptDebuggable().setValue(false);
    buildType.renderscriptOptimLevel().setValue(2);
    buildType.shrinkResources().setValue(true);
    buildType.testCoverageEnabled().setValue(false);
    buildType.useJack().setValue(true);
    buildType.versionNameSuffix().setValue("def");
    buildType.zipAlignEnabled().setValue(false);

    assertEquals("applicationIdSuffix", "mySuffix-1", buildType.applicationIdSuffix());
    assertEquals("debuggable", Boolean.FALSE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.FALSE, buildType.jniDebuggable());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.FALSE, buildType.multiDexEnabled());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.FALSE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(2), buildType.renderscriptOptimLevel());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.FALSE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "def", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.FALSE, buildType.zipAlignEnabled());

    applyChanges(buildModel);
    assertEquals("applicationIdSuffix", "mySuffix-1", buildType.applicationIdSuffix());
    assertEquals("debuggable", Boolean.FALSE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.FALSE, buildType.jniDebuggable());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.FALSE, buildType.multiDexEnabled());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.FALSE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(2), buildType.renderscriptOptimLevel());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.FALSE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "def", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.FALSE, buildType.zipAlignEnabled());

    buildModel.reparse();
    buildType = getXyzBuildType(buildModel);
    assertEquals("applicationIdSuffix", "mySuffix-1", buildType.applicationIdSuffix());
    assertEquals("debuggable", Boolean.FALSE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.FALSE, buildType.jniDebuggable());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.FALSE, buildType.multiDexEnabled());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.FALSE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(2), buildType.renderscriptOptimLevel());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.FALSE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "def", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.FALSE, buildType.zipAlignEnabled());
  }

  @Test
  public void testAddAndApplyLiteralElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertMissingProperty("applicationIdSuffix", buildType.applicationIdSuffix());
    assertEmpty("buildConfigFields", buildType.buildConfigFields());
    assertMissingProperty("consumerProguardFiles", buildType.consumerProguardFiles());
    assertMissingProperty("debuggable", buildType.debuggable());
    assertMissingProperty("embedMicroApp", buildType.embedMicroApp());
    assertMissingProperty("jniDebuggable", buildType.jniDebuggable());
    assertMissingProperty("manifestPlaceholders", buildType.manifestPlaceholders());
    assertMissingProperty("minifyEnabled", buildType.minifyEnabled());
    assertMissingProperty("multiDexEnabled", buildType.multiDexEnabled());
    assertMissingProperty("proguardFiles", buildType.proguardFiles());
    assertMissingProperty("pseudoLocalesEnabled", buildType.pseudoLocalesEnabled());
    assertMissingProperty("renderscriptDebuggable", buildType.renderscriptDebuggable());
    assertMissingProperty("renderscriptOptimLevel", buildType.renderscriptOptimLevel());
    assertEmpty("resValues", buildType.resValues());
    assertMissingProperty("shrinkResources", buildType.shrinkResources());
    assertMissingProperty("testCoverageEnabled", buildType.testCoverageEnabled());
    assertMissingProperty("useJack", buildType.useJack());
    assertMissingProperty("versionNameSuffix", buildType.versionNameSuffix());
    assertMissingProperty("zipAlignEnabled", buildType.zipAlignEnabled());

    buildType.applicationIdSuffix().setValue("mySuffix-1");
    buildType.debuggable().setValue(false);
    buildType.embedMicroApp().setValue(true);
    buildType.jniDebuggable().setValue(false);
    buildType.minifyEnabled().setValue(true);
    buildType.multiDexEnabled().setValue(false);
    buildType.pseudoLocalesEnabled().setValue(true);
    buildType.renderscriptDebuggable().setValue(false);
    buildType.renderscriptOptimLevel().setValue(2);
    buildType.shrinkResources().setValue(true);
    buildType.testCoverageEnabled().setValue(false);
    buildType.useJack().setValue(true);
    buildType.versionNameSuffix().setValue("def");
    buildType.zipAlignEnabled().setValue(false);

    assertEquals("applicationIdSuffix", "mySuffix-1", buildType.applicationIdSuffix());
    assertEquals("debuggable", Boolean.FALSE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.FALSE, buildType.jniDebuggable());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.FALSE, buildType.multiDexEnabled());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.FALSE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(2), buildType.renderscriptOptimLevel());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.FALSE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "def", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.FALSE, buildType.zipAlignEnabled());

    applyChanges(buildModel);
    assertEquals("applicationIdSuffix", "mySuffix-1", buildType.applicationIdSuffix());
    assertEquals("debuggable", Boolean.FALSE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.FALSE, buildType.jniDebuggable());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.FALSE, buildType.multiDexEnabled());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.FALSE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(2), buildType.renderscriptOptimLevel());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.FALSE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "def", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.FALSE, buildType.zipAlignEnabled());

    buildModel.reparse();
    buildType = getXyzBuildType(buildModel);
    assertEquals("applicationIdSuffix", "mySuffix-1", buildType.applicationIdSuffix());
    assertEquals("debuggable", Boolean.FALSE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.FALSE, buildType.jniDebuggable());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.FALSE, buildType.multiDexEnabled());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.FALSE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(2), buildType.renderscriptOptimLevel());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.FALSE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "def", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.FALSE, buildType.zipAlignEnabled());
  }

  @Test
  public void testReplaceAndApplyListElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      buildConfigField \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "      consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      resValue \"mnop\", \"qrst\", \"uvwx\"\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());

    buildType.replaceBuildConfigField("abcd", "efgh", "ijkl", "abcd", "mnop", "qrst");
    replaceListValue(buildType.consumerProguardFiles(), "proguard-android.txt", "proguard-android-1.txt");
    replaceListValue(buildType .proguardFiles(), "proguard-android.txt", "proguard-android-1.txt");
    buildType.replaceResValue("mnop", "qrst", "uvwx", "mnop", "efgh", "ijkl");
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "mnop", "qrst")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "efgh", "ijkl")), buildType.resValues());

    applyChanges(buildModel);
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "mnop", "qrst")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "efgh", "ijkl")), buildType.resValues());

    buildModel.reparse();
    buildType = getXyzBuildType(buildModel);
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "mnop", "qrst")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "efgh", "ijkl")), buildType.resValues());
  }

  @Test
  public void testAddAndApplyListElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEmpty("buildConfigFields", buildType.buildConfigFields());
    assertMissingProperty("consumerProguardFiles", buildType.consumerProguardFiles());
    assertMissingProperty("proguardFiles", buildType.proguardFiles());
    assertEmpty("resValues", buildType.resValues());

    buildType.addBuildConfigField("abcd", "efgh", "ijkl");
    buildType.consumerProguardFiles().addListValue().setValue("proguard-android.txt");
    buildType.proguardFiles().addListValue().setValue("proguard-android.txt");
    buildType.addResValue("mnop", "qrst", "uvwx");
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());

    applyChanges(buildModel);
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());

    buildModel.reparse();
    buildType = getXyzBuildType(buildModel);
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());
  }

  @Test
  public void testAddToAndApplyListElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      buildConfigField \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "      consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      resValue \"mnop\", \"qrst\", \"uvwx\"\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);

    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());

    buildType.addBuildConfigField("cdef", "ghij", "klmn");
    buildType.consumerProguardFiles().addListValue().setValue("proguard-android-1.txt");
    buildType.proguardFiles().addListValue().setValue("proguard-android-1.txt");
    buildType.addResValue("opqr", "stuv", "wxyz");
    verifyFlavorType("buildConfigFields",
                 ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl"), Lists.newArrayList("cdef", "ghij", "klmn")),
                 buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx"), Lists.newArrayList("opqr", "stuv", "wxyz")),
                 buildType.resValues());

    applyChanges(buildModel);
    verifyFlavorType("buildConfigFields",
                 ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl"), Lists.newArrayList("cdef", "ghij", "klmn")),
                 buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx"), Lists.newArrayList("opqr", "stuv", "wxyz")),
                 buildType.resValues());

    buildModel.reparse();
    buildType = getXyzBuildType(buildModel);
    verifyFlavorType("buildConfigFields",
                 ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl"), Lists.newArrayList("cdef", "ghij", "klmn")),
                 buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx"), Lists.newArrayList("opqr", "stuv", "wxyz")),
                 buildType.resValues());
  }

  @Test
  public void testRemoveFromAndApplyListElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      buildConfigField \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "      buildConfigField \"cdef\", \"ghij\", \"klmn\"\n" +
                  "      consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      resValue \"mnop\", \"qrst\", \"uvwx\"\n" +
                  "      resValue \"opqr\", \"stuv\", \"wxyz\"\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    verifyFlavorType("buildConfigFields",
                 ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl"), Lists.newArrayList("cdef", "ghij", "klmn")),
                 buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx"), Lists.newArrayList("opqr", "stuv", "wxyz")),
                 buildType.resValues());

    buildType.removeBuildConfigField("abcd", "efgh", "ijkl");
    removeListValue(buildType.consumerProguardFiles(), "proguard-rules.pro");
    removeListValue(buildType.proguardFiles(), "proguard-rules.pro");
    buildType.removeResValue("opqr", "stuv", "wxyz");
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("cdef", "ghij", "klmn")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());

    applyChanges(buildModel);
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("cdef", "ghij", "klmn")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());

    buildModel.reparse();
    buildType = getXyzBuildType(buildModel);
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("cdef", "ghij", "klmn")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());
  }

  @Test
  public void testRemoveFromAndApplyListElementsWithSingleElement() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      consumerProguardFiles 'proguard-android.txt'\n" +
                  "      proguardFiles = ['proguard-rules.pro']\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-rules.pro"), buildType.proguardFiles());

    removeListValue(buildType.consumerProguardFiles(), "proguard-android.txt");
    removeListValue(buildType.proguardFiles(), "proguard-rules.pro");
    assertTrue(buildType.consumerProguardFiles().getValue(LIST_TYPE).isEmpty());
    assertTrue(buildType.proguardFiles().getValue(LIST_TYPE).isEmpty());

    applyChanges(buildModel);
    assertTrue(buildType.consumerProguardFiles().getValue(LIST_TYPE).isEmpty());
    assertTrue(buildType.proguardFiles().getValue(LIST_TYPE).isEmpty());

    buildModel.reparse();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    assertThat(android, instanceOf(AndroidModelImpl.class));
  }

  @Test
  public void testSetAndApplyMapElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      manifestPlaceholders key1:\"value1\", key2:\"value2\"\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEquals("manifestPlaceholders", ImmutableMap.of("key1", "value1", "key2", "value2"), buildType.manifestPlaceholders());

    buildType.manifestPlaceholders().getMapValue("key1").setValue(12345);
    buildType.manifestPlaceholders().getMapValue("key3").setValue(true);
    assertEquals("manifestPlaceholders", ImmutableMap.of("key1", 12345, "key2", "value2", "key3", true),
                 buildType.manifestPlaceholders());

    applyChanges(buildModel);
    assertEquals("manifestPlaceholders", ImmutableMap.of("key1", 12345, "key2", "value2", "key3", true),
                 buildType.manifestPlaceholders());

    buildModel.reparse();
    buildType = getXyzBuildType(buildModel);
    assertEquals("manifestPlaceholders", ImmutableMap.of("key1", 12345, "key2", "value2", "key3", true),
                 buildType.manifestPlaceholders());
  }

  @Test
  public void testAddAndApplyMapElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertMissingProperty("manifestPlaceholders", buildType.manifestPlaceholders());

    buildType.manifestPlaceholders().getMapValue("activityLabel1").setValue("newName1");
    buildType.manifestPlaceholders().getMapValue("activityLabel2").setValue("newName2");
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "newName1", "activityLabel2", "newName2"),
                 buildType.manifestPlaceholders());

    applyChanges(buildModel);
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "newName1", "activityLabel2", "newName2"),
                 buildType.manifestPlaceholders());

    buildModel.reparse();
    buildType = getXyzBuildType(buildModel);
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "newName1", "activityLabel2", "newName2"),
                 buildType.manifestPlaceholders());
  }

  @Test
  public void testRemoveAndApplyMapElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      manifestPlaceholders activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());

    buildType.manifestPlaceholders().getValue(MAP_TYPE).get("activityLabel1").delete();
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());

    applyChanges(buildModel);
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());

    buildModel.reparse();
    buildType = getXyzBuildType(buildModel);
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());
  }

  @Test
  public void testReadSigningConfig() throws Exception {
    String text = "android {\n" +
                  "  signingConfigs {\n" +
                  "    myConfig {\n" +
                  "      storeFile file('config.keystore')\n" +
                  "    }\n" +
                  "  }\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      signingConfig signingConfigs.myConfig\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    verifyPropertyModel(buildType.signingConfig(), STRING_TYPE, "myConfig", CUSTOM, REGULAR, 1);
    assertThat(buildType.signingConfig().getRawValue(STRING_TYPE), equalTo("signingConfigs.myConfig"));
    SigningConfigModel signingConfigModel = buildType.signingConfig().toSigningConfig();
    assertThat(signingConfigModel.name(), equalTo("myConfig"));
  }

  @Test
  public void testSetSigningConfig() throws Exception {
    String text = "android {\n" +
                  "  signingConfigs {\n" +
                  "    myConfig {\n" +
                  "      storeFile file('config.keystore')\n" +
                  "    }\n" +
                  "    myBetterConfig {\n" +
                  "      storeFile file('betterConfig.keystore')\n" +
                  "    }\n" +
                  "  }\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      signingConfig signingConfigs.myConfig\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    verifyPropertyModel(buildType.signingConfig(), STRING_TYPE, "myConfig", CUSTOM, REGULAR, 1);
    assertThat(buildType.signingConfig().getRawValue(STRING_TYPE), equalTo("signingConfigs.myConfig"));
    SigningConfigPropertyModel signingConfigModel = buildType.signingConfig();
    assertThat(signingConfigModel.toSigningConfig().name(), equalTo("myConfig"));
    // Set the value to be equal to a different config.
    List<SigningConfigModel> signingConfigs = buildModel.android().signingConfigs();
    assertThat(signingConfigs.size(), equalTo(2));
    assertThat(signingConfigs.get(0).name(), equalTo("myConfig"));
    assertThat(signingConfigs.get(1).name(), equalTo("myBetterConfig"));
    signingConfigModel.setValue(new ReferenceTo(signingConfigs.get(1)));

    verifyPropertyModel(buildType.signingConfig(), STRING_TYPE, "myBetterConfig", CUSTOM, REGULAR, 1);
    assertThat(buildType.signingConfig().getRawValue(STRING_TYPE), equalTo("signingConfigs.myBetterConfig"));
    signingConfigModel = buildType.signingConfig();
    assertThat(signingConfigModel.toSigningConfig().name(), equalTo("myBetterConfig"));

    applyChangesAndReparse(buildModel);

    buildType = getXyzBuildType(buildModel);
    verifyPropertyModel(buildType.signingConfig(), STRING_TYPE, "myBetterConfig", CUSTOM, REGULAR, 1);
    assertThat(buildType.signingConfig().getRawValue(STRING_TYPE), equalTo("signingConfigs.myBetterConfig"));
    signingConfigModel = buildType.signingConfig();
    assertThat(signingConfigModel.toSigningConfig().name(), equalTo("myBetterConfig"));

    signingConfigModel.setValue(ReferenceTo.createForSigningConfig("myConfig"));
    verifyPropertyModel(buildType.signingConfig(), STRING_TYPE, "myConfig", CUSTOM, REGULAR, 1);
    assertThat(buildType.signingConfig().getRawValue(STRING_TYPE), equalTo("signingConfigs.myConfig"));
    signingConfigModel = buildType.signingConfig();
    assertThat(signingConfigModel.toSigningConfig().name(), equalTo("myConfig"));

    applyChangesAndReparse(buildModel);

    signingConfigModel.setValue(ReferenceTo.createForSigningConfig("myConfig"));

    buildType = getXyzBuildType(buildModel);
    verifyPropertyModel(buildType.signingConfig(), STRING_TYPE, "myConfig", CUSTOM, REGULAR, 1);
    assertThat(buildType.signingConfig().getRawValue(STRING_TYPE), equalTo("signingConfigs.myConfig"));
    signingConfigModel = buildType.signingConfig();
    assertThat(signingConfigModel.toSigningConfig().name(), equalTo("myConfig"));
  }

  @Test
  public void testSetSigningConfigFromEmpty() throws Exception {
    String text = "android {\n" +
                  "  signingConfigs {\n" +
                  "    myConfig {\n" +
                  "      storeFile file('config.keystore')\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    BuildTypeModel buildTypeModel = android.addBuildType("xyz");

    SigningConfigModel signingConfig = android.signingConfigs().get(0);
    assertMissingProperty(buildTypeModel.signingConfig());
    buildTypeModel.signingConfig().setValue(new ReferenceTo(signingConfig));

    SigningConfigPropertyModel signingConfigPropertyModel = buildTypeModel.signingConfig();
    verifyPropertyModel(signingConfigPropertyModel, STRING_TYPE, "myConfig", CUSTOM, REGULAR, 1);
    assertThat(signingConfigPropertyModel.getRawValue(STRING_TYPE), equalTo("signingConfigs.myConfig"));
    assertThat(signingConfigPropertyModel.toSigningConfig().name(), equalTo("myConfig"));

    applyChangesAndReparse(buildModel);

    android = buildModel.android();
    buildTypeModel = android.addBuildType("xyz");

    signingConfigPropertyModel = buildTypeModel.signingConfig();
    verifyPropertyModel(signingConfigPropertyModel, STRING_TYPE, "myConfig", CUSTOM, REGULAR, 1);
    assertThat(signingConfigPropertyModel.getRawValue(STRING_TYPE), equalTo("signingConfigs.myConfig"));
    assertThat(signingConfigPropertyModel.toSigningConfig().name(), equalTo("myConfig"));
  }

  @NotNull
  private static BuildTypeModel getXyzBuildType(GradleBuildModel buildModel) {
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    List<BuildTypeModel> buildTypeModels = android.buildTypes();
    assertThat(buildTypeModels.size(), equalTo(1));

    BuildTypeModel buildType = buildTypeModels.get(0);
    assertEquals("name", "xyz", buildType.name());
    return buildType;
  }
}
