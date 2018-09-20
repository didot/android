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
package com.android.tools.idea.naveditor.scene.decorator

import com.android.SdkConstants
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.decorator.SceneDecorator
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.draw.DrawFilledRectangle
import com.android.tools.idea.common.scene.draw.DrawLine
import com.android.tools.idea.naveditor.scene.DRAW_BACKGROUND_LEVEL
import com.android.tools.idea.naveditor.scene.DRAW_NAV_SCREEN_LEVEL
import com.android.tools.idea.naveditor.scene.NavColorSet
import com.android.tools.idea.naveditor.scene.REGULAR_FRAME_THICKNESS
import com.android.tools.idea.naveditor.scene.RefinableImage
import com.android.tools.idea.naveditor.scene.ThumbnailManager
import com.android.tools.idea.naveditor.scene.createDrawCommand
import com.android.tools.idea.naveditor.scene.draw.DrawNavScreen
import com.android.tools.idea.res.resolve
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.xml.XmlFile
import java.awt.BasicStroke
import java.awt.Dimension
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.io.File

/**
 * [NavScreenDecorator] Base class for navigation decorators.
 */
abstract class NavScreenDecorator : SceneDecorator() {
  override fun addFrame(list: DisplayList, sceneContext: SceneContext, component: SceneComponent) {
  }

  override fun addBackground(list: DisplayList, sceneContext: SceneContext, component: SceneComponent) {
  }

  override fun buildList(list: DisplayList, time: Long, sceneContext: SceneContext, component: SceneComponent) {
    val displayList = DisplayList()
    super.buildList(displayList, time, sceneContext, component)
    list.add(createDrawCommand(displayList, component))
  }

  // TODO: Either set an appropriate clip here, or make this the default behavior in the base class
  override fun buildListChildren(list: DisplayList,
                                 time: Long,
                                 sceneContext: SceneContext,
                                 component: SceneComponent) {
    for (child in component.children) {
      child.buildDisplayList(time, list, sceneContext)
    }
  }

  protected fun drawScreen(list: DisplayList,
                           sceneContext: SceneContext,
                           component: SceneComponent, @SwingCoordinate rectangle: Rectangle2D.Float) {
    val layout = component.nlComponent.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT)
    if (layout == null) {
      drawPlaceholder(list, sceneContext, rectangle)
    }
    else {
      drawImage(list, sceneContext, component, rectangle)
    }
  }

  private fun drawPlaceholder(list: DisplayList, sceneContext: SceneContext, @SwingCoordinate rectangle: Rectangle2D.Float) {
    list.add(DrawFilledRectangle(DRAW_BACKGROUND_LEVEL, rectangle, NavColorSet.NO_PREVIEW_BACKGROUND_COLOR))

    val color = sceneContext.colorSet.highlightedFrames
    val stroke = BasicStroke(REGULAR_FRAME_THICKNESS)
    val p1 = Point2D.Float(rectangle.x, rectangle.y)
    val p2 = Point2D.Float(p1.x, p1.y + rectangle.height)
    val p3 = Point2D.Float(p1.x + rectangle.width, p1.y)
    val p4 = Point2D.Float(p3.x, p2.y)

    list.add(DrawLine(DRAW_NAV_SCREEN_LEVEL, p1, p4, color, stroke))
    list.add(DrawLine(DRAW_NAV_SCREEN_LEVEL, p2, p3, color, stroke))
  }

  private fun drawImage(list: DisplayList, sceneContext: SceneContext, component: SceneComponent, rectangle: Rectangle2D.Float) {
    val image = buildImage(sceneContext, component, Dimension(rectangle.width.toInt(), rectangle.height.toInt()))
    list.add(DrawNavScreen(rectangle, image))
  }

  private fun buildImage(sceneContext: SceneContext,
                         component: SceneComponent,
                         dimensions: Dimension): RefinableImage {
    val empty = RefinableImage()
    val surface = sceneContext.surface ?: return empty
    val configuration = surface.configuration ?: return empty
    val facet = surface.model?.facet ?: return empty

    val layout = component.nlComponent.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT) ?: return empty
    val resourceUrl = ResourceUrl.parse(layout) ?: return empty
    if (resourceUrl.type != ResourceType.LAYOUT) {
      return empty
    }
    val resourceResolver = configuration.resourceResolver
    val resourceValue = ApplicationManager.getApplication().runReadAction<String> {
      resourceResolver?.resolve(resourceUrl, component.nlComponent.tag)?.value
    } ?: return empty

    val file = File(resourceValue)
    if (!file.exists()) {
      return empty
    }
    val virtualFile = VfsUtil.findFileByIoFile(file, false) ?: return empty

    val psiFile = AndroidPsiUtils.getPsiFileSafely(surface.project, virtualFile) as? XmlFile ?: return empty
    val manager = ThumbnailManager.getInstance(facet)
    return manager.getThumbnail(psiFile, configuration, dimensions)
  }
}
