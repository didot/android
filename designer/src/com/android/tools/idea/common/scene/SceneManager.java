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
package com.android.tools.idea.common.scene;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.decorator.SceneDecoratorFactory;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A facility for creating and updating {@link Scene}s based on {@link NlModel}s.
 */
abstract public class SceneManager implements Disposable {
  /**
   * Provider mapping {@link NlComponent}s to {@link SceneComponent}/
   */
  public interface SceneComponentHierarchyProvider {
    /**
     * Called by the {@link SceneManager} to create the initially {@link SceneComponent} hierarchy from the given
     * {@link NlComponent}.
     */
    @NotNull
    List<SceneComponent> createHierarchy(@NotNull SceneManager manager, @NotNull NlComponent component);

    /**
     * Call by the {@link SceneManager} to trigger a sync of the {@link NlComponent} to the given {@link SceneComponent}.
     * This allows for the SceneComponent to sync the latest data from the {@link NlModel} and update the UI
     * representation. The method will be called when the {@link SceneManager} detects that there is the need to sync.
     * This could be after a render or after a model change, for example.
     */
    void syncFromNlComponent(@NotNull SceneComponent sceneComponent);
  }

  public static final boolean SUPPORTS_LOCKING = false;

  @NotNull private final NlModel myModel;
  @NotNull private final DesignSurface myDesignSurface;
  @NotNull private final Scene myScene;
  // This will be initialized when constructor calls updateSceneView().
  @Nullable private SceneView mySceneView;
  @NotNull private final HitProvider myHitProvider = new DefaultHitProvider();
  @NotNull private final SceneComponentHierarchyProvider mySceneComponentProvider;

  /**
   * Creates a new {@link SceneManager}.
   * @param model the {@NlMode} linked to this {@link SceneManager}.
   * @param surface the {@DesignSurface} that will render this {@link SceneManager}.
   * @param useLiveRendering if true, the {@link SceneManager} will re-render on every component update. When false, only when a explicit
   *                         {@link SceneManager#requestRender} happens.
   * @param sceneComponentProvider a {@link SceneComponentHierarchyProvider} that will generate the {@link SceneComponent}s from the
   *                               given {@link NlComponent}.
   */
  public SceneManager(
    @NotNull NlModel model,
    @NotNull DesignSurface surface,
    boolean useLiveRendering,
    @Nullable SceneComponentHierarchyProvider sceneComponentProvider) {
    myModel = model;
    myDesignSurface = surface;
    Disposer.register(model, this);

    mySceneComponentProvider = sceneComponentProvider == null ? new DefaultSceneManagerHierarchyProvider() : sceneComponentProvider;
    myScene = new Scene(this, myDesignSurface, useLiveRendering);
  }

  /**
   * Create the SceneView
   */
  protected void createSceneView() {
    if (mySceneView != null){
      Disposer.dispose(mySceneView);
    }
    mySceneView = doCreateSceneView();
    Disposer.register(this, mySceneView);
  }

  /**
   * Create the SceneView.
   * @return the created SceneView.
   */
  @NotNull
  protected abstract SceneView doCreateSceneView();

  /**
   * Update the SceneView of SceneManager. The SceneView may be recreated if needed.
   */
  public final void updateSceneView() {
    // Remove the current SceneViews before they are disposed
    for (SceneView sceneView : getSceneViews()) {
      myDesignSurface.removeSceneView(sceneView);
    }
    createSceneView();

    // Add the newly allocated SceneViews
    for (SceneView sceneView : getSceneViews()) {
      myDesignSurface.addSceneView(sceneView);
    }
  }

  @Deprecated // A SceneManager can have more than one SceneView. Use getSceneViews() instead
  @NotNull
  public SceneView getSceneView() {
    assert mySceneView != null : "createSceneView was not called";
    return mySceneView;
  }

  @NotNull
  public List<SceneView> getSceneViews() {
    return ImmutableList.of(getSceneView());
  }

  /**
   * In the layout editor, Scene uses {@link AndroidDpCoordinate}s whereas rendering is done in (zoomed and offset)
   * {@link AndroidCoordinate}s. The scaling factor between them is the ratio of the screen density to the standard density (160).
   */
  public abstract float getSceneScalingFactor();

  @Override
  public void dispose() {
    for (SceneView sceneView : getSceneViews()) {
      myDesignSurface.removeSceneView(sceneView);
    }
  }

  /**
   * Update the Scene with the components in the current NlModel. This method needs to be called in the dispatch thread.<br/>
   * This includes marking the display list as dirty.
   */
  public void update() {
    List<NlComponent> components = getModel().getComponents();
    Scene scene = getScene();
    if (components.isEmpty()) {
      scene.removeAllComponents();
      scene.setRoot(null);
      return;
    }
    Set<SceneComponent> usedComponents = new HashSet<>();
    Set<SceneComponent> oldComponents = new HashSet<>(scene.getSceneComponents());

    NlComponent rootComponent = getRoot();
    if (myScene.getRoot() != null && rootComponent != myScene.getRoot().getNlComponent()) {
      scene.removeAllComponents();
      scene.setRoot(null);
    }

    List<SceneComponent> hierarchy = mySceneComponentProvider.createHierarchy(this, rootComponent);
    SceneComponent root = hierarchy.isEmpty() ? null : hierarchy.get(0);
    scene.setRoot(root);
    if (root != null) {
      updateFromComponent(root, usedComponents);
    }
    oldComponents.removeAll(usedComponents);
    // The temporary component are not present in the NLModel so won't be added to the used component array
    oldComponents.removeIf(component -> component instanceof TemporarySceneComponent);
    oldComponents.forEach(scene::removeComponent);

    scene.needsRebuildList();
  }

  @NotNull
  protected NlComponent getRoot() {
    return getModel().getComponents().get(0).getRoot();
  }

  /**
   * Returns false if the value of the tools:visible attribute is false, true otherwise.
   * When a component is not tool visible, it will not be rendered by the Scene mechanism (though it might be by others, e.g. layoutlib),
   * and no interaction will be possible with it from the design surface.
   *
   * @param component component to look at
   * @return tool visibility status
   */
  public static boolean isComponentLocked(@NotNull NlComponent component) {
    if (SUPPORTS_LOCKING) {
      String attribute = component.getLiveAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LOCKED);
      if (attribute != null) {
        return attribute.equals(SdkConstants.VALUE_TRUE);
      }
    }
    return false;
  }

  /**
   * Update the SceneComponent paired to the given NlComponent and its children.
   *
   * @param component      the root SceneComponent to update
   * @param seenComponents Collector of components that were seen during NlComponent tree traversal.
   */
  protected final void updateFromComponent(@NotNull SceneComponent component, @NotNull Set<SceneComponent> seenComponents) {
    seenComponents.add(component);

    syncFromNlComponent(component);

    for (SceneComponent child : component.getChildren()) {
      updateFromComponent(child, seenComponents);
    }
  }

  /**
   * Creates a {@link TemporarySceneComponent} in our Scene.
   */
  @NotNull
  abstract public TemporarySceneComponent createTemporaryComponent(@NotNull NlComponent component);

  /**
   * Updates a single SceneComponent from its corresponding NlComponent.
   */
  protected final void syncFromNlComponent(SceneComponent sceneComponent) {
    mySceneComponentProvider.syncFromNlComponent(sceneComponent);
  }

  @NotNull
  protected DesignSurface getDesignSurface() {
    return myDesignSurface;
  }

  @NotNull
  public NlModel getModel() {
    return myModel;
  }

  @NotNull
  public Scene getScene() {
    return myScene;
  }

  @NotNull
  public abstract CompletableFuture<Void> requestRender();

  @NotNull
  public CompletableFuture<Void> requestLayoutAndRender(boolean animate) {
    return CompletableFuture.completedFuture(null);
  }

  @NotNull
  public abstract CompletableFuture<Void> requestLayout(boolean animate);

  public abstract void layout(boolean animate);

  @NotNull
  public abstract SceneDecoratorFactory getSceneDecoratorFactory();

  public abstract Map<Object, Map<ResourceReference, ResourceValue>> getDefaultProperties();

  public abstract Map<Object, String> getDefaultStyles();

  @NotNull
  protected HitProvider getHitProvider(@NotNull NlComponent component) {
    return myHitProvider;
  }
}
