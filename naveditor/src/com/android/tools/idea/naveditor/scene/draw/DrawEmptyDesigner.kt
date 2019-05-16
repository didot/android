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
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DrawCommand
import com.android.tools.idea.common.scene.draw.parse
import com.android.tools.idea.common.scene.draw.pointToString
import com.android.tools.idea.common.scene.draw.stringToPoint
import com.android.tools.idea.naveditor.scene.DRAW_FRAME_LEVEL
import com.android.tools.idea.naveditor.scene.NavColors.TEXT
import com.intellij.util.ui.JBUI
import icons.StudioIcons.NavEditor.Toolbar.ADD_DESTINATION
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Point

private val text1 = "Click "
private val text2 = " to add a destination"
@SwingCoordinate private val FONT_SIZE = JBUI.scale(13)
@SwingCoordinate private val VERTICAL_OFFSET = JBUI.scale(3)

class DrawEmptyDesigner(@SwingCoordinate private val point: Point) : DrawCommand {
  private constructor(tokens: Array<String>) : this(stringToPoint(tokens[0]))

  constructor(serialized: String) : this(parse(serialized, 1))

  override fun getLevel(): Int {
    return DRAW_FRAME_LEVEL
  }

  override fun paint(g: Graphics2D, sceneContext: SceneContext) {
    val g2 = g.create() as Graphics2D

    g2.color = TEXT
    g2.font = Font("Default", 0, JBUI.scale(FONT_SIZE))

    @SwingCoordinate var x = point.x
    @SwingCoordinate val y = point.y

    g2.drawString(text1, x, y)
    x += g2.fontMetrics.stringWidth(text1)

    ADD_DESTINATION.paintIcon(null, g2, x, y - ADD_DESTINATION.iconHeight + JBUI.scale(VERTICAL_OFFSET))
    x += ADD_DESTINATION.iconWidth

    g2.drawString(text2, x, y)
  }

  override fun serialize(): String {
    return com.android.tools.idea.common.scene.draw.buildString(javaClass.simpleName, pointToString(point))
  }
}