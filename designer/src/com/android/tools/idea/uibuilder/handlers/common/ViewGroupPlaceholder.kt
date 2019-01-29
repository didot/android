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
package com.android.tools.idea.uibuilder.handlers.common

import com.android.tools.idea.common.model.AndroidDpCoordinate
import com.android.tools.idea.common.model.NlAttributesHolder
import com.android.tools.idea.common.scene.Placeholder
import com.android.tools.idea.common.scene.Region
import com.android.tools.idea.common.scene.SceneComponent
import java.awt.Point

/**
 * The region of [ViewGroupPlaceholder] covered the area of all ViewGroups.
 */
class ViewGroupPlaceholder(host: SceneComponent) : Placeholder(host) {

  override val region = Region(host.drawX, host.drawY, host.drawX + host.drawWidth, host.drawY + host.drawHeight, host.depth)

  override fun snap(@AndroidDpCoordinate left: Int,
                    @AndroidDpCoordinate top: Int,
                    @AndroidDpCoordinate right: Int,
                    @AndroidDpCoordinate bottom: Int,
                    retPoint: Point)
    : Boolean {
    val centerX = (left + right) / 2
    val centerY = (top + bottom) / 2
    if (centerX in region.left..region.right && centerY in region.top..region.bottom) {
      retPoint.x = left
      retPoint.y = top
      return true
    }
    return false
  }

  override fun updateAttribute(sceneComponent: SceneComponent, attributes: NlAttributesHolder) = Unit
}
