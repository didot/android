/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.android.modules

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.createTreeModel
import com.android.tools.idea.gradle.structure.configurables.ui.PropertiesUiModel
import com.android.tools.idea.gradle.structure.configurables.ui.listPropertyEditor
import com.android.tools.idea.gradle.structure.configurables.ui.mapPropertyEditor
import com.android.tools.idea.gradle.structure.configurables.ui.modules.ModulePanel
import com.android.tools.idea.gradle.structure.configurables.ui.simplePropertyEditor
import com.android.tools.idea.gradle.structure.configurables.ui.uiProperty
import com.android.tools.idea.gradle.structure.model.android.AndroidModuleDescriptors
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModuleDefaultConfigDescriptors
import com.google.wireless.android.sdk.stats.PSDEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer

class AndroidModuleRootConfigurable(
  context: PsContext, module: PsAndroidModule
) : AbstractModuleConfigurable<PsAndroidModule, ModulePanel>(context, module), Disposable {

  private val signingConfigsModel = createTreeModel(SigningConfigsConfigurable(module, context).also { Disposer.register(this, it) })

  override fun getId() = "android.psd.modules." + displayName
  override fun createPanel() =
      ModulePanel(context, module, signingConfigsModel)
  override fun dispose() = Unit
}

fun androidModulePropertiesModel() =
  PropertiesUiModel(
    listOf(
      uiProperty(AndroidModuleDescriptors.compileSdkVersion, ::simplePropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_MODULE_PROPERTIES_COMPILE_SDK_VERSION),
      uiProperty(AndroidModuleDescriptors.buildToolsVersion, ::simplePropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_MODULE_PROPERTIES_BUILD_TOLS_VERSION),
      uiProperty(AndroidModuleDescriptors.sourceCompatibility, ::simplePropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_MODULE_PROPERTIES_SOURCE_COMPATIBILITY),
      uiProperty(AndroidModuleDescriptors.targetCompatibility, ::simplePropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_MODULE_PROPERTIES_TARGET_COMPATIBILITY)))

fun defaultConfigPropertiesModel(isLibrary: Boolean) =
  PropertiesUiModel(
    listOfNotNull(
      if (!isLibrary) uiProperty(PsAndroidModuleDefaultConfigDescriptors.applicationId, ::simplePropertyEditor,
                                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_MODULES_DEFAULTCONFIG_APPLICATION_ID)
      else null,
      // TODO(b/123013466): [New PSD] Analytics for new PSD missing fields.
      if (!isLibrary) uiProperty(PsAndroidModuleDefaultConfigDescriptors.applicationIdSuffix, ::simplePropertyEditor, null) else null,
      uiProperty(PsAndroidModuleDefaultConfigDescriptors.targetSdkVersion, ::simplePropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_MODULES_DEFAULTCONFIG_TARGET_SDK_VERSION),
      uiProperty(PsAndroidModuleDefaultConfigDescriptors.minSdkVersion, ::simplePropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_MODULES_DEFAULTCONFIG_MIN_SDK_VERSION),
      uiProperty(PsAndroidModuleDefaultConfigDescriptors.maxSdkVersion, ::simplePropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_MODULES_DEFAULTCONFIG_MAX_SDK_VERSION),
      uiProperty(PsAndroidModuleDefaultConfigDescriptors.signingConfig, ::simplePropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_MODULES_DEFAULTCONFIG_SIGNING_CONFIG),
      // TODO(b/123013466): [New PSD] Analytics for new PSD missing fields.
      if (isLibrary) uiProperty(PsAndroidModuleDefaultConfigDescriptors.consumerProGuardFiles, ::listPropertyEditor, null) else null,
      uiProperty(PsAndroidModuleDefaultConfigDescriptors.proGuardFiles, ::listPropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_MODULES_DEFAULTCONFIG_PROGUARD_FILES),
      uiProperty(PsAndroidModuleDefaultConfigDescriptors.manifestPlaceholders, ::mapPropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_MODULES_DEFAULTCONFIG_MANIFEST_PLACEHOLDERS),
      uiProperty(PsAndroidModuleDefaultConfigDescriptors.multiDexEnabled, ::simplePropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_MODULES_DEFAULTCONFIG_MULTI_DEX_ENABLED),
      // TODO(b/123013466): [New PSD] Analytics for new PSD missing fields.
      uiProperty(PsAndroidModuleDefaultConfigDescriptors.resConfigs, ::listPropertyEditor, null),
      uiProperty(PsAndroidModuleDefaultConfigDescriptors.testInstrumentationRunner, ::simplePropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_MODULES_DEFAULTCONFIG_TEST_INSTRUMENTATION_RUNNER_CLASS_NAME),
      // TODO(b/123013466): [New PSD] Analytics for new PSD missing fields.
      uiProperty(PsAndroidModuleDefaultConfigDescriptors.testInstrumentationRunnerArguments, ::mapPropertyEditor, null),
      // TODO(b/123013466): [New PSD] Analytics for new PSD missing fields.
      uiProperty(PsAndroidModuleDefaultConfigDescriptors.testFunctionalTest, ::simplePropertyEditor, null),
      // TODO(b/123013466): [New PSD] Analytics for new PSD missing fields.
      uiProperty(PsAndroidModuleDefaultConfigDescriptors.testHandleProfiling, ::simplePropertyEditor, null),
      uiProperty(PsAndroidModuleDefaultConfigDescriptors.testApplicationId, ::simplePropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_MODULES_DEFAULTCONFIG_TEST_APPLICATION_ID),
      uiProperty(PsAndroidModuleDefaultConfigDescriptors.versionCode, ::simplePropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_MODULES_DEFAULTCONFIG_VERSION_CODE),
      uiProperty(PsAndroidModuleDefaultConfigDescriptors.versionName, ::simplePropertyEditor,
                 PSDEvent.PSDField.PROJECT_STRUCTURE_DIALOG_FIELD_MODULES_DEFAULTCONFIG_VERSION_NAME),
      // TODO(b/123013466): [New PSD] Analytics for new PSD missing fields.
      uiProperty(PsAndroidModuleDefaultConfigDescriptors.versionNameSuffix, ::simplePropertyEditor, null)))
