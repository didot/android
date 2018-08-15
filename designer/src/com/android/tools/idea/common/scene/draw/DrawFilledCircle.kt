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
import com.android.tools.idea.common.scene.LerpInt
import com.android.tools.idea.common.scene.SceneContext
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Point

class DrawFilledCircle(private val level: Int,
                       @SwingCoordinate private val center: Point,
                       private val color: Color,
                       @SwingCoordinate private val radius: LerpInt) : DrawCommandBase() {

  constructor(myLevel: Int,
              @SwingCoordinate myCenter: Point,
              myColor: Color,
              @SwingCoordinate radius: Int) : this(myLevel, myCenter, myColor, LerpInt(radius))

  private constructor(sp: Array<String>) : this(sp[0].toInt(), stringToPoint(sp[1]),
      stringToColor(sp[2]), stringToLerp(sp[3]))

  constructor(s: String) : this(parse(s, 4))

  override fun getLevel(): Int {
    return level
  }

  override fun serialize(): String {
    return buildString(javaClass.simpleName,
        level,
        pointToString(center),
        colorToString(color),
        lerpToString(radius))
  }

  override fun onPaint(g: Graphics2D, sceneContext: SceneContext) {
    val r = radius.getValue(sceneContext.time)

    g.color = color
    g.fillOval(center.x - r, center.y - r, 2 * r, 2 * r)

    if (radius.isComplete(sceneContext.time)) {
      sceneContext.repaint()
    }
  }
}