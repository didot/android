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

import com.android.resources.ResourceType;
import com.android.tools.idea.XmlBuilder;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager;
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

import static com.android.SdkConstants.*;

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
      String src = editor.displayResourceInput(EnumSet.of(ResourceType.DRAWABLE));
      if (src != null) {
        setSrcAttribute(newChild, src);
        return true;
      }
      else {
        // Remove the view; the insertion was canceled
        return false;
      }
    }

    // Fallback if dismissed or during previews etc
    if (insertType.isCreate()) {
      setSrcAttribute(newChild, getSampleImageSrc());
    }

    return true;
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
    if (shouldUseSrcCompat(component.getModel())) {
      component.setAttribute(ANDROID_URI, ATTR_SRC, null);
      component.setAttribute(AUTO_URI, ATTR_SRC_COMPAT, imageSource);
    }
    else {
      component.setAttribute(ANDROID_URI, ATTR_SRC, imageSource);
      component.setAttribute(AUTO_URI, ATTR_SRC_COMPAT, null);
    }
  }

  public boolean shouldUseSrcCompat(@NotNull NlModel model) {
    return moduleDependsOnAppCompat(model) &&
           currentActivityIsDerivedFromAppCompatActivity(model);
  }

  private static boolean moduleDependsOnAppCompat(@NotNull NlModel model) {
    GradleDependencyManager manager = GradleDependencyManager.getInstance(model.getProject());
    return manager.dependsOn(model.getModule(), APPCOMPAT_LIB_ARTIFACT);
  }

  private static boolean currentActivityIsDerivedFromAppCompatActivity(@NotNull NlModel model) {
    Configuration configuration = model.getConfiguration();
    String activityClassName = configuration.getActivity();
    if (activityClassName == null) {
      // The activity is not specified in the XML file.
      // We cannot know if the activity is derived from AppCompatActivity.
      // Assume we are since this is how the default activities are created.
      return true;
    }
    if (activityClassName.startsWith(".")) {
      MergedManifest manifest = MergedManifest.get(model.getModule());
      String pkg = StringUtil.notNullize(manifest.getPackage());
      activityClassName = pkg + activityClassName;
    }
    JavaPsiFacade facade = JavaPsiFacade.getInstance(model.getProject());
    PsiClass activityClass = facade.findClass(activityClassName, model.getModule().getModuleScope());
    while (activityClass != null && !CLASS_APP_COMPAT_ACTIVITY.equals(activityClass.getQualifiedName())) {
      activityClass = activityClass.getSuperClass();
    }
    return activityClass != null;
  }
}
