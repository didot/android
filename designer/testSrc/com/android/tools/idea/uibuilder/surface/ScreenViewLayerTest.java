/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface;

import com.android.testutils.VirtualTimeScheduler;
import com.android.tools.adtui.imagediff.ImageDiffUtil;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.imagepool.ImagePool;
import com.android.tools.idea.rendering.imagepool.ImagePoolFactory;
import com.intellij.mock.MockApplicationEx;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.when;

public class ScreenViewLayerTest {

  public static final int SCREEN_VIEW_WIDTH = 200;
  public static final int SCREEN_VIEW_HEIGHT = 200;
  public static final int IMAGE_WIDTH = 500;
  public static final int IMAGE_HEIGHT = 500;
  public static final double SCALE = SCREEN_VIEW_HEIGHT / (double)IMAGE_HEIGHT;
  private static final Rectangle FULL_SIZE = new Rectangle(IMAGE_WIDTH, IMAGE_HEIGHT);

  private Disposable myDisposable =  Disposer.newDisposable();

  @Before
  public void setUp() {
    MockApplicationEx instance = new MockApplicationEx(myDisposable);
    ApplicationManager.setApplication(instance, myDisposable);
  }

  @After
  public void tearDown() {
    Disposer.dispose(myDisposable);
  }

  private static Rectangle scaleRectangle(Rectangle rect, double scale) {
    return new Rectangle(rect.x, rect.y, (int)(rect.width * scale), (int)(rect.width * scale));
  }

  /**
   * Gets the {@link Graphics2D} for the given image and cleans it up to be ready for the output.
   * It will paint the image as white and set the clip to the given size
   */
  @NotNull
  private static Graphics2D createGraphicsAndClean(@NotNull BufferedImage image, @NotNull Rectangle rectangle) {
    Graphics2D g = (Graphics2D)image.getGraphics();
    g.setColor(Color.WHITE);
    g.fill(rectangle);
    g.setClip(rectangle);

    return g;
  }

  @NotNull
  private static RenderResult createRenderResultMock(@NotNull ImagePool.Image resultImage) {
    RenderResult result = Mockito.mock(RenderResult.class);
    when(result.getRenderedImage()).thenReturn(resultImage);
    when(result.hasImage()).thenReturn(true);

    return result;
  }

  @NotNull
  private ScreenView createScreenViewMock(@NotNull Ref<Rectangle> screenViewLayerSize,
                                          @NotNull RenderResult firstResult,
                                          @NotNull RenderResult ...otherResults) {
    ScreenView screenView = Mockito.mock(ScreenView.class, RETURNS_DEEP_STUBS);
    when(screenView.getScreenShape()).thenAnswer(new Answer<Shape>() {
      @Override
      public Shape answer(InvocationOnMock invocation) {
        return screenViewLayerSize.get();
      }
    });
    when(screenView.getX()).thenReturn(0);
    when(screenView.getY()).thenReturn(0);
    when(screenView.getSize(any())).thenAnswer(new Answer<Dimension>() {
      @Override
      public Dimension answer(InvocationOnMock invocation) {
        Dimension returnDimension = (Dimension)invocation.getArguments()[0];

        if (returnDimension != null) {
          returnDimension.setSize(screenViewLayerSize.get().getSize());
        }
        else {
          returnDimension = screenViewLayerSize.get().getSize();
        }

        return returnDimension;
      }
    });

    when(screenView.getResult()).thenReturn(firstResult, otherResults);

    Disposer.register(myDisposable, screenView.getSurface());

    return screenView;
  }

  @SuppressWarnings("UndesirableClassUsage")
  @Test
  public void scalingPaintTest() throws Exception {
    VirtualTimeScheduler timeScheduler = new VirtualTimeScheduler();
    Ref<Rectangle> screenViewSize = new Ref<>(scaleRectangle(FULL_SIZE, SCALE));

    // Create a high quality image bigger than the screenView that will be scaled.
    ImagePool.Image imageHQ = getTestImage(IMAGE_WIDTH, IMAGE_HEIGHT);
    ScreenView screenView = createScreenViewMock(screenViewSize, createRenderResultMock(imageHQ));
    ScreenViewLayer layer = new ScreenViewLayer(screenView, timeScheduler);

    // First, we expect the low quality scaling in the first call.
    BufferedImage unscaled = new BufferedImage(SCREEN_VIEW_WIDTH, SCREEN_VIEW_HEIGHT, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = (Graphics2D)unscaled.getGraphics();
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    imageHQ.drawImageTo(g, 0, 0, SCREEN_VIEW_WIDTH, SCREEN_VIEW_HEIGHT);

    BufferedImage output = new BufferedImage(SCREEN_VIEW_WIDTH, SCREEN_VIEW_HEIGHT, BufferedImage.TYPE_INT_ARGB);
    g = createGraphicsAndClean(output, screenViewSize.get());
    layer.paint(g);
    ImageDiffUtil.assertImageSimilar("screenviewlayer_result.png", unscaled, output, 0.0);

    double xScale = imageHQ.getWidth() / screenViewSize.get().getWidth();
    double yScale = imageHQ.getHeight() / screenViewSize.get().getHeight();
    BufferedImage imageHQScaled = ScreenViewLayer.scaleOriginalImage(imageHQ.getCopy(), xScale, yScale);

    BufferedImage scaledHQ = new BufferedImage(imageHQScaled.getWidth(), imageHQScaled.getHeight(), BufferedImage.TYPE_INT_ARGB);
    StartupUiUtil.drawImage(scaledHQ.createGraphics(), imageHQScaled, 0, 0, null);

    // We wait more than the debounce delay to ensure that the next call to paint will draw an scaled image.
    timeScheduler.advanceBy(600, TimeUnit.MILLISECONDS);
    //noinspection UndesirableClassUsage
    g = createGraphicsAndClean(output, screenViewSize.get());
    layer.paint(g);
    ImageDiffUtil.assertImageSimilar("screenviewlayer_result.png", scaledHQ, output, 0.0);

    // Scale value back to 1.0, so no scaling.
    screenViewSize.set(FULL_SIZE);
    unscaled = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
    output = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
    g = (Graphics2D)unscaled.getGraphics();
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    imageHQ.drawImageTo(g, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
    g = createGraphicsAndClean(output, screenViewSize.get());
    layer.paint(g);
    ImageDiffUtil.assertImageSimilar("screenviewlayer_result.png", unscaled, output, 0.0);
  }

  // b/115639193
  @Test
  public void cancelPreviewTest() {
    VirtualTimeScheduler timeScheduler = new VirtualTimeScheduler();
    Ref<Rectangle> screenViewSize = new Ref<>(scaleRectangle(FULL_SIZE, SCALE));

    // Create a high quality image bigger than the screenView that will be scaled.
    ImagePool.Image imageHQ = getTestImage(IMAGE_WIDTH, IMAGE_HEIGHT);
    ImagePool.Image imageNoScale = getTestImage(SCREEN_VIEW_WIDTH, SCREEN_VIEW_HEIGHT);

    ScreenView screenView = createScreenViewMock(screenViewSize, createRenderResultMock(imageHQ), createRenderResultMock(imageNoScale));
    ScreenViewLayer layer = new ScreenViewLayer(screenView, timeScheduler);

    //noinspection UndesirableClassUsage
    BufferedImage output = new BufferedImage(SCREEN_VIEW_WIDTH, SCREEN_VIEW_HEIGHT, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = createGraphicsAndClean(output, screenViewSize.get());
    layer.paint(g);

    // This has scheduled a task to resize the image
    assertEquals(1, timeScheduler.getActionsQueued());
    // Advance time without triggering the debounce timeout
    timeScheduler.advanceBy(30, TimeUnit.MILLISECONDS);
    assertEquals(1, timeScheduler.getActionsQueued());

    // Get a new image that does not need resizing so it will cancel the existing timer
    layer.paint(g);
    assertEquals(0, timeScheduler.getActionsQueued());
  }

  @NotNull
  private static ImagePool.Image getTestImage(int imageWidth, int imageHeight) {
    ImagePool imagePool = ImagePoolFactory.createImagePool();
    ImagePool.Image imageHQ = imagePool.create(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
    imageHQ.paint(g -> {
      g.setStroke(new BasicStroke(10));
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, imageWidth, imageHeight);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.setColor(Color.BLACK);
      g.drawLine(0, 0, imageWidth, imageHeight);
      g.drawLine(imageWidth, 0, 0, imageHeight);
    });
    return imageHQ;
  }
}