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

package com.android.tools.idea.ui.screenshot

import com.android.tools.adtui.ImageUtils
import java.awt.image.BufferedImage

class ScreenshotImage(val image: BufferedImage, val screenshotRotationQuadrants: Int, private val displayInfo: String)  {
  val width
    get() = image.width

  val height
    get() = image.height

  val isRoundDisplay: Boolean = displayInfo.contains("FLAG_ROUND")

  /**
   * Returns the rotated screenshot.
   */
  fun rotated(rotationQuadrants: Int): ScreenshotImage {
    if (rotationQuadrants == 0) {
      return this
    }
    return ScreenshotImage(
        ImageUtils.rotateByQuadrants(image, rotationQuadrants),
        (screenshotRotationQuadrants + rotationQuadrants) and 0x03,
        displayInfo)
  }
}
