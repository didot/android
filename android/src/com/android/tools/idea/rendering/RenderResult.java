/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.rendering.imagepool.ImagePool;
import com.android.util.PropertiesMap;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class RenderResult {
  @NotNull private final PsiFile myFile;
  @NotNull private final RenderLogger myLogger;
  @NotNull private final ImmutableList<ViewInfo> myRootViews;
  @NotNull private final ImmutableList<ViewInfo> mySystemRootViews;
  @NotNull private final ImagePool.Image myImage;
  @Nullable private final RenderTask myRenderTask;
  @NotNull private final Result myRenderResult;
  @NotNull private final ImmutableMap<Object, PropertiesMap> myDefaultProperties;
  @NotNull private final Module myModule;
  private boolean isDisposed;

  protected RenderResult(@NotNull PsiFile file,
                         @NotNull Module module,
                         @NotNull RenderLogger logger,
                         @Nullable RenderTask renderTask,
                         @NotNull Result renderResult,
                         @NotNull ImmutableList<ViewInfo> rootViews,
                         @NotNull ImmutableList<ViewInfo> systemRootViews,
                         @NotNull ImagePool.Image image,
                         @NotNull ImmutableMap<Object, PropertiesMap> defaultProperties) {
    myRenderTask = renderTask;
    myModule = module;
    myFile = file;
    myLogger = logger;
    myRenderResult = renderResult;
    myRootViews = rootViews;
    mySystemRootViews = systemRootViews;
    myImage = image;
    myDefaultProperties = defaultProperties;
  }

  public void dispose() {
    isDisposed = true;
    myImage.dispose();
  }

  /**
   * Creates a new {@link RenderResult} from a given RenderTask and RenderSession
   */
  @NotNull
  public static RenderResult create(@NotNull RenderTask renderTask,
                                    @NotNull RenderSession session,
                                    @NotNull PsiFile file,
                                    @NotNull RenderLogger logger,
                                    @NotNull ImagePool.Image image) {
    List<ViewInfo> rootViews = session.getRootViews();
    List<ViewInfo> systemRootViews = session.getSystemRootViews();
    Map<Object, PropertiesMap> defaultProperties = session.getDefaultProperties();
    return new RenderResult(
      file,
      renderTask.getContext().getModule(),
      logger,
      renderTask,
      session.getResult(),
      rootViews != null ? ImmutableList.copyOf(rootViews) : ImmutableList.of(),
      systemRootViews != null ? ImmutableList.copyOf(systemRootViews) : ImmutableList.of(),
      image, // image might be ImagePool.NULL_POOL_IMAGE if there is no rendered image (as in layout())
      defaultProperties != null ? ImmutableMap.copyOf(defaultProperties) : ImmutableMap.of());
  }

  /**
   * Creates a new session initialization error {@link RenderResult} from a given RenderTask
   */
  @NotNull
  public static RenderResult createSessionInitializationError(@NotNull RenderTask renderTask,
                                                              @NotNull PsiFile file,
                                                              @NotNull RenderLogger logger,
                                                              @Nullable Throwable throwable) {
    Module module = logger.getModule();
    assert module != null;
    return new RenderResult(
      file,
      module, // do not use renderTask.getModule as a disposed renderTask could be the reason we are here
      logger,
      renderTask,
      Result.Status.ERROR_UNKNOWN.createResult("Failed to initialize session", throwable),
      ImmutableList.of(),
      ImmutableList.of(),
      ImagePool.NULL_POOLED_IMAGE,
      ImmutableMap.of());
  }

  /**
   * Creates a new blank {@link RenderResult}
   *
   * @param file the PSI file the render result corresponds to
   * @return a blank render result
   */
  @NotNull
  public static RenderResult createBlank(@NotNull PsiFile file) {
    Module module = ModuleUtilCore.findModuleForPsiElement(file);
    assert module != null;
    return new RenderResult(
      file,
      module,
      new RenderLogger(null, module),
      null,
      Result.Status.ERROR_UNKNOWN.createResult(""),
      ImmutableList.of(),
      ImmutableList.of(),
      ImagePool.NULL_POOLED_IMAGE,
      ImmutableMap.of());
  }

  @NotNull
  public Result getRenderResult() {
    return myRenderResult;
  }

  @NotNull
  public RenderLogger getLogger() {
    return myLogger;
  }

  @NotNull
  public ImagePool.Image getRenderedImage() {
    return !isDisposed ? myImage : ImagePool.NULL_POOLED_IMAGE;
  }

  public boolean hasImage() {
    return !isDisposed && myImage != ImagePool.NULL_POOLED_IMAGE;
  }

  @NotNull
  public PsiFile getFile() {
    return myFile;
  }

  @Nullable
  public RenderTask getRenderTask() {
    return myRenderTask;
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  @NotNull
  public ImmutableList<ViewInfo> getRootViews() {
    return myRootViews;
  }

  @NotNull
  public ImmutableList<ViewInfo> getSystemRootViews() {
    return mySystemRootViews;
  }

  /**
   * Returns the default properties map. This map contains a list of the widgets default values for every attribute as returned by layoutlib.
   * The map is index by view cookie.
   */
  @NotNull
  public ImmutableMap<Object, PropertiesMap> getDefaultProperties() {
    return myDefaultProperties;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
      .add("renderResult", myRenderResult)
      .add("psiFile", myFile)
      .add("rootViews", myRootViews)
      .add("systemViews", mySystemRootViews)
      .toString();
  }
}
