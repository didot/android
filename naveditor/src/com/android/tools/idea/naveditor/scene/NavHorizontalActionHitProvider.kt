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
package com.android.tools.idea.naveditor.scene

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.AndroidDpCoordinate
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.DefaultHitProvider
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.ScenePicker

class NavHorizontalActionHitProvider : DefaultHitProvider() {
  override fun addHit(component: SceneComponent, sceneTransform: SceneContext, picker: ScenePicker) {
    super.addHit(component, sceneTransform, picker)

    @AndroidDpCoordinate val componentRect = component.fillDrawRect2D(0, null)
    @SwingCoordinate val swingRect = Coordinates.getSwingRectDip(sceneTransform, componentRect)
    @SwingCoordinate val iconRect = getHorizontalActionIconRect(swingRect)

    picker.addRect(component, 0, iconRect.x.toInt(),
                   iconRect.y.toInt(),
                   (iconRect.x + iconRect.width).toInt(),
                   (iconRect.y + iconRect.height).toInt())
  }
}
