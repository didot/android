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
package com.android.tools.idea.uibuilder.handlers;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.resources.ResourceType;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.command.NlWriteCommandAction;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.target.ComponentAssistantActionTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.res.SampleDataResourceItem;
import com.android.tools.idea.res.SampleDataResourceRepository;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.tools.idea.uibuilder.handlers.assistant.ImageViewAssistant;
import com.android.tools.idea.uibuilder.model.NlModelHelperKt;
import com.android.tools.idea.uibuilder.property.assistant.ComponentAssistantFactory;
import com.android.xml.XmlBuilder;
import com.google.common.collect.ImmutableList;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.flags.StudioFlags.NELE_SAMPLE_DATA_UI;

/**
 * Handler for the {@code <ImageView>} widget
 */
public class ImageViewHandler extends ViewHandler {
  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_SRC,
      ATTR_CONTENT_DESCRIPTION,
      ATTR_BACKGROUND,
      ATTR_SCALE_TYPE,
      ATTR_ADJUST_VIEW_BOUNDS,
      ATTR_CROP_TO_PADDING);
  }

  @Override
  @NotNull
  public String getPreferredProperty() {
    return ATTR_SRC;
  }

  @Override
  @NotNull
  @Language("XML")
  public String getXml(@NotNull String tagName, @NotNull XmlType xmlType) {
    return new XmlBuilder()
      .startTag(tagName)
      .androidAttribute(ATTR_SRC, getSampleImageSrc())
      .androidAttribute(ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT)
      .androidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT)
      .endTag(tagName)
      .toString();
  }

  @Override
  public boolean onCreate(@NotNull ViewEditor editor,
                          @Nullable NlComponent parent,
                          @NotNull NlComponent newChild,
                          @NotNull InsertType insertType) {
    if (insertType == InsertType.CREATE) { // NOT InsertType.CREATE_PREVIEW
      return showImageChooser(editor, newChild);
    }

    // Fallback if dismissed or during previews etc
    if (insertType.isCreate()) {
      setSrcAttribute(newChild, getSampleImageSrc());
    }

    return true;
  }

  private boolean showImageChooser(@NotNull ViewEditor editor, @NotNull NlComponent component) {
    String src = editor.displayResourceInput(EnumSet.of(ResourceType.DRAWABLE));
    if (src != null) {
      setSrcAttribute(component, src);
      return true;
    }

    // Remove the view; the insertion was canceled
    return false;
  }

  /**
   * Returns a source attribute value which points to a sample image. This is typically
   * used to provide an initial image shown on ImageButtons, etc. There is no guarantee
   * that the source pointed to by this method actually exists.
   *
   * @return a source attribute to use for sample images, never null
   */
  @NotNull
  public String getSampleImageSrc() {
    // Builtin graphics available since v1:
    return "@android:drawable/btn_star"; //$NON-NLS-1$
  }

  public void setSrcAttribute(@NotNull NlComponent component, @NotNull String imageSource) {
    NlWriteCommandAction.run(component, "", () -> {
      if (shouldUseSrcCompat(component.getModel())) {
        component.setAttribute(ANDROID_URI, ATTR_SRC, null);
        component.setAttribute(AUTO_URI, ATTR_SRC_COMPAT, imageSource);
      }
      else {
        component.setAttribute(ANDROID_URI, ATTR_SRC, imageSource);
        component.setAttribute(AUTO_URI, ATTR_SRC_COMPAT, null);
      }
    });
  }

  public void setSampleSrc(@NotNull NlComponent component, @Nullable SampleDataResourceItem item, int resourceValueIndex) {
    if (item != null) {
      String suffix = resourceValueIndex >= 0 ? "[" + resourceValueIndex + "]" : "";
      setSampleSrc(component, getResourcePrefix(item.getNamespace()) + item.getName() + suffix);
    }
    else {
      setSampleSrc(component, null);
    }
  }

  public void setSampleSrc(@NotNull NlComponent component, String value) {
    String attr = shouldUseSrcCompat(component.getModel()) ? ATTR_SRC_COMPAT : ATTR_SRC;
    NlWriteCommandAction.run(component, "Set sample source", () -> component.setAttribute(TOOLS_URI, attr, value));
  }

  @Nullable
  public String getSampleSrc(@NotNull NlComponent component) {
    String attr = shouldUseSrcCompat(component.getModel()) ? ATTR_SRC_COMPAT : ATTR_SRC;
    return component.getAttribute(TOOLS_URI, attr);
  }

  @NotNull
  private static String getResourcePrefix(ResourceNamespace namespace) {
    String prefix;
    if (SampleDataResourceRepository.PREDEFINED_SAMPLES_NS.equals(namespace)) {
      prefix = TOOLS_SAMPLE_PREFIX;
    }
    else if (ResourceNamespace.TODO.equals(namespace)) {
      prefix = SAMPLE_PREFIX;
    }
    else {
      String packageName = namespace.getPackageName();
      if (packageName != null) {
        prefix = "@" + packageName + ":" + SAMPLE_PREFIX.substring(1);
      }
      else {
        prefix = SAMPLE_PREFIX;
      }
    }
    return prefix;
  }

  public static boolean shouldUseSrcCompat(@NotNull NlModel model) {
    return NlModelHelperKt.moduleDependsOnAppCompat(model) &&
           NlModelHelperKt.currentActivityIsDerivedFromAppCompatActivity(model);
  }

  @Nullable
  private ComponentAssistantFactory getComponentAssistant() {
    if (!NELE_SAMPLE_DATA_UI.get()) {
      return null;
    }
    return (context) -> new ImageViewAssistant(context, this).getComponent();
  }

  @NotNull
  @Override
  public List<Target> createTargets(@NotNull SceneComponent sceneComponent) {
    ComponentAssistantFactory panelFactory =
      getComponentAssistant();

    return panelFactory != null ?
           ImmutableList.of(new ComponentAssistantActionTarget(panelFactory)) :
           ImmutableList.of();
  }
}
