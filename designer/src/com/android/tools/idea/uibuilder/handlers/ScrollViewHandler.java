/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers;

import android.view.View;
import android.view.ViewGroup;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.common.api.DragType;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.api.actions.ToggleViewAction;
import com.android.tools.idea.uibuilder.api.actions.ViewAction;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.handlers.frame.FrameDragHandler;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.common.scene.SceneComponent;
import com.google.common.collect.ImmutableList;
import icons.AndroidDesignerIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

import static com.android.SdkConstants.*;

/**
 * Handler for the {@code <ScrollView>} widget
 */
public class ScrollViewHandler extends ViewGroupHandler {
  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_SCROLLBAR_STYLE,
      ATTR_STYLE,
      ATTR_FILL_VIEWPORT,
      ATTR_CLIP_TO_PADDING);
  }

  @Override
  public void onChildInserted(@NotNull ViewEditor editor,
                              @NotNull NlComponent parent,
                              @NotNull NlComponent child,
                              @NotNull InsertType insertType) {
    child.setAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT);
    child.setAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
  }

  @Override
  public boolean onCreate(@NotNull ViewEditor editor,
                          @Nullable NlComponent parent,
                          @NotNull NlComponent node,
                          @NotNull InsertType insertType) {
    if (insertType.isCreate()) {
      // Insert a default linear layout (which will in turn be registered as
      // a child of this node and the create child method above will set its
      // fill parent attributes, its id, etc.
      NlComponent linear = NlComponentHelperKt.createChild(node, editor, FQCN_LINEAR_LAYOUT, null, InsertType.PROGRAMMATIC);
      linear.setAttribute(ANDROID_URI, ATTR_ORIENTATION, VALUE_VERTICAL);
    }

    return true;
  }

  @Nullable
  @Override
  public DragHandler createDragHandler(@NotNull ViewEditor editor,
                                       @NotNull SceneComponent layout,
                                       @NotNull List<NlComponent> components,
                                       @NotNull DragType type) {
    return new FrameDragHandler(editor, this, layout, components, type);
  }

  @Nullable
  @Override
  public ScrollHandler createScrollHandler(@NotNull ViewEditor editor, @NotNull NlComponent component) {
    ViewGroup viewGroup = getViewGroupFromComponent(component);
    if (viewGroup == null) {
      return null;
    }

    int maxScrollableHeight = getMaxScrollable(viewGroup, ViewGroup::getHeight, View::getMeasuredHeight);

    if (maxScrollableHeight > 0) {
      // There is something to scroll
      return ScrollViewScrollHandler.createHandler(viewGroup, maxScrollableHeight, 10, ScrollViewScrollHandler.Orientation.VERTICAL);
    }

    return null;
  }

  /**
   * Returns the {@link ViewGroup} linked from the passed {@link NlComponent} or null if the {@link View} is not a {@link ViewGroup}.
   */
  @Nullable
  static ViewGroup getViewGroupFromComponent(@NotNull NlComponent component) {
    ViewInfo viewInfo = NlComponentHelperKt.getViewInfo(component);
    Object viewObject = viewInfo != null ? viewInfo.getViewObject() : null;

    if (viewObject != null && viewObject instanceof ViewGroup) {
      return (ViewGroup)viewObject;
    }
    return null;
  }

  /**
   * Returns the maximum distance that the passed view group could scroll
   *
   * @param measureGroup    {@link Function} used to measure the passed viewGroup (for example {@link ViewGroup#getHeight()})
   * @param measureChildren {@link Function} used to measure the children of the viewGroup (for example {@link View#getMeasuredHeight()})
   */
  static int getMaxScrollable(@NotNull ViewGroup viewGroup,
                              @NotNull Function<ViewGroup, Integer> measureGroup,
                              @NotNull Function<View, Integer> measureChildren) {
    int maxScrollable = 0;
    for (int i = 0; i < viewGroup.getChildCount(); i++) {
      maxScrollable += measureChildren.apply(viewGroup.getChildAt(i));
    }

    // Subtract the viewport height from the scrollable size
    maxScrollable -= measureGroup.apply(viewGroup);

    if (maxScrollable < 0) {
      maxScrollable = 0;
    }

    return maxScrollable;
  }

  @Override
  public boolean acceptsChild(@NotNull NlComponent layout, @NotNull NlComponent newChild) {
    return layout.getChildCount() == 0;
  }

  @Override
  public void addToolbarActions(@NotNull List<ViewAction> actions) {
    actions.add(new ToggleRenderModeAction());
  }

  static class ToggleRenderModeAction extends ToggleViewAction {
    public ToggleRenderModeAction() {
      super(AndroidDesignerIcons.ViewportRender, AndroidDesignerIcons.NormalRender, "Toggle Viewport Render Mode", null);
    }

    @Override
    public boolean isSelected(@NotNull ViewEditor editor,
                              @NotNull ViewHandler handler,
                              @NotNull NlComponent parent,
                              @NotNull List<NlComponent> selectedChildren) {
      return LayoutlibSceneManager.isRenderViewPort();
    }

    @Override
    public void setSelected(@NotNull ViewEditor editor,
                            @NotNull ViewHandler handler,
                            @NotNull NlComponent parent,
                            @NotNull List<NlComponent> selectedChildren,
                            boolean selected) {
      LayoutlibSceneManager.setRenderViewPort(selected);
      editor.getSceneBuilder().requestRender();
    }
  }
}
