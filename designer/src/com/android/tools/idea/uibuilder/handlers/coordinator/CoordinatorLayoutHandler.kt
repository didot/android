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

import com.android.SdkConstants.*
import com.android.tools.idea.uibuilder.api.DragHandler
import com.android.tools.idea.uibuilder.api.DragType
import com.android.tools.idea.uibuilder.api.ViewEditor
import com.android.tools.idea.uibuilder.handlers.ScrollViewHandler
import com.android.tools.idea.uibuilder.handlers.constraint.targets.*
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneInteraction
import com.android.tools.idea.common.scene.target.Target
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget
import com.android.tools.idea.common.surface.Interaction
import com.android.tools.idea.uibuilder.surface.ScreenView
import com.google.common.collect.ImmutableList
import java.util.*

/**
 * Handler for the {@code <android.support.design.widget.CoordinatorLayout>} layout
 */
class CoordinatorLayoutHandler : ScrollViewHandler() {

  enum class InteractionState { NORMAL, DRAGGING }

  var interactionState = InteractionState.NORMAL

  override fun getInspectorProperties(): List<String> {
    return ImmutableList.of<String>(
        ATTR_CONTEXT,
        ATTR_FITS_SYSTEM_WINDOWS)
  }

  override fun getLayoutInspectorProperties(): List<String> {
    return listOf(ATTR_LAYOUT_BEHAVIOR, ATTR_LAYOUT_ANCHOR, ATTR_LAYOUT_ANCHOR_GRAVITY)
  }

  override fun createDragHandler(editor: ViewEditor,
                                 layout: SceneComponent,
                                 components: List<NlComponent>,
                                 type: DragType): DragHandler? {
    // The {@link CoordinatorDragHandler} handles the logic for anchoring a
    // single component to an existing component in the CoordinatorLayout.
    // If we are moving several components we probably don't want them to be
    // anchored to the same place, so instead we use the FrameLayoutHandler in
    // this case.
    if (components.size == 1 && components[0] != null) {
      return CoordinatorDragHandler(editor, this, layout, components, type)
    }
    else {
      return super.createDragHandler(editor, layout, components, type)
    }
  }

  /**
   * Return a new ConstraintInteraction instance to handle a mouse interaction

   * @param screenView the associated screen view
   * @param component  the component we belong to
   * @return a new instance of ConstraintInteraction
   */
  override fun createInteraction(screenView: ScreenView, component: NlComponent): Interaction? {
    return SceneInteraction(screenView)
  }

  override fun createChildTargets(parentComponent: SceneComponent, childComponent: SceneComponent): MutableList<Target> {
    val listBuilder = ImmutableList.builder<Target>()
    listBuilder.add(
        CoordinatorDragTarget(),
        ConstraintResizeTarget(ResizeBaseTarget.Type.LEFT),
        ConstraintResizeTarget(ResizeBaseTarget.Type.RIGHT),
        ConstraintResizeTarget(ResizeBaseTarget.Type.TOP),
        ConstraintResizeTarget(ResizeBaseTarget.Type.BOTTOM),
        ConstraintResizeTarget(ResizeBaseTarget.Type.LEFT_TOP),
        ConstraintResizeTarget(ResizeBaseTarget.Type.LEFT_BOTTOM),
        ConstraintResizeTarget(ResizeBaseTarget.Type.RIGHT_TOP),
        ConstraintResizeTarget(ResizeBaseTarget.Type.RIGHT_BOTTOM)
    )

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
}
