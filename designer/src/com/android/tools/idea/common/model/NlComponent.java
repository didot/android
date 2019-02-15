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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ID_PREFIX;
import static com.android.SdkConstants.NEW_ID_PREFIX;
import static com.android.SdkConstants.XMLNS;
import static com.android.SdkConstants.XMLNS_PREFIX;

import com.android.resources.ResourceFolderType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.rendering.parsers.AttributeSnapshot;
import com.android.tools.idea.rendering.parsers.TagSnapshot;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.uibuilder.model.AttributesHelperKt;
import com.android.tools.idea.uibuilder.model.QualifiedName;
import com.android.tools.idea.util.ListenerCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Represents a component editable in the UI builder. A component has properties,
 * if visual it has bounds, etc.
 */
public class NlComponent implements NlAttributesHolder {

  @Nullable private XmlModelComponentMixin myMixin;

  private final List<NlComponent> children = Lists.newArrayList();
  @Nullable private List<NlComponent> cachedChildrenCopy = null;
  private NlComponent myParent;
  @NotNull private final NlModel myModel;
  @Nullable private TagSnapshot mySnapshot;

  // Backend allows NlComponent to be independent of any specific library or file types.
  @NotNull private NlComponentBackend myBackend;

  private final HashMap<Object, Object> myClientProperties = new HashMap<>();
  private final ListenerCollection<ChangeListener> myListeners = ListenerCollection.createWithDirectExecutor();
  private final ChangeEvent myChangeEvent = new ChangeEvent(this);
  private NlComponentDelegate myDelegate;

  /**
   * Current open attributes transaction or null if none is open
   */
  @Nullable AttributesTransaction myCurrentTransaction;

  public NlComponent(@NotNull NlModel model, @NotNull XmlTag tag) {
    myModel = model;
    myBackend = new NlComponentBackendXml(model, tag);
  }

  @TestOnly
  public NlComponent(@NotNull NlModel model, @NotNull XmlTag tag, @NotNull SmartPsiElementPointer<XmlTag> tagPointer) {
    myModel = model;
    myBackend = new NlComponentBackendXml(model, tag, tagPointer);
  }

  @Nullable
  public NlComponentDelegate getDelegate() {
    return myDelegate;
  }

  public void setDelegate(@Nullable NlComponentDelegate delegate) {
    myDelegate = delegate;
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
  @Deprecated
  public XmlTag getTag() {
    return myBackend.getTagDeprecated();
  }

  @NotNull
  @Deprecated
  public SmartPsiElementPointer<XmlTag> getTagPointer() {
    return myBackend.getTagPointer();
  }

  @NotNull
  public NlModel getModel() {
    return myModel;
  }

  public void setTag(@NotNull XmlTag tag) {
    myBackend.setTagElement(tag);
  }

  @Nullable
  @Deprecated
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
    synchronized (children) {
      cachedChildrenCopy = null;
      int index = before != null ? children.indexOf(before) : -1;
      if (index != -1) {
        children.add(index, component);
      }
      else {
        children.add(component);
      }
    }
    component.setParent(this);
  }

  public void removeChild(@NotNull NlComponent component) {
    if (component == this) {
      throw new IllegalArgumentException();
    }
    if (myDelegate != null) {
      myDelegate.willRemoveChild(component);
    }
    synchronized (children) {
      cachedChildrenCopy = null;
      children.remove(component);
    }
    component.setParent(null);
  }

  public void setChildren(@Nullable List<NlComponent> components) {
    synchronized (children) {
      cachedChildrenCopy = null;
      children.clear();
      if (components == null) {
        return;
      }
      children.addAll(components);
    }
    for (NlComponent component : components) {
      if (component == this) {
        throw new IllegalArgumentException();
      }
      component.setParent(this);
    }
  }

  @NotNull
  public List<NlComponent> getChildren() {
    List<NlComponent> childrenCopy = cachedChildrenCopy;
    if (childrenCopy == null) {
      synchronized (children) {
        childrenCopy = ImmutableList.copyOf(children);
        cachedChildrenCopy = childrenCopy;
      }
    }
    return childrenCopy;
  }

  public int getChildCount() {
    return getChildren().size();
  }

  @Nullable
  public NlComponent getChild(int index) {
    List<NlComponent> children = getChildren();
    return index >= 0 && index < children.size() ? children.get(index) : null;
  }

  @NotNull
  public Stream<NlComponent> flatten() {
    return Stream.concat(
      Stream.of(this),
      getChildren().stream().flatMap(NlComponent::flatten));
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
    if (getTag() == tag) {
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
    for (NlComponent child : getChildren()) {
      child.findViewsByTag(tag, builder);
    }

    if (getTag() == tag) {
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
    return !(getTag().getParent() instanceof XmlTag);
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

  public void clearTransaction() {
    if (myCurrentTransaction != null) {
      myCurrentTransaction.finishTransaction();
    }
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
    return myBackend.getTagName();
  }

  @NotNull
  public NlComponentBackend getBackend() {
    return myBackend;
  }

  @Override
  public String toString() {
    if (this.getMixin() != null) {
      return getMixin().toString();
    }
    return String.format("<%s>", myBackend.getTagName());
  }

  /**
   * Convenience wrapper for now; this should be replaced with property lookup
   */
  @Override
  public void setAttribute(@Nullable String namespace, @NotNull String attribute, @Nullable String value) {
    if (myDelegate != null && myDelegate.handlesAttribute(this, namespace, attribute)) {
      myDelegate.setAttribute(this, namespace, attribute, value);
      return;
    }
    XmlTag tag = getTag();
    if (!tag.isValid()) {
      // This could happen when trying to set an attribute in a component that has been already deleted
      return;
    }

    String prefix = null;
    if (namespace != null && !ANDROID_URI.equals(namespace)) {
      prefix = AndroidResourceUtil.ensureNamespaceImported((XmlFile)tag.getContainingFile(), namespace, null);
    }
    String previous = getAttribute(namespace, attribute);
    if (Objects.equals(previous, value)) {
      return;
    }
    // Handle validity
    myBackend.setAttribute(attribute, namespace, value);
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
    if (myDelegate != null && myDelegate.handlesAttribute(this, namespace, attribute)) {
      return myDelegate.getAttribute(this, namespace, attribute);
    }
    return getAttribute(namespace, attribute);
  }

  @Override
  @Nullable
  public String getAttribute(@Nullable String namespace, @NotNull String attribute) {
    if (myDelegate != null && myDelegate.handlesAttribute(this, namespace, attribute)) {
      return myDelegate.getAttribute(this, namespace, attribute);
    }
    return getAttributeImpl(namespace, attribute);
  }

  @Nullable
  public String getAttributeImpl(@Nullable String namespace, @NotNull String attribute) {
    if (mySnapshot != null) {
      return mySnapshot.getAttribute(attribute, namespace);
    }

    return myBackend.getAttribute(attribute, namespace);
  }

  @Nullable
  public String resolveAttribute(@Nullable String namespace, @NotNull String attribute) {
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
    if (myDelegate != null && myDelegate.handlesAttributes(this)) {
      List<AttributeSnapshot> attributes = myDelegate.getAttributes(this);
      if (attributes != null) {
        return attributes;
      } else {
        return Collections.emptyList();
      }
    }
    return getAttributesImpl();
  }

  @NotNull
  public List<AttributeSnapshot> getAttributesImpl() {
    if (mySnapshot != null) {
      return mySnapshot.attributes;
    }

    XmlTag tag = getTag();
    if (tag.isValid()) {
      Application application = ApplicationManager.getApplication();

      if (!application.isReadAccessAllowed()) {
        return application.runReadAction((Computable<List<AttributeSnapshot>>)() -> AttributeSnapshot.createAttributesForTag(tag));
      }
      return AttributeSnapshot.createAttributesForTag(tag);
    }

    return Collections.emptyList();
  }

  /**
   * Make sure there is a namespace declaration for the specified namespace.
   *
   * @param suggestedPrefix use this prefix if a namespace declaration is to be added
   * @param namespace the namespace a declaration is needed for
   * @return the prefix for the namespace. This will be the existing namespace prefix unless
   *         a new namespace declaration was added in which case it will be suggestedPrefix.
   *         If the corresponding XmlTag doesn't exist a null is returned.
   */
  @Nullable
  public String ensureNamespace(@NotNull String suggestedPrefix, @NotNull String namespace) {
    XmlTag tag = getBackend().getTag();
    if (tag == null) {
      return null;
    }
    return AndroidResourceUtil.ensureNamespaceImported((XmlFile)tag.getContainingFile(), namespace, suggestedPrefix);
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
  private String assignId(@NotNull Set<String> ids) {
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
  private String assignId(@NotNull String baseName, @NotNull Set<String> ids) {
    String newId = generateId(baseName, ids, ResourceFolderType.LAYOUT, getModel().getModule());
    // If the component has an open transaction, assign the id in that transaction
    NlAttributesHolder attributes = myCurrentTransaction == null ? this : myCurrentTransaction;
    attributes.setAttribute(ANDROID_URI, ATTR_ID, NEW_ID_PREFIX + newId);

    // TODO clear the pending ids
    getModel().getPendingIds().add(newId);
    return newId;
  }

  public void incrementId(@NotNull Set<String> ids) {
    String id = getId();
    if (id == null || id.isEmpty()) {
      ids.add(assignId(ids));
    }
    else {
      // Regex to get the base name of a component id, where the basename of
      // "component123" is "component"
      String baseName = id.replaceAll("[0-9]*$", "");
      if (baseName != null && !baseName.isEmpty()) {
        ids.add(assignId(baseName, ids));
      }
    }
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

  public boolean canAddTo(NlComponent receiver) {
    XmlModelComponentMixin mixin = getMixin();
    if (mixin != null) {
      return mixin.canAddTo(receiver);
    }
    return true;
  }

  public void moveTo(@NotNull NlComponent receiver, @Nullable NlComponent before, @NotNull InsertType type, @NotNull Set<String> ids,
                     @Nullable DesignSurface surface) {
    XmlModelComponentMixin mixin = getMixin();
    if (mixin != null) {
      mixin.beforeMove(type, receiver, ids);
    }
    NlComponent oldParent = getParent();
    addTags(receiver, before, type);
    if (mixin != null) {
      mixin.afterMove(type, oldParent, receiver, surface);
    }
  }

  public void addTags(@NotNull NlComponent receiver, @Nullable NlComponent before, @NotNull InsertType type) {
    NlComponent parent = getParent();
    if (parent != null) {
      parent.removeChild(this);
    }
    XmlTag tag = getBackend().getTag();
    XmlTag receiverTag = receiver.getBackend().getTag();
    XmlTag beforeTag = before != null ? before.getBackend().getTag() : null;
    if (receiverTag == null || tag == null) {
      return; // Abort: the XML has been edited before this change was made, and the tags are no longer available.
    }
    if (receiverTag == tag) {
      return; // Abort: cannot add XmlTag to itself
    }
    receiver.addChild(this, before);
    transferNamespaces(receiver);
    if (beforeTag != null) {
      setTag((XmlTag)receiverTag.addBefore(tag, beforeTag));
    }
    else {
      setTag(receiverTag.addSubTag(tag, false));
    }
    if (type.isMove()) {
      tag.delete();
    }
  }

  public void postCreateFromTransferrable(@NotNull DnDTransferComponent dndComponent) {
    XmlModelComponentMixin mixin = getMixin();
    if (mixin != null) {
      mixin.postCreateFromTransferrable(dndComponent);
    }
  }

  public boolean postCreate(@Nullable DesignSurface surface, @NotNull InsertType insertType) {
    XmlModelComponentMixin mixin = getMixin();
    if (mixin != null) {
      return mixin.postCreate(surface, insertType);
    }
    return true;
  }

  /**
   * Given a tag on the current component which is not yet part of the current
   * document and a receiver component where the tag is going to be added:
   * <ul>
   *   <li>look up any namespaces defined on the receiver or its parents</li>
   *   <li>look up any namespaces defined on the current new tag</li>
   * </ul>
   * and transfer all those namespace declarations to the current document
   */
  private void transferNamespaces(@NotNull NlComponent receiver) {
    XmlTag rootTag = getDocumentRoot();
    XmlTag tag = receiver.getTag();
    while (tag != null && tag != rootTag) {
      if (!tag.getLocalNamespaceDeclarations().isEmpty()) {
        // This is done to cleanup after a manual change of the Xml file.
        // See b/78318923
        receiver.transferLocalNamespaces();
      }

      receiver = receiver.getParent();
      tag = receiver != null ? receiver.getTag() : null;
    }
    transferLocalNamespaces();
  }

  /**
   * Given a tag on the current component:
   * <ul>
   *   <li>transfer any namespaces to the rootTag of the current document</li>
   *   <li>update all attribute prefixes for namespaces to match those in the rootTag</li>
   * </ul>
   */
  private void transferLocalNamespaces() {
    XmlTag rootTag = getDocumentRoot();
    if (rootTag == this) {
      return;
    }
    // Transfer namespace attributes to the root tag
    Map<String, String> prefixToNamespace = rootTag.getLocalNamespaceDeclarations();
    Map<String, String> namespaceToPrefix = Maps.newHashMap();
    for (Map.Entry<String, String> entry : prefixToNamespace.entrySet()) {
      namespaceToPrefix.put(entry.getValue(), entry.getKey());
    }
    Map<String, String> oldPrefixToPrefix = Maps.newHashMap();

    for (Map.Entry<String, String> entry : getTag().getLocalNamespaceDeclarations().entrySet()) {
      String namespace = entry.getValue();
      String prefix = entry.getKey();
      String currentPrefix = namespaceToPrefix.get(namespace);
      if (currentPrefix == null) {
        // The namespace isn't used in the document. Import it.
        XmlFile file = getModel().getFile();
        String newPrefix = AndroidResourceUtil.ensureNamespaceImported(file, namespace, prefix);
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
      updatePrefixes(getTag(), oldPrefixToPrefix);
    }

    removeNamespaceAttributes();
  }

  @NotNull
  private XmlTag getDocumentRoot() {
    XmlFile file = getModel().getFile();
    XmlDocument xmlDocument = file.getDocument();
    assert xmlDocument != null;
    XmlTag rootTag = xmlDocument.getRootTag();
    assert rootTag != null;
    return rootTag;
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

  private void removeNamespaceAttributes() {
    for (XmlAttribute attribute : getTag().getAttributes()) {
      if (attribute.getName().startsWith(XMLNS_PREFIX)) {
        attribute.delete();
      }
    }
  }

  public Set<String> getDependencies() {
    XmlModelComponentMixin mixin = getMixin();
    if (mixin != null) {
      return mixin.getDependencies();
    }
    return ImmutableSet.of();
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
    public String getAttribute(@Nullable String namespace, @NotNull String attribute) {
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

    public boolean canAddTo(@NotNull NlComponent receiver) {
      return true;
    }

    public Set<String> getDependencies() {
      return ImmutableSet.of();
    }

    public void beforeMove(@NotNull InsertType insertType, @NotNull NlComponent receiver, @NotNull Set<String> ids) {}

    public void afterMove(@NotNull InsertType insertType,
                          @Nullable NlComponent previousParent,
                          @NotNull NlComponent receiver,
                          @Nullable DesignSurface surface) {}

    public boolean postCreate(@Nullable DesignSurface surface, @NotNull InsertType insertType) {
      return true;
    }

    public void postCreateFromTransferrable(DnDTransferComponent dndComponent) {}

    public abstract boolean maybeHandleDeletion(@NotNull Collection<NlComponent> children);
  }
}
