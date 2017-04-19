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

import com.android.tools.idea.rendering.errors.ui.RenderErrorModel;
import com.android.tools.idea.rendering.errors.ui.RenderErrorPanel;
import com.android.tools.idea.ui.designer.EditorDesignSurface;
import com.android.tools.idea.uibuilder.analytics.NlUsageTracker;
import com.android.tools.idea.uibuilder.analytics.NlUsageTrackerManager;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.editor.ActionManager;
import com.android.tools.idea.uibuilder.editor.NlPreviewForm;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.lint.LintAnnotationsModel;
import com.android.tools.idea.uibuilder.lint.LintNotificationPanel;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.scene.Scene;
import com.android.tools.idea.uibuilder.scene.SceneManager;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.wireless.android.sdk.stats.LayoutEditorEvent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.Magnificator;
import com.intellij.util.Alarm;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.android.uipreview.AndroidEditorSettings;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.ScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.uibuilder.graphics.NlConstants.DESIGN_SURFACE_BG;

/**
 * A generic design surface for use in a graphical editor.
 */
public abstract class DesignSurface extends EditorDesignSurface implements Disposable, DataProvider {
  private static final Integer LAYER_PROGRESS = JLayeredPane.POPUP_LAYER + 100;
  private static final String PROPERTY_ERROR_PANEL_SPLITTER = DesignSurface.class.getCanonicalName() + ".error.panel.split";

  private final Project myProject;
  private final JBSplitter myErrorPanelSplitter;

  private boolean myZoomFitted = true;
  /**
   * {@link LintNotificationPanel} currently being displayed
   */
  private WeakReference<JBPopup> myLintTooltipPopup = new WeakReference<>(null);

  protected double myScale = 1;
  @NotNull protected final JScrollPane myScrollPane;
  private final MyLayeredPane myLayeredPane;
  protected boolean myDeviceFrames = false;
  protected final List<Layer> myLayers = Lists.newArrayList();
  private final InteractionManager myInteractionManager;
  private final GlassPane myGlassPane;
  protected final RenderErrorPanel myErrorPanel;
  protected final List<DesignSurfaceListener> myListeners = new ArrayList<>();
  private List<PanZoomListener> myZoomListeners;
  private final ActionManager myActionManager;
  @SuppressWarnings("CanBeFinal") private float mySavedErrorPanelProportion;
  @NotNull private WeakReference<FileEditor> myFileEditorDelegate = new WeakReference<>(null);
  protected NlModel myModel;
  protected Scene myScene;
  private SceneManager mySceneManager;
  private final SelectionModel mySelectionModel;
  private ViewEditorImpl myViewEditor;

  public DesignSurface(@NotNull Project project) {
    super(new BorderLayout());
    myProject = project;

    setOpaque(true);
    setFocusable(true);
    setRequestFocusEnabled(true);
    setBackground(UIUtil.TRANSPARENT_COLOR);

    mySelectionModel = new SelectionModel();
    myInteractionManager = new InteractionManager(this);

    myLayeredPane = new MyLayeredPane();
    myLayeredPane.setBounds(0, 0, 100, 100);
    myGlassPane = new GlassPane();
    myLayeredPane.add(myGlassPane, JLayeredPane.DRAG_LAYER);

    myProgressPanel = new MyProgressPanel();
    myProgressPanel.setName("Layout Editor Progress Panel");
    myLayeredPane.add(myProgressPanel, LAYER_PROGRESS);

    myScrollPane = new MyScrollPane();
    myScrollPane.setViewportView(myLayeredPane);
    myScrollPane.setBorder(null);
    myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
    myScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
    myScrollPane.getHorizontalScrollBar().addAdjustmentListener(this::notifyPanningChanged);
    myScrollPane.getVerticalScrollBar().addAdjustmentListener(this::notifyPanningChanged);

    myErrorPanel = new RenderErrorPanel();
    Disposer.register(this, myErrorPanel);
    myErrorPanel.setName("Layout Editor Error Panel");

    // The error panel can only take up to 50% of the surface and it will take a 25% by default
    myErrorPanelSplitter = new JBSplitter(true, 0.75f, 0.5f, 1f);
    myErrorPanelSplitter.setAndLoadSplitterProportionKey(PROPERTY_ERROR_PANEL_SPLITTER);
    myErrorPanelSplitter.setHonorComponentsMinimumSize(true);
    myErrorPanelSplitter.setFirstComponent(myScrollPane);
    myErrorPanelSplitter.setSecondComponent(myErrorPanel);

    mySavedErrorPanelProportion = myErrorPanelSplitter.getProportion();
    myErrorPanel.setMinimizeListener((isMinimized) -> {
      NlUsageTracker tracker = NlUsageTrackerManager.getInstance(this);
      if (isMinimized) {
        tracker.logAction(LayoutEditorEvent.LayoutEditorEventType.MINIMIZE_ERROR_PANEL);
        mySavedErrorPanelProportion = myErrorPanelSplitter.getProportion();
        myErrorPanelSplitter.setProportion(1f);
      }
      else {
        tracker.logAction(LayoutEditorEvent.LayoutEditorEventType.RESTORE_ERROR_PANEL);
        myErrorPanelSplitter.setProportion(mySavedErrorPanelProportion);
      }
      updateErrorPanelSplitterUi(isMinimized);
    });

    updateErrorPanelSplitterUi(myErrorPanel.isMinimized());
    add(myErrorPanelSplitter);

    // TODO: Do this as part of the layout/validate operation instead
    myScrollPane.addComponentListener(new ComponentListener() {
      @Override
      public void componentResized(ComponentEvent componentEvent) {
        if (isShowing() && getWidth() > 0 && getHeight() > 0 && myZoomFitted) {
          // Only rescale if the user did not set the zoom
          // to something else than fit
          zoomToFit();
        }
        else {
          layoutContent();
          if (myZoomFitted) {
            zoomToFit();
          }
        }
      }

      @Override
      public void componentMoved(ComponentEvent componentEvent) {
      }

      @Override
      public void componentShown(ComponentEvent componentEvent) {
      }

      @Override
      public void componentHidden(ComponentEvent componentEvent) {
      }
    });

    myInteractionManager.registerListeners();
    myActionManager = createActionManager();
    myActionManager.registerActions(myLayeredPane);
  }

  protected abstract ActionManager createActionManager();

  @NotNull
  protected abstract SceneManager createSceneManager(@NotNull NlModel model);

  protected abstract void layoutContent();

  private void updateErrorPanelSplitterUi(boolean isMinimized) {
    boolean showDivider = myErrorPanel.isVisible() && !isMinimized;
    myErrorPanelSplitter.setShowDividerIcon(showDivider);
    myErrorPanelSplitter.setShowDividerControls(showDivider);
    myErrorPanelSplitter.setResizeEnabled(showDivider);

    if (isMinimized) {
      myErrorPanelSplitter.setProportion(1f);
    }
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public NlLayoutType getLayoutType() {
    if (getCurrentSceneView() == null) {
      return NlLayoutType.UNKNOWN;
    }
    return NlLayoutType.typeOf(getCurrentSceneView().getModel().getFile());
  }

  @NotNull
  public ActionManager getActionManager() {
    return myActionManager;
  }

  public SelectionModel getSelectionModel() {
    return mySelectionModel;
  }

  protected final void createSceneViews() {
    doCreateSceneViews();
    notifySceneViewChanged();
  }

  protected abstract void doCreateSceneViews();

  @Nullable
  public NlModel getModel() {
    return myModel;
  }

  public void setModel(@Nullable NlModel model) {
    myModel = model;
    SceneView sceneView = getCurrentSceneView();
    if (model == null && sceneView == null) {
      return;
    }

    List<NlComponent> selectionBefore = Collections.emptyList();
    List<NlComponent> selectionAfter = Collections.emptyList();

    if (sceneView != null) {
      sceneView.getModel().removeListener(myModelListener);

      SelectionModel selectionModel = sceneView.getSelectionModel();
      selectionBefore = selectionModel.getSelection();
      selectionModel.removeListener(mySelectionListener);
    }

    if (model != null) {
      model.addListener(myModelListener);
      mySceneManager = createSceneManager(model);
      myScene = mySceneManager.build();
    }
    else {
      myScene = null;
      mySceneManager = null;
    }

    createSceneViews();

    if (model != null) {
      SelectionModel selectionModel = model.getSelectionModel();
      selectionModel.addListener(mySelectionListener);
      selectionAfter = selectionModel.getSelection();
      if (myInteractionManager.isListening() && !getLayoutType().isSupportedByDesigner()) {
        myInteractionManager.unregisterListeners();
      }
      else if (!myInteractionManager.isListening() && getLayoutType().isSupportedByDesigner()) {
        myInteractionManager.registerListeners();
      }
    }
    repaint();

    if (!selectionBefore.equals(selectionAfter)) {
      notifySelectionListeners(selectionAfter);
    }
    for (DesignSurfaceListener listener : ImmutableList.copyOf(myListeners)) {
      listener.modelChanged(this, model);
    }
    notifySceneViewChanged();
  }

  @Override
  public void dispose() {
  }

  /**
   * @return The new {@link Dimension} of the LayeredPane (SceneView)
   */
  @Nullable
  public abstract Dimension getScrolledAreaSize();

  @Nullable
  public Dimension updateScrolledAreaSize() {
    final Dimension dimension = getScrolledAreaSize();
    if (dimension == null) {
      return null;
    }
    myLayeredPane.setBounds(0, 0, dimension.width, dimension.height);
    myLayeredPane.setPreferredSize(dimension);
    myScrollPane.revalidate();
    SceneView view = getCurrentSceneView();
    if (view != null) {
      myProgressPanel.setBounds(getContentOriginX(), getContentOriginY(), view.getSize().width, view.getSize().height);
    }
    return dimension;
  }

  /**
   * The x (swing) coordinate of the origin of this DesignSurface's content.
   */
  @SwingCoordinate
  protected abstract int getContentOriginX();

  /**
   * The y (swing) coordinate of the origin of this DesignSurface's content.
   */
  @SwingCoordinate
  protected abstract int getContentOriginY();

  public JComponent getPreferredFocusedComponent() {
    return myGlassPane;
  }

  @Override
  protected void paintChildren(Graphics graphics) {
    super.paintChildren(graphics);

    if (isFocusOwner()) {
      graphics.setColor(UIUtil.getFocusedBoundsColor());
      graphics.drawRect(getX(), getY(), getWidth() - 1, getHeight() - 1);
    }
  }

  @Nullable
  public abstract SceneView getCurrentSceneView();

  /**
   * Gives us a chance to change layers behaviour upon drag and drop interaction starting
   */
  public void startDragDropInteraction() {
    for (Layer layer : myLayers) {
      if (layer instanceof ConstraintsLayer) {
        ConstraintsLayer constraintsLayer = (ConstraintsLayer)layer;
        if (!constraintsLayer.isShowOnHover()) {
          constraintsLayer.setShowOnHover(true);
          repaint();
        }
      }
      if (layer instanceof SceneLayer) {
        SceneLayer sceneLayer = (SceneLayer)layer;
        if (!sceneLayer.isShowOnHover()) {
          sceneLayer.setShowOnHover(true);
          repaint();
        }
      }
    }
  }

  /**
   * Gives us a chance to change layers behaviour upon drag and drop interaction ending
   */
  public void stopDragDropInteraction() {
    for (Layer layer : myLayers) {
      if (layer instanceof ConstraintsLayer) {
        ConstraintsLayer constraintsLayer = (ConstraintsLayer)layer;
        if (constraintsLayer.isShowOnHover()) {
          constraintsLayer.setShowOnHover(false);
          repaint();
        }
      }
      if (layer instanceof SceneLayer) {
        SceneLayer sceneLayer = (SceneLayer)layer;
        if (!sceneLayer.isShowOnHover()) {
          sceneLayer.setShowOnHover(false);
          repaint();
        }
      }
    }
  }

  /**
   * @param dimension the Dimension object to reuse to avoid reallocation
   * @return The total size of all the ScreenViews in the DesignSurface
   */
  @NotNull
  public abstract Dimension getContentSize(@Nullable Dimension dimension);

  public void hover(@SwingCoordinate int x, @SwingCoordinate int y) {
    for (Layer layer : myLayers) {
      layer.hover(x, y);
    }

    if (myErrorPanel.isVisible() && hasProblems()) {
      // don't show any warnings on hover if there is already some errors that are being displayed
      // TODO: we should really move this logic into the error panel itself
      return;
    }

    // Currently, we use the hover action only to check whether we need to show a warning.
    if (AndroidEditorSettings.getInstance().getGlobalState().isShowLint()) {
      SceneView currentSceneView = getCurrentSceneView();
      LintAnnotationsModel lintModel = currentSceneView != null ? currentSceneView.getModel().getLintAnnotationsModel() : null;
      if (lintModel != null) {
        for (Layer layer : myLayers) {
          String tooltip = layer.getTooltip(x, y);
          if (tooltip != null) {
            JBPopup lintPopup = myLintTooltipPopup.get();
            if (lintPopup == null || !lintPopup.isVisible()) {
              NlUsageTrackerManager.getInstance(this).logAction(LayoutEditorEvent.LayoutEditorEventType.LINT_TOOLTIP);
              LintNotificationPanel lintPanel = new LintNotificationPanel(getCurrentSceneView(), lintModel);
              lintPanel.selectIssueAtPoint(Coordinates.getAndroidX(getCurrentSceneView(), x),
                                           Coordinates.getAndroidY(getCurrentSceneView(), y));

              Point point = new Point(x, y);
              SwingUtilities.convertPointToScreen(point, this);
              myLintTooltipPopup = new WeakReference<>(lintPanel.showInScreenPosition(myProject, this, point));
            }
            break;
          }
        }
      }
    }
  }

  protected boolean hasProblems() {
    return false;
  }

  public void resetHover() {
    if (hasProblems()) {
      return;
    }
    // if we were showing some warnings, then close it.
    // TODO: similar to hover() method above, this logic of warning/error should be inside the error panel itself
    myErrorPanel.setVisible(false);
  }

  /**
   * Execute a zoom on the content. See {@link ZoomType} for the different type of zoom available.
   *
   * @see #zoom(ZoomType, int, int)
   */
  public void zoom(@NotNull ZoomType type) {
    zoom(type, -1, -1);
  }

  /**
   * <p>
   * Execute a zoom on the content. See {@link ZoomType} for the different types of zoom available.
   * </p><p>
   * If type is {@link ZoomType#IN}, zoom toward the given
   * coordinates (relative to {@link #getLayeredPane()})
   *
   * If x or y are negative, zoom toward the center of the viewport.
   * </p>
   *
   * @param type Type of zoom to execute
   * @param x    Coordinate where the zoom will be centered
   * @param y    Coordinate where the zoom will be centered
   */
  public void zoom(@NotNull ZoomType type, int x, int y) {
    myZoomFitted = false;
    switch (type) {
      case IN: {
        double currentScale = myScale;
        if (SystemInfo.isMac && UIUtil.isRetina()) {
          currentScale *= 2;
        }
        int current = (int)(currentScale * 100);
        double scale = ZoomType.zoomIn(current) / 100.0;
        if (SystemInfo.isMac && UIUtil.isRetina()) {
          scale /= 2;
        }
        setScale(scale, x, y);
        repaint();
        break;
      }
      case OUT: {
        double currentScale = myScale;
        if (SystemInfo.isMac && UIUtil.isRetina()) {
          currentScale *= 2;
        }
        int current = (int)(currentScale * 100);
        double scale = ZoomType.zoomOut(current) / 100.0;
        if (SystemInfo.isMac && UIUtil.isRetina()) {
          scale /= 2;
        }
        setScale(scale);
        repaint();
        break;
      }
      case ACTUAL:
        if (SystemInfo.isMac && UIUtil.isRetina()) {
          setScale(0.5);
        }
        else {
          setScale(1);
        }
        repaint();
        break;
      case FIT:
      case FIT_INTO:
        myZoomFitted = true;
        if (getCurrentSceneView() == null) {
          return;
        }

        setScale(getFitScale(type == ZoomType.FIT_INTO));
        repaint();
        break;
      default:
      case SCREEN:
        throw new UnsupportedOperationException("Not yet implemented: " + type);
    }
  }

  /**
   * @param fitInto {@link ZoomType#FIT_INTO}
   * @return The scale to make the content fit the design surface
   */
  private double getFitScale(boolean fitInto) {
    // Fit to zoom
    int availableWidth = myScrollPane.getWidth() - myScrollPane.getVerticalScrollBar().getWidth();
    int availableHeight = myScrollPane.getHeight() - myScrollPane.getHorizontalScrollBar().getHeight();
    Dimension padding = getDefaultOffset();
    availableWidth -= padding.width;
    availableHeight -= padding.height;

    Dimension preferredSize = getPreferredContentSize(availableWidth, availableHeight);
    double scaleX = (double)availableWidth / preferredSize.getWidth();
    double scaleY = (double)availableHeight / preferredSize.getHeight();
    double scale = Math.min(scaleX, scaleY);
    if (fitInto) {
      double min = (SystemInfo.isMac && UIUtil.isRetina()) ? 0.5 : 1.0;
      scale = Math.min(min, scale);
    }
    return scale;
  }

  @SwingCoordinate
  protected abstract Dimension getDefaultOffset();

  @SwingCoordinate
  protected abstract Dimension getPreferredContentSize(int availableWidth, int availableHeight);

  public void zoomActual() {
    zoom(ZoomType.ACTUAL);
  }

  public void zoomIn() {
    zoom(ZoomType.IN);
  }

  public void zoomOut() {
    zoom(ZoomType.OUT);
  }

  public void zoomToFit() {
    zoom(ZoomType.FIT);
  }

  public boolean isZoomFitted() {
    return myZoomFitted;
  }

  public double getScale() {
    return myScale;
  }

  public void setScrollPosition(int x, int y) {
    setScrollPosition(new Point(x, y));
  }

  public void setScrollPosition(Point p) {
    final JScrollBar horizontalScrollBar = myScrollPane.getHorizontalScrollBar();
    final JScrollBar verticalScrollBar = myScrollPane.getVerticalScrollBar();
    p.setLocation(
      Math.max(horizontalScrollBar.getMinimum(), p.x),
      Math.max(verticalScrollBar.getMinimum(), p.y));

    p.setLocation(
      Math.min(horizontalScrollBar.getMaximum() - horizontalScrollBar.getVisibleAmount(), p.x),
      Math.min(verticalScrollBar.getMaximum() - verticalScrollBar.getVisibleAmount(), p.y));
    myScrollPane.getViewport().setViewPosition(p);
  }

  public Point getScrollPosition() {
    return myScrollPane.getViewport().getViewPosition();
  }

  /**
   * Set the scale factor used to multiply the content size.
   *
   * @param scale The scale factor. Can be any value but it will be capped between -1 and 10
   *              (value below 0 means zoom to fit)
   */
  private void setScale(double scale) {
    setScale(scale, -1, -1);
  }

  /**
   * <p>
   * Set the scale factor used to multiply the content size and try to
   * position the viewport such that its center is the closest possible
   * to the provided x and y coordinate in the Viewport's view coordinate system
   * ({@link JViewport#getView()}).
   * </p><p>
   * If x OR y are negative, the scale will be centered toward the center the viewport.
   * </p>
   *
   * @param scale The scale factor. Can be any value but it will be capped between -1 and 10
   *              (value below 0 means zoom to fit)
   * @param x     The X coordinate to center the scale to (in the Viewport's view coordinate system)
   * @param y     The Y coordinate to center the scale to (in the Viewport's view coordinate system)
   */
  private void setScale(double scale, @SwingCoordinate int x, @SwingCoordinate int y) {

    if (scale < 0) {
      // We wait for component resized to be fired
      // that will take care of calling zoomToFit
      scale = -1;
    }
    else if (Math.abs(scale - 1) < 0.0001) {
      scale = 1;
    }
    else if (scale > 10) {
      scale = 10;
    }
    double fitScale = getFitScale(false);
    myScale = Math.max(scale, fitScale > 1 ? 1 : fitScale);

    Dimension oldSize = myScrollPane.getViewport().getViewSize();
    Point viewPortTargetCoordinates;
    if (x < 0 || y < 0) {
      // Get the coordinates of the point of the view showing at the center of the viewport
      Point viewPortPosition = myScrollPane.getViewport().getViewPosition();
      x = viewPortPosition.x + myScrollPane.getWidth() / 2;
      y = viewPortPosition.y + myScrollPane.getHeight() / 2;

      // Set the center of the viewPort as the target for centering the scale
      viewPortTargetCoordinates = new Point(myScrollPane.getWidth() / 2, myScrollPane.getHeight() / 2);
    }
    else {
      viewPortTargetCoordinates = SwingUtilities.convertPoint(myLayeredPane, x, y, myScrollPane.getViewport());
    }

    // Normalized value (between 0.0 and 1.0) of the target position
    double nx = x / (double)oldSize.width;
    double ny = y / (double)oldSize.height;

    layoutContent();
    final Dimension newSize = updateScrolledAreaSize();
    if (newSize != null) {
      viewPortTargetCoordinates.setLocation(nx * newSize.getWidth() - viewPortTargetCoordinates.x,
                                            ny * newSize.getHeight() - viewPortTargetCoordinates.y);
      myScrollPane.getViewport().setViewPosition(viewPortTargetCoordinates);
      notifyScaleChanged();
    }
  }

  private void notifyScaleChanged() {
    if (myZoomListeners != null) {
      for (PanZoomListener myZoomListener : myZoomListeners) {
        myZoomListener.zoomChanged(this);
      }
    }
  }

  private void notifyPanningChanged(AdjustmentEvent adjustmentEvent) {
    if (myZoomListeners != null) {
      for (PanZoomListener myZoomListener : myZoomListeners) {
        myZoomListener.panningChanged(adjustmentEvent);
      }
    }
  }

  @NotNull
  public JComponent getLayeredPane() {
    return myLayeredPane;
  }

  private void notifySelectionListeners(@NotNull List<NlComponent> newSelection) {
    List<DesignSurfaceListener> listeners = Lists.newArrayList(myListeners);
    for (DesignSurfaceListener listener : listeners) {
      listener.componentSelectionChanged(this, newSelection);
    }
  }

  private void notifySceneViewChanged() {
    SceneView screenView = getCurrentSceneView();
    List<DesignSurfaceListener> listeners = Lists.newArrayList(myListeners);
    for (DesignSurfaceListener listener : listeners) {
      listener.sceneChanged(this, screenView);
    }
  }

  public void notifyComponentActivateInComponentTree(@NotNull NlComponent component) {
    ViewHandler handler = component.getViewHandler();
    ViewEditor editor = getViewEditor();

    if (handler != null && editor != null) {
      handler.onActivateInComponentTree(editor, component);
    }

    activatePreferredEditor(component);
  }

  /**
   * @param x the x coordinate of the double click converted to pixels in the Android coordinate system
   * @param y the y coordinate of the double click converted to pixels in the Android coordinate system
   */
  void notifyComponentActivateInDesignSurface(@NotNull NlComponent component, @AndroidCoordinate int x, @AndroidCoordinate int y) {
    ViewHandler handler = component.getViewHandler();
    ViewEditor editor = getViewEditor();

    if (handler != null && editor != null) {
      handler.onActivateInDesignSurface(editor, component, x, y);
    }

    activatePreferredEditor(component);
  }

  private void activatePreferredEditor(@NotNull NlComponent component) {
    for (DesignSurfaceListener listener : new ArrayList<>(myListeners)) {
      if (listener.activatePreferredEditor(this, component)) {
        break;
      }
    }
  }

  public void addListener(@NotNull DesignSurfaceListener listener) {
    myListeners.remove(listener); // ensure single registration
    myListeners.add(listener);
  }

  public void removeListener(@NotNull DesignSurfaceListener listener) {
    myListeners.remove(listener);
  }

  private final SelectionListener mySelectionListener = (model, selection) -> {
    if (getCurrentSceneView() != null) {
      notifySelectionListeners(selection);
    }
    else {
      notifySelectionListeners(Collections.emptyList());
    }
  };

  protected void modelRendered(@NotNull NlModel model) {
    if (getCurrentSceneView() != null) {
      repaint();
      layoutContent();
    }
  }

  private final ModelListener myModelListener = new ModelListener() {
    @Override
    public void modelRendered(@NotNull NlModel model) {
      DesignSurface.this.modelRendered(model);
    }

    @Override
    public void modelChangedOnLayout(@NotNull NlModel model, boolean animate) {
      repaint();
    }
  };

  public void addPanZoomListener(PanZoomListener listener) {
    if (myZoomListeners == null) {
      myZoomListeners = Lists.newArrayList();
    }
    else {
      myZoomListeners.remove(listener);
    }
    myZoomListeners.add(listener);
  }

  public void removePanZoomListener(PanZoomListener listener) {
    if (myZoomListeners != null) {
      myZoomListeners.remove(listener);
    }
  }

  /**
   * The editor has been activated
   */
  public void activate() {
    if (getCurrentSceneView() != null) {
      getCurrentSceneView().getModel().activate();
    }
  }

  public void deactivate() {
    if (getCurrentSceneView() != null) {
      getCurrentSceneView().getModel().deactivate();
    }

    myInteractionManager.cancelInteraction();
  }

  /**
   * Sets the file editor to which actions like undo/redo will be delegated. This is only needed if this DesignSurface is not a child
   * of a {@link FileEditor} like in the case of {@link NlPreviewForm}.
   * <p>
   * The surface will only keep a {@link WeakReference} to the editor.
   */
  public void setFileEditorDelegate(@Nullable FileEditor fileEditor) {
    myFileEditorDelegate = new WeakReference<>(fileEditor);
  }

  public SceneView getSceneView(@SwingCoordinate int x, @SwingCoordinate int y) {
    return getCurrentSceneView();
  }

  /**
   * Return the SceneView under the given position
   *
   * @param x
   * @param y
   * @return the SceneView, or null if we are not above one.
   */
  @Nullable
  public SceneView getHoverSceneView(@SwingCoordinate int x, @SwingCoordinate int y) {
    return getCurrentSceneView();
  }

  public void toggleDeviceFrames() {
    myDeviceFrames = !myDeviceFrames;
    layoutContent();
    repaint();
  }

  public Scene getScene() {
    return myScene;
  }

  @Nullable
  public SceneManager getSceneManager() {
    return mySceneManager;
  }

  private static class MyScrollPane extends JBScrollPane {
    private MyScrollPane() {
      super(0);
      setOpaque(true);
      setBackground(UIUtil.TRANSPARENT_COLOR);
      setupCorners();
    }

    @NotNull
    @Override
    public JScrollBar createVerticalScrollBar() {
      return new MyScrollBar(Adjustable.VERTICAL);
    }

    @NotNull
    @Override
    public JScrollBar createHorizontalScrollBar() {
      return new MyScrollBar(Adjustable.HORIZONTAL);
    }
  }

  private static class MyScrollBar extends JBScrollBar implements IdeGlassPane.TopComponent {
    private ScrollBarUI myPersistentUI;

    private MyScrollBar(@JdkConstants.AdjustableOrientation int orientation) {
      super(orientation);
      setOpaque(false);
    }

    @Override
    public boolean canBePreprocessed(MouseEvent e) {
      return JBScrollPane.canBePreprocessed(e, this);
    }

    @Override
    public void setUI(ScrollBarUI ui) {
      if (myPersistentUI == null) myPersistentUI = ui;
      super.setUI(myPersistentUI);
      setOpaque(false);
    }

    @Override
    public int getUnitIncrement(int direction) {
      return 5;
    }

    @Override
    public int getBlockIncrement(int direction) {
      return 1;
    }
  }

  private class MyLayeredPane extends JLayeredPane implements Magnificator, DataProvider {
    public MyLayeredPane() {
      setOpaque(true);
      setBackground(UIUtil.TRANSPARENT_COLOR);

      // Enable pinching to zoom
      putClientProperty(Magnificator.CLIENT_PROPERTY_KEY, this);
    }

    // ---- Implements Magnificator ----

    @Override
    public Point magnify(double scale, Point at) {
      // Handle screen zooming.
      // Note: This only seems to work (be invoked) on Mac with the Apple JDK (1.6) currently
      setScale(scale * myScale);
      DesignSurface.this.repaint();
      return new Point((int)(at.x * scale), (int)(at.y * scale));
    }

    @Override
    protected void paintComponent(@NotNull Graphics graphics) {
      super.paintComponent(graphics);

      Graphics2D g2d = (Graphics2D)graphics;
      // (x,y) coordinates of the top left corner in the view port
      int tlx = myScrollPane.getHorizontalScrollBar().getValue();
      int tly = myScrollPane.getVerticalScrollBar().getValue();

      paintBackground(g2d, tlx, tly);

      if (getCurrentSceneView() == null) {
        return;
      }

      for (Layer layer : myLayers) {
        if (!layer.isHidden()) {
          layer.paint(g2d);
        }
      }

      if (!getLayoutType().isSupportedByDesigner()) {
        return;
      }

      // Temporary overlays:
      List<Layer> layers = myInteractionManager.getLayers();
      if (layers != null) {
        for (Layer layer : layers) {
          if (!layer.isHidden()) {
            layer.paint(g2d);
          }
        }
      }
    }

    private void paintBackground(@NotNull Graphics2D graphics, int lx, int ly) {
      int width = myScrollPane.getWidth();
      int height = myScrollPane.getHeight();
      graphics.setColor(DESIGN_SURFACE_BG);
      graphics.fillRect(lx, ly, width, height);
    }

    @Nullable
    @Override
    public Object getData(@NonNls String dataId) {
      if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
        if (getCurrentSceneView() != null) {
          SelectionModel selectionModel = getCurrentSceneView().getSelectionModel();
          NlComponent primary = selectionModel.getPrimary();
          if (primary != null) {
            return primary.getTag();
          }
        }
      }
      if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
        if (getCurrentSceneView() != null) {
          SelectionModel selectionModel = getCurrentSceneView().getSelectionModel();
          List<NlComponent> selection = selectionModel.getSelection();
          List<XmlTag> list = Lists.newArrayListWithCapacity(selection.size());
          for (NlComponent component : selection) {
            list.add(component.getTag());
          }
          return list.toArray(XmlTag.EMPTY);
        }
      }

      return null;
    }
  }

  public void setShowErrorPanel(boolean show) {
    UIUtil.invokeLaterIfNeeded(() -> {
      myErrorPanel.setVisible(show);
      updateErrorPanelSplitterUi(myErrorPanel.isMinimized());
      revalidate();
      repaint();
    });
  }

  @NotNull
  protected MergingUpdateQueue getErrorQueue() {
    synchronized (myErrorQueueLock) {
      if (myErrorQueue == null) {
        myErrorQueue = new MergingUpdateQueue("android.error.computation", 200, true, null, myProject, null,
                                              Alarm.ThreadToUse.POOLED_THREAD);
      }
      return myErrorQueue;
    }
  }

  private final Object myErrorQueueLock = new Object();
  private MergingUpdateQueue myErrorQueue;

  private static class GlassPane extends JComponent {
    private static final long EVENT_FLAGS = AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK;

    public GlassPane() {
      enableEvents(EVENT_FLAGS);
    }

    @Override
    public void setEnabled(boolean enabled) {
      super.setEnabled(enabled);
      if (enabled) {
        enableEvents(EVENT_FLAGS);
      }
      else {
        disableEvents(EVENT_FLAGS);
      }
    }

    @Override
    protected void processKeyEvent(KeyEvent event) {
      if (!event.isConsumed()) {
        super.processKeyEvent(event);
      }
    }

    @Override
    protected void processMouseEvent(MouseEvent event) {
      if (event.getID() == MouseEvent.MOUSE_PRESSED) {
        requestFocusInWindow();
      }

      super.processMouseEvent(event);
    }
  }

  private final List<ProgressIndicator> myProgressIndicators = new ArrayList<>();

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private final MyProgressPanel myProgressPanel;

  public void registerIndicator(@NotNull ProgressIndicator indicator) {
    if (myProject.isDisposed()) {
      return;
    }

    synchronized (myProgressIndicators) {
      myProgressIndicators.add(indicator);
      myProgressPanel.showProgressIcon();
    }
  }

  public void unregisterIndicator(@NotNull ProgressIndicator indicator) {
    synchronized (myProgressIndicators) {
      myProgressIndicators.remove(indicator);

      if (myProgressIndicators.size() == 0) {
        myProgressPanel.hideProgressIcon();
      }
    }
  }

  protected boolean useSmallProgressIcon() {
    return true;
  }

  /**
   * Panel which displays the progress icon. The progress icon can either be a large icon in the
   * center, when there is no rendering showing, or a small icon in the upper right corner when there
   * is a rendering. This is necessary because even though the progress icon looks good on some
   * renderings, depending on the layout theme colors it is invisible in other cases.
   */
  private class MyProgressPanel extends JPanel {
    private AsyncProcessIcon mySmallProgressIcon;
    private AsyncProcessIcon myLargeProgressIcon;
    private boolean mySmall;
    private boolean myProgressVisible;

    private MyProgressPanel() {
      super(new BorderLayout());
      setOpaque(false);
      setVisible(false);
    }

    /**
     * The "small" icon mode isn't just for the icon size; it's for the layout position too; see {@link #doLayout}
     */
    private void setSmallIcon(boolean small) {
      if (small != mySmall) {
        if (myProgressVisible && getComponentCount() != 0) {
          AsyncProcessIcon oldIcon = getProgressIcon();
          oldIcon.suspend();
        }
        mySmall = true;
        removeAll();
        AsyncProcessIcon icon = getProgressIcon();
        add(icon, BorderLayout.CENTER);
        if (myProgressVisible) {
          icon.setVisible(true);
          icon.resume();
        }
      }
    }

    public void showProgressIcon() {
      if (!myProgressVisible) {
        setSmallIcon(useSmallProgressIcon());
        myProgressVisible = true;
        setVisible(true);
        AsyncProcessIcon icon = getProgressIcon();
        if (getComponentCount() == 0) { // First time: haven't added icon yet?
          add(getProgressIcon(), BorderLayout.CENTER);
        }
        else {
          icon.setVisible(true);
        }
        icon.resume();
      }
    }

    public void hideProgressIcon() {
      if (myProgressVisible) {
        myProgressVisible = false;
        setVisible(false);
        AsyncProcessIcon icon = getProgressIcon();
        icon.setVisible(false);
        icon.suspend();
      }
    }

    @Override
    public void doLayout() {
      super.doLayout();
      setBackground(JBColor.RED); // make this null instead?

      if (!myProgressVisible) {
        return;
      }

      // Place the progress icon in the center if there's no rendering, and in the
      // upper right corner if there's a rendering. The reason for this is that the icon color
      // will depend on whether we're in a light or dark IDE theme, and depending on the rendering
      // in the layout it will be invisible. For example, in Darcula the icon is white, and if the
      // layout is rendering a white screen, the progress is invisible.
      AsyncProcessIcon icon = getProgressIcon();
      Dimension size = icon.getPreferredSize();
      if (mySmall) {
        icon.setBounds(getWidth() - size.width - 1, 1, size.width, size.height);
      }
      else {
        icon.setBounds(getWidth() / 2 - size.width / 2, getHeight() / 2 - size.height / 2, size.width, size.height);
      }
    }

    @Override
    public Dimension getPreferredSize() {
      return getProgressIcon().getPreferredSize();
    }

    @NotNull
    private AsyncProcessIcon getProgressIcon() {
      return getProgressIcon(mySmall);
    }

    @NotNull
    private AsyncProcessIcon getProgressIcon(boolean small) {
      if (small) {
        if (mySmallProgressIcon == null) {
          mySmallProgressIcon = new AsyncProcessIcon("Android layout rendering");
          Disposer.register(DesignSurface.this, mySmallProgressIcon);
        }
        return mySmallProgressIcon;
      }
      else {
        if (myLargeProgressIcon == null) {
          myLargeProgressIcon = new AsyncProcessIcon.Big("Android layout rendering");
          Disposer.register(DesignSurface.this, myLargeProgressIcon);
        }
        return myLargeProgressIcon;
      }
    }
  }

  /**
   * Requests a new render of the layout.
   *
   * @param invalidateModel if true, the model will be invalidated and re-inflated. When false, this will only repaint the current model.
   */
  @Override
  public void requestRender(boolean invalidateModel) {
    SceneView sceneView = getCurrentSceneView();
    if (sceneView != null) {
      if (invalidateModel) {
        // Invalidate the current model and request a render
        sceneView.getModel().notifyModified(NlModel.ChangeType.REQUEST_RENDER);
      }
      else {
        sceneView.getSceneManager().requestRender();
      }
    }
  }

  @NotNull
  public JScrollPane getScrollPane() {
    return myScrollPane;
  }

  /**
   * Sets the tooltip for the design surface
   *
   * @param text
   */
  public void setDesignToolTip(@Nullable String text) {
    myLayeredPane.setToolTipText(text);
  }

  /**
   * Invalidates the current model and request a render of the layout. This will re-inflate the layout and render it.
   */
  @Override
  public void requestRender() {
    requestRender(true);
  }

  @Override
  public Object getData(@NonNls String dataId) {
    if (PlatformDataKeys.FILE_EDITOR.is(dataId)) {
      return myFileEditorDelegate.get();
    }
    else if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId) ||
             PlatformDataKeys.CUT_PROVIDER.is(dataId) ||
             PlatformDataKeys.COPY_PROVIDER.is(dataId) ||
             PlatformDataKeys.PASTE_PROVIDER.is(dataId)) {
      return new DesignSurfaceActionHandler(this);
    }
    return null;
  }

  @VisibleForTesting
  RenderErrorModel getErrorModel() {
    return myErrorPanel.getModel();
  }

  /**
   * Returns true we shouldn't currently try to relayout our content (e.g. if some other operations is in progress).
   */
  public abstract boolean isLayoutDisabled();

  @Nullable
  public ViewEditor getViewEditor() {
    SceneView currentSceneView = getCurrentSceneView();
    if (currentSceneView == null) {
      return null;
    }

    if (myViewEditor == null || myViewEditor.getSceneView() != currentSceneView) {
      myViewEditor = new ViewEditorImpl(currentSceneView);
    }
    return myViewEditor;
  }
}
