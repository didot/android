/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.emulator

import com.android.emulator.control.Rotation.SkinRotation
import com.android.tools.adtui.ImageUtils
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage

/**
 * Layout of the device frame and mask for a particular display orientation.
 *
 * @param displaySize the size of the display
 * @param frameRectangle the frame boundary rectangle relative to the display
 * @param frameImages the images constituting the device frame
 * @param maskImages the images constituting the device display mask
 */
class SkinLayout(val displaySize: Dimension, val frameRectangle: Rectangle,
                 val frameImages: List<AnchoredImage>, val maskImages: List<AnchoredImage>) {

  /**
   * Creates a layout without a frame or mask.
   */
  constructor(displaySize: Dimension) : this(displaySize, Rectangle(0, 0, displaySize.width, displaySize.height), emptyList(), emptyList())

  /**
   * Draws frame and mask to the given graphics context. The [displayX] and [displayY] parameters
   * define the coordinates of the top left corner of the display rectangle.
   */
  fun drawFrameAndMask(displayX: Int, displayY: Int, g: Graphics2D) {
    if (frameImages.isNotEmpty() || maskImages.isNotEmpty()) {
      val transform = AffineTransform()
      // Draw frame.
      for (image in frameImages) {
        drawImage(image, displayX, displayY, transform, g)
      }
      for (image in maskImages) {
        drawImage(image, displayX, displayY, transform, g)
      }
    }
  }

  private fun drawImage(anchoredImage: AnchoredImage, displayX: Int, displayY: Int, transform: AffineTransform, g: Graphics2D) {
    val x = displayX + anchoredImage.anchorPoint.x * displaySize.width + anchoredImage.offset.x
    val y = displayY + anchoredImage.anchorPoint.y * displaySize.height + anchoredImage.offset.y
    transform.setToTranslation(x.toDouble(), y.toDouble())
    g.drawImage(anchoredImage.image, transform, null)
  }
}

/**
 * Image attached to a rectangle.
 *
 * @param image the graphic image
 * @param size the size of the image
 * @param anchorPoint the point on the boundary of the display rectangle the image is attached to
 * @param offset the offset of the upper left corner of the image relative to the anchor point
 */
class AnchoredImage(val image: BufferedImage, val size: Dimension, val anchorPoint: AnchorPoint, val offset: Point) {
  /**
   * Creates another [AnchoredImage] that is result of rotating and scaling this one.
   *
   * @param rotation the rotation that is applied to the image and the display rectangle
   * @param scaleX the X-axis scale factor applied to the rotated image
   * @param scaleY the Y-axis scale factor applied to the rotated image
   */
  fun rotatedAndScaled(rotation: SkinRotation, scaleX: Double, scaleY: Double): AnchoredImage {
    val rotatedSize = size.rotated(rotation)
    val width = rotatedSize.width.scaled(scaleX)
    val height = rotatedSize.height.scaled(scaleY)
    val rotatedAnchorPoint = anchorPoint.rotated(rotation)
    val rotatedOffset = offset.rotated(rotation)
    val transformedOffset =
      when (rotation) {
        SkinRotation.LANDSCAPE -> Point(rotatedOffset.x.scaled(scaleX), rotatedOffset.y.scaled(scaleY) - height)
        SkinRotation.REVERSE_PORTRAIT -> Point(rotatedOffset.x.scaled(scaleX) - width, rotatedOffset.y.scaled(scaleY) - height)
        SkinRotation.REVERSE_LANDSCAPE -> Point(rotatedOffset.x.scaled(scaleX) - width, rotatedOffset.y.scaled(scaleY))
        else -> Point(rotatedOffset.x.scaled(scaleX), rotatedOffset.y.scaled(scaleY))
      }
    val transformedImage = ImageUtils.rotateByQuadrantsAndScale(image, rotation.ordinal, width, height)
    return AnchoredImage(transformedImage, Dimension(width, height), rotatedAnchorPoint, transformedOffset)
  }
}

/**
 * One of the four corners of the display rectangle.
 *
 * @param x the normalized X coordinate, with the value of 0 or 1
 * @param y the normalized Y coordinate, with the value of 0 or 1
 */
enum class AnchorPoint(val x: Int, val y: Int) {
  TOP_LEFT(0, 0), BOTTOM_LEFT(0, 1), BOTTOM_RIGHT(1, 1), TOP_RIGHT(1, 0);

  /**
   * Returns the anchor point corresponding to this one after rotating the display rectangle.
   *
   * @param rotation the rotation of the display rectangle
   */
  fun rotated(rotation: SkinRotation): AnchorPoint {
    return values()[(ordinal + rotation.ordinal) % values().size]
  }
}