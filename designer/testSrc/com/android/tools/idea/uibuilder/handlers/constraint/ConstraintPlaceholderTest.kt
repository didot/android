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
package com.android.tools.idea.uibuilder.handlers.constraint

import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.applyPlaceholderToSceneComponent
import com.android.tools.idea.uibuilder.scene.SceneTest
import java.awt.Point

class ConstraintPlaceholderTest : SceneTest() {

  override fun setUp() {
    super.setUp()
    StudioFlags.NELE_DRAG_PLACEHOLDER.override(true)
  }

  override fun tearDown() {
    StudioFlags.NELE_DRAG_PLACEHOLDER.clearOverride()
    super.tearDown()
  }

  fun testRegion() {
    val constraint = myScene.getSceneComponent("constraint")!!
    val placeholder = ConstraintPlaceholder(constraint)

    val region = placeholder.region
    assertEquals(constraint.drawX, region.left)
    assertEquals(constraint.drawY, region.top)
    assertEquals(constraint.drawX + constraint.drawWidth, region.right)
    assertEquals(constraint.drawY + constraint.drawHeight, region.bottom)
  }

  fun testSnapFailed() {
    val constraint = myScene.getSceneComponent("constraint")!!

    val placeholder = ConstraintPlaceholder(constraint)

    val left = constraint.drawX - 30
    val top = constraint.drawY - 30

    val p = Point(-1, -1)
    val snappedResult = placeholder.snap(left, top, left + 10, top + 10, p)
    assertFalse(snappedResult)
    assertEquals(-1, p.x)
    assertEquals(-1, p.y)
  }

  fun testSnapSucceed() {
    val constraint = myScene.getSceneComponent("constraint")!!

    val placeholder = ConstraintPlaceholder(constraint)

    val left = constraint.drawX + 10
    val top = constraint.drawY + 10

    val p = Point(-1, -1)
    val snappedResult = placeholder.snap(left, top, left + 10, top + 10, p)
    assertTrue(snappedResult)
    assertEquals(left, p.x)
    assertEquals(top, p.y)
  }

  fun testApply() {
    val constraint = myScene.getSceneComponent("constraint")!!
    val textView = myScene.getSceneComponent("textView")!!

    val placeholder = ConstraintPlaceholder(constraint)

    val mouseX = constraint.drawX + 50
    val mouseY = constraint.drawY + 60
    textView.setPosition(mouseX, mouseY)

    mySceneManager.update()

    applyPlaceholderToSceneComponent(textView, placeholder)

    assertEquals("50dp", textView.nlComponent.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X))
    assertEquals("60dp", textView.nlComponent.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y))
  }

  fun testDraggingComponentOutsideWillRemoveAllConstraintLayoutAttributes() {
    val linearLayout = myScreen.get("@id/linear").sceneComponent!!
    val textView2 = myScreen.get("@id/textView2").sceneComponent!!

    textView2.nlComponent.let {
      assertEquals("parent", it.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF))
      assertEquals("linear", it.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF))
      assertEquals("0.632", it.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS))
      assertEquals("0dp", it.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X))
    }

    myInteraction.select("textView2", true)
    myInteraction.mouseDown("textView2")
    myInteraction.mouseRelease("linear")

    val textView2AfterDrag = myScreen.get("@id/textView2").sceneComponent!!
    assertEquals(textView2, textView2AfterDrag)

    assertEquals(1, linearLayout.childCount)
    assertEquals(textView2AfterDrag, linearLayout.getChild(0))

    textView2AfterDrag.nlComponent.let {
      assertNull(it.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF))
      assertNull(it.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF))
      assertNull(it.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS))
      assertNull(it.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X))
    }
  }

  override fun createModel(): ModelBuilder {
    return model("constraint.xml",
                 component(SdkConstants.CONSTRAINT_LAYOUT.newName())
                   .withBounds(0, 0, 1000, 1000)
                   .id("@id/constraint")
                   .matchParentWidth()
                   .matchParentHeight()
                   .children(
                     component(SdkConstants.TEXT_VIEW)
                       .withBounds(0, 0, 200, 200)
                       .id("@id/textView")
                       .width("100dp")
                       .height("100dp"),
                     component(SdkConstants.TEXT_VIEW)
                       .withBounds(200, 0, 200, 200)
                       .id("@id/textView2")
                       .width("100dp")
                       .height("100dp")
                       .withAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF, "parent")
                       .withAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF, "linear")
                       .withAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS, "0.632")
                       .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, "0dp"),
                     component(SdkConstants.LINEAR_LAYOUT)
                       .withBounds(200, 200, 800, 800)
                       .id("@id/linear")
                       .width("400dp")
                       .height("400dp")
                       .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, "100dp")
                       .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, "100dp")
                   )
    )
  }
}
