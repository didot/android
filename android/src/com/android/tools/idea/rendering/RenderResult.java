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

import com.android.ide.common.rendering.api.*;
import com.android.tools.idea.rendering.imagepool.ImagePool;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiFile;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class RenderResult {
  private static Logger LOG = Logger.getInstance(RenderResult.class);

  @NotNull private final PsiFile myFile;
  @NotNull private final RenderLogger myLogger;
  @NotNull private final ImmutableList<ViewInfo> myRootViews;
  @NotNull private final ImmutableList<ViewInfo> mySystemRootViews;
  @NotNull private final ImagePool.Image myImage;
  @Nullable private final RenderTask myRenderTask;
  @NotNull private final Result myRenderResult;
  @NotNull private final Map<Object, Map<ResourceReference, ResourceValue>> myDefaultProperties;
  @NotNull private final Map<Object, String> myDefaultStyles;
  @NotNull private final Module myModule;
  private final ReadWriteLock myDisposeLock = new ReentrantReadWriteLock();
  @Nullable private final Object myValidatorResult;
  private boolean isDisposed;

  protected RenderResult(@NotNull PsiFile file,
                         @NotNull Module module,
                         @NotNull RenderLogger logger,
                         @Nullable RenderTask renderTask,
                         @NotNull Result renderResult,
                         @NotNull ImmutableList<ViewInfo> rootViews,
                         @NotNull ImmutableList<ViewInfo> systemRootViews,
                         @NotNull ImagePool.Image image,
                         @NotNull Map<Object, Map<ResourceReference, ResourceValue>> defaultProperties,
                         @NotNull Map<Object, String> defaultStyles,
                         @Nullable Object validatorResult) {
    myRenderTask = renderTask;
    myModule = module;
    myFile = file;
    myLogger = logger;
    myRenderResult = renderResult;
    myRootViews = rootViews;
    mySystemRootViews = systemRootViews;
    myImage = image;
    myDefaultProperties = defaultProperties;
    myDefaultStyles = defaultStyles;
    myValidatorResult = validatorResult;
  }

  public void dispose() {
    myDisposeLock.writeLock().lock();
    try {
      isDisposed = true;
      myImage.dispose();
    } finally {
      myDisposeLock.writeLock().unlock();
    }
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
    Map<Object, Map<ResourceReference, ResourceValue>> defaultProperties = session.getDefaultNamespacedProperties();
    Map<Object, String> defaultStyles = session.getDefaultStyles();
    RenderResult result = new RenderResult(
      file,
      renderTask.getContext().getModule(),
      logger,
      renderTask,
      session.getResult(),
      rootViews != null ? ImmutableList.copyOf(rootViews) : ImmutableList.of(),
      systemRootViews != null ? ImmutableList.copyOf(systemRootViews) : ImmutableList.of(),
      image, // image might be ImagePool.NULL_POOL_IMAGE if there is no rendered image (as in layout())
      defaultProperties != null ? ImmutableMap.copyOf(defaultProperties) : ImmutableMap.of(),
      defaultStyles != null ? ImmutableMap.copyOf(defaultStyles) : ImmutableMap.of(),
      session.getValidationData());

    if (LOG.isDebugEnabled()) {
      LOG.debug(result.toString());
    }

    return result;
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
    RenderResult result = new RenderResult(
      file,
      module, // do not use renderTask.getModule as a disposed renderTask could be the reason we are here
      logger,
      renderTask,
      Result.Status.ERROR_UNKNOWN.createResult("Failed to initialize session", throwable),
      ImmutableList.of(),
      ImmutableList.of(),
      ImagePool.NULL_POOLED_IMAGE,
      ImmutableMap.of(),
      ImmutableMap.of(),
      null);

    if (LOG.isDebugEnabled()) {
      LOG.debug(result.toString());
    }

    return result;
  }

  /**
   * Creates a new blank {@link RenderResult}
   *
   * @param file the PSI file the render result corresponds to
   * @return a blank render result
   */
  @NotNull
  public static RenderResult createBlank(@NotNull PsiFile file) {
    return createErrorResult(file, Result.Status.ERROR_UNKNOWN.createResult(""), null);
  }

  /**
   * Creates a blank {@link RenderResult} to report render task creation errors
   *
   * @param file the PSI file the render result corresponds to
   * @param logger the logger containing the errors to surface to the user
   */
  @NotNull
  public static RenderResult createRenderTaskErrorResult(@NotNull PsiFile file, @NotNull RenderLogger logger) {
    return createErrorResult(file, Result.Status.ERROR_RENDER_TASK.createResult(), logger);
  }

  @NotNull
  private static RenderResult createErrorResult(@NotNull PsiFile file, @NotNull Result errorResult, @Nullable RenderLogger logger) {
    Module module = ModuleUtilCore.findModuleForPsiElement(file);
    assert module != null;
    RenderResult result = new RenderResult(
      file,
      module,
      logger != null ? logger : new RenderLogger(null, module),
      null,
      errorResult,
      ImmutableList.of(),
      ImmutableList.of(),
      ImagePool.NULL_POOLED_IMAGE,
      ImmutableMap.of(),
      ImmutableMap.of(),
      null);

    if (LOG.isDebugEnabled()) {
      LOG.debug(result.toString());
    }

    return result;
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
    myDisposeLock.readLock().lock();
    try {
      return !isDisposed ? myImage : ImagePool.NULL_POOLED_IMAGE;
    } finally {
      myDisposeLock.readLock().unlock();
    }
  }

  public boolean hasImage() {
    myDisposeLock.readLock().lock();
    try {
      return !isDisposed && myImage != ImagePool.NULL_POOLED_IMAGE;
    } finally {
      myDisposeLock.readLock().unlock();
    }
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

  @Nullable
  public Object getValidatorResult() {
    return myValidatorResult;
  }

  /**
   * Returns the default properties map. This map contains a list of the widgets default values for every attribute as returned by layoutlib.
   * The map is index by view cookie.
   */
  @NotNull
  public Map<Object, Map<ResourceReference, ResourceValue>> getDefaultProperties() {
    return myDefaultProperties;
  }

  /**
   * Returns the default style map. This map contains the default style of the widgets as returned by layoutlib.
   * The map is index by view cookie.
   */
  @NotNull
  public Map<Object, String> getDefaultStyles() {
    return myDefaultStyles;
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
