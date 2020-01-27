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
package com.android.tools.idea.common.surface;

import static java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL;

import com.android.tools.adtui.actions.ZoomType;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.adtui.ui.AdtUiCursors;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.uibuilder.graphics.NlConstants;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities;
import com.android.tools.idea.uibuilder.model.NlDropEvent;
import com.android.tools.idea.uibuilder.surface.DragDropInteraction;
import com.android.tools.idea.uibuilder.surface.PanInteraction;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.intellij.lang.annotations.JdkConstants;
import org.intellij.lang.annotations.JdkConstants.InputEventMask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * The {@linkplain InteractionManager} is is the central manager of interactions; it is responsible for
 * recognizing when particular interactions should begin and terminate. It listens to the mouse and keyboard
 * events to find out when to start interactions and in order to update the interactions along the way.
 */
public class InteractionManager implements Disposable {
  private static final int HOVER_DELAY_MS = Registry.intValue("ide.tooltip.initialDelay");
  private static final int SCROLL_END_TIME_MS = 500;

  /**
   * The canvas which owns this {@linkplain InteractionManager}.
   */
  @NotNull
  private final DesignSurface mySurface;

  /**
   * The {@linkplain InteractionHandler} which provides the {@linkplain Interaction} during interacting.
   */
  @NotNull
  private final InteractionHandler myInteractionHandler;

  /**
   * The currently executing {@link Interaction}, or null.
   */
  @Nullable
  private Interaction myCurrentInteraction;

  /**
   * The list of overlays associated with {@link #myCurrentInteraction}. Will be
   * null before it has been initialized lazily by the buildDisplayList routine (the
   * initialized value can never be null, but it can be an empty collection).
   */
  @Nullable
  private List<Layer> myLayers;

  /**
   * Most recently seen mouse position (x coordinate). We keep a copy of this
   * value since we sometimes need to know it when we aren't told about the
   * mouse position (such as when a keystroke is received, such as an arrow
   * key in order to tweak the current drop position)
   */
  @SwingCoordinate
  protected int myLastMouseX;

  /**
   * Most recently seen mouse position (y coordinate). We keep a copy of this
   * value since we sometimes need to know it when we aren't told about the
   * mouse position (such as when a keystroke is received, such as an arrow
   * key in order to tweak the current drop position)
   */
  @SwingCoordinate
  protected int myLastMouseY;

  /**
   * Most recently seen mouse mask. We keep a copy of this since in some
   * scenarios (such as on a drag interaction) we don't get access to it.
   */
  @InputEventMask
  protected int myLastModifiersEx;

  /**
   * A timer used to control when to initiate a mouse hover action. It is active only when
   * the mouse is within the design surface. It gets reset every time the mouse is moved, and
   * fires after a certain delay once the mouse comes to rest.
   */
  private final Timer myHoverTimer;

  /**
   * A timer used to decide when we can end the scroll motion.
   */
  private final Timer myScrollEndTimer;

  private final ActionListener myScrollEndListener;

  /**
   * Listener for mouse motion, click and keyboard events.
   */
  private final Listener myListener;

  /**
   * Drop target installed by this manager
   */
  private DropTarget myDropTarget;

  /**
   * Indicates whether listeners have been registered to listen for interactions
   */
  private boolean myIsListening;

  /**
   * Flag to indicate if interaction has canceled by pressing escape button.
   */
  private boolean myIsInteractionCanceled;

  /**
   * Constructs a new {@link InteractionManager} for the given {@link DesignSurface}.
   *
   * @param surface The surface which controls this {@link InteractionManager}
   */
  public InteractionManager(@NotNull DesignSurface surface, @NotNull InteractionHandler provider) {
    mySurface = surface;
    myInteractionHandler = provider;
    Disposer.register(surface, this);

    myListener = new Listener();

    myHoverTimer = new Timer(HOVER_DELAY_MS, null);
    myHoverTimer.setRepeats(false);

    myScrollEndTimer = new Timer(SCROLL_END_TIME_MS, null);
    myScrollEndTimer.setRepeats(false);

    myScrollEndListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myScrollEndTimer.removeActionListener(this);
        finishInteraction(new MouseWheelStopEvent(e, getInteractionInformation()), false);
      }
    };
  }

  @Override
  public void dispose() {
    myHoverTimer.stop();
    myScrollEndTimer.stop();
  }

  /**
   * Returns the canvas associated with this {@linkplain InteractionManager}.
   *
   * @return The {@link DesignSurface} associated with this {@linkplain InteractionManager}.
   * Never null.
   */
  @NotNull
  public DesignSurface getSurface() {
    return mySurface;
  }

  public boolean isPanning() {
    return myCurrentInteraction instanceof PanInteraction;
  }

  /**
   * This will registers all the listeners to {@link DesignSurface} needed by the {@link InteractionManager}.<br>
   * Do nothing if it is listening already.
   * @see #stopListening()
   */
  public void startListening() {
    if (myIsListening) {
      return;
    }
    JComponent layeredPane = mySurface.getLayeredPane();
    layeredPane.addMouseMotionListener(myListener);
    layeredPane.addMouseWheelListener(myListener);
    layeredPane.addMouseListener(myListener);
    layeredPane.addKeyListener(myListener);

    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      myDropTarget = new DropTarget(mySurface.getLayeredPane(), DnDConstants.ACTION_COPY_OR_MOVE, myListener, true, null);
    }
    myHoverTimer.addActionListener(myListener);
    myIsListening = true;
  }

  /**
   * This will unregister all the listeners previously registered by from {@link DesignSurface}.<br>
   * Do nothing if it is not listening.
   * @see #startListening()
   */
  public void stopListening() {
    if (!myIsListening) {
      return;
    }
    JComponent layeredPane = mySurface.getLayeredPane();
    layeredPane.removeMouseMotionListener(myListener);
    layeredPane.removeMouseWheelListener(myListener);
    layeredPane.removeMouseListener(myListener);
    layeredPane.removeKeyListener(myListener);
    if (myDropTarget != null) {
      myDropTarget.removeDropTargetListener(myListener);
    }
    myHoverTimer.removeActionListener(myListener);
    myHoverTimer.stop();
    myIsListening = false;
  }

  /**
   * Starts the given interaction.
   *
   * @param event       The event makes the interaction start.
   * @param interaction The given interaction to start
   */
  private void startInteraction(@NotNull InteractionEvent event, @Nullable Interaction interaction) {
    if (myCurrentInteraction != null) {
      finishInteraction(event, true);
      assert myCurrentInteraction == null;
    }

    if (interaction != null) {
      myCurrentInteraction = interaction;
      myCurrentInteraction.begin(event);
      myLayers = interaction.createOverlays();
    }
  }

  /**
   * Returns the currently active overlays, if any
   */
  @Nullable
  public List<Layer> getLayers() {
    return myLayers;
  }

  /**
   * Returns the most recently observed input event mask
   */
  @InputEventMask
  public int getLastModifiersEx() {
    return myLastModifiersEx;
  }

  /**
   * TODO: Remove this function when test framework use real interaction for testing.
   */
  @TestOnly
  public void setModifier(@InputEventMask int modifier) {
    myLastModifiersEx = modifier;
  }

  /**
   * Updates the current interaction, if any, for the given event.
   */
  private void updateMouseMoved(@NotNull MouseEvent event, @SwingCoordinate int x, @SwingCoordinate int y) {
    if (myCurrentInteraction != null) {
      myCurrentInteraction.update(new MouseMovedEvent(event, getInteractionInformation()));
    }
    else {
      myInteractionHandler.hoverWhenNoInteraction(x, y, myLastModifiersEx);
    }
  }

  /**
   * Finish the given interaction, either from successful completion or from
   * cancellation.
   *
   * @param event    The event which makes the current interaction end.
   * @param canceled True if and only if the interaction was canceled.
   */
  private void finishInteraction(@NotNull InteractionEvent event, boolean canceled) {
    if (myCurrentInteraction != null) {
      if (canceled) {
        myCurrentInteraction.cancel(event);
      }
      else {
        myCurrentInteraction.commit(event);
      }
      if (myLayers != null) {
        for (Layer layer : myLayers) {
          //noinspection SSBasedInspection
          layer.dispose();
        }
        myLayers = null;
      }
      myCurrentInteraction = null;
      myLastModifiersEx = 0;
      myInteractionHandler.hoverWhenNoInteraction(myLastMouseX, myLastMouseY, myLastModifiersEx);
      updateCursor(myLastMouseX, myLastMouseY, myLastModifiersEx);
      mySurface.repaint();
    }
  }

  /**
   * Update the cursor to show the type of operation we expect on a mouse press:
   * <ul>
   * <li>Over a resizable region, show the resize cursor.
   * <li>Over a SceneView, show the cursor which provide by the Scene.
   * <li>Otherwise, show the default arrow cursor.
   * </ul>
   */
  void updateCursor(@SwingCoordinate int x, @SwingCoordinate int y, @JdkConstants.InputEventMask int modifiersEx) {
    {
      Cursor cursor;
      if (myCurrentInteraction == null) {
        cursor = myInteractionHandler.getCursorWhenNoInteraction(x, y, modifiersEx);
      }
      else {
        cursor = myCurrentInteraction.getCursor();
      }
      mySurface.setCursor(cursor != Cursor.getDefaultCursor() ? cursor : null);
    }
  }

  public boolean isInteractionInProgress() {
    return myCurrentInteraction != null;
  }

  /**
   * Helper class which implements the {@link MouseMotionListener},
   * {@link MouseListener} and {@link KeyListener} interfaces.
   */
  private class Listener
    implements MouseMotionListener, MouseListener, KeyListener, DropTargetListener, ActionListener, MouseWheelListener {

    // --- Implements MouseListener ----

    @Override
    public void mouseClicked(@NotNull MouseEvent event) {
      // TODO(b/142953949): send MouseClickEvent into myCurrentInteraction.
      int x = event.getX();
      int y = event.getY();
      int clickCount = event.getClickCount();

      if (clickCount == 2 && event.getButton() == MouseEvent.BUTTON1) {
        myInteractionHandler.doubleClick(x, y, myLastModifiersEx);
        return;
      }

      // No need to navigate XML when click was done holding some modifiers (e.g multi-selecting).
      if (clickCount == 1 && event.getButton() == MouseEvent.BUTTON1 && !event.isShiftDown() && !AdtUiUtils.isActionKeyDown(event)) {
        myInteractionHandler.singleClick(x, y, myLastModifiersEx);
      }

      if (event.isPopupTrigger()) {
        myInteractionHandler.popupMenuTrigger(event);
      }
    }

    @Override
    public void mousePressed(@NotNull MouseEvent event) {
      if (event.getID() == MouseEvent.MOUSE_PRESSED) {
        mySurface.getLayeredPane().requestFocusInWindow();
      }

      myIsInteractionCanceled = false;

      myLastMouseX = event.getX();
      myLastMouseY = event.getY();
      myLastModifiersEx = event.getModifiersEx();

      if (event.isPopupTrigger()) {
        myInteractionHandler.popupMenuTrigger(event);
        event.consume();
        return;
      }
      if (SwingUtilities.isRightMouseButton(event)) {
        // On Windows the convention is that the mouse up event triggers the popup.
        // If the user is starting a right click, return and handle the popup in mouseReleased.
        return;
      }
      // TODO: move this logic into InteractionHandler.createInteractionOnPressed()
      if (myCurrentInteraction instanceof PanInteraction) {
        myCurrentInteraction.update(new MousePressedEvent(event, getInteractionInformation()));
        updateCursor(myLastMouseX, myLastMouseY, myLastModifiersEx);
        return;
      }
      else if (SwingUtilities.isMiddleMouseButton(event)) {
        startInteraction(new MousePressedEvent(event, getInteractionInformation()), new PanInteraction(mySurface));
        updateCursor(myLastMouseX, myLastMouseY, myLastModifiersEx);
        return;
      }

      Interaction interaction = myInteractionHandler.createInteractionOnPressed(myLastMouseX, myLastMouseY, myLastModifiersEx);
      if (interaction != null) {
        startInteraction(new MousePressedEvent(event, getInteractionInformation()), interaction);
      }
    }

    @Override
    public void mouseReleased(@NotNull MouseEvent event) {
      // TODO: Should we update the last mouse position and modifiers here?
      if (myIsInteractionCanceled) {
        return;
      }
      if (event.isPopupTrigger()) {
        // On Windows the convention is that the mouse up event triggers the popup.
        // Handle popup triggers here for Windows.
        myInteractionHandler.popupMenuTrigger(event);
        return;
      }

      if (myCurrentInteraction instanceof PanInteraction) {
        // This never be true because PanInteraction never be created when NELE_NEW_INTERACTION_INTERFACE is disable.
        // Consume event, but only stop panning if the middle mouse button was released.
        if (SwingUtilities.isMiddleMouseButton(event)) {
          finishInteraction(new MouseReleasedEvent(event, getInteractionInformation()), true);
        }
        else {
          myCurrentInteraction.update(
            new MouseReleasedEvent(event, new InteractionInformation(event.getX(), event.getY(), event.getModifiersEx())));
          updateCursor(myLastMouseX, myLastMouseY, myLastModifiersEx);
        }
        return;
      }

      if (interceptPanInteraction(event)) {
        if (SwingUtilities.isMiddleMouseButton(event)) {
          // Consume event, but only disable panning if the middle mouse button was released.
          setPanning(false);
        }
        return;
      }
      else if (event.getButton() > 1 || SystemInfo.isMac && event.isControlDown()) {
        // mouse release from a popup click (the popup menu was posted on
        // the mousePressed event
        return;
      }

      int x = event.getX();
      int y = event.getY();
      int modifiersEx = event.getModifiersEx();

      if (myCurrentInteraction == null) {
        myInteractionHandler.mouseReleaseWhenNoInteraction(x, y, modifiersEx);
        updateCursor(x, y, modifiersEx);
      }
      else {
        finishInteraction(new MouseReleasedEvent(event, getInteractionInformation()), false);
        myCurrentInteraction = null;
      }
      mySurface.repaint();
    }

    @Override
    public void mouseEntered(@NotNull MouseEvent event) {
      myHoverTimer.restart();
    }

    @Override
    public void mouseExited(@NotNull MouseEvent event) {
      myHoverTimer.stop();
    }

    // --- Implements MouseMotionListener ----

    /**
     * TODO(b/142953949): Should always update last mouse and modifiersEx.
     */
    @Override
    public void mouseDragged(MouseEvent event) {
      if (myIsInteractionCanceled) {
        return;
      }
      int x = event.getX();
      int y = event.getY();

      if (myCurrentInteraction instanceof PanInteraction) {
        myCurrentInteraction.update(new MouseDraggedEvent(event, new InteractionInformation(x, y, event.getModifiersEx())));
        updateCursor(x, y, event.getModifiersEx());
        return;
      }

      if (!SwingUtilities.isLeftMouseButton(event)) {
        // mouse drag from a popup click (the popup menu was posted on
        // the mousePressed event
        return;
      }

      int modifiersEx = event.getModifiersEx();
      if (myCurrentInteraction != null) {
        myLastMouseX = x;
        myLastMouseY = y;
        myLastModifiersEx = modifiersEx;
        myCurrentInteraction.update(new MouseDraggedEvent(event, getInteractionInformation()));
        updateCursor(x, y, modifiersEx);
        mySurface.getLayeredPane().scrollRectToVisible(
          new Rectangle(x - NlConstants.DEFAULT_SCREEN_OFFSET_X, y - NlConstants.DEFAULT_SCREEN_OFFSET_Y,
                        2 * NlConstants.DEFAULT_SCREEN_OFFSET_X, 2 * NlConstants.DEFAULT_SCREEN_OFFSET_Y));
        mySurface.repaint();
      }
      else {
        x = myLastMouseX; // initiate the drag from the mousePress location, not the point we've dragged to
        y = myLastMouseY;
        myLastModifiersEx = modifiersEx;

        Interaction interaction = myInteractionHandler.createInteractionOnDrag(x, y, myLastModifiersEx);

        if (interaction != null) {
          startInteraction(new MouseDraggedEvent(event, getInteractionInformation()), interaction);
        }
        updateCursor(x, y, modifiersEx);
      }
      myHoverTimer.restart();
    }

    @Override
    public void mouseMoved(@NotNull MouseEvent event) {
      int x = event.getX();
      int y = event.getY();
      int modifiersEx = event.getModifiersEx();
      myLastMouseX = x;
      myLastMouseY = y;
      myLastModifiersEx = modifiersEx;

      if (myCurrentInteraction instanceof PanInteraction) {
        myCurrentInteraction.update(new MouseMovedEvent(event, getInteractionInformation()));
        updateCursor(x, y, modifiersEx);
        return;
      }

      updateMouseMoved(event, x, y);
      updateCursor(x, y, modifiersEx);

      mySurface.repaint();
      myHoverTimer.restart();
    }

    // --- Implements KeyListener ----

    @Override
    public void keyTyped(KeyEvent event) {
      myLastModifiersEx = event.getModifiersEx();
    }

    @Override
    public void keyPressed(KeyEvent event) {
      int modifiersEx = event.getModifiersEx();
      int keyCode = event.getKeyCode();

      myLastModifiersEx = modifiersEx;

      Scene scene = mySurface.getScene();
      if (scene != null) {
        // TODO: Make it so this step is only done when necessary. i.e Move to ConstraintSceneInteraction.keyPressed().
        // Since holding some of these keys might change some visuals, repaint.
        scene.needsRebuildList();
        scene.repaint();
      }

      // Give interactions a first chance to see and consume the key press
      if (myCurrentInteraction != null) {
        // unless it's "Escape", which cancels the interaction
        if (keyCode == KeyEvent.VK_ESCAPE) {
          finishInteraction(new KeyPressedEvent(event, getInteractionInformation()), true);
          myIsInteractionCanceled = true;
          return;
        }
      }

      if (isPanningKeyboardKey(event)) {
        // TODO (b/142953949): Replace this by startInteraction with PanInteraction.
        setPanning(new KeyPressedEvent(event, getInteractionInformation()), true);
        return;
      }

      // The below shortcuts only apply without modifier keys.
      // (Zooming with "+" *may* require modifier keys, since on some keyboards you press for
      // example Shift+= to create the + key.
      if (event.isAltDown() || event.isMetaDown() || event.isShiftDown() || event.isControlDown()) {
        return;
      }

      if (keyCode == KeyEvent.VK_DELETE || keyCode == KeyEvent.VK_BACK_SPACE) {
        SceneView sceneView = mySurface.getSceneView(myLastMouseX, myLastMouseY);
        if (sceneView != null) {
          SelectionModel model = sceneView.getSelectionModel();
          if (!model.isEmpty() && !ConstraintComponentUtilities.clearSelectedConstraint(mySurface)) {
            List<NlComponent> selection = model.getSelection();
            sceneView.getModel().delete(selection);
          }
        }
      }
    }

    @Override
    public void keyReleased(KeyEvent event) {
      myLastModifiersEx = event.getModifiersEx();

      Scene scene = mySurface.getScene();
      if (scene != null) {
        // TODO: Make it so this step is only done when necessary. i.e Move to ConstraintSceneInteraction.keyReleased().
        // Since holding some of these keys might change some visuals, repaint.
        scene.needsRebuildList();
        scene.repaint();
      }
      if (myCurrentInteraction != null) {
        myCurrentInteraction.update(new KeyReleasedEvent(event, getInteractionInformation()));
      }

      if (isPanningKeyboardKey(event)) {
        // TODO (b/142953949): this should be handled by PanInteraction itself.
        setPanning(new KeyReleasedEvent(event, getInteractionInformation()), false);
        updateCursor(myLastMouseX, myLastMouseY, myLastModifiersEx);
      }
    }

    // ---- Implements DropTargetListener ----

    @Override
    public void dragEnter(@NotNull DropTargetDragEvent dragEvent) {
      if (myCurrentInteraction == null) {
        Point location = dragEvent.getLocation();
        myLastMouseX = location.x;
        myLastMouseY = location.y;
        Interaction interaction = myInteractionHandler.createInteractionOnDragEnter(dragEvent);
        startInteraction(new DragEnterEvent(dragEvent, getInteractionInformation()), interaction);
      }
    }

    @Override
    public void dragOver(DropTargetDragEvent dragEvent) {
      Point location = dragEvent.getLocation();
      myLastMouseX = location.x;
      myLastMouseY = location.y;
      NlDropEvent event = new NlDropEvent(dragEvent);
      if (myCurrentInteraction == null) {
        event.reject();
        return;
      }
      myCurrentInteraction.update(new DragOverEvent(dragEvent, getInteractionInformation()));
    }

    @Override
    public void dropActionChanged(DropTargetDragEvent event) {
      if (myCurrentInteraction != null) {
        myCurrentInteraction.update(new DropActionChangedEvent(event, getInteractionInformation()));
      }
    }

    @Override
    public void dragExit(DropTargetEvent event) {
      if (myCurrentInteraction instanceof DragDropInteraction) {
        finishInteraction(new DragExistEvent(event, getInteractionInformation()), true);
      }
    }

    @Override
    public void drop(@NotNull final DropTargetDropEvent dropEvent) {
      Point location = dropEvent.getLocation();
      myLastMouseX = location.x;
      myLastMouseY = location.y;

      NlDropEvent event = new NlDropEvent(dropEvent);
      if (!(myCurrentInteraction instanceof DragDropInteraction)) {
        event.reject();
        return;
      }
      finishInteraction(new DropEvent(dropEvent, getInteractionInformation()), false);
    }

    // --- Implements ActionListener ----

    @Override
    public void actionPerformed(ActionEvent e) {
      if (e.getSource() != myHoverTimer) {
        return;
      }

      int x = myLastMouseX; // initiate the drag from the mousePress location, not the point we've dragged to
      int y = myLastMouseY;

      // TODO (b/142953949): Should layer be hovered when action performed?
      for (SceneView sceneView : mySurface.getSceneViews()) {
        sceneView.onHover(x, y);
      }
    }

    // --- Implements MouseWheelListener ----

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
      int x = e.getX();
      int y = e.getY();
      int scrollAmount;
      if (e.getScrollType() == WHEEL_UNIT_SCROLL) {
        scrollAmount = e.getUnitsToScroll();
      }
      else {
        scrollAmount = (e.getWheelRotation() < 0 ? -1 : 1);
      }

      // On Touchpad handling horizontal scrolling, the horizontal scroll is
      // interpreted as a mouseWheel Event with Shift down.
      // If some scrolling imprecision happens for other scroll interaction, it might be good
      // to do the filtering at a higher level
      if (!e.isShiftDown() && (SystemInfo.isMac && e.isMetaDown() || e.isControlDown())) {
        if (scrollAmount < 0) {
          mySurface.zoom(ZoomType.IN, x, y);
        }
        else if (scrollAmount > 0) {
          mySurface.zoom(ZoomType.OUT, x, y);
        }
        return;
      }

      if (myCurrentInteraction == null) {
        Interaction scrollInteraction = myInteractionHandler.createInteractionOnMouseWheelMoved(e);

        if (scrollInteraction == null) {
          // There is no component consuming the scroll
          e.getComponent().getParent().dispatchEvent(e);
          return;
        }

        // Start a scroll interaction and a timer to bundle all the scroll events
        startInteraction(new MouseWheelMovedEvent(e, getInteractionInformation()), scrollInteraction);
        myScrollEndTimer.addActionListener(myScrollEndListener);
      }

      boolean isScrollInteraction = myCurrentInteraction instanceof ScrollInteraction;
      myCurrentInteraction.update(new MouseWheelMovedEvent(e, getInteractionInformation()));
      if (isScrollInteraction) {
        myScrollEndTimer.restart();
      }
    }
  }

  void setPanning(boolean panning) {
    if (panning && !(myCurrentInteraction instanceof PanInteraction)) {
      startInteraction(new InteractionNonInputEvent(getInteractionInformation()), new PanInteraction(mySurface));
      updateCursor(myLastMouseX, myLastMouseY, myLastModifiersEx);
    }
    else if (!panning && myCurrentInteraction instanceof PanInteraction) {
      finishInteraction(new InteractionNonInputEvent(getInteractionInformation()), false);
      updateCursor(myLastMouseX, myLastMouseY, myLastModifiersEx);
    }
  }

  private void setPanning(@NotNull InteractionEvent event, boolean panning) {
    if (panning && !(myCurrentInteraction instanceof PanInteraction)) {
      startInteraction(event, new PanInteraction(mySurface));
      updateCursor(myLastMouseX, myLastMouseY, myLastModifiersEx);
    }
    else if (!panning && myCurrentInteraction instanceof PanInteraction) {
      finishInteraction(event, false);
      updateCursor(myLastMouseX, myLastMouseY, myLastModifiersEx);
    }
  }

  /**
   * Intercepts a mouse event if panning had already started or if the mouse wheel button is down.
   *
   * @param event {@link MouseEvent} passed by {@link MouseMotionListener#mouseDragged}
   * @return true if the event should be intercepted and handled, false otherwise.
   */
  public boolean interceptPanInteraction(@NotNull MouseEvent event) {
    boolean wheelClickDown = SwingUtilities.isMiddleMouseButton(event);
    if (isPanning() || wheelClickDown) {
      boolean leftClickDown = SwingUtilities.isLeftMouseButton(event);
      mySurface.setCursor((leftClickDown || wheelClickDown) ? AdtUiCursors.GRABBING : AdtUiCursors.GRAB);
      return true;
    }
    return false;
  }

  private static boolean isPanningKeyboardKey(@NotNull KeyEvent event) {
    return event.getKeyCode() == DesignSurfaceShortcut.PAN.getKeyCode();
  }

  /**
   * Cancels the current running interaction
   */
  public void cancelInteraction() {
    finishInteraction(new InteractionNonInputEvent(getInteractionInformation()), true);
  }

  /**
   * Create and return a <b>copy</b> of current {@link InteractionInformation} data.
   */
  @NotNull
  private InteractionInformation getInteractionInformation() {
    return new InteractionInformation(myLastMouseX, myLastMouseY, myLastModifiersEx);
  }

  @VisibleForTesting
  public Object getListener() {
    return myListener;
  }
}
