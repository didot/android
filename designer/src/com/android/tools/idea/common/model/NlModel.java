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
package com.android.tools.idea.common.model;

import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.common.command.NlWriteCommandAction;
import com.android.tools.idea.common.lint.LintAnnotationsModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.common.surface.ZoomType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationListener;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.rendering.RefreshRenderAction;
import com.android.tools.idea.rendering.TagSnapshot;
import com.android.tools.idea.res.ResourceNotificationManager;
import com.android.tools.idea.res.ResourceNotificationManager.ResourceChangeListener;
import com.android.tools.idea.uibuilder.api.DragHandler;
import com.android.tools.idea.uibuilder.api.DragType;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.util.ListenerCollection;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.Transferable;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static com.android.SdkConstants.*;

/**
 * Model for an XML file
 */
public class NlModel implements Disposable, ResourceChangeListener, ModificationTracker {
  private static final boolean CHECK_MODEL_INTEGRITY = false;
  private final Set<String> myPendingIds = Sets.newHashSet();

  @NotNull private final DesignSurface mySurface;
  @NotNull private final AndroidFacet myFacet;
  private final XmlFile myFile;
  private final ConfigurationListener myConfigurationListener = new ConfigurationListener() {
    @Override
    public boolean changed(int flags) {
      if ((flags & (CFG_DEVICE | CFG_DEVICE_STATE)) != 0 && !mySurface.isLayoutDisabled()) {
        mySurface.zoom(ZoomType.FIT_INTO);
      }

      return true;
    }
  };
  private final Configuration myConfiguration;
  private final ListenerCollection<ModelListener> myListeners = ListenerCollection.createWithDirectExecutor();
  private NlComponent myRootComponent;
  private LintAnnotationsModel myLintAnnotationsModel;
  private final long myId;
  private final Set<Object> myActivations = Collections.newSetFromMap(new WeakHashMap<>());
  private final ModelVersion myModelVersion = new ModelVersion();
  private final NlLayoutType myType;
  private long myConfigurationModificationCount;

  // Variable to track what triggered the latest render (if known)
  private ChangeType myModificationTrigger;

  @NotNull
  public static NlModel create(@NotNull DesignSurface surface,
                               @Nullable Disposable parent,
                               @NotNull AndroidFacet facet,
                               @NotNull XmlFile file) {
    return new NlModel(surface, parent, facet, file);
  }

  @VisibleForTesting
  protected NlModel(@NotNull DesignSurface surface,
                    @Nullable Disposable parent,
                    @NotNull AndroidFacet facet,
                    @NotNull XmlFile file) {
    mySurface = surface;
    myFacet = facet;
    myFile = file;
    myConfiguration = ConfigurationManager.getOrCreateInstance(facet).getConfiguration(myFile.getVirtualFile());
    myConfigurationModificationCount = myConfiguration.getModificationCount();
    myId = System.nanoTime() ^ file.getName().hashCode();
    if (parent != null) {
      Disposer.register(parent, this);
    }
    myType = NlLayoutType.typeOf(file);
  }

  /**
   * Notify model that it's active. A model is active by default.
   *
   * @param source caller used to keep track of the references to this model. See {@link #deactivate(Object)}
   */
  public void activate(@NotNull Object source) {
    // TODO: Tracking the source is just a workaround for the model being shared so the activations and deactivations are
    // handled correctly. This should be solved by moving the removing this responsibility from the model. The model shouldn't
    // need to keep track of activations/deactivation and they should be handled by the caller.
    boolean wasActive;
    synchronized (myActivations) {
      wasActive = !myActivations.isEmpty();
      myActivations.add(source);
    }
    if (!wasActive) {
      // This was the first activation so enable listeners
      myConfiguration.addListener(myConfigurationListener);

      // If the resources have changed or the configuration has been modified, request a model update
      if (myConfiguration.getModificationCount() != myConfigurationModificationCount) {
        updateTheme();
      }
      ResourceNotificationManager manager = ResourceNotificationManager.getInstance(getProject());
      manager.addListener(this, myFacet, myFile, myConfiguration);
      myListeners.forEach(listener -> listener.modelActivated(this));
    }
  }

  public void updateTheme() {
    String theme = myConfiguration.getTheme();
    ResourceUrl themeUrl = theme != null ? ResourceUrl.parse(myConfiguration.getTheme()) : null;
    if (themeUrl != null &&
        themeUrl.type == ResourceType.STYLE) {
      ResourceResolver resolver = myConfiguration.getResourceResolver();
      if (resolver == null || resolver.getTheme(themeUrl.name, themeUrl.framework) == null) {
        myConfiguration.setTheme(myConfiguration.getConfigurationManager().computePreferredTheme(myConfiguration));
      }
    }
  }

  private void deactivate() {
    myListeners.forEach(listener -> listener.modelDeactivated(this));
    ResourceNotificationManager manager = ResourceNotificationManager.getInstance(myFile.getProject());
    manager.removeListener(this, myFacet, myFile, myConfiguration);
    myConfigurationModificationCount = myConfiguration.getModificationCount();
    myConfiguration.removeListener(myConfigurationListener);
  }

  /**
   * Notify model that it's not active. This means it can stop watching for events etc. It may be activated again in the future.
   *
   * @param source the source is used to keep track of the references that are using this model. Only when all the sources have called
   *               {@link #deactivate(Object)}, the model will be really deactivated.
   */
  public void deactivate(@NotNull Object source) {
    boolean shouldDeactivate;
    synchronized (myActivations) {
      boolean removed = myActivations.remove(source);
      // If there are no more activations, call the private #deactivate()
      shouldDeactivate = removed && myActivations.isEmpty();
    }
    if (shouldDeactivate) {
      deactivate();
    }
  }

  @NotNull
  public XmlFile getFile() {
    return myFile;
  }

  @NotNull
  public NlLayoutType getType() {
    return myType;
  }

  @NotNull
  public DesignSurface getSurface() {
    return mySurface;
  }

  @NotNull
  public SelectionModel getSelectionModel() {
    return mySurface.getSelectionModel();
  }

  @Nullable
  public LintAnnotationsModel getLintAnnotationsModel() {
    return myLintAnnotationsModel;
  }

  public void setLintAnnotationsModel(@Nullable LintAnnotationsModel model) {
    myLintAnnotationsModel = model;
    // Deliberately not rev'ing the model version and firing changes here;
    // we know only the warnings layer cares about this change and can be
    // updated by a single repaint
  }

  @NotNull
  Set<String> getPendingIds() {
    return myPendingIds;
  }

  public void syncWithPsi(@NotNull XmlTag newRoot, @NotNull List<TagSnapshotTreeNode> roots) {
    new ModelUpdater(this).update(newRoot, roots);
  }

  public void checkStructure() {
    if (CHECK_MODEL_INTEGRITY) {
      ApplicationManager.getApplication().runReadAction(() -> {
        Set<NlComponent> unique = Sets.newIdentityHashSet();
        Set<XmlTag> uniqueTags = Sets.newIdentityHashSet();
        checkUnique(myFile.getRootTag(), uniqueTags);
        uniqueTags.clear();
        if (myRootComponent != null) {
          checkUnique(myRootComponent.getTag(), uniqueTags);
          checkUnique(myRootComponent, unique);
          checkStructure(myRootComponent);
        }
      });
    }
  }

  @SuppressWarnings("MethodMayBeStatic")
  private void checkUnique(NlComponent component, Set<NlComponent> unique) {
    if (CHECK_MODEL_INTEGRITY) {
      assert !unique.contains(component);
      unique.add(component);

      for (NlComponent child : component.getChildren()) {
        checkUnique(child, unique);
      }
    }
  }

  @SuppressWarnings("MethodMayBeStatic")
  private void checkUnique(XmlTag tag, Set<XmlTag> unique) {
    if (CHECK_MODEL_INTEGRITY) {
      assert !unique.contains(tag);
      unique.add(tag);
      for (XmlTag subTag : tag.getSubTags()) {
        checkUnique(subTag, unique);
      }
    }
  }

  @SuppressWarnings("MethodMayBeStatic")
  private void checkStructure(NlComponent component) {
    if (CHECK_MODEL_INTEGRITY) {
      // This is written like this instead of just "assert component.w != -1" to ease
      // setting breakpoint to debug problems
      if (NlComponentHelperKt.getHasNlComponentInfo(component)) {
        int w = NlComponentHelperKt.getW(component);
        if (w == -1) {
          assert false : w;
        }
      }
      if (component.getSnapshot() == null) {
        assert false;
      }
      if (component.getTag() == null) {
        assert false;
      }
      if (!component.getTagName().equals(component.getTag().getName())) {
        assert false;
      }

      if (!component.getTag().isValid()) {
        assert false;
      }

      // Look for parent chain cycle
      NlComponent p = component.getParent();
      while (p != null) {
        if (p == component) {
          assert false;
        }
        p = p.getParent();
      }

      for (NlComponent child : component.getChildren()) {
        if (child == component) {
          assert false;
        }
        if (child.getParent() == null) {
          assert false;
        }
        if (child.getParent() != component) {
          assert false;
        }
        if (child.getTag().getParent() != component.getTag()) {
          assert false;
        }

        // Check recursively
        checkStructure(child);
      }
    }
  }

  /**
   * Adds a new {@link ModelListener}. If the listener already exists, this method will make sure that the listener is only
   * added once.
   */
  public void addListener(@NotNull ModelListener listener) {
    myListeners.add(listener);
  }

  public void removeListener(@Nullable ModelListener listener) {
    myListeners.remove(listener);
  }

  /**
   * Calls all the listeners {@link ModelListener#modelDerivedDataChanged(NlModel)} method.
   *
   * // TODO: move this mechanism to LayoutlibSceneManager, or, ideally, remove the need for it entirely by
   * // moving all the derived data into the Scene.
   */
  public void notifyListenersModelUpdateComplete() {
    myListeners.forEach(listener -> listener.modelDerivedDataChanged(this));
  }

  /**
   * Calls all the listeners {@link ModelListener#modelChangedOnLayout(NlModel, boolean)} method.
   *
   * TODO: move these listeners out of NlModel, since the model shouldn't care about being laid out.
   *
   * @param animate if true, warns the listeners to animate the layout update
   */
  public void notifyListenersModelLayoutComplete(boolean animate) {
    myListeners.forEach(listener -> listener.modelChangedOnLayout(this, animate));
  }

  @NotNull
  public AndroidFacet getFacet() {
    return myFacet;
  }

  @NotNull
  public Module getModule() {
    return myFacet.getModule();
  }

  @NotNull
  public Project getProject() {
    return getModule().getProject();
  }

  @NotNull
  public Configuration getConfiguration() {
    return myConfiguration;
  }

  @NotNull
  public ImmutableList<NlComponent> getComponents() {
    return myRootComponent != null ? ImmutableList.of(myRootComponent) : ImmutableList.of();
  }

  @NotNull
  public Stream<NlComponent> flattenComponents() {
    return myRootComponent != null ? Stream.of(myRootComponent).flatMap(NlComponent::flatten) : Stream.empty();
  }

  /**
   * This will warn model listeners that the model has been changed "live", without
   * the attributes of components being actually committed. Listeners such as Scene Managers will
   * likely want for example to schedule a layout pass in reaction to that callback.
   *
   * @param animate should the changes be animated or not.
   */
  public void notifyLiveUpdate(boolean animate) {
    myListeners.forEach(listener -> listener.modelLiveUpdate(this, animate));
  }

  /**
   * A node in a tree structure where each node provides a {@link TagSnapshot}.
   */
  public interface TagSnapshotTreeNode {
    @Nullable
    TagSnapshot getTagSnapshot();

    @NotNull
    List<TagSnapshotTreeNode> getChildren();
  }

  /**
   * Synchronizes a {@linkplain NlModel} such that the component hierarchy
   * is up to date wrt tag snapshots etc. Crucially, it attempts to preserve
   * component hierarchy (since XmlTags may sometimes not survive a PSI reparse, but we
   * want the {@linkplain NlComponent} instances to keep the same instances across these
   * edits such that for example the selection (a set of {@link NlComponent} instances)
   * are preserved.
   */
  private static class ModelUpdater {
    private final NlModel myModel;
    private final Map<XmlTag, NlComponent> myTagToComponentMap = Maps.newIdentityHashMap();
    private final Map<NlComponent, XmlTag> myComponentToTagMap = Maps.newIdentityHashMap();
    /**
     * Map from snapshots in the old component map to the corresponding components
     */
    protected final Map<TagSnapshot, NlComponent> mySnapshotToComponent = Maps.newIdentityHashMap();
    /**
     * Map from tags in the view render tree to the corresponding snapshots
     */
    private final Map<XmlTag, TagSnapshot> myTagToSnapshot = Maps.newHashMap();

    public ModelUpdater(@NotNull NlModel model) {
      myModel = model;
    }

    private void recordComponentMapping(@NotNull XmlTag tag, @NotNull NlComponent component) {
      // Is the component already registered to some other tag?
      XmlTag prevTag = myComponentToTagMap.get(component);
      if (prevTag != null) {
        // Yes. Unregister it.
        myTagToComponentMap.remove(prevTag);
      }

      myComponentToTagMap.put(component, tag);
      myTagToComponentMap.put(tag, component);
    }

    /**
     * Update the component hierarchy associated with this {@link NlModel} such
     * that the associated component list correctly reflects the latest versions of the
     * XML PSI file, the given tag snapshot and {@link TagSnapshotTreeNode} hierarchy
     */
    @VisibleForTesting
    public void update(@Nullable XmlTag newRoot, @NotNull List<TagSnapshotTreeNode> roots) {
      if (newRoot == null) {
        myModel.myRootComponent = null;
        return;
      }

      boolean isValidRoot = ApplicationManager.getApplication().runReadAction((Computable<Boolean>)newRoot::isValid);
      if (!isValidRoot) {
        myModel.myRootComponent = null;
        return;
      }

      // Next find the snapshots corresponding to the missing components.
      // We have to search among the view infos in the new components.
      for (TagSnapshotTreeNode root : roots) {
        gatherTagsAndSnapshots(root, myTagToSnapshot);
      }

      NlComponent rootComponent = ApplicationManager.getApplication().runReadAction((Computable<NlComponent>)() -> {
        // Ensure that all XmlTags in the new XmlFile contents map to a corresponding component
        // form the old map
        mapOldToNew(newRoot);

        for (Map.Entry<XmlTag, NlComponent> entry : myTagToComponentMap.entrySet()) {
          XmlTag tag = entry.getKey();
          NlComponent component = entry.getValue();
          if (!component.getTagName().equals(tag.getName())) {
            // One or more incompatible changes: PSI nodes have been reused unpredictably
            // so completely recompute the hierarchy
            myTagToComponentMap.clear();
            myComponentToTagMap.clear();
            break;
          }
        }

        // Build up the new component tree
        return createTree(newRoot);
      });

      myModel.myRootComponent = rootComponent;

      // Wipe out state in older components to make sure on reuse we don't accidentally inherit old
      // data
      for (NlComponent component : myTagToComponentMap.values()) {
        component.setSnapshot(null);
      }

      // Update the components' snapshots
      for (TagSnapshotTreeNode root : roots) {
        updateHierarchy(root);
      }
    }

    private void mapOldToNew(@NotNull XmlTag newRootTag) {
      ApplicationManager.getApplication().assertReadAccessAllowed();

      // First build up a new component tree to reflect the latest XmlFile hierarchy.
      // If there have been no structural changes, these map 1-1 from the previous hierarchy.
      // We first attempt to do it based on the XmlTags:
      //  (1) record a map from XmlTag to NlComponent in the previous component list
      for (NlComponent component : myModel.getComponents()) {
        gatherTagsAndSnapshots(component);
      }

      // Look for any NlComponents no longer present in the new set
      List<XmlTag> missing = Lists.newArrayList();
      Set<XmlTag> remaining = Sets.newIdentityHashSet();
      remaining.addAll(myTagToComponentMap.keySet());
      checkMissing(newRootTag, remaining, missing);

      // If we've just removed a component, there will be no missing tags; we
      // can build the new/updated component hierarchy directly from the old
      // NlComponent instances
      if (missing.isEmpty()) {
        return;
      }

      // If we've just added a component, there will be no remaining tags from
      // old component instances. In this case all components should be new
      // instances
      if (remaining.isEmpty()) {
        return;
      }

      // Try to map more component instances from old to new.
      // We will do this via multiple heuristics:
      //   - mapping id's
      //   - looking at all component attributes (e.g. snapshots)

      // First check by id.
      // Note: We can't use XmlTag#getAttribute on the old component hierarchy;
      // those elements may not be valid and PSI will throw exceptions if we
      // attempt to access them.
      Map<String, NlComponent> oldIds = Maps.newHashMap();
      for (Map.Entry<TagSnapshot, NlComponent> entry : mySnapshotToComponent.entrySet()) {
        TagSnapshot snapshot = entry.getKey();
        if (snapshot != null) {
          String id = snapshot.getAttribute(ATTR_ID, ANDROID_URI);
          if (id != null) {
            oldIds.put(id, entry.getValue());
          }
        }
      }
      ListIterator<XmlTag> missingIterator = missing.listIterator();
      while (missingIterator.hasNext()) {
        XmlTag tag = missingIterator.next();
        String id = tag.getAttributeValue(ATTR_ID, ANDROID_URI);
        if (id != null) {
          // TODO: Consider unifying @+id/ and @id/ references here
          // (though it's unlikely for this to change across component
          // synchronization operations)
          NlComponent component = oldIds.get(id);
          if (component != null) {
            recordComponentMapping(tag, component);
            remaining.remove(component.getTag());
            missingIterator.remove();
          }
        }
      }

      if (missing.isEmpty() || remaining.isEmpty()) {
        // We've now resolved everything
        return;
      }

      // Next attempt to correlate components based on tag snapshots

      // First compute fingerprints of the old components
      Multimap<Long, TagSnapshot> snapshotIds = ArrayListMultimap.create();
      for (XmlTag old : remaining) {
        NlComponent component = myTagToComponentMap.get(old);
        if (component != null) { // this *should* be the case
          TagSnapshot snapshot = component.getSnapshot();
          if (snapshot != null) {
            snapshotIds.put(snapshot.getSignature(), snapshot);
          }
        }
      }

      // Note that we're using a multimap rather than a map for these keys,
      // so if you have the same exact element and attributes multiple times,
      // they'll be found and matched in the same order. (This works because
      // we're also tracking the missing xml tags in iteration order by using a
      // list instead of a set.)
      missingIterator = missing.listIterator();
      while (missingIterator.hasNext()) {
        XmlTag tag = missingIterator.next();
        TagSnapshot snapshot = myTagToSnapshot.get(tag);
        if (snapshot != null) {
          long signature = snapshot.getSignature();
          Collection<TagSnapshot> snapshots = snapshotIds.get(signature);
          if (!snapshots.isEmpty()) {
            TagSnapshot first = snapshots.iterator().next();
            NlComponent component = mySnapshotToComponent.get(first);
            if (component != null) {
              recordComponentMapping(tag, component);
              remaining.remove(component.getTag());
              snapshotIds.remove(tag, first);
              missingIterator.remove();
            }
          }
        }
      }

      // Finally, if there's just a single tag in question, it might have been
      // that we changed an attribute of a tag (so the fingerprint no longer matches).
      // If the tag name is identical, we'll go ahead.
      if (missing.size() == 1 && remaining.size() == 1) {
        XmlTag oldTag = remaining.iterator().next();
        NlComponent component = myTagToComponentMap.get(oldTag);
        if (component != null) {
          XmlTag newTag = missing.get(0);
          TagSnapshot snapshot = component.getSnapshot();
          if (snapshot != null) {
            if (snapshot.tagName.equals(newTag.getName())) {
              recordComponentMapping(newTag, component);
            }
          }
        }
      }
    }

    /**
     * Processes through the XML tag hierarchy recursively, and checks
     * whether the tag is in the remaining set, and if so removes it,
     * otherwise adds it to the missing set.
     */
    private static void checkMissing(XmlTag tag, Set<XmlTag> remaining, List<XmlTag> missing) {

      boolean found = remaining.remove(tag);
      if (!found) {
        missing.add(tag);
      }
      for (XmlTag child : tag.getSubTags()) {
        checkMissing(child, remaining, missing);
      }
    }

    private void gatherTagsAndSnapshots(@NotNull NlComponent component) {
      XmlTag tag = component.getTag();

      recordComponentMapping(tag, component);
      mySnapshotToComponent.put(component.getSnapshot(), component);

      for (NlComponent child : component.getChildren()) {
        gatherTagsAndSnapshots(child);
      }
    }

    private static void gatherTagsAndSnapshots(@NotNull TagSnapshotTreeNode node, @NotNull Map<XmlTag, TagSnapshot> map) {
      TagSnapshot snapshot = node.getTagSnapshot();
      if (snapshot != null) {
        map.put(snapshot.tag, snapshot);
      }

      for (TagSnapshotTreeNode child : node.getChildren()) {
        gatherTagsAndSnapshots(child, map);
      }
    }

    @NotNull
    private NlComponent createTree(@NotNull XmlTag tag) {
      NlComponent component = myTagToComponentMap.get(tag);
      if (component == null) {
        // New component: tag didn't exist in the previous component hierarchy,
        // and no similar tag was found
        component = myModel.createComponent(tag);
        recordComponentMapping(tag, component);
      }

      XmlTag[] subTags = tag.getSubTags();
      if (subTags.length > 0) {
        if (CHECK_MODEL_INTEGRITY) {
          Set<NlComponent> seen = Sets.newHashSet();
          Set<XmlTag> seenTags = Sets.newHashSet();
          for (XmlTag t : subTags) {
            if (seenTags.contains(t)) {
              assert false : t;
            }
            seenTags.add(t);
            NlComponent registeredComponent = myTagToComponentMap.get(t);
            if (registeredComponent != null) {
              if (seen.contains(registeredComponent)) {
                assert false : registeredComponent;
              }
              seen.add(registeredComponent);
            }
          }
        }

        List<NlComponent> children = new ArrayList<>(subTags.length);
        for (XmlTag subtag : subTags) {
          NlComponent child = createTree(subtag);
          children.add(child);
        }
        component.setChildren(children);
      }
      else {
        component.setChildren(null);
      }

      return component;
    }

    private void updateHierarchy(@NotNull TagSnapshotTreeNode node) {
      TagSnapshot snapshot = node.getTagSnapshot();
      NlComponent component;
      if (snapshot != null) {
        component = mySnapshotToComponent.get(snapshot);
        if (component == null) {
          component = myTagToComponentMap.get(snapshot.tag);
        }

        if (component != null) {
          component.setSnapshot(snapshot);
          assert snapshot.tag != null;
          component.setTag(snapshot.tag);
        }
      }
      for (TagSnapshotTreeNode child : node.getChildren()) {
        updateHierarchy(child);
      }
    }
  }

  @NotNull
  public ImmutableList<NlComponent> findByOffset(int offset) {
    XmlTag tag = PsiTreeUtil.findElementOfClassAtOffset(myFile, offset, XmlTag.class, false);
    return (tag != null) ? findViewsByTag(tag) : ImmutableList.of();
  }

  @Nullable
  public NlComponent findViewByTag(@NotNull XmlTag tag) {
    return myRootComponent != null ? myRootComponent.findViewByTag(tag) : null;
  }

  @Nullable
  public NlComponent find(@NotNull String id) {
    return flattenComponents().filter(c -> id.equals(c.getId())).findFirst().orElse(null);
  }

  @NotNull
  private ImmutableList<NlComponent> findViewsByTag(@NotNull XmlTag tag) {
    if (myRootComponent == null) {
      return ImmutableList.of();
    }

    return myRootComponent.findViewsByTag(tag);
  }

  @Nullable
  public NlComponent findViewByPsi(@Nullable PsiElement element) {
    assert ApplicationManager.getApplication().isReadAccessAllowed();

    while (element != null) {
      if (element instanceof XmlTag) {
        return findViewByTag((XmlTag)element);
      }
      element = element.getParent();
    }

    return null;
  }

  public void delete(final Collection<NlComponent> components) {
    // Group by parent and ask each one to participate
    WriteCommandAction<Void> action = new WriteCommandAction<Void>(myFacet.getModule().getProject(), "Delete Component", myFile) {
      @Override
      protected void run(@NotNull Result<Void> result) throws Throwable {
        handleDeletion(components);
      }
    };
    action.execute();

    List<NlComponent> remaining = Lists.newArrayList(getSelectionModel().getSelection());
    remaining.removeAll(components);
    getSelectionModel().setSelection(remaining);
    notifyModified(ChangeType.DELETE);
  }

  private void handleDeletion(@NotNull Collection<NlComponent> components) {
    // Segment the deleted components into lists of siblings
    Multimap<NlComponent, NlComponent> siblingLists = NlComponentUtil.groupSiblings(components);

    // Notify parent components about children getting deleted
    for (NlComponent parent : siblingLists.keySet()) {
      if (parent == null) {
        continue;
      }

      Collection<NlComponent> children = siblingLists.get(parent);
      if (!NlModelHelper.INSTANCE.handleDeletion(parent, children)) {
        for (NlComponent component : children) {
          NlComponent p = component.getParent();
          if (p != null) {
            p.removeChild(component);
          }
          component.getTag().delete();
        }
      }
    }
  }

  /**
   * Creates a new component of the given type. It will optionally insert it as a child of the given parent (and optionally
   * right before the given sibling or null to append at the end.)
   * <p/>
   * Note: This operation can only be called when the caller is already holding a write lock. This will be the
   * case from {@link ViewHandler} callbacks such as {@link ViewHandler#onCreate} and {@link DragHandler#commit}.
   * <p/>
   * Note: The caller is responsible for calling {@link #notifyModified(ChangeType)} if the creation completes successfully.
   *
   * @param tag    The XmlTag for the component.
   * @param parent The parent to add this component to.
   * @param before The sibling to insert immediately before, or null to append
   */
  public NlComponent createComponent(@NotNull XmlTag tag,
                                     @Nullable NlComponent parent,
                                     @Nullable NlComponent before) {
    if (parent != null) {
      // Creating a component intended to be inserted into an existing layout
      XmlTag parentTag = parent.getTag();
      if (before != null) {
        tag = (XmlTag)parentTag.addBefore(tag, before.getTag());
      }
      else {
        tag = parentTag.addSubTag(tag, false);
      }
    }

    NlComponent child = createComponent(tag);

    if (parent != null) {
      parent.addChild(child, before);
    }

    return child;
  }

  /**
   * Simply create a component. In most cases you probably want
   * {@link #createComponent(SceneView, XmlTag, NlComponent, NlComponent, InsertType)}.
   */
  public NlComponent createComponent(@NotNull XmlTag tag) {
    return mySurface.createComponent(tag);
  }

  @NotNull
  public Transferable getSelectionAsTransferable() {
    return getSelectionModel().getTransferable(myId);
  }

  /**
   * Returns true if the specified components can be added to the specified receiver.
   */
  public boolean canAddComponents(@Nullable List<NlComponent> toAdd, @NotNull NlComponent receiver, @Nullable NlComponent before) {
    if (before != null && before.getParent() != receiver) {
      return false;
    }
    if (toAdd == null || toAdd.isEmpty()) {
      return false;
    }
    if (!NlModelHelperKt.canAddComponents(this, receiver, toAdd)) {
      return false;
    }

    for (NlComponent component : toAdd) {
      // If the receiver is a (possibly indirect) child of any of the dragged components, then reject the operation
      NlComponent same = receiver;
      while (same != null) {
        if (same == component) {
          return false;
        }
        same = same.getParent();
      }
    }

    return true;
  }

  /**
   * Adds components to the specified receiver before the given sibling.
   * If insertType is a move the components specified should be components from this model.
   */
  public void addComponents(@Nullable List<NlComponent> toAdd,
                            @NotNull NlComponent receiver,
                            @Nullable NlComponent before,
                            @NotNull InsertType insertType) {
    if (!canAddComponents(toAdd, receiver, before)) {
      return;
    }
    if (!NlModelHelperKt.addDependencies(this, toAdd, insertType)) {
      return;
    }

    assert toAdd != null;
    NlWriteCommandAction.run(toAdd, insertType.getDragType().getDescription(), () -> handleAddition(toAdd, receiver, before, insertType));

    notifyModified(ChangeType.ADD_COMPONENTS);
  }

  /**
   * Add tags component to the specified receiver before the given sibling.
   */
  public void addTags(@NotNull List<NlComponent> added,
                      @NotNull NlComponent receiver,
                      @Nullable NlComponent before,
                      final @NotNull InsertType insertType) {
    NlWriteCommandAction.run(added, insertType.getDragType().getDescription(), () -> {
      for (NlComponent component : added) {
        NlComponent parent = component.getParent();
        if (parent != null) {
          parent.removeChild(component);
        }
        receiver.addChild(component, before);
        if (receiver.getTag() != component.getTag()) {
          XmlTag prev = component.getTag();
          transferNamespaces(prev);
          if (before != null) {
            component.setTag((XmlTag)receiver.getTag().addBefore(component.getTag(), before.getTag()));
          }
          else {
            component.setTag(receiver.getTag().addSubTag(component.getTag(), false));
          }
          if (insertType.isMove()) {
            prev.delete();
          }
        }
        removeNamespaceAttributes(component);
      }
    });

    notifyModified(ChangeType.ADD_COMPONENTS);
  }

  private void handleAddition(@NotNull List<NlComponent> added,
                              @NotNull NlComponent receiver,
                              @Nullable NlComponent before,
                              @NotNull InsertType insertType) {
    // TODO: remove this
    NlModelHelperKt.handleAddition(this, added, receiver, insertType, mySurface);

    for (NlComponent component : added) {
      NlComponent parent = component.getParent();
      if (parent != null) {
        parent.removeChild(component);
      }
      receiver.addChild(component, before);
      if (receiver.getTag() != component.getTag()) {
        XmlTag prev = component.getTag();
        transferNamespaces(prev);
        if (before != null) {
          component.setTag((XmlTag)receiver.getTag().addBefore(component.getTag(), before.getTag()));
        }
        else {
          component.setTag(receiver.getTag().addSubTag(component.getTag(), false));
        }
        if (insertType.isMove()) {
          prev.delete();
        }
      }
      removeNamespaceAttributes(component);
    }
  }

  /**
   * Given a root tag which is not yet part of the current document, (1) look up any namespaces defined on that root tag, transfer
   * those to the current document, and (2) update all attribute prefixes for namespaces to match those in the current document
   */
  private void transferNamespaces(@NotNull XmlTag tag) {
    // Transfer namespace attributes
    XmlDocument xmlDocument = myFile.getDocument();
    assert xmlDocument != null;
    XmlTag rootTag = xmlDocument.getRootTag();
    assert rootTag != null;
    Map<String, String> prefixToNamespace = rootTag.getLocalNamespaceDeclarations();
    Map<String, String> namespaceToPrefix = Maps.newHashMap();
    for (Map.Entry<String, String> entry : prefixToNamespace.entrySet()) {
      namespaceToPrefix.put(entry.getValue(), entry.getKey());
    }
    Map<String, String> oldPrefixToPrefix = Maps.newHashMap();

    for (Map.Entry<String, String> entry : tag.getLocalNamespaceDeclarations().entrySet()) {
      String namespace = entry.getValue();
      String prefix = entry.getKey();
      String currentPrefix = namespaceToPrefix.get(namespace);
      if (currentPrefix == null) {
        // The namespace isn't used in the document. Import it.
        String newPrefix = AndroidResourceUtil.ensureNamespaceImported(myFile, namespace, prefix);
        if (!prefix.equals(newPrefix)) {
          // We imported the namespace, but the prefix used in the new document isn't available
          // so we need to update all attribute references to the new name
          oldPrefixToPrefix.put(prefix, newPrefix);
          namespaceToPrefix.put(namespace, newPrefix);
        }
      }
      else if (!prefix.equals(currentPrefix)) {
        // The namespace is already imported, but using a different prefix. We need
        // to switch the prefixes.
        oldPrefixToPrefix.put(prefix, currentPrefix);
      }
    }

    if (!oldPrefixToPrefix.isEmpty()) {
      updatePrefixes(tag, oldPrefixToPrefix);
    }
  }

  /**
   * Recursively update all attributes such that XML attributes with prefixes in the {@code oldPrefixToPrefix} key set
   * are replaced with the corresponding values
   */
  private static void updatePrefixes(@NotNull XmlTag tag, @NotNull Map<String, String> oldPrefixToPrefix) {
    for (XmlAttribute attribute : tag.getAttributes()) {
      String prefix = attribute.getNamespacePrefix();
      if (!prefix.isEmpty()) {
        if (prefix.equals(XMLNS)) {
          String newPrefix = oldPrefixToPrefix.get(attribute.getLocalName());
          if (newPrefix != null) {
            attribute.setName(XMLNS_PREFIX + newPrefix);
          }
        }
        else {
          String newPrefix = oldPrefixToPrefix.get(prefix);
          if (newPrefix != null) {
            attribute.setName(newPrefix + ':' + attribute.getLocalName());
          }
        }
      }
    }

    for (XmlTag child : tag.getSubTags()) {
      updatePrefixes(child, oldPrefixToPrefix);
    }
  }

  private static void removeNamespaceAttributes(NlComponent component) {
    for (XmlAttribute attribute : component.getTag().getAttributes()) {
      if (attribute.getName().startsWith(XMLNS_PREFIX)) {
        attribute.delete();
      }
    }
  }

  @NotNull
  public InsertType determineInsertType(@NotNull DragType dragType, @Nullable DnDTransferItem item, boolean asPreview) {
    if (item != null && item.isFromPalette()) {
      return asPreview ? InsertType.CREATE_PREVIEW : InsertType.CREATE;
    }
    switch (dragType) {
      case CREATE:
        return asPreview ? InsertType.CREATE_PREVIEW : InsertType.CREATE;
      case MOVE:
        return item != null && myId != item.getModelId() ? InsertType.COPY : InsertType.MOVE_INTO;
      case COPY:
        return InsertType.COPY;
      case PASTE:
      default:
        return InsertType.PASTE;
    }
  }

  @Override
  public void dispose() {
    boolean shouldDeactivate;
    synchronized (myActivations) {
      // If there are no activations left, make sure we deactivate the model correctly
      shouldDeactivate = !myActivations.isEmpty();
      myActivations.clear();
    }
    if (shouldDeactivate) {
      deactivate(); // ensure listeners are unregistered if necessary
    }

    myListeners.clear();
  }

  @Override
  public String toString() {
    return NlModel.class.getSimpleName() + " for " + myFile;
  }

  // ---- Implements ResourceNotificationManager.ResourceChangeListener ----

  @Override
  public void resourcesChanged(@NotNull Set<ResourceNotificationManager.Reason> reason) {
    for (ResourceNotificationManager.Reason r : reason) {
      switch (r) {
        case RESOURCE_EDIT:
          notifyModified(ChangeType.RESOURCE_EDIT);
          break;
        case EDIT:
          notifyModified(ChangeType.EDIT);
          break;
        case IMAGE_RESOURCE_CHANGED:
          RefreshRenderAction.clearCache(mySurface);
          break;
        case GRADLE_SYNC:
        case PROJECT_BUILD:
        case VARIANT_CHANGED:
        case SDK_CHANGED:
          notifyModified(ChangeType.BUILD);
          break;
        case CONFIGURATION_CHANGED:
          notifyModified(ChangeType.CONFIGURATION_CHANGE);
          break;
      }
    }
  }

  // ---- Implements ModificationTracker ----

  public enum ChangeType {
    RESOURCE_EDIT,
    EDIT,
    RESOURCE_CHANGED,
    ADD_COMPONENTS,
    DELETE,
    DND_COMMIT,
    DND_END,
    DROP,
    RESIZE_END, RESIZE_COMMIT,
    UPDATE_HIERARCHY,
    BUILD,
    CONFIGURATION_CHANGE
  }

  /**
   * Maintains multiple counter depending on what did change in the model
   */
  static class ModelVersion {
    private final AtomicLong myVersion = new AtomicLong();
    @SuppressWarnings("unused") ChangeType mLastReason;

    public void increase(ChangeType reason) {
      myVersion.incrementAndGet();
      mLastReason = reason;
    }

    public long getVersion() {
      return myVersion.get();
    }
  }

  @Override
  public long getModificationCount() {
    return myModelVersion.getVersion();
  }

  public long getConfigurationModificationCount() {
    return myConfigurationModificationCount;
  }

  public void notifyModified(ChangeType reason) {
    myModelVersion.increase(reason);
    updateTheme();
    myModificationTrigger = reason;
    myListeners.forEach(listener -> listener.modelChanged(this));
  }

  public ChangeType getLastChangeType() {
    return myModificationTrigger;
  }

  public void resetLastChange() {
    myModificationTrigger = null;
  }
}
