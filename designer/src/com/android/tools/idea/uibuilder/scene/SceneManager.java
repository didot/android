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

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.ConstraintDragDndTarget;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.scene.decorator.SceneDecoratorFactory;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.util.PropertiesMap;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A facility for creating and updating {@link Scene}s based on {@link NlModel}s.
 */
abstract public class SceneManager implements Disposable {

  public static final boolean SUPPORTS_LOCKING = false;

  private final NlModel myModel;
  final private DesignSurface myDesignSurface;
  private Scene myScene;

  public SceneManager(NlModel model, DesignSurface surface) {
    myModel = model;
    myDesignSurface = surface;
    Disposer.register(model, this);
  }

  @Override
  public void dispose() {
  }

  /**
   * Constructs a {@link Scene} from our {@link NlModel}. Must only be called once. For updates use {@link #update()}.
   * @return
   */
  @NotNull
  public Scene build() {
    assert myScene == null;
    myScene = new Scene(myDesignSurface);
    return myScene;
  }

  /**
   * Update the Scene with the components in the given NlModel. This method needs to be called in the dispatch thread.
   * {@link #build()} must have been invoked already.
   * This includes marking the display list as dirty.
   */
  public void update() {
    List<NlComponent> components = getModel().getComponents();
    Scene scene = getScene();
    if (components.size() == 0) {
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

    SceneComponent root = createHierarchy(rootComponent);
    if (root != null) {
      updateFromComponent(root, usedComponents);
    }
    oldComponents.removeAll(usedComponents);
    // The temporary component are not present in the NLModel so won't be added to the used component array
    oldComponents.removeIf(component -> component instanceof TemporarySceneComponent);
    oldComponents.forEach(scene::removeComponent);

    scene.setRoot(root);
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
   * Create SceneComponents corresponding to an NlComponent hierarchy
   */
  @Nullable
  protected SceneComponent createHierarchy(@NotNull NlComponent component) {
    SceneComponent sceneComponent = getScene().getSceneComponent(component);
    if (sceneComponent == null) {
      sceneComponent = new SceneComponent(getScene(), component);
    }
    sceneComponent.setToolLocked(isComponentLocked(component));
    Set<SceneComponent> oldChildren = new HashSet<>(sceneComponent.getChildren());
    for (NlComponent nlChild : component.getChildren()) {
      SceneComponent child = createHierarchy(nlChild);
      oldChildren.remove(child);
      if (child != null && child.getParent() != sceneComponent) {
        sceneComponent.addChild(child);
      }
    }
    for (SceneComponent child : oldChildren) {
      if (child.getParent() == sceneComponent) {
        child.removeFromParent();
      }
    }
    return sceneComponent;
  }

  /**
   * Update the SceneComponent paired to the given NlComponent and its children.
   *
   * @param component      the root SceneComponent to update
   * @param seenComponents Collector of components that were seen during NlComponent tree traversal.
   * @return the SceneComponent paired with the given NlComponent
   */
  protected final void updateFromComponent(@NotNull SceneComponent component, @NotNull Set<SceneComponent> seenComponents) {
    seenComponents.add(component);

    updateFromComponent(component);

    for (SceneComponent child : component.getChildren()) {
      updateFromComponent(child, seenComponents);
    }
  }

  /**
   * Creates a {@link TemporarySceneComponent} in our Scene.
   */
  @NotNull
  public TemporarySceneComponent createTemporaryComponent(@NotNull NlComponent component) {
    Scene scene = getScene();

    assert scene.getRoot() != null;

    TemporarySceneComponent tempComponent = new TemporarySceneComponent(getScene(), component);
    tempComponent.addTarget(new ConstraintDragDndTarget());
    scene.setAnimated(false);
    scene.getRoot().addChild(tempComponent);
    updateFromComponent(tempComponent);
    scene.setAnimated(true);

    return tempComponent;
  }

  /**
   * Updates a single SceneComponent from its corresponding NlComponent.
   */
  protected void updateFromComponent(SceneComponent sceneComponent) {
    sceneComponent.setToolLocked(false); // the root is always unlocked.
  }

  @NotNull
  protected DesignSurface getDesignSurface() {
    return myDesignSurface;
  }

  @NotNull
  protected NlModel getModel() {
    return myModel;
  }

  @NotNull
  protected Scene getScene() {
    assert myScene != null;
    return myScene;
  }

  public abstract void requestRender();

  public abstract void layout(boolean animate);

  @NotNull
  public abstract SceneDecoratorFactory getSceneDecoratorFactory();

  public abstract Map<Object, PropertiesMap> getDefaultProperties();
}
