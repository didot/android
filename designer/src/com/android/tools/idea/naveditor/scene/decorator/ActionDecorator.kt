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
package com.android.tools.idea.naveditor.scene.decorator

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.decorator.SceneDecorator
import com.android.tools.idea.common.scene.draw.ArrowDirection
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.draw.DrawArrow
import com.android.tools.idea.common.scene.draw.DrawLine
import com.android.tools.idea.naveditor.model.ActionType
import com.android.tools.idea.naveditor.model.actionType
import com.android.tools.idea.naveditor.scene.ACTION_STROKE
import com.android.tools.idea.naveditor.scene.DRAW_ACTION_LEVEL
import com.android.tools.idea.naveditor.scene.NavSceneManager
import com.android.tools.idea.naveditor.scene.actionColor
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D

/**
 * [ActionDecorator] responsible for creating draw commands for actions.
 */

object ActionDecorator : SceneDecorator() {
  override fun addBackground(list: DisplayList, sceneContext: SceneContext, component: SceneComponent) {
  }

  override fun addContent(list: DisplayList, time: Long, sceneContext: SceneContext, component: SceneComponent) {
    // TODO: Add support for other action types
    when (component.nlComponent.actionType) {
      ActionType.GLOBAL, ActionType.EXIT -> {
        val color = actionColor(sceneContext, component)

        val view = component.scene.designSurface.currentSceneView ?: return

        @SwingCoordinate val drawRect = Coordinates.getSwingRectDip(view, component.fillDrawRect2D(0, null))
        @SwingCoordinate val x1 = drawRect.x
        @SwingCoordinate val x2 = x1 + drawRect.width - Coordinates.getSwingDimension(view, NavSceneManager.ACTION_ARROW_PARALLEL)
        @SwingCoordinate val y = drawRect.y + drawRect.height / 2
        list.add(DrawLine(DRAW_ACTION_LEVEL, Point2D.Float(x1, y), Point2D.Float(x2, y), color, ACTION_STROKE))

        val arrowRect = Rectangle2D.Float()
        arrowRect.x = x2
        arrowRect.y = drawRect.y
        arrowRect.width = Coordinates.getSwingDimension(view, NavSceneManager.ACTION_ARROW_PARALLEL)
        arrowRect.height = drawRect.height
        list.add(DrawArrow(DRAW_ACTION_LEVEL, ArrowDirection.RIGHT, arrowRect, color))
      }
    }
  }

  override fun addFrame(list: DisplayList, sceneContext: SceneContext, component: SceneComponent) {
  }
}