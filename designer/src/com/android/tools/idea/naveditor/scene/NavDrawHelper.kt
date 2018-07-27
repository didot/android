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
package com.android.tools.idea.naveditor.scene

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.naveditor.model.NavCoordinate
import com.android.tools.idea.naveditor.scene.targets.ActionHandleTarget
import com.google.common.collect.ImmutableMap
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.BasicStroke.CAP_BUTT
import java.awt.BasicStroke.JOIN_ROUND

private const val DEFAULT_FONT_NAME = "Default"
private val DEFAULT_FONT_SIZE = JBUI.scale(12)

const val DRAW_BACKGROUND_LEVEL = 0
const val DRAW_FRAME_LEVEL = DRAW_BACKGROUND_LEVEL + 1
const val DRAW_ACTION_LEVEL = DRAW_FRAME_LEVEL + 1
const val DRAW_SCREEN_LABEL_LEVEL = DRAW_ACTION_LEVEL + 1
const val DRAW_ICON_LEVEL = DRAW_SCREEN_LABEL_LEVEL + 1
const val DRAW_NAV_SCREEN_LEVEL = DRAW_ICON_LEVEL + 1
const val DRAW_ACTIVITY_BORDER_LEVEL = DRAW_ICON_LEVEL + 1
const val DRAW_ACTION_HANDLE_BACKGROUND_LEVEL = DRAW_ACTIVITY_BORDER_LEVEL + 1
const val DRAW_ACTION_HANDLE_LEVEL = DRAW_ACTION_HANDLE_BACKGROUND_LEVEL + 1
const val DRAW_ACTION_HANDLE_DRAG_LEVEL = DRAW_ACTION_HANDLE_LEVEL + 1

private val HQ_RENDERING_HITS = ImmutableMap.of(
    RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON,
    RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY,
    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR
)

@SwingCoordinate private val ACTION_STROKE_WIDTH = JBUI.scale(3f)
@SwingCoordinate private val DASHED_STROKE_CYCLE = JBUI.scale(5f)

@JvmField
@NavCoordinate
val INNER_RADIUS_SMALL = JBUI.scale(5)
@JvmField
@NavCoordinate
val INNER_RADIUS_LARGE = JBUI.scale(8)
@JvmField
@NavCoordinate
val OUTER_RADIUS_SMALL = JBUI.scale(7)
@JvmField
@NavCoordinate
val OUTER_RADIUS_LARGE = JBUI.scale(11)

@SwingCoordinate val REGULAR_FRAME_THICKNESS = JBUI.scale(1)
@SwingCoordinate val HIGHLIGHTED_FRAME_THICKNESS = JBUI.scale(2)

@JvmField
@NavCoordinate
val SELF_ACTION_LENGTHS = intArrayOf(JBUI.scale(28), JBUI.scale(26), JBUI.scale(60), JBUI.scale(8))

@JvmField
val ACTION_STROKE = BasicStroke(ACTION_STROKE_WIDTH, CAP_BUTT, JOIN_ROUND)
@JvmField
val DASHED_ACTION_STROKE = BasicStroke(ACTION_STROKE_WIDTH, CAP_BUTT, JOIN_ROUND, DASHED_STROKE_CYCLE,
    floatArrayOf(DASHED_STROKE_CYCLE), DASHED_STROKE_CYCLE)

@JvmField
@NavCoordinate val FRAGMENT_BORDER_SPACING = JBUI.scale(2)
@JvmField
@NavCoordinate val ACTION_HANDLE_OFFSET = FRAGMENT_BORDER_SPACING + JBUI.scale(2)

fun frameColor(context: SceneContext, component: SceneComponent): Color {
  val colorSet = context.colorSet

  return when (component.drawState) {
    SceneComponent.DrawState.SELECTED -> colorSet.selectedFrames
    SceneComponent.DrawState.HOVER ->
      if (ActionHandleTarget.isDragCreateInProgress(component.nlComponent) && !component.id.isNullOrEmpty()) colorSet.selectedFrames
      else colorSet.highlightedFrames
    SceneComponent.DrawState.DRAG -> colorSet.highlightedFrames
    else -> colorSet.frames
  }
}

fun textColor(context: SceneContext, component: SceneComponent): Color {
  val colorSet = context.colorSet

  return if (component.isSelected) {
    colorSet.selectedText
  }
  else {
    colorSet.text
  }
}

fun actionColor(context: SceneContext, component: SceneComponent): Color {
  val colorSet = context.colorSet as NavColorSet

  return when {
    component.isSelected -> colorSet.selectedActions
    component.drawState == SceneComponent.DrawState.HOVER -> colorSet.highlightedActions
    else -> colorSet.actions
  }
}

fun scaledFont(context: SceneContext, style: Int): Font {
  val scale = context.scale
  val size = (scale * (2.0 - Math.min(scale, 1.0))) * DEFAULT_FONT_SIZE // keep font size slightly larger at smaller scales

  return Font(DEFAULT_FONT_NAME, style, size.toInt())
}

fun createDrawCommand(list: DisplayList, component: SceneComponent): DrawCommand {
  var level = DrawCommand.COMPONENT_LEVEL

  if (component.isDragging) {
    level = DrawCommand.TOP_LEVEL
  }
  else if (component.flatten().anyMatch { it.isSelected }) {
    level = DrawCommand.COMPONENT_SELECTED_LEVEL
  }

  return list.getCommand(level)
}

fun setRenderingHints(g: Graphics2D) {
  g.setRenderingHints(HQ_RENDERING_HITS)
}

fun frameThickness(component: SceneComponent): Int {
  return if (isHighlighted(component)) HIGHLIGHTED_FRAME_THICKNESS else REGULAR_FRAME_THICKNESS
}

fun isHighlighted(component: SceneComponent): Boolean {
  return when (component.drawState) {
    SceneComponent.DrawState.SELECTED, SceneComponent.DrawState.HOVER, SceneComponent.DrawState.DRAG -> true
    else -> false
  }
}

/**
 * Returns an array of five points representing the path of the self action
 * start: middle of right side of component
 * 1: previous point offset 28 to the left
 * 2: previous point offset to 26 below the bottom of component
 * 3: previous point shifted 60 to the right
 * end: previous point shifted up 8
 */
fun selfActionPoints(@SwingCoordinate start: Point, @SwingCoordinate end: Point, context: SceneContext): Array<Point> {
  val p1 = Point(start.x + context.getSwingDimension(SELF_ACTION_LENGTHS[0]), start.y)
  val p2 = Point(p1.x, end.y + context.getSwingDimension(SELF_ACTION_LENGTHS[3]))
  val p3 = Point(p2.x - context.getSwingDimension(SELF_ACTION_LENGTHS[2]), p2.y)
  return arrayOf(start, p1, p2, p3, end)
}