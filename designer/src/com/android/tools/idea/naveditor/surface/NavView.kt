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
package com.android.tools.idea.naveditor.surface

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.uibuilder.model.Coordinates
import com.android.tools.idea.uibuilder.model.NlModel
import com.android.tools.idea.uibuilder.scene.Scene
import com.android.tools.idea.uibuilder.surface.SceneView

import java.awt.*

/**
 * View of a navigation editor [Scene], as part of a [NavDesignSurface].
 */
class NavView(surface: NavDesignSurface, model: NlModel) : SceneView(surface, model) {

  @SwingCoordinate
  override fun getPreferredSize(dimension: Dimension?): Dimension {
    val result = dimension ?: Dimension()

    result.height = Coordinates.dpToPx(this, surface.scene?.root?.drawHeight ?: 0)
    result.width = Coordinates.dpToPx(this, surface.scene?.root?.drawWidth ?: 0)
    return result
  }

  override fun getBgColor(): Color {
    return Color.WHITE
  }
}
