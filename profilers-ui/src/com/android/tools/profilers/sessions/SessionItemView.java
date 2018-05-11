/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.sessions;

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.stdui.StandardColors;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.ProfilerAction;
import com.android.tools.profilers.stacktrace.ContextMenuItem;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.JBUI;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.font.TextAttribute;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static com.android.tools.profilers.ProfilerColors.ACTIVE_SESSION_COLOR;
import static com.android.tools.profilers.ProfilerColors.SESSION_DIVIDER_COLOR;
import static com.android.tools.profilers.ProfilerLayout.TOOLBAR_ICON_BORDER;

/**
 * A {@link SessionArtifactView} that represents a {@link com.android.tools.profiler.proto.Common.Session}
 */
public final class SessionItemView extends SessionArtifactView<SessionItem> {

  private static final Border DIVIDER_BORDER = JBUI.Borders.customLine(SESSION_DIVIDER_COLOR, 1, 0, 0, 0);
  private static final Border COMPONENT_PADDING = JBUI.Borders.empty(4, 2, 4, 4);
  private static final Font SESSION_TIME_FONT =
    TITLE_FONT.deriveFont(Collections.singletonMap(TextAttribute.WEIGHT, TextAttribute.WEIGHT_DEMIBOLD));

  public SessionItemView(@NotNull ArtifactDrawInfo artifactDrawInfo, @NotNull SessionItem artifact) {
    super(artifactDrawInfo, artifact);
  }

  @Override
  protected JComponent buildComponent() {
    // 1st column reserved for the Session's title (time), 2nd column for the live session icon.
    // 1st row for showing session start time, 2nd row for name, 3rd row for duration
    JPanel panel = new JPanel(new TabularLayout("Fit,Fit,*", "Fit,Fit,Fit"));

    boolean isSessionAlive = SessionsManager.isSessionAlive(getArtifact().getSession());
    DateFormat timeFormat = new SimpleDateFormat("hh:mm a");
    JLabel startTime = new JLabel(timeFormat.format(new Date(getArtifact().getSessionMetaData().getStartTimestampEpochMs())));
    startTime.setBorder(LABEL_PADDING);
    startTime.setFont(SESSION_TIME_FONT);
    startTime.setForeground(StandardColors.TEXT_COLOR);
    panel.add(startTime, new TabularLayout.Constraint(0, 0));
    JPanel liveDotWrapper = new JPanel(new BorderLayout());
    // Session is ongoing.
    if (isSessionAlive) {
      liveDotWrapper.setBorder(TOOLBAR_ICON_BORDER);
      liveDotWrapper.setOpaque(false);
      LiveSessionDot liveDot = new LiveSessionDot();
      liveDot.setToolTipText("Currently Profiling");
      addMouseListeningComponents(liveDot);
      liveDotWrapper.add(liveDot, BorderLayout.CENTER);
      panel.add(liveDotWrapper, new TabularLayout.Constraint(0, 1));
    }

    JLabel sessionName = new JLabel(getArtifact().getName());
    sessionName.setBorder(LABEL_PADDING);
    sessionName.setFont(STATUS_FONT);
    sessionName.setForeground(StandardColors.TEXT_COLOR);
    // Display a tooltip in case there isn't enough space to show the full name in the session's panel.
    sessionName.setToolTipText(getArtifact().getName());
    addMouseListeningComponents(sessionName);
    panel.add(sessionName, new TabularLayout.Constraint(1, 0, 1, 3));

    JLabel durationLabel = new JLabel(getArtifact().getSubtitle());
    durationLabel.setBorder(LABEL_PADDING);
    durationLabel.setFont(STATUS_FONT);
    durationLabel
      .setForeground(AdtUiUtils.overlayColor(durationLabel.getBackground().getRGB(), StandardColors.TEXT_COLOR.getRGB(), 0.6f));
    // Only show duration for non-imported sessions.
    if (getArtifact().getSessionMetaData().getType() == Common.SessionMetaData.SessionType.FULL) {
      panel.add(durationLabel, new TabularLayout.Constraint(2, 0, 1, 3));
    }

    // TODO (b/78520629) - TabularLayout currently does not account for size of components that span multiple row/column, hence we are
    // inserting a filler in occupy the blank space in 1st row, 3rd col to make sure the dimensions of the session name + duration labels
    // are accounted for instead.
    int fillerWidth = Math.max(sessionName.getMinimumSize().width, durationLabel.getMinimumSize().width) -
                      startTime.getMinimumSize().width - (isSessionAlive ? liveDotWrapper.getMinimumSize().width : 0);
    Dimension fillerDimension = new Dimension(fillerWidth, 1);
    Box.Filler filler = new Box.Filler(fillerDimension, fillerDimension, fillerDimension);
    panel.add(filler, new TabularLayout.Constraint(0, 2));

    getArtifact().addDependency(myObserver).onChange(SessionItem.Aspect.MODEL, () -> {
      durationLabel.setText(getArtifact().getSubtitle());
      int updatedFillerWidth = Math.max(sessionName.getMinimumSize().width, durationLabel.getMinimumSize().width) -
                               startTime.getMinimumSize().width - (isSessionAlive ? liveDotWrapper.getMinimumSize().width : 0);
      Dimension updatedFillerDimension = new Dimension(updatedFillerWidth, 1);
      filler.changeShape(updatedFillerDimension, updatedFillerDimension, updatedFillerDimension);
    });

    // Listen to selected session changed so we can update the selection visuals accordingly.
    getProfilers().getSessionsManager().addDependency(myObserver).onChange(SessionAspect.SELECTED_SESSION, () -> updateBorder(panel));
    updateBorder(panel);

    return panel;
  }

  private void updateBorder(@NotNull JPanel panel) {
    Border selectionBorder = isSessionSelected() ?
                             JBUI.Borders.merge(SELECTED_BORDER, COMPONENT_PADDING, false) :
                             JBUI.Borders.merge(UNSELECTED_BORDER, COMPONENT_PADDING, false);
    // Skip the top border for the first entry as that would duplicate with the toolbar's border
    panel.setBorder(getIndex() == 0 ? selectionBorder : JBUI.Borders.merge(DIVIDER_BORDER, selectionBorder, false));
  }

  @Override
  protected List<ContextMenuItem> getContextMenus() {
    boolean canEndSession = SessionsManager.isSessionAlive(getArtifact().getSession());
    Icon endIcon =
      canEndSession ? StudioIcons.Profiler.Toolbar.STOP_SESSION : IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.STOP_SESSION);
    ProfilerAction endAction = new ProfilerAction.Builder("End session")
      .setEnableBooleanSupplier(() -> canEndSession)
      .setActionRunnable(() -> getSessionsView().stopProfilingSession())
      .setIcon(endIcon)
      .build();
    ProfilerAction deleteAction = new ProfilerAction.Builder("Delete")
      .setActionRunnable(() -> getArtifact().deleteSession())
      .build();

    return ImmutableList.of(endAction, ContextMenuItem.SEPARATOR, deleteAction);
  }

  /**
   * A component for rendering a green dot in {@link SessionItemView} to indicate that the session is ongoing.
   */
  private static class LiveSessionDot extends JComponent {

    private static final int SIZE = JBUI.scale(10);
    private static final Dimension DIMENSION = new Dimension(SIZE, SIZE);

    @Override
    public Dimension getMinimumSize() {
      return DIMENSION;
    }

    @Override
    public Dimension getMaximumSize() {
      return DIMENSION;
    }

    @Override
    public Dimension getPreferredSize() {
      return DIMENSION;
    }

    @Override
    public void paint(Graphics g) {
      Graphics2D g2d = (Graphics2D)g.create();
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2d.setColor(ACTIVE_SESSION_COLOR);
      g2d.fillOval(0, 0, SIZE, SIZE);
      g2d.dispose();
    }
  }
}
