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

import com.android.ide.common.rendering.api.Result;
import com.android.sdklib.devices.Device;
import com.android.tools.adtui.imagediff.ImageDiffUtil;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.legacy.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static java.io.File.separator;
import static java.io.File.separatorChar;

/**
 * Base for unit tests which perform rendering; this test can generate configurations and perform
 * rendering, then check that the rendered result matches a known thumbnail (by a certain maximum
 * percentage difference). The test will generate the required thumbnail if it does not exist,
 * so to create a new render test just call {@link #checkRenderedImage(java.awt.image.BufferedImage, String)}
 * and run the test once; then verify that the thumbnail looks fine, and if so, check it in; the test
 * will now check that subsequent renders are similar.
 * <p>
 * The reason the test checks for similarity is that whenever rendering includes fonts, there are some
 * platform differences in text rendering etc which does not give us a pixel for pixel match.
 */
public abstract class RenderTestBase extends AndroidTestCase {
  protected static final String DEFAULT_DEVICE_ID = "Nexus 4";
  private static final String DEFAULT_THEME_STYLE = "@android:style/Theme.Holo";
  private static final float MAX_PERCENT_DIFFERENT = 5.0f;

  @Override
  protected boolean requireRecentSdk() {
    return true;
  }

  protected RenderTask createRenderTask(VirtualFile file) throws Exception {
    Configuration configuration = getConfiguration(file, DEFAULT_DEVICE_ID, DEFAULT_THEME_STYLE);
    return createRenderTask(file, configuration);
  }

  protected Configuration getConfiguration(VirtualFile file, String deviceId) {
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assertNotNull(facet);
    ConfigurationManager configurationManager = facet.getConfigurationManager();
    assertNotNull(configurationManager);
    Configuration configuration = configurationManager.getConfiguration(file);
    configuration.setDevice(findDeviceById(configurationManager, deviceId), false);

    return configuration;
  }

  protected Configuration getConfiguration(VirtualFile file, String deviceId, String themeStyle) {
    Configuration configuration = getConfiguration(file, deviceId);
    configuration.setTheme(themeStyle);
    return configuration;
  }

  protected RenderTask createRenderTask(VirtualFile file, Configuration configuration) throws IOException {
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    assertNotNull(psiFile);
    assertNotNull(facet);
    RenderService renderService = RenderService.get(facet);
    RenderLogger logger = renderService.createLogger();
    final RenderTask task = renderService.createTask(psiFile, configuration, logger, null);
    assertNotNull(task);
    return task;
  }

  protected void checkRendering(RenderTask task, String thumbnailPath) throws IOException {
    // Next try a render
    RenderResult result = task.render();
    RenderResult render = renderOnSeparateThread(task);
    assertNotNull(render);

    assertNotNull(result);
    Result renderResult = result.getRenderResult();
    assertEquals(String.format("Render failed with message: %s\n%s", renderResult.getErrorMessage(), renderResult.getException()),
                 Result.Status.SUCCESS, result.getRenderResult().getStatus());
    RenderedImage image = result.getImage();
    assertNotNull(image);
    image.setMaxSize(200, 200);
    image.setDeviceFrameEnabled(false);
    @SuppressWarnings("UndesirableClassUsage") // Don't want Retina images in unit tests
    BufferedImage thumbnail = new BufferedImage(image.getRequiredWidth(), image.getRequiredHeight(), TYPE_INT_ARGB);
    Graphics graphics = thumbnail.getGraphics();
    image.paint(graphics, 0, 0);
    graphics.dispose();
    checkRenderedImage(thumbnail, "render" + separator + "thumbnails" + separator + thumbnailPath.replace('/', separatorChar));
  }

  @Nullable
  public static RenderResult renderOnSeparateThread(@NotNull final RenderTask task) {
    // Ensure that we don't render on the read lock (since we want to test that all parts of the
    // rendering system which needs a read lock asks for one!)
    final AtomicReference<RenderResult> holder = new AtomicReference<>();
    Thread thread = new Thread("render test") {
      @Override
      public void run() {
        holder.set(task.render());
      }
    };
    thread.start();
    try {
      thread.join();
    }
    catch (InterruptedException e) {
      fail("Interrupted");
    }

    return holder.get();
  }

  @NotNull
  protected static Device findDeviceById(ConfigurationManager manager, String id) {
    for (Device device : manager.getDevices()) {
      if (device.getId().equals(id)) {
        return device;
      }
    }
    fail("Can't find device " + id);
    throw new IllegalStateException();
  }

  protected void checkRenderedImage(BufferedImage image, String relativePath) throws IOException {
    relativePath = relativePath.replace('/', separatorChar);

    final String testDataPath = getTestDataPath();
    assert testDataPath != null : "test data path not specified";

    File fromFile = new File(testDataPath + "/" + relativePath);
    System.out.println("fromFile=" + fromFile);

    if (fromFile.exists()) {
      BufferedImage goldenImage = ImageIO.read(fromFile);
      ImageDiffUtil.assertImageSimilar(relativePath, goldenImage, image, MAX_PERCENT_DIFFERENT);
    } else {
      File dir = fromFile.getParentFile();
      assertNotNull(dir);
      if (!dir.exists()) {
        boolean ok = dir.mkdirs();
        assertTrue(dir.getPath(), ok);
      }
      ImageIO.write(image, "PNG", fromFile);
      fail("File did not exist, created " + fromFile);
    }
  }

  @NotNull
  public static File getTempDir() {
    if (System.getProperty("os.name").equals("Mac OS X")) {
      return new File("/tmp"); //$NON-NLS-1$
    }

    return new File(System.getProperty("java.io.tmpdir")); //$NON-NLS-1$
  }
}
