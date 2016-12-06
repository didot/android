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
package com.android.tools.idea.uibuilder.property;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.templates.TemplateUtils;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.property.ptable.PTableGroupItem;
import com.android.tools.idea.uibuilder.property.ptable.PTableItem;
import com.android.tools.idea.uibuilder.property.ptable.StarState;
import com.android.tools.idea.uibuilder.property.renderer.NlPropertyRenderers;
import com.android.util.PropertiesMap;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.NamespaceAwareXmlAttributeDescriptor;
import com.intellij.xml.XmlAttributeDescriptor;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.TableCellRenderer;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class NlPropertyItem extends PTableItem implements NlProperty {
  // Certain attributes are special and do not have an attribute definition from attrs.xml
  private static final Set<String> ATTRS_WITHOUT_DEFINITIONS = ImmutableSet.of(
    SdkConstants.ATTR_STYLE, // <View style="..." />
    SdkConstants.ATTR_CLASS, // class is suggested as an attribute for a <fragment>!
    SdkConstants.ATTR_LAYOUT // <include layout="..." />
  );

  @NotNull
  protected final List<NlComponent> myComponents;
  @Nullable
  protected final AttributeDefinition myDefinition;
  @NotNull
  private final String myName;
  @Nullable
  private final String myNamespace;
  @Nullable
  private PropertiesMap.Property myDefaultValue;
  @NotNull
  private StarState myStarState;

  public static NlPropertyItem create(@NotNull List<NlComponent> components,
                                      @NotNull XmlAttributeDescriptor descriptor,
                                      @Nullable String namespace,
                                      @Nullable AttributeDefinition attributeDefinition) {
    if (attributeDefinition != null && attributeDefinition.getFormats().contains(AttributeFormat.Flag)) {
      return new NlFlagPropertyItem(components, descriptor, namespace, attributeDefinition);
    }
    else if (descriptor.getName().equals(SdkConstants.ATTR_ID)) {
      return new NlIdPropertyItem(components, descriptor, attributeDefinition);
    }
    else {
      return new NlPropertyItem(components, descriptor, namespace, attributeDefinition);
    }
  }

  protected NlPropertyItem(@NotNull List<NlComponent> components,
                           @NotNull XmlAttributeDescriptor descriptor,
                           @Nullable String namespace,
                           @Nullable AttributeDefinition attributeDefinition) {
    assert !components.isEmpty();
    if (namespace == null) {
      namespace = descriptor instanceof NamespaceAwareXmlAttributeDescriptor ?
                  ((NamespaceAwareXmlAttributeDescriptor)descriptor).getNamespace(components.get(0).getTag()) : null;
    }
    if (attributeDefinition == null &&
        !ATTRS_WITHOUT_DEFINITIONS.contains(descriptor.getName()) &&
        !SdkConstants.TOOLS_URI.equals(namespace)) {
      throw new IllegalArgumentException("Missing attribute definition for " + descriptor.getName());
    }

    // NOTE: we do not save any PSI data structures as fields as they could go out of date as the user edits the file.
    // Instead, we have a reference to the component, and query whatever information we need from the component, and expect
    // that the component can provide that information by having a shadow copy that is consistent with the rendering
    myComponents = components;
    myName = descriptor.getName();
    myNamespace = namespace;
    myDefinition = attributeDefinition;
    myStarState = StarState.STAR_ABLE;
  }

  public NlPropertyItem(@NotNull List<NlComponent> components,
                        @NotNull String namespace,
                        @Nullable AttributeDefinition attributeDefinition) {
    assert !components.isEmpty();
    assert attributeDefinition != null;
    myComponents = components;
    myName = attributeDefinition.getName();
    myNamespace = namespace;
    myDefinition = attributeDefinition;
    myStarState = StarState.STAR_ABLE;
  }

  protected NlPropertyItem(@NotNull NlPropertyItem property, @NotNull String namespace) {
    assert !property.myComponents.isEmpty();
    myComponents = property.myComponents;
    myName = property.myName;
    myNamespace = namespace;
    myDefinition = property.myDefinition;
    myStarState = StarState.STAR_ABLE;
    if (property.getParent() != null) {
      PTableGroupItem group = (PTableGroupItem)property.getParent();
      group.addChild(this, property);
    }
  }

  public boolean sameDefinition(@Nullable NlPropertyItem other) {
    return other != null &&
           Objects.equal(myName, other.myName) &&
           Objects.equal(myNamespace, other.myNamespace) &&
           myDefinition == other.myDefinition;
  }

  @Override
  @NotNull
  public List<NlComponent> getComponents() {
    return myComponents;
  }

  @Override
  @NotNull
  public StarState getStarState() {
    return myStarState;
  }

  public void setInitialStarred() {
    myStarState = StarState.STARRED;
  }

  @Override
  public void setStarState(@NotNull StarState starState) {
    myStarState = starState;
    NlProperties.saveStarState(myNamespace, myName, starState == StarState.STARRED);
    updateAllProperties();
  }

  private void updateAllProperties() {
    getModel().getSelectionModel().updateListeners();
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @Nullable
  public String getNamespace() {
    return myNamespace;
  }

  public void setDefaultValue(@Nullable PropertiesMap.Property defaultValue) {
    myDefaultValue = defaultValue;
  }

  @Override
  @Nullable
  public String getValue() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    String prev = null;
    for (NlComponent component : myComponents) {
      String value = component.getAttribute(myNamespace, myName);
      if (value == null) {
        return null;
      }
      if (prev == null) {
        prev = value;
      }
      else if (!value.equals(prev)) {
        return null;
      }
    }
    return prev;
  }

  @Override
  @Nullable
  public String getResolvedValue() {
    return resolveValue(getValue());
  }

  @Override
  public boolean isDefaultValue(@Nullable String value) {
    if (value == null) {
      return true;
    }
    if (myDefaultValue == null) {
      return false;
    }
    return value.equals(myDefaultValue.resource);
  }

  @Override
  @Nullable
  public String resolveValue(@Nullable String value) {
    if (myDefaultValue != null && isDefaultValue(value)) {
      if (myDefaultValue.value == null) {
        myDefaultValue = new PropertiesMap.Property(myDefaultValue.resource, resolveValueUsingResolver(myDefaultValue.resource));
      }
      return myDefaultValue.value;
    }
    return value != null ? resolveValueUsingResolver(value) : null;
  }

  public void delete() {
    PTableGroupItem group = (PTableGroupItem)getParent();
    if (group != null) {
      group.deleteChild(this);
    }
  }

  @NotNull
  private String resolveValueUsingResolver(@NotNull String value) {
    if (value.startsWith("?") || value.startsWith("@") && !isId(value)) {
      ResourceResolver resolver = getResolver();
      if (resolver != null) {
        ResourceValue resource = resolver.findResValue(value, false);
        if (resource == null) {
          resource = resolver.findResValue(value, true);
        }
        if (resource != null) {
          if (resource.getValue() != null) {
            value = resource.getValue();
            if (resource.isFramework()) {
              value = addAndroidPrefix(value);
            }
          }
          ResourceValue resolved = resolver.resolveResValue(resource);
          if (resolved != null && resolved.getValue() != null) {
            value = resolved.getValue();
            if (resource.isFramework()) {
              value = addAndroidPrefix(value);
            }
          }
        }
      }
    }
    return value;
  }

  @NotNull
  private static String addAndroidPrefix(@NotNull String value) {
    if (value.startsWith("@") && !value.startsWith(SdkConstants.ANDROID_PREFIX)) {
      return SdkConstants.ANDROID_PREFIX + value.substring(1);
    }
    return value;
  }

  private static boolean isId(@NotNull String value) {
    return value.startsWith(SdkConstants.ID_PREFIX) ||
           value.startsWith(SdkConstants.NEW_ID_PREFIX) ||
           value.startsWith(SdkConstants.ANDROID_ID_PREFIX) ||
           value.startsWith(SdkConstants.ANDROID_NEW_ID_PREFIX);
  }

  @NotNull
  @Override
  public NlProperty getChildProperty(@NotNull String itemName) {
    throw new UnsupportedOperationException(itemName);
  }

  @NotNull
  @Override
  public NlPropertyItem getDesignTimeProperty() {
    if (SdkConstants.TOOLS_URI.equals(myNamespace)) {
      return this;
    }
    return new NlPropertyItem(this, SdkConstants.TOOLS_URI);
  }

  @Override
  @NotNull
  public NlModel getModel() {
    return myComponents.get(0).getModel();
  }

  @Override
  @Nullable
  public XmlTag getTag() {
    return myComponents.size() == 1 ? myComponents.get(0).getTag() : null;
  }

  @Override
  @Nullable
  public String getTagName() {
    String tagName = null;
    for (NlComponent component : myComponents) {
      if (tagName == null) {
        tagName = component.getTagName();
      }
      else if (!tagName.equals(component.getTagName())) {
        return null;
      }
    }
    return tagName;
  }

  @Override
  @Nullable
  public ResourceResolver getResolver() {
    Configuration configuration = getModel().getConfiguration();

    // TODO: what happens if this is configuration dependent? (in theory, those should be edited in the theme editor)
    return configuration.getResourceResolver();
  }

  @Override
  public void setValue(@Nullable Object value) {
    // TODO: Consider making getApplication() a field to avoid statics
    assert ApplicationManager.getApplication().isDispatchThread();
    if (getModel().getProject().isDisposed()) {
      return;
    }
    String strValue = value == null ? null : value.toString();
    if (StringUtil.isEmpty(strValue) || isDefaultValue(strValue)) {
      strValue = null;
    }
    final String attrValue = strValue;
    NlComponent first = myComponents.get(0);
    String componentName = myComponents.size() == 1 ? first.getTagName() : "Multiple";
    String msg = String.format("Set %1$s.%2$s to %3$s", componentName, myName, attrValue);
    new WriteCommandAction.Simple(getModel().getProject(), msg, first.getTag().getContainingFile()) {
      @Override
      protected void run() throws Throwable {
        for (NlComponent component : myComponents) {
          String v = StringUtil.isEmpty(attrValue) ? null : attrValue;
          component.setAttribute(myNamespace, myName, v);
          TemplateUtils.reformatAndRearrange(getProject(), component.getTag());
        }
      }
    }.execute();

    if (SdkConstants.VIEW_MERGE.equals(componentName) &&
        SdkConstants.TOOLS_URI.equals(getNamespace()) &&
        SdkConstants.ATTR_PARENT_TAG.equals(getName())) {
      // Special case: When the tools:parentTag is updated on a <merge> tag, the set of attributes for
      // the <merge> tag may change e.g. if the value is set to "LinearLayout" the <merge> tag will
      // then have all attributes from a <LinearLayout>. Force an update of all properties:
      updateAllProperties();
    }
  }

  @NotNull
  public List<String> getParentStylables() {
    return myDefinition == null ? Collections.emptyList() : myDefinition.getParentStyleables();
  }

  @Override
  @Nullable
  public AttributeDefinition getDefinition() {
    return myDefinition;
  }

  @NotNull
  @Override
  public TableCellRenderer getCellRenderer() {
    return NlPropertyRenderers.get(this);
  }

  @Override
  public boolean isEditable(int col) {
    return true;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
      .add("name", myName)
      .add("namespace", namespaceToPrefix(myNamespace))
      .toString();
  }

  @Override
  @NotNull
  public String getTooltipText() {
    StringBuilder sb = new StringBuilder(100);
    sb.append(namespaceToPrefix(myNamespace));
    sb.append(myName);
    if (myDefinition != null) {
      String value = myDefinition.getDocValue(null);

      if (value != null) {
        sb.append(": ");
        sb.append(value);
      }
    }
    return sb.toString();
  }

  @NotNull
  private static String namespaceToPrefix(@Nullable String namespace) {
    if (namespace != null && SdkConstants.NS_RESOURCES.equalsIgnoreCase(namespace)) {
      return SdkConstants.ANDROID_PREFIX;
    }
    else {
      return "";
    }
  }
}
