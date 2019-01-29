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
package com.android.tools.idea.uibuilder.handlers.coordinator

import com.android.SdkConstants
import com.android.SdkConstants.*
import com.android.tools.idea.uibuilder.api.DragHandler
import com.android.tools.idea.common.api.DragType
import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.uibuilder.api.ViewEditor
import com.android.tools.idea.uibuilder.handlers.ScrollViewHandler
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.*
import com.android.tools.idea.common.scene.target.Target
import com.android.tools.idea.uibuilder.handlers.common.ViewGroupPlaceholder
import com.android.tools.idea.uibuilder.handlers.frame.FrameResizeTarget
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget
import com.android.tools.idea.uibuilder.surface.ScreenView
import com.google.common.collect.ImmutableList

/**
 * Handler for android.support.design.widget.CoordinatorLayout
 */
class CoordinatorLayoutHandler : ScrollViewHandler() {

  enum class InteractionState { NORMAL, DRAGGING }

  var interactionState: InteractionState = InteractionState.NORMAL

  override fun handlesPainting() = true

  override fun getInspectorProperties(): List<String> = listOf(ATTR_CONTEXT, ATTR_FITS_SYSTEM_WINDOWS)

  override fun getLayoutInspectorProperties(): List<String> {
    return listOf(ATTR_LAYOUT_BEHAVIOR, ATTR_LAYOUT_ANCHOR, ATTR_LAYOUT_ANCHOR_GRAVITY)
  }

  // Don't do the same behavior as ScrollViewHandler does.
  override fun onChildInserted(editor: ViewEditor, parent: NlComponent, child: NlComponent, insertType: InsertType) = Unit

  override fun onChildRemoved(editor: ViewEditor, layout: NlComponent, newChild: NlComponent, insertType: InsertType) {
    newChild.removeAttribute(SdkConstants.AUTO_URI, SdkConstants.ATTR_LAYOUT_ANCHOR_GRAVITY)
    newChild.removeAttribute(SdkConstants.AUTO_URI, SdkConstants.ATTR_LAYOUT_ANCHOR)
  }

  override fun createDragHandler(editor: ViewEditor,
                                 layout: SceneComponent,
                                 components: List<NlComponent>,
                                 type: DragType
  ): DragHandler? {
    // The {@link CoordinatorDragHandler} handles the logic for anchoring a
    // single component to an existing component in the CoordinatorLayout.
    // If we are moving several components we probably don't want them to be
    // anchored to the same place, so instead we use the FrameLayoutHandler in
    // this case.
    return if (components.size == 1) {
      CoordinatorDragHandler(editor, this, layout, components, type)
    }
    else {
      super.createDragHandler(editor, layout, components, type)
    }
  }

  /**
   * Return a new ConstraintInteraction instance to handle a mouse interaction

   * @param screenView the associated screen view
   * @param component  the component we belong to
   * @return a new instance of ConstraintInteraction
   */
  override fun createInteraction(screenView: ScreenView, component: NlComponent) = SceneInteraction(screenView)

  override fun createChildTargets(parentComponent: SceneComponent, childComponent: SceneComponent): MutableList<Target> {
    val listBuilder = ImmutableList.builder<Target>()

    if (childComponent !is TemporarySceneComponent) {
      listBuilder.add(
          CoordinatorDragTarget(),
          CoordinatorResizeTarget(ResizeBaseTarget.Type.LEFT_TOP),
          CoordinatorResizeTarget(ResizeBaseTarget.Type.LEFT),
          CoordinatorResizeTarget(ResizeBaseTarget.Type.LEFT_BOTTOM),
          CoordinatorResizeTarget(ResizeBaseTarget.Type.TOP),
          CoordinatorResizeTarget(ResizeBaseTarget.Type.BOTTOM),
          CoordinatorResizeTarget(ResizeBaseTarget.Type.RIGHT_TOP),
          CoordinatorResizeTarget(ResizeBaseTarget.Type.RIGHT),
          CoordinatorResizeTarget(ResizeBaseTarget.Type.RIGHT_BOTTOM)
      )
    }

    if (!childComponent.isSelected && interactionState == InteractionState.DRAGGING) {
      listBuilder.add(
          CoordinatorSnapTarget(CoordinatorSnapTarget.Type.LEFT),
          CoordinatorSnapTarget(CoordinatorSnapTarget.Type.TOP),
          CoordinatorSnapTarget(CoordinatorSnapTarget.Type.RIGHT),
          CoordinatorSnapTarget(CoordinatorSnapTarget.Type.BOTTOM),
          CoordinatorSnapTarget(CoordinatorSnapTarget.Type.LEFT_TOP),
          CoordinatorSnapTarget(CoordinatorSnapTarget.Type.LEFT_BOTTOM),
          CoordinatorSnapTarget(CoordinatorSnapTarget.Type.RIGHT_TOP),
          CoordinatorSnapTarget(CoordinatorSnapTarget.Type.RIGHT_BOTTOM)
      )
    }

    return listBuilder.build()
  }

  override fun acceptsChild(layout: NlComponent, newChild: NlComponent) = true

  override fun getPlaceholders(component: SceneComponent) =
    component.children
      .filterNot { component.scene.selection.contains(it.nlComponent) }
      .flatMap { child -> CoordinatorPlaceholder.Type.values().map { type -> CoordinatorPlaceholder(component, child, type) } }
      .toList()
      .plus(ViewGroupPlaceholder(component))
}

// The resize behaviour is similar to the FrameResizeTarget so far.
private class CoordinatorResizeTarget(type: ResizeBaseTarget.Type): FrameResizeTarget(type)
