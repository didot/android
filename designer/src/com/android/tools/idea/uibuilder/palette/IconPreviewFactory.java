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
package com.android.tools.idea.uibuilder.palette;

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.SessionParams;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.devices.Screen;
import com.android.sdklib.devices.State;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.*;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.Lists;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.api.PaletteComponentHandler.NO_PREVIEW;
import static com.android.tools.idea.uibuilder.palette.NlPaletteModel.ANDROID_PALETTE;
import static com.android.tools.idea.uibuilder.palette.NlPaletteModel.PALETTE_VERSION;

/**
 * IconPreviewFactory generates a preview of certain palette components.
 * The images are rendered from preview.xml and are used as an alternate representation on
 * the palette i.e. a button is rendered as the SDK button would look like on the target device.
 */
public class IconPreviewFactory implements Disposable {
  private static final Logger LOG = Logger.getInstance(IconPreviewFactory.class);
  @AndroidDpCoordinate
  private static final int SHADOW_SIZE = 6;
  private static final int PREVIEW_LIMIT = 4000;
  private static final int DEFAULT_X_DIMENSION = 1080;
  private static final int DEFAULT_Y_DIMENSION = 1920;
  private static final String DEFAULT_THEME = "AppTheme";
  private static final String PREVIEW_PLACEHOLDER_FILE = "preview.xml";
  private static final String CONTAINER_ID = "TopLevelContainer";
  private static final String LINEAR_LAYOUT = "<LinearLayout\n" +
                                              "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                              "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                                              "    android:id=\"@+id/%1$s\"\n" +
                                              "    android:layout_width=\"match_parent\"\n" +
                                              "    android:layout_height=\"wrap_content\"\n" +
                                              "    android:orientation=\"vertical\">\n" +
                                              "  %2$s\n" +
                                              "</LinearLayout>\n";

  private RenderTask myRenderTask;

  @Nullable
  public BufferedImage getImage(@NotNull Palette.Item item, @NotNull Configuration configuration, double scale) {
    BufferedImage image = readImage(item.getId(), configuration);
    if (image == null) {
      return null;
    }
    if (scale != 1.0) {
      image = ImageUtils.scale(image, scale);
    }
    return image;
  }

  @Nullable
  private RenderTask getRenderTask(Configuration configuration) {
    if (myRenderTask == null || myRenderTask.getModule() != configuration.getModule()) {
      if (myRenderTask != null) {
        myRenderTask.dispose();
      }

      AndroidFacet facet = AndroidFacet.getInstance(configuration.getModule());
      if (facet == null) {
        return null;
      }
      RenderService renderService = RenderService.getInstance(facet);
      RenderLogger logger = renderService.createLogger();
      myRenderTask = renderService.createTask(null, configuration, logger, null);
    }

    return myRenderTask;
  }

  /**
   * Return a component image to display while dragging a component from the palette.
   * Return null if such an image cannot be rendered. The palette must provide a fallback in this case.
   */
  @Nullable
  public BufferedImage renderDragImage(@NotNull Palette.Item item, @NotNull ScreenView screenView) {
    XmlElementFactory elementFactory = XmlElementFactory.getInstance(screenView.getModel().getProject());
    String xml = item.getDragPreviewXml();
    if (xml.equals(NO_PREVIEW)) {
      return null;
    }

    XmlTag tag;

    try {
      tag = elementFactory.createTagFromText(xml);
    }
    catch (IncorrectOperationException exception) {
      return null;
    }

    NlModel model = screenView.getModel();

    NlComponent component = ApplicationManager.getApplication()
      .runWriteAction((Computable<NlComponent>)() -> model.createComponent(screenView, tag, null, null, InsertType.CREATE_PREVIEW));

    if (component == null) {
      return null;
    }


    // Some components require a parent to render correctly.
    xml = String.format(LINEAR_LAYOUT, CONTAINER_ID, component.getTag().getText());

    RenderResult result = renderImage(getRenderTask(model.getConfiguration()), xml);
    if (result == null || !result.hasImage()) {
      return null;
    }

    ImagePool.Image image = result.getRenderedImage();
    List<ViewInfo> infos = result.getRootViews();
    if (infos.isEmpty()) {
      return null;
    }
    infos = infos.get(0).getChildren();
    if (infos == null || infos.isEmpty()) {
      return null;
    }
    ViewInfo view = infos.get(0);
    if (image.getHeight() < view.getBottom() || image.getWidth() < view.getRight() ||
    view.getBottom() <= view.getTop() || view.getRight() <= view.getLeft()) {
      return null;
    }
    @AndroidCoordinate
    int shadowWitdh = SHADOW_SIZE * screenView.getConfiguration().getDensity().getDpiValue() / Density.DEFAULT_DENSITY;
    @SwingCoordinate
    int shadowIncrement = 1 + Coordinates.getSwingDimension(screenView, shadowWitdh);

    BufferedImage imageCopy = image.getCopy();
    if (imageCopy == null) {
      return null;
    }
    return imageCopy.getSubimage(view.getLeft(),
                             view.getTop(),
                             Math.min(view.getRight() + shadowIncrement, image.getWidth()),
                             Math.min(view.getBottom() + shadowIncrement, image.getHeight()));
  }

  private static BufferedImage readImage(@NotNull String id, @NotNull Configuration configuration) {
    File file = new File(getPreviewCacheDirForConfiguration(configuration), id + DOT_PNG);
    if (!file.exists()) {
      return null;
    }
    try {
      return ImageIO.read(file);
    }
    catch (Throwable ignore) {
      // corrupt cached image, e.g. I've seen
      //  java.lang.IndexOutOfBoundsException
      //  at java.io.RandomAccessFile.readBytes(Native Method)
      //  at java.io.RandomAccessFile.read(RandomAccessFile.java:338)
      //  at javax.imageio.stream.FileImageInputStream.read(FileImageInputStream.java:101)
      //  at com.sun.imageio.plugins.common.SubImageInputStream.read(SubImageInputStream.java:46)
    }
    return null;
  }

  /**
   * Drop the preview cache for this configuration.
   */
  public void dropCache() {
    FileUtil.delete(getPreviewCacheDir());
  }

  /**
   * Load preview images for each component into a file cache.
   * Each combination of theme, device density, and API level will have its own cache.
   *
   * @param configuration a hardware configuration to generate previews for
   * @param palette a palette will the components to generate previews of
   * @param reload if true replace the existing preview images
   * @return true if the images were loaded, false if they already existed
   */
  public boolean load(@NotNull Configuration configuration,
                      @NotNull Palette palette,
                      boolean reload) {
    return load(configuration, palette, reload, null, null);
  }

  /**
   * Load preview images for each component into a file cache.
   * Each combination of theme, device density, and API level will have its own cache.
   *
   * @param configuration a hardware configuration to generate previews for
   * @param palette a palette with the components to generate previews of
   * @param reload if true replace the existing preview images
   * @param requestedIds for testing only: gather the IDs of the components whose previews were requested
   * @param generatedIds for testing only: gather the IDs of the components whose preview images where generated
   * @return true if the images were loaded, false if they already existed
   */
  @VisibleForTesting
  boolean load(@NotNull final Configuration configuration,
               @NotNull final Palette palette,
               boolean reload,
               @Nullable final List<String> requestedIds,
               @Nullable final List<String> generatedIds) {
    File cacheDir = getPreviewCacheDirForConfiguration(configuration);
    String[] files = cacheDir.list();
    if (files != null && files.length > 0) {
      // The previews have already been generated.
      if (!reload) {
        return false;
      }
      FileUtil.delete(cacheDir);
    }
    ApplicationManager.getApplication().runReadAction(new Computable<Void>() {
      @Override
      public Void compute() {
        List<StringBuilder> sources = Lists.newArrayList();
        loadSources(sources, requestedIds, palette.getItems());
        for (StringBuilder source : sources) {
          String preview = String.format(LINEAR_LAYOUT, CONTAINER_ID, source);
          addResultToCache(renderImage(getRenderTask(configuration), preview), generatedIds, configuration);
        }
        return null;
      }
    });
    return true;
  }

  private static void loadSources(@NotNull List<StringBuilder> sources, @Nullable List<String> ids, List<Palette.BaseItem> items) {
    boolean previousRenderedSeparately = false;
    for (Palette.BaseItem base : items) {
      if (base instanceof Palette.Group) {
        Palette.Group group = (Palette.Group) base;
        loadSources(sources, ids, group.getItems());
      }
      else if (base instanceof Palette.Item) {
        Palette.Item item = (Palette.Item) base;
        String preview = item.getPreviewXml();
        if (!preview.equals(NO_PREVIEW)) {
          StringBuilder last = sources.isEmpty() ? null : sources.get(sources.size() - 1);
          if (last == null ||
              last.length() > PREVIEW_LIMIT ||
              (last.length() > 0 && (item.isPreviewRenderedSeparately() || previousRenderedSeparately))) {
            last = new StringBuilder();
            sources.add(last);
          }
          previousRenderedSeparately = item.isPreviewRenderedSeparately();
          last.append(preview);
          if (ids != null) {
            ids.add(item.getId());
          }
        }
      }
    }
  }

  @NotNull
  private static File getPreviewCacheDir() {
    return new File(
      PathUtil.getCanonicalPath(PathManager.getSystemPath()) + File.separator +
      ANDROID_PALETTE + File.separator +
      PALETTE_VERSION + File.separator +
      "image-cache");
  }

  @NotNull
  private static File getPreviewCacheDirForConfiguration(@NotNull Configuration configuration) {
    int density = configuration.getDensity().getDpiValue();
    State state = configuration.getDeviceState();
    Screen screen = state != null ? state.getHardware().getScreen() : null;
    int xDimension = DEFAULT_X_DIMENSION;
    int yDimension = DEFAULT_Y_DIMENSION;
    if (screen != null) {
      xDimension = screen.getXDimension();
      yDimension = screen.getYDimension();
      density = screen.getPixelDensity().getDpiValue();
    }
    ScreenOrientation orientation = state != null ? state.getOrientation() : ScreenOrientation.PORTRAIT;
    if ((orientation == ScreenOrientation.LANDSCAPE && xDimension < yDimension) ||
        (orientation == ScreenOrientation.PORTRAIT && xDimension > yDimension)) {
      int temp = xDimension;
      //noinspection SuspiciousNameCombination
      xDimension = yDimension;
      yDimension = temp;
    }
    String theme = getTheme(configuration);
    String apiVersion = getApiVersion(configuration);
    String cacheFolder = theme + File.separator +
                         xDimension + "x" + yDimension + "-" + density + "-" + apiVersion;
    return new File(getPreviewCacheDir(), cacheFolder);
  }

  @NotNull
  private static String getTheme(@NotNull Configuration configuration) {
    String theme = configuration.getTheme();
    if (theme == null) {
      theme = DEFAULT_THEME;
    }
    theme = StringUtil.trimStart(theme, STYLE_RESOURCE_PREFIX);
    theme = StringUtil.trimStart(theme, ANDROID_STYLE_RESOURCE_PREFIX);
    return theme;
  }

  private static String getApiVersion(@NotNull Configuration configuration) {
    IAndroidTarget target = configuration.getTarget();
    // If the target is not found, return a version that cannot be confused with a proper result.
    // For now: use "U" for "unknown".
    return target == null ? SdkVersionInfo.HIGHEST_KNOWN_STABLE_API + "U" : target.getVersion().getApiString();
  }

  @Nullable
  private static BufferedImage addResultToCache(@Nullable RenderResult result, @Nullable List<String> ids, @NotNull Configuration configuration) {
    if (result == null || result.getRenderedImage() == null || result.getRootViews().isEmpty()) {
      return null;
    }
    ImageAccumulator accumulator = new ImageAccumulator(result.getRenderedImage().getCopy(), ids, configuration);
    accumulator.run(result.getRootViews(), 0, null);
    return null;
  }

  @Nullable
  private static RenderResult renderImage(@Nullable RenderTask renderTask, @NotNull String xml) {
    if (renderTask == null) {
      return null;
    }
    PsiFile file = PsiFileFactory.getInstance(renderTask.getModule().getProject()).createFileFromText(PREVIEW_PLACEHOLDER_FILE, XmlFileType.INSTANCE, xml);

    renderTask.setPsiFile(file);
    renderTask.setOverrideBgColor(UIUtil.TRANSPARENT_COLOR.getRGB());
    renderTask.setDecorations(false);
    renderTask.setRenderingMode(SessionParams.RenderingMode.V_SCROLL);
    renderTask.setFolderType(ResourceFolderType.LAYOUT);
    renderTask.inflate();
    //noinspection deprecation
    return renderTask.render();
  }

  @Override
  public void dispose() {
    if (myRenderTask != null) {
      myRenderTask.dispose();
      myRenderTask = null;
    }
  }

  private static class ImageAccumulator {
    private final BufferedImage myImage;
    private final List<String> myIds;
    private final File myCacheDir;
    private final int myHeight;
    private final int myWidth;

    private ImageAccumulator(@NotNull BufferedImage image, @Nullable List<String> ids, @NotNull Configuration configuration) {
      myImage = image;
      myIds = ids;
      myCacheDir = getPreviewCacheDirForConfiguration(configuration);
      myHeight = image.getRaster().getHeight();
      myWidth = image.getRaster().getWidth();
    }

    private void run(@NotNull List<ViewInfo> views, int top, @Nullable String parentId) {
      for (ViewInfo info : views) {
        String id = null;
        XmlTag tag = RenderService.getXmlTag(info);
        if (tag != null) {
          id = getId(tag.getAttributeValue(ATTR_ID, ANDROID_URI));
          if (CONTAINER_ID.equals(parentId)) {
            if (info.getBottom() + top <= myHeight && info.getRight() <= myWidth && info.getBottom() > info.getTop()) {
              Rectangle bounds =
                new Rectangle(info.getLeft(), info.getTop() + top, info.getRight() - info.getLeft(), info.getBottom() - info.getTop());
              BufferedImage image = myImage.getSubimage(bounds.x, bounds.y, bounds.width, bounds.height);
              if (id == null) {
                id = tag.getName();
              }
              saveImage(id, image);
              if (myIds != null) {
                myIds.add(id);
              }
            }
            else {
              LOG.warn(String.format("Dimensions of %1$s is out of range", id));
            }
          }
        }
        if (!info.getChildren().isEmpty() && !CONTAINER_ID.equals(parentId)) {
          run(info.getChildren(), top + info.getTop(), id);
        }
      }
    }

    @Nullable
    private static String getId(@Nullable String id) {
      if (id != null) {
        if (id.startsWith(NEW_ID_PREFIX)) {
          return id.substring(NEW_ID_PREFIX.length());
        } else if (id.startsWith(ID_PREFIX)) {
          return id.substring(ID_PREFIX.length());
        }
      }
      return id;
    }


    private void saveImage(@NotNull String id, @NotNull BufferedImage image) {
      //noinspection ResultOfMethodCallIgnored
      myCacheDir.mkdirs();
      File file = new File(myCacheDir, id + DOT_PNG);
      try {
        ImageIO.write(image, "PNG", file);
      }
      catch (IOException e) {
        // pass
        if (file.exists()) {
          //noinspection ResultOfMethodCallIgnored
          file.delete();
        }
      }
    }
  }

  private interface RenderResultHandler {
    @Nullable
    BufferedImage handle(@NotNull RenderResult result);
  }
}
