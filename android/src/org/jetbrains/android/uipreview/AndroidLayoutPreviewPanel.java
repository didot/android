/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.uipreview;

import com.android.tools.idea.configurations.RenderContext;
import com.android.tools.idea.rendering.*;
import com.android.tools.idea.rendering.multi.RenderPreviewManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.Gray;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.util.ui.AsyncProcessIcon;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.rendering.RenderErrorPanel.SIZE_ERROR_PANEL_DYNAMICALLY;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidLayoutPreviewPanel extends JPanel implements Disposable {
  public static final Gray DESIGNER_BACKGROUND_COLOR = Gray._150;
  public static final Color SELECTION_BORDER_COLOR = new Color(0x00, 0x99, 0xFF, 255);
  public static final Color SELECTION_FILL_COLOR = new Color(0x00, 0x99, 0xFF, 32);
  /** FileEditorProvider ID for the layout editor */
  public static final String ANDROID_DESIGNER_ID = "android-designer";

  private RenderResult myRenderResult;

  private final JPanel myTitlePanel;
  private boolean myZoomToFit = true;

  private final List<ProgressIndicator> myProgressIndicators = new ArrayList<ProgressIndicator>();
  private boolean myProgressVisible = false;

  private final JComponent myImagePanel = new JPanel() {
    @Override
    public void paintComponent(Graphics g) {
      paintRenderedImage(g);
    }
  };

  private AsyncProcessIcon myProgressIcon;
  @NonNls private static final String PROGRESS_ICON_CARD_NAME = "Progress";
  @NonNls private static final String EMPTY_CARD_NAME = "Empty";
  private JPanel myProgressIconWrapper = new JPanel();
  private final JLabel myFileNameLabel = new JLabel();
  private TextEditor myEditor;
  private RenderedView mySelectedView;
  private CaretModel myCaretModel;
  private RenderErrorPanel myErrorPanel;
  private int myErrorPanelHeight = -1;
  private CaretListener myCaretListener = new CaretListener() {
    @Override
    public void caretPositionChanged(CaretEvent e) {
      updateCaret();
    }
  };
  private RenderPreviewManager myPreviewManager;

  public AndroidLayoutPreviewPanel() {
    super(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, true));
    setBackground(DESIGNER_BACKGROUND_COLOR);
    myImagePanel.setBackground(null);

    myImagePanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent mouseEvent) {
        if (myRenderResult == null) {
          return;
        }

        selectViewAt(mouseEvent.getX(), mouseEvent.getY());
      }

      @Override
      public void mouseClicked(MouseEvent mouseEvent) {
        if (myRenderResult == null) {
          return;
        }

        if (mouseEvent.getClickCount() == 2) {
          // Double click: open in the UI editor
          switchToLayoutEditor();
        }
      }
    });

    myFileNameLabel.setHorizontalAlignment(SwingConstants.CENTER);
    myFileNameLabel.setBorder(new EmptyBorder(5, 0, 5, 0));
    // We're using a hardcoded color here rather than say a JBLabel, since this
    // label is sitting on top of the preview gray background, which is the same
    // in all themes
    myFileNameLabel.setForeground(Color.BLACK);

    final JPanel progressPanel = new JPanel();
    progressPanel.setLayout(new BoxLayout(progressPanel, BoxLayout.X_AXIS));
    myProgressIcon = new AsyncProcessIcon("Android layout rendering");
    myProgressIconWrapper.setLayout(new CardLayout());
    myProgressIconWrapper.add(PROGRESS_ICON_CARD_NAME, myProgressIcon);
    myProgressIconWrapper.add(EMPTY_CARD_NAME, new JBLabel(" "));
    myProgressIconWrapper.setOpaque(false);

    Disposer.register(this, myProgressIcon);
    progressPanel.add(myProgressIconWrapper);
    progressPanel.add(new JBLabel(" "));
    progressPanel.setOpaque(false);

    myTitlePanel = new JPanel(new BorderLayout());
    myTitlePanel.setOpaque(false);
    myTitlePanel.add(myFileNameLabel, BorderLayout.CENTER);
    myTitlePanel.add(progressPanel, BorderLayout.EAST);

    ((CardLayout)myProgressIconWrapper.getLayout()).show(myProgressIconWrapper, EMPTY_CARD_NAME);

    add(myTitlePanel);

    MyImagePanelWrapper previewPanel = new MyImagePanelWrapper();
    add(previewPanel);

    myErrorPanel = new RenderErrorPanel();
    myErrorPanel.setVisible(false);
    previewPanel.add(myErrorPanel, JLayeredPane.POPUP_LAYER);
  }

  private void switchToLayoutEditor() {
    if (myEditor != null && myRenderResult != null && myRenderResult.getFile() != null) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          VirtualFile file = myRenderResult.getFile().getVirtualFile();
          if (file != null) {
            Project project = myEditor.getEditor().getProject();
            if (project != null) {
              FileEditorManager.getInstance(project).setSelectedEditor(file, ANDROID_DESIGNER_ID);
            }
          }
        }
      }, ModalityState.NON_MODAL);
    }
  }

  private void selectViewAt(int x1, int y1) {
    if (myEditor != null && myRenderResult.getImage() != null) {
      double zoomFactor = myRenderResult.getImage().getScale();
      int x = (int)(x1 / zoomFactor);
      int y = (int)(y1 / zoomFactor);
      RenderedViewHierarchy hierarchy = myRenderResult.getHierarchy();
      assert hierarchy != null; // because image != null
      RenderedView leaf = hierarchy.findLeafAt(x, y);

      // If you've clicked on for example a list item, the view you clicked
      // on may not correspond to a tag, it could be a designtime preview item,
      // so search upwards for the nearest surrounding tag
      while (leaf != null && leaf.tag == null) {
        leaf = leaf.getParent();
      }

      if (leaf != null) {
        int offset = leaf.tag.getTextOffset();
        if (offset != -1) {
          // TODO: Figure out how to scroll the view, too!
          myEditor.getEditor().getCaretModel().moveToOffset(offset);
        }
      }
    }
  }

  private void paintRenderedImage(Graphics g) {
    if (myRenderResult == null) {
      return;
    }
    ScalableImage image = myRenderResult.getImage();
    if (image != null) {
      image.paint(g);

      // TODO: Use layout editor's static feedback rendering
      RenderedView selected = mySelectedView;
      if (selected != null && !myErrorPanel.isVisible()) {
        double zoomFactor = image.getScale();
        int x = (int)(selected.x * zoomFactor);
        int y = (int)(selected.y * zoomFactor);
        int w = (int)(selected.w * zoomFactor);
        int h = (int)(selected.h * zoomFactor);

        g.setColor(SELECTION_FILL_COLOR);
        g.fillRect(x, y, w, h);

        g.setColor(SELECTION_BORDER_COLOR);
        x -= 1;
        y -= 1;
        w += 1; // +1 rather than +2: drawRect already includes end point whereas fillRect does not
        h += 1;
        if (x < 0) {
          w -= x;
          x = 0;
        }
        if (y < 0) {
          h -= y;
          h = 0;
        }
        g.drawRect(x, y, w, h);
      }
    }
  }

  private void updateCaret() {
    if (myCaretModel != null) {
      RenderedViewHierarchy hierarchy = myRenderResult.getHierarchy();
      if (hierarchy != null) {
        int offset = myCaretModel.getOffset();
        if (offset != -1) {
          RenderedView view = hierarchy.findByOffset(offset);
          if (view != null && view.isRoot()) {
            view = null;
          }
          if (view != mySelectedView) {
            mySelectedView = view;
            repaint();
          }
        }
      }
    }
  }

  public void setRenderResult(@NotNull final RenderResult renderResult, @Nullable final TextEditor editor) {
    double prevScale = myRenderResult != null && myRenderResult.getImage() != null ? myRenderResult.getImage().getScale() : 1;
    myRenderResult = renderResult;
    ScalableImage image = myRenderResult.getImage();
    if (image != null) {
      if (myPreviewManager != null) {
        Dimension fixedRenderSize = myPreviewManager.getFixedRenderSize();
        if (fixedRenderSize != null) {
          image.setMaxSize(fixedRenderSize.width, fixedRenderSize.height);
          image.setUseLargeShadows(false);
        }
      }
      image.setScale(prevScale);
    }

    mySelectedView = null;
    if (renderResult.getFile() != null) {
      myFileNameLabel.setText(renderResult.getFile().getName());
    }

    RenderLogger logger = myRenderResult.getLogger();
    if (logger.hasProblems()) {
      if (!myErrorPanel.isVisible()) {
        myErrorPanelHeight = -1;
      }
      myErrorPanel.showErrors(myRenderResult);
      myErrorPanel.setVisible(true);
    } else {
      myErrorPanel.setVisible(false);
    }

    setEditor(editor);
    updateCaret();
    doRevalidate();

    // Ensure that if we have a a preview mode enabled, it's shown
    if (myPreviewManager != null && myPreviewManager.hasPreviews()) {
      myPreviewManager.renderPreviews();
    }
  }

  private void setEditor(@Nullable TextEditor editor) {
    if (editor != myEditor) {
      myEditor = editor;

      if (myCaretModel != null) {
        myCaretModel.removeCaretListener(myCaretListener);
        myCaretModel = null;
      }
      if (editor != null)
      myCaretModel = myEditor.getEditor().getCaretModel();
      if (myCaretModel != null) {
        myCaretModel.addCaretListener(myCaretListener);
      }
    }
  }

  public synchronized void registerIndicator(@NotNull ProgressIndicator indicator) {
    synchronized (myProgressIndicators) {
      myProgressIndicators.add(indicator);

      if (!myProgressVisible) {
        myProgressVisible = true;
        ((CardLayout)myProgressIconWrapper.getLayout()).show(myProgressIconWrapper, PROGRESS_ICON_CARD_NAME);
        myProgressIcon.setVisible(true);
        myProgressIcon.resume();
      }
    }
  }

  public void unregisterIndicator(@NotNull ProgressIndicator indicator) {
    synchronized (myProgressIndicators) {
      myProgressIndicators.remove(indicator);

      if (myProgressIndicators.size() == 0 && myProgressVisible) {
        myProgressVisible = false;
        myProgressIcon.suspend();
        ((CardLayout)myProgressIconWrapper.getLayout()).show(myProgressIconWrapper, EMPTY_CARD_NAME);
        myProgressIcon.setVisible(false);
      }
    }
  }

  private void doRevalidate() {
    revalidate();
    updateImageSize();
    repaint();
  }

  public void update() {
    revalidate();

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        doRevalidate();
      }
    });
  }

  void updateImageSize() {
    if (myRenderResult == null) {
      return;
    }
    ScalableImage image = myRenderResult.getImage();
    if (image == null) {
      myImagePanel.setSize(0, 0);
    }
    else {
      if (myZoomToFit) {
        double availableHeight = getPanelHeight() - myTitlePanel.getSize().getHeight();
        double availableWidth = getPanelWidth();
        final int MIN_SIZE = 200;
        if (myPreviewManager != null && availableWidth > MIN_SIZE) {
          int previewWidth  = myPreviewManager.computePreviewWidth();
          availableWidth = Math.max(MIN_SIZE, availableWidth - previewWidth);
        }
        image.zoomToFit((int)availableWidth, (int)availableHeight, false, 0, 0);
      }

      myImagePanel.setSize(image.getRequiredSize());
    }
  }

  private double getPanelHeight() {
    return getParent().getParent().getSize().getHeight() - 5;
  }

  private double getPanelWidth() {
    return getParent().getParent().getSize().getWidth() - 5;
  }

  public void zoomOut() {
    myZoomToFit = false;
    if (myRenderResult.getImage() != null) {
      myRenderResult.getImage().zoomOut();
    }
    doRevalidate();
  }

  public void zoomIn() {
    myZoomToFit = false;
    if (myRenderResult.getImage() != null) {
      myRenderResult.getImage().zoomIn();
    }
    doRevalidate();
  }

  public void zoomActual() {
    myZoomToFit = false;
    if (myRenderResult.getImage() != null) {
      myRenderResult.getImage().zoomActual();
    }
    doRevalidate();
  }

  public void setZoomToFit(boolean zoomToFit) {
    myZoomToFit = zoomToFit;
    doRevalidate();
  }

  public boolean isZoomToFit() {
    return myZoomToFit;
  }

  @Override
  public void dispose() {
    if (myPreviewManager != null) {
      myPreviewManager.dispose();
      myPreviewManager = null;
    }
    myErrorPanel.dispose();
    myErrorPanel = null;
  }

  // RenderContext helpers

  public boolean hasAlphaChannel() {
    return myRenderResult.getImage() != null && !myRenderResult.getImage().getShowDropShadow();
  }

  @NotNull
  public Dimension getFullImageSize() {
    if (myRenderResult != null) {
      ScalableImage scaledImage = myRenderResult.getImage();
      if (scaledImage != null) {
        return new Dimension(scaledImage.getOriginalWidth(), scaledImage.getOriginalHeight());
      }
    }

    return RenderContext.NO_SIZE;
  }

  @NotNull
  public Dimension getScaledImageSize() {
    if (myRenderResult != null) {
      ScalableImage scaledImage = myRenderResult.getImage();
      if (scaledImage != null) {
        return new Dimension(scaledImage.getScaledWidth(), scaledImage.getScaledHeight());
      }
    }

    return RenderContext.NO_SIZE;
  }

  @NotNull
  public Dimension getClientSize() {
    return myImagePanel.getParent().getSize();
  }

  @NotNull
  public Rectangle getClientArea() {
    return myImagePanel.getParent().getBounds();
  }

  public Component getRenderComponent() {
    return myImagePanel.getParent();
  }

  public void setPreviewManager(@Nullable RenderPreviewManager manager) {
    if (manager == myPreviewManager) {
      return;
    }
    Component renderComponent = getRenderComponent();
    if (myPreviewManager != null) {
      myPreviewManager.unregisterMouseListener(renderComponent);
      myPreviewManager.dispose();;
    }
    myPreviewManager = manager;
    if (myPreviewManager != null) {
      myPreviewManager.registerMouseListener(renderComponent);
    }
  }

  @Nullable
  public RenderPreviewManager getPreviewManager(@Nullable RenderContext context, boolean createIfNecessary) {
    if (myPreviewManager == null && createIfNecessary && context != null) {
      setPreviewManager(new RenderPreviewManager(context));
    }

    return myPreviewManager;
  }

  public void setMaxSize(int width, int height) {
    ScalableImage scaledImage = myRenderResult.getImage();
    if (scaledImage != null) {
      scaledImage.setMaxSize(width, height);
      scaledImage.setUseLargeShadows(false);
    }
    myTitlePanel.setVisible(width <= 0);
  }

  /**
   * Layered pane which shows the rendered image, as well as (if applicable) an error message panel on top of the rendering
   * near the bottom
   */
  private class MyImagePanelWrapper extends JBLayeredPane {
    public MyImagePanelWrapper() {
      add(myImagePanel);
      setBackground(null);
      setOpaque(false);
    }

    @Override
    public void revalidate() {
      super.revalidate();
    }

    @Override
    public void doLayout() {
      super.doLayout();
      positionErrorPanel();

      if (myPreviewManager == null || !myPreviewManager.hasPreviews()) {
        centerComponents();
      } else {
        if (myRenderResult != null) {
          ScalableImage image = myRenderResult.getImage();
          if (image != null) {
            int fixedWidth = image.getMaxWidth();
            int fixedHeight = image.getFixedHeight();
            if (fixedWidth > 0) {
              myImagePanel.setLocation(Math.max(0, (fixedWidth - image.getScaledWidth()) / 2),
                                       2 + Math.max(0, (fixedHeight - image.getScaledHeight()) / 2));
              return;
            }
          }
        }

        myImagePanel.setLocation(0, 0);
      }
    }

    private void centerComponents() {
      Rectangle bounds = getBounds();
      Point point = myImagePanel.getLocation();
      point.x = (bounds.width - myImagePanel.getWidth()) / 2;

      // If we're squeezing the image to fit, and there's a drop shadow showing
      // shift *some* space away from the tail portion of the drop shadow over to
      // the left to make the image look more balanced
      if (myRenderResult != null) {
        if (point.x <= 2) {
          ScalableImage image = myRenderResult.getImage();
          // If there's a drop shadow
          if (image != null) {
            if (image.getShowDropShadow()) {
              point.x += ShadowPainter.SHADOW_SIZE / 3;
            }
          }
        }
        if (point.y <= 2) {
          ScalableImage image = myRenderResult.getImage();
          // If there's a drop shadow
          if (image != null) {
            if (image.getShowDropShadow()) {
              point.y += ShadowPainter.SHADOW_SIZE / 3;
            }
          }
        }
      }
      myImagePanel.setLocation(point);
    }

    private void positionErrorPanel() {
      int height = getHeight();
      int width = getWidth();
      int size;
      if (SIZE_ERROR_PANEL_DYNAMICALLY) {
        if (myErrorPanelHeight == -1) {
          // Make the layout take up to 3/4ths of the height, and at least 1/4th, but
          // anywhere in between based on what the actual text requires
          size = height * 3 / 4;
          int preferredHeight = myErrorPanel.getPreferredHeight(width) + 8;
          if (preferredHeight < size) {
            size = Math.max(preferredHeight, Math.min(height / 4, size));
            myErrorPanelHeight = size;
          }
        } else {
          size = myErrorPanelHeight;
        }
      } else {
        size = height / 2;
      }

      myErrorPanel.setSize(width, size);
      myErrorPanel.setLocation(0, height - size);
    }


    @Override
    protected void paintChildren(Graphics graphics) {
      // Done as part of paintChildren rather than paintComponent to ensure these are painted after the myImagePanel render.
      // This is to avoid having the drop shadow painting of the main image overlap on top of the previews to its right and
      // below.
      if (myPreviewManager != null) {
        myPreviewManager.paint((Graphics2D)graphics);
      }

      super.paintChildren(graphics);
    }

    @Override
    public Dimension getPreferredSize() {
      return myImagePanel.getSize();
    }
  }
}
