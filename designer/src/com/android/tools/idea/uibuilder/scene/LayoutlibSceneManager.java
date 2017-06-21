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
package com.android.tools.idea.uibuilder.scene;

import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationListener;
import com.android.tools.idea.rendering.*;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.res.ResourceNotificationManager;
import com.android.tools.idea.uibuilder.analytics.NlUsageTrackerManager;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.scene.decorator.NlSceneDecoratorFactory;
import com.android.tools.idea.uibuilder.scene.decorator.SceneDecoratorFactory;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.util.PropertiesMap;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.wireless.android.sdk.stats.LayoutEditorEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.GuardedBy;
import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.android.tools.idea.configurations.ConfigurationListener.CFG_DEVICE;
import static com.intellij.util.ui.update.Update.HIGH_PRIORITY;
import static com.intellij.util.ui.update.Update.LOW_PRIORITY;

/**
 * {@link SceneManager} that creates a Scene from an NlModel representing a layout using layoutlib.
 */
public class LayoutlibSceneManager extends SceneManager {

  private static final SceneDecoratorFactory DECORATOR_FACTORY = new NlSceneDecoratorFactory();

  private int myDpi = 0;
  private final SelectionChangeListener mySelectionChangeListener = new SelectionChangeListener();
  private static final Object PROGRESS_LOCK = new Object();
  private AndroidPreviewProgressIndicator myCurrentIndicator;
  private final Object myRenderingQueueLock = new Object();
  private MergingUpdateQueue myRenderingQueue;
  private static final int RENDER_DELAY_MS = 10;
  private RenderTask myRenderTask;
  private static final Object RENDERING_LOCK = new Object();
  private ResourceNotificationManager.ResourceVersion myRenderedVersion;
  private final ReentrantReadWriteLock myRenderResultLock = new ReentrantReadWriteLock();
  @GuardedBy("myRenderResultLock")
  private RenderResult myRenderResult;
  // Variables to track previous values of the configuration bar for tracking purposes
  private String myPreviousDeviceName;
  private Locale myPreviousLocale;
  private String myPreviousVersion;
  private String myPreviousTheme;
  @AndroidCoordinate private static final int VISUAL_EMPTY_COMPONENT_SIZE = 1;
  private long myElapsedFrameTimeMs = -1;
  private final LinkedList<Runnable> myRenderCallbacks = new LinkedList<>();

  public LayoutlibSceneManager(@NotNull NlModel model, @NotNull DesignSurface designSurface) {
    super(model, designSurface);
    updateTrackingConfiguration();
  }

  @Override
  @NotNull
  public SceneDecoratorFactory getSceneDecoratorFactory() {
    return DECORATOR_FACTORY;
  }

  /**
   * Creates a {@link Scene} from our {@link NlModel}. This must only be called once per builder.
   *
   * @return
   */
  @NotNull
  @Override
  public Scene build() {
    Scene scene = super.build();
    getDesignSurface().getSelectionModel().addListener(mySelectionChangeListener);
    NlModel model = getModel();
    ConfigurationListener listener = (flags) -> {
      if ((flags & CFG_DEVICE) != 0) {
        int newDpi = model.getConfiguration().getDensity().getDpiValue();
        if (myDpi != newDpi) {
          // Update from the model to update the dpi
          update();
        }
      }
      return true;
    };
    model.getConfiguration().addListener(listener);
    Disposer.register(model, () -> model.getConfiguration().removeListener(listener));

    List<NlComponent> components = model.getComponents();
    if (components.size() != 0) {
      NlComponent rootComponent = components.get(0).getRoot();
      boolean previous = getScene().isAnimated();
      scene.setAnimated(false);
      SceneComponent root = createHierarchy(rootComponent);
      updateFromComponent(root, new HashSet<>());
      scene.setRoot(root);
      addTargets(root);
      scene.setAnimated(previous);
    }
    model.addListener(new ModelChangeListener());
    // let's make sure the selection is correct
    scene.selectionChanged(getDesignSurface().getSelectionModel(), getDesignSurface().getSelectionModel().getSelection());

    return scene;
  }

  @Override
  public void dispose() {
    super.dispose();
    // dispose is called by the project close using the read lock. Invoke the render task dispose later without the lock.
    ApplicationManager.getApplication().invokeLater(() -> {
      synchronized (RENDERING_LOCK) {
        if (myRenderTask != null) {
          myRenderTask.dispose();
          myRenderTask = null;
        }
      }
      myRenderResultLock.writeLock().lock();
      try {
        myRenderResult = null;
      }
      finally {
        myRenderResultLock.writeLock().unlock();
      }
    });
  }

  @Override
  protected void updateFromComponent(SceneComponent sceneComponent) {
    super.updateFromComponent(sceneComponent);
    NlComponent component = sceneComponent.getNlComponent();
    if (getScene().isAnimated()) {
      long time = System.currentTimeMillis();
      sceneComponent.setPositionTarget(Coordinates.pxToDp(component.getModel(), NlComponentHelperKt.getX(component)),
                                       Coordinates.pxToDp(component.getModel(), NlComponentHelperKt.getY(component)),
                                       time, true);
      sceneComponent.setSizeTarget(Coordinates.pxToDp(component.getModel(), NlComponentHelperKt.getW(component)),
                                   Coordinates.pxToDp(component.getModel(), NlComponentHelperKt.getH(component)),
                                   time, true);
    }
    else {
      sceneComponent.setPosition(Coordinates.pxToDp(component.getModel(), NlComponentHelperKt.getX(component)),
                                 Coordinates.pxToDp(component.getModel(), NlComponentHelperKt.getY(component)), true);
      sceneComponent.setSize(Coordinates.pxToDp(component.getModel(), NlComponentHelperKt.getW(component)),
                             Coordinates.pxToDp(component.getModel(), NlComponentHelperKt.getH(component)), true);
    }
  }

  @Override
  public void update() {
    super.update();
    SelectionModel selectionModel = getDesignSurface().getSelectionModel();
    if (getScene().getRoot() != null && selectionModel.isEmpty()) {
      addTargets(getScene().getRoot());
    }
  }

  /**
   * Add targets to the given component (by asking the associated
   * {@linkplain ViewGroupHandler} to do it)
   */
  public void addTargets(@NotNull SceneComponent component) {
    SceneComponent parent = component.getParent();
    if (parent != null) {
      component = parent;
    }
    else {
      component = getScene().getRoot();
    }
    ViewHandler handler = NlComponentHelperKt.getViewHandler(component.getNlComponent());
    if (handler instanceof ViewGroupHandler) {
      ViewGroupHandler viewGroupHandler = (ViewGroupHandler)handler;
      component.setTargetProvider(viewGroupHandler, true);
      for (SceneComponent child : component.getChildren()) {
        child.setTargetProvider(viewGroupHandler, false);
      }
    }
  }

  private class ModelChangeListener implements ModelListener {
    @Override
    public void modelDerivedDataChanged(@NotNull NlModel model) {
      DesignSurface surface = getDesignSurface();
      // TODO: this is the right behavior, but seems to unveil repaint issues. Turning it off for now.
      if (false && surface instanceof NlDesignSurface && ((NlDesignSurface)surface).getScreenMode() == NlDesignSurface.ScreenMode.BLUEPRINT_ONLY) {
        layout(true);
      }
      else {
        render();
      }
    }

    @Override
    public void modelChanged(@NotNull NlModel model) {
      requestModelUpdate();
      ApplicationManager.getApplication().invokeLater(() -> mySelectionChangeListener
        .selectionChanged(getDesignSurface().getSelectionModel(), getDesignSurface().getSelectionModel().getSelection()));
    }

    @Override
    public void modelRendered(@NotNull NlModel model) {
      // updateFrom needs to be called in the dispatch thread
      UIUtil.invokeLaterIfNeeded(LayoutlibSceneManager.this::update);
    }

    @Override
    public void modelChangedOnLayout(@NotNull NlModel model, boolean animate) {
      UIUtil.invokeLaterIfNeeded(() -> {
        boolean previous = getScene().isAnimated();
        getScene().setAnimated(animate);
        update();
        getScene().setAnimated(previous);
      });
    }

    @Override
    public void modelActivated(@NotNull NlModel model) {
      ResourceNotificationManager manager = ResourceNotificationManager.getInstance(getModel().getProject());
      ResourceNotificationManager.ResourceVersion version =
        manager.getCurrentVersion(getModel().getFacet(), getModel().getFile(), getModel().getConfiguration());
      if (!version.equals(myRenderedVersion)) {
        requestModelUpdate();
        model.updateTheme();
      }
    }

    @Override
    public void modelDeactivated(@NotNull NlModel model) {
      if (myRenderingQueue != null) {
        myRenderingQueue.cancelAllUpdates();
      }
    }

    @Override
    public void modelLiveUpdate(@NotNull NlModel model, boolean animate) {
      layout(animate);
    }
  }

  private class SelectionChangeListener implements SelectionListener {
    @Override
    public void selectionChanged(@NotNull SelectionModel model, @NotNull List<NlComponent> selection) {
      SceneComponent root = getScene().getRoot();
      if (root != null) {
        clearChildTargets(root);
        // After a new selection, we need to figure out the context
        if (!selection.isEmpty()) {
          NlComponent primary = selection.get(0);
          SceneComponent component = getScene().getSceneComponent(primary);
          if (component != null) { // TODO only add "static" target here (the ones that are not part of any an interaction
            addTargets(component); // or dependent on a specific state
          }
          else {
            addTargets(root);
          }
        }
        else {
          addTargets(root);
        }
      }
      getScene().needsRebuildList();
    }

    void clearChildTargets(SceneComponent component) {
      component.setTargetProvider(null, true);
      for (SceneComponent child : component.getChildren()) {
        child.setTargetProvider(null, false);
        clearChildTargets(child);
      }
    }
  }

  public void requestRender(@Nullable Runnable callback) {
    if (callback != null) {
      synchronized (myRenderCallbacks) {
        myRenderCallbacks.add(callback);
      }
    }
    // This update is low priority so the model updates take precedence
    getRenderingQueue().queue(new Update("model.render", LOW_PRIORITY) {
      @Override
      public void run() {
        render();
      }

      @Override
      public boolean canEat(Update update) {
        return this.equals(update);
      }
    });
  }

  @Override
  public void requestRender() {
    requestRender(null);
  }

  public void requestLayoutAndRender(boolean animate) {
    requestRender(() -> {
      getModel().notifyListenersModelLayoutComplete(animate);
    });
  }

  /**
   * Asynchronously inflates the model and updates the view hierarchy
   */
  protected void requestModelUpdate() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    synchronized (PROGRESS_LOCK) {
      if (myCurrentIndicator == null) {
        myCurrentIndicator = new AndroidPreviewProgressIndicator();
        myCurrentIndicator.start();
      }
    }

    getRenderingQueue().queue(new Update("model.update", HIGH_PRIORITY) {
      @Override
      public void run() {
        Project project = getModel().getModule().getProject();
        if (project.isOpen()) {
          DumbService.getInstance(project).waitForSmartMode();
          if (!getModel().getFacet().isDisposed()) {
            try {
              updateModel();
            }
            catch (Throwable e) {
              Logger.getInstance(NlModel.class).error(e);
            }
          }
        }

        synchronized (PROGRESS_LOCK) {
          if (myCurrentIndicator != null) {
            myCurrentIndicator.stop();
            myCurrentIndicator = null;
          }
        }
      }

      @Override
      public boolean canEat(Update update) {
        return equals(update);
      }
    });
  }

  @NotNull
  public MergingUpdateQueue getRenderingQueue() {
    synchronized (myRenderingQueueLock) {
      if (myRenderingQueue == null) {
        myRenderingQueue = new MergingUpdateQueue("android.layout.rendering", RENDER_DELAY_MS, true, null, this, null,
                                                  Alarm.ThreadToUse.POOLED_THREAD);
        myRenderingQueue.setRestartTimerOnAdd(true);
      }
      return myRenderingQueue;
    }
  }

  /**
   * Whether we should render just the viewport
   */
  private static boolean ourRenderViewPort;

  public static void setRenderViewPort(boolean state) {
    ourRenderViewPort = state;
  }

  public static boolean isRenderViewPort() {
    return ourRenderViewPort;
  }

  /**
   * Request a layout pass
   *
   * @param animate if true, the resulting layout should be animated
   */
  @Override
  public void layout(boolean animate) {
    if (myRenderTask != null) {
      synchronized (RENDERING_LOCK) {
        RenderResult result = null;
        try {
          result = myRenderTask.layout().get();
        }
        catch (InterruptedException | ExecutionException e) {
          Logger.getInstance(NlModel.class).warn("Unable to run layout()", e);
        }
        if (result != null) {
          updateHierarchy(result);
          getModel().notifyListenersModelLayoutComplete(animate);
        }
      }
    }
  }

  @Nullable
  public RenderResult getRenderResult() {
    myRenderResultLock.readLock().lock();
    try {
      return myRenderResult;
    }
    finally {
      myRenderResultLock.readLock().unlock();
    }
  }

  @Override
  @NotNull
  public Map<Object, PropertiesMap> getDefaultProperties() {
    myRenderResultLock.readLock().lock();
    try {
      if (myRenderResult == null) {
        return Collections.emptyMap();
      }
      return myRenderResult.getDefaultProperties();
    }
    finally {
      myRenderResultLock.readLock().unlock();
    }
  }

  private void updateHierarchy(@Nullable RenderResult result) {
    if (result == null || !result.getRenderResult().isSuccess()) {
      updateHierarchy(Collections.emptyList(), getModel());
    }
    else {
      updateHierarchy(getRootViews(result), getModel());
    }
    getModel().checkStructure();
  }

  @NotNull
  private List<ViewInfo> getRootViews(@NotNull RenderResult result) {
    return getModel().getType() == NlLayoutType.MENU ? result.getSystemRootViews() : result.getRootViews();
  }

  @VisibleForTesting
  public static void updateHierarchy(@NotNull XmlTag rootTag, @NotNull List<ViewInfo> rootViews, @NotNull NlModel model) {
    model.syncWithPsi(rootTag, rootViews.stream().map(ViewInfoTagSnapshotNode::new).collect(Collectors.toList()));
    updateBounds(rootViews, model);
  }


  @VisibleForTesting
  public static void updateHierarchy(@NotNull List<ViewInfo> rootViews, @NotNull NlModel model) {
    updateHierarchy(AndroidPsiUtils.getRootTagSafely(model.getFile()), rootViews, model);
  }

  /**
   * Synchronously inflates the model and updates the view hierarchy
   *
   * @param force forces the model to be re-inflated even if a previous version was already inflated
   * @returns whether the model was inflated in this call or not
   */
  private boolean inflate(boolean force) {
    Configuration configuration = getModel().getConfiguration();

    Project project = getModel().getProject();
    if (project.isDisposed()) {
      return false;
    }
    ResourceNotificationManager resourceNotificationManager = ResourceNotificationManager.getInstance(project);

    // Some types of files must be saved to disk first, because layoutlib doesn't
    // delegate XML parsers for non-layout files (meaning layoutlib will read the
    // disk contents, so we have to push any edits to disk before rendering)
    LayoutPullParserFactory.saveFileIfNecessary(getModel().getFile());

    RenderResult result = null;
    synchronized (RENDERING_LOCK) {
      if (myRenderTask != null && !force) {
        // No need to inflate
        return false;
      }

      // Record the current version we're rendering from; we'll use that in #activate to make sure we're picking up any
      // external changes
      myRenderedVersion = resourceNotificationManager.getCurrentVersion(getModel().getFacet(), getModel().getFile(), configuration);

      RenderService renderService = RenderService.getInstance(getModel().getFacet());
      RenderLogger logger = renderService.createLogger();
      if (myRenderTask != null && !myRenderTask.isDisposed()) {
        myRenderTask.dispose();
      }
      myRenderTask = renderService.createTask(getModel().getFile(), configuration, logger, getDesignSurface());
      setupRenderTask(myRenderTask);
      if (myRenderTask != null) {
        if (!isRenderViewPort()) {
          myRenderTask.useDesignMode(getModel().getFile());
        }
        result = myRenderTask.inflate();
        if (result == null || !result.getRenderResult().isSuccess()) {
          myRenderTask.dispose();
          myRenderTask = null;

          if (result == null) {
            result = RenderResult.createBlank(getModel().getFile());
          }
        }
      }

      updateHierarchy(result);
      myRenderResultLock.writeLock().lock();
      try {
        myRenderResult = result;
      }
      finally {
        myRenderResultLock.writeLock().unlock();
      }

      return myRenderTask != null;
    }
  }

  @VisibleForTesting
  protected void setupRenderTask(@Nullable RenderTask task) {
  }

  /**
   * Synchronously update the model. This will inflate the layout and notify the listeners using
   * {@link ModelListener#modelDerivedDataChanged(NlModel)}.
   */
  protected void updateModel() {
    inflate(true);
    getModel().notifyListenersModelUpdateComplete();
  }

  /**
   * Renders the current model synchronously. Once the render is complete, the listeners {@link ModelListener#modelRendered(NlModel)}
   * method will be called.
   * <p/>
   * If the layout hasn't been inflated before, this call will inflate the layout before rendering.
   * <p/>
   * <b>Do not call this method from the dispatch thread!</b>
   */
  protected void render() {
    try {
      renderImpl();
    }
    catch (Throwable e) {
      if (!getModel().getFacet().isDisposed()) {
        throw e;
      }
    } finally {
      ImmutableList<Runnable> callbacks;
      synchronized (myRenderCallbacks) {
        callbacks = ImmutableList.copyOf(myRenderCallbacks);
      }
      callbacks.forEach(Runnable::run);
    }
  }

  private void renderImpl() {
    Configuration configuration = getModel().getConfiguration();
    DesignSurface surface = getDesignSurface();
    if (getModel().getConfigurationModificationCount() != configuration.getModificationCount()) {
      // usage tracking (we only pay attention to individual changes where only one item is affected since those are likely to be triggered
      // by the user
      if (!StringUtil.equals(configuration.getTheme(), myPreviousTheme)) {
        myPreviousTheme = configuration.getTheme();
        NlUsageTrackerManager.getInstance(surface).logAction(LayoutEditorEvent.LayoutEditorEventType.THEME_CHANGE);
      }
      else if (configuration.getTarget() != null && !StringUtil.equals(configuration.getTarget().getVersionName(), myPreviousVersion)) {
        myPreviousVersion = configuration.getTarget().getVersionName();
        NlUsageTrackerManager.getInstance(surface).logAction(LayoutEditorEvent.LayoutEditorEventType.API_LEVEL_CHANGE);
      }
      else if (!configuration.getLocale().equals(myPreviousLocale)) {
        myPreviousLocale = configuration.getLocale();
        NlUsageTrackerManager.getInstance(surface).logAction(LayoutEditorEvent.LayoutEditorEventType.LANGUAGE_CHANGE);
      }
      else if (configuration.getDevice() != null && !StringUtil.equals(configuration.getDevice().getDisplayName(), myPreviousDeviceName)) {
        myPreviousDeviceName = configuration.getDevice().getDisplayName();
        NlUsageTrackerManager.getInstance(surface).logAction(LayoutEditorEvent.LayoutEditorEventType.DEVICE_CHANGE);
      }
    }

    NlModel.ChangeType changeType = getModel().getLastChangeType();
    getModel().resetLastChange();
    long renderStartTimeMs = System.currentTimeMillis();
    boolean inflated = inflate(false);

    synchronized (RENDERING_LOCK) {
      if (myRenderTask != null) {
        if (myElapsedFrameTimeMs != -1) {
          myRenderTask.setElapsedFrameTimeNanos(TimeUnit.MILLISECONDS.toNanos(myElapsedFrameTimeMs));
        }
        RenderResult result = Futures.getUnchecked(myRenderTask.render());
        // When the layout was inflated in this same call, we do not have to update the hierarchy again
        if (result != null && !inflated) {
          updateHierarchy(result);
        }
        myRenderResultLock.writeLock().lock();
        try {
          myRenderResult = result;
          // Downgrade the write lock to read lock
          myRenderResultLock.readLock().lock();
        }
        finally {
          myRenderResultLock.writeLock().unlock();
        }
        try {
          NlUsageTrackerManager.getInstance(surface).logRenderResult(changeType,
                                                                     myRenderResult,
                                                                     System.currentTimeMillis() - renderStartTimeMs);
        }
        finally {
          myRenderResultLock.readLock().unlock();
        }
      }
    }

    getModel().notifyListenersRenderComplete();
  }

  public void setElapsedFrameTimeMs(long ms) {
    myElapsedFrameTimeMs = ms;
  }

  /**
   * Updates the saved values that are used to log user changes to the configuration toolbar.
   */
  private void updateTrackingConfiguration() {
    Configuration configuration = getModel().getConfiguration();
    myPreviousDeviceName = configuration.getDevice() != null ? configuration.getDevice().getDisplayName() : null;
    myPreviousVersion = configuration.getTarget() != null ? configuration.getTarget().getVersionName() : null;
    myPreviousLocale = configuration.getLocale();
    myPreviousTheme = configuration.getTheme();
  }

  private class AndroidPreviewProgressIndicator extends ProgressIndicatorBase {
    private final Object myLock = new Object();

    @Override
    public void start() {
      super.start();
      UIUtil.invokeLaterIfNeeded(() -> {
        final Timer timer = UIUtil.createNamedTimer("Android rendering progress timer", 0, event -> {
          synchronized (myLock) {
            if (isRunning()) {
              getDesignSurface().registerIndicator(this);
            }
          }
        });
        timer.setRepeats(false);
        timer.start();
      });
    }

    @Override
    public void stop() {
      synchronized (myLock) {
        super.stop();
        ApplicationManager.getApplication().invokeLater(() -> getDesignSurface().unregisterIndicator(this));
      }
    }
  }

  /**
   * A TagSnapshot tree that mirrors the ViewInfo tree.
   */
  private static class ViewInfoTagSnapshotNode implements NlModel.TagSnapshotTreeNode {

    private final ViewInfo myViewInfo;

    public ViewInfoTagSnapshotNode(ViewInfo info) {
      myViewInfo = info;
    }

    @Nullable
    @Override
    public TagSnapshot getTagSnapshot() {
      Object result = myViewInfo.getCookie();
      return result instanceof TagSnapshot ? (TagSnapshot)result : null;
    }

    @NotNull
    @Override
    public List<NlModel.TagSnapshotTreeNode> getChildren() {
      return myViewInfo.getChildren().stream().map(ViewInfoTagSnapshotNode::new).collect(Collectors.toList());
    }
  }

  private static void clearDerivedData(@NotNull NlComponent component) {
    NlComponentHelperKt.setBounds(component, 0, 0, -1, -1); // -1: not initialized
    NlComponentHelperKt.setViewInfo(component, null);
  }

  // TODO: we shouldn't be going back in and modifying NlComponents here
  private static void updateBounds(@NotNull List<ViewInfo> rootViews, @NotNull NlModel model) {
    model.flattenComponents().forEach(LayoutlibSceneManager::clearDerivedData);
    Map<TagSnapshot, NlComponent> snapshotToComponent =
      model.flattenComponents().collect(Collectors.toMap(NlComponent::getSnapshot, Function.identity(), (n1, n2) -> n1));
    Map<XmlTag, NlComponent> tagToComponent =
      model.flattenComponents().collect(Collectors.toMap(NlComponent::getTag, Function.identity()));

    // Update the bounds. This is based on the ViewInfo instances.
    for (ViewInfo view : rootViews) {
      updateBounds(view, 0, 0, snapshotToComponent, tagToComponent);
    }

    // Finally, fix up bounds: ensure that all components not found in the view
    // info hierarchy inherit position from parent
    fixBounds(model.getComponents().get(0));
  }

  private static void fixBounds(@NotNull NlComponent root) {
    boolean computeBounds = false;
    if (NlComponentHelperKt.getW(root) == -1 && NlComponentHelperKt.getH(root) == -1) { // -1: not initialized
      computeBounds = true;

      // Look at parent instead
      NlComponent parent = root.getParent();
      if (parent != null && NlComponentHelperKt.getW(parent) >= 0) {
        NlComponentHelperKt.setBounds(root, NlComponentHelperKt.getX(parent), NlComponentHelperKt.getY(parent), 0, 0);
      }
    }

    List<NlComponent> children = root.children;
    if (children != null && !children.isEmpty()) {
      for (NlComponent child : children) {
        fixBounds(child);
      }

      if (computeBounds) {
        Rectangle rectangle = new Rectangle(NlComponentHelperKt.getX(root), NlComponentHelperKt.getY(root), NlComponentHelperKt.getW(root),
                                            NlComponentHelperKt.getH(root));
        // Grow bounds to include child bounds
        for (NlComponent child : children) {
          rectangle = rectangle.union(new Rectangle(NlComponentHelperKt.getX(child), NlComponentHelperKt.getY(child),
                                                    NlComponentHelperKt.getW(child), NlComponentHelperKt.getH(child)));
        }

        NlComponentHelperKt.setBounds(root, rectangle.x, rectangle.y, rectangle.width, rectangle.height);
      }
    }
  }

  private static void updateBounds(@NotNull ViewInfo view,
                                   int parentX,
                                   int parentY,
                                   Map<TagSnapshot, NlComponent> snapshotToComponent,
                                   Map<XmlTag, NlComponent> tagToComponent) {
    ViewInfo bounds = RenderService.getSafeBounds(view);
    Object cookie = view.getCookie();
    NlComponent component;
    if (cookie != null) {
      if (cookie instanceof TagSnapshot) {
        TagSnapshot snapshot = (TagSnapshot)cookie;
        component = snapshotToComponent.get(snapshot);
        if (component == null) {
          component = tagToComponent.get(snapshot.tag);
        }
        if (component != null && NlComponentHelperKt.getViewInfo(component) == null) {
          NlComponentHelperKt.setViewInfo(component, view);
          int left = parentX + bounds.getLeft();
          int top = parentY + bounds.getTop();
          int width = bounds.getRight() - bounds.getLeft();
          int height = bounds.getBottom() - bounds.getTop();

          NlComponentHelperKt.setBounds(component, left, top, Math.max(width, VISUAL_EMPTY_COMPONENT_SIZE),
                                        Math.max(height, VISUAL_EMPTY_COMPONENT_SIZE));
        }
      }
    }
    parentX += bounds.getLeft();
    parentY += bounds.getTop();

    for (ViewInfo child : view.getChildren()) {
      updateBounds(child, parentX, parentY, snapshotToComponent, tagToComponent);
    }
  }
}
