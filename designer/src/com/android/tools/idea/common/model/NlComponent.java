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

import com.android.resources.ResourceFolderType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.rendering.AttributeSnapshot;
import com.android.tools.idea.rendering.TagSnapshot;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.uibuilder.handlers.relative.DependencyGraph;
import com.android.tools.idea.uibuilder.model.AttributesHelperKt;
import com.android.tools.idea.uibuilder.model.QualifiedName;
import com.android.tools.idea.util.ListenerCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.util.*;
import java.util.stream.Stream;

import static com.android.SdkConstants.*;

/**
 * Represents a component editable in the UI builder. A component has properties,
 * if visual it has bounds, etc.
 */
public class NlComponent implements NlAttributesHolder {

  @Nullable private XmlModelComponentMixin myMixin;

  @Nullable public List<NlComponent> children;
  private NlComponent myParent;
  @NotNull private final NlModel myModel;
  @NotNull private XmlTag myTag;
  @NotNull private String myTagName; // for non-read lock access elsewhere
  @Nullable private TagSnapshot mySnapshot;
  final HashMap<Object, Object> myClientProperties = new HashMap<>();
  private final ListenerCollection<ChangeListener> myListeners = ListenerCollection.createWithDirectExecutor();
  private final ChangeEvent myChangeEvent = new ChangeEvent(this);
  private DependencyGraph myCachedDependencyGraph;

  /**
   * Current open attributes transaction or null if none is open
   */
  @Nullable AttributesTransaction myCurrentTransaction;

  public NlComponent(@NotNull NlModel model, @NotNull XmlTag tag) {
    myModel = model;
    myTag = tag;
    myTagName = tag.getName();
  }

  public void setMixin(@NotNull XmlModelComponentMixin mixin) {
    assert myMixin == null;
    myMixin = mixin;
  }

  @Nullable
  public XmlModelComponentMixin getMixin() {
    return myMixin;
  }

  @NotNull
  public XmlTag getTag() {
    return myTag;
  }

  @NotNull
  public NlModel getModel() {
    return myModel;
  }

  public void setTag(@NotNull XmlTag tag) {
    myTag = tag;
    myTagName = tag.getName();
  }

  @Nullable
  public TagSnapshot getSnapshot() {
    return mySnapshot;
  }

  public void setSnapshot(@Nullable TagSnapshot snapshot) {
    mySnapshot = snapshot;
  }

  public void addChild(@NotNull NlComponent component) {
    addChild(component, null);
  }

  public void addChild(@NotNull NlComponent component, @Nullable NlComponent before) {
    if (component == this) {
      throw new IllegalArgumentException();
    }
    if (children == null) {
      children = Lists.newArrayList();
    }
    int index = before != null ? children.indexOf(before) : -1;
    if (index != -1) {
      children.add(index, component);
    }
    else {
      children.add(component);
    }
    component.setParent(this);
  }

  public void removeChild(@NotNull NlComponent component) {
    if (component == this) {
      throw new IllegalArgumentException();
    }
    if (children != null) {
      children.remove(component);
    }
    component.setParent(null);
  }

  public void setChildren(@Nullable List<NlComponent> components) {
    children = components;
    if (components != null) {
      for (NlComponent component : components) {
        if (component == this) {
          throw new IllegalArgumentException();
        }
        component.setParent(this);
      }
    }
  }

  @NotNull
  public List<NlComponent> getChildren() {
    return children != null ? children : Collections.emptyList();
  }

  public int getChildCount() {
    return children != null ? children.size() : 0;
  }

  @Nullable
  public NlComponent getChild(int index) {
    return children != null && index >= 0 && index < children.size() ? children.get(index) : null;
  }

  @NotNull
  public Stream<NlComponent> flatten() {
    return Stream.concat(
      Stream.of(this),
      getChildren().stream().flatMap(NlComponent::flatten));
  }

  /**
   * Returns the {@link DependencyGraph} for the given relative layout widget
   *
   * @return a {@link DependencyGraph} for the layout
   */
  @NotNull
  public DependencyGraph getDependencyGraph() {
    if (myCachedDependencyGraph == null || myCachedDependencyGraph.isStale(this)) {
      myCachedDependencyGraph = new DependencyGraph(this);
    }
    return myCachedDependencyGraph;
  }

  @Nullable
  public NlComponent getNextSibling() {
    if (myParent == null) {
      return null;
    }
    for (int index = 0; index < myParent.getChildCount(); index++) {
      if (myParent.getChild(index) == this) {
        return myParent.getChild(index + 1);
      }
    }
    return null;
  }

  @Nullable
  public NlComponent findViewByTag(@NotNull XmlTag tag) {
    if (myTag == tag) {
      return this;
    }

    for (NlComponent child : getChildren()) {
      NlComponent result = child.findViewByTag(tag);
      if (result != null) {
        return result;
      }
    }

    return null;
  }

  private void findViewsByTag(@NotNull XmlTag tag, @NotNull ImmutableList.Builder<NlComponent> builder) {
    if (children == null && myTag == tag) {
      builder.add(this);
      return;
    }

    for (NlComponent child : getChildren()) {
      child.findViewsByTag(tag, builder);
    }

    if (myTag == tag) {
      builder.add(this);
    }
  }

  @NotNull
  public ImmutableList<NlComponent> findViewsByTag(@NotNull XmlTag tag) {
    ImmutableList.Builder<NlComponent> builder = ImmutableList.builder();
    findViewsByTag(tag, builder);
    return builder.build();
  }

  public boolean isRoot() {
    return !(myTag.getParent() instanceof XmlTag);
  }

  public NlComponent getRoot() {
    NlComponent component = this;
    while (component != null && !component.isRoot()) {
      component = component.getParent();
    }
    return component;
  }

  /**
   * Returns the ID of this component
   */
  @Nullable
  public String getId() {
    String id = myCurrentTransaction != null ? myCurrentTransaction.getAndroidAttribute(ATTR_ID) : resolveAttribute(ANDROID_URI, ATTR_ID);

    return stripId(id);
  }

  @Nullable
  public static String stripId(@Nullable String id) {
    if (id != null) {
      if (id.startsWith(NEW_ID_PREFIX)) {
        return id.substring(NEW_ID_PREFIX.length());
      }
      else if (id.startsWith(ID_PREFIX)) {
        return id.substring(ID_PREFIX.length());
      }
    }
    return null;
  }

  @Nullable
  public NlComponent getParent() {
    return myParent;
  }

  private void setParent(@Nullable NlComponent parent) {
    myParent = parent;
  }

  @NotNull
  public String getTagName() {
    return myTagName;
  }

  @Override
  public String toString() {
    if (this.getMixin() != null) {
      return getMixin().toString();
    }
    return String.format("<%s>", myTagName);
  }

  /**
   * Convenience wrapper for now; this should be replaced with property lookup
   */
  @Override
  public void setAttribute(@Nullable String namespace, @NotNull String attribute, @Nullable String value) {
    if (!myTag.isValid()) {
      // This could happen when trying to set an attribute in a component that has been already deleted
      return;
    }

    String prefix = null;
    if (namespace != null && !ANDROID_URI.equals(namespace)) {
      prefix = AndroidResourceUtil.ensureNamespaceImported((XmlFile)myTag.getContainingFile(), namespace, null);
    }
    String previous = getAttribute(namespace, attribute);
    if (Objects.equals(previous, value)) {
      return;
    }
    // Handle validity
    myTag.setAttribute(attribute, namespace, value);
    if (mySnapshot != null) {
      mySnapshot.setAttribute(attribute, namespace, prefix, value);
    }
  }

  /**
   * Starts an {@link AttributesTransaction} or returns the current open one.
   */
  @NotNull
  public AttributesTransaction startAttributeTransaction() {
    if (myCurrentTransaction == null) {
      myCurrentTransaction = new AttributesTransaction(this);
    }

    return myCurrentTransaction;
  }

  /**
   * Returns the latest attribute value (either live -- not committed -- or from xml)
   *
   * @param namespace
   * @param attribute
   * @return
   */
  @Nullable
  public String getLiveAttribute(@Nullable String namespace, @NotNull String attribute) {
    if (myCurrentTransaction != null) {
      return myCurrentTransaction.getAttribute(namespace, attribute);
    }
    return getAttribute(namespace, attribute);
  }

  @Override
  @Nullable
  public String getAttribute(@Nullable String namespace, @NotNull String attribute) {
    if (mySnapshot != null) {
      return mySnapshot.getAttribute(attribute, namespace);
    }
    else if (AndroidPsiUtils.isValid(myTag)) {
      return AndroidPsiUtils.getAttributeSafely(myTag, namespace, attribute);
    }
    else {
      // Newly created components for example
      return null;
    }
  }

  @Nullable
  public String resolveAttribute(@NotNull String namespace, @NotNull String attribute) {
    String value = getAttribute(namespace, attribute);
    if (value != null) {
      return value;
    }
    if (getMixin() != null) {
      return getMixin().getAttribute(namespace, attribute);
    }
    return null;
  }

  @NotNull
  public List<AttributeSnapshot> getAttributes() {
    if (mySnapshot != null) {
      return mySnapshot.attributes;
    }

    if (myTag.isValid()) {
      Application application = ApplicationManager.getApplication();

      if (!application.isReadAccessAllowed()) {
        return application.runReadAction((Computable<List<AttributeSnapshot>>)() -> AttributeSnapshot.createAttributesForTag(myTag));
      }
      return AttributeSnapshot.createAttributesForTag(myTag);
    }

    return Collections.emptyList();
  }

  public String ensureNamespace(@NotNull String prefix, @NotNull String namespace) {
    return AndroidResourceUtil.ensureNamespaceImported((XmlFile)myTag.getContainingFile(), namespace, prefix);
  }

  public boolean isShowing() {
    return mySnapshot != null;
  }

  /**
   * Utility function to extract the id
   *
   * @param str the string to extract the id from
   * @return the string id
   */
  @Nullable
  public static String extractId(@Nullable String str) {
    if (str == null) {
      return null;
    }
    int index = str.lastIndexOf("@id/");
    if (index != -1) {
      return str.substring(index + 4);
    }

    index = str.lastIndexOf("@+id/");

    if (index != -1) {
      return str.substring(index + 5);
    }
    return null;
  }

  /**
   * Remove attributes that are not valid anymore for the current tag
   */
  public void removeObsoleteAttributes() {
    Set<QualifiedName> obsoleteAttributes = AttributesHelperKt.getObsoleteAttributes(this);
    AttributesTransaction transaction = startAttributeTransaction();
    obsoleteAttributes.forEach(
      qualifiedName -> transaction.removeAttribute(qualifiedName.getNamespace(), qualifiedName.getName()));
    transaction.commit();
  }

  /**
   * A cache for use by system to reduce recalculating information
   * The cache may be destroyed at any time as the system rebuilds the components
   *
   * @param key
   * @param value
   */
  public final void putClientProperty(Object key, Object value) {
    myClientProperties.put(key, value);
  }

  /**
   * A cache for use by system to reduce recalculating information
   * The cache may be destroyed at any time as the system rebuilds the components
   *
   * @param key
   * @return
   */
  public final Object getClientProperty(Object key) {
    return myClientProperties.get(key);
  }

  /**
   * Removes an element from the cache
   * A cache for use by system to reduce recalculating information
   * The cache may be destroyed at any time as the system rebuilds the components
   *
   * @param key
   * @return
   */
  public final Object removeClientProperty(Object key) {
    return myClientProperties.remove(key);
  }

  /**
   * You can add listeners to track interactive updates
   * Listeners should look at the liveUpdates for changes
   *
   * @param listener
   */
  public void addLiveChangeListener(ChangeListener listener) {
    myListeners.add(listener);
  }

  /**
   * remove a listener you have already added
   *
   * @param listener
   */
  public void removeLiveChangeListener(ChangeListener listener) {
    myListeners.remove(listener);
  }

  /**
   * call to notify listeners you have made a "live" change
   */
  public void fireLiveChangeEvent() {
    myListeners.forEach(listener -> listener.stateChanged(myChangeEvent));
  }

  /**
   * Assign a new unique and valid id to this component. The id will be based on the tag name, and will not be de-duped against any
   * existing pending ids.
   *
   * @return The new id.
   */
  @NotNull
  public String assignId() {
    return assignId(getTagName());
  }

  /**
   * Returns the ID, but also assigns a default id if the component does not already have an id (even if the component does
   * not need one according to [.needsDefaultId]
   */
  public String ensureId() {
    if (getId() != null) {
      return getId();
    }
    return assignId();
  }

  /**
   * Assign a new unique and valid id to this component. The id will not be du-duped against any existing pending ids.
   *
   * @param baseName The base (prefix) for the new id.
   * @return The new id.
   */
  @NotNull
  public String assignId(@NotNull String baseName) {
    return assignId(baseName, getModel().getIds());
  }

  /**
   * Assign a new unique and valid id to this component. The id will be based on the tag name.
   *
   * @param ids A collection of existing pending ids, so the newly-created id doesn't clash with existing pending ones.
   * @return The new id.
   */
  @NotNull
  public String assignId(@NotNull Set<String> ids) {
    return assignId(getTagName(), ids);
  }

  /**
   * Assign a new unique and valid id to this component.
   *
   * @param baseName The base (prefix) for the new id.
   * @param ids      A collection of existing pending ids, so the newly-created id doesn't clash with existing pending ones.
   * @return The new id.
   */
  @NotNull
  public String assignId(@NotNull String baseName, @NotNull Set<String> ids) {
    String newId = generateId(baseName, ids, ResourceFolderType.LAYOUT, getModel().getModule());
    // If the component has an open transaction, assign the id in that transaction
    NlAttributesHolder attributes = myCurrentTransaction == null ? this : myCurrentTransaction;
    attributes.setAttribute(ANDROID_URI, ATTR_ID, NEW_ID_PREFIX + newId);

    // TODO clear the pending ids
    getModel().getPendingIds().add(newId);
    return newId;
  }

  @NotNull
  public static String generateId(@NotNull String baseName, @NotNull Set<String> ids, ResourceFolderType type, Module module) {
    String idValue = StringUtil.decapitalize(baseName.substring(baseName.lastIndexOf('.') + 1));

    Project project = module.getProject();
    idValue = ResourceHelper.prependResourcePrefix(module, idValue, type);

    String nextIdValue = idValue;
    int index = 0;

    // Ensure that we don't create something like "switch" as an id, which won't compile when used
    // in the R class
    NamesValidator validator = LanguageNamesValidation.INSTANCE.forLanguage(JavaLanguage.INSTANCE);

    while (ids.contains(nextIdValue) || validator != null && validator.isKeyword(nextIdValue, project)) {
      index++;
      if (index == 1 && (validator == null || !validator.isKeyword(nextIdValue, project))) {
        nextIdValue = idValue;
      }
      else {
        nextIdValue = idValue + index;
      }
    }

    return idValue + (index == 0 ? "" : index);
  }

  @Nullable
  public String getTooltipText() {
    XmlModelComponentMixin mixin = getMixin();
    if (mixin != null) {
      return mixin.getTooltipText();
    }
    return null;
  }

  public abstract static class XmlModelComponentMixin {
    private final NlComponent myComponent;

    public XmlModelComponentMixin(@NotNull NlComponent component) {
      myComponent = component;
    }

    @NotNull
    protected NlComponent getComponent() {
      return myComponent;
    }

    @Nullable
    public String getAttribute(@NotNull String namespace, @NotNull String attribute) {
      return null;
    }

    @Override
    public String toString() {
      return String.format("<%s>", myComponent.getTagName());
    }

    @Nullable
    public String getTooltipText() {
      return null;
    }
  }
}
