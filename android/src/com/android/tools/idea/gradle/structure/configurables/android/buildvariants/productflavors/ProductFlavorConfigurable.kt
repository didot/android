// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.gradle.structure.configurables.android.buildvariants.productflavors

import com.android.tools.idea.gradle.structure.configurables.ContainerConfigurable
import com.android.tools.idea.gradle.structure.configurables.android.ChildModelConfigurable
import com.android.tools.idea.gradle.structure.configurables.ui.*
import com.android.tools.idea.gradle.structure.configurables.ui.buildvariants.productflavors.ProductFlavorConfigPanel
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.PsBuildType
import com.android.tools.idea.gradle.structure.model.android.PsProductFlavor
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.intellij.openapi.ui.NamedConfigurable
import javax.swing.JComponent
import javax.swing.JPanel

class ProductFlavorConfigurable(private val productFlavor: PsProductFlavor)
  : ChildModelConfigurable<PsProductFlavor, ProductFlavorConfigPanel>(
  productFlavor) {
  override fun getBannerSlogan() = "Product Flavor '${productFlavor.name}'"
  override fun createPanel(): ProductFlavorConfigPanel = ProductFlavorConfigPanel(productFlavor)
}

class FlavorDimensionConfigurable(
    private val module: PsAndroidModule,
    val flavorDimension: String
) : NamedConfigurable<String>(), ContainerConfigurable<PsProductFlavor> {
  override fun getEditableObject(): String = flavorDimension
  override fun getBannerSlogan(): String = "Dimension '$flavorDimension'"
  override fun isModified(): Boolean = false
  override fun getDisplayName(): String = flavorDimension
  override fun apply() = Unit
  override fun setDisplayName(name: String?) = throw UnsupportedOperationException()

  override fun getChildren(): List<NamedConfigurable<PsProductFlavor>> =
    module
      .productFlavors
      .filter { productFlavor ->
        val dimension = productFlavor.dimension
        dimension is ParsedValue.Set.Parsed<String> && dimension.value == flavorDimension
      }
      .map { productFlavor ->
        ProductFlavorConfigurable(productFlavor)
      }


  private val component = JPanel()
  override fun createOptionsPanel(): JComponent = component
}

fun productFlavorPropertiesModel(isLibrary: Boolean) =
  PropertiesUiModel(
    listOfNotNull(
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.dimension, ::simplePropertyEditor),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.minSdkVersion, ::simplePropertyEditor),
      if (!isLibrary) uiProperty(PsProductFlavor.ProductFlavorDescriptors.applicationId, ::simplePropertyEditor) else null,
      if (!isLibrary) uiProperty(PsProductFlavor.ProductFlavorDescriptors.applicationIdSuffix, ::simplePropertyEditor) else null,
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.targetSdkVersion, ::simplePropertyEditor),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.maxSdkVersion, ::simplePropertyEditor),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.signingConfig, ::simplePropertyEditor),
      if (isLibrary) uiProperty(PsProductFlavor.ProductFlavorDescriptors.consumerProGuardFiles, ::listPropertyEditor) else null,
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.proGuardFiles, ::listPropertyEditor),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.manifestPlaceholders, ::mapPropertyEditor),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.multiDexEnabled, ::simplePropertyEditor),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.testInstrumentationRunner, ::simplePropertyEditor),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.testInstrumentationRunnerArguments, ::mapPropertyEditor),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.testApplicationId, ::simplePropertyEditor),
// TODO(b/70501607): Decide on PsProductFlavor.ProductFlavorDescriptors.testFunctionalTest,
// TODO(b/70501607): Decide on PsProductFlavor.ProductFlavorDescriptors.testHandleProfiling,
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.versionCode, ::simplePropertyEditor),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.versionName, ::simplePropertyEditor),
      uiProperty(PsProductFlavor.ProductFlavorDescriptors.matchingFallbacks, ::listPropertyEditor)))

