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
package com.android.tools.idea.uibuilder.property2.testutils

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceValueImpl
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.resources.ResourceType
import com.android.tools.idea.common.property2.api.EnumValue
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.google.common.truth.Truth
import com.intellij.openapi.diagnostic.Logger

object EnumValueUtil {
  /**
   * Checks a section of [EnumValue]s.
   *
   * @param values the list of [EnumValue] to check
   * @param startIndex check the section starting at this index
   * @param expectedHeader the expected header of this section. Must be in the first value.
   * @param expectedCount the expected count in this section. A negative value indicates a minimum count.
   * @param expectedValues the expected values (may be a subset of the complete section)
   * @param expectedDisplayValues the expected display values (may be a subset of the complete section)
   */
  fun checkSection(values: List<EnumValue>, startIndex: Int,
                   expectedHeader: String, expectedCount: Int, expectedValues: List<String>, expectedDisplayValues: List<String>): Int {
    Truth.assertThat(startIndex).isAtLeast(0)
    Truth.assertThat(startIndex).isAtMost(values.lastIndex)
    var nextSectionIndex = values.size
    for (index in startIndex..values.lastIndex) {
      val enum = values[index]
      if (index == startIndex) {
        Truth.assertThat(enum.header).isEqualTo(expectedHeader)
      }
      else if (enum.header.isNotEmpty()) {
        nextSectionIndex = index
        break
      }
      val valueIndex = index - startIndex
      if (expectedValues.size > valueIndex) {
        Truth.assertThat(enum.value).isEqualTo(expectedValues[valueIndex])
      }
      if (expectedDisplayValues.size > valueIndex) {
        Truth.assertThat(enum.display).isEqualTo(expectedDisplayValues[valueIndex])
      }
    }
    if (expectedCount >= 0) {
      Truth.assertThat(nextSectionIndex - startIndex).named("Expected Style Count").isEqualTo(expectedCount)
    }
    else {
      Truth.assertThat(nextSectionIndex - startIndex).named("Expected Style Count").isAtLeast(Math.abs(expectedCount))
    }
    return if (nextSectionIndex < values.size) nextSectionIndex else -1
  }

  // Hack to set the libraryName field on the AppCompat styles added for this test.
  // When AppCompat has its own namespace this can be removed.
  fun patchLibraryNameOfAllAppCompatStyles(property: NelePropertyItem) {
    val resolver = property.resolver ?: error("Resolver should not be null")
    @Suppress("DEPRECATION")
    val resources = resolver.projectResources[ResourceType.STYLE] ?: return
    val libraryNameField = ResourceValueImpl::class.java.getDeclaredField("libraryName")
    libraryNameField.isAccessible = true

    for (style in resources.values) {
      if (style is StyleResourceValue && style.name.contains("AppCompat")) {
        libraryNameField.set(style, SdkConstants.APPCOMPAT_LIB_ARTIFACT)

        // Temporary dump the styles generated in order to help with fixing b/80518128
        Logger.getInstance(EnumValueUtil::class.java).warn("identified as AppCompat style: $style")
      }
    }
  }
}
