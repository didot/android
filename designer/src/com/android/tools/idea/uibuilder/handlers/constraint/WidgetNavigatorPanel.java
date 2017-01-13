/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.constraint;

import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.uibuilder.graphics.NlConstants;
import com.android.tools.idea.uibuilder.model.ModelListener;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.*;
import com.android.tools.sherpa.drawing.BlueprintColorSet;
import com.android.tools.sherpa.drawing.ColorSet;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.popup.*;
import com.intellij.ui.AncestorListenerAdapter;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

import static com.android.tools.idea.uibuilder.surface.NlDesignSurface.ScreenMode.BOTH;

/**
 * UI component for Navigator Panel showing a miniature representation of the NlDesignSurface
 * allowing to easily scroll inside the NlDesignSurface when the UI builder is zoomed.
 * The panel can be collapsed and expanded. The default state is collapsed
 */
public class WidgetNavigatorPanel extends JPanel
  implements DesignSurfaceListener, PanZoomListener, ModelListener, JBPopupListener {

  public static final String TITLE = "Pan and Zoom";
  public static final String HINT = "(Scroll to Zoom)";
  private static final IconButton CANCEL_BUTTON = new IconButton("Close", AllIcons.Actions.Close, AllIcons.Actions.CloseHovered);
  private static final int SCREEN_SPACE = NlConstants.SCREEN_DELTA;
  private static final Dimension PREFERRED_SIZE = new Dimension(250, 216);

  private static final BlueprintColorSet BLUEPRINT_COLOR_SET = new BlueprintColorSet();
  private static final JBColor DRAWING_SURFACE_RECTANGLE_COLOR = JBColor.red;
  private static final JBColor OVERLAY_COLOR = new JBColor(new Color(232, 232, 232, 127), new Color(80, 80, 80, 127));
  private static final JBColor NORMAL_SCREEN_VIEW_COLOR = new JBColor(Gray._255, Gray._240);
  private static final Color BLUEPRINT_SCREEN_VIEW_COLOR = BLUEPRINT_COLOR_SET.getBackground();
  private static final Color COMPONENT_STROKE_COLOR = BLUEPRINT_COLOR_SET.getFrames();
  private static final Color BACKGROUND_COLOR = BLUEPRINT_COLOR_SET.getBackground();

  private final ColorSet myColorSet;
  private MiniMap myMiniMap;

  @Nullable private NlDesignSurface myDesignSurface;
  @Nullable private NlComponent myComponent;
  @Nullable private JBPopup myContainerPopup;
  private Dimension myCurrentScreenViewSize;
  private Dimension myDesignSurfaceSize;
  private Dimension myDeviceSize;
  private double myScreenViewScale;
  private double myDeviceScale;
  private Point myDesignSurfaceOffset;
  private Point mySecondScreenOffset;
  private Point myCenterOffset;
  private boolean myIsZoomed;
  private int myYScreenNumber;
  private int myXScreenNumber;
  private int myScaledScreenSpace;
  private AncestorListenerAdapter myAncestorListener;

  public WidgetNavigatorPanel(@NotNull NlDesignSurface surface) {
    super(new BorderLayout());
    myDesignSurfaceOffset = new Point();
    mySecondScreenOffset = new Point();
    myCenterOffset = new Point();
    myMiniMap = new MiniMap();
    myAncestorListener = new MyAncestorListenerAdapter();
    myColorSet = BLUEPRINT_COLOR_SET;
    setPreferredSize(PREFERRED_SIZE);
    setSurface(surface);
    updateComponents(null);

    add(myMiniMap, BorderLayout.CENTER);

    // Listening to mouse event
    final MouseInteractionListener listener = new MouseInteractionListener();
    addMouseListener(listener);
    addMouseMotionListener(listener);
    addMouseWheelListener(listener);
    configureUI();
  }

  /**
   * Set up the UI
   */
  public void configureUI() {
    if (myDesignSurface == null) {
      return;
    }
    computeScale(myDesignSurface.getCurrentScreenView(), myDesignSurface.getSize(),
                 myDesignSurface.getContentSize(null));
    computeOffsets(myDesignSurface.getCurrentScreenView());
  }

  /**
   * Set the selected component. If no component is selected, it will set the root of the previous selected component as the selected one
   *
   * @param selectedComponents
   */
  public void updateComponents(@Nullable List<NlComponent> selectedComponents) {
    if (selectedComponents != null && !selectedComponents.isEmpty()) {
      myComponent = selectedComponents.get(0);
    }
    else if (myComponent != null) {
      // If not component are selected, displays the full screen
      myComponent = myComponent.getRoot();
    }
    else if (myDesignSurface != null) {
      final ScreenView currentScreenView = myDesignSurface.getCurrentScreenView();
      if (currentScreenView != null) {
        final List<NlComponent> components = currentScreenView.getModel().getComponents();
        myComponent = !components.isEmpty() ? components.get(0) : null;
      }
    }
    myMiniMap.repaint();
  }

  /* implements DesignSurfaceListener */
  @Override
  public void componentSelectionChanged(@NotNull DesignSurface surface, @NotNull List<NlComponent> selectedComponents) {
    updateComponents(selectedComponents);
    configureUI();
  }

  @Override
  public void screenChanged(@NotNull DesignSurface surface, @Nullable ScreenView screenView) {
    assert surface instanceof NlDesignSurface;
    setSurface((NlDesignSurface)surface);
    assert myDesignSurface != null;
    computeOffsets(myDesignSurface.getCurrentScreenView());
    myMiniMap.repaint();
  }

  /**
   * The model of the design surface changed
   */
  @Override
  public void modelChanged(@NotNull DesignSurface surface, @Nullable NlModel model) {
    assert surface instanceof NlDesignSurface;
    setSurface((NlDesignSurface)surface);
    if (model != null) {
      model.addListener(this);
    }

    // The model change can be triggered by a change of editor, in this case, we want to keep
    // the popup opened but we have to change the content, so we try to find the current selection
    // in the model or at least the component of the current model.
    computeOffsets(surface.getCurrentScreenView());
    if (model != null) {
      List<NlComponent> selection = model.getSelectionModel().getSelection();
      if (selection.isEmpty()) {
        selection = model.getComponents();
      }
      updateComponents(selection);
    }
    configureUI();
    myMiniMap.repaint();
  }

  @Override
  public void modelChangedOnLayout(@NotNull NlModel model, boolean animate) {
    // Do nothing
  }

  @Override
  public boolean activatePreferredEditor(@NotNull DesignSurface surface, @NotNull NlComponent component) {
    return false;
  }

  /* implements ModelListener */

  /**
   * A change occurred inside the model object
   *
   * @param model
   */
  @Override
  public void modelChanged(@NotNull NlModel model) {
    if (myDesignSurface != null) {
      updateDeviceConfiguration(myDesignSurface.getConfiguration());
      updateComponents(model.getComponents());
      updateScreenNumber(myDesignSurface);
      myMiniMap.repaint();
    }
  }

  @Override
  public void modelRendered(@NotNull NlModel model) {
  }

  /* Implements PanZoomListener */
  @Override
  public void zoomChanged(DesignSurface designSurface) {
    assert designSurface instanceof NlDesignSurface;
    setSurface((NlDesignSurface)designSurface);
    myMiniMap.repaint();
  }

  @Override
  public void panningChanged(AdjustmentEvent adjustmentEvent) {
    if (myDesignSurface == null) {
      return;
    }
    final Point scrollPosition = myDesignSurface.getScrollPosition();
    final ScreenView currentScreenView = myDesignSurface.getCurrentScreenView();
    if (currentScreenView != null) {
      myDesignSurfaceSize = myDesignSurface.getSize(myDesignSurfaceSize);
      final Dimension contentSize = myDesignSurface.getContentSize(null);
      computeScale(currentScreenView, myDesignSurfaceSize, contentSize);
    }
    myDesignSurfaceOffset.setLocation(
      scrollPosition.getX() * myScreenViewScale,
      scrollPosition.getY() * myScreenViewScale
    );
    repaint();
  }

  /* Implements JBPopupListener */
  @Override
  public void beforeShown(LightweightWindowEvent event) {
    myContainerPopup = event.asPopup();
  }

  @Override
  public void onClosed(LightweightWindowEvent event) {
    if (myContainerPopup != null) {
      myContainerPopup.removeListener(this);
      myContainerPopup = null;
    }
  }

  /**
   * JPanel where the miniature are drown
   */
  private class MyAncestorListenerAdapter extends AncestorListenerAdapter {
    @Override
    public void ancestorRemoved(AncestorEvent event) {
      super.ancestorRemoved(event);
      if (myContainerPopup != null) {
        myContainerPopup.cancel();
      }
    }
  }

  /**
   * Set the NlDesignSurface to display the minimap from
   *
   * @param surface
   */
  public void setSurface(@Nullable NlDesignSurface surface) {
    updateScreenNumber(surface);
    if (surface == myDesignSurface) {
      return;
    }

    // Removing all listener for the oldSurface
    if (myDesignSurface != null) {
      myDesignSurface.removeListener(this);
      myDesignSurface.removePanZoomListener(this);
      myDesignSurface.removeAncestorListener(myAncestorListener);
      final ScreenView currentScreenView = myDesignSurface.getCurrentScreenView();
      if (currentScreenView != null) {
        currentScreenView.getModel().removeListener(this);
      }
    }

    myDesignSurface = surface;
    if (myDesignSurface == null) {
      return;
    }
    myDesignSurface.addListener(this);
    myDesignSurface.addPanZoomListener(this);
    myDesignSurface.addAncestorListener(myAncestorListener);

    final ScreenView currentScreenView = myDesignSurface.getCurrentScreenView();
    if (currentScreenView != null) {
      currentScreenView.getModel().addListener(this);
    }

    final Configuration configuration = myDesignSurface.getConfiguration();
    if (configuration != null) {
      updateDeviceConfiguration(configuration);
    }
  }

  /**
   * Update the number of screen displayed in X and Y axis
   *
   * @param surface
   */
  private void updateScreenNumber(@Nullable NlDesignSurface surface) {
    if (surface != null) {
      myXScreenNumber = !surface.isStackVertically() && surface.getScreenMode() == BOTH ? 2 : 1;
      myYScreenNumber = surface.isStackVertically() && surface.getScreenMode() == BOTH ? 2 : 1;
    }
  }

  /**
   * Update the screen size depending on the orientation.
   * Should be called whenever a change in the orientation occurred
   *
   * @param configuration The current configuration used by the model
   */
  private void updateDeviceConfiguration(Configuration configuration) {
    final Device device = configuration.getDevice();
    final State deviceState = configuration.getDeviceState();
    if (device != null && deviceState != null) {
      myDeviceSize = device.getScreenSize(deviceState.getOrientation());
    }
  }

  /**
   * Handle all mouse interaction onto the Minimap
   */

  private class MouseInteractionListener implements MouseListener, MouseMotionListener, MouseWheelListener {
    private final Point myMouseOrigin = new Point(0, 0);
    private final Point mySurfaceOrigin = new Point(0, 0);
    private double myNewXOffset;
    private double myNewYOffset;
    private Dimension myScreenViewSize = new Dimension();
    private ScreenView myCurrentScreenView;

    private boolean myCanDrag;

    @Override
    public void mouseDragged(MouseEvent e) {
      if (myCanDrag) {
        myNewXOffset = mySurfaceOrigin.x + e.getX() - myMouseOrigin.x;
        myNewYOffset = mySurfaceOrigin.y + e.getY() - myMouseOrigin.y;

        // Since the scroll is changed, the panningChanged method will be called by the NlDesignSurface so no
        // need to manually redraw the NlDesignSurface miniature
        assert myDesignSurface != null;
        myDesignSurface.setScrollPosition(
          (int)Math.round(myNewXOffset / myScreenViewScale),
          (int)Math.round(myNewYOffset / myScreenViewScale)
        );
      }
    }

    /**
     * Check is the clicked happened in the miniature {@link NlDesignSurface} representation
     *
     * @param e The {@link MouseEvent}
     * @return True if the event happened in the miniature {@link NlDesignSurface} representation
     */
    public boolean isInDesignSurfaceRectangle(MouseEvent e) {
      assert myDesignSurface != null;
      return e.getX() > myDesignSurfaceOffset.x + myCenterOffset.x
             && e.getX() < myDesignSurfaceOffset.x + myCenterOffset.x + myDesignSurface.getWidth() * myScreenViewScale
             && e.getY() > myDesignSurfaceOffset.y + myCenterOffset.y
             && e.getY() < myDesignSurfaceOffset.y + myCenterOffset.y + myDesignSurface.getHeight() * myScreenViewScale;
    }

    @Override
    public void mousePressed(MouseEvent e) {
      if (myDesignSurface != null && isInDesignSurfaceRectangle(e)) {
        // Init the value for the drag event
        myCurrentScreenView = myDesignSurface.getCurrentScreenView();
        if (myCurrentScreenView == null) {
          return;
        }
        myScreenViewSize = myCurrentScreenView.getSize(myScreenViewSize);
        myMouseOrigin.setLocation(e.getX(), e.getY());
        mySurfaceOrigin.setLocation(myDesignSurfaceOffset.x, myDesignSurfaceOffset.y);
        myCanDrag = true;
      }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      myCanDrag = false;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      if (isInDesignSurfaceRectangle(e) && myIsZoomed) {
        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
      }
      else {
        setCursor(Cursor.getDefaultCursor());
      }
    }

    @Override
    public void mouseExited(MouseEvent e) {
      setCursor(Cursor.getDefaultCursor());
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
      if (myDesignSurface == null) {
        return;
      }

      final int wheelRotation = e.getWheelRotation();
      if (wheelRotation < 0) {
        for (int i = 0; i > wheelRotation && myDesignSurface.getScale() <= 2.; i--) {
          myDesignSurface.zoomIn();
        }
      }
      else if (wheelRotation > 0) {
        for (int i = 0; i < wheelRotation && myDesignSurface.getScale() > 0.1; i++) {
          myDesignSurface.zoomOut();
        }
      }
    }
  }

  /**
   * Set the scale ratio to display the miniature of the {@link ScreenView} and {@link NlDesignSurface} inside this panel.
   * The scale ratio is computed such as both the {@link ScreenView} and the {@link NlDesignSurface} are always totally visible
   * whatever the value of the zoom is.
   *
   * @param currentScreenView The active {@link ScreenView}
   * @param designSurfaceSize The real size of the {@link NlDesignSurface}
   * @param contentSize       The total size of all the {@link ScreenView} in the {@link NlDesignSurface}
   */
  private void computeScale(@Nullable ScreenView currentScreenView,
                            @NotNull Dimension designSurfaceSize,
                            @NotNull Dimension contentSize) {

    if (currentScreenView == null || myMiniMap == null) {
      return;
    }

    myIsZoomed = designSurfaceSize.getWidth() < currentScreenView.getX() + contentSize.getWidth()
                 || designSurfaceSize.getHeight() < currentScreenView.getY() + contentSize.getHeight();

    double surfaceScale = Math.min(PREFERRED_SIZE.height / designSurfaceSize.getHeight(),
                                   PREFERRED_SIZE.width / designSurfaceSize.width);
    myScaledScreenSpace = (int)Math.round(SCREEN_SPACE * surfaceScale);

    myScreenViewScale = Math.min(PREFERRED_SIZE.height / contentSize.getHeight(),
                                 PREFERRED_SIZE.width / contentSize.getWidth());

    myDeviceScale = Math.min(PREFERRED_SIZE.height / myDeviceSize.getHeight() / (double)myYScreenNumber,
                             PREFERRED_SIZE.width / myDeviceSize.getWidth() / (double)myXScreenNumber);
    computeOffsets(currentScreenView);
  }

  /**
   * Set the Offsets of the Screens to draw and the offset to center all the content
   *
   * @param currentScreenView
   */
  private void computeOffsets(@Nullable ScreenView currentScreenView) {
    if (myDesignSurface != null && currentScreenView != null) {
      myCurrentScreenViewSize = currentScreenView.getSize(myCurrentScreenViewSize);

      // If there is two ScreenViews displayed,
      // we compute the offset of the second ScreenView
      if (myDesignSurface.getScreenMode() == BOTH) {
        if (myDesignSurface.isStackVertically()) {
          mySecondScreenOffset.setLocation(0, myDeviceSize.getHeight() * myDeviceScale + myScaledScreenSpace);
        }
        else {
          mySecondScreenOffset.setLocation(myDeviceSize.getWidth() * myDeviceScale + myScaledScreenSpace, 0);
        }
      }
    }
    myCenterOffset.x = (int)Math.round((PREFERRED_SIZE.getWidth() - myXScreenNumber * myDeviceSize.getWidth() * myDeviceScale) / 2);
    myCenterOffset.y = (int)Math.round((PREFERRED_SIZE.getHeight() - myYScreenNumber * myDeviceSize.getHeight() * myDeviceScale) / 2);
  }

  private class MiniMap extends JPanel {

    @Override
    public void paintChildren(Graphics g) {

      // Clear the graphics
      Graphics2D gc = (Graphics2D)g;
      gc.setBackground(UIUtil.getWindowColor());
      gc.clearRect(0, 0, getWidth(), getHeight());

      if (myDesignSurface != null) {

        final ScreenView currentScreenView = myDesignSurface.getCurrentScreenView();
        if (currentScreenView != null) {

          myDesignSurfaceSize = myDesignSurface.getSize(myDesignSurfaceSize);

          final Dimension contentSize = myDesignSurface.getContentSize(null);
          final ScreenView blueprintView = myDesignSurface.getBlueprintView();
          computeScale(currentScreenView, myDesignSurfaceSize, contentSize);
          gc.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          gc.setColor(BACKGROUND_COLOR);
          drawContainers(gc, currentScreenView, blueprintView);

          if (myIsZoomed) {
            drawDesignSurface(gc, currentScreenView);
          }
          gc.setColor(COMPONENT_STROKE_COLOR);
          if (myComponent != null) {
            drawAllComponents(gc, myComponent.getRoot(), blueprintView);
          }
          super.paintChildren(g);
        }
      }
    }

    /**
     * Recursively draw all components  by finding the root of component then find all its children and grandchildren (BFS)
     *
     * @param gc            the {@link Graphics2D} to draw on
     * @param component     a component in the {@link ScreenView}
     * @param blueprintView the blueprint {@link ScreenView} if it is available
     */
    private void drawAllComponents(Graphics2D gc,
                                   NlComponent component,
                                   @Nullable ScreenView blueprintView) {

      // Save the current color to highlight the selected component then reset the color
      Color color = gc.getColor();
      if (myComponent != null && component.getId() != null
          && component.getId().equals(myComponent.getId())) {
        gc.setColor(myColorSet.getSelectedFrames());
      }
      drawComponent(gc, component, blueprintView);
      gc.setColor(color);

      // Recursively draw the others components
      int childCount = component.getChildCount();
      for (int i = 0; i < childCount; i++) {
        drawAllComponents(gc, component.getChild(i), blueprintView);
      }
    }

    /**
     * Draw on component on all the miniatures {@link ScreenView} available
     *
     * @param gc            the {@link Graphics2D} to draw on
     * @param component     a component in the {@link ScreenView}
     * @param blueprintView the blueprint {@link ScreenView} if it is available
     */
    private void drawComponent(Graphics2D gc,
                               NlComponent component,
                               @Nullable ScreenView blueprintView) {

      final double componentRatio = myDeviceScale;
      gc.drawRect(
        (int)Math.round(myCenterOffset.x + component.x * componentRatio),
        (int)Math.round(myCenterOffset.y + component.y * componentRatio),
        (int)Math.round(component.w * componentRatio),
        (int)Math.round(component.h * componentRatio));

      assert myDesignSurface != null;
      if (myDesignSurface.getScreenMode() == BOTH && blueprintView != null) {
        gc.drawRect(
          (int)Math.round(myCenterOffset.x + mySecondScreenOffset.x + component.x * componentRatio),
          (int)Math.round(myCenterOffset.y + mySecondScreenOffset.y + component.y * componentRatio),
          (int)Math.round(component.w * componentRatio),
          (int)Math.round(component.h * componentRatio)
        );
      }
    }

    /**
     * @param gc                the {@link Graphics2D} to draw on
     * @param currentScreenView
     * @param blueprintView     the blueprint {@link ScreenView} if it is availableView
     */
    private void drawContainers(Graphics2D gc, @NotNull ScreenView currentScreenView, @Nullable ScreenView blueprintView) {

      // Draw the first screen view
      assert myDesignSurface != null;
      if (myDesignSurface.getScreenMode() == NlDesignSurface.ScreenMode.BLUEPRINT_ONLY) {
        gc.setColor(BLUEPRINT_SCREEN_VIEW_COLOR);
      }
      else {
        gc.setColor(NORMAL_SCREEN_VIEW_COLOR);
      }
      gc.fillRect(
        myCenterOffset.x,
        myCenterOffset.y,
        (int)Math.round(myDeviceSize.getWidth() * myDeviceScale),
        (int)Math.round(myDeviceSize.getHeight() * myDeviceScale)
      );

      if (!myDesignSurface.getScreenMode().equals(NlDesignSurface.ScreenMode.BLUEPRINT_ONLY)) {
        RenderResult renderResult = currentScreenView.getModel().getRenderResult();
        if (renderResult != null) {
          renderResult.getRenderedImage().drawImageTo(gc,
                                                      myCenterOffset.x, myCenterOffset.y,
                                                      (int)Math.round(myDeviceSize.getWidth() * myDeviceScale),
                                                      (int)Math.round(myDeviceSize.getHeight() * myDeviceScale));
        }
      }

      // Draw the second screenView
      if (myDesignSurface.getScreenMode() == BOTH
          && blueprintView != null) {

        gc.setColor(BLUEPRINT_SCREEN_VIEW_COLOR);
        gc.fillRect(
          myCenterOffset.x + mySecondScreenOffset.x,
          myCenterOffset.y + mySecondScreenOffset.y,
          (int)Math.round(myDeviceSize.getWidth() * myDeviceScale),
          (int)Math.round(myDeviceSize.getHeight() * myDeviceScale)
        );
      }
    }

    private void drawDesignSurface(Graphics2D gc, ScreenView currentScreenView) {
      // Rectangle of the drawing surface
      gc.setColor(DRAWING_SURFACE_RECTANGLE_COLOR);

      int x = (int)Math.round(myCenterOffset.x + myDesignSurfaceOffset.x - (currentScreenView.getX() / 2) * myScreenViewScale);
      int y = (int)Math.round(myCenterOffset.y + myDesignSurfaceOffset.y - (currentScreenView.getX() / 2) * myScreenViewScale);
      int width = (int)Math.round((myDesignSurfaceSize.getWidth() - currentScreenView.getX() / 2.) * myScreenViewScale);
      int height = (int)Math.round((myDesignSurfaceSize.getHeight() - currentScreenView.getY() / 2.) * myScreenViewScale);

      final Rectangle intersection = new Rectangle(x, y, width, height).intersection(getVisibleRect());
      x = intersection.x;
      y = intersection.y;
      width = intersection.width - 1;
      height = intersection.height - 1;

      gc.drawRect(x, y, width, height);

      // Darken the non visible parts
      gc.setColor(OVERLAY_COLOR);

      // Left
      gc.fillRect(0, 0, x, getHeight());

      // Top
      gc.fillRect(x, 0, width, y);

      // Right
      gc.fillRect(x + width,
                  0,
                  PREFERRED_SIZE.width,
                  getHeight());

      // Bottom
      gc.fillRect(x,
                  y + height,
                  width,
                  (int)Math.round(Math.max(
                    (myDeviceSize.getHeight() * myDeviceScale) * myYScreenNumber - y - height,
                    (myDeviceSize.getWidth() * myDeviceScale) * myXScreenNumber - y - height)));
    }
  }

  public static JBPopup createPopup(NlDesignSurface surface) {

    WidgetNavigatorPanel navigatorPanel = new WidgetNavigatorPanel(surface);
    final Dimension minSize = new Dimension(navigatorPanel.getSize());
    JBPopup builder = JBPopupFactory.getInstance().createComponentPopupBuilder(navigatorPanel, navigatorPanel)
      .setTitle(TITLE)
      .setMinSize(minSize)
      .setResizable(false)
      .setMovable(true)
      .setRequestFocus(true)
      .setLocateWithinScreenBounds(true)
      .setCancelButton(CANCEL_BUTTON)
      .setShowBorder(true)
      .setShowShadow(true)
      .setCancelOnClickOutside(false)
      .setCancelOnWindowDeactivation(false)
      .setCancelOnOtherWindowOpen(true)
      .addListener(navigatorPanel)
      .createPopup();

    final int x = surface.getWidth() - PREFERRED_SIZE.width - surface.getScrollPane().getVerticalScrollBar().getWidth();
    final int y = NlConstants.RULER_SIZE_PX;
    RelativePoint position = new RelativePoint(surface, new Point(x, y));
    builder.show(position);
    return builder;
  }
}
