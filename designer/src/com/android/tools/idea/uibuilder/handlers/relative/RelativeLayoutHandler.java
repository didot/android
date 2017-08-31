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
package com.android.tools.idea.uibuilder.handlers.relative;

import com.android.tools.idea.common.command.NlWriteCommandAction;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.surface.Interaction;
import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.model.SegmentType;
import com.android.tools.idea.uibuilder.model.TextDirection;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handler for the {@code <RelativeLayout>} layout
 */
public class RelativeLayoutHandler extends ViewGroupHandler {
  @Override
  @Nullable
  public DragHandler createDragHandler(@NotNull ViewEditor editor,
                                       @NotNull SceneComponent layout,
                                       @NotNull List<NlComponent> components,
                                       @NotNull DragType type) {
    final RelativeDragHandler moveHandler = new RelativeDragHandler(editor, layout, components);
    return new DragHandler(editor, this, layout, components, type) {
      @Nullable
      @Override
      public String update(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, int modifiers) {
        super.update(x, y, modifiers);
        if (components.isEmpty()) {
          return null;
        }
        NlComponent primary = components.get(0);
        int deltaX = lastX;
        int deltaY = lastY;
        if (!primary.isRoot()) {
          deltaX -= startX;
          deltaY -= startY;
        }
        else {
          deltaX -= editor.pxToDp(NlComponentHelperKt.getW(primary) / 2);
          deltaY -= editor.pxToDp(NlComponentHelperKt.getH(primary) / 2);
        }
        moveHandler.updateMove(primary, deltaX, deltaY, modifiers);

        return null;
      }

      @Override
      public void paint(@NotNull NlGraphics graphics) {
        GuidelinePainter.paint(graphics, moveHandler);
      }

      @Override
      public void commit(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers, @NotNull InsertType insertType) {
        moveHandler.removeCycles();

        NlComponent previous = null;
        for (NlComponent component : components) {
          NlComponent finalPrevious = previous;

          NlWriteCommandAction.run(component, type.getDescription(), () -> {
            if (finalPrevious == null) {
              moveHandler.applyConstraints(component);
            }
            else {
              // Arrange the nodes next to each other, depending on which
              // edge we are attaching to. For example, if attaching to the
              // top edge, arrange the subsequent nodes in a column below it.
              //
              // TODO: Try to do something smarter here where we detect
              // constraints between the dragged edges, and we preserve these.
              // We have to do this carefully though because if the
              // constraints go through some other nodes not part of the
              // selection, this doesn't work right, and you might be
              // dragging several connected components, which we'd then
              // need to stitch together such that they are all visible.
              moveHandler.attachPrevious(finalPrevious, component);
            }
          });

          previous = component;
        }
        insertAddedComponents(insertType);
      }

      private void insertAddedComponents(@NotNull InsertType insertType) {
        List<NlComponent> added = components
          .stream()
          .filter(component -> component.getParent() != layout.getNlComponent())
          .collect(Collectors.toList());
        editor.getModel().addComponents(added, layout.getNlComponent(), null, insertType);
      }
    };
  }

  @Override
  @Nullable
  public ResizeHandler createResizeHandler(@NotNull ViewEditor editor,
                                           @NotNull NlComponent component,
                                           @Nullable SegmentType horizontalEdgeType,
                                           @Nullable SegmentType verticalEdgeType) {
    NlComponent parent = component.getParent();
    if (parent == null) {
      return null;
    }
    final RelativeResizeHandler resizeHandler = new RelativeResizeHandler(editor, parent, component, horizontalEdgeType, verticalEdgeType);

    return new ResizeHandler(editor, this, component, horizontalEdgeType, verticalEdgeType) {
      @Nullable
      @Override
      public String update(@AndroidCoordinate int x,
                           @AndroidCoordinate int y,
                           int modifiers,
                           @NotNull @AndroidCoordinate Rectangle newBounds) {
        super.update(x, y, modifiers, newBounds);
        resizeHandler.updateResize(component, newBounds, modifiers);
        return null;
      }

      @Override
      public void commit(@AndroidCoordinate int px,
                         @AndroidCoordinate int py,
                         int modifiers,
                         @NotNull @AndroidCoordinate Rectangle newBounds) {
        resizeHandler.removeCycles();
        resizeHandler.applyConstraints(component);
      }

      @Override
      public void paint(@NotNull NlGraphics graphics) {
        GuidelinePainter.paint(graphics, resizeHandler);
      }
    };
  }

  @Nullable
  @Override
  public Interaction createInteraction(@NotNull ScreenView screenView, @NotNull NlComponent layout) {
    SelectionModel selectionModel = screenView.getSelectionModel();
    if (selectionModel.getSelection().isEmpty()) {
      // The interacted component hasn't been selected, select it.
      // This happened when starting dragging a component without any selection.
      selectionModel.setSelection(Collections.singletonList(layout));
    }
    return super.createInteraction(screenView, layout);
  }
}
