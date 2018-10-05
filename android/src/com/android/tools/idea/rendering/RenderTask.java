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

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.HardwareConfigHelper;
import com.android.ide.common.rendering.api.*;
import com.android.ide.common.rendering.api.SessionParams.RenderingMode;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.LayoutDirectionQualifier;
import com.android.ide.common.util.PathString;
import com.android.resources.LayoutDirection;
import com.android.resources.ResourceFolderType;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.tools.analytics.crash.CrashReporter;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.diagnostics.crash.StudioExceptionReport;
import com.android.tools.idea.layoutlib.LayoutLibrary;
import com.android.tools.idea.layoutlib.RenderParamsFlags;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.model.MergedManifest.ActivityAttributes;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.android.tools.idea.rendering.imagepool.ImagePool;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import com.android.tools.idea.rendering.parsers.ILayoutPullParserFactory;
import com.android.tools.idea.rendering.parsers.LayoutFilePullParser;
import com.android.tools.idea.rendering.parsers.LayoutPsiPullParser;
import com.android.tools.idea.rendering.parsers.LayoutPullParsers;
import com.android.tools.idea.res.*;
import com.android.tools.idea.util.DependencyManagementUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.lang.annotation.HighlightSeverity.ERROR;

/**
 * The {@link RenderTask} provides rendering and layout information for
 * Android layouts. This is a wrapper around the layout library.
 */
public class RenderTask {
  private static final Logger LOG = Logger.getInstance(RenderTask.class);

  /**
   * {@link IImageFactory} that returns a new image exactly of the requested size. It does not do caching or resizing.
   */
  private static final IImageFactory SIMPLE_IMAGE_FACTORY = new IImageFactory() {
    @Override
    public BufferedImage getImage(int width, int height) {
      @SuppressWarnings("UndesirableClassUsage")
      BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      image.setAccelerationPriority(1f);

      return image;
    }
  };

  /**
   * Minimum downscaling factor used. The quality can go from [0, 1] but that setting is actually mapped into [MIN_DOWNSCALING_FACTOR, 1]
   * since below MIN_DOWNSCALING_FACTOR the quality is not good enough.
   */
  private static final float MIN_DOWNSCALING_FACTOR = .7f;
  /**
   * When quality < 1.0, the max allowed size for the rendering is DOWNSCALED_IMAGE_MAX_BYTES * downscalingFactor
   */
  private static final int DOWNSCALED_IMAGE_MAX_BYTES = 2_500_000; // 2.5MB

  @NotNull private final ImagePool myImagePool;
  @NotNull private final RenderTaskContext myContext;
  @NotNull private final RenderLogger myLogger;
  @NotNull private final LayoutlibCallbackImpl myLayoutlibCallback;
  @NotNull private final LayoutLibrary myLayoutLib;
  @NotNull private final HardwareConfigHelper myHardwareConfigHelper;
  private final float myDefaultQuality;
  @Nullable private IncludeReference myIncludedWithin;
  @NotNull private RenderingMode myRenderingMode = RenderingMode.NORMAL;
  @Nullable private Integer myOverrideBgColor;
  private boolean myShowDecorations = true;
  @NotNull private final AssetRepositoryImpl myAssetRepository;
  private long myTimeout;
  @NotNull private final Locale myLocale;
  @NotNull private final Object myCredential;
  private boolean myProvideCookiesForIncludedViews = false;
  @Nullable private RenderSession myRenderSession;
  @NotNull private IImageFactory myCachingImageFactory;
  @Nullable private IImageFactory myImageFactoryDelegate;
  private final boolean isSecurityManagerEnabled;
  @NotNull private CrashReporter myCrashReporter;
  private final List<ListenableFuture<?>> myRunningFutures = new LinkedList<>();
  @NotNull private final AtomicBoolean isDisposed = new AtomicBoolean(false);
  @Nullable private XmlFile myXmlFile;

  /**
   * Don't create this task directly; obtain via {@link RenderService}
   *
   * @param quality Factor from 0 to 1 used to downscale the rendered image. A lower value means smaller images used
   *                during rendering at the expense of quality. 1 means that downscaling is disabled.
   */
  RenderTask(@NotNull AndroidFacet facet,
             @NotNull RenderService renderService,
             @NotNull Configuration configuration,
             @NotNull RenderLogger logger,
             @NotNull LayoutLibrary layoutLib,
             @NotNull Device device,
             @NotNull Object credential,
             @NotNull CrashReporter crashReporter,
             @NotNull ImagePool imagePool,
             @Nullable ILayoutPullParserFactory parserFactory,
             boolean isSecurityManagerEnabled,
             float quality) {
    this.isSecurityManagerEnabled = isSecurityManagerEnabled;

    if (!isSecurityManagerEnabled) {
      LOG.debug("Security manager was disabled");
    }

    myLogger = logger;
    myCredential = credential;
    myCrashReporter = crashReporter;
    myImagePool = imagePool;
    myAssetRepository = new AssetRepositoryImpl(facet);
    myHardwareConfigHelper = new HardwareConfigHelper(device);

    ScreenOrientation orientation = configuration.getFullConfig().getScreenOrientationQualifier() != null ?
                                    configuration.getFullConfig().getScreenOrientationQualifier().getValue() :
                                    ScreenOrientation.PORTRAIT;
    myHardwareConfigHelper.setOrientation(orientation);
    myLayoutLib = layoutLib;
    LocalResourceRepository appResources = ResourceRepositoryManager.getAppResources(facet);
    ActionBarHandler actionBarHandler = new ActionBarHandler(this, myCredential);
    Module module = facet.getModule();
    myLayoutlibCallback =
        new LayoutlibCallbackImpl(this, myLayoutLib, appResources, module, facet, myLogger, myCredential, actionBarHandler, parserFactory);
    if (ResourceIdManager.get(module).getFinalIdsUsed()) {
      myLayoutlibCallback.loadAndParseRClass();
    }
    AndroidModuleInfo moduleInfo = AndroidModuleInfo.getInstance(facet);
    myLocale = configuration.getLocale();
    myContext = new RenderTaskContext(module.getProject(),
                                      module,
                                      configuration,
                                      moduleInfo,
                                      renderService.getPlatform(facet));
    myDefaultQuality = quality;
    restoreDefaultQuality();
  }

  public void setQuality(float quality) {
    if (quality >= 1.f) {
      myCachingImageFactory = SIMPLE_IMAGE_FACTORY;
      return;
    }

    float actualSamplingFactor = MIN_DOWNSCALING_FACTOR + Math.max(Math.min(quality, 1f), 0f) * (1f - MIN_DOWNSCALING_FACTOR);
    long maxSize = (long)((float)DOWNSCALED_IMAGE_MAX_BYTES * actualSamplingFactor);
    myCachingImageFactory = new CachingImageFactory(((width, height) -> {
      int downscaleWidth = width;
      int downscaleHeight = height;
      int size = width * height;
      if (size > maxSize) {
        double scale = maxSize / (double)size;
        downscaleWidth *= scale;
        downscaleHeight *= scale;
      }

      return SIMPLE_IMAGE_FACTORY.getImage(downscaleWidth, downscaleHeight);
    }));
  }

  public void restoreDefaultQuality() {
    setQuality(myDefaultQuality);
  }

  public void setXmlFile(@NotNull XmlFile file) {
    myXmlFile = file;
    ReadAction.run(() -> getContext().setFolderType(ResourceHelper.getFolderType(file)));
  }

  @Nullable
  public XmlFile getXmlFile() {
    return myXmlFile;
  }

  @NotNull
  public IRenderLogger getLogger() {
    return myLogger;
  }

  @NotNull
  public HardwareConfigHelper getHardwareConfigHelper() {
    return myHardwareConfigHelper;
  }

  public boolean getShowDecorations() {
    return myShowDecorations;
  }

  public boolean isDisposed() {
    return isDisposed.get();
  }

  /**
   * Disposes the RenderTask and releases the allocated resources. The execution of the dispose operation will run asynchronously.
   * The returned {@link Future} can be used to wait for the dispose operation to complete.
   */
  public Future<?> dispose() {
    if (isDisposed.getAndSet(true)) {
      assert false : "RenderTask was already disposed";
      return Futures.immediateFailedFuture(new IllegalStateException("RenderTask was already disposed"));
    }

    FutureTask<Void> disposeTask = new FutureTask<>(() -> {
      try {
        ImmutableList<ListenableFuture<?>> currentRunningFutures;
        synchronized (myRunningFutures) {
          currentRunningFutures = ImmutableList.copyOf(myRunningFutures);
          myRunningFutures.clear();
        }
        // Wait for all current running operations to complete
        Futures.successfulAsList(currentRunningFutures).get(5, TimeUnit.SECONDS);
      }
      catch (InterruptedException | ExecutionException e) {
        // We do not care about these exceptions since we are disposing the task anyway
        LOG.debug(e);
      }
      myLayoutlibCallback.setLogger(IRenderLogger.NULL_LOGGER);
      myLayoutlibCallback.setResourceResolver(null);
      if (myRenderSession != null) {
        try {
          RenderService.runAsyncRenderAction(myRenderSession::dispose);
          myRenderSession = null;
        }
        catch (Exception ignored) {
        }
      }
      myImageFactoryDelegate = null;

      return null;
    });

    new Thread(disposeTask, "RenderTask dispose thread").start();
    return disposeTask;
  }

  /**
   * Overrides the width and height to be used during rendering (which might be adjusted if
   * the {@link #setRenderingMode(RenderingMode)} is {@link RenderingMode#FULL_EXPAND}.
   * <p/>
   * A value of -1 will make the rendering use the normal width and height coming from the
   * {@link Configuration#getDevice()} object.
   *
   * @param overrideRenderWidth  the width in pixels of the layout to be rendered
   * @param overrideRenderHeight the height in pixels of the layout to be rendered
   * @return this (such that chains of setters can be stringed together)
   */
  @SuppressWarnings("UnusedReturnValue")
  @NotNull
  public RenderTask setOverrideRenderSize(int overrideRenderWidth, int overrideRenderHeight) {
    myHardwareConfigHelper.setOverrideRenderSize(overrideRenderWidth, overrideRenderHeight);
    return this;
  }

  /**
   * Sets the max width and height to be used during rendering (which might be adjusted if
   * the {@link #setRenderingMode(RenderingMode)} is {@link RenderingMode#FULL_EXPAND}.
   * <p/>
   * A value of -1 will make the rendering use the normal width and height coming from the
   * {@link Configuration#getDevice()} object.
   *
   * @param maxRenderWidth  the max width in pixels of the layout to be rendered
   * @param maxRenderHeight the max height in pixels of the layout to be rendered
   * @return this (such that chains of setters can be stringed together)
   */
  @SuppressWarnings("UnusedReturnValue")
  @NotNull
  public RenderTask setMaxRenderSize(int maxRenderWidth, int maxRenderHeight) {
    myHardwareConfigHelper.setMaxRenderSize(maxRenderWidth, maxRenderHeight);
    return this;
  }

  /**
   * Sets the {@link RenderingMode} to be used during rendering. If none is specified, the default is
   * {@link RenderingMode#NORMAL}.
   *
   * @param renderingMode the rendering mode to be used
   * @return this (such that chains of setters can be stringed together)
   */
  @SuppressWarnings("UnusedReturnValue")
  @NotNull
  public RenderTask setRenderingMode(@NotNull RenderingMode renderingMode) {
    myRenderingMode = renderingMode;
    return this;
  }

  @SuppressWarnings("UnusedReturnValue")
  @NotNull
  public RenderTask setTimeout(long timeout) {
    myTimeout = timeout;
    return this;
  }

  /**
   * Sets the overriding background color to be used, if any. The color should be a bitmask of AARRGGBB.
   * The default is null.
   *
   * @param overrideBgColor the overriding background color to be used in the rendering,
   *                        in the form of a AARRGGBB bitmask, or null to use no custom background.
   * @return this (such that chains of setters can be stringed together)
   */
  @SuppressWarnings("UnusedReturnValue")
  @NotNull
  public RenderTask setOverrideBgColor(@Nullable Integer overrideBgColor) {
    myOverrideBgColor = overrideBgColor;
    return this;
  }

  /**
   * Sets whether the rendering should include decorations such as a system bar, an
   * application bar etc depending on the SDK target and theme. The default is true.
   *
   * @param showDecorations true if the rendering should include system bars etc.
   * @return this (such that chains of setters can be stringed together)
   */
  @SuppressWarnings("UnusedReturnValue")
  @NotNull
  public RenderTask setDecorations(boolean showDecorations) {
    myShowDecorations = showDecorations;
    return this;
  }

  /** Returns whether this parser will provide view cookies for included views. */
  public boolean getProvideCookiesForIncludedViews() {
    return myProvideCookiesForIncludedViews;
  }

  /**
   * Renders the model and returns the result as a {@link RenderSession}.
   *
   * @param factory Factory for images which would be used to render layouts to.
   * @return the {@link RenderResult resulting from rendering the current model
   */
  @Nullable
  private RenderResult createRenderSession(@NotNull IImageFactory factory) {
    PsiFile psiFile = getXmlFile();
    if (psiFile == null) {
      throw new IllegalStateException("createRenderSession shouldn't be called on RenderTask without PsiFile");
    }
    if (isDisposed.get()) {
      return null;
    }

    ResourceResolver resolver = ResourceResolver.copy(getContext().getConfiguration().getResourceResolver());
    if (resolver == null) {
      // Abort the rendering if the resources are not found.
      return null;
    }

    ILayoutPullParser modelParser = LayoutPullParsers.create(this);
    if (modelParser == null) {
      return null;
    }

    myLayoutlibCallback.reset();

    if (modelParser instanceof LayoutPsiPullParser) {
      // For regular layouts, if we use appcompat, we have to emulat the app:srcCompat attribute behaviour.
      boolean useSrcCompat = DependencyManagementUtil.dependsOn(getContext().getModule(), GoogleMavenArtifactId.APP_COMPAT_V7) ||
                             DependencyManagementUtil.dependsOn(getContext().getModule(), GoogleMavenArtifactId.ANDROIDX_APP_COMPAT_V7);
      ((LayoutPsiPullParser)modelParser).setUseSrcCompat(useSrcCompat);
      myLayoutlibCallback.setAaptDeclaredResources(((LayoutPsiPullParser)modelParser).getAaptDeclaredAttrs());
    }


    ILayoutPullParser includingParser = getIncludingLayoutParser(resolver, modelParser);
    if (includingParser != null) {
      modelParser = includingParser;
    }

    RenderTaskContext context = getContext();
    IAndroidTarget target = context.getConfiguration().getTarget();
    int simulatedPlatform = target instanceof CompatibilityRenderTarget ? target.getVersion().getApiLevel() : 0;

    Module module = context.getModule();
    HardwareConfig hardwareConfig = myHardwareConfigHelper.getConfig();
    SessionParams params =
        new SessionParams(modelParser, myRenderingMode, module /* projectKey */, hardwareConfig, resolver,
                          myLayoutlibCallback, context.getMinSdkVersion().getApiLevel(), context.getTargetSdkVersion().getApiLevel(),
                          myLogger, simulatedPlatform);
    params.setAssetRepository(myAssetRepository);

    params.setFlag(RenderParamsFlags.FLAG_KEY_ROOT_TAG, AndroidPsiUtils.getRootTagName(psiFile));
    params.setFlag(RenderParamsFlags.FLAG_KEY_RECYCLER_VIEW_SUPPORT, true);
    params.setFlag(RenderParamsFlags.FLAG_KEY_DISABLE_BITMAP_CACHING, true);
    params.setFlag(RenderParamsFlags.FLAG_DO_NOT_RENDER_ON_CREATE, true);
    params.setFlag(RenderParamsFlags.FLAG_KEY_RESULT_IMAGE_AUTO_SCALE, true);

    // Request margin and baseline information.
    // TODO: Be smarter about setting this; start without it, and on the first request
    // for an extended view info, re-render in the same session, and then set a flag
    // which will cause this to create extended view info each time from then on in the
    // same session.
    params.setExtendedViewInfoMode(true);

    MergedManifest manifestInfo = MergedManifest.get(module);

    Configuration configuration = context.getConfiguration();
    LayoutDirectionQualifier qualifier = configuration.getFullConfig().getLayoutDirectionQualifier();
    if (qualifier != null && qualifier.getValue() == LayoutDirection.RTL && !getLayoutLib().isRtl(myLocale.toLocaleId())) {
      // We don't have a flag to force RTL regardless of locale, so just pick a RTL locale (note that
      // this is decoupled from resource lookup)
      params.setLocale("ur");
    } else {
      params.setLocale(myLocale.toLocaleId());
    }
    try {
      params.setRtlSupport(manifestInfo.isRtlSupported());
    } catch (Exception e) {
      // ignore.
    }

    // Don't show navigation buttons on older platforms.
    Device device = configuration.getDevice();
    if (!myShowDecorations || HardwareConfigHelper.isWear(device)) {
      params.setForceNoDecor();
    }
    else {
      try {
        ResourceValue appLabel = manifestInfo.getApplicationLabel();
        params.setAppIcon(manifestInfo.getApplicationIcon());
        String activity = configuration.getActivity();
        if (activity != null) {
          params.setActivityName(activity);
          ActivityAttributes attributes = manifestInfo.getActivityAttributes(activity);
          if (attributes != null) {
            if (attributes.getLabel() != null) {
              appLabel = attributes.getLabel();
            }
            if (attributes.getIcon() != null) {
              params.setAppIcon(attributes.getIcon());
            }
          }
        }
        params.setAppLabel(params.getResources().resolveResValue(appLabel).getValue());
      }
      catch (Exception ignored) {
      }
    }

    if (myOverrideBgColor != null) {
      params.setOverrideBgColor(myOverrideBgColor.intValue());
    } else if (requiresTransparency()) {
      params.setOverrideBgColor(0);
    }

    params.setImageFactory(factory);

    if (myTimeout > 0) {
      params.setTimeout(myTimeout);
    }

    try {
      myLayoutlibCallback.setLogger(myLogger);
      myLayoutlibCallback.setResourceResolver(resolver);

      RenderSecurityManager securityManager =
          isSecurityManagerEnabled ? RenderSecurityManagerFactory.create(module, getContext().getPlatform()) : null;
      if (securityManager != null) {
        securityManager.setActive(true, myCredential);
      }

      try {
        RenderSession session = myLayoutLib.createSession(params);

        if (session.getResult().isSuccess()) {
          long now = System.nanoTime();
          session.setSystemBootTimeNanos(now);
          session.setSystemTimeNanos(now);
          // Advance the frame time to display the material progress bars
          session.setElapsedFrameTimeNanos(TimeUnit.MILLISECONDS.toNanos(500));
        }
        RenderResult result = RenderResult.create(this, session, psiFile, myLogger, myImagePool.copyOf(session.getImage()));
        myRenderSession = session;
        addDiagnostics(result.getRenderResult());
        return result;
      }
      finally {
        if (securityManager != null) {
          securityManager.dispose(myCredential);
        }
      }
    }
    catch (RuntimeException t) {
      // Exceptions from the bridge
      myLogger.error(null, t.getLocalizedMessage(), t, null, null);
      throw t;
    }
  }

  @Nullable
  private ILayoutPullParser getIncludingLayoutParser(RenderResources resolver, ILayoutPullParser modelParser) {
    XmlFile xmlFile = getXmlFile();
    if (xmlFile == null) {
      throw new IllegalStateException("getIncludingLayoutParser shouldn't be called on RenderTask without PsiFile");
    }

    // Code to support editing included layout.
    if (myIncludedWithin == null) {
      String layout = IncludeReference.getIncludingLayout(xmlFile);
      Module module = getContext().getModule();
      myIncludedWithin = layout != null ? IncludeReference.get(module, xmlFile, resolver) : IncludeReference.NONE;
    }

    ILayoutPullParser topParser = null;
    if (myIncludedWithin != IncludeReference.NONE) {
      assert Comparing.equal(myIncludedWithin.getToFile(), xmlFile.getVirtualFile());
      // TODO: Validate that we're really including the same layout here!
      //ResourceValue contextLayout = resolver.findResValue(myIncludedWithin.getFromResourceUrl(), false  /* forceFrameworkOnly*/);
      //if (contextLayout != null) {
      //  File layoutFile = new File(contextLayout.getValue());
      //  if (layoutFile.isFile()) {
      //
      VirtualFile layoutVirtualFile = myIncludedWithin.getFromFile();

      // Get the name of the layout actually being edited, without the extension
      // as it's what IXmlPullParser.getParser(String) will receive.
      String queryLayoutName = ResourceHelper.getResourceName(xmlFile);
      myLayoutlibCallback.setLayoutParser(queryLayoutName, modelParser);

      // Attempt to read from PSI.
      PsiFile psiFile = AndroidPsiUtils.getPsiFileSafely(getContext().getProject(), layoutVirtualFile);
      if (psiFile instanceof XmlFile) {
        LayoutPsiPullParser parser = LayoutPsiPullParser.create((XmlFile)psiFile, myLogger);
        // For included layouts, we don't normally see view cookies; we want the leaf to point back to the include tag
        parser.setProvideViewCookies(myProvideCookiesForIncludedViews);
        topParser = parser;
      } else {
        // TODO(namespaces, b/74003372): figure out where to get the namespace from.
        topParser = LayoutFilePullParser.create(new PathString(myIncludedWithin.getFromPath()), ResourceNamespace.TODO());
        if (topParser == null) {
          myLogger.error(null, String.format("Could not read layout file %1$s", myIncludedWithin.getFromPath()), null, null, null);
        }
      }
    }

    return topParser;
  }

  /**
   * Executes the passed {@link Callable} as an async render action and keeps track of it. If {@link #dispose()} is called, the call will
   * wait until all the async actions have finished running.
   * See {@link RenderService#runAsyncRenderAction(Callable)}.
   */
  @VisibleForTesting
  @NotNull
  <V> ListenableFuture<V> runAsyncRenderAction(@NotNull Callable<V> callable) {
    if (isDisposed.get()) {
      return Futures.immediateFailedFuture(new IllegalStateException("RenderTask was already disposed"));
    }

    synchronized (myRunningFutures) {
      ListenableFuture<V> newFuture = RenderService.runAsyncRenderAction(callable);
      Futures.addCallback(newFuture, new FutureCallback<V>() {
        @Override
        public void onSuccess(@Nullable V result) {
          synchronized (myRunningFutures) {
            myRunningFutures.remove(newFuture);
          }
        }

        @Override
        public void onFailure(@Nullable Throwable ignored) {
          synchronized (myRunningFutures) {
            myRunningFutures.remove(newFuture);
          }
        }
      });
      myRunningFutures.add(newFuture);

      return newFuture;
    }
  }

  /**
   * Inflates the layout but does not render it.
   * @return A {@link RenderResult} with the result of inflating the inflate call. The result might not contain a result bitmap.
   */
  @Nullable
  public RenderResult inflate() {
    // During development only:
    //assert !ApplicationManager.getApplication().isReadAccessAllowed() : "Do not hold read lock during inflate!";

    XmlFile xmlFile = getXmlFile();
    if (xmlFile == null) {
      throw new IllegalStateException("inflate shouldn't be called on RenderTask without PsiFile");
    }
    if (xmlFile.getProject().isDisposed()) {
      return null;
    }

    try {
      return runAsyncRenderAction(() -> createRenderSession((width, height) -> {
        if (xmlFile.getProject().isDisposed()) {
          return null;
        }
        if (myImageFactoryDelegate != null) {
          return myImageFactoryDelegate.getImage(width, height);
        }

        //noinspection UndesirableClassUsage
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      })).get();
    }
    catch (Exception e) {
      String message = e.getMessage();
      if (message == null) {
        message = e.toString();
      }
      myLogger.addMessage(RenderProblem.createPlain(ERROR, message, myLogger.getProject(), myLogger.getLinkManager(), e));
      return RenderResult.createSessionInitializationError(this, xmlFile, myLogger, e);
    }
  }

  /**
   * Only do a measure pass using the current render session
   */
  @NotNull
  public ListenableFuture<RenderResult> layout() {
    if (myRenderSession == null) {
      return Futures.immediateFuture(null);
    }

    assert getXmlFile() != null;
    try {
      // runAsyncRenderAction might not run immediately so we need to capture the current myRenderSession and myPsiFile values
      RenderSession renderSession = myRenderSession;
      PsiFile psiFile = getXmlFile();
      return runAsyncRenderAction(() -> {
        myRenderSession.measure();
        return RenderResult.create(this, renderSession, psiFile, myLogger, ImagePool.NULL_POOLED_IMAGE);
      });
    }
    catch (Exception e) {
      // nothing
    }
    return Futures.immediateFuture(null);
  }

  /**
   * Method used to report unhandled layoutlib exceptions to the crash reporter
   */
  private void reportException(@NotNull Throwable e) {
    // This in an unhandled layoutlib exception, pass it to the crash reporter
    myCrashReporter.submit(new StudioExceptionReport.Builder().setThrowable(e, false).build());
  }

  /**
   * Renders the layout to the current {@link IImageFactory} set in {@link #myImageFactoryDelegate}
   */
  @NotNull
  private ListenableFuture<RenderResult> renderInner() {
    // During development only:
    //assert !ApplicationManager.getApplication().isReadAccessAllowed() : "Do not hold read lock during render!";

    if (myRenderSession == null) {
      RenderResult renderResult = inflate();
      Result result = renderResult != null ? renderResult.getRenderResult() : null;
      if (result == null || !result.isSuccess()) {
        if (result != null) {
          if (result.getException() != null) {
            reportException(result.getException());
          }
          myLogger.error(null, result.getErrorMessage(), result.getException(), null, null);
        }
        return Futures.immediateFuture(renderResult);
      }
    }

    PsiFile psiFile = getXmlFile();
    assert psiFile != null;
    try {
      return runAsyncRenderAction(() -> {
        myRenderSession.render();
        RenderResult result =
          RenderResult.create(this, myRenderSession, psiFile, myLogger, myImagePool.copyOf(myRenderSession.getImage()));
        Result renderResult = result.getRenderResult();
        if (renderResult.getException() != null) {
          reportException(renderResult.getException());
          myLogger.error(null, renderResult.getErrorMessage(), renderResult.getException(), null, null);
        }
        return result;
      });
    }
    catch (Exception e) {
      reportException(e);
      String message = e.getMessage();
      if (message == null) {
        message = e.toString();
      }
      myLogger.addMessage(RenderProblem.createPlain(ERROR, message, myLogger.getProject(), myLogger.getLinkManager(), e));
      return Futures.immediateFuture(RenderResult.createSessionInitializationError(this, psiFile, myLogger, e));
    }
  }

  /**
   * Method that renders the layout to a bitmap using the given {@link IImageFactory}. This render call will render the image to a
   * bitmap that can be accessed via the returned {@link RenderResult}.
   * <p/>
   * If {@link #inflate()} hasn't been called before, this method will implicitly call it.
   */
  @NotNull
  ListenableFuture<RenderResult> render(@NotNull IImageFactory factory) {
    myImageFactoryDelegate = factory;

    return renderInner();
  }

  /**
   * Run rendering with default IImageFactory implementation provided by RenderTask. This render call will render the image to a bitmap
   * that can be accessed via the returned {@link RenderResult}
   * <p/>
   * If {@link #inflate()} hasn't been called before, this method will implicitly call it.
   */
  @NotNull
  public ListenableFuture<RenderResult> render() {
    return render(myCachingImageFactory);
  }

  /**
   * Sets the time for which the next frame will be selected. The time is the elapsed time from
   * the current system nanos time.
   */
  public void setElapsedFrameTimeNanos(long nanos) {
    if (myRenderSession != null) {
      myRenderSession.setElapsedFrameTimeNanos(nanos);
    }
  }

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  private void addDiagnostics(@NotNull Result result) {
    if (!myLogger.hasProblems() && !result.isSuccess()) {
      if (result.getException() != null || result.getErrorMessage() != null) {
        myLogger.error(null, result.getErrorMessage(), result.getException(), null, null);
      } else if (result.getStatus() == Result.Status.ERROR_TIMEOUT) {
        myLogger.error(null, "Rendering timed out.", null, null, null);
      } else {
        myLogger.error(null, "Unknown render problem: " + result.getStatus(), null, null, null);
      }
    } else if (myIncludedWithin != null && myIncludedWithin != IncludeReference.NONE) {
      ILayoutPullParser layoutEmbeddedParser = myLayoutlibCallback.getLayoutEmbeddedParser();
      if (layoutEmbeddedParser != null) {  // Should have been nulled out if used
        myLogger.error(null, String.format("The surrounding layout (%1$s) did not actually include this layout. " +
                                           "Remove tools:" + SdkConstants.ATTR_SHOW_IN + "=... from the root tag.",
                                           myIncludedWithin.getFromResourceUrl()), null, null, null);
      }
    }
  }

  /**
   * Asynchronously renders the given resource value (which should refer to a drawable)
   * and returns it as an image.
   *
   * @param drawableResourceValue the drawable resource value to be rendered, or null
   * @return a {@link ListenableFuture} with the BufferedImage of the passed drawable.
   */
  @NotNull
  public ListenableFuture<BufferedImage> renderDrawable(ResourceValue drawableResourceValue) {
    if (drawableResourceValue == null) {
      return Futures.immediateFuture(null);
    }

    HardwareConfig hardwareConfig = myHardwareConfigHelper.getConfig();

    RenderTaskContext context = getContext();
    Module module = getContext().getModule();
    DrawableParams params =
        new DrawableParams(drawableResourceValue, module, hardwareConfig, context.getConfiguration().getResourceResolver(),
                           myLayoutlibCallback, context.getMinSdkVersion().getApiLevel(), context.getTargetSdkVersion().getApiLevel(),
                           myLogger);
    params.setForceNoDecor();
    params.setAssetRepository(myAssetRepository);

    ListenableFuture<Result> futureResult = runAsyncRenderAction(() -> myLayoutLib.renderDrawable(params));
    return Futures.transform(futureResult, result -> {
      if (result != null && result.isSuccess()) {
        Object data = result.getData();
        if (data instanceof BufferedImage) {
          return (BufferedImage)data;
        }
      }

      return null;
    });
  }

  /**
   * Renders the given resource value (which should refer to a drawable) and returns it
   * as an image
   *
   * @param drawableResourceValue the drawable resource value to be rendered, or null
   * @return the image, or null if something went wrong
   */
  @NotNull
  @SuppressWarnings("unchecked")
  public List<BufferedImage> renderDrawableAllStates(@Nullable ResourceValue drawableResourceValue) {
    if (drawableResourceValue == null) {
      return Collections.emptyList();
    }

    HardwareConfig hardwareConfig = myHardwareConfigHelper.getConfig();

    RenderTaskContext context = getContext();
    Module module = context.getModule();
    DrawableParams params =
        new DrawableParams(drawableResourceValue, module, hardwareConfig, context.getConfiguration().getResourceResolver(),
                           myLayoutlibCallback, context.getMinSdkVersion().getApiLevel(), context.getTargetSdkVersion().getApiLevel(),
                           myLogger);
    params.setForceNoDecor();
    params.setAssetRepository(myAssetRepository);
    boolean supportsMultipleStates = myLayoutLib.supports(Features.RENDER_ALL_DRAWABLE_STATES);
    if (supportsMultipleStates) {
      params.setFlag(RenderParamsFlags.FLAG_KEY_RENDER_ALL_DRAWABLE_STATES, Boolean.TRUE);
    }

    try {
      Result result = RenderService.runRenderAction(() -> myLayoutLib.renderDrawable(params));

      if (result != null && result.isSuccess()) {
        Object data = result.getData();
        if (supportsMultipleStates && data instanceof List) {
          return (List<BufferedImage>)data;
        } else if (!supportsMultipleStates && data instanceof BufferedImage) {
          return Collections.singletonList((BufferedImage) data);
        }
      }
    }
    catch (Exception e) {
      // ignore
    }

    return Collections.emptyList();
  }

  @NotNull
  private LayoutLibrary getLayoutLib() {
    return myLayoutLib;
  }

  @NotNull
  public LayoutlibCallbackImpl getLayoutlibCallback() {
    return myLayoutlibCallback;
  }

  public boolean supportsCapability(@MagicConstant(flagsFromClass = Features.class) int capability) {
    return myLayoutLib.supports(capability);
  }

  /** Returns true if this service can render a non-rectangular shape */
  private boolean isNonRectangular() {
    ResourceFolderType folderType = getContext().getFolderType();
    // Drawable images can have non-rectangular shapes; we need to ensure that we blank out the
    // background with full alpha
    return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
  }

  /** Returns true if this service requires rendering into a transparent/alpha channel image */
  private boolean requiresTransparency() {
    // Drawable images can have non-rectangular shapes; we need to ensure that we blank out the
    // background with full alpha
    return isNonRectangular();
  }

  /**
   * Measure the children of the given parent tag, applying the given filter to the pull parser's
   * attribute values.
   *
   * @param parent the parent tag to measure children for
   * @param filter the filter to apply to the attribute values
   * @return a map from the children of the parent to new bounds of the children
   */
  @Nullable
  public Map<XmlTag, ViewInfo> measureChildren(@NotNull XmlTag parent, @Nullable AttributeFilter filter) {
    ILayoutPullParser modelParser = LayoutPsiPullParser.create(filter, parent, myLogger);
    Map<XmlTag, ViewInfo> map = new HashMap<>();
    RenderSession session = null;
    try {
      session = RenderService.runRenderAction(() -> measure(modelParser));
    }
    catch (Exception ignored) {
    }
    if (session != null) {
      try {
        Result result = session.getResult();

        if (result != null && result.isSuccess()) {
          assert session.getRootViews().size() == 1;
          ViewInfo root = session.getRootViews().get(0);
          List<ViewInfo> children = root.getChildren();
          for (ViewInfo info : children) {
            XmlTag tag = RenderService.getXmlTag(info);
            if (tag != null) {
              map.put(tag, info);
            }
          }
        }

        return map;
      } finally {
        RenderService.runAsyncRenderAction(session::dispose);
      }
    }

    return null;
  }

  /**
   * Measure the given child in context, applying the given filter to the
   * pull parser's attribute values.
   *
   * @param tag the child to measure
   * @param filter the filter to apply to the attribute values
   * @return a view info, if found
   */
  @Nullable
  public ViewInfo measureChild(@NotNull XmlTag tag, @Nullable AttributeFilter filter) {
    XmlTag parent = tag.getParentTag();
    if (parent != null) {
      Map<XmlTag, ViewInfo> map = measureChildren(parent, filter);
      if (map != null) {
        for (Map.Entry<XmlTag, ViewInfo> entry : map.entrySet()) {
          if (entry.getKey() == tag) {
            return entry.getValue();
          }
        }
      }
    }

    return null;
  }

  @Nullable
  private RenderSession measure(ILayoutPullParser parser) {
    RenderTaskContext context = getContext();
    ResourceResolver resolver = context.getConfiguration().getResourceResolver();
    if (resolver == null) {
      // Abort the rendering if the resources are not found.
      return null;
    }

    myLayoutlibCallback.reset();

    HardwareConfig hardwareConfig = myHardwareConfigHelper.getConfig();
    Module module = getContext().getModule();
    SessionParams params = new SessionParams(parser,
                                             RenderingMode.NORMAL,
                                             module /* projectKey */,
                                             hardwareConfig,
                                             resolver,
                                             myLayoutlibCallback,
                                             context.getMinSdkVersion().getApiLevel(),
                                             context.getTargetSdkVersion().getApiLevel(),
                                             myLogger);
    //noinspection deprecation We want to measure while creating the session. RenderSession.measure would require a second call.
    params.setLayoutOnly();
    params.setForceNoDecor();
    params.setExtendedViewInfoMode(true);
    params.setLocale(myLocale.toLocaleId());
    params.setAssetRepository(myAssetRepository);
    params.setFlag(RenderParamsFlags.FLAG_KEY_RECYCLER_VIEW_SUPPORT, true);
    MergedManifest manifestInfo = MergedManifest.get(module);
    try {
      params.setRtlSupport(manifestInfo.isRtlSupported());
    } catch (Exception ignore) {
    }

    try {
      myLayoutlibCallback.setLogger(myLogger);
      myLayoutlibCallback.setResourceResolver(resolver);

      return myLayoutLib.createSession(params);
    }
    catch (RuntimeException t) {
      // Exceptions from the bridge.
      myLogger.error(null, t.getLocalizedMessage(), t, null, null);
      throw t;
    }
  }

  @VisibleForTesting
  void setCrashReporter(@NotNull CrashReporter crashReporter) {
    myCrashReporter = crashReporter;
  }

  /**
   * Returns the context used in this render task. The context includes things like platform information, file or module.
   */
  @NotNull
  public RenderTaskContext getContext() {
    return myContext;
  }

  /**
   * The {@link AttributeFilter} allows a client of {@link #measureChildren} to modify the actual
   * XML values of the nodes being rendered, for example to force width and height values to
   * wrap_content when measuring preferred size.
   */
  public interface AttributeFilter {
    /**
     * Returns the attribute value for the given node and attribute name. This filter
     * allows a client to adjust the attribute values that a node presents to the
     * layout library.
     * <p/>
     * Returns "" to unset an attribute. Returns null to return the unfiltered value.
     *
     * @param node      the node for which the attribute value should be returned
     * @param namespace the attribute namespace
     * @param localName the attribute local name
     * @return an override value, or null to return the unfiltered value
     */
    @Nullable
    String getAttribute(@NotNull XmlTag node, @Nullable String namespace, @NotNull String localName);
  }
}
