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
package com.android.tools.idea.resourceExplorer.rendering

import com.android.ide.common.resources.ResourceResolver
import com.android.resources.ResourceType
import com.android.tools.adtui.ImageUtils
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.res.resolveDrawable
import com.android.tools.idea.resourceExplorer.ImageCache
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.model.resolveValue
import com.android.tools.idea.resourceExplorer.plugin.DesignAssetRendererManager
import com.android.tools.idea.resourceExplorer.plugin.LayoutRenderer
import com.android.tools.idea.resourceExplorer.view.EMPTY_ICON
import com.android.tools.idea.resourceExplorer.view.ERROR_ICON
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.xml.XmlFile
import com.intellij.util.ui.ImageUtil
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.ide.PooledThreadExecutor
import java.awt.Dimension
import java.awt.Image
import java.awt.image.BufferedImage
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier
import javax.swing.ImageIcon
import kotlin.math.min

private val LOG = Logger.getInstance(DrawableIconProvider::class.java)

typealias AsyncImageProvider = ((dimension: Dimension, designAsset: DesignAsset) -> CompletableFuture<Image?>)?

/**
 * [AssetIconProvider] that renders images of Layouts and Drawable and update
 * an [ImageIcon] each time [getIcon] is called. This means that the returned icon should
 * not be cached because it will change next time [getIcon] is called.
 *
 * The generated images are scaled to the provided dimensions and saved in [imageCache].
 *
 * @param alternateImageProvider Let the option to use an external method to render the [DesignAsset] while still
 * benefiting from the scaling and caching mechanism. It can also be used for test.
 */
class DrawableIconProvider(
  private val facet: AndroidFacet,
  private val resourceResolver: ResourceResolver,
  private val imageCache: ImageCache,
  private val alternateImageProvider: AsyncImageProvider = null
) : AssetIconProvider {

  private val imageIcon = ImageIcon(EMPTY_ICON)
  private val contentRatio = 0.1
  override var supportsTransparency: Boolean = true
  val project = facet.module.project

  private fun getDrawableImage(dimension: Dimension, designAsset: DesignAsset): CompletableFuture<out Image?>? {
    val resolveValue = resourceResolver.resolveValue(designAsset) ?: return null
    val file = resourceResolver.resolveDrawable(resolveValue, project)
               ?: designAsset.file
    return DesignAssetRendererManager.getInstance().getViewer(file)
      .getImage(file, facet.module, dimension)
  }

  /**
   * Returns an image of the provided [DesignAsset] representing a Layout.
   */
  private fun getLayoutImage(designAsset: DesignAsset): CompletableFuture<out Image?>? {
    val file = designAsset.file
    val psiFile = AndroidPsiUtils.getPsiFileSafely(facet.module.project, file)
    return if (psiFile is XmlFile) {
      CompletableFuture.supplyAsync(
        Supplier { ConfigurationManager.getOrCreateInstance(facet).getConfiguration(file) }, PooledThreadExecutor.INSTANCE)
        .thenCompose { configuration -> LayoutRenderer.getInstance(facet).getLayoutRender(psiFile, configuration) }
    }
    else null
  }

  private fun renderImage(dimension: Dimension, designAsset: DesignAsset): CompletableFuture<out Image?> =
    alternateImageProvider?.invoke(dimension, designAsset)
    ?: when (designAsset.type) {
      ResourceType.LAYOUT -> getLayoutImage(designAsset)
      ResourceType.DRAWABLE -> getDrawableImage(dimension, designAsset)
      else -> null
    }
    ?: CompletableFuture.completedFuture(null)

  override fun getIcon(assetToRender: DesignAsset,
                       width: Int,
                       height: Int,
                       refreshCallback: () -> Unit,
                       shouldBeRendered: () -> Boolean): ImageIcon {
    if (height > 0 && width > 0) {
      val targetSize = Dimension(width, height)
      var image = fetchImage(assetToRender, refreshCallback, shouldBeRendered, targetSize)
      // If an image is cached but does not fit into the content (i.e the list cell size was changed)
      // we do a fast rescaling in place and request a higher quality scaled image in the background
      val imageWidth = image.getWidth(null)
      val imageHeight = image.getHeight(null)
      val scale = getScale(targetSize, Dimension(imageWidth, imageHeight))
      if (image != EMPTY_ICON && image != ERROR_ICON && shouldScale(scale)) {
        val bufferedImage = ImageUtil.toBufferedImage(image)
        image = ImageUtils.lowQualityFastScale(bufferedImage, scale, scale)
        fetchImage(assetToRender, refreshCallback, shouldBeRendered, targetSize, true)
      }
      imageIcon.image = image
    }
    else {
      imageIcon.image = EMPTY_ICON
    }
    return imageIcon
  }

  /**
   * To avoid scaling too many times, we keep an acceptable window for the scale value before actually
   * requiring the scale.
   *
   * Since we have a margin around the image defined by [contentRatio], the image does not need to be resized
   * when it fits into this margin.
   */
  private fun shouldScale(scale: Double) = scale !in (1 - contentRatio)..(1 + contentRatio)

  /**
   * Get the scaling factor from [source] to [target].
   */
  private fun getScale(target: Dimension, source: Dimension): Double {
    val xScale = target.width / source.getWidth()
    val yScale = target.height / source.getHeight()
    return min(xScale, yScale)
  }

  /**
   * Returns a rendering of [designAsset] if its already cached otherwise asynchronously render
   * the [designAsset] at the given [targetSize] and returns [EMPTY_ICON]
   *
   * @param isStillVisible The isStillVisible of the designAsset in the refreshCallBack used to refresh the correct cell
   * @param forceImageRender if true, render the [designAsset] even if it's already cached.
   * @return a placeholder image.
   */
  private fun fetchImage(designAsset: DesignAsset,
                         refreshCallBack: () -> Unit,
                         isStillVisible: () -> Boolean,
                         targetSize: Dimension,
                         forceImageRender: Boolean = false): Image {
    return imageCache.computeAndGet(designAsset, EMPTY_ICON, forceImageRender, refreshCallBack) {
      if (isStillVisible()) {
        renderImage(targetSize, designAsset)
          .thenApplyAsync { image -> image ?: ERROR_ICON }
          .thenApply { image -> scaleToFitIfNeeded(image, targetSize) }
          .exceptionally { throwable ->
            LOG.error("Error while rendering $designAsset", throwable); ERROR_ICON
          }
      }
      else CompletableFuture.completedFuture(null)
    }
  }

  /**
   * Scale the provided [image] to fit into [targetSize] if needed. It might be converted to a
   * [BufferedImage] before being scaled
   */
  private fun scaleToFitIfNeeded(image: Image, targetSize: Dimension): Image {
    val imageSize = Dimension(image.getWidth(null), image.getHeight(null))
    val scale = getScale(targetSize, imageSize)
    if (shouldScale(scale)) {
      val newWidth = (imageSize.width * scale).toInt()
      val newHeight = (imageSize.height * scale).toInt()
      if (newWidth > 0 && newHeight > 0) {
        return ImageUtil.toBufferedImage(image)
          .getScaledInstance(newWidth, newHeight, BufferedImage.SCALE_SMOOTH)
      }
    }
    return image
  }
}