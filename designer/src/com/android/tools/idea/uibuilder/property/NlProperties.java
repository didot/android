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

import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.property.PropertiesManager;
import com.android.tools.idea.lint.LintIdeClient;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.lint.checks.ApiLookup;
import com.android.utils.Pair;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.XmlName;
import com.intellij.xml.NamespaceAwareXmlAttributeDescriptor;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.android.dom.AndroidDomElementDescriptorProvider;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.attrs.StyleableDefinition;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.*;

public class NlProperties {
  public static final String STARRED_PROP = "ANDROID.STARRED_PROPERTIES";

  private static NlProperties ourInstance = null;
  private final AndroidDomElementDescriptorProvider myDescriptorProvider = new AndroidDomElementDescriptorProvider();

  public static synchronized NlProperties getInstance() {
    if (ourInstance == null) {
      ourInstance = new NlProperties();
    }
    return ourInstance;
  }

  @NotNull
  public Table<String, String, NlPropertyItem> getProperties(@NotNull AndroidFacet facet,
                                                             @Nullable PropertiesManager propertiesManager,
                                                             @NotNull List<NlComponent> components) {
    assert !EventQueue.isDispatchThread() || ApplicationManager.getApplication().isUnitTestMode();

    if (components.isEmpty()) {
      return ImmutableTable.of();
    }

    Project project = facet.getModule().getProject();
    return DumbService.getInstance(project).runReadActionInSmartMode(() -> getPropertiesImpl(facet, propertiesManager, components));
  }

  @NotNull
  private Table<String, String, NlPropertyItem> getPropertiesImpl(@NotNull AndroidFacet facet,
                                                                  @Nullable PropertiesManager propertiesManager,
                                                                  @NotNull List<NlComponent> components) {
    assert !components.isEmpty();
    ModuleResourceManagers resourceManagers = ModuleResourceManagers.getInstance(facet);
    ResourceManager localResourceManager = resourceManagers.getLocalResourceManager();
    ResourceManager frameworkResourceManager = resourceManagers.getFrameworkResourceManager();
    if (frameworkResourceManager == null) {
      Logger.getInstance(NlProperties.class).error("No system resource manager for module: " + facet.getModule().getName());
      return ImmutableTable.of();
    }

    AttributeDefinitions localAttrDefs = localResourceManager.getAttributeDefinitions();
    AttributeDefinitions systemAttrDefs = frameworkResourceManager.getAttributeDefinitions();

    Table<String, String, NlPropertyItem> combinedProperties = null;
    Project project = facet.getModule().getProject();
    ApiLookup apiLookup = LintIdeClient.getApiLookup(project);
    int minApi = AndroidModuleInfo.getInstance(facet).getMinSdkVersion().getFeatureLevel();

    for (NlComponent component : components) {
      XmlTag tag = component.getTagDeprecated();
      if (!tag.isValid()) {
        return ImmutableTable.of();
      }

      XmlElementDescriptor elementDescriptor = myDescriptorProvider.getDescriptor(tag);
      if (elementDescriptor == null) {
        return ImmutableTable.of();
      }

      XmlAttributeDescriptor[] descriptors = elementDescriptor.getAttributesDescriptors(tag);
      Table<String, String, NlPropertyItem> properties = HashBasedTable.create(3, descriptors.length);

      for (XmlAttributeDescriptor desc : descriptors) {
        XmlName name = getXmlName(desc, tag);
        if (ANDROID_URI.equals(name.getNamespaceKey()) && apiLookup != null &&
            apiLookup.getFieldVersion("android/R$attr", name.getLocalName()) > minApi) {
          continue;
        }
        AttributeDefinitions attrDefs = ANDROID_URI.equals(name.getNamespaceKey()) ? systemAttrDefs : localAttrDefs;
        AttributeDefinition attrDef = attrDefs == null ? null : attrDefs.getAttrDefByName(name.getLocalName());
        if (!NlPropertyItem.isDefinitionAcceptable(name, attrDef)) {
          // Ignore attributes we don't have information about.
          // This will ignore special data binding attributes such as:
          //    CheckBox.onCheckedChanged
          //    SearchView.onSearchClick
          //    ZoomControls.onZoomOut
          // among others.
          //
          // TODO: Investigate further:
          // There is code in TagSnapshot.getValue that blocks the value of these attributes.
          // Ignore these attributes for now.
          continue;
        }
        NlPropertyItem property = NlPropertyItem.create(name, attrDef, components, propertiesManager);
        properties.put(StringUtil.notNullize(name.getNamespaceKey()), property.getName(), property);
      }

      PsiElement declaration = elementDescriptor.getDeclaration();
      PsiClass tagClass = (declaration instanceof PsiClass) ? (PsiClass)declaration : null;
      String className = tagClass != null ? tagClass.getQualifiedName() : null;
      if (NlComponentHelperKt.getHasNlComponentInfo(component)) {
        ViewInfo view = NlComponentHelperKt.getViewInfo(component);
        String viewClassName = view != null ? view.getClassName() : null;
        if (localAttrDefs != null && viewClassName != null && className != null && !viewClassName.equals(className)) {
          addAttributesFromInflatedStyleable(properties, localAttrDefs, tagClass, viewClassName, propertiesManager, components);
        }
      }

      // Exceptions:
      switch (tag.getName()) {
        case AUTO_COMPLETE_TEXT_VIEW:
          // An AutoCompleteTextView has a popup that is created at runtime.
          // Properties for this popup can be added to the AutoCompleteTextView tag.
          XmlName popup = new XmlName(ATTR_POPUP_BACKGROUND, ANDROID_URI);
          AttributeDefinition definition = systemAttrDefs != null ? systemAttrDefs.getAttrDefByName(ATTR_POPUP_BACKGROUND) : null;
          if (NlPropertyItem.isDefinitionAcceptable(popup, definition)) {
            properties.put(ANDROID_URI, ATTR_POPUP_BACKGROUND,
                           NlPropertyItem.create(popup, definition, components, propertiesManager));
          }
          break;
      }

      combinedProperties = combine(properties, combinedProperties);
    }

    // The following properties are deprecated in the support library and can be ignored by tools:
    assert combinedProperties != null;
    combinedProperties.remove(AUTO_URI, ATTR_PADDING_START);
    combinedProperties.remove(AUTO_URI, ATTR_PADDING_END);
    combinedProperties.remove(AUTO_URI, ATTR_THEME);

    setUpDesignProperties(combinedProperties);

    initStarState(combinedProperties);

    //noinspection ConstantConditions
    return combinedProperties;
  }

  private static void addAttributesFromInflatedStyleable(@NotNull Table<String, String, NlPropertyItem> properties,
                                                         @NotNull AttributeDefinitions localAttrDefs,
                                                         @NotNull PsiClass xmlClass,
                                                         @NotNull String inflatedClassName,
                                                         @NotNull PropertiesManager propertiesManager,
                                                         @NotNull List<NlComponent> components) {
    PsiManager psiManager = PsiManager.getInstance(xmlClass.getProject());
    PsiClass inflatedClass = ClassUtil.findPsiClass(psiManager, inflatedClassName);
    while (inflatedClass != null && inflatedClass != xmlClass) {
      String styleableName = inflatedClass.getName();
      StyleableDefinition styleable = styleableName != null ? localAttrDefs.getStyleableByName(styleableName) : null;
      if (styleable != null) {
        for (AttributeDefinition attrDef : styleable.getAttributes()) {
          if (properties.contains(ANDROID_URI, attrDef.getName())) {
            // If the corresponding framework attribute is supported, prefer the framework attribute.
            continue;
          }
          XmlName name = getXmlName(attrDef.getName(), AUTO_URI);
          NlPropertyItem property = NlPropertyItem.create(name, attrDef, components, propertiesManager);
          properties.put(StringUtil.notNullize(name.getNamespaceKey()), property.getName(), property);
        }
      }

      inflatedClass = inflatedClass.getSuperClass();
    }
  }

  private static void initStarState(@NotNull Table<String, String, NlPropertyItem> properties) {
    for (String starredProperty : getStarredProperties()) {
      Pair<String, String> property = split(starredProperty);
      NlPropertyItem item = properties.get(property.getFirst(), property.getSecond());
      if (item != null) {
        item.setInitialStarred();
      }
    }
  }

  public static void saveStarState(@Nullable String propertyNamespace,
                                   @NotNull String propertyName,
                                   boolean starred,
                                   @Nullable PropertiesManager propertiesManager) {
    String propertyNameWithPrefix = getPropertyNameWithPrefix(propertyNamespace, propertyName);
    List<String> favorites = new ArrayList<>();
    for (String starredProperty : getStarredProperties()) {
      if (!starredProperty.equals(propertyNameWithPrefix)) {
        favorites.add(starredProperty);
      }
    }
    if (starred) {
      favorites.add(propertyNameWithPrefix);
    }
    PropertiesComponent properties = PropertiesComponent.getInstance();
    properties.setValue(STARRED_PROP, Joiner.on(';').join(favorites));
    String added = starred ? propertyNameWithPrefix : "";
    String removed = !starred ? propertyNameWithPrefix : "";
    if (propertiesManager != null) {
      propertiesManager.logFavoritesChange(added, removed, favorites);
    }
  }

  public static String getStarredPropertiesAsString() {
    String starredProperties = PropertiesComponent.getInstance().getValue(STARRED_PROP);
    if (starredProperties == null) {
      starredProperties = ATTR_VISIBILITY;
    }
    return starredProperties;
  }

  public static Iterable<String> getStarredProperties() {
    return Splitter.on(';').trimResults().omitEmptyStrings().split(getStarredPropertiesAsString());
  }

  @NotNull
  private static String getPropertyNameWithPrefix(@Nullable String namespace, @NotNull String propertyName) {
    if (namespace == null) {
      return propertyName;
    }
    switch (namespace) {
      case TOOLS_URI:
        return TOOLS_NS_NAME_PREFIX + propertyName;
      case ANDROID_URI:
        return propertyName;
      default:
        return PREFIX_APP + propertyName;
    }
  }

  @NotNull
  private static Pair<String, String> split(@NotNull String propertyNameWithPrefix) {
    if (propertyNameWithPrefix.startsWith(TOOLS_NS_NAME_PREFIX)) {
      return Pair.of(TOOLS_URI, propertyNameWithPrefix.substring(TOOLS_NS_NAME_PREFIX.length()));
    }
    if (propertyNameWithPrefix.startsWith(PREFIX_APP)) {
      return Pair.of(AUTO_URI, propertyNameWithPrefix.substring(PREFIX_APP.length()));
    }
    return Pair.of(ANDROID_URI, propertyNameWithPrefix);
  }

  @NotNull
  private static XmlName getXmlName(@NotNull XmlAttributeDescriptor descriptor, @NotNull XmlTag context) {
    String namespace = null;
    if (descriptor instanceof NamespaceAwareXmlAttributeDescriptor) {
      namespace = ((NamespaceAwareXmlAttributeDescriptor)descriptor).getNamespace(context);
    }
    return new XmlName(descriptor.getName(), namespace);
  }

  @NotNull
  private static XmlName getXmlName(@NotNull String qualifiedName, @NotNull String defaultNamespace) {
    if (qualifiedName.startsWith(ANDROID_NS_NAME_PREFIX)) {
      return new XmlName(StringUtil.trimStart(qualifiedName, ANDROID_NS_NAME_PREFIX), ANDROID_URI);
    }
    return new XmlName(qualifiedName, defaultNamespace);
  }

  private static Table<String, String, NlPropertyItem> combine(@NotNull Table<String, String, NlPropertyItem> properties,
                                                               @Nullable Table<String, String, NlPropertyItem> combinedProperties) {
    if (combinedProperties == null) {
      return properties;
    }
    List<String> namespaces = new ArrayList<>(combinedProperties.rowKeySet());
    List<String> propertiesToRemove = new ArrayList<>();
    for (String namespace : namespaces) {
      propertiesToRemove.clear();
      for (Map.Entry<String, NlPropertyItem> entry : combinedProperties.row(namespace).entrySet()) {
        NlPropertyItem other = properties.get(namespace, entry.getKey());
        if (!entry.getValue().sameDefinition(other)) {
          propertiesToRemove.add(entry.getKey());
        }
      }
      for (String propertyName : propertiesToRemove) {
        combinedProperties.remove(namespace, propertyName);
      }
    }
    // Never include the ID attribute when looking at multiple components:
    combinedProperties.remove(ANDROID_URI, ATTR_ID);
    return combinedProperties;
  }

  private static void setUpDesignProperties(@NotNull Table<String, String, NlPropertyItem> properties) {
    List<String> designProperties = new ArrayList<>(properties.row(TOOLS_URI).keySet());
    for (String propertyName : designProperties) {
      NlPropertyItem item = properties.get(AUTO_URI, propertyName);
      if (item == null) {
        item = properties.get(ANDROID_URI, propertyName);
      }
      if (item != null) {
        NlPropertyItem designItem = item.getDesignTimeProperty();
        properties.put(TOOLS_URI, propertyName, designItem);
      }
    }
  }
}
