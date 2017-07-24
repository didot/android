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

import com.android.annotations.VisibleForTesting;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.ui.designer.EditorDesignSurface;
import com.android.tools.idea.uibuilder.analytics.NlUsageTrackerManager;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.editor.ActionManager;
import com.android.tools.idea.uibuilder.editor.NlPreviewForm;
import com.android.tools.idea.uibuilder.error.IssueModel;
import com.android.tools.idea.uibuilder.error.IssuePanel;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.inspector.InspectorProviders;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.scene.Scene;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneManager;
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
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.Magnificator;
import com.intellij.util.Alarm;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import org.intellij.lang.annotations.JdkConstants;
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

import static com.android.annotations.VisibleForTesting.*;
import static com.android.tools.idea.uibuilder.graphics.NlConstants.DESIGN_SURFACE_BG;

/**
 * A generic design surface for use in a graphical editor.
 */
public abstract class DesignSurface extends EditorDesignSurface implements Disposable, DataProvider {
  private static final Integer LAYER_PROGRESS = JLayeredPane.POPUP_LAYER + 100;

  private final Project myProject;

  protected double myScale = 1;
  @NotNull protected final JScrollPane myScrollPane;
  private final MyLayeredPane myLayeredPane;
  protected boolean myDeviceFrames = false;
  private ImmutableList<Layer> myLayers = ImmutableList.of();
  private final InteractionManager myInteractionManager;
  private final GlassPane myGlassPane;
  protected final List<DesignSurfaceListener> myListeners = new ArrayList<>();
  private List<PanZoomListener> myZoomListeners;
  private final ActionManager myActionManager;
  @NotNull private WeakReference<FileEditor> myFileEditorDelegate = new WeakReference<>(null);
  protected NlModel myModel;
  protected Scene myScene;
  private SceneManager mySceneManager;
  private final SelectionModel mySelectionModel;
  private ViewEditorImpl myViewEditor;

  private final IssueModel myIssueModel = new IssueModel();
  private final IssuePanel myIssuePanel;
  private final JBSplitter myErrorPanelSplitter;
  private final Object myErrorQueueLock = new Object();
  private MergingUpdateQueue myErrorQueue;
  private boolean myIsActive = false;

  public DesignSurface(@NotNull Project project, @NotNull Disposable parentDisposable) {
    super(new BorderLayout());
    Disposer.register(parentDisposable, this);
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

    myIssuePanel = new IssuePanel(this, myIssueModel);
    Disposer.register(this, myIssuePanel);
    myIssuePanel.setMinimizeListener((isMinimized) -> {
      NlUsageTrackerManager.getInstance(this).logAction(
        isMinimized ? LayoutEditorEvent.LayoutEditorEventType.MINIMIZE_ERROR_PANEL
                    : LayoutEditorEvent.LayoutEditorEventType.RESTORE_ERROR_PANEL);
      updateErrorPanelSplitterUi(isMinimized);
    });

    // The error panel can only take up to 50% of the surface and it will take a 25% by default
    myErrorPanelSplitter = new OnePixelSplitter(true, 0.75f, 0.5f, 1f);
    myErrorPanelSplitter.setHonorComponentsMinimumSize(true);
    myErrorPanelSplitter.setFirstComponent(myScrollPane);
    myErrorPanelSplitter.setSecondComponent(myIssuePanel);
    add(myErrorPanelSplitter);

    // TODO: Do this as part of the layout/validate operation instead
    addComponentListener(new ComponentListener() {
      @Override
      public void componentResized(ComponentEvent componentEvent) {
        if (isShowing() && getWidth() > 0 && getHeight() > 0 && getFitScale(false) > myScale) {
          // Only rescale if the user did not set the zoom
          // to something else than fit
          zoomToFit();
        }
        else {
          layoutContent();
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

  // TODO: add self-type parameter DesignSurface?
  @NotNull
  protected abstract ActionManager<? extends DesignSurface> createActionManager();

  @NotNull
  protected abstract SceneManager createSceneManager(@NotNull NlModel model);

  protected abstract void layoutContent();

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
    zoomToFit();
  }

  @Override
  public void dispose() {
    // This takes care of disposing any existing layers
    setLayers(ImmutableList.of());
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
    myLayeredPane.setSize(dimension.width, dimension.height);
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
  public void zoom(@NotNull ZoomType type, @SwingCoordinate int x, @SwingCoordinate int y) {
    SceneView view = getCurrentSceneView();
    if (type == ZoomType.IN && (x < 0 || y < 0)
        && view != null && !getSelectionModel().isEmpty()) {
      Scene scene = getScene();
      if (scene != null) {
        SceneComponent component = scene.getSceneComponent(getSelectionModel().getPrimary());
        if (component != null) {
          x = Coordinates.getSwingXDip(view, component.getCenterX());
          y = Coordinates.getSwingYDip(view, component.getCenterY());
        }
      }
    }
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
  protected double getFitScale(boolean fitInto) {
    // Fit to zoom
    int availableWidth = myScrollPane.getWidth() - myScrollPane.getVerticalScrollBar().getWidth();
    int availableHeight = myScrollPane.getHeight() - myScrollPane.getHorizontalScrollBar().getHeight();
    Dimension padding = getDefaultOffset();
    availableWidth -= padding.width;
    availableHeight -= padding.height;

    Dimension preferredSize = getPreferredContentSize(availableWidth, availableHeight);
    double scaleX = preferredSize.width == 0 ? 1 : (double)availableWidth / preferredSize.width;
    double scaleY = preferredSize.height == 0 ? 1 : (double)availableHeight / preferredSize.height;
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
  @NotNull
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
    myScale = Math.max(scale, getMinScale());

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

  /**
   * The minimum scale we'll allow.
   */
  protected double getMinScale() {
    return 0;
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

  /**
   * @param x the x coordinate of the double click converted to pixels in the Android coordinate system
   * @param y the y coordinate of the double click converted to pixels in the Android coordinate system
   */
  public void notifyComponentActivate(@NotNull NlComponent component, @AndroidCoordinate int x, @AndroidCoordinate int y) {
    notifyComponentActivate(component);
  }

  public void notifyComponentActivate(@NotNull NlComponent component) {
    activatePreferredEditor(component);
  }

  protected void activatePreferredEditor(@NotNull NlComponent component) {
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
    if (!myIsActive && getCurrentSceneView() != null) {
      getCurrentSceneView().getModel().activate(this);
    }
    myIsActive = true;

  }

  public void deactivate() {
    if (myIsActive && getCurrentSceneView() != null) {
      getCurrentSceneView().getModel().deactivate(this);
    }
    myIsActive = false;

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

  @Nullable
  public Scene getScene() {
    return myScene;
  }

  @Nullable
  public SceneManager getSceneManager() {
    return mySceneManager;
  }

  @NotNull
  public abstract InspectorProviders getInspectorProviders(@NotNull NlPropertiesManager propertiesManager,
                                                           @NotNull Disposable parentDisposable);


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
      graphics.setColor(getBackgroundColor());
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

  @NotNull
  protected JBColor getBackgroundColor() {
    return DESIGN_SURFACE_BG;
  }

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
    if (myProject.isDisposed() || Disposer.isDisposed(this)) {
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

      if (myProgressIndicators.isEmpty()) {
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

  @Override
  public void forceUserRequestedRefresh() {
    SceneView sceneView = getCurrentSceneView();
    SceneManager sceneManager = sceneView != null ? sceneView.getSceneManager() : null;

    if (sceneManager instanceof LayoutlibSceneManager) {
      ((LayoutlibSceneManager)sceneManager).requestUserInitatedRender();
    }
  }

  /**
   * Invalidates the current model and request a render of the layout. This will re-inflate the layout and render it.
   */
  public void requestRender() {
    SceneView sceneView = getCurrentSceneView();
    if (sceneView != null) {
      sceneView.getSceneManager().requestRender();
    }
  }

  @NotNull
  public JScrollPane getScrollPane() {
    return myScrollPane;
  }

  /**
   * Sets the tooltip for the design surface
   */
  public void setDesignToolTip(@Nullable String text) {
    myLayeredPane.setToolTipText(text);
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

  @Nullable
  @Override
  public Configuration getConfiguration() {
    return getCurrentSceneView() != null ? getCurrentSceneView().getConfiguration() : null;
  }

  @NotNull
  public IssueModel getIssueModel() {
    return myIssueModel;
  }

  @NotNull
  public IssuePanel getIssuePanel() {
    return myIssuePanel;
  }

  public void setShowIssuePanel(boolean show) {
    UIUtil.invokeLaterIfNeeded(() -> {
      myIssuePanel.setMinimized(!show);
      updateErrorPanelSplitterUi(myIssuePanel.isMinimized());
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

  private void updateErrorPanelSplitterUi(boolean isMinimized) {
    boolean showDivider = !isMinimized;
    myErrorPanelSplitter.setShowDividerIcon(showDivider);
    myErrorPanelSplitter.setShowDividerControls(showDivider);
    myErrorPanelSplitter.setResizeEnabled(showDivider);

    if (isMinimized) {
      myErrorPanelSplitter.setProportion(1f);
    }
    else {
      int height = myIssuePanel.getSuggestedHeight();
      float proportion = 1 - (height / (float)getHeight());
      myErrorPanelSplitter.setProportion(Math.max(0.5f, proportion));
    }
  }

  @NotNull
  public NlComponent createComponent(@NotNull XmlTag tag) {
    NlModel model = getModel();
    assert model != null;
    return createComponent(tag, model);
  }

  @VisibleForTesting
  public static NlComponent createComponent(@NotNull XmlTag tag, @NotNull NlModel model) {
    return new NlComponent(model, tag);
  }

  /**
   * Attaches the given {@link Layer}s to the current design surface
   */
  protected void setLayers(@NotNull ImmutableList<Layer> layers) {
    myLayers.forEach(Disposer::dispose);
    myLayers = ImmutableList.copyOf(layers);
  }

  /**
   * Returns the list of {@link Layer}s attached to this {@link DesignSurface}
   */
  @NotNull
  protected ImmutableList<Layer> getLayers() {
    return myLayers;
  }

  @Nullable
  public final Interaction createInteractionOnClick(@SwingCoordinate int mouseX, @SwingCoordinate int mouseY) {
    SceneView sceneView = getSceneView(mouseX, mouseY);
    if (sceneView == null) {
      return null;
    }
    return doCreateInteractionOnClick(mouseX, mouseY, sceneView);
  }

  @VisibleForTesting(visibility = Visibility.PROTECTED)
  @Nullable
  public abstract Interaction doCreateInteractionOnClick(@SwingCoordinate int mouseX, @SwingCoordinate int mouseY, @NotNull SceneView view);

  @Nullable
  public abstract Interaction createInteractionOnDrag(@NotNull SceneComponent draggedSceneComponent, @Nullable SceneComponent primary);
}
