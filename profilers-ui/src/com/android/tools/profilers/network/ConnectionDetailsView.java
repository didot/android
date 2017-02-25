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

import com.android.tools.adtui.LegendComponent;
import com.android.tools.adtui.LegendConfig;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.TreeWalker;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.legend.FixedLegend;
import com.android.tools.adtui.model.legend.Legend;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.common.FileViewer;
import com.android.tools.profilers.common.StackTraceView;
import com.android.tools.profilers.common.TabsPanel;
import com.android.tools.profilers.common.ThreadId;
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

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongFunction;

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
  private static final LongFunction<String> TIME_FORMATTER =
    time -> time > 0 ? StringUtil.formatDuration(TimeUnit.MICROSECONDS.toMillis(time)) : "*";

  @NotNull
  private final JPanel myResponsePanel;
  @NotNull
  private final JPanel myHeadersPanel;
  @NotNull
  private final StackTraceView myStackTraceView;

  @NotNull
  private final NetworkProfilerStageView myStageView;

  @NotNull
  private final TabsPanel myTabsPanel;

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

    myTabsPanel = stageView.getIdeComponents().createTabsPanel();

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

    myTabsPanel.addTab("Response", responseScroll);

    myHeadersPanel = new JPanel(new VerticalFlowLayout(0, PAGE_VGAP));
    myHeadersPanel.setName("Headers");
    JBScrollPane headersScroll = new JBScrollPane(myHeadersPanel);
    headersScroll.getVerticalScrollBar().setUnitIncrement(SCROLL_UNIT);
    headersScroll.getHorizontalScrollBar().setUnitIncrement(SCROLL_UNIT);
    myTabsPanel.addTab("Headers", headersScroll);

    myStackTraceView = myStageView.getIdeComponents().createStackView(stageView.getStage().getStackTraceModel());
    myStackTraceView.getComponent().setName("StackTrace");
    myTabsPanel.addTab("Call Stack", myStackTraceView.getComponent());

    IconButton closeIcon = new IconButton("Close", AllIcons.Actions.Close, AllIcons.Actions.CloseHovered);
    InplaceButton closeButton = new InplaceButton(closeIcon, e -> this.setHttpData(null));
    closeButton.setMinimumSize(closeButton.getPreferredSize()); // Prevent layout phase from squishing this button

    rootPanel.add(closeButton, new TabularLayout.Constraint(0, 1));
    rootPanel.add(myTabsPanel.getComponent(), new TabularLayout.Constraint(0, 0, 2, 2));

    add(rootPanel);
  }

  /**
   * Updates the view to show given data. If {@code httpData} is {@ocde null}, this clears the view
   * and closes it.
   */
  public void setHttpData(@Nullable HttpData httpData) {
    setBackground(JBColor.background());
    myResponsePanel.removeAll();
    myHeadersPanel.removeAll();

    if (httpData != null) {
      Optional<File> payloadFile = Optional.ofNullable(httpData.getResponsePayloadFile());
      FileViewer fileViewer = myStageView.getIdeComponents().createFileViewer(payloadFile.orElse(new File("")));
      fileViewer.getComponent().setName("FileViewer");
      myResponsePanel.add(fileViewer.getComponent(), new TabularLayout.Constraint(0, 0));
      myResponsePanel.add(createFields(httpData, fileViewer.getDimension()), new TabularLayout.Constraint(1, 0));

      myHeadersPanel.add(createHeaderSection("Response Headers", httpData.getResponseHeaders()));
      myHeadersPanel.add(new JSeparator());
      myHeadersPanel.add(createHeaderSection("Request Headers", httpData.getRequestHeaders()));

      myStackTraceView.getModel().setStackFrames(ThreadId.INVALID_THREAD_ID, httpData.getStackTrace().getCodeLocations());
    }
    else {
      myStackTraceView.getModel().clearStackFrames();
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

    row++;
    JSeparator separator = new JSeparator();
    separator.setMinimumSize(separator.getPreferredSize());
    int gap = PAGE_VGAP - SECTION_VGAP - (int) separator.getPreferredSize().getHeight() / 2;
    JPanel separatorContainer = new JPanel(new VerticalFlowLayout(0, gap));
    separatorContainer.add(separator);
    myFieldsPanel.add(separatorContainer, new TabularLayout.Constraint(row, 0, 1, 3));

    row++;
    NoWrapBoldLabel timingLabel = new NoWrapBoldLabel("Timing");
    timingLabel.setVerticalAlignment(SwingConstants.TOP);
    myFieldsPanel.add(timingLabel, new TabularLayout.Constraint(row, 0));
    myFieldsPanel.add(createTimingBar(httpData), new TabularLayout.Constraint(row, 2));

    new TreeWalker(myFieldsPanel).descendantStream().forEach(c -> {
      Font font = c.getFont();
      c.setFont(font.deriveFont(FIELD_FONT_SIZE));
    });

    myFieldsPanel.setName("Response fields");
    return myFieldsPanel;
  }

  @NotNull
  private static JComponent createTimingBar(@NotNull HttpData httpData) {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    Range range = new Range(httpData.getStartTimeUs(),
                            httpData.getEndTimeUs() > 0 ? httpData.getEndTimeUs() : httpData.getStartTimeUs() + 1);
    RequestTimeline timeline = new RequestTimeline(httpData, range);
    timeline.getComponent().setMinimumSize(new Dimension(0, 28));
    panel.add(timeline.getComponent());

    long receivingTime = httpData.getDownloadingTimeUs() > 0 ? httpData.getEndTimeUs() - httpData.getDownloadingTimeUs() : -1;
    long sendingEndTime = receivingTime > 0 ? httpData.getDownloadingTimeUs() : httpData.getEndTimeUs();
    long sendingTime = sendingEndTime - httpData.getStartTimeUs();
    Legend sentLegend = new FixedLegend(String.format("Sent: %s", TIME_FORMATTER.apply(sendingTime)));
    Legend receivedLegend = new FixedLegend(String.format("Received: %s", TIME_FORMATTER.apply(receivingTime)));
    LegendComponentModel legendModel = new LegendComponentModel(ProfilerMonitor.LEGEND_UPDATE_FREQUENCY_MS);
    legendModel.add(sentLegend);
    legendModel.add(receivedLegend);

    // TODO: Add waiting time in (currently hidden because it's always 0)
    LegendComponent legend = new LegendComponent(legendModel);
    legend.configure(sentLegend,
                     new LegendConfig(LegendConfig.IconType.BOX, timeline.getColors().getColor(NetworkState.SENDING)));
    legend.configure(receivedLegend,
                     new LegendConfig(LegendConfig.IconType.BOX, timeline.getColors().getColor(NetworkState.RECEIVING)));
    legendModel.update(1);
    panel.add(legend);

    panel.setName("Timing");
    return panel;
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
