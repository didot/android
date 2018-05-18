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
package com.android.tools.idea.uibuilder.property2.inspector

import com.android.SdkConstants.*
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property2.NelePropertyType
import com.android.tools.idea.uibuilder.property2.testutils.InspectorTestUtil
import com.android.tools.idea.uibuilder.property2.testutils.LineType
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.runInEdtAndWait
import org.junit.Rule
import org.junit.Test

class AdvancedInspectorBuilderTest {
  @JvmField @Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testAdvancedInspector() {
    runInEdtAndWait {
      val util = InspectorTestUtil(projectRule, TEXT_VIEW, LINEAR_LAYOUT)
      addProperties(util)
      val builder = AdvancedInspectorBuilder()
      builder.attachToInspector(util.inspector, util.properties)
      assertThat(util.inspector.lines).hasSize(4)
      assertThat(util.inspector.lines[0].type).isEqualTo(LineType.TITLE)
      assertThat(util.inspector.lines[1].type).isEqualTo(LineType.TABLE)
      assertThat(util.inspector.lines[2].type).isEqualTo(LineType.TITLE)
      assertThat(util.inspector.lines[3].type).isEqualTo(LineType.TABLE)

      assertThat(util.inspector.lines[0].title).isEqualTo("Declared Attributes")
      assertThat(util.inspector.lines[0].expandable).isTrue()
      assertThat(util.inspector.lines[2].title).isEqualTo("All Attributes")
      assertThat(util.inspector.lines[2].expandable).isTrue()

      // Check the 3 declared attributes:
      assertThat(util.inspector.lines[1].tableModel?.items?.map { it.name })
        .containsExactly(ATTR_LAYOUT_WIDTH, ATTR_LAYOUT_HEIGHT, ATTR_TEXT).inOrder()

      // Check all 6 attributes:
      assertThat(util.inspector.lines[3].tableModel?.items?.map { it.name })
        .containsExactly(ATTR_LAYOUT_WIDTH, ATTR_LAYOUT_HEIGHT, ATTR_CONTENT_DESCRIPTION,
                         ATTR_TEXT, ATTR_TEXT_COLOR, ATTR_TEXT_SIZE).inOrder()
    }
  }

  private fun addProperties(util: InspectorTestUtil) {
    util.addProperty(ANDROID_URI, ATTR_TEXT, NelePropertyType.STRING)
    util.addProperty(ANDROID_URI, ATTR_TEXT_SIZE, NelePropertyType.FONT_SIZE)
    util.addProperty(ANDROID_URI, ATTR_TEXT_COLOR, NelePropertyType.COLOR)
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_WIDTH, NelePropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_LAYOUT_HEIGHT, NelePropertyType.DIMENSION)
    util.addProperty(ANDROID_URI, ATTR_CONTENT_DESCRIPTION, NelePropertyType.STRING)

    util.properties[ANDROID_URI, ATTR_TEXT].value = "Testing"
    util.properties[ANDROID_URI, ATTR_LAYOUT_WIDTH].value = VALUE_WRAP_CONTENT
    util.properties[ANDROID_URI, ATTR_LAYOUT_HEIGHT].value = VALUE_WRAP_CONTENT
  }
}
