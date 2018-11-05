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
package com.android.tools.idea.common.scene.draw

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.scene.SceneContext
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.Line2D
import java.awt.geom.Point2D

/**
 * [DrawLine] draws a line between the specified endpoints.
 */
// TODO: Integrate with DisplayList.addLine
data class DrawLine(
  private val myLevel: Int,
  @SwingCoordinate private val myFrom: Point2D.Float,
  @SwingCoordinate private val myTo: Point2D.Float,
  private val myColor: Color,
  private val myStroke: BasicStroke
) : DrawCommandBase() {

  private constructor(sp: Array<String>) : this(sp[0].toInt(), stringToPoint2D(sp[1]), stringToPoint2D(sp[2]),
                                                stringToColor(sp[3]), stringToStroke(sp[4]))

  constructor(s: String) : this(parse(s, 5))

  override fun getLevel(): Int {
    return myLevel
  }

  override fun serialize(): String {
    return buildString(javaClass.simpleName, myLevel,
                       point2DToString(myFrom), point2DToString(myTo),
                       colorToString(myColor), strokeToString(myStroke))
  }

  override fun onPaint(g: Graphics2D, sceneContext: SceneContext) {
    g.color = myColor
    g.stroke = myStroke
    val line = Line2D.Float(myFrom, myTo)
    g.draw(line)
  }

  override fun toString() = "DrawLine[${myFrom.x}, ${myFrom.y}, ${myTo.x}, ${myTo.y}]"
}