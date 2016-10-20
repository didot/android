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
package com.android.tools.idea.monitor.ui;

import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.Range;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.common.RotatedLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.util.List;

public abstract class BaseSegment extends JComponent {

  private static final int SPACER_WIDTH = 100;

  /**
   * Top/bottom border between segments.
   */
  private static final Border SEGMENT_BORDER = new CompoundBorder(new MatteBorder(0, 0, 1, 0, AdtUiUtils.DEFAULT_BORDER_COLOR),
                                                                   new EmptyBorder(0, 0, 0, 0));

  private static final int LABEL_BORDER_WIDTH = 2;

  /**
   * Border around the segment label.
   */
  private static final Border LABEL_BORDER = new MatteBorder(0, 0, 0, LABEL_BORDER_WIDTH, AdtUiUtils.DEFAULT_BORDER_COLOR);

  private JPanel mRightPanel;

  private JPanel mLeftPanel;

  private JPanel mLabelPanel;

  private RotatedLabel mLabel;

  @NotNull
  protected final String myName;

  @NotNull
  protected Range myTimeCurrentRangeUs;

  @NotNull
  protected final EventDispatcher<ProfilerEventListener> mEventDispatcher;

  public BaseSegment(@NotNull String name, @NotNull Range timeCurrentRangeUs, @NotNull EventDispatcher<ProfilerEventListener> dispatcher) {
    myName = name;
    myTimeCurrentRangeUs = timeCurrentRangeUs;
    mEventDispatcher = dispatcher;
  }

  public static int getSpacerWidth() {
    return SPACER_WIDTH;
  }

  public void initializeComponents() {
    setLayout(new BorderLayout());

    FontMetrics metrics = getFontMetrics(AdtUiUtils.DEFAULT_FONT);
    mLabelPanel = createSpacerPanel(metrics.getHeight() + LABEL_BORDER_WIDTH);
    mLabelPanel.setBorder(LABEL_BORDER);
    mLabel = new RotatedLabel();
    mLabel.setFont(AdtUiUtils.DEFAULT_FONT);
    mLabel.setText(myName);
    mLabel.setBorder(SEGMENT_BORDER);
    mLabelPanel.add(mLabel);
    this.add(mLabelPanel, BorderLayout.WEST);

    JBPanel panels = new JBPanel();
    panels.setBorder(SEGMENT_BORDER);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.BOTH;
    panels.setLayout(new GridBagLayout());

    gbc.weightx = 0;
    gbc.weighty = 0;

    //Setup the left panel
    if (hasLeftContent()) {
      mLeftPanel = createSpacerPanel(getSpacerWidth());
      gbc.gridx = 0;
      gbc.gridy = 1;
      panels.add(mLeftPanel, gbc);
      setLeftContent(mLeftPanel);
    }

    //Setup the top center panel.
    JBPanel topPanel = new JBPanel();
    topPanel.setLayout(new BorderLayout());
    gbc.gridx = 1;
    gbc.gridy = 0;
    panels.add(topPanel, gbc);
    setTopCenterContent(topPanel);

    //Setup the right panel
    if (hasRightContent()) {
      mRightPanel = createSpacerPanel(getSpacerWidth());
      gbc.gridx = 2;
      gbc.gridy = 1;
      panels.add(mRightPanel, gbc);
      setRightContent(mRightPanel);
    }

    //Setup the center panel, the primary component.
    //This component should consume all available space.
    JBPanel centerPanel = new JBPanel();
    centerPanel.setLayout(new BorderLayout());
    gbc.weightx = 1;
    gbc.weighty = 1;
    gbc.gridx = 1;
    gbc.gridy = 1;
    panels.add(centerPanel, gbc);
    setCenterContent(centerPanel);

    add(panels, BorderLayout.CENTER);

    // By default, starts in L1, this gives the Segment a chance to hide the right spacer panel and determine what it should rendered.
    toggleView(false);
  }

  public int getLabelColumnWidth() {
    return mLabelPanel.getPreferredSize().width;
  }

  private JPanel createSpacerPanel(int spacerWidth) {
    JBPanel panel = new JBPanel();
    panel.setLayout(new BorderLayout());
    Dimension spacerDimension = new Dimension(spacerWidth, 0);
    panel.setPreferredSize(spacerDimension);
    panel.setMinimumSize(spacerDimension);
    return panel;
  }

  /**
   * This enables segments to toggle the visibility of the right panel.
   *
   * @param isVisible True indicates the panel is visible, false hides it.
   */
  public void setRightSpacerVisible(boolean isVisible) {
    if (hasRightContent()) {
      mRightPanel.setVisible(isVisible);
    }
  }

  public boolean isRightSpacerVisible() {
    return hasRightContent() && mRightPanel.isVisible();
  }

  /**
   * This enables segments to toggle the visibility of the left panel.
   *
   * @param isVisible True indicates the panel is visible, false hides it.
   */
  public void setLeftSpacerVisible(boolean isVisible) {
    if (hasLeftContent()) {
      mLeftPanel.setVisible(isVisible);
    }
  }

  public boolean isLeftSpacerVisible() {
    return hasLeftContent() && mLeftPanel.isVisible();
  }

  public void toggleView(boolean isExpanded) {
    setRightSpacerVisible(isExpanded);
  }

  public void createComponentsList(@NotNull List<Animatable> animatables) {}

  /**
   * A read-only flag that indicates whether this segment has content in the left spacer. If true, the segment will construct and reverse
   * space left of the center content, and {@link #setLeftContent(JPanel)} will be invoked. Subclasses of {@link BaseSegment} can override
   * this to change how the segment is laid out and whether it will participate in any transitions toggling between the overview and
   * detailed view.
   */
  protected boolean hasLeftContent() {
    return true;
  }

  protected void setLeftContent(@NotNull JPanel panel) {}

  protected abstract void setCenterContent(@NotNull JPanel panel);

  /**
   * A read-only flag that indicates whether this segment has content in the right spacer. If true, the segment will construct and reverse
   * space right of the center content, and {@link #setRightContent(JPanel)} will be invoked. Subclasses of {@link BaseSegment} can override
   * this to change how the segment is laid out and whether it will participate in any transitions toggling between the overview and
   * detailed view.
   */
  protected boolean hasRightContent() {
    return true;
  }

  protected void setRightContent(@NotNull JPanel panel) {}

  protected void setTopCenterContent(@NotNull JPanel panel) {}
}
