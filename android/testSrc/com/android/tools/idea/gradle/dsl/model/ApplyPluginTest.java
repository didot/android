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

import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_ADD_AND_APPLY_ALREADY_EXISTING_PLUGIN;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_ADD_AND_APPLY_PLUGIN;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_ADD_AND_APPLY_PLUGIN_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_ADD_AND_RESET_ALREADY_EXISTING_PLUGIN;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_ADD_AND_RESET_PLUGIN;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_ADD_EXISTING_PLUGIN_TO_PLUGINS_AND_APPLY_BLOCKS;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_ADD_PLUGIN_TO_PLUGINS_BLOCK;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_APPLIED_KOTLIN_PLUGIN;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_APPLIED_PLUGINS_BLOCK;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_APPLIED_PLUGINS_BLOCK_WITH_REPEATED_PLUGINS;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_APPLIED_PLUGIN_COMPATIBILITY;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_APPLY_PLUGINS_FROM_PLUGINS_BLOCK;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_APPLY_PLUGIN_AT_START;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_APPLY_PLUGIN_AT_START_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_APPLY_PLUGIN_STATEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_APPLY_PLUGIN_STATEMENTS_WITH_REPEATED_PLUGINS;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_APPLY_REPEATED_PLUGINS_FROM_APPLY_AND_PLUGINS_BLOCK;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_DELETE_PLUGIN_NAME;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_INSERT_PLUGIN_ORDER;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_INSERT_PLUGIN_ORDER_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_PLUGINS_BLOCK_WITH_REPEATED_PLUGINS;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_PLUGINS_FROM_APPLY_AND_PLUGINS_BLOCK;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_PLUGINS_UNSUPPORTED_SYNTAX;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_BLOCK;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_BLOCK_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_BLOCK_WITH_DUPLICATED_PLUGIN;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_BLOCK_WITH_DUPLICATED_PLUGIN_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_STATEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_STATEMENTS_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_STATEMENTS_WITH_REPEATED_PLUGINS;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_STATEMENTS_WITH_REPEATED_PLUGINS_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_REMOVE_AND_RESET_PLUGIN_FROM_APPLY_BLOCK;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_REMOVE_AND_RESET_PLUGIN_FROM_APPLY_BLOCK_WITH_DUPLICATED_PLUGIN;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_REMOVE_AND_RESET_PLUGIN_FROM_APPLY_STATEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_REMOVE_AND_RESET_PLUGIN_FROM_APPLY_STATEMENTS_WITH_REPEATED_PLUGINS;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_SET_PLUGIN_NAME;
import static com.android.tools.idea.gradle.dsl.TestFileName.APPLY_PLUGIN_SET_PLUGIN_NAME_EXPECTED;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.STRING;
import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.DERIVED;
import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.PluginModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.google.common.collect.ImmutableList;
import com.intellij.psi.PsiElement;
import java.util.List;
import org.junit.Test;

/**
 * Tests for {@link GradleBuildModelImpl} to test apply, add and remove plugins.
 */
public class ApplyPluginTest extends GradleFileModelTestCase {
  @Test
  public void testAppliedPluginsBlock() throws Exception {
    writeToBuildFile(APPLY_PLUGIN_APPLIED_PLUGINS_BLOCK);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
  }

  @Test
  public void testAppliedPluginsBlockWithRepeatedPlugins() throws Exception {
    writeToBuildFile(APPLY_PLUGIN_APPLIED_PLUGINS_BLOCK_WITH_REPEATED_PLUGINS);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
  }

  @Test
  public void testApplyPluginStatements() throws Exception {
    writeToBuildFile(APPLY_PLUGIN_APPLY_PLUGIN_STATEMENTS);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
  }

  @Test
  public void testApplyPluginStatementsWithRepeatedPlugins() throws Exception {
    writeToBuildFile(APPLY_PLUGIN_APPLY_PLUGIN_STATEMENTS_WITH_REPEATED_PLUGINS);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
  }

  @Test
  public void testRemoveAndResetPluginFromApplyBlock() throws Exception {
    writeToBuildFile(APPLY_PLUGIN_REMOVE_AND_RESET_PLUGIN_FROM_APPLY_BLOCK);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());

    buildModel.removePlugin("com.android.application");
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());

    buildModel.resetState();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
  }

  @Test
  public void testRemoveAndResetPluginFromApplyBlockWithDuplicatedPlugin() throws Exception {
    writeToBuildFile(APPLY_PLUGIN_REMOVE_AND_RESET_PLUGIN_FROM_APPLY_BLOCK_WITH_DUPLICATED_PLUGIN);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());

    buildModel.removePlugin("com.android.application");
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());

    buildModel.resetState();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
  }

  @Test
  public void testRemoveAndResetPluginFromApplyStatements() throws Exception {
    writeToBuildFile(APPLY_PLUGIN_REMOVE_AND_RESET_PLUGIN_FROM_APPLY_STATEMENTS);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());

    buildModel.removePlugin("com.android.application");
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());

    buildModel.resetState();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
  }

  @Test
  public void testRemoveAndResetPluginFromApplyStatementsWithRepeatedPlugins() throws Exception {
    writeToBuildFile(APPLY_PLUGIN_REMOVE_AND_RESET_PLUGIN_FROM_APPLY_STATEMENTS_WITH_REPEATED_PLUGINS);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());

    buildModel.removePlugin("com.android.application");
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());

    buildModel.resetState();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
  }

  @Test
  public void testRemoveAndApplyPluginFromApplyBlock() throws Exception {
    writeToBuildFile(APPLY_PLUGIN_REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_BLOCK);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());

    buildModel.removePlugin("com.android.application");
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, APPLY_PLUGIN_REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_BLOCK_EXPECTED);

    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());

    buildModel.reparse();
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());
  }

  @Test
  public void testRemoveAndApplyPluginFromApplyBlockWithDuplicatedPlugin() throws Exception {
    writeToBuildFile(APPLY_PLUGIN_REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_BLOCK_WITH_DUPLICATED_PLUGIN);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());

    buildModel.removePlugin("com.android.application");
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, APPLY_PLUGIN_REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_BLOCK_WITH_DUPLICATED_PLUGIN_EXPECTED);

    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());

    buildModel.reparse();
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());
  }

  @Test
  public void testRemoveAndApplyPluginFromApplyStatements() throws Exception {
    writeToBuildFile(APPLY_PLUGIN_REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_STATEMENTS);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());

    buildModel.removePlugin("com.android.application");
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, APPLY_PLUGIN_REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_STATEMENTS_EXPECTED);

    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());

    buildModel.reparse();
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());
  }

  @Test
  public void testRemoveAndApplyPluginFromApplyStatementsWithRepeatedPlugins() throws Exception {
    writeToBuildFile(APPLY_PLUGIN_REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_STATEMENTS_WITH_REPEATED_PLUGINS);
    GradleBuildModel buildModel = getGradleBuildModel();

    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());

    buildModel.removePlugin("com.android.application");
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, APPLY_PLUGIN_REMOVE_AND_APPLY_PLUGIN_FROM_APPLY_STATEMENTS_WITH_REPEATED_PLUGINS_EXPECTED);

    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());

    buildModel.reparse();
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());
  }

  @Test
  public void testAddAndResetPlugin() throws Exception {
    writeToBuildFile(APPLY_PLUGIN_ADD_AND_RESET_PLUGIN);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.plugins());

    buildModel.applyPlugin("com.android.library");
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());

    buildModel.resetState();
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.plugins());
  }

  @Test
  public void testAddAndResetAlreadyExistingPlugin() throws Exception {
    writeToBuildFile(APPLY_PLUGIN_ADD_AND_RESET_ALREADY_EXISTING_PLUGIN);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());

    buildModel.applyPlugin("com.android.application");
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());

    buildModel.resetState();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
  }

  @Test
  public void testAddAndApplyPlugin() throws Exception {
    // TODO(b/142539025): creating an apply plugin form in Kotlin generates invalid Dsl
    writeToBuildFile(APPLY_PLUGIN_ADD_AND_APPLY_PLUGIN);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.plugins());

    buildModel.applyPlugin("com.android.library");

    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, APPLY_PLUGIN_ADD_AND_APPLY_PLUGIN_EXPECTED);

    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());

    buildModel.reparse();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
  }

  @Test
  public void testAddAndApplyAlreadyExistingPlugin() throws Exception {
    writeToBuildFile(APPLY_PLUGIN_ADD_AND_APPLY_ALREADY_EXISTING_PLUGIN);

    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());

    buildModel.applyPlugin("com.android.application");
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, APPLY_PLUGIN_ADD_AND_APPLY_ALREADY_EXISTING_PLUGIN);

    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());

    buildModel.reparse();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
  }

  @Test
  public void testSetPluginName() throws Exception {
    // TODO(b/142539025): Kotlin parsing of apply(plugin=...) is different from assumptions made elsewhere (it parses as a DslMethodCall, but
    //  code assumes that this form is a literal DslMap (with name "apply").
    assumeTrue(isGroovy());
    writeToBuildFile(APPLY_PLUGIN_SET_PLUGIN_NAME);
    GradleBuildModel buildModel = getGradleBuildModel();

    assertSize(2, buildModel.plugins());
    PluginModel pluginModel = buildModel.plugins().get(0);
    verifyPropertyModel(pluginModel.name(), STRING_TYPE, "com.android.application", STRING, DERIVED, 0, "plugin");
    PluginModel otherPlugin = buildModel.plugins().get(1);
    verifyPropertyModel(otherPlugin.name(), STRING_TYPE, "com.foo.bar", STRING, REGULAR, 0, "plugin");

    pluginModel.name().setValue("com.google.application");
    otherPlugin.name().setValue("bar.com.foo");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, APPLY_PLUGIN_SET_PLUGIN_NAME_EXPECTED);

    assertSize(2, buildModel.plugins());
    pluginModel = buildModel.plugins().get(0);
    verifyPropertyModel(pluginModel.name(), STRING_TYPE, "com.google.application", STRING, DERIVED, 0, "plugin");
    otherPlugin = buildModel.plugins().get(1);
    verifyPropertyModel(otherPlugin.name(), STRING_TYPE, "bar.com.foo", STRING, REGULAR, 0, "plugin");
  }

  @Test
  public void testDeletePluginName() throws Exception {
    writeToBuildFile(APPLY_PLUGIN_DELETE_PLUGIN_NAME);
    GradleBuildModel buildModel = getGradleBuildModel();

    assertSize(2, buildModel.plugins());
    PluginModel pluginModel = buildModel.plugins().get(0);
    PluginModel otherPlugin = buildModel.plugins().get(1);

    pluginModel.name().delete();
    otherPlugin.name().delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, "");

    assertSize(0, buildModel.plugins());
  }

  @Test
  public void testInsertPluginOrder() throws Exception {
    writeToBuildFile(APPLY_PLUGIN_INSERT_PLUGIN_ORDER);
    GradleBuildModel buildModel = getGradleBuildModel();
    buildModel.applyPlugin("kotlin-android");
    buildModel.applyPlugin("kotlin-plugin-extensions");
    buildModel.applyPlugin("some-other-plugin");

    verifyPropertyModel(buildModel.plugins().get(0).name(), STRING_TYPE, "kotlin-android", STRING, REGULAR, 0);
    verifyPropertyModel(buildModel.plugins().get(1).name(), STRING_TYPE, "kotlin-plugin-extensions", STRING, REGULAR, 0);
    verifyPropertyModel(buildModel.plugins().get(2).name(), STRING_TYPE, "some-other-plugin", STRING, REGULAR, 0);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, APPLY_PLUGIN_INSERT_PLUGIN_ORDER_EXPECTED);

    verifyPropertyModel(buildModel.plugins().get(0).name(), STRING_TYPE, "kotlin-android", STRING, REGULAR, 0);
    verifyPropertyModel(buildModel.plugins().get(1).name(), STRING_TYPE, "kotlin-plugin-extensions", STRING, REGULAR, 0);
    verifyPropertyModel(buildModel.plugins().get(2).name(), STRING_TYPE, "some-other-plugin", STRING, REGULAR, 0);
  }

  @Test
  public void testApplyPluginAtStart() throws Exception {
    writeToBuildFile(APPLY_PLUGIN_APPLY_PLUGIN_AT_START);
    GradleBuildModel buildModel = getGradleBuildModel();
    buildModel.applyPlugin("kotlin-android");
    buildModel.applyPlugin("kotlin-plugin-extensions");
    buildModel.applyPlugin("some-other-plugin");

    verifyPropertyModel(buildModel.plugins().get(0).name(), STRING_TYPE, "kotlin-android", STRING, REGULAR, 0);
    verifyPropertyModel(buildModel.plugins().get(1).name(), STRING_TYPE, "kotlin-plugin-extensions", STRING, REGULAR, 0);
    verifyPropertyModel(buildModel.plugins().get(2).name(), STRING_TYPE, "some-other-plugin", STRING, REGULAR, 0);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, APPLY_PLUGIN_APPLY_PLUGIN_AT_START_EXPECTED);

    verifyPropertyModel(buildModel.plugins().get(0).name(), STRING_TYPE, "kotlin-android", STRING, REGULAR, 0);
    verifyPropertyModel(buildModel.plugins().get(1).name(), STRING_TYPE, "kotlin-plugin-extensions", STRING, REGULAR, 0);
    verifyPropertyModel(buildModel.plugins().get(2).name(), STRING_TYPE, "some-other-plugin", STRING, REGULAR, 0);
  }

  @Test
  public void testAppliedKotlinPlugin() throws Exception {
    writeToBuildFile(APPLY_PLUGIN_APPLIED_KOTLIN_PLUGIN);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("kotlin-android", "kotlin-plugin-extensions"), buildModel.plugins());
  }

  @Test
  public void testApplyPluginsFromPluginsBlock() throws Exception {
    writeToBuildFile(APPLY_PLUGIN_APPLY_PLUGINS_FROM_PLUGINS_BLOCK);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("maven-publish", "jacoco"), buildModel.plugins());
  }

  @Test
  public void testPluginsBlockWithRepeatedPlugins() throws Exception {
    writeToBuildFile(APPLY_PLUGIN_PLUGINS_BLOCK_WITH_REPEATED_PLUGINS);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
  }

  @Test
  public void testPluginsFromApplyAndPluginsBlock() throws Exception {
    writeToBuildFile(APPLY_PLUGIN_PLUGINS_FROM_APPLY_AND_PLUGINS_BLOCK);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application", "com.android.library"), buildModel.plugins());
  }

  @Test
  public void testApplyRepeatedPluginsFromApplyAndPluginsBlocks() throws Exception {
    writeToBuildFile(APPLY_PLUGIN_APPLY_REPEATED_PLUGINS_FROM_APPLY_AND_PLUGINS_BLOCK);
    GradleBuildModel buildModel = getGradleBuildModel();
    List<PluginModel> plugins = buildModel.plugins();
    assertSize(3, plugins);
    verifyPlugins(ImmutableList.of("maven-publish", "com.android.application", "com.android.library"), plugins);
  }

  @Test
  public void testAddPluginToPluginsBlock() throws Exception {
    writeToBuildFile(APPLY_PLUGIN_ADD_PLUGIN_TO_PLUGINS_BLOCK);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.application"), buildModel.plugins());

    buildModel.applyPlugin("kotlin-android");
    List<PluginModel> plugins = buildModel.plugins();
    verifyPlugins(ImmutableList.of("com.android.application", "kotlin-android"), plugins);
  }

  @Test
  public void testAddExistingPluginToPluginsAndApplyBlock() throws Exception {
    writeToBuildFile(APPLY_PLUGIN_ADD_EXISTING_PLUGIN_TO_PLUGINS_AND_APPLY_BLOCKS);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("kotlin-android", "com.android.application"), buildModel.plugins());

    buildModel.applyPlugin("kotlin-android");
    List<PluginModel> plugins = buildModel.plugins();
    assertSize(2, plugins);
    verifyPlugins(ImmutableList.of("com.android.application", "kotlin-android"), plugins);
  }

  @Test
  public void testOnlyParsePluginsWithCorrectSyntax() throws Exception {
    writeToBuildFile(APPLY_PLUGIN_PLUGINS_UNSUPPORTED_SYNTAX);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifyPlugins(ImmutableList.of("com.android.library"), buildModel.plugins());
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testAppliedPluginCompatibility() throws Exception {
    writeToBuildFile(APPLY_PLUGIN_APPLIED_PLUGIN_COMPATIBILITY);
    GradleBuildModel buildModel = getGradleBuildModel();

    List<GradleNotNullValue<String>> plugins = buildModel.appliedPlugins();
    assertSize(2, plugins);

    GradleNotNullValue<String> first = plugins.get(0);
    assertEquals("com.android.application", first.value());
    assertEquals("plugin", first.getPropertyName());
    // TODO(b/142539688): this is the only reference in the Android Studio project to getDslText().  However, the commit message associated
    //  with this line indicates that this test was introduced along with a restoration of the functionality for the benefit of the
    //  RenderCore asset plugin.  In a multilingual world, testing the raw DslText is unsound, and we shouldn't provide such a direct
    //  access to it either.
    assertEquals("plugin: 'com.android.application'", first.getDslText());
    assertEquals(myBuildFile, first.getFile());
  }
}
