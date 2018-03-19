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
package com.android.tools.idea.uibuilder.handlers.relative.targets

import com.android.SdkConstants
import com.android.tools.idea.common.command.NlWriteCommandAction
import com.android.tools.idea.common.model.AndroidDpCoordinate
import com.android.tools.idea.common.model.AttributesTransaction
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.Scene
import com.android.tools.idea.common.scene.target.DragBaseTarget
import com.android.tools.idea.common.scene.target.Target
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.intellij.openapi.util.text.StringUtil

/**
 * Target to handle the drag of RelativeLayout's children
 */
class RelativeDragTarget : DragBaseTarget() {
  private var myTargetX: BaseRelativeTarget? = null
  private var myTargetY: BaseRelativeTarget? = null

  /**
   * Calculate the align and margin on x axis.
   * This functions should only be used when there is no Notch on X axis
   */
  private fun updateMarginOnX(attributes: AttributesTransaction, @AndroidDpCoordinate x: Int) {
    val parent = myComponent.parent!!
    if (myComponent.drawX + myComponent.drawWidth / 2 < parent.drawX + parent.drawWidth / 2) {
      // near to left side
      attributes.setAndroidAttribute(SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_START, "true")
      attributes.setAndroidAttribute(SdkConstants.ATTR_LAYOUT_MARGIN_START,
          String.format(SdkConstants.VALUE_N_DP, Math.max(x - parent.drawX, 0)))
    }
    else {
      // near to right side
      attributes.setAndroidAttribute(SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_END, "true")
      attributes.setAndroidAttribute(SdkConstants.ATTR_LAYOUT_MARGIN_END,
          String.format(SdkConstants.VALUE_N_DP, Math.max(parent.drawWidth - (x - parent.drawX) - myComponent.drawWidth, 0)))
    }
  }

  /**
   * Calculate the align and margin on y axis.
   * This functions should only be used when there is no Notch on Y axis
   */
  private fun updateMarginOnY(attributes: AttributesTransaction, @AndroidDpCoordinate y: Int) {
    val parent = myComponent.parent!!
    if (myComponent.drawY + myComponent.drawHeight / 2 < parent.drawY + parent.drawHeight / 2) {
      // near to top side
      attributes.setAndroidAttribute(SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_TOP, "true")
      attributes.setAndroidAttribute(SdkConstants.ATTR_LAYOUT_MARGIN_TOP,
          String.format(SdkConstants.VALUE_N_DP, Math.max(y - parent.drawY, 0)))
    }
    else {
      // near to bottom side
      attributes.setAndroidAttribute(SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_BOTTOM, "true")
      attributes.setAndroidAttribute(SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM,
          String.format(SdkConstants.VALUE_N_DP, Math.max(parent.drawHeight - (y - parent.drawY) - myComponent.drawHeight, 0)))
    }
  }

  private fun clearAlignAttributes(attributes: AttributesTransaction) =
      ALIGNING_ATTRIBUTE_NAMES.map { attributes.removeAndroidAttribute(it) }

  override fun mouseDown(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int) {
    val parent = myComponent?.parent ?: return
    // Need to call this to update the targetsProvider when moving from one layout to another during a drag
    // but we should have a better scenario to recreate the targets
    (parent.scene.sceneManager as LayoutlibSceneManager).addTargets(myComponent)
    parent.updateTargets()

    super.mouseDown(x, y)
    myComponent.setModelUpdateAuthorized(true)
  }

  override fun mouseDrag(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int, closestTargets: List<Target>) {
    myComponent.isDragging = true
    val attributes = myComponent.authoritativeNlComponent.startAttributeTransaction()
    updateAttributes(attributes, x, y)
    attributes.apply()

    myTargetX?.myIsHighlight = false
    myTargetX = targetNotchSnapper.sappedHorizontalTarget as BaseRelativeTarget?
    myTargetX?.myIsHighlight = true

    myTargetY?.myIsHighlight = false
    myTargetY = targetNotchSnapper.sappedVerticalTarget as BaseRelativeTarget?
    myTargetY?.myIsHighlight = true
  }

  override fun mouseRelease(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int, closestTarget: List<Target>) {
    if (!myComponent.isDragging) return
    myComponent.isDragging = false

    if (myComponent.parent != null) {
      val component = myComponent.authoritativeNlComponent
      val attributes = component.startAttributeTransaction()
      updateAttributes(attributes, x, y)
      attributes.apply()

      if (!(Math.abs(x - myFirstMouseX) <= 1 && Math.abs(y - myFirstMouseY) <= 1)) {
        NlWriteCommandAction.run(component, "Dragged " + StringUtil.getShortName(component.tagName), { attributes.commit() })
      }
    }

    myComponent.updateTargets()

    myTargetX?.myIsHighlight = false
    myTargetY?.myIsHighlight = false

    if (myChangedComponent) {
      myComponent.scene.needsLayout(Scene.IMMEDIATE_LAYOUT)
    }
  }

  fun mouseRelease(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int, component: NlComponent) {
    myComponent.isDragging = false
    if (myComponent.parent != null) {
      val attributes = component.startAttributeTransaction()
      updateAttributes(attributes, x, y)
      attributes.apply()

      if (Math.abs(x - myFirstMouseX) > 1 || Math.abs(y - myFirstMouseY) > 1) {
        NlWriteCommandAction.run(component, "Dragged " + StringUtil.getShortName(component.tagName), { attributes.commit() })
      }
    }
    if (myChangedComponent) {
      myComponent.scene.needsLayout(Scene.IMMEDIATE_LAYOUT)
    }
  }

  override fun updateAttributes(attributes: AttributesTransaction, @AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int) {
    if (myComponent?.parent == null) return

    clearAlignAttributes(attributes)

    val sceneParent = myComponent?.parent ?: return

    val nx = Math.max(sceneParent.drawX, Math.min(x - myOffsetX, sceneParent.drawX + sceneParent.drawWidth - myComponent.drawWidth))
    val ny = Math.max(sceneParent.drawY, Math.min(y - myOffsetY, sceneParent.drawY + sceneParent.drawHeight - myComponent.drawHeight))
    val snappedX = targetNotchSnapper.trySnapHorizontal(nx)
    val snappedY = targetNotchSnapper.trySnapVertical(ny)

    myComponent.setPosition(snappedX.orElse(nx), snappedY.orElse(ny), false)

    targetNotchSnapper.applyNotches(attributes)
    if (!snappedX.isPresent) {
      updateMarginOnX(attributes, nx)
    }
    if (!snappedY.isPresent) {
      updateMarginOnY(attributes, ny)
    }

    if (attributes.getAndroidAttribute(SdkConstants.ATTR_LAYOUT_CENTER_HORIZONTAL) == "true"
        && attributes.getAndroidAttribute(SdkConstants.ATTR_LAYOUT_CENTER_VERTICAL) == "true") {
      attributes.removeAndroidAttribute(SdkConstants.ATTR_LAYOUT_CENTER_HORIZONTAL)
      attributes.removeAndroidAttribute(SdkConstants.ATTR_LAYOUT_CENTER_VERTICAL)
      attributes.setAndroidAttribute(SdkConstants.ATTR_LAYOUT_CENTER_IN_PARENT, "true")
    }
  }
}

private val ALIGNING_ATTRIBUTE_NAMES = arrayOf(
    SdkConstants.ATTR_LAYOUT_MARGIN,
    SdkConstants.ATTR_LAYOUT_MARGIN_LEFT,
    SdkConstants.ATTR_LAYOUT_MARGIN_START,
    SdkConstants.ATTR_LAYOUT_MARGIN_TOP,
    SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT,
    SdkConstants.ATTR_LAYOUT_MARGIN_END,
    SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM,
    SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_LEFT,
    SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_START,
    SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_TOP,
    SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_RIGHT,
    SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_END,
    SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_BOTTOM,
    SdkConstants.ATTR_LAYOUT_CENTER_HORIZONTAL,
    SdkConstants.ATTR_LAYOUT_CENTER_VERTICAL,
    SdkConstants.ATTR_LAYOUT_CENTER_IN_PARENT,
    SdkConstants.ATTR_LAYOUT_ALIGN_WITH_PARENT_MISSING,
    SdkConstants.ATTR_LAYOUT_ALIGN_BASELINE,
    SdkConstants.ATTR_LAYOUT_ALIGN_LEFT,
    SdkConstants.ATTR_LAYOUT_ALIGN_START,
    SdkConstants.ATTR_LAYOUT_ALIGN_TOP,
    SdkConstants.ATTR_LAYOUT_ALIGN_RIGHT,
    SdkConstants.ATTR_LAYOUT_ALIGN_END,
    SdkConstants.ATTR_LAYOUT_ALIGN_BOTTOM,
    SdkConstants.ATTR_LAYOUT_TO_LEFT_OF,
    SdkConstants.ATTR_LAYOUT_TO_START_OF,
    SdkConstants.ATTR_LAYOUT_ABOVE,
    SdkConstants.ATTR_LAYOUT_TO_RIGHT_OF,
    SdkConstants.ATTR_LAYOUT_TO_END_OF,
    SdkConstants.ATTR_LAYOUT_BELOW
)
