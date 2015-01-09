/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.swing.layoutlib;

import com.android.ide.common.rendering.HardwareConfigHelper;
import com.android.ide.common.rendering.LayoutLibrary;
import com.android.ide.common.rendering.RenderSecurityManager;
import com.android.ide.common.rendering.api.*;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.rendering.*;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.Graphics2DDelegate;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.uipreview.RenderingException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Class to render layouts to a {@link Graphics} instance. This renderer does not allow for much customization of the device and does not
 * include any kind of frames.
 *
 * <p/>This class will render a layout to a {@link Graphics} object and can be used to paint in controls that require live updates.
 *
 * <p/>Note: This class is not thread safe.
 */
public class GraphicsLayoutRenderer {
  private static final Logger LOG = Logger.getInstance(GraphicsLayoutRenderer.class.getName());

  private final LayoutLibrary myLayoutLibrary;
  private final SessionParams mySessionParams;
  private final FakeImageFactory myImageFactory;
  private final DynamicHardwareConfig myHardwareConfig;
  private final Object myCredential = new Object();
  private final RenderSecurityManager mySecurityManager;
  /** Invalidate the layout in the next render call */
  private boolean myInvalidate;

  /*
   * The render session is lazily initialized. We need to wait until we have a valid Graphics2D
   * instance to launch it.
   */
  private RenderSession myRenderSession;

  private GraphicsLayoutRenderer(@NotNull LayoutLibrary layoutLib,
                                 @NotNull SessionParams sessionParams,
                                 @NotNull RenderSecurityManager securityManager,
                                 @NotNull DynamicHardwareConfig hardwareConfig,
                                 @NotNull ILayoutPullParser parser) {
    myLayoutLibrary = layoutLib;
    mySecurityManager = securityManager;
    myHardwareConfig = hardwareConfig;
    mySessionParams = sessionParams;
    myImageFactory = new FakeImageFactory();

    mySessionParams.setImageFactory(myImageFactory);
  }

  @NotNull
  protected static GraphicsLayoutRenderer create(@NotNull AndroidFacet facet,
                                               @NotNull AndroidPlatform platform,
                                               @NotNull IAndroidTarget target,
                                               @NotNull Project project,
                                               @NotNull Configuration configuration,
                                               @NotNull ILayoutPullParser parser) throws InitializationException {
    Module module = facet.getModule();
    AndroidModuleInfo moduleInfo = AndroidModuleInfo.get(facet);

    LayoutLibrary layoutLib = null;
    try {
      layoutLib = platform.getSdkData().getTargetData(target).getLayoutLibrary(project);

      if (layoutLib == null) {
        throw new InitializationException("getLayoutLibrary() returned null");
      }
    }
    catch (RenderingException e) {
      throw new InitializationException(e);
    }
    catch (IOException e) {
      throw new InitializationException(e);
    }

    AppResourceRepository appResources = AppResourceRepository.getAppResources(facet, true);
    RenderLogger logger = new RenderLogger("theme_editor", module);

    final ActionBarCallback actionBarCallback = new ActionBarCallback();
    // TODO: Remove LayoutlibCallback dependency.
    //noinspection ConstantConditions
    LayoutlibCallback layoutlibCallback = new LayoutlibCallback(layoutLib, appResources, module, facet, logger, new Object(), null, null) {
      @Override
      public ActionBarCallback getActionBarCallback() {
        return actionBarCallback;
      }
    };


    HardwareConfigHelper hardwareConfigHelper = new HardwareConfigHelper(configuration.getDevice());
    DynamicHardwareConfig hardwareConfig = new DynamicHardwareConfig(hardwareConfigHelper.getConfig());
    final SessionParams params =
      new SessionParams(parser, SessionParams.RenderingMode.NORMAL, module, hardwareConfig, configuration.getResourceResolver(),
                        layoutlibCallback,
                        moduleInfo.getTargetSdkVersion().getApiLevel(), moduleInfo.getMinSdkVersion().getApiLevel(), logger,
                        target instanceof CompatibilityRenderTarget ? target.getVersion().getApiLevel() : 0);
    params.setForceNoDecor();

    RenderSecurityManager mySecurityManager = RenderSecurityManagerFactory.create(module, platform);
    return new GraphicsLayoutRenderer(layoutLib, params, mySecurityManager, hardwareConfig, parser);

  }

  @Nullable
  public static GraphicsLayoutRenderer create(@NotNull PsiFile layoutFile,
                                            @NotNull Configuration configuration,
                                            @NotNull ILayoutPullParser parser) throws InitializationException {
    AndroidFacet facet = AndroidFacet.getInstance(layoutFile);
    if (facet == null) {
      return null;
    }

    Module module = facet.getModule();
    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    IAndroidTarget target = configuration.getTarget();

    if (target == null) {
      return null;
    }

    if (sdk == null || !AndroidSdkUtils.isAndroidSdk(sdk)) {
      return null;
    }

    AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
    if (data == null) {
      return null;
    }
    AndroidPlatform platform = data.getAndroidPlatform();
    if (platform == null) {
      return null;
    }

    return create(facet, platform, target, module.getProject(), configuration, parser);
  }

  public void setSize(Dimension dimen) {
    myHardwareConfig.setScreenSize(dimen.width, dimen.height);
    myInvalidate = true;
  }

  /**
   * Render the layout to the passed {@link Graphics2D} instance using the defined viewport.
   *
   * <p/>Please note that this method is not thread safe so, if used from multiple threads, it's the caller's responsibility to synchronize
   * the access to it.
   */
  public void render(@NotNull final Graphics2D graphics) {
    myImageFactory.setGraphics(graphics);

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        Result result = null;
        mySecurityManager.setActive(true, myCredential);
        try {
          if (myRenderSession == null) {
            myRenderSession = initRenderSession();
            // initRenderSession will call render so we do not need to do it here.
            return;
          }

          // TODO: Currently passing true to invalidate in every render since layoutlib caches the passed Graphics2D otherwise.
          //       This should be addressed as part of the changes to pass the Graphics2D instance to layoutlib.
          result = myRenderSession.render(RenderParams.DEFAULT_TIMEOUT, true);
        }
        finally {
          mySecurityManager.setActive(false, myCredential);
        }

        // We need to log the errors after disabling the security manager since the logger will cause a security exception when trying to
        // access the system properties.
        if (result != null && result.getStatus() != Result.Status.SUCCESS) {
          LOG.error(result.getException());
        }
      }
    });
    myInvalidate = false;
  }

  /**
   * Returns the initialised render session. This will also do the an initial render of the layout.
   */
  private
  @Nullable
  RenderSession initRenderSession() {
    // createSession will also render the layout for the first time.
    RenderSession session = myLayoutLibrary.createSession(mySessionParams);
    Result result = session.getResult();

    if (!result.isSuccess() && result.getStatus() == Result.Status.ERROR_TIMEOUT) {
      // This could happen if layout is accessed from different threads and render is taking a long time.
      throw new RuntimeException("createSession ERROR_TIMEOUT.");
    }

    return session;
  }

  /**
   * {@link IImageFactory} that allows changing the image on the fly.
   * <p/>
   * <p/>This is a temporary workaround until we expose an interface in layoutlib that allows directly
   * rendering to a {@link Graphics} instance.
   */
  static class FakeImageFactory implements IImageFactory {
    private Graphics myGraphics;

    public void setGraphics(@NotNull Graphics graphics) {
      myGraphics = graphics;
    }

    @Override
    public BufferedImage getImage(final int w, final int h) {
      // BufferedImage can not have a 0 size. We pass 1,1 since we are not really interested in the bitmap,
      // only in the createGraphics call.
      return new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_BINARY) {
        @Override
        public Graphics2D createGraphics() {
          // TODO: Check if we can stop layoutlib from reseting the transforms.
          final Graphics2D g = new Graphics2DDelegate((Graphics2D)myGraphics.create(0, 0, w, h)) {
            @Override
            public void setTransform(AffineTransform Tx) {
            }
          };

          return g;
        }

        @Override
        public int getWidth() {
          return w;
        }

        @Override
        public int getHeight() {
          return h;
        }
      };
    }
  }

  /**
   * {@link HardwareConfig} that allows changing the screen size of the device on the fly.
   *
   * <p/>This allows to pass the HardwareConfig to the LayoutLib and then dynamically modify the size
   * for every render call.
   */
  static class DynamicHardwareConfig extends HardwareConfig {
    private int myWidth;
    private int myHeight;

    public DynamicHardwareConfig(HardwareConfig delegate) {
      super(delegate.getScreenWidth(), delegate.getScreenHeight(), delegate.getDensity(), delegate.getXdpi(), delegate.getYdpi(),
            delegate.getScreenSize(), delegate.getOrientation(), delegate.hasSoftwareButtons());

      myWidth = delegate.getScreenWidth();
      myHeight = delegate.getScreenHeight();
    }

    public void setScreenSize(int width, int height) {
      myWidth = width;
      myHeight = height;
    }

    @Override
    public int getScreenWidth() {
      return myWidth;
    }

    @Override
    public int getScreenHeight() {
      return myHeight;
    }
  }
}