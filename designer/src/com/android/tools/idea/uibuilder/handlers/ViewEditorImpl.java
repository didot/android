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

import com.android.assetstudiolib.GraphicGenerator;
import com.android.assetstudiolib.MaterialDesignIcons;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.resources.ResourceType;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.res.ModuleResourceRepository;
import com.android.tools.idea.ui.resourcechooser.ChooseResourceDialog;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.SceneView;
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
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.ChooseClassDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;

import static com.android.SdkConstants.*;

/**
 * Implementation of the {@link ViewEditor} abstraction presented
 * to {@link ViewHandler} instances
 */
public class ViewEditorImpl extends ViewEditor {
  private final SceneView mySceneView;

  public ViewEditorImpl(@NotNull SceneView scene) {
    mySceneView = scene;
  }

  @Override
  public int getDpi() {
    return mySceneView.getConfiguration().getDensity().getDpiValue();
  }

  @Nullable
  @Override
  public AndroidVersion getCompileSdkVersion() {
    return AndroidModuleInfo.getInstance(mySceneView.getModel().getFacet()).getBuildSdkVersion();
  }

  @NotNull
  @Override
  public AndroidVersion getMinSdkVersion() {
    return AndroidModuleInfo.getInstance(mySceneView.getModel().getFacet()).getMinSdkVersion();
  }

  @NotNull
  @Override
  public AndroidVersion getTargetSdkVersion() {
    return AndroidModuleInfo.getInstance(mySceneView.getModel().getFacet()).getTargetSdkVersion();
  }

  @NotNull
  @Override
  public Configuration getConfiguration() {
    return mySceneView.getConfiguration();
  }

  @NotNull
  @Override
  public NlModel getModel() {
    return mySceneView.getModel();
  }

  @NotNull
  @Override
  public Collection<ViewInfo> getRootViews() {
    RenderResult result = mySceneView.getModel().getRenderResult();

    if (result == null) {
      return Collections.emptyList();
    }

    return result.getRootViews();
  }

  @Override
  public boolean moduleContainsResource(@NotNull ResourceType type, @NotNull String name) {
    AndroidFacet facet = mySceneView.getModel().getFacet();
    return ModuleResourceRepository.getOrCreateInstance(facet).hasResourceItem(type, name);
  }

  @Override
  public void copyVectorAssetToMainModuleSourceSet(@NotNull String asset) {
    Project project = mySceneView.getModel().getProject();
    String message = "Do you want to copy vector asset " + asset + " to your main module source set?";

    if (Messages.showYesNoDialog(project, message, "Copy Vector Asset", Messages.getQuestionIcon()) == Messages.NO) {
      return;
    }

    try (InputStream in = GraphicGenerator.class.getClassLoader().getResourceAsStream(MaterialDesignIcons.getPathForBasename(asset))) {
      createResourceFile(FD_RES_DRAWABLE, asset + DOT_XML, ByteStreams.toByteArray(in));
    }
    catch (IOException exception) {
      Logger.getInstance(ViewEditorImpl.class).warn(exception);
    }
  }

  @Override
  public void copyLayoutToMainModuleSourceSet(@NotNull String layout, @Language("XML") @NotNull String xml) {
    String message = "Do you want to copy layout " + layout + " to your main module source set?";

    if (Messages.showYesNoDialog(mySceneView.getModel().getProject(), message, "Copy Layout", Messages.getQuestionIcon()) == Messages.NO) {
      return;
    }

    createResourceFile(FD_RES_LAYOUT, layout + DOT_XML, xml.getBytes(StandardCharsets.UTF_8));
  }

  private void createResourceFile(@NotNull String resourceDirectory, @NotNull String resourceFile, @NotNull byte[] resourceFileContent) {
    try {
      VirtualFile directory = getResourceDirectoryChild(resourceDirectory);

      if (directory == null) {
        return;
      }

      directory.createChildData(this, resourceFile).setBinaryContent(resourceFileContent);
    }
    catch (IOException exception) {
      Logger.getInstance(ViewEditorImpl.class).warn(exception);
    }
  }

  @Nullable
  private VirtualFile getResourceDirectoryChild(@NotNull String child) throws IOException {
    VirtualFile resourceDirectory = mySceneView.getModel().getFacet().getPrimaryResourceDir();

    if (resourceDirectory == null) {
      Logger.getInstance(ViewEditorImpl.class).warn("resourceDirectory is null");
      return null;
    }

    VirtualFile resourceDirectoryChild = resourceDirectory.findChild(child);

    if (resourceDirectoryChild == null) {
      return resourceDirectory.createChildDirectory(this, child);
    }

    return resourceDirectoryChild;
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

      NlModel model = mySceneView.getModel();
      XmlFile xmlFile = model.getFile();
      AndroidFacet facet = model.getFacet();
      RenderService renderService = RenderService.getInstance(facet);
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
    NlModel model = mySceneView.getModel();
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
    Module module = mySceneView.getModel().getModule();
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
  public SceneView getSceneView() {
    return mySceneView;
  }
}
