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
package com.android.tools.idea.naveditor.scene

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.ScenePicker
import com.android.tools.idea.naveditor.model.isFragment

/*
  Augments the hit region for destinations which support actions to include the action handle
 */
class NavActionSourceHitProvider : NavDestinationHitProvider() {
  override fun addHit(component: SceneComponent, sceneTransform: SceneContext, picker: ScenePicker) {
    super.addHit(component, sceneTransform, picker)

    val sceneView = sceneTransform.surface?.focusedSceneView ?: return
    @SwingCoordinate val drawRectangle = Coordinates.getSwingRectDip(sceneView, component.fillDrawRect2D(0, null))

    @SwingCoordinate var x = drawRectangle.x + drawRectangle.width
    if (component.nlComponent.isFragment) {
      x += sceneTransform.getSwingDimension(ACTION_HANDLE_OFFSET.toInt())
    }

    @SwingCoordinate val y = drawRectangle.y + drawRectangle.height / 2
    @SwingCoordinate val r = sceneTransform.getSwingDimensionDip(OUTER_RADIUS_LARGE.value)
    picker.addCircle(component, 0, x.toInt(), y.toInt(), r)
  }
}