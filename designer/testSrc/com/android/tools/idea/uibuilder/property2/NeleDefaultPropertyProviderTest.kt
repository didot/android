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
package com.android.tools.idea.uibuilder.property2

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_TEXT_APPEARANCE
import com.android.SdkConstants.BUTTON
import com.android.SdkConstants.TEXT_VIEW
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.tools.idea.uibuilder.property2.testutils.PropertyTestCase
import com.google.common.truth.Truth.assertThat

class NeleDefaultPropertyProviderTest: PropertyTestCase() {

  fun testAttributeWithoutDefaultValue() {
    val components = createComponents(component(TEXT_VIEW))
    val property = createPropertyItem(ANDROID_URI, ATTR_TEXT_APPEARANCE, NelePropertyType.STYLE, components)
    val defaultProvider = NeleDefaultPropertyProvider(property.model.surface!!.currentSceneView!!.sceneManager)
    val value = defaultProvider.provideDefaultValue(property)
    assertThat(value).isNull()
  }

  fun testAttributeWithDefaultValue() {
    val components = createComponents(component(TEXT_VIEW))
    val property = createPropertyItem(ANDROID_URI, ATTR_TEXT_APPEARANCE, NelePropertyType.STYLE, components)
    val manager = getSceneManager(property)
    manager.putDefaultPropertyValue(components[0], ResourceNamespace.ANDROID, ATTR_TEXT_APPEARANCE, "?attr/textAppearanceSmall")
    val defaultProvider = NeleDefaultPropertyProvider(manager)
    val value = defaultProvider.provideDefaultValue(property)
    assertThat(value?.value).isEqualTo("?attr/textAppearanceSmall")
  }

  fun testMultipleComponentsWithDifferentDefaultValues() {
    val components = createComponents(component(TEXT_VIEW), component(BUTTON))
    val property = createPropertyItem(ANDROID_URI, ATTR_TEXT_APPEARANCE, NelePropertyType.STYLE, components)
    val manager = getSceneManager(property)
    manager.putDefaultPropertyValue(components[0], ResourceNamespace.ANDROID, ATTR_TEXT_APPEARANCE, "?attr/textAppearanceSmall")
    manager.putDefaultPropertyValue(components[1], ResourceNamespace.ANDROID, ATTR_TEXT_APPEARANCE, "?attr/textAppearanceLarge")
    val defaultProvider = NeleDefaultPropertyProvider(manager)
    val value = defaultProvider.provideDefaultValue(property)
    assertThat(value).isNull()
  }

  fun testMultipleComponentsWithSomeMissingDefaultValues() {
    val components = createComponents(component(TEXT_VIEW), component(BUTTON))
    val property = createPropertyItem(ANDROID_URI, ATTR_TEXT_APPEARANCE, NelePropertyType.STYLE, components)
    val manager = getSceneManager(property)
    manager.putDefaultPropertyValue(components[0], ResourceNamespace.ANDROID, ATTR_TEXT_APPEARANCE, "?attr/textAppearanceSmall")
    val defaultProvider = NeleDefaultPropertyProvider(manager)
    val value = defaultProvider.provideDefaultValue(property)
    assertThat(value).isNull()
  }

  fun testMultipleComponentsWithIdenticalDefaultValues() {
    val components = createComponents(component(TEXT_VIEW), component(BUTTON))
    val property = createPropertyItem(ANDROID_URI, ATTR_TEXT_APPEARANCE, NelePropertyType.STYLE, components)
    val manager = getSceneManager(property)
    manager.putDefaultPropertyValue(components[0], ResourceNamespace.ANDROID, ATTR_TEXT_APPEARANCE, "?attr/textAppearanceLarge")
    manager.putDefaultPropertyValue(components[1], ResourceNamespace.ANDROID, ATTR_TEXT_APPEARANCE, "?attr/textAppearanceLarge")
    val defaultProvider = NeleDefaultPropertyProvider(manager)
    val value = defaultProvider.provideDefaultValue(property)
    assertThat(value?.value).isEqualTo("?attr/textAppearanceLarge")
  }

  fun testMultipleComponentsWithOneMissingSnapshot() {
    val components = createComponents(component(TEXT_VIEW), component(BUTTON))
    val property = createPropertyItem(ANDROID_URI, ATTR_TEXT_APPEARANCE, NelePropertyType.STYLE, components)
    val manager = getSceneManager(property)
    manager.putDefaultPropertyValue(components[0], ResourceNamespace.ANDROID, ATTR_TEXT_APPEARANCE, "?attr/textAppearanceLarge")
    components[1].snapshot = null
    val defaultProvider = NeleDefaultPropertyProvider(manager)
    val value = defaultProvider.provideDefaultValue(property)
    assertThat(value).isNull()
  }
}
