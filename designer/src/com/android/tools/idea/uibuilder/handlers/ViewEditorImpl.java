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

import com.android.assetstudiolib.AssetStudio;
import com.android.assetstudiolib.GraphicGenerator;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.common.repository.GradleVersion;
import com.android.resources.ResourceType;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.ui.resourcechooser.ChooseResourceDialog;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.ChooseClassDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.DRAWABLE_FOLDER;

/**
 * Implementation of the {@link ViewEditor} abstraction presented
 * to {@link ViewHandler} instances
 */
public class ViewEditorImpl extends ViewEditor {
  private final ScreenView myScreen;

  public ViewEditorImpl(@NotNull ScreenView screen) {
    myScreen = screen;
  }

  @Override
  public int getDpi() {
    return myScreen.getConfiguration().getDensity().getDpiValue();
  }

  @Nullable
  @Override
  public AndroidVersion getCompileSdkVersion() {
    return AndroidModuleInfo.get(myScreen.getModel().getFacet()).getBuildSdkVersion();
  }

  @NotNull
  @Override
  public AndroidVersion getMinSdkVersion() {
    return AndroidModuleInfo.get(myScreen.getModel().getFacet()).getMinSdkVersion();
  }

  @NotNull
  @Override
  public AndroidVersion getTargetSdkVersion() {
    return AndroidModuleInfo.get(myScreen.getModel().getFacet()).getTargetSdkVersion();
  }

  @NotNull
  @Override
  public Configuration getConfiguration() {
    return myScreen.getConfiguration();
  }

  @NotNull
  @Override
  public NlModel getModel() {
    return myScreen.getModel();
  }

  @Override
  public boolean moduleContainsResource(@NotNull ResourceType type, @NotNull String name) {
    return myScreen.getModel().getFacet().getModuleResources(true).hasResourceItem(type, name);
  }

  @Override
  public void copyVectorAssetToMainModuleSourceSet(@NotNull String asset) {
    Project project = myScreen.getModel().getProject();
    String message = "Do you want to copy vector asset " + asset + " to your main module source set?";

    if (Messages.showYesNoDialog(project, message, "Copy Vector Asset", Messages.getQuestionIcon()) == Messages.NO) {
      return;
    }

    try (InputStream in = GraphicGenerator.class.getClassLoader().getResourceAsStream(AssetStudio.getPathForBasename(asset))) {
      VirtualFile drawableDirectory = getDrawableDirectory();

      if (drawableDirectory == null) {
        return;
      }

      drawableDirectory.createChildData(this, asset + DOT_XML).setBinaryContent(ByteStreams.toByteArray(in));
    }
    catch (IOException exception) {
      Logger.getInstance(ViewEditorImpl.class).warn(exception);
    }
  }

  @Nullable
  private VirtualFile getDrawableDirectory() throws IOException {
    VirtualFile resourceDirectory = myScreen.getModel().getFacet().getPrimaryResourceDir();

    if (resourceDirectory == null) {
      Logger.getInstance(ViewEditorImpl.class).warn("resourceDirectory is null");
      return null;
    }

    VirtualFile drawableDirectory = resourceDirectory.findChild(DRAWABLE_FOLDER);

    if (drawableDirectory == null) {
      return resourceDirectory.createChildDirectory(this, DRAWABLE_FOLDER);
    }

    return drawableDirectory;
  }

  @Override
  public boolean isModuleDependency(@NotNull String artifact) {
    AndroidGradleModel gradleModel = AndroidGradleModel.get(getModel().getFacet());
    return gradleModel != null && GradleUtil.dependsOn(gradleModel, artifact);
  }

  @Nullable
  @Override
  public GradleVersion getModuleDependencyVersion(@NotNull String artifact) {
    AndroidGradleModel gradleModel = AndroidGradleModel.get(getModel().getFacet());
    return gradleModel != null ? GradleUtil.getModuleDependencyVersion(gradleModel, artifact) : null;
  }

  @Nullable
  @Override
  public Map<NlComponent, Dimension> measureChildren(@NotNull NlComponent parent, @Nullable RenderTask.AttributeFilter filter) {
    // TODO: Reuse snapshot!
    Map<NlComponent, Dimension> unweightedSizes = Maps.newHashMap();
    XmlTag parentTag = parent.getTag();
    if (parentTag.isValid()) {
      if (parent.getChildCount() == 0) {
        return Collections.emptyMap();
      }
      Map<XmlTag, NlComponent> tagToComponent = Maps.newHashMapWithExpectedSize(parent.getChildCount());
      for (NlComponent child : parent.getChildren()) {
        tagToComponent.put(child.getTag(), child);
      }

      NlModel model = myScreen.getModel();
      XmlFile xmlFile = model.getFile();
      AndroidFacet facet = model.getFacet();
      RenderService renderService = RenderService.get(facet);
      RenderLogger logger = renderService.createLogger();
      final RenderTask task = renderService.createTask(xmlFile, getConfiguration(), logger, null);
      if (task == null) {
        return null;
      }

      // Measure unweighted bounds
      Map<XmlTag, ViewInfo> map = task.measureChildren(parentTag, filter);
      task.dispose();
      if (map != null) {
        for (Map.Entry<XmlTag, ViewInfo> entry : map.entrySet()) {
          ViewInfo viewInfo = entry.getValue();
          viewInfo = RenderService.getSafeBounds(viewInfo);
          Dimension size = new Dimension(viewInfo.getRight() - viewInfo.getLeft(), viewInfo.getBottom() - viewInfo.getTop());
          NlComponent child = tagToComponent.get(entry.getKey());
          if (child != null) {
            unweightedSizes.put(child, size);
          }
        }
      }
    }

    return unweightedSizes;
  }

  @Nullable
  @Override
  public String displayResourceInput(@NotNull String title, @NotNull EnumSet<ResourceType> types) {
    NlModel model = myScreen.getModel();
    ChooseResourceDialog dialog = ChooseResourceDialog.builder()
      .setModule(model.getModule())
      .setTypes(types)
      .setConfiguration(model.getConfiguration())
      .build();

    if (!title.isEmpty()) {
      dialog.setTitle(title);
    }

    dialog.show();

    if (dialog.isOK()) {
      String resource = dialog.getResourceName();

      if (resource != null && !resource.isEmpty()) {
        return resource;
      }
    }

    return null;
  }

  @Nullable
  @Override
  public String displayClassInput(@NotNull Set<String> superTypes,
                                  @Nullable final Predicate<String> filter,
                                  @Nullable String currentValue) {
    Module module = myScreen.getModel().getModule();
    String[] superTypesArray = ArrayUtil.toStringArray(superTypes);

    Condition<PsiClass> psiFilter = null;
    if (filter != null) {
      psiFilter = psiClass -> {
        String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName == null) {
          return false;
        }
        return filter.test(qualifiedName);
      };
    }

    return ChooseClassDialog.openDialog(module, "Classes", true, psiFilter, superTypesArray);
  }

  @NotNull
  public ScreenView getScreenView() {
    return myScreen;
  }
}
