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
import static com.android.tools.idea.uibuilder.handlers.motion.property2.MotionLayoutPropertyProvider.mapToCustomType;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.uibuilder.api.AccessoryPanelInterface;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionDesignSurfaceEdits;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneUtils;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionAttributes;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditorSelector;
import com.android.tools.idea.uibuilder.property2.NelePropertiesModel;
import com.android.tools.idea.uibuilder.property2.NelePropertyItem;
import com.android.tools.property.panel.api.PropertiesModel;
import com.android.tools.property.panel.api.PropertiesTable;
import com.google.common.base.Predicates;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ui.UIUtil;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import kotlin.jvm.functions.Function0;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link PropertiesModel} for motion layout property editor.
 */
public class MotionLayoutAttributesModel extends NelePropertiesModel {
  private final MotionLayoutPropertyProvider myMotionLayoutPropertyProvider;
  private Map<String, PropertiesTable<NelePropertyItem>> myAllProperties;

  public MotionLayoutAttributesModel(@NotNull Disposable parentDisposable, @NotNull AndroidFacet facet) {
    super(parentDisposable, new MotionLayoutPropertyProvider(facet), facet, false);
    myMotionLayoutPropertyProvider = (MotionLayoutPropertyProvider)getProvider();
    setDefaultValueProvider(new MotionDefaultPropertyValueProvider());
  }

  public Map<String, PropertiesTable<NelePropertyItem>> getAllProperties() {
    return myAllProperties;
  }

  @Override
  protected boolean loadProperties(@Nullable Object accessoryType,
                                   @Nullable Object accessory,
                                   @NotNull List<? extends NlComponent> components,
                                   @NotNull Function0<Boolean> wantUpdate) {
    if (accessoryType == null || accessory == null || !wantUpdate.invoke()) {
      return false;
    }

    MotionEditorSelector.Type type = (MotionEditorSelector.Type)accessoryType;
    MTag[] tags = (MTag[])accessory;
    MotionSelection selection = new MotionSelection(type, tags, components);
    Map<String, PropertiesTable<NelePropertyItem>> newProperties =
      myMotionLayoutPropertyProvider.getAllProperties(this, selection);
    setLastUpdateCompleted(false);

    UIUtil.invokeLaterIfNeeded(() -> {
      try {
        if (wantUpdate.invoke()) {
          updateLiveListeners(components);
          PropertiesTable<NelePropertyItem> first = newProperties.isEmpty() ? PropertiesTable.Companion.emptyTable()
                                                                            : newProperties.values().iterator().next();
          myAllProperties = newProperties;
          setProperties(first);
          firePropertiesGenerated();
        }
      }
      finally {
        setLastUpdateCompleted(true);
      }
    });
    return true;
  }

  @Override
  @Nullable
  public String getPropertyValue(@NotNull NelePropertyItem property) {
    MotionSelection selection = getMotionSelection(property);
    String subTag = getSubTag(property);
    if (selection == null) {
      return null;
    }
    MotionSceneTag motionTag = selection.getMotionSceneTag();
    if (motionTag == null) {
      return null;
    }
    if (subTag != null && subTag.equals(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE)) {
      MTag customTag = findCustomTag(motionTag, property.getName());
      return customTag != null ? customTag.getAttributeValue(mapToCustomType(property.getType())) : null;
    }
    if (subTag == null) {
      return motionTag.getAttributeValue(property.getName());
    }
    MotionSceneTag sectionTag = getConstraintSectionTag(motionTag, subTag);
    if (sectionTag == null ){
      return null;
    }
    return sectionTag.getAttributeValue(property.getName());
  }

  @Override
  public void setPropertyValue(@NotNull NelePropertyItem property, @Nullable String newValue) {
    String attributeName = property.getName();
    MotionSelection selection = getMotionSelection(property);
    String subTag = getSubTag(property);
    MTag.TagWriter tagWriter = null;
    if (selection == null) {
      return;
    }
    MotionSceneTag motionTag = selection.getMotionSceneTag();
    if (motionTag == null) {
      tagWriter = createConstraintTag(selection);
    }
    else if (subTag != null && subTag.equals(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE)) {
      MTag customTag = findCustomTag(motionTag, property.getName());
      if (customTag != null) {
        tagWriter = MotionSceneUtils.getTagWriter(customTag);
        attributeName = mapToCustomType(property.getType());
      }
    }
    else if (subTag == null) {
      tagWriter = MotionSceneUtils.getTagWriter(motionTag);
    }
    else if (selection.getType() == MotionEditorSelector.Type.CONSTRAINT){
      MotionSceneTag sectionTag = getConstraintSectionTag(motionTag, subTag);
      if (sectionTag != null) {
        tagWriter = MotionSceneUtils.getTagWriter(sectionTag);
      }
      else if (newValue != null) {
        tagWriter = createConstraintSectionTag(selection, motionTag, subTag);
      }
    }
    if (tagWriter != null) {
      tagWriter.setAttribute(property.getNamespace(), attributeName, newValue);
      tagWriter.commit(String.format("Set %1$s.%2$s to %3$s", tagWriter.getTagName(), property.getName(), String.valueOf(newValue)));
    }
  }

  public static MTag.TagWriter createConstraintSectionTag(@NotNull MotionSelection selection,
                                                          @NotNull MotionSceneTag constraintTag,
                                                          @NotNull String section) {
    MTag.TagWriter tagWriter = MotionSceneUtils.getChildTagWriter(constraintTag, section);
    MotionAttributes attrs = selection.getMotionAttributes();
    if (attrs != null) {
      Predicate<MotionAttributes.DefinedAttribute> isApplicable = findIncludePredicate(section);
      for (MotionAttributes.DefinedAttribute attr : attrs.getAttrMap().values()) {
        if (isApplicable.test(attr)) {
          tagWriter.setAttribute(attr.getNamespace(), attr.getName(), attr.getValue());
        }
      }
    }
    return tagWriter;
  }

  private static Predicate<MotionAttributes.DefinedAttribute> findIncludePredicate(@NotNull String sectionName) {
    switch (sectionName) {
      case MotionSceneAttrs.Tags.LAYOUT:
        return attr -> attr.isLayoutAttribute();
      case MotionSceneAttrs.Tags.PROPERTY_SET:
        return attr -> attr.isPropertySetAttribute();
      case MotionSceneAttrs.Tags.TRANSFORM:
        return attr -> attr.isTransformAttribute();
      case MotionSceneAttrs.Tags.MOTION:
        return attr -> attr.isMotionAttribute();
      default:
        return Predicates.alwaysFalse();
    }
  }

  /**
   * Given the current selection create a new custom tag with the specified attrName, value, and type
   *
   * Upon completion perform the specified operation with the created custom tag.
   * Note that this method may create the constraint tag for the custom tag as well.
   */
  public void createCustomXmlTag(@NotNull MotionSelection selection,
                                 @NotNull String attrName,
                                 @NotNull String value,
                                 @NotNull CustomAttributeType type,
                                 @NotNull Consumer<MotionSceneTag> operation) {
    String valueAttrName = type.getTagName();
    String newValue = StringUtil.isNotEmpty(value) ? value : type.getDefaultValue();
    String commandName = String.format("Set %1$s.%2$s to %3$s", MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE, attrName, newValue);
    MTag.TagWriter tagWriter = null;
    MTag oldCustomTag = null;

    MotionSceneTag motionTag = selection.getMotionSceneTag();
    if (motionTag == null) {
      tagWriter = createConstraintTag(selection);
      if (tagWriter == null) {
        // Should not happen!
        return;
      }
    }
    else {
      oldCustomTag = findCustomTag(motionTag, attrName);
    }
    MTag.TagWriter constraintWriter = tagWriter;

    if (tagWriter != null) {
      tagWriter = tagWriter.getChildTagWriter(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE);
    }
    else if (oldCustomTag != null) {
      tagWriter = oldCustomTag.getTagWriter();
    }
    else {
      tagWriter = motionTag.getChildTagWriter(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE);
    }
    tagWriter.setAttribute(AUTO_URI, MotionSceneAttrs.ATTR_CUSTOM_ATTRIBUTE_NAME, attrName);
    tagWriter.setAttribute(AUTO_URI, valueAttrName, newValue);
    if (oldCustomTag != null) {
      for (String attr : MotionSceneAttrs.ourCustomAttribute) {
        if (attr != valueAttrName) {
          tagWriter.setAttribute(AUTO_URI, attr, null);
        }
      }
    }
    MTag.TagWriter committer = constraintWriter != null ? constraintWriter : tagWriter;

    Runnable transaction = () -> {
      MTag createdCustomTag = committer.commit(commandName);
      if (!createdCustomTag.getTagName().equals(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE)) {
        createdCustomTag = createdCustomTag.getChildTags(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE)[0];
      }
      operation.accept((MotionSceneTag)createdCustomTag);
    };

    ApplicationManager.getApplication().assertIsDispatchThread();
    TransactionGuard.submitTransaction(this, () ->
      WriteCommandAction.runWriteCommandAction(
        getFacet().getModule().getProject(),
        commandName,
        null,
        transaction,
        selection.getSceneFile()));
  }

  /**
   * Create a <Constraint> tag and copy over the inherited attributes.
   * @return an uncommitted TagWriter on the created tag.
   */
  private static MTag.TagWriter createConstraintTag(@NotNull MotionSelection selection) {
    if (selection.getType() != MotionEditorSelector.Type.CONSTRAINT) {
      // Should not happen!
      return null;
    }
    MotionAttributes attrs = selection.getMotionAttributes();
    if (attrs == null) {
      // Should not happen!
      return null;
    }
    MTag constraintSetTag = attrs.getConstraintSet();
    if (constraintSetTag == null) {
      // Should not happen!
      return null;
    }
    MTag.TagWriter tagWriter = constraintSetTag.getChildTagWriter(MotionSceneAttrs.Tags.CONSTRAINT);
    if (tagWriter == null) {
      // Should not happen!
      return null;
    }
    tagWriter.setAttribute(ANDROID_URI, ATTR_ID, selection.getComponentId());
    for (MotionAttributes.DefinedAttribute attr : attrs.getAttrMap().values()) {
      tagWriter.setAttribute(attr.getNamespace(), attr.getName(), attr.getValue());
    }
    return tagWriter;
  }

  public void deleteTag(@NotNull XmlTag tag, @NotNull Runnable operation) {
    PsiFile file = tag.getContainingFile();
    Runnable transaction = () -> {
      tag.delete();
      operation.run();
    };

    ApplicationManager.getApplication().assertIsDispatchThread();
    TransactionGuard.submitTransaction(this, () ->
      WriteCommandAction.runWriteCommandAction(
        getFacet().getModule().getProject(),
        "Delete " + tag.getLocalName(),
        null,
        transaction,
        file));
  }

  @Override
  protected boolean wantComponentSelectionUpdate(@Nullable DesignSurface surface,
                                                 @Nullable DesignSurface activeSurface,
                                                 @Nullable AccessoryPanelInterface activePanel) {
    return wantPanelSelectionUpdate(activePanel, activePanel);
  }

  @Override
  protected boolean wantPanelSelectionUpdate(@Nullable AccessoryPanelInterface panel, @Nullable AccessoryPanelInterface activePanel) {
    return panel == activePanel &&
           panel != null &&
           panel.getSelectedAccessoryType() != null &&
           panel instanceof MotionDesignSurfaceEdits;
  }

  @Nullable
  public static MotionSelection getMotionSelection(@NotNull NelePropertyItem property) {
    return (MotionSelection)property.getOptionalValue1();
  }

  @Nullable
  public static String getSubTag(@NotNull NelePropertyItem property) {
    return (String)property.getOptionalValue2();
  }

  @Nullable
  public static MTag findCustomTag(MotionSceneTag motionTag, @Nullable String attrName) {
    if (attrName == null) {
      return null;
    }
    return Arrays.stream(motionTag.getChildTags(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE))
      .filter(child -> attrName.equals(child.getAttributeValue(MotionSceneAttrs.ATTR_CUSTOM_ATTRIBUTE_NAME)))
      .findFirst()
      .orElse(null);
  }


  @Nullable
  public static MotionSceneTag getConstraintSectionTag(@NotNull MotionSceneTag constraint, @NotNull String sectionTagName) {
    MTag[] tags = constraint.getChildTags(sectionTagName);
    if (tags.length == 0 || !(tags[0] instanceof MotionSceneTag)) {
      return null;
    }
    // TODO: If there are multiple sub tags (by mistake) should we write to all of them?
    return (MotionSceneTag)tags[0];
  }
}
