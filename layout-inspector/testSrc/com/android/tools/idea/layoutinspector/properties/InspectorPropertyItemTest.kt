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
package com.android.tools.idea.layoutinspector.properties

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_BACKGROUND
import com.android.SdkConstants.ATTR_PADDING_TOP
import com.android.SdkConstants.ATTR_TEXT_SIZE
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.util.CheckUtil
import com.android.tools.idea.layoutinspector.util.DemoExample
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.Property.Type
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.ComponentStack
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito

@RunsInEdt
class InspectorPropertyItemTest {
  private var componentStack: ComponentStack? = null
  private var model: InspectorModel? = null
  private val projectRule = AndroidProjectRule.withSdk()

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  @Before
  fun setUp() {
    val project = projectRule.project
    model = model(project, DemoExample.setUpDemo(projectRule.fixture))
    componentStack = ComponentStack(project)
    componentStack!!.registerComponentInstance(FileEditorManager::class.java, Mockito.mock(FileEditorManager::class.java))
    val propertiesComponent = PropertiesComponentMock()
    projectRule.replaceService(PropertiesComponent::class.java, propertiesComponent)
    model!!.resourceLookup.dpi = 560
    PropertiesSettings.dimensionUnits = DimensionUnits.PIXELS
  }

  @After
  fun tearDown() {
    componentStack!!.restore()
    componentStack = null
    model = null
  }

  @Test
  fun testFormatDimensionInPixels() {
    assertThat(dimensionPropertyOf("").value).isEqualTo("")
    assertThat(dimensionPropertyOf("-1").value).isEqualTo("-1")
    assertThat(dimensionPropertyOf("-2147483648").value).isEqualTo("-2147483648")
    assertThat(dimensionPropertyOf("84").value).isEqualTo("84px")
    assertThat(dimensionPropertyOf("2168").value).isEqualTo("2168px")

    model!!.resourceLookup.dpi = -1
    assertThat(dimensionPropertyOf("").value).isEqualTo("")
    assertThat(dimensionPropertyOf("-1").value).isEqualTo("-1")
    assertThat(dimensionPropertyOf("-2147483648").value).isEqualTo("-2147483648")
    assertThat(dimensionPropertyOf("84").value).isEqualTo("84px")
    assertThat(dimensionPropertyOf("2168").value).isEqualTo("2168px")
  }

  @Test
  fun testFormatDimensionInDp() {
    PropertiesSettings.dimensionUnits = DimensionUnits.DP
    assertThat(dimensionPropertyOf("").value).isEqualTo("")
    assertThat(dimensionPropertyOf("-1").value).isEqualTo("-1")
    assertThat(dimensionPropertyOf("-2147483648").value).isEqualTo("-2147483648")
    assertThat(dimensionPropertyOf("84").value).isEqualTo("24dp")
    assertThat(dimensionPropertyOf("2168").value).isEqualTo("619dp")

    model!!.resourceLookup.dpi = -1
    assertThat(dimensionPropertyOf("").value).isEqualTo("")
    assertThat(dimensionPropertyOf("-1").value).isEqualTo("-1")
    assertThat(dimensionPropertyOf("-2147483648").value).isEqualTo("-2147483648")
    assertThat(dimensionPropertyOf("84").value).isEqualTo("84px")
    assertThat(dimensionPropertyOf("2168").value).isEqualTo("2168px")
  }

  @Test
  fun testFormatDimensionFloatInPixels() {
    assertThat(dimensionFloatPropertyOf("").value).isEqualTo("")
    assertThat(dimensionFloatPropertyOf("0").value).isEqualTo("0px")
    assertThat(dimensionFloatPropertyOf("0.5").value).isEqualTo("0.5px")
    assertThat(dimensionFloatPropertyOf("0.499999999").value).isEqualTo("0.5px")
    assertThat(dimensionFloatPropertyOf("0.1234567").value).isEqualTo("0.123px")

    model!!.resourceLookup.dpi = -1
    assertThat(dimensionFloatPropertyOf("").value).isEqualTo("")
    assertThat(dimensionFloatPropertyOf("0").value).isEqualTo("0px")
    assertThat(dimensionFloatPropertyOf("0.5").value).isEqualTo("0.5px")
    assertThat(dimensionFloatPropertyOf("0.499999999").value).isEqualTo("0.5px")
    assertThat(dimensionFloatPropertyOf("0.1234567").value).isEqualTo("0.123px")
  }

  @Test
  fun testFormatDimensionFloatInDp() {
    PropertiesSettings.dimensionUnits = DimensionUnits.DP
    assertThat(dimensionFloatPropertyOf("").value).isEqualTo("")
    assertThat(dimensionFloatPropertyOf("0").value).isEqualTo("0dp")
    assertThat(dimensionFloatPropertyOf("1.75").value).isEqualTo("0.5dp")
    assertThat(dimensionFloatPropertyOf("1.749").value).isEqualTo("0.5dp")
    assertThat(dimensionFloatPropertyOf("1.234567").value).isEqualTo("0.353dp")

    model!!.resourceLookup.dpi = -1
    assertThat(dimensionFloatPropertyOf("").value).isEqualTo("")
    assertThat(dimensionFloatPropertyOf("0").value).isEqualTo("0px")
    assertThat(dimensionFloatPropertyOf("0.5").value).isEqualTo("0.5px")
    assertThat(dimensionFloatPropertyOf("0.499999999").value).isEqualTo("0.5px")
    assertThat(dimensionFloatPropertyOf("0.1234567").value).isEqualTo("0.123px")
  }

  @Test
  fun testFormatTextSizeInPixels() {
    assertThat(textSizePropertyOf("").value).isEqualTo("")
    assertThat(textSizePropertyOf("49.0").value).isEqualTo("49.0px")
    model!!.resourceLookup.fontScale = 0.9f
    assertThat(textSizePropertyOf("44.0").value).isEqualTo("44.0px")

    model!!.resourceLookup.dpi = -1
    assertThat(textSizePropertyOf("").value).isEqualTo("")
    assertThat(textSizePropertyOf("49.0").value).isEqualTo("49.0px")
    model!!.resourceLookup.fontScale = 0.9f
    assertThat(textSizePropertyOf("44.0").value).isEqualTo("44.0px")
  }

  @Test
  fun testFormatTextSizeInSp() {
    PropertiesSettings.dimensionUnits = DimensionUnits.DP
    model!!.resourceLookup.fontScale = 1.0f
    assertThat(textSizePropertyOf("").value).isEqualTo("")
    assertThat(textSizePropertyOf("49.0").value).isEqualTo("14.0sp")
    model!!.resourceLookup.fontScale = 0.9f
    assertThat(textSizePropertyOf("44.0").value).isEqualTo("14.0sp")
    model!!.resourceLookup.fontScale = 1.3f
    assertThat(textSizePropertyOf("64.0").value).isEqualTo("14.1sp")

    model!!.resourceLookup.dpi = -1
    assertThat(textSizePropertyOf("").value).isEqualTo("")
    assertThat(textSizePropertyOf("49.0").value).isEqualTo("49.0px")
    model!!.resourceLookup.fontScale = 0.9f
    assertThat(textSizePropertyOf("44.0").value).isEqualTo("44.0px")
  }

  @Test
  fun testBrowseBackgroundInLayout() {
    val descriptor = browseProperty(ATTR_BACKGROUND, Type.DRAWABLE, null)
    assertThat(descriptor.file.name).isEqualTo("demo.xml")
    assertThat(CheckUtil.findLineAtOffset(descriptor.file, descriptor.offset))
      .isEqualTo("framework:background=\"@drawable/battery\"")
  }

  @Test
  fun testBrowseTextSizeFromTextAppearance() {
    val textAppearance = ResourceReference.style(ResourceNamespace.ANDROID, "TextAppearance.Material.Body1")
    val descriptor = browseProperty(ATTR_TEXT_SIZE, Type.INT32, textAppearance)
    assertThat(descriptor.file.name).isEqualTo("styles_material.xml")
    assertThat(CheckUtil.findLineAtOffset(descriptor.file, descriptor.offset))
      .isEqualTo("<item name=\"textSize\">@dimen/text_size_body_1_material</item>")
  }

  private fun dimensionPropertyOf(value: String?): InspectorPropertyItem {
    val node = model!!["title"]!!
    return InspectorPropertyItem(ANDROID_URI, ATTR_PADDING_TOP, Type.DIMENSION, value, PropertySection.DECLARED, null, node,
                                 model!!.resourceLookup)
  }

  private fun dimensionFloatPropertyOf(value: String?): InspectorPropertyItem {
    val node = model!!["title"]!!
    return InspectorPropertyItem(ANDROID_URI, ATTR_PADDING_TOP, Type.DIMENSION_FLOAT, value, PropertySection.DECLARED, null, node,
                                 model!!.resourceLookup)
  }

  private fun textSizePropertyOf(value: String?): InspectorPropertyItem {
    val node = model!!["title"]!!
    return InspectorPropertyItem(ANDROID_URI, ATTR_TEXT_SIZE, Type.DIMENSION_FLOAT, value, PropertySection.DECLARED, null, node,
                                 model!!.resourceLookup)
  }

  private fun browseProperty(attrName: String,
                             type: Type,
                             source: ResourceReference?): OpenFileDescriptor {
    val node = model!!["title"]!!
    val property = InspectorPropertyItem(
      ANDROID_URI, attrName, attrName, type, null, PropertySection.DECLARED, source ?: node.layout, node, model!!.resourceLookup)
    val fileManager = FileEditorManager.getInstance(projectRule.project)
    val file = ArgumentCaptor.forClass(OpenFileDescriptor::class.java)
    Mockito.`when`(fileManager.openEditor(ArgumentMatchers.any(OpenFileDescriptor::class.java), ArgumentMatchers.anyBoolean()))
      .thenReturn(listOf(Mockito.mock(FileEditor::class.java)))

    property.helpSupport.browse()
    Mockito.verify(fileManager).openEditor(file.capture(), ArgumentMatchers.eq(true))
    return file.value
  }
}
