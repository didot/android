/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.resource

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_TEXT_COLOR
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.properties.InspectorPropertiesModel
import com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem
import com.android.tools.idea.layoutinspector.properties.PropertySection
import com.android.tools.idea.res.RESOURCE_ICON_SIZE
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.Property.Type
import com.google.common.truth.Truth
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.JBUI
import org.junit.Rule
import org.junit.Test
import java.awt.Color

class ResourceLookupTest {

  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testSingleColorIcon() {
    val model = InspectorPropertiesModel()
    val inspectorModel = model.layoutInspector?.layoutInspectorModel
    val title = ViewNode(1, "TextView", null, 30, 60, 0, 0, 300, 100, null, "Hello Folks")
    val property = InspectorPropertyItem(ANDROID_URI, ATTR_TEXT_COLOR, ATTR_TEXT_COLOR, Type.COLOR, "#CC0000",
                                         PropertySection.DECLARED, null, title, inspectorModel?.resourceLookup)
    val lookup = ResourceLookup(projectRule.project)
    val icon = lookup.resolveAsIcon(property)
    Truth.assertThat(icon).isEqualTo(JBUI.scale(ColorIcon(RESOURCE_ICON_SIZE, Color(0xCC0000), false)))
  }
}