/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.animation.timeline

import com.android.tools.idea.compose.preview.animation.AnimatedProperty
import com.android.tools.idea.compose.preview.animation.ComposeUnit
import com.android.tools.idea.compose.preview.animation.InspectorLayout
import com.android.tools.idea.compose.preview.animation.InspectorPainter
import java.awt.Graphics2D

/**
 * Curves for all components of [AnimatedProperty].
 */
class PropertyCurve private constructor(
  state : ElementState,
  private val property: AnimatedProperty<Double>,
  private val componentCurves: List<ComponentCurve>,
  positionProxy: PositionProxy)
  : ParentTimelineElement(state, componentCurves, positionProxy) {

  companion object {
    fun create(state : ElementState, property: AnimatedProperty<Double>, rowMinY: Int, colorIndex : Int, positionProxy: PositionProxy) : PropertyCurve {
      val curves = property.components.mapIndexed { componentId, _ ->
        ComponentCurve.create(state, property, componentId,
                              rowMinY + componentId * InspectorLayout.timelineCurveRowHeightScaled(), positionProxy, colorIndex)
      }.toList()
      return PropertyCurve(state, property, curves, positionProxy)
    }
  }

  var timelineUnit: ComposeUnit.TimelineUnit? = null

  override fun paint(g: Graphics2D) {
    componentCurves.forEachIndexed { index, curve ->
      curve.paint(g)
      timelineUnit?.also {
        InspectorPainter.BoxedLabel.paintBoxedLabel(
          g, it, index, property.grouped,
          curve.boxedLabelPosition)
      }
    }
  }
}