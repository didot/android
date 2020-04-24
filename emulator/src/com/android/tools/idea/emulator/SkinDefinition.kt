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

import com.android.annotations.concurrency.Slow
import com.android.emulator.control.Rotation.SkinRotation
import com.android.emulator.control.Rotation.SkinRotation.LANDSCAPE
import com.android.emulator.control.Rotation.SkinRotation.REVERSE_LANDSCAPE
import com.android.emulator.control.Rotation.SkinRotation.REVERSE_PORTRAIT
import com.android.tools.adtui.ImageUtils.TRANSPARENCY_FILTER
import com.android.tools.adtui.ImageUtils.getCropBounds
import com.android.tools.adtui.ImageUtils.getCroppedImage
import com.android.tools.idea.avdmanager.SkinLayoutDefinition
import org.jetbrains.kotlin.utils.ThreadSafe
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.IOException
import java.net.URL
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * Description of AVD frame and mask.
 *
 * @param layout the layout corresponding to the default orientation of the virtual device display
 */
@ThreadSafe
class SkinDefinition private constructor(val layout: SkinLayout) {
  /**
   * Creates a [SkinLayout] for the given display dimensions and rotation.
   *
   * @param displayWidth the width of the rotated display
   * @param displayHeight the height of the rotated display
   * @param displayRotation the orientation of the display
   */
  fun createScaledLayout(displayWidth: Int, displayHeight: Int, displayRotation: SkinRotation): SkinLayout {
    if (displayRotation == LANDSCAPE && displayWidth == layout.displaySize.width && displayHeight == layout.displaySize.height) {
      return layout // No rotation or scaling needed.
    }

    val rotatedFrameRect = layout.frameRectangle.rotated(displayRotation, layout.displaySize)
    val rotatedDisplaySize = layout.displaySize.rotated(displayRotation)
    val scaleX = displayWidth.toDouble() / rotatedDisplaySize.width
    val scaleY = displayHeight.toDouble() / rotatedDisplaySize.height
    // To avoid visible seams between parts of the skin scale the frame margins separately from the display.
    val frameX = rotatedFrameRect.x.scaled(scaleX)
    val frameY = rotatedFrameRect.y.scaled(scaleY)
    val frameWidth = -frameX + displayWidth + (rotatedFrameRect.right - rotatedDisplaySize.width).scaled(scaleX)
    val frameHeight = -frameY + displayHeight + (rotatedFrameRect.bottom - rotatedDisplaySize.height).scaled(scaleY)
    val frameRect = Rectangle(frameX, frameY, frameWidth, frameHeight)
    val frameImages = layout.frameImages.map { it.rotatedAndScaled(displayRotation, scaleX, scaleY) }.toList()
    val maskImages = layout.maskImages.map { it.rotatedAndScaled(displayRotation, scaleX, scaleY) }.toList()
    return SkinLayout(Dimension(displayWidth, displayHeight), frameRect, frameImages, maskImages)
  }

  /**
   * Returns the frame dimensions for the given display orientation.
   */
  fun getRotatedFrameSize(displayRotation: SkinRotation): Dimension {
    return layout.frameRectangle.size.rotated(displayRotation)
  }

  companion object {
    @Slow
    @JvmStatic
    fun create(skinFolder: Path): SkinDefinition? {
      try {
        val layoutFile = skinFolder.resolve("layout") ?: return null
        val contents = Files.readAllBytes(layoutFile).toString(UTF_8)
        val skin = SkinLayoutDefinition.parseString(contents)
        var displayWidth = 0
        var displayHeight = 0
        // Process part nodes. The "onion" and "controls" nodes are ignored because they don't
        // contribute to the device frame appearance.
        val partsByName: MutableMap<String, Part> = hashMapOf()
        val partNodes = skin.getNode("parts")?.children ?: return null
        for ((name, node) in partNodes.entries) {
          if (name == "device") {
            displayWidth = node.getValue("display.width")?.toInt() ?: return null
            displayHeight = node.getValue("display.height")?.toInt() ?: return null
          }
          else if (name != "onion" && name != "controls") {
            partsByName[name] = createPart(node, skinFolder)
          }
        }

        // Process layout nodes.
        var layout: SkinLayout? = null
        val layoutNodes = skin.getNode("layouts")?.children ?: return null
        layout@ for (layoutNode in layoutNodes.values) {
          val width = layoutNode.getValue("width")?.toInt() ?: continue
          val height = layoutNode.getValue("height")?.toInt() ?: continue
          var part: Part? = null
          var frameX = 0
          var frameY = 0
          var partX = 0
          var partY = 0
          for (subnode in layoutNode.children.values) {
            val x = subnode.getValue("x")?.toInt() ?: 0
            val y = subnode.getValue("y")?.toInt() ?: 0
            val name = subnode.getValue("name") ?: continue
            if (name == "device") {
              val rotation = subnode.getValue("rotation")?.toInt() ?: 0
              if (rotation != 0) {
                continue@layout // The layout is rotated - ignore it.
              }
              frameX = -x
              frameY = -y
            }
            else {
              if (part == null) {
                part = partsByName[name]
                if (part != null) {
                  partX = x
                  partY = y
                }
              }
            }
          }

          if (part != null) {
            val frameRectangle = Rectangle(frameX + partX, frameY + partY, width, height)
            layout = createLayout(Dimension(displayWidth, displayHeight), frameRectangle, part)
          }
        }

        if (layout != null) {
          return SkinDefinition(layout)
        }
      }
      catch (e: NoSuchFileException) {
        logger.error("File not found: ${e.file}")
      }
      catch (e: IOException) {
        logger.error(e)
      }
      return null
    }

    /**
     * Returns the skin rectangle rotated with the display according to [rotation].
     *
     * @param rotation the requested rotation
     * @param displaySize the display dimensions before rotation
     */
    @JvmStatic
    private fun Rectangle.rotated(rotation: SkinRotation, displaySize: Dimension): Rectangle {
      return when (rotation) {
        LANDSCAPE -> Rectangle(y, displaySize.width - width - x, height, width)
        REVERSE_PORTRAIT -> Rectangle(displaySize.width - width - x, displaySize.height - height - y, width, height)
        REVERSE_LANDSCAPE -> Rectangle(displaySize.height - height - y, x, height, width)
        else -> this
      }
    }

    @JvmStatic
    private fun createPart(partNode: SkinLayoutDefinition, skinFolder: Path): Part {
      val background = getReferencedFile(partNode, "background.image", skinFolder)
      val mask = getReferencedFile(partNode, "foreground.mask", skinFolder)
      return Part(background, mask)
    }

    @JvmStatic
    private fun getReferencedFile(node: SkinLayoutDefinition, propertyName: String, skinFolder: Path): URL? {
      val filename = node.getValue(propertyName) ?: return null
      return skinFolder.resolve(filename).toUri().toURL()
    }

    @JvmStatic
    private fun createLayout(displaySize: Dimension, frameRectangle: Rectangle, part: Part): SkinLayout {
      val backgroundImages: List<AnchoredImage>
      val maskImages: List<AnchoredImage>
      val background = part.backgroundFile?.let { readImage(it) }
      if (background == null) {
        backgroundImages = emptyList()
      }
      else {
        backgroundImages = disassembleFrame(background, frameRectangle, displaySize)
      }

      val mask =
        if (part.maskFile != null) {
          readImage(part.maskFile)
        }
        else if (part.backgroundFile?.file?.contains("_mask_") == true) {
          // The AndroidWearRound/layout file doesn't specify a mask, but the background image is supposed to be used instead.
          background?.cropped(Rectangle(-frameRectangle.x, -frameRectangle.y, displaySize.width, displaySize.height))
        }
        else {
          null
        }

      if (mask == null) {
        maskImages = emptyList()
      }
      else {
        maskImages = disassembleMask(mask, displaySize)
      }

      val adjustedFrameRectangle = computeAdjustedFrameRectangle(backgroundImages, displaySize)
      return SkinLayout(displaySize, adjustedFrameRectangle, backgroundImages, maskImages)
    }

    /**
     * Crops the background image and breaks it into 8 pieces, 4 for sides and 4 for corners of the frame.
     */
    @JvmStatic
    private fun disassembleFrame(background: BufferedImage, frameRectangle: Rectangle, displaySize: Dimension): List<AnchoredImage> {
      // Display edges in coordinates of the cropped background image.
      val cropBounds = getCropBounds(background, null) ?: return emptyList()
      val displayLeft = -frameRectangle.x
      val displayRight = displayLeft + displaySize.width
      val displayTop = -frameRectangle.y
      val displayBottom = displayTop + displaySize.height
      // Thickness of the right and bottom sides of the frame.
      val marginLeft = displayLeft - cropBounds.x
      val marginRight = cropBounds.right - displayRight
      val marginTop = displayTop - cropBounds.y
      val marginBottom = cropBounds.bottom - displayBottom

      val images = mutableListOf<AnchoredImage>()
      run {
        // Right side.
        val rect = Rectangle(displayRight, displayTop, marginRight, displaySize.height)
        val image = background.cropped(rect)
        val offset = Point(rect.x - displayRight, rect.y - displayTop)
        images.add(AnchoredImage(image, rect.size, AnchorPoint.TOP_RIGHT, offset))
      }
      run {
        // Top right corner.
        val rect = Rectangle(displayRight, cropBounds.y, marginRight, marginTop)
        val image = background.cropped(rect)
        val offset = Point(rect.x - displayRight, rect.y - displayTop)
        images.add(AnchoredImage(image, rect.size, AnchorPoint.TOP_RIGHT, offset))
      }
      run {
        // Top side.
        val rect = Rectangle(displayLeft, cropBounds.y, displaySize.width, marginTop)
        val image = background.cropped(rect)
        val offset = Point(rect.x - displayLeft, rect.y - displayTop)
        images.add(AnchoredImage(image, rect.size, AnchorPoint.TOP_LEFT, offset))
      }
      run {
        // Top left corner.
        val rect = Rectangle(cropBounds.x, cropBounds.y, marginLeft, marginTop)
        val image = background.cropped(rect)
        val offset = Point(rect.x - displayLeft, rect.y - displayTop)
        images.add(AnchoredImage(image, rect.size, AnchorPoint.TOP_LEFT, offset))
      }
      run {
        // Left side.
        val rect = Rectangle(cropBounds.x, displayTop, marginLeft, displaySize.height)
        val image = background.cropped(rect)
        val offset = Point(rect.x - displayLeft, rect.y - displayTop)
        images.add(AnchoredImage(image, rect.size, AnchorPoint.TOP_LEFT, offset))
      }
      run {
        // Bottom left corner.
        val rect = Rectangle(cropBounds.x, displayBottom, marginLeft, marginBottom)
        val image = background.cropped(rect)
        val offset = Point(rect.x - displayLeft, rect.y - displayBottom)
        images.add(AnchoredImage(image, rect.size, AnchorPoint.BOTTOM_LEFT, offset))
      }
      run {
        // Bottom side.
        val rect = Rectangle(displayLeft, displayBottom, displaySize.width, marginBottom)
        val image = background.cropped(rect)
        val offset = Point(rect.x - displayLeft, rect.y - displayBottom)
        images.add(AnchoredImage(image, rect.size, AnchorPoint.BOTTOM_LEFT, offset))
      }
      run {
        // Bottom right corner.
        val rect = Rectangle(displayRight, displayBottom, marginRight, marginBottom)
        val image = background.cropped(rect)
        val offset = Point(rect.x - displayRight, rect.y - displayBottom)
        images.add(AnchoredImage(image, rect.size, AnchorPoint.BOTTOM_RIGHT, offset))
      }
      return images
    }

    /**
     * Breaks the background image into 8 pieces, 4 for sides and 4 for corners of the frame.
     * Each piece is cropped to remove the fully transparent part.
     */
    @JvmStatic
    private fun disassembleMask(mask: BufferedImage, displaySize: Dimension): List<AnchoredImage> {
      return cutHorizontally(mask, Rectangle(0, 0, mask.width, mask.height))
        .flatMap { cutVertically(mask, it) }
        .map { createAnchoredImage(mask, it, displaySize) }
        .toList()
    }

    @JvmStatic
    private fun cutHorizontally(image: BufferedImage, cropBounds: Rectangle): Sequence<Rectangle> {
      return sequence {
        var bounds = cropBounds
        outer@ while (true) {
          for (y in bounds.y until bounds.bottom) {
            if (isTransparentHorizontalLine(image, bounds.x, bounds.right, y)) {
              val piece = getCropBounds(image, Rectangle(bounds.x, cropBounds.y, bounds.width, y - bounds.y))
              piece?.let { yield(it) }
              bounds = getCropBounds(image, Rectangle(bounds.x, y + 1, cropBounds.width, bounds.height - y - 1)) ?: return@sequence
              continue@outer
            }
          }
          yield(bounds)
          return@sequence
        }
      }
    }

    @JvmStatic
    private fun cutVertically(image: BufferedImage, cropBounds: Rectangle): Sequence<Rectangle> {
      return sequence {
        var bounds = cropBounds
        outer@ while (true) {
          for (x in bounds.x until bounds.right) {
            if (isTransparentVerticalLine(image, x, bounds.y, bounds.bottom)) {
              val piece = getCropBounds(image, Rectangle(bounds.x, cropBounds.y, x - bounds.x, bounds.height))
              piece?.let { yield(it) }
              bounds = getCropBounds(image, Rectangle(x + 1, bounds.y, cropBounds.width - x - 1, bounds.height)) ?: return@sequence
              continue@outer
            }
          }
          yield(bounds)
          return@sequence
        }
      }
    }

    @JvmStatic
    private fun isTransparentHorizontalLine(image: BufferedImage, startX: Int, endX: Int, y: Int): Boolean {
      for (x in startX until endX) {
        if (!isTransparentPixel(image, x, y)) {
          return false
        }
      }
      return true
    }

    @JvmStatic
    private fun isTransparentVerticalLine(image: BufferedImage, x: Int, startY: Int, endY: Int): Boolean {
      for (y in startY until endY) {
        if (!isTransparentPixel(image, x, y)) {
          return false
        }
      }
      return true
    }

    @JvmStatic
    private fun isTransparentPixel(image: BufferedImage, x: Int, y: Int): Boolean {
      return image.getRGB(x, y) and ALPHA_MASK == 0
    }

    @JvmStatic
    private fun createAnchoredImage(mask: BufferedImage, cropBounds: Rectangle, displaySize: Dimension): AnchoredImage {
      val anchorPoint =
        if (cropBounds.x > displaySize.width / 2) {
          if (cropBounds.y > displaySize.height / 2) {
            AnchorPoint.BOTTOM_RIGHT
          }
          else {
            AnchorPoint.TOP_RIGHT
          }
        }
        else {
          if (cropBounds.y > displaySize.height / 2) {
            AnchorPoint.BOTTOM_LEFT
          }
          else {
            AnchorPoint.TOP_LEFT
          }
        }
      val offset = Point(cropBounds.x - anchorPoint.x * displaySize.width, cropBounds.y - anchorPoint.y * displaySize.height)
      return AnchoredImage(mask.cropped(cropBounds), cropBounds.size, anchorPoint, offset)
    }

    @JvmStatic
    private fun computeAdjustedFrameRectangle(backgroundImages: Iterable<AnchoredImage>, displaySize: Dimension): Rectangle {
      var left = 0
      var top = 0
      var right = displaySize.width
      var bottom = displaySize.height
      for (image in backgroundImages) {
        left = left.coerceAtMost(image.offset.x + displaySize.width * image.anchorPoint.x)
        right = right.coerceAtLeast(image.offset.x + image.size.width + displaySize.width * image.anchorPoint.x)
        top = top.coerceAtMost(image.offset.y + displaySize.height * image.anchorPoint.y)
        bottom = bottom.coerceAtLeast(image.offset.y + image.size.height + displaySize.height * image.anchorPoint.y)
      }
      return Rectangle(left, top, right - left, bottom - top)
    }

    @JvmStatic
    private fun BufferedImage.cropped(cropBounds: Rectangle): BufferedImage {
      return getCroppedImage(this, cropBounds, -1)
    }

    @JvmStatic
    private fun getCropBounds(image: BufferedImage, initialCrop: Rectangle?): Rectangle? {
      return getCropBounds(image, TRANSPARENCY_FILTER, initialCrop)
    }

    @JvmStatic
    private val Rectangle.right
      get() = x + width

    @JvmStatic
    private val Rectangle.bottom
      get() = y + height

    @JvmStatic
    private fun readImage(file: URL): BufferedImage? {
      try {
        return ImageIO.read(file)
      }
      catch (e: IOException) {
        logger.warn("Failed to read Emulator skin image $file")
        return null
      }
    }

    private const val ALPHA_MASK = 0xFF000000.toInt()
  }

  private data class Part(val backgroundFile: URL?, val maskFile: URL?)
}