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
package com.android.tools.idea.uibuilder.handlers.motion.property2

import com.android.SdkConstants
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.property2.api.PropertiesTable
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class MotionLayoutPropertyProviderTest : LayoutTestCase() {

  @Test
  fun testTransition() {
    val properties = getProperties(MotionSceneString.MotionSceneTransition)
    assertThat(properties.getByNamespace(SdkConstants.AUTO_URI).keys).containsExactly(
      "duration",
      "interpolator",
      "constraintSetStart",
      "constraintSetEnd",
      "staggered")
    assertThat(properties.getByNamespace(SdkConstants.ANDROID_URI).keys).containsExactly(
      "id")
    assertThat(properties[SdkConstants.AUTO_URI, "duration"].value).isEqualTo("2000")
  }

  @Test
  fun testConstraint() {
    val properties = getProperties(MotionSceneString.MotionSceneConstraintSet)
    assertThat(properties.getByNamespace(SdkConstants.AUTO_URI).keys).containsAllOf(
      "constraint_referenced_ids",
      "barrierDirection",
      "barrierAllowsGoneWidgets",
      "layout_constraintLeft_toLeftOf",
      "layout_constraintLeft_toRightOf")
    assertThat(properties.getByNamespace(SdkConstants.ANDROID_URI).keys).containsExactly(
      "id",
      "alpha",
      "elevation",
      "layout_width",
      "layout_marginBottom",
      "layout_marginEnd",
      "layout_marginLeft",
      "layout_marginRight",
      "layout_marginStart",
      "layout_marginTop",
      "layout_height",
      "maxHeight",
      "maxWidth",
      "minHeight",
      "minWidth",
      "orientation",
      "pivotX",
      "pivotY",
      "rotation",
      "rotationX",
      "rotationY",
      "scaleX",
      "scaleY",
      "transformPivotX",
      "transformPivotY",
      "translationX",
      "translationY",
      "translationZ",
      "visibility")
    assertThat(properties[SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH].value).isEqualTo("64dp")
  }

  @Test
  fun testKeyPosition() {
    val properties = getProperties(MotionSceneString.KeyTypePosition)
    assertThat(properties.getByNamespace(SdkConstants.AUTO_URI).keys).containsExactly(
      "framePosition",
      "target",
      "keyPositionType",
      "transitionEasing",
      "pathMotionArc",
      "percentX",
      "percentY",
      "curveFit",
      "drawPath",
      "sizePercent")
    assertThat(properties.getByNamespace(SdkConstants.ANDROID_URI).keys).isEmpty()
    assertThat(properties[SdkConstants.AUTO_URI, "framePosition"].value).isEqualTo("51")
    assertThat(properties[SdkConstants.AUTO_URI, "pathMotionArc"].value).isEqualTo("flip")
  }

  @Test
  fun testKeyAttribute() {
    val properties = getProperties(MotionSceneString.KeyTypeAttribute)
    assertThat(properties.getByNamespace(SdkConstants.AUTO_URI).keys).containsExactly(
      "framePosition",
      "target",
      "transitionEasing",
      "curveFit",
      "progress",
      "transitionPathRotate")
    assertThat(properties.getByNamespace(SdkConstants.ANDROID_URI).keys).containsExactly(
      "visibility",
      "alpha",
      "elevation",
      "rotation",
      "rotationX",
      "rotationY",
      "scaleX",
      "scaleY",
      "translationX",
      "translationY",
      "translationZ")
    assertThat(properties[SdkConstants.AUTO_URI, "framePosition"].value).isEqualTo("0")
    assertThat(properties[SdkConstants.ANDROID_URI, "rotation"].value).isEqualTo("0")
  }

  @Test
  fun testKeyCycle() {
    val properties = getProperties(MotionSceneString.KeyTypeCycle)
    assertThat(properties.getByNamespace(SdkConstants.AUTO_URI).keys).containsExactly(
      "framePosition",
      "target",
      "transitionEasing",
      "curveFit",
      "progress",
      "transitionPathRotate",
      "waveOffset",
      "wavePeriod",
      "waveShape",
      "waveVariesBy")
    assertThat(properties.getByNamespace(SdkConstants.ANDROID_URI).keys).containsExactly(
      "alpha",
      "elevation",
      "rotation",
      "rotationX",
      "rotationY",
      "scaleX",
      "scaleY",
      "translationX",
      "translationY",
      "translationZ")
    assertThat(properties[SdkConstants.AUTO_URI, "framePosition"].value).isEqualTo("15")
    assertThat(properties[SdkConstants.AUTO_URI, "transitionPathRotate"].value).isEqualTo("1.5")
  }

  @Test
  fun testKeyTimeCycle() {
    val properties = getProperties(MotionSceneString.KeyTypeTimeCycle)
    assertThat(properties.getByNamespace(SdkConstants.AUTO_URI).keys).containsExactly(
      "framePosition",
      "target",
      "transitionEasing",
      "curveFit",
      "progress",
      "transitionPathRotate",
      "waveOffset",
      "wavePeriod",
      "waveShape")
    assertThat(properties.getByNamespace(SdkConstants.ANDROID_URI).keys).containsExactly(
      "alpha",
      "elevation",
      "rotation",
      "rotationX",
      "rotationY",
      "scaleX",
      "scaleY",
      "translationX",
      "translationY",
      "translationZ")
    assertThat(properties[SdkConstants.AUTO_URI, "framePosition"].value).isEqualTo("25")
    assertThat(properties[SdkConstants.AUTO_URI, "transitionPathRotate"].value).isEqualTo("1.5")
  }

  private fun extractTag(tag: XmlTag, tagName: String): XmlTag? {
    if (tag.name == tagName) {
      return tag
    }
    return tag.subTags.mapNotNull { extractTag(it, tagName) }.firstOrNull()
  }

  private fun getProperties(tagName: String): PropertiesTable<NelePropertyItem> {
    // TODO: Pickup attrs.xml from the ConstraintLayout library
    myFixture.copyFileToProject("motion/attrs.xml", "res/values/attrs.xml")
    val file = myFixture.copyFileToProject("motion/scene.xml", "res/xml/scene.xml")
    val xmlFile = AndroidPsiUtils.getPsiFileSafely(project, file) as XmlFile
    val tag = extractTag(xmlFile.rootTag!!, tagName)!!
    val tagPointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(tag)
    val nlModel = createNlModel()
    val textView = nlModel.components[0].getChild(0)!!
    val provider = MotionLayoutPropertyProvider(myFacet)
    val model = MotionLayoutAttributesModel(testRootDisposable, myFacet)
    return provider.getProperties(model, tagPointer, listOf(textView))
  }

  private fun createNlModel(): SyncNlModel {
    val builder = model(
      "motion.xml",
      component(SdkConstants.MOTION_LAYOUT.newName())
        .withBounds(0, 0, 1000, 1500)
        .id("@id/motion")
        .matchParentWidth()
        .matchParentHeight()
        .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_CONTEXT, "com.example.MyActivity")
        .children(
          component(SdkConstants.TEXT_VIEW)
            .withBounds(100, 100, 100, 100)
            .id("@+id/widget")
            .width("wrap_content")
            .height("wrap_content")
        )
    )
    return builder.build()
  }
}
