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
package com.android.tools.idea.uibuilder.surface;

import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.graphics.NlConstants;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.model.*;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import org.intellij.lang.annotations.JdkConstants.InputEventMask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Interaction where you insert a new component into a parent layout (which can vary
 * during the interaction -- as you drag across the canvas, different layout parents
 * become eligible based on the mouse pointer).
 * <p>
 * There are multiple types of insert modes:
 * <ul>
 *   <li>Copy. This is typically the interaction used when dragging from the palette;
 *       a new copy of the components are created. This can also be achieved when
 *       dragging with a modifier key.</li>
 *   <li>Move. This is typically done by dragging one or more widgets around in
 *       the canvas; when moving the widget within a single parent, it may only
 *       translate into some updated layout parameters or widget reordering, whereas
 *       when moving from one parent to another widgets are moved in the hierarchy
 *       as well.</li>
 *   <li>A paste is similar to a copy. It typically tries to preserve internal
 *   relationships and id's when possible. If you for example select 3 widgets and
 *   cut them, if you paste them the widgets will come back in the exact same place
 *   with the same id's. If you paste a second time, the widgets will now all have
 *   new unique id's (and any internal references to each other are also updated.)</li>
 * </ul>
 */
public class DragDropInteraction extends Interaction {

  /**
   * The surface associated with this interaction.
   */
  private final NlDesignSurface myDesignSurface;

  /**
   * The components being dragged
   */
  private final List<NlComponent> myDraggedComponents;

  /**
   * The current view group handler, if any. This is the layout widget we're dragging over (or the
   * nearest layout widget containing the non-layout views we're dragging over
   */
  private ViewGroupHandler myCurrentHandler;

  /**
   * The drag handler for the layout view, if it supports drags
   */
  private DragHandler myDragHandler;

  /**
   * The view group we're dragging over/into
   */
  private NlComponent myDragReceiver;

  /**
   * Whether we're copying or moving
   */
  private DragType myType = DragType.MOVE;

  /**
   * The last accessed screen view.
   */
  private ScreenView myScreenView;

  /**
   * The transfer item for this drag if any
   */
  private DnDTransferItem myTransferItem;

  public DragDropInteraction(@NotNull NlDesignSurface designSurface, @NotNull List<NlComponent> dragged) {
    myDesignSurface = designSurface;
    myDraggedComponents = dragged;
  }

  public void setType(DragType type) {
    myType = type;
    if (myDragHandler != null) {
      myDragHandler.setDragType(type);
    }
  }

  public void setTransferItem(@NotNull DnDTransferItem item) {
    myTransferItem = item;
  }

  @Nullable
  public DnDTransferItem getTransferItem() {
    return myTransferItem;
  }

  @Override
  public void begin(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask int modifiers) {
    super.begin(x, y, modifiers);
    moveTo(x, y, modifiers, false);
    myDesignSurface.startDragDropInteraction();
  }

  @Override
  public void update(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask int modifiers) {
    super.update(x, y, modifiers);
    moveTo(x, y, modifiers, false);
  }

  @Override
  public void end(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask int modifiers, boolean canceled) {
    super.end(x, y, modifiers, canceled);
    moveTo(x, y, modifiers, !canceled);
    myScreenView = myDesignSurface.getScreenView(x, y);
    if (myScreenView != null && !canceled) {
      myScreenView.getModel().notifyModified(NlModel.ChangeType.DND_END);
    }
    if (canceled && myDragHandler != null) {
      myDragHandler.cancel();
    }
    myDesignSurface.stopDragDropInteraction();
  }

  private void moveTo(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask final int modifiers, boolean commit) {
    myScreenView = myDesignSurface.getScreenView(x, y);
    if (myScreenView == null) {
      return;
    }
    myDesignSurface.getLayeredPane().scrollRectToVisible(
      new Rectangle(x - NlConstants.DEFAULT_SCREEN_OFFSET_X, y - NlConstants.DEFAULT_SCREEN_OFFSET_Y,
                    2 * NlConstants.DEFAULT_SCREEN_OFFSET_X, 2 * NlConstants.DEFAULT_SCREEN_OFFSET_Y));
    final int ax = Coordinates.getAndroidX(myScreenView, x);
    final int ay = Coordinates.getAndroidY(myScreenView, y);

    Project project = myScreenView.getModel().getProject();
    ViewGroupHandler handler = findViewGroupHandlerAt(ax, ay);

    if (handler != myCurrentHandler) {
      if (myDragHandler != null) {
        myDragHandler.cancel();
        myDragHandler = null;
        myScreenView.getSurface().repaint();
      }

      myCurrentHandler = handler;
      if (myCurrentHandler != null) {
        assert myDragReceiver != null;

        String error = null;
        ViewHandlerManager viewHandlerManager = ViewHandlerManager.get(project);
        for (NlComponent component : myDraggedComponents) {
          if (!myCurrentHandler.acceptsChild(myDragReceiver, component, ax, ay)) {
            error = String.format("<%1$s> does not accept <%2$s> as a child", myDragReceiver.getTagName(), component.getTagName());
            break;
          }
          ViewHandler viewHandler = viewHandlerManager.getHandler(component);
          if (viewHandler != null && !viewHandler.acceptsParent(myDragReceiver, component)) {
            error = String.format("<%1$s> does not accept <%2$s> as a parent", component.getTagName(), myDragReceiver.getTagName());
            break;
          }
        }
        if (error == null) {
          myDragHandler = myCurrentHandler.createDragHandler(new ViewEditorImpl(myScreenView), myDragReceiver, myDraggedComponents, myType);
          if (myDragHandler != null) {
            myDragHandler
              .start(Coordinates.getAndroidX(myScreenView, myStartX), Coordinates.getAndroidY(myScreenView, myStartY), myStartMask);
          }
        }
        else {
          myCurrentHandler = null;
        }
      }
    }

    if (myDragHandler != null && myCurrentHandler != null) {
      String error = myDragHandler.update(ax, ay, modifiers);
      final List<NlComponent> added = Lists.newArrayList();
      if (commit && error == null) {
        added.addAll(myDraggedComponents);
        final NlModel model = myScreenView.getModel();
        XmlFile file = model.getFile();
        String label = myType.getDescription();
        WriteCommandAction action = new WriteCommandAction(project, label, file) {
          @Override
          protected void run(@NotNull Result result) throws Throwable {
            InsertType insertType = model.determineInsertType(myType, myTransferItem, false /* not for preview */);
            myDragHandler.commit(ax, ay, modifiers, insertType); // TODO: Run this *after* making a copy
          }
        };
        action.execute();
        model.notifyModified(NlModel.ChangeType.DND_COMMIT);
        // Select newly dropped components
        model.getSelectionModel().setSelection(added);
      }
      myScreenView.getSurface().repaint();
    }
  }

  /**
   * Cached handler for the most recent call to {@link #findViewGroupHandlerAt}, this
   * corresponds to the result found for {@link #myCachedComponent} (which may not be
   * the component corresponding to the view handler. E.g. if you have a LinearLayout
   * with a button inside, the view handler will always return the view handler for the
   * LinearLayout, even when pointing at the button.)
   */
  private ViewGroupHandler myCachedHandler;

  /**
   * Cached handler for the most recent call to {@link #findViewGroupHandlerAt}
   */
  private NlComponent myCachedComponent;

  @Nullable
  private ViewGroupHandler findViewGroupHandlerAt(@AndroidCoordinate int x, @AndroidCoordinate int y) {
    final ScreenView screenView = myDesignSurface.getScreenView(x, y);
    if (screenView == null) {
      return null;
    }
    NlModel model = screenView.getModel();
    NlComponent component = model.findLeafAt(x, y, true);
    component = excludeDraggedComponents(component);
    if (component == myCachedComponent && myCachedHandler != null) {
      return myCachedHandler;
    }

    myCachedComponent = component;
    myCachedHandler = null;

    ViewHandlerManager handlerManager = ViewHandlerManager.get(model.getFacet());
    while (component != null) {
      Object handler = handlerManager.getHandler(component);

      if (handler instanceof ViewGroupHandler && acceptsDrop(component, (ViewGroupHandler)handler, x, y)) {
        myCachedHandler = (ViewGroupHandler)handlerManager.getHandler(component);
        myDragReceiver = component; // HACK: This method should not side-effect set this; instead the method should compute it!
        return myCachedHandler;
      }

      component = component.getParent();
    }

    return null;
  }

  @Nullable
  private NlComponent excludeDraggedComponents(@Nullable NlComponent component) {
    NlComponent receiver = component;
    while (component != null) {
      if (myDraggedComponents.contains(component)) {
        receiver = component.getParent();
      }
      component = component.getParent();
    }
    return receiver;
  }

  private boolean acceptsDrop(@NotNull NlComponent parent,
                              @NotNull ViewGroupHandler parentHandler,
                              @AndroidCoordinate int x,
                              @AndroidCoordinate int y) {
    ScreenView view = myDesignSurface.getScreenView(x, y);
    assert view != null;

    ViewHandlerManager manager = ViewHandlerManager.get(view.getModel().getFacet());

    Predicate<NlComponent> acceptsChild = child -> parentHandler.acceptsChild(parent, child, x, y);

    Predicate<NlComponent> acceptsParent = child -> {
      ViewHandler childHandler = manager.getHandler(child);
      return childHandler != null && childHandler.acceptsParent(parent, child);
    };

    return myDraggedComponents.stream().allMatch(acceptsChild.and(acceptsParent));
  }

  @Override
  public List<Layer> createOverlays() {
    return Collections.singletonList(new DragLayer());
  }

  @NotNull
  public List<NlComponent> getDraggedComponents() {
    return myDraggedComponents;
  }

  /**
   * An {@link Layer} for the {@link DragDropInteraction}; paints feedback from
   * the current drag handler, if any
   */
  private class DragLayer extends Layer {

    /**
     * Constructs a new {@link DragLayer}.
     */
    public DragLayer() {
    }

    @Override
    public void create() {
    }

    @Override
    public void dispose() {
    }

    @Override
    public void paint(@NotNull Graphics2D gc) {
      if (myDragHandler != null) {
        myDragHandler.paint(new NlGraphics(gc, myScreenView));
      }
    }
  }
}
