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
package com.android.tools.idea.uibuilder.handlers.motion.property2

import com.android.SdkConstants
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs
import com.android.tools.idea.uibuilder.handlers.motion.property2.testutil.MotionAttributeRule
import com.google.common.truth.Truth
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class MotionLayoutPropertyProviderTest {

  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.withSdk()

  @JvmField
  @Rule
  val motionRule = MotionAttributeRule(projectRule, "layout.xml", "scene.xml")

  @JvmField
  @Rule
  val edtRule = EdtRule()

  @Test
  fun testTransition() {
    motionRule.selectTransition("start", "end")
    val properties = motionRule.properties.getValue(MotionSceneAttrs.Tags.TRANSITION)
    Truth.assertThat(properties.getByNamespace(SdkConstants.AUTO_URI).keys).containsExactly(
      "autoTransition",
      "duration",
      "motionInterpolator",
      "constraintSetStart",
      "constraintSetEnd",
      "staggered",
      "transitionDisable")
    Truth.assertThat(properties.getByNamespace(SdkConstants.ANDROID_URI).keys).containsExactly(
      "id")
    Truth.assertThat(properties[SdkConstants.AUTO_URI, "duration"].value).isEqualTo("2000")
  }

  @Test
  fun testConstraint() {
    motionRule.selectConstraint("start", "widget")
    val properties = motionRule.properties.getValue(MotionSceneAttrs.Tags.CONSTRAINT)
    Truth.assertThat(properties.getByNamespace(SdkConstants.AUTO_URI).keys).containsAllOf(
      "constraint_referenced_ids",
      "barrierDirection",
      "barrierAllowsGoneWidgets",
      "layout_constraintLeft_toLeftOf",
      "layout_constraintLeft_toRightOf")
    Truth.assertThat(properties.getByNamespace(SdkConstants.ANDROID_URI).keys).containsExactly(
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
    Truth.assertThat(properties[SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH].value)
      .isEqualTo("64dp")

    val customProperties = motionRule.properties.getValue(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE)
    Truth.assertThat(customProperties.getByNamespace(SdkConstants.AUTO_URI).keys).containsExactly(
      "textSize")
  }

  @Test
  fun testSectionedConstraint() {
    motionRule.selectConstraint("start", "button")
    val layoutProperties = motionRule.properties.getValue(MotionSceneAttrs.Tags.LAYOUT)
    Truth.assertThat(layoutProperties.getByNamespace(SdkConstants.AUTO_URI).keys).containsAllOf(
      "constraint_referenced_ids",
      "barrierDirection",
      "barrierAllowsGoneWidgets",
      "layout_constraintLeft_toLeftOf",
      "layout_constraintLeft_toRightOf")
    Truth.assertThat(layoutProperties.getByNamespace(SdkConstants.ANDROID_URI).keys).containsAllOf(
      "layout_width",
      "layout_marginBottom",
      "layout_marginEnd",
      "layout_marginLeft",
      "layout_marginRight",
      "layout_marginStart",
      "layout_marginTop",
      "layout_height")

    val motionProperties = motionRule.properties.getValue(MotionSceneAttrs.Tags.MOTION)
    Truth.assertThat(motionProperties.getByNamespace(SdkConstants.AUTO_URI).keys).containsAllOf(
      "animate_relativeTo",
      "transitionEasing",
      "pathMotionArc",
      "motionPathRotate",
      "motionStagger",
      "drawPath")
    Truth.assertThat(motionProperties.getByNamespace(SdkConstants.ANDROID_URI).keys).isEmpty()

    val propertySetProperties = motionRule.properties.getValue(MotionSceneAttrs.Tags.PROPERTY_SET)
    Truth.assertThat(propertySetProperties.getByNamespace(SdkConstants.AUTO_URI).keys).containsAllOf(
      "visibilityMode",
      "motionProgress",
      "layout_constraintTag")
    Truth.assertThat(propertySetProperties.getByNamespace(SdkConstants.ANDROID_URI).keys).containsAllOf(
      "alpha",
      "visibility")

    val customProperties = motionRule.properties.getValue(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE)
    Truth.assertThat(customProperties.getByNamespace(SdkConstants.AUTO_URI).keys).containsExactly(
      "textSize")
  }

  @Test
  fun testKeyPosition() {
    motionRule.selectKeyFrame("start", "end", MotionSceneAttrs.Tags.KEY_POSITION, 51, "widget")
    val properties = motionRule.properties.getValue(MotionSceneAttrs.Tags.KEY_POSITION)
    Truth.assertThat(properties.getByNamespace(SdkConstants.AUTO_URI).keys).containsExactly(
      "framePosition",
      "motionTarget",
      "keyPositionType",
      "transitionEasing",
      "pathMotionArc",
      "percentX",
      "percentY",
      "percentHeight",
      "percentWidth",
      "curveFit",
      "drawPath",
      "sizePercent")
    Truth.assertThat(properties.getByNamespace(SdkConstants.ANDROID_URI).keys).isEmpty()
    Truth.assertThat(properties[SdkConstants.AUTO_URI, "framePosition"].value).isEqualTo("51")
    Truth.assertThat(properties[SdkConstants.AUTO_URI, "pathMotionArc"].value).isEqualTo("flip")
  }

  @Test
  fun testKeyAttribute() {
    motionRule.selectKeyFrame("start", "end", MotionSceneAttrs.Tags.KEY_ATTRIBUTE, 99, "widget")
    val properties = motionRule.properties.getValue(MotionSceneAttrs.Tags.KEY_ATTRIBUTE)
    Truth.assertThat(properties.getByNamespace(SdkConstants.AUTO_URI).keys).containsExactly(
      "framePosition",
      "motionTarget",
      "transitionEasing",
      "curveFit",
      "motionProgress",
      "transitionPathRotate")
    Truth.assertThat(properties.getByNamespace(SdkConstants.ANDROID_URI).keys).containsExactly(
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
    Truth.assertThat(properties[SdkConstants.AUTO_URI, "framePosition"].value).isEqualTo("99")
    Truth.assertThat(properties[SdkConstants.ANDROID_URI, "rotation"].value).isEqualTo("1")
  }

  @Test
  fun testKeyCycle() {
    motionRule.selectKeyFrame("start", "end", MotionSceneAttrs.Tags.KEY_CYCLE, 15, "widget")
    val properties = motionRule.properties.getValue(MotionSceneAttrs.Tags.KEY_CYCLE)
    Truth.assertThat(properties.getByNamespace(SdkConstants.AUTO_URI).keys).containsExactly(
      "framePosition",
      "motionTarget",
      "transitionEasing",
      "curveFit",
      "motionProgress",
      "transitionPathRotate",
      "waveOffset",
      "wavePeriod",
      "waveShape",
      "waveVariesBy")
    Truth.assertThat(properties.getByNamespace(SdkConstants.ANDROID_URI).keys).containsExactly(
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
    Truth.assertThat(properties[SdkConstants.AUTO_URI, "framePosition"].value).isEqualTo("15")
    Truth.assertThat(properties[SdkConstants.AUTO_URI, "transitionPathRotate"].value).isEqualTo("1.5")
  }

  @Test
  fun testKeyTimeCycle() {
    motionRule.selectKeyFrame("start", "end", MotionSceneAttrs.Tags.KEY_TIME_CYCLE, 25, "widget")
    val properties = motionRule.properties.getValue(MotionSceneAttrs.Tags.KEY_TIME_CYCLE)
    Truth.assertThat(properties.getByNamespace(SdkConstants.AUTO_URI).keys).containsExactly(
      "framePosition",
      "motionTarget",
      "transitionEasing",
      "curveFit",
      "motionProgress",
      "transitionPathRotate",
      "waveOffset",
      "waveDecay",
      "wavePeriod",
      "waveShape")
    Truth.assertThat(properties.getByNamespace(SdkConstants.ANDROID_URI).keys).containsExactly(
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
    Truth.assertThat(properties[SdkConstants.AUTO_URI, "framePosition"].value).isEqualTo("25")
    Truth.assertThat(properties[SdkConstants.AUTO_URI, "transitionPathRotate"].value).isEqualTo("1.5")
  }
}