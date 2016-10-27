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
package com.android.tools.idea.gradle.dsl.model.values;

import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.android.tools.idea.gradle.dsl.model.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.model.android.ProductFlavorModel;

import java.util.List;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link GradleValue}.
 */
public class GradleValueTest extends GradleFileModelTestCase {
  public void testGradleValuesOfLiteralElementsInApplicationStatements() throws Exception {
    String text = "android { \n" +
                  "  buildToolsVersion \"23.0.0\"\n" +
                  "  compileSdkVersion 23\n" +
                  "  defaultPublishConfig \"debug\"\n" +
                  "  generatePureSplits true\n" +
                  "  publishNonDefault false\n" +
                  "  resourcePrefix \"abcd\"\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    verifyGradleValue(android.buildToolsVersion(), "android.buildToolsVersion", "\"23.0.0\"");
    verifyGradleValue(android.compileSdkVersion(), "android.compileSdkVersion", "23");
    verifyGradleValue(android.defaultPublishConfig(), "android.defaultPublishConfig", "\"debug\"");
    verifyGradleValue(android.generatePureSplits(), "android.generatePureSplits", "true");
    verifyGradleValue(android.publishNonDefault(), "android.publishNonDefault", "false");
    verifyGradleValue(android.resourcePrefix(), "android.resourcePrefix", "\"abcd\"");
  }

  public void testGradleValuesOfLiteralElementsInAssignmentStatements() throws Exception {
    String text = "android { \n" +
                  "  buildToolsVersion = \"23.0.0\"\n" +
                  "  compileSdkVersion = \"android-23\"\n" +
                  "  defaultPublishConfig = \"debug\"\n" +
                  "  generatePureSplits = true\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    verifyGradleValue(android.buildToolsVersion(), "android.buildToolsVersion", "\"23.0.0\"");
    verifyGradleValue(android.compileSdkVersion(), "android.compileSdkVersion", "\"android-23\"");
    verifyGradleValue(android.defaultPublishConfig(), "android.defaultPublishConfig", "\"debug\"");
    verifyGradleValue(android.generatePureSplits(), "android.generatePureSplits", "true");
  }

  public void testListOfGradleValuesInApplicationStatements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "    consumerProguardFiles 'con-proguard-android.txt', 'con-proguard-rules.pro'\n" +
                  "    proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "    resConfigs 'abcd', 'efgh'" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);
    ProductFlavorModel defaultConfig = android.defaultConfig();

    List<GradleNotNullValue<String>> consumerProguardFiles = defaultConfig.consumerProguardFiles();
    assertNotNull(consumerProguardFiles);
    assertThat(consumerProguardFiles).hasSize(2);
    verifyGradleValue(consumerProguardFiles.get(0), "android.defaultConfig.consumerProguardFiles.consumerProguardFiles",
                      "'con-proguard-android.txt'");
    verifyGradleValue(consumerProguardFiles.get(1), "android.defaultConfig.consumerProguardFiles.consumerProguardFiles",
                      "'con-proguard-rules.pro'");

    List<GradleNotNullValue<String>> proguardFiles = defaultConfig.proguardFiles();
    assertNotNull(proguardFiles);
    assertThat(proguardFiles).hasSize(2);
    verifyGradleValue(proguardFiles.get(0), "android.defaultConfig.proguardFiles.proguardFiles", "'proguard-android.txt'");
    verifyGradleValue(proguardFiles.get(1), "android.defaultConfig.proguardFiles.proguardFiles", "'proguard-rules.pro'");

    List<GradleNotNullValue<String>> resConfigs = defaultConfig.resConfigs();
    assertNotNull(resConfigs);
    assertThat(resConfigs).hasSize(2);
    verifyGradleValue(resConfigs.get(0), "android.defaultConfig.resConfigs.resConfigs", "'abcd'");
    verifyGradleValue(resConfigs.get(1), "android.defaultConfig.resConfigs.resConfigs", "'efgh'");
  }

  public void testListOfGradleValuesInAssignmentStatements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "    consumerProguardFiles = ['con-proguard-android.txt', 'con-proguard-rules.pro']\n" +
                  "    proguardFiles = ['proguard-android.txt', 'proguard-rules.pro']\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);
    ProductFlavorModel defaultConfig = android.defaultConfig();

    List<GradleNotNullValue<String>> consumerProguardFiles = defaultConfig.consumerProguardFiles();
    assertNotNull(consumerProguardFiles);
    assertThat(consumerProguardFiles).hasSize(2);
    verifyGradleValue(consumerProguardFiles.get(0), "android.defaultConfig.consumerProguardFiles.consumerProguardFiles",
                      "'con-proguard-android.txt'");
    verifyGradleValue(consumerProguardFiles.get(1), "android.defaultConfig.consumerProguardFiles.consumerProguardFiles",
                      "'con-proguard-rules.pro'");

    List<GradleNotNullValue<String>> proguardFiles = defaultConfig.proguardFiles();
    assertNotNull(proguardFiles);
    assertThat(proguardFiles).hasSize(2);
    verifyGradleValue(proguardFiles.get(0), "android.defaultConfig.proguardFiles.proguardFiles", "'proguard-android.txt'");
    verifyGradleValue(proguardFiles.get(1), "android.defaultConfig.proguardFiles.proguardFiles", "'proguard-rules.pro'");
  }

  public void testMapOfGradleValuesInApplicationStatements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "    manifestPlaceholders activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"\n" +
                  "    testInstrumentationRunnerArguments size:\"medium\", foo:\"bar\"\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();

    Map<String, GradleNotNullValue<Object>> manifestPlaceholders = defaultConfig.manifestPlaceholders();
    assertNotNull(manifestPlaceholders);
    GradleNotNullValue<Object> activityLabel1 = manifestPlaceholders.get("activityLabel1");
    assertNotNull(activityLabel1);
    verifyGradleValue(activityLabel1, "android.defaultConfig.manifestPlaceholders.activityLabel1", "\"defaultName1\"");
    GradleNotNullValue<Object> activityLabel2 = manifestPlaceholders.get("activityLabel2");
    assertNotNull(activityLabel2);
    verifyGradleValue(activityLabel2, "android.defaultConfig.manifestPlaceholders.activityLabel2", "\"defaultName2\"");

    Map<String, GradleNotNullValue<String>> testInstrumentationRunnerArguments = defaultConfig.testInstrumentationRunnerArguments();
    assertNotNull(testInstrumentationRunnerArguments);
    GradleNotNullValue<String> size = testInstrumentationRunnerArguments.get("size");
    assertNotNull(size);
    verifyGradleValue(size, "android.defaultConfig.testInstrumentationRunnerArguments.size", "\"medium\"");
    GradleNotNullValue<String> foo = testInstrumentationRunnerArguments.get("foo");
    assertNotNull(foo);
    verifyGradleValue(foo, "android.defaultConfig.testInstrumentationRunnerArguments.foo", "\"bar\"");
  }

  public void testMapOfGradleValuesInAssignmentStatements() throws Exception {
    String text = "android {\n" +
                  "  defaultConfig {\n" +
                  "    manifestPlaceholders = [activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"]\n" +
                  "    testInstrumentationRunnerArguments = [size:\"medium\", foo:\"bar\"]\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    ProductFlavorModel defaultConfig = android.defaultConfig();

    Map<String, GradleNotNullValue<Object>> manifestPlaceholders = defaultConfig.manifestPlaceholders();
    assertNotNull(manifestPlaceholders);
    GradleNotNullValue<Object> activityLabel1 = manifestPlaceholders.get("activityLabel1");
    assertNotNull(activityLabel1);
    verifyGradleValue(activityLabel1, "android.defaultConfig.manifestPlaceholders.activityLabel1", "\"defaultName1\"");
    GradleNotNullValue<Object> activityLabel2 = manifestPlaceholders.get("activityLabel2");
    assertNotNull(activityLabel2);
    verifyGradleValue(activityLabel2, "android.defaultConfig.manifestPlaceholders.activityLabel2", "\"defaultName2\"");

    Map<String, GradleNotNullValue<String>> testInstrumentationRunnerArguments = defaultConfig.testInstrumentationRunnerArguments();
    assertNotNull(testInstrumentationRunnerArguments);
    GradleNotNullValue<String> size = testInstrumentationRunnerArguments.get("size");
    assertNotNull(size);
    verifyGradleValue(size, "android.defaultConfig.testInstrumentationRunnerArguments.size", "\"medium\"");
    GradleNotNullValue<String> foo = testInstrumentationRunnerArguments.get("foo");
    assertNotNull(foo);
    verifyGradleValue(foo, "android.defaultConfig.testInstrumentationRunnerArguments.foo", "\"bar\"");
  }
}
