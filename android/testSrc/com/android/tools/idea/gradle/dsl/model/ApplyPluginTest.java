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
package com.android.tools.idea.gradle.dsl.model;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.google.common.collect.ImmutableList;
import com.intellij.psi.PsiElement;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link GradleBuildModelImpl} to test apply, add and remove plugins.
 */
public class ApplyPluginTest extends GradleFileModelTestCase {
  @Test
  public void testAppliedPluginsBlock() throws Exception {
    String text = "apply {\n" +
                  "  plugin 'com.android.application'\n" +
                  "  plugin 'com.android.library'\n" +
                  "}";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    assertEquals("apply", ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testAppliedPluginsBlockWithRepeatedPlugins() throws Exception {
    String text = "apply {\n" +
                  "  plugin 'com.android.application'\n" +
                  "  plugin 'com.android.library'\n" +
                  "  plugin 'com.android.application'\n" +
                  "}";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    assertEquals("apply", ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testApplyPluginStatements() throws Exception {
    String text = "apply plugin: 'com.android.application'\n" +
                  "apply plugin: 'com.android.library'";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    assertEquals("apply", ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testApplyPluginStatementsWithRepeatedPlugins() throws Exception {
    String text = "apply plugin: 'com.android.application'\n" +
                  "apply plugin: 'com.android.library'\n" +
                  "apply plugin: 'com.android.application'";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    assertEquals("apply", ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testRemoveAndResetPluginFromApplyBlock() throws Exception {
    String text = "apply {\n" +
                  "  plugin 'com.android.application'\n" +
                  "  plugin 'com.android.library'\n" +
                  "}";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    assertEquals("apply", ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());

    buildModel.removePlugin("com.android.application");
    assertEquals("apply", ImmutableList.of("com.android.library"), buildModel.appliedPlugins());

    buildModel.resetState();
    assertEquals("apply", ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testRemoveAndResetPluginFromApplyBlockWithDuplicatedPlugin() throws Exception {
    String text = "apply {\n" +
                  "  plugin 'com.android.application'\n" +
                  "  plugin 'com.android.library'\n" +
                  "  plugin 'com.android.application'\n" +
                  "}";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    assertEquals("apply", ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());

    buildModel.removePlugin("com.android.application");
    assertEquals("apply", ImmutableList.of("com.android.library"), buildModel.appliedPlugins());

    buildModel.resetState();
    assertEquals("apply", ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testRemoveAndResetPluginFromApplyStatements() throws Exception {
    String text = "apply plugin: 'com.android.application'\n" +
                  "apply plugin: 'com.android.library'";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    assertEquals("apply", ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());

    buildModel.removePlugin("com.android.application");
    assertEquals("apply", ImmutableList.of("com.android.library"), buildModel.appliedPlugins());

    buildModel.resetState();
    assertEquals("apply", ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testRemoveAndResetPluginFromApplyStatementsWithRepeatedPlugins() throws Exception {
    String text = "apply plugin: 'com.android.application'\n" +
                  "apply plugin: 'com.android.library'\n" +
                  "apply plugin: 'com.android.application'";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    assertEquals("apply", ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());

    buildModel.removePlugin("com.android.application");
    assertEquals("apply", ImmutableList.of("com.android.library"), buildModel.appliedPlugins());

    buildModel.resetState();
    assertEquals("apply", ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testRemoveAndApplyPluginFromApplyBlock() throws Exception {
    String text = "apply {\n" +
                  "  plugin 'com.android.application'\n" +
                  "  plugin 'com.android.library'\n" +
                  "}";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    assertEquals("apply", ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());

    buildModel.removePlugin("com.android.application");
    assertEquals("apply", ImmutableList.of("com.android.library"), buildModel.appliedPlugins());

    applyChanges(buildModel);
    assertEquals("apply", ImmutableList.of("com.android.library"), buildModel.appliedPlugins());

    buildModel.reparse();
    assertEquals("apply", ImmutableList.of("com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testRemoveAndApplyPluginFromApplyBlockWithDuplicatedPlugin() throws Exception {
    String text = "apply {\n" +
                  "  plugin 'com.android.application'\n" +
                  "  plugin 'com.android.library'\n" +
                  "  plugin 'com.android.application'\n" +
                  "}";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    assertEquals("apply", ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());

    buildModel.removePlugin("com.android.application");
    assertEquals("apply", ImmutableList.of("com.android.library"), buildModel.appliedPlugins());

    applyChanges(buildModel);
    assertEquals("apply", ImmutableList.of("com.android.library"), buildModel.appliedPlugins());

    buildModel.reparse();
    assertEquals("apply", ImmutableList.of("com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testRemoveAndApplyPluginFromApplyStatements() throws Exception {
    String text = "apply plugin: 'com.android.application'\n" +
                  "apply plugin: 'com.android.library'";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    assertEquals("apply", ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());

    buildModel.removePlugin("com.android.application");
    assertEquals("apply", ImmutableList.of("com.android.library"), buildModel.appliedPlugins());

    applyChanges(buildModel);
    assertEquals("apply", ImmutableList.of("com.android.library"), buildModel.appliedPlugins());

    buildModel.reparse();
    assertEquals("apply", ImmutableList.of("com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testRemoveAndApplyPluginFromApplyStatementsWithRepeatedPlugins() throws Exception {
    String text = "apply plugin: 'com.android.application'\n" +
                  "apply plugin: 'com.android.library'\n" +
                  "apply plugin: 'com.android.application'";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    assertEquals("apply", ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());

    buildModel.removePlugin("com.android.application");
    assertEquals("apply", ImmutableList.of("com.android.library"), buildModel.appliedPlugins());

    applyChanges(buildModel);
    assertEquals("apply", ImmutableList.of("com.android.library"), buildModel.appliedPlugins());

    buildModel.reparse();
    assertEquals("apply", ImmutableList.of("com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testAddAndResetPlugin() throws Exception {
    String text = "apply plugin: 'com.android.application'";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    assertEquals("apply", ImmutableList.of("com.android.application"), buildModel.appliedPlugins());

    buildModel.applyPlugin("com.android.library");
    assertEquals("apply", ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());

    buildModel.resetState();
    assertEquals("apply", ImmutableList.of("com.android.application"), buildModel.appliedPlugins());
  }

  @Test
  public void testAddAndResetAlreadyExistingPlugin() throws Exception {
    String text = "apply plugin: 'com.android.application'\n" +
                  "apply plugin: 'com.android.library'";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    assertEquals("apply", ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());

    buildModel.applyPlugin("com.android.application");
    assertEquals("apply", ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());

    buildModel.resetState();
    assertEquals("apply", ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testAddAndApplyPlugin() throws Exception {
    String text = "apply plugin: 'com.android.application'";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    assertEquals("apply", ImmutableList.of("com.android.application"), buildModel.appliedPlugins());

    buildModel.applyPlugin("com.android.library");
    assertEquals("apply", ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());

    applyChanges(buildModel);
    assertEquals("apply", ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());

    buildModel.reparse();
    assertEquals("apply", ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());
  }

  @Test
  public void testAddAndApplyAlreadyExistingPlugin() throws Exception {
    String text = "apply plugin: 'com.android.application'\n" +
                  "apply plugin: 'com.android.library'";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyAppliedPlugins(buildModel, text);

    buildModel.applyPlugin("com.android.application");
    verifyAppliedPlugins(buildModel, text);

    applyChanges(buildModel);
    verifyAppliedPlugins(buildModel, text);

    buildModel.reparse();
    verifyAppliedPlugins(buildModel, text);
  }

  private static void verifyAppliedPlugins(GradleBuildModel buildModel, String buildText) {
    assertEquals("apply", ImmutableList.of("com.android.application", "com.android.library"), buildModel.appliedPlugins());
    assertThat(buildModel).isInstanceOf(GradleBuildModelImpl.class);
    GradleBuildModelImpl buildModelImpl = (GradleBuildModelImpl)buildModel;

    PsiElement buildFilePsiElement = buildModelImpl.getPsiElement();
    assertNotNull(buildFilePsiElement);
    assertEquals("buildText", buildText, buildFilePsiElement.getText());
  }
}
