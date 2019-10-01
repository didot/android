/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.property2;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.AUTO_URI;

import com.android.ide.common.rendering.api.AttributeFormat;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.resources.ResourceType;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditorSelector;
import com.android.tools.idea.uibuilder.property2.NeleFlagsPropertyItem;
import com.android.tools.idea.uibuilder.property2.NeleIdPropertyItem;
import com.android.tools.idea.uibuilder.property2.NelePropertiesModel;
import com.android.tools.idea.uibuilder.property2.NelePropertyItem;
import com.android.tools.idea.uibuilder.property2.NelePropertyType;
import com.android.tools.idea.uibuilder.property2.PropertiesProvider;
import com.android.tools.idea.uibuilder.property2.support.TypeResolver;
import com.android.tools.property.panel.api.PropertiesTable;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.NamespaceAwareXmlAttributeDescriptor;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import java.awt.EventQueue;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.android.dom.AndroidDomElementDescriptorProvider;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.attrs.StyleableDefinition;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Property provider for motion layout property editor.
 *
 * The properties are retrieved from the attrs.xml supplied with the
 * constraint layout library.
 */
public class MotionLayoutPropertyProvider implements PropertiesProvider {
  private final AndroidFacet myFacet;
  private final Project myProject;
  private final XmlElementDescriptorProvider myDescriptorProvider;
  private final Table<String, String, NelePropertyItem> myEmptyTable;

  private static final int EXPECTED_ROWS = 3;
  private static final int EXPECTED_CELLS_PER_ROW = 10;

  public MotionLayoutPropertyProvider(@NotNull AndroidFacet facet) {
    myFacet = facet;
    myProject = facet.getModule().getProject();
    myDescriptorProvider = new AndroidDomElementDescriptorProvider();
    myEmptyTable = ImmutableTable.of();
  }

  @NotNull
  @Override
  public PropertiesTable<NelePropertyItem> getProperties(@NotNull NelePropertiesModel model,
                                                         @Nullable Object optionalValue,
                                                         @NotNull List<? extends NlComponent> components) {
    return PropertiesTable.Companion.emptyTable();
  }

  @NotNull
  public Map<String, PropertiesTable<NelePropertyItem>> getAllProperties(@NotNull NelePropertiesModel model,
                                                                         @NotNull MotionSelection selection) {
    assert (!EventQueue.isDispatchThread() || ApplicationManager.getApplication().isUnitTestMode());

    return DumbService.getInstance(myProject).runReadActionInSmartMode(() -> getPropertiesImpl(model, selection));
  }

  @NotNull
  @Override
  public PropertiesTable<NelePropertyItem> createEmptyTable() {
    return PropertiesTable.Companion.create(HashBasedTable.create(EXPECTED_ROWS, EXPECTED_CELLS_PER_ROW));
  }

  private Map<String, PropertiesTable<NelePropertyItem>> getPropertiesImpl(@NotNull NelePropertiesModel model,
                                                                           @NotNull MotionSelection selection) {
    if (selection.getComponents().isEmpty()) {
      return Collections.emptyMap();
    }

    ModuleResourceManagers resourceManagers = ModuleResourceManagers.getInstance(myFacet);
    ResourceManager localResourceManager = resourceManagers.getLocalResourceManager();
    ResourceManager frameworkResourceManager = resourceManagers.getFrameworkResourceManager();
    if (frameworkResourceManager == null) {
      Logger.getInstance(MotionLayoutPropertyProvider.class).error(
        "No system resource manager for module: " + myFacet.getModule().getName());
      return Collections.emptyMap();
    }

    AttributeDefinitions localAttrDefs = localResourceManager.getAttributeDefinitions();
    AttributeDefinitions systemAttrDefs = frameworkResourceManager.getAttributeDefinitions();
    if (localAttrDefs == null) {
      return Collections.emptyMap();
    }

    Map<String, PropertiesTable<NelePropertyItem>> allProperties = new LinkedHashMap<>();

    MotionSceneTag motionSceneTag = selection.getMotionSceneTag();
    XmlTag tag = selection.getXmlTag(motionSceneTag);
    if (motionSceneTag != null && tag != null) {
      XmlElementDescriptor elementDescriptor = myDescriptorProvider.getDescriptor(tag);
      if (elementDescriptor == null) {
        return Collections.emptyMap();
      }
      XmlAttributeDescriptor[] descriptors = elementDescriptor.getAttributesDescriptors(tag);
      Table<String, String, NelePropertyItem> properties = HashBasedTable.create(3, descriptors.length);
      for (XmlAttributeDescriptor descriptor : descriptors) {
        String namespaceUri = getNamespace(descriptor, tag);
        String name = descriptor.getName();
        AttributeDefinitions attrDefs = (ANDROID_URI == namespaceUri) ? systemAttrDefs : localAttrDefs;
        ResourceNamespace namespace = ResourceNamespace.fromNamespaceUri(namespaceUri);
        AttributeDefinition attrDef = (namespace != null && attrDefs != null)
                                      ? attrDefs.getAttrDefinition(ResourceReference.attr(namespace, name)) : null;
        NelePropertyItem property = createProperty(namespaceUri, name, attrDef, model, selection, null);
        properties.put(namespaceUri, name, property);
      }
      allProperties.put(tag.getLocalName(), PropertiesTable.Companion.create(properties));

      loadCustomAttributes(model, allProperties, motionSceneTag, selection);

      if (tag.getLocalName().equals(MotionSceneAttrs.Tags.CONSTRAINT)) {
        XmlElementDescriptor[] subTagDescriptors = elementDescriptor.getElementsDescriptors(tag);
        for (XmlElementDescriptor descriptor : subTagDescriptors) {
          String subTagName = descriptor.getName();
          if (!subTagName.equals(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE)) {
            Table<String, String, NelePropertyItem> subTagProperties =
              loadFromStyleableName(subTagName, localAttrDefs, model, selection);
            allProperties.put(subTagName, PropertiesTable.Companion.create(subTagProperties));
          }
        }
      }
    }
    else if (selection.getType() == MotionEditorSelector.Type.CONSTRAINT) {
      Table<String, String, NelePropertyItem> constraintProperties =
        loadFromStyleableName(MotionSceneAttrs.Tags.CONSTRAINT, localAttrDefs, model, selection);
      allProperties.put(MotionSceneAttrs.Tags.CONSTRAINT, PropertiesTable.Companion.create(constraintProperties));
    }
    return allProperties;
  }

  private static void loadCustomAttributes(@NotNull NelePropertiesModel model,
                                           @NotNull Map<String, PropertiesTable<NelePropertyItem>> allProperties,
                                           @NotNull MotionSceneTag motionSceneTag,
                                           @NotNull MotionSelection selection) {
    MTag[] customTags = motionSceneTag.getChildTags(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE);
    if (customTags.length == 0) {
      return;
    }
    Table<String, String, NelePropertyItem> customProperties = HashBasedTable.create(3, customTags.length);
    for (MTag customTag : customTags) {
      String name = customTag.getAttributeValue(MotionSceneAttrs.ATTR_CUSTOM_ATTRIBUTE_NAME);
      if (name == null) {
        continue;
      }
      for (String customType : MotionSceneAttrs.ourCustomAttribute) {
        String customValue = customTag.getAttributeValue(customType);
        if (customValue != null) {
          customProperties.put(AUTO_URI, name, createCustomProperty(name, customType, selection, model));
          break;
        }
      }
    }
    allProperties.put(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE, PropertiesTable.Companion.create(customProperties));
  }

  private Table<String, String, NelePropertyItem> loadFromStyleableName(@NotNull String subTagName,
                                                                        @NotNull AttributeDefinitions attrDefs,
                                                                        @NotNull NelePropertiesModel model,
                                                                        @NotNull MotionSelection selection) {
    ResourceReference reference = new ResourceReference(ResourceNamespace.TODO(), ResourceType.STYLEABLE, subTagName);
    StyleableDefinition styleable = attrDefs.getStyleableDefinition(reference);
    if (styleable == null) {
      return myEmptyTable;
    }
    Table<String, String, NelePropertyItem> properties = HashBasedTable.create(3, styleable.getAttributes().size());
    styleable.getAttributes().forEach((AttributeDefinition attr) -> {
      NelePropertyItem property = createProperty(attr.getResourceReference().getNamespace().getXmlNamespaceUri(),
                                                 attr.getName(), attr, model, selection, subTagName);
      properties.put(property.getNamespace(), property.getName(), property);
    });
    return properties;
  }

  public static NelePropertyItem createCustomProperty(@NotNull String name,
                                                      @NotNull String customType,
                                                      @NotNull MotionSelection selection,
                                                      @NotNull NelePropertiesModel model) {
    NelePropertyType type = mapFromCustomType(customType);
    List<? extends NlComponent> components = selection.getComponents();
    return new NelePropertyItem("", name, type, null, "", "", model, components, selection, MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE);
  }

  private static NelePropertyItem createProperty(@NotNull String namespace,
                                                 @NotNull String name,
                                                 @Nullable AttributeDefinition attr,
                                                 @NotNull NelePropertiesModel model,
                                                 @NotNull MotionSelection selection,
                                                 @Nullable String subTag) {
    List<? extends NlComponent> components = selection.getComponents();
    NelePropertyType type = TypeResolver.INSTANCE.resolveType(name, attr);
    String libraryName = StringUtil.notNullize(attr != null ? attr.getLibraryName() : null);
    if (namespace == ANDROID_URI && name == ATTR_ID) {
      return new NeleIdPropertyItem(model, attr, "", components, selection, subTag);
    }
    if (attr != null && attr.getFormats().contains(AttributeFormat.FLAGS) && attr.getValues().length == 0) {
      return new NeleFlagsPropertyItem(namespace, name, type, attr, "", libraryName, model, components, selection, subTag);
    }
    return new NelePropertyItem(namespace, name, type, attr, "", libraryName, model, components, selection, subTag);
  }

  @NotNull
  private static String getNamespace(@NotNull XmlAttributeDescriptor descriptor, @NotNull XmlTag context) {
    String namespace = null;
    if (descriptor instanceof NamespaceAwareXmlAttributeDescriptor) {
      namespace = ((NamespaceAwareXmlAttributeDescriptor)descriptor).getNamespace(context);
    }
    return namespace != null ? namespace : ANDROID_URI;
  }

  @NotNull
  static NelePropertyType mapFromCustomType(@NotNull String customType) {
    switch (customType) {
      case MotionSceneAttrs.ATTR_CUSTOM_COLOR_VALUE:
        return NelePropertyType.COLOR;

      case MotionSceneAttrs.ATTR_CUSTOM_COLOR_DRAWABLE_VALUE:
        return NelePropertyType.COLOR_STATE_LIST;

      case MotionSceneAttrs.ATTR_CUSTOM_INTEGER_VALUE:
        return NelePropertyType.INTEGER;

      case MotionSceneAttrs.ATTR_CUSTOM_FLOAT_VALUE:
        return NelePropertyType.FLOAT;

      case MotionSceneAttrs.ATTR_CUSTOM_DIMENSION_VALUE:
        return NelePropertyType.DIMENSION;

      case MotionSceneAttrs.ATTR_CUSTOM_PIXEL_DIMENSION_VALUE:
        return NelePropertyType.DIMENSION_PIXEL;

      case MotionSceneAttrs.ATTR_CUSTOM_BOOLEAN_VALUE:
        return NelePropertyType.BOOLEAN;

      case MotionSceneAttrs.ATTR_CUSTOM_STRING_VALUE:
      default:
        return NelePropertyType.STRING;
    }
  }

  @NotNull
  static String mapToCustomType(@NotNull NelePropertyType type) {
    switch (type) {
      case COLOR:
        return MotionSceneAttrs.ATTR_CUSTOM_COLOR_VALUE;

      case COLOR_STATE_LIST:
        return MotionSceneAttrs.ATTR_CUSTOM_COLOR_DRAWABLE_VALUE;

      case INTEGER:
        return MotionSceneAttrs.ATTR_CUSTOM_INTEGER_VALUE;

      case FLOAT:
        return MotionSceneAttrs.ATTR_CUSTOM_FLOAT_VALUE;

      case DIMENSION:
        return MotionSceneAttrs.ATTR_CUSTOM_DIMENSION_VALUE;

      case DIMENSION_PIXEL:
        return MotionSceneAttrs.ATTR_CUSTOM_PIXEL_DIMENSION_VALUE;

      case BOOLEAN:
        return MotionSceneAttrs.ATTR_CUSTOM_BOOLEAN_VALUE;

      case STRING:
      default:
        return MotionSceneAttrs.ATTR_CUSTOM_STRING_VALUE;
    }
  }
}
