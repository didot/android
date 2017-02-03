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
package com.android.tools.profilers.network;

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.TreeWalker;
import com.android.tools.profilers.common.StackTraceView;
import com.android.tools.profilers.common.TabsPanel;
import com.google.common.collect.ImmutableMap;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.labels.BoldLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.PlatformColors;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

/**
 * View to display a single network request and its response's detailed information.
 */
public class ConnectionDetailsView extends JPanel {
  private static final int PAGE_VGAP = JBUI.scale(32);
  private static final int SECTION_VGAP = JBUI.scale(10);
  private static final int HGAP = JBUI.scale(22);
  private static final int SCROLL_UNIT = JBUI.scale(10);
  private static final float TITLE_FONT_SIZE = 14.f;
  private static final float FIELD_FONT_SIZE = 11.f;

  @NotNull
  private final JPanel myResponsePanel;
  @NotNull
  private final JPanel myHeadersPanel;
  @NotNull
  private final StackTraceView myStackTraceView;

  @NotNull
  private final NetworkProfilerStageView myStageView;

  public ConnectionDetailsView(@NotNull NetworkProfilerStageView stageView) {
    super(new BorderLayout());
    myStageView = stageView;
    // Create 2x2 pane
    //     * Fit
    // Fit _ _
    // *   _ _
    //
    // where main contents span the whole area and a close button fits into the top right
    JPanel rootPanel = new JPanel(new TabularLayout("*,Fit", "Fit,*"));

    TabsPanel tabsPanel = stageView.getIdeComponents().createTabsPanel();

    TabularLayout layout = new TabularLayout("*").setVGap(PAGE_VGAP);
    myResponsePanel = new JPanel(layout);
    myResponsePanel.setBorder(BorderFactory.createEmptyBorder(PAGE_VGAP, HGAP, 0, HGAP));
    myResponsePanel.setName("Response");
    JBScrollPane responseScroll = new JBScrollPane(myResponsePanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                   ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    responseScroll.getVerticalScrollBar().setUnitIncrement(SCROLL_UNIT);
    responseScroll.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        layout.setRowSizing(0, new TabularLayout.SizingRule(TabularLayout.SizingRule.Type.FIXED,
                                                            (int)(responseScroll.getViewport().getHeight() * 0.4f)));
        layout.layoutContainer(myResponsePanel);
      }
    });

    tabsPanel.addTab("Response", responseScroll);

    myHeadersPanel = new JPanel(new VerticalFlowLayout(0, PAGE_VGAP));
    myHeadersPanel.setName("Headers");
    JBScrollPane headersScroll = new JBScrollPane(myHeadersPanel);
    headersScroll.getVerticalScrollBar().setUnitIncrement(SCROLL_UNIT);
    headersScroll.getHorizontalScrollBar().setUnitIncrement(SCROLL_UNIT);
    tabsPanel.addTab("Headers", headersScroll);

    myStackTraceView = myStageView.getIdeComponents().createStackView(null);
    myStackTraceView.getComponent().setName("StackTrace");
    tabsPanel.addTab("Call Stack", myStackTraceView.getComponent());

    IconButton closeIcon = new IconButton("Close", AllIcons.Actions.Close, AllIcons.Actions.CloseHovered);
    InplaceButton closeButton = new InplaceButton(closeIcon, e -> this.update((HttpData)null));
    closeButton.setMinimumSize(closeButton.getPreferredSize()); // Prevent layout phase from squishing this button

    rootPanel.add(closeButton, new TabularLayout.Constraint(0, 1));
    rootPanel.add(tabsPanel.getComponent(), new TabularLayout.Constraint(0, 0, 2, 2));

    add(rootPanel);
  }

  /**
   * Updates the view to show given data. If given {@code httpData} is not null, show the details and set the view to be visible;
   * otherwise, clears the view and set view to be invisible.
   */
  public void update(@Nullable HttpData httpData) {
    setBackground(JBColor.background());
    myResponsePanel.removeAll();
    myHeadersPanel.removeAll();
    myStackTraceView.clearStackFrames();

    if (httpData != null) {
      FileViewer fileViewer = createFileViewer(httpData);
      myResponsePanel.add(fileViewer.getComponent(), new TabularLayout.Constraint(0, 0));
      myResponsePanel.add(createFields(httpData, fileViewer.getDimension()), new TabularLayout.Constraint(1, 0));

      myHeadersPanel.add(createHeaderSection("Response Headers", httpData.getResponseHeaders()));
      myHeadersPanel.add(new JSeparator());
      myHeadersPanel.add(createHeaderSection("Request Headers", httpData.getRequestHeaders()));

      myStackTraceView.setStackFrames(httpData.getTrace());
    }
    setVisible(httpData != null);
    revalidate();
  }

  private static JComponent createFields(@NotNull HttpData httpData, @Nullable Dimension payloadDimension) {
    JPanel myFieldsPanel = new JPanel(new TabularLayout("Fit,20px,*").setVGap(SECTION_VGAP));

    int row = 0;
    myFieldsPanel.add(new NoWrapBoldLabel("Request"), new TabularLayout.Constraint(row, 0));
    myFieldsPanel.add(new JLabel(HttpData.getUrlName(httpData.getUrl())), new TabularLayout.Constraint(row, 2));

    row++;
    myFieldsPanel.add(new NoWrapBoldLabel("Method"), new TabularLayout.Constraint(row, 0));
    myFieldsPanel.add(new JLabel(httpData.getMethod()), new TabularLayout.Constraint(row, 2));

    if (httpData.getStatusCode() != HttpData.NO_STATUS_CODE) {
      row++;
      myFieldsPanel.add(new NoWrapBoldLabel("Status"), new TabularLayout.Constraint(row, 0));
      JLabel statusCode = new JLabel(String.valueOf(httpData.getStatusCode()));
      statusCode.setName("StatusCode");
      myFieldsPanel.add(statusCode, new TabularLayout.Constraint(row, 2));
    }

    if (payloadDimension != null) {
      row++;
      myFieldsPanel.add(new NoWrapBoldLabel("Dimension"), new TabularLayout.Constraint(row, 0));
      JLabel dimension = new JLabel(String.format("%d x %d", (int) payloadDimension.getWidth(), (int) payloadDimension.getHeight()));
      dimension.setName("Dimension");
      myFieldsPanel.add(dimension, new TabularLayout.Constraint(row, 2));
    }

    if (httpData.getContentType() != null) {
      row++;
      myFieldsPanel.add(new NoWrapBoldLabel("Content type"), new TabularLayout.Constraint(row, 0));
      JLabel contentTypeLabel = new JLabel(httpData.getContentType().getMimeType());
      contentTypeLabel.setName("Content type");
      myFieldsPanel.add(contentTypeLabel, new TabularLayout.Constraint(row, 2));
    }

    String contentLength = httpData.getResponseField(HttpData.FIELD_CONTENT_LENGTH);
    if (contentLength != null) {
      contentLength = contentLength.split(";")[0];
      try {
        long number = Long.parseUnsignedLong(contentLength);
        row++;
        myFieldsPanel.add(new NoWrapBoldLabel("Size"), new TabularLayout.Constraint(row, 0));
        JLabel contentLengthLabel = new JLabel(StringUtil.formatFileSize(number));
        contentLengthLabel.setName("Size");
        myFieldsPanel.add(contentLengthLabel, new TabularLayout.Constraint(row, 2));
      } catch (NumberFormatException ignored) {}
    }

    row++;
    NoWrapBoldLabel urlLabel = new NoWrapBoldLabel("URL");
    urlLabel.setVerticalAlignment(SwingConstants.TOP);
    myFieldsPanel.add(urlLabel, new TabularLayout.Constraint(row, 0));
    WrappedHyperlink hyperlink = new WrappedHyperlink(httpData.getUrl());
    hyperlink.setName("URL");
    myFieldsPanel.add(hyperlink, new TabularLayout.Constraint(row, 2));

    new TreeWalker(myFieldsPanel).descendantStream().forEach(c -> {
      Font font = c.getFont();
      c.setFont(font.deriveFont(FIELD_FONT_SIZE));
    });

    myFieldsPanel.setName("Response fields");
    return myFieldsPanel;
  }

  @NotNull
  private static JPanel createHeaderSection(@NotNull String title, @NotNull Map<String, String> map) {
    JPanel panel = new JPanel(new GridLayout(0, 1, 0, SECTION_VGAP));
    panel.setBorder(BorderFactory.createEmptyBorder(0, HGAP, 0, 0));

    JLabel titleLabel = new NoWrapBoldLabel(title);
    titleLabel.setFont(titleLabel.getFont().deriveFont(TITLE_FONT_SIZE));
    panel.add(titleLabel);

    if (map.isEmpty()) {
      JLabel emptyLabel = new JLabel("No data available");
      // TODO: Adjust color.
      panel.add(emptyLabel);
    } else {
      Map<String, String> sortedMap = new TreeMap<>(map);
      Border rowKeyBorder = BorderFactory.createEmptyBorder(0, 0, 0, JBUI.scale(18));
      for (Map.Entry<String, String> entry : sortedMap.entrySet()) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JLabel keyLabel = new NoWrapBoldLabel(entry.getKey() + ":");
        keyLabel.setBorder(rowKeyBorder);
        row.add(keyLabel);
        row.add(new JLabel(entry.getValue()));
        row.setName(entry.getKey());
        panel.add(row);
      }
    }

    new TreeWalker(panel).descendantStream().forEach(c -> {
      if (c != titleLabel) {
        Font font = c.getFont();
        c.setFont(font.deriveFont(FIELD_FONT_SIZE));
      }
    });

    // Set name so tests can get a handle to this panel.
    panel.setName(title);
    return panel;
  }

  @NotNull
  public StackTraceView getStackTraceView() {
    return myStackTraceView;
  }

  /**
   * This is a label with bold font and does not wrap.
   */
  private static final class NoWrapBoldLabel extends BoldLabel {
    public NoWrapBoldLabel(String text) {
      super("<nobr>" + text + "</nobr>");
    }
  }

  @NotNull
  private static FileViewer createFileViewer(@NotNull HttpData httpData) {
    JComponent component = null;
    Dimension dimension = null;
    if (httpData.getResponsePayloadFile() == null) {
      component = new JLabel("No preview available");
      component.setFont(component.getFont().deriveFont(TITLE_FONT_SIZE));
    }

    String contentType = httpData.getResponseField(HttpData.FIELD_CONTENT_TYPE);
    if (component == null && contentType != null && StringUtil.containsIgnoreCase(contentType, "image")) {
      try {
        BufferedImage image = ImageIO.read(httpData.getResponsePayloadFile());
        component = new ResizableImage(image);
        dimension = new Dimension(image.getWidth(), image.getHeight());
      }
      catch (IOException ignored) {
      }
    }

    if (component == null) {
      // TODO: Fix the viewer for html, json and etc.
      JTextArea textArea = new JTextArea();
      try {
        BufferedReader reader = new BufferedReader(new FileReader(httpData.getResponsePayloadFile()));
        textArea.read(reader, null);
        reader.close();
      }
      catch (IOException ignored) {}
      textArea.setEditable(false);
      textArea.setLineWrap(true);
      component = new JBScrollPane(textArea);
    }

    return new FileViewer(component, dimension);
  }

  private static class FileViewer {
    @NotNull
    private final JComponent myComponent;
    @Nullable
    private final Dimension myDimension;

    public FileViewer(@NotNull JComponent component, @Nullable Dimension dimension) {
      myComponent = component;
      myDimension = dimension;
      myComponent.setName("FileViewer");
    }

    @NotNull
    public JComponent getComponent() {
      return myComponent;
    }

    /**
     * The (width x height) size of the target file, or {@code null} if the concept of a size
     * doesn't make sense for the file type (e.g. txt, xml)
     */
    @Nullable
    public Dimension getDimension() {
      return myDimension;
    }
  }

  /**
   * This is an image which can be resized but maintains its aspect ratio.
   */
  public static class ResizableImage extends JLabel {

    @NotNull private final BufferedImage myImage;
    @Nullable private Dimension myLastSize;

    /**
     * Check if two dimension objects are basically the same size, plus or minus a pixel. This
     * works around the fact that calculating the rescaled size of an image occasionally produces
     * off-by-one rounding errors, letting us avoid triggering an expensive image regeneration for
     * such a small change.
     */
    private static boolean areSimilarSizes(@NotNull Dimension d1, @NotNull Dimension d2) {
      return Math.abs(d2.width - d1.width) <= 1 && Math.abs(d2.height - d1.height) <= 1;
    }

    public ResizableImage(@NotNull BufferedImage image) {
      super("", CENTER);
      myImage = image;

      addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          resize();
        }
      });
    }

    private void resize() {
      Dimension d = calculateScaledSize();

      if (d.width == 0 || d.height == 0) {
        setIcon(null);
        myLastSize = null;
      }
      else if (myLastSize == null || !areSimilarSizes(myLastSize, d)) {
        Image image = d.getWidth() == myImage.getWidth() ? myImage : myImage.getScaledInstance(d.width, d.height, Image.SCALE_SMOOTH);
        setIcon(new ImageIcon(image));
        myLastSize = d;
      }
    }

    @NotNull
    private Dimension calculateScaledSize() {
      if (getWidth() == 0 || getHeight() == 0) {
        return new Dimension();
      }

      float sourceRatio = (float)myImage.getWidth() / myImage.getHeight();
      int finalWidth = getWidth();
      int finalHeight = (int) (finalWidth / sourceRatio);

      // Don't allow the final size to be larger than the original image, in order to prevent small
      // images from stretching into a blurry mess.
      int maxWidth = Math.min(getWidth(), myImage.getWidth());
      int maxHeight = Math.min(getHeight(), myImage.getHeight());

      if (finalWidth > maxWidth) {
        float scale = (float)maxWidth / finalWidth;
        finalWidth *= scale;
        finalHeight *= scale;
      }
      if (finalHeight > maxHeight) {
        float scale = (float)maxHeight / finalHeight;
        finalWidth *= scale;
        finalHeight *= scale;
      }

      return new Dimension(finalWidth, finalHeight);
    }
  }

  /**
   * This is a hyperlink which will break and wrap when it hits the right border of its container.
   */
  private static class WrappedHyperlink extends JTextArea {

    public WrappedHyperlink(@NotNull String url) {
      super(url);
      setLineWrap(true);
      setEditable(false);
      setBackground(UIUtil.getLabelBackground());
      setFont(getFont().deriveFont(ImmutableMap.of(
        TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON,
        TextAttribute.FOREGROUND, PlatformColors.BLUE,
        TextAttribute.BACKGROUND, UIUtil.getLabelBackground())));

      MouseAdapter mouseAdapter = getMouseAdapter(url);
      addMouseListener(mouseAdapter);
      addMouseMotionListener(mouseAdapter);
    }

    private MouseAdapter getMouseAdapter(String url) {
      return new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent e) {
          mouseMoved(e);
        }

        @Override
        public void mouseExited(MouseEvent e) {
          setCursor(Cursor.getDefaultCursor());
        }

        @Override
        public void mouseClicked(MouseEvent e) {
          if (isMouseOverText(e)) {
            BrowserUtil.browse(url);
          }
        }

        @Override
        public void mouseMoved(MouseEvent e) {
          setCursor(isMouseOverText(e) ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        }

        private boolean isMouseOverText(MouseEvent e) {
          return viewToModel(e.getPoint()) < getDocument().getLength();
        }
      };
    }
  }
}
