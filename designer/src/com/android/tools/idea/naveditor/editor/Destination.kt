// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.naveditor.editor

import com.android.SdkConstants
import com.android.annotations.VisibleForTesting
import com.android.resources.ResourceType
import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.scene.draw.HQ_RENDERING_HINTS
import com.android.tools.idea.common.util.iconToImage
import com.android.tools.idea.naveditor.model.schema
import com.android.tools.idea.naveditor.model.setAsStartDestination
import com.android.tools.idea.naveditor.model.startDestinationId
import com.android.tools.idea.naveditor.scene.NavColorSet.*
import com.android.tools.idea.naveditor.scene.ThumbnailManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.xml.XmlFile
import com.intellij.util.ui.StartupUiUtil
import icons.StudioIcons
import org.jetbrains.android.dom.navigation.NavigationSchema
import java.awt.*
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

private const val THUMBNAIL_MAX_DIMENSION = 60f
private const val THUMBNAIL_BORDER_THICKNESS = 1f
private const val THUMBNAIL_OUTER_RADIUS = 5f
private const val THUMBNAIL_INNER_RADIUS = 3f
private val THUMBNAIL_BORDER_STROKE = BasicStroke(THUMBNAIL_BORDER_THICKNESS)

sealed class Destination : Comparable<Destination> {
  /**
   * Add this to the graph. Must be called in a write action.
   */

  enum class DestinationOrder {
    PLACEHOLDER,
    FRAGMENT,
    INCLUDE,
    ACTIVITY,
    OTHER
  }

  abstract fun addToGraph()

  abstract val label: String
  abstract val thumbnail: Image
  abstract val typeLabel: String
  abstract val destinationOrder: DestinationOrder
  abstract val inProject: Boolean

  var component: NlComponent? = null

  override fun compareTo(other: Destination): Int {
    return comparator.compare(this, other)
  }

  abstract class ScreenShapedDestination(private val _parent: NlComponent) : Destination() {
    override val thumbnail: Image by lazy {
      val model = _parent.model
      val screenSize = model.configuration.deviceState?.orientation?.let { model.configuration.device?.getScreenSize(it) }
                       ?: error("No device in configuration!")
      val ratio = THUMBNAIL_MAX_DIMENSION / maxOf(screenSize.height, screenSize.width)
      val thumbnailDimension = Dimension((screenSize.width * ratio - 2 * THUMBNAIL_BORDER_THICKNESS).toInt(),
                                         (screenSize.height * ratio - 2 * THUMBNAIL_BORDER_THICKNESS).toInt())


      val result = BufferedImage(thumbnailDimension.width + 2 * THUMBNAIL_BORDER_THICKNESS.toInt(),
                                 thumbnailDimension.height + 2 * THUMBNAIL_BORDER_THICKNESS.toInt(), BufferedImage.TYPE_INT_ARGB)

      val graphics = result.createGraphics()
      val roundRect = RoundRectangle2D.Float(THUMBNAIL_BORDER_THICKNESS, THUMBNAIL_BORDER_THICKNESS, thumbnailDimension.width.toFloat(),
                                             thumbnailDimension.height.toFloat(), THUMBNAIL_INNER_RADIUS, THUMBNAIL_INNER_RADIUS)
      val oldClip = graphics.clip
      graphics.clip = roundRect
      graphics.setRenderingHints(HQ_RENDERING_HINTS)
      drawThumbnailContents(model, thumbnailDimension, graphics)

      graphics.clip = oldClip
      graphics.color = PLACEHOLDER_BORDER_COLOR
      graphics.stroke = THUMBNAIL_BORDER_STROKE
      roundRect.width = roundRect.width + THUMBNAIL_BORDER_THICKNESS
      roundRect.height = roundRect.height + THUMBNAIL_BORDER_THICKNESS
      roundRect.x = 0.5f
      roundRect.y = 0.5f
      roundRect.archeight = THUMBNAIL_OUTER_RADIUS
      roundRect.arcwidth = THUMBNAIL_OUTER_RADIUS
      graphics.draw(roundRect)

      return@lazy result
    }

    abstract fun drawThumbnailContents(model: NlModel, thumbnailDimension: Dimension, graphics: Graphics2D)

    protected fun drawBackground(thumbnailDimension: Dimension, graphics: Graphics2D) {
      graphics.color = PLACEHOLDER_BACKGROUND_COLOR
      graphics.fillRect(THUMBNAIL_BORDER_THICKNESS.toInt(), THUMBNAIL_BORDER_THICKNESS.toInt(),
                        thumbnailDimension.width, thumbnailDimension.height)
    }
  }

  @VisibleForTesting
  data class RegularDestination @JvmOverloads constructor(
    val parent: NlComponent, val tag: String, private val destinationLabel: String? = null, val destinationClass: PsiClass,
    val idBase: String = destinationClass.name ?: tag, private val layoutFile: XmlFile? = null,
    override val inProject: Boolean = true)
    : ScreenShapedDestination(parent) {

    override fun drawThumbnailContents(model: NlModel, thumbnailDimension: Dimension, graphics: Graphics2D) {
      if (layoutFile != null) {
        val refinableImage = ThumbnailManager.getInstance(model.facet).getThumbnail(layoutFile, model.configuration, thumbnailDimension)
        // TODO: wait for rendering nicely
        val image = refinableImage.terminalImage

        if (image != null) {
          StartupUiUtil.drawImage(graphics, image, Rectangle(THUMBNAIL_BORDER_THICKNESS.toInt(), THUMBNAIL_BORDER_THICKNESS.toInt(), image.width,
                                                      image.height), null)
        }
      }
      else {
        drawBackground(thumbnailDimension, graphics)
        graphics.font = graphics.font.deriveFont(13).deriveFont(Font.BOLD)
        val unknownString = "?"
        val stringWidth = graphics.fontMetrics.charWidth('?')
        graphics.color = PLACEHOLDER_TEXT_COLOR
        graphics.drawString(unknownString, (thumbnailDimension.width - stringWidth) / 2 + THUMBNAIL_BORDER_THICKNESS,
                            (thumbnailDimension.height + graphics.fontMetrics.ascent) / 2 + THUMBNAIL_BORDER_THICKNESS)
      }
    }

    override val typeLabel: String
      get() = parent.model.schema.getTagLabel(tag)

    override val destinationOrder = parent.model.schema.getDestinationTypesForTag(tag).let {
      when {
        it.contains(NavigationSchema.DestinationType.FRAGMENT) -> DestinationOrder.FRAGMENT
        it.contains(NavigationSchema.DestinationType.ACTIVITY) -> DestinationOrder.ACTIVITY
        else -> DestinationOrder.OTHER
      }
    }

    override val label = destinationLabel ?: layoutFile?.let { FileUtil.getNameWithoutExtension(it.name) } ?: destinationClass.name ?: tag

    override fun addToGraph() {
      val model = parent.model

      val tag = parent.tag.createChildTag(tag, null, null, true)
      val newComponent = model.createComponent(null, tag, parent, null, InsertType.CREATE)
      newComponent.assignId(idBase)
      newComponent.setAndroidAttribute(SdkConstants.ATTR_NAME, destinationClass.qualifiedName)
      newComponent.setAndroidAttribute(SdkConstants.ATTR_LABEL, label)
      if (parent.startDestinationId == null) {
        newComponent.setAsStartDestination()
      }
      layoutFile?.let {
        // TODO: do this the right way
        val layoutId = "@${ResourceType.LAYOUT.getName()}/${FileUtil.getNameWithoutExtension(it.name)}"
        newComponent.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, layoutId)
      }
      component = newComponent
    }
  }

  data class IncludeDestination(val graph: String, val parent: NlComponent) : Destination() {
    override fun addToGraph() {
      val model = parent.model

      val tag = parent.tag.createChildTag(SdkConstants.TAG_INCLUDE, null, null, true)
      val newComponent = model.createComponent(null, tag, parent, null, InsertType.CREATE)
      newComponent.setAttribute(SdkConstants.AUTO_URI, SdkConstants.ATTR_GRAPH,
                                "@${ResourceType.NAVIGATION.getName()}/${FileUtil.getNameWithoutExtension(graph)}")
      component = newComponent
    }

    override val label = graph

    // TODO: update
    override val thumbnail: Image by lazy {
      iconToImage(StudioIcons.NavEditor.ExistingDestinations.NESTED).getScaledInstance(45, 60, Image.SCALE_SMOOTH)
    }

    override val typeLabel: String
      get() = parent.model.schema.getTagLabel(SdkConstants.TAG_INCLUDE)

    override val destinationOrder = DestinationOrder.INCLUDE

    override val inProject = true
  }

  data class PlaceholderDestination(val parent: NlComponent) : ScreenShapedDestination(parent) {
    override fun addToGraph() {
      val model = parent.model

      val tag = parent.tag.createChildTag("fragment", null, null, true)
      val newComponent = model.createComponent(null, tag, parent, null, InsertType.CREATE)
      newComponent.assignId("placeholder")
      if (parent.startDestinationId == null) {
        newComponent.setAsStartDestination()
      }
      component = newComponent
    }

    override val label = "placeholder"

    override fun drawThumbnailContents(model: NlModel, thumbnailDimension: Dimension, graphics: Graphics2D) {
      drawBackground(thumbnailDimension, graphics)
      graphics.color = PLACEHOLDER_BORDER_COLOR
      graphics.drawLine(0, 0, thumbnailDimension.width, thumbnailDimension.height)
      graphics.drawLine(thumbnailDimension.width, 0, 0, thumbnailDimension.height)
    }

    override val typeLabel = "Empty destination"

    override val destinationOrder = DestinationOrder.PLACEHOLDER

    override val inProject = true
  }

  companion object {
    private val comparator = Comparator.comparing<Destination, Boolean> { it.inProject }
      .thenComparingInt { it.destinationOrder.ordinal }
      .thenComparing<String> { it.label }
  }
}