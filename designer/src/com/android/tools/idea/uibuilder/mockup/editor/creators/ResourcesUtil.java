/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.mockup.editor.creators;

import com.android.resources.ResourceFolderType;
import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static com.android.SdkConstants.FD_RES_DRAWABLE;
import static org.jetbrains.android.util.AndroidUtils.createChildDirectoryIfNotExist;

/**
 *
 */
public final class ResourcesUtil {

  private static final Logger LOGGER = Logger.getInstance(ResourcesUtil.class);

  public static boolean createDrawable(@NotNull String drawableName,
                                       @NotNull String drawableType,
                                       @NotNull WidgetCreator.DoneCallback doneCallback,
                                       @NotNull NlModel model,
                                       @NotNull BufferedImage image,
                                       @NotNull Object requestor) {

    AndroidFacet facet = model.getFacet();

    // Create a new file in the res/drawable folder
    List<VirtualFile> drawableSubDirs = AndroidResourceUtil.getResourceSubdirs(
      ResourceFolderType.DRAWABLE,
      VfsUtilCore.toVirtualFileArray(facet.getModuleResources(true).getResourceDirs()));

    try {
      byte[] imageInByte = imageToByteArray(image, drawableType);

      // Check if the drawable folder already exist, create it otherwise
      Project project = model.getProject();
      if (!drawableSubDirs.isEmpty()) {
        createDrawableFile(drawableName, drawableType, imageInByte, project, drawableSubDirs.get(0), doneCallback, requestor);
      }
      else {
        createDrawableAndFolder(drawableName, drawableType, facet, imageInByte, project, doneCallback, requestor);
      }
      return true;
    }
    catch (IOException e) {
      LOGGER.error("Error while converting image to bytes");
      return false;
    }
  }

  /**
   * Create a drawable into the drawable folder with the given name. The image created will be a png file.
   *
   * @param drawableName    The name of the image to create (without the extension)
   * @param doneCallback    The callback to call once the image is created
   * @param mockup          Mockup to extract the image from
   * @param model           Current Model of the mockup
   * @param selectionBounds The selection in the mockup editor
   * @param requestor       The object requesting the drawable creation
   */
  public static boolean createDrawable(@NotNull String drawableName,
                                       @NotNull String drawableType,
                                       @NotNull WidgetCreator.DoneCallback doneCallback,
                                       @NotNull Mockup mockup,
                                       @NotNull NlModel model,
                                       @NotNull Rectangle selectionBounds,
                                       @NotNull Object requestor) {

    BufferedImage image = mockup.getImage();
    if (image == null) {
      return false;
    }

    // Extract selection from original image
    final Rectangle realCropping = mockup.getRealCropping();
    BufferedImage subImage =
      image.getSubimage(selectionBounds.x + realCropping.x, selectionBounds.y + realCropping.y, selectionBounds.width,
                        selectionBounds.height);

    // Transform the new image into a byte array

    return createDrawable(drawableName, drawableType, doneCallback, model, subImage, requestor);

  }

  /**
   * Create a byte array from a BufferedImage
   *
   * @param subImage  the image to convert
   * @param imageType
   * @return the byte array representing the image
   * @throws IOException
   */
  public static byte[] imageToByteArray(BufferedImage subImage, String imageType) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(subImage, imageType, baos);
    baos.flush();
    byte[] imageInByte = baos.toByteArray();
    baos.close();
    return imageInByte;
  }

  /**
   * Create the drawable folder into the main resource directory and
   * then create the new image file represented by imageInByte
   *
   * @param drawableName The name of the drawable to create
   * @param drawableType The file type of the drawable to create ( png, jpg...)
   * @param facet        the current facet of the model
   * @param imageInByte  the byte representation of the image to create
   * @param project      the current project
   * @param doneCallback The callback to call one the image is created
   * @param requestor    Object requesting the drawable creation
   */
  private static void createDrawableAndFolder(@NotNull String drawableName,
                                              @NotNull String drawableType,
                                              @NotNull AndroidFacet facet,
                                              @NotNull byte[] imageInByte,
                                              @NotNull Project project,
                                              @NotNull WidgetCreator.DoneCallback doneCallback,
                                              @NotNull Object requestor) {
    Collection<VirtualFile> resDirectories = facet.getMainIdeaSourceProvider().getResDirectories();
    Iterator<VirtualFile> iterator = resDirectories.iterator();
    if (iterator.hasNext()) {
      CommandProcessor.getInstance().executeCommand(
        project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
          try {
            VirtualFile drawableDir = createChildDirectoryIfNotExist(project, iterator.next(), FD_RES_DRAWABLE);
            createDrawableFile(drawableName, drawableType, imageInByte, project, drawableDir, doneCallback, requestor);
          }
          catch (IOException e) {
            LOGGER.error(e);
          }
        }),
        "Export selection to drawable",
        null
      );
    }
  }

  /**
   * Create the image file in the drawable directory
   *
   * @param drawableName The name of the drawable to create
   * @param imageInByte  the byte representation of the image to create
   * @param project      the current project
   * @param doneCallback The callback to call one the image is created
   * @param requestor    Object requesting the drawable creation
   */
  private static void createDrawableFile(@NotNull String drawableName,
                                         @NotNull String drawableType,
                                         @NotNull byte[] imageInByte,
                                         @NotNull Project project,
                                         @NotNull VirtualFile drawableDir,
                                         @NotNull WidgetCreator.DoneCallback doneCallback,
                                         @NotNull Object requestor) {
    CommandProcessor.getInstance().executeCommand(
      project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
        try {
          final VirtualFile folder = drawableDir.createChildData(requestor, drawableName + "." + drawableType);
          folder.setBinaryContent(imageInByte);
          doneCallback.done(WidgetCreator.DoneCallback.FINISH);
        }
        catch (IOException e) {
          LOGGER.error(e);
          doneCallback.done(WidgetCreator.DoneCallback.CANCEL);
        }
      }),
      "Export selection to drawable",
      null
    );
  }
}
