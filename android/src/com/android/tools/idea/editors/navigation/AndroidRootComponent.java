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
package com.android.tools.idea.editors.navigation;

import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

public class AndroidRootComponent extends JComponent {
  //private static final Object RENDERING_LOCK = new Object();

  private final RenderingParameters myRenderingParameters;
  private final PsiFile myPsiFile;
  private final boolean myIsMenu;

  @NotNull Transform transform = createTransform(1);
  private Image myScaledImage;
  private RenderResult myRenderResult = null;

  public AndroidRootComponent(@NotNull final RenderingParameters renderingParameters, @Nullable final PsiFile psiFile, boolean isMenu) {
    this.myRenderingParameters = renderingParameters;
    this.myPsiFile = psiFile;
    this.myIsMenu = isMenu;
  }

  public AndroidRootComponent(@NotNull final RenderingParameters renderingParameters, @Nullable final PsiFile psiFile) {
    this(renderingParameters, psiFile, false);
  }

  @Nullable
  private RenderResult getRenderResult() {
    return myRenderResult;
  }

  private void setRenderResult(@Nullable RenderResult renderResult) {
    myRenderResult = renderResult;
    revalidate(); // once we have finished rendering we will know where our internal views are, need to relayout otherwise
    repaint();
  }

  public float getScale() {
    return transform.myScale;
  }

  private void invalidate2() {
    myScaledImage = null;
  }

  public void setScale(float scale) {
    transform = createTransform(scale);
    invalidate2();
  }

  private Transform createTransform(float scale) {
    if (myIsMenu) {
      return new Transform(scale) {
        @Override
        public int modelToViewX(int d) {
          RenderedView menu = getMenu(myRenderResult);
          return super.modelToViewX(d - (menu == null ? 0 : menu.x));
        }
      };
    }
    return new Transform(scale);
  }

  private static RenderedView getMenu(RenderResult renderResult) {
    RenderedView root = getRoot(renderResult);
    if (root == null) {
      return null;
    }
    return root.getChildren().get(0);
  }

  private com.android.navigation.Dimension size(RenderedView view) {
    if (view == null) {
      //return com.android.navigation.Dimension.ZERO;
      return new com.android.navigation.Dimension(100, 100); // width/height 0 and 1 is too small to cause an invalidate, for some reason
    }
    return new com.android.navigation.Dimension(view.w, view.h);
  }

  @Override
  public Dimension getPreferredSize() {
    return transform.modelToView(myIsMenu ? size(getMenu(myRenderResult)) : myRenderingParameters.getDeviceScreenSize());
  }

  //ScalableImage image = myRenderResult.getImage();
  //if (image != null) {
  //  image.paint(g);
  //}
      /*
      if (false) {
        Graphics2D g2 = (Graphics2D)g;
        g2.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_BICUBIC);
        //g2.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setRenderingHint(KEY_RENDERING, VALUE_RENDER_QUALITY);
        g2.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);
        g2.drawImage(image, 0, 0, getWidth(), getHeight(), 0, 0, image.getWidth(), image.getHeight(), null);
      }
      if (false) {
        g.drawImage(image, 0, 0, getWidth(), getHeight(), null);
      }
      */

  private Image getScaledImage() {
    if (myScaledImage == null ||
        myScaledImage.getWidth(null) != getWidth() ||
        myScaledImage.getHeight(null) != getHeight()) {
      ScalableImage scalableImage = myRenderResult == null ? null : myRenderResult.getImage();
      BufferedImage image = scalableImage == null ? null : scalableImage.getOriginalImage();
      if (image != null) {
        myScaledImage = ImageUtils.scale(image, transform.myScale, transform.myScale, 0, 0);
      }
    }
    return myScaledImage;
  }

  @Override
  public void paintComponent(Graphics g) {
    Image scaledImage = getScaledImage();
    if (scaledImage != null) {
      if (myIsMenu) {
        Point point = transform.modelToView(com.android.navigation.Point.ORIGIN);
        g.drawImage(scaledImage, point.x, point.y, null);
      }
      else {
        g.drawImage(scaledImage, 0, 0, null);
      }
    }
    else {
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, getWidth(), getHeight());
      g.setColor(Color.GRAY);
      String message = "Initialising...";
      Font font = g.getFont();
      int messageWidth = getFontMetrics(font).stringWidth(message);
      g.drawString(message, (getWidth() - messageWidth) / 2, getHeight() / 2);
      render();
    }
  }

  private void render() {
    if (myPsiFile == null) {
      return;
    }
    Project project = myRenderingParameters.myProject;
    AndroidFacet facet = myRenderingParameters.myFacet;
    Configuration configuration = myRenderingParameters.myConfiguration;

    if (project.isDisposed()) {
      return;
    }

    Module module = facet.getModule();
    RenderLogger logger = new RenderLogger(myPsiFile.getName(), module);
    //synchronized (RENDERING_LOCK) {
    final RenderService service = RenderService.create(facet, module, myPsiFile, configuration, logger, null);
    // The rendering service takes long enough to initialise that we don't want to do this from the EDT.
    // Further, IntelliJ's helper classes don't not allow read access from outside EDT, so we need nested runnables.
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            if (service != null) {
              setRenderResult(service.render());
              service.dispose();
            }
            //}
          }
        });
      }
    });
  }

  @Nullable
  public RenderedView getRootView() {
    RenderResult renderResult = getRenderResult();
    if (renderResult == null) {
      return null;
    }
    return getRoot(renderResult);
  }

  @Nullable
  private static RenderedView getRoot(@Nullable RenderResult renderResult) {
    if (renderResult == null) {
      return null;
    }
    RenderedViewHierarchy hierarchy = renderResult.getHierarchy();
    if (hierarchy == null) {
      return null;
    }
    List<RenderedView> roots = hierarchy.getRoots();
    if (roots.isEmpty()) {
      return null;
    }
    return roots.get(0);
  }

  @Nullable
  public RenderedView getRenderedView(Point p) {
    RenderResult renderResult = getRenderResult();
    if (renderResult == null) {
      return null;
    }
    RenderedViewHierarchy hierarchy = renderResult.getHierarchy();
    if (hierarchy == null) {
      return null;
    }
    return hierarchy.findLeafAt(transform.viewToModel(p.x), transform.viewToModel(p.y));
  }
}
