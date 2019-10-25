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
package com.android.tools.idea.naveditor.scene.draw

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.Scale
import com.android.tools.idea.common.model.toScale
import com.android.tools.idea.common.scene.draw.CompositeDrawCommand
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.common.scene.draw.DrawCommand.COMPONENT_LEVEL
import com.android.tools.idea.common.scene.draw.DrawShape
import com.android.tools.idea.common.scene.draw.buildString
import com.android.tools.idea.common.scene.draw.colorToString
import com.android.tools.idea.common.scene.draw.parse
import com.android.tools.idea.common.scene.draw.rect2DToString
import com.android.tools.idea.common.scene.draw.stringToColor
import com.android.tools.idea.common.scene.draw.stringToRect2D
import com.android.tools.idea.naveditor.scene.ACTION_STROKE
import com.android.tools.idea.naveditor.scene.ArrowDirection
import com.android.tools.idea.naveditor.scene.NavSceneManager.ACTION_ARROW_PARALLEL
import com.android.tools.idea.naveditor.scene.getHorizontalActionIconRect
import com.android.tools.idea.naveditor.scene.makeDrawArrowCommand
import java.awt.Color
import java.awt.geom.Line2D
import java.awt.geom.Rectangle2D

data class DrawHorizontalAction(@SwingCoordinate private val rectangle: Rectangle2D.Float,
                                private val scale: Scale,
                                private val color: Color,
                                private val isPopAction: Boolean) : CompositeDrawCommand(COMPONENT_LEVEL) {
  private constructor(tokens: Array<String>)
    : this(stringToRect2D(tokens[0]), tokens[1].toScale(), stringToColor(tokens[2]), tokens[3].toBoolean())

  constructor(serialized: String) : this(parse(serialized, 4))

  override fun serialize(): String = buildString(javaClass.simpleName, rect2DToString(rectangle),
                                                 scale, colorToString(color), isPopAction)

  override fun buildCommands(): List<DrawCommand> {
    val arrowWidth = ACTION_ARROW_PARALLEL * scale.value.toFloat()
    val lineLength = Math.max(0f, rectangle.width - arrowWidth)

    val x1 = rectangle.x
    val x2 = x1 + lineLength
    val y = rectangle.centerY.toFloat()

    val drawLine = DrawShape(Line2D.Float(x1, y, x2, y), color, ACTION_STROKE)

    val arrowRect = Rectangle2D.Float(x2, rectangle.y, arrowWidth, rectangle.height)
    val drawArrow = makeDrawArrowCommand(arrowRect, ArrowDirection.RIGHT, color)

    val list = mutableListOf(drawLine, drawArrow)

    if (isPopAction) {
      val iconRect = getHorizontalActionIconRect(rectangle)
      val drawIcon = DrawIcon(iconRect, DrawIcon.IconType.POP_ACTION, color)
      list.add(drawIcon)
    }

    return list
  }
}