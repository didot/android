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
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.profilers.ContextMenuInstaller;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.stacktrace.ContextMenuItem;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;

import static com.android.tools.profilers.ProfilerColors.HOVERED_SESSION_COLOR;
import static com.android.tools.profilers.ProfilerColors.SELECTED_SESSION_COLOR;

/**
 * A view for showing different {@link SessionArtifact}'s in the sessions panel.
 */
public abstract class SessionArtifactView<T extends SessionArtifact> extends JPanel {

  protected static final Border ARTIFACT_ICON_BORDER = JBUI.Borders.empty(4, 0);
  protected static final Border SELECTED_BORDER = JBUI.Borders.customLine(SELECTED_SESSION_COLOR, 0, 3, 0, 0);
  protected static final Border UNSELECTED_BORDER = JBUI.Borders.empty(0, 3, 0, 0);

  protected static final Border ARTIFACT_PADDING = JBUI.Borders.empty(2, 9, 2, 4);
  protected static final Border LABEL_PADDING = JBUI.Borders.empty(1, 8, 1, 0);

  protected static final Font TITLE_FONT = AdtUiUtils.DEFAULT_FONT.biggerOn(3);
  protected static final Font STATUS_FONT = AdtUiUtils.DEFAULT_FONT.biggerOn(1);

  @NotNull private final T myArtifact;
  @NotNull private final ArtifactDrawInfo myArtifactDrawInfo;
  @NotNull protected final AspectObserver myObserver;

  @NotNull private final JComponent myArtifactView;

  @NotNull private final java.util.List<JComponent> myMouseListeningComponents;

  public SessionArtifactView(@NotNull ArtifactDrawInfo artifactDrawInfo, @NotNull T artifact) {
    myArtifactDrawInfo = artifactDrawInfo;
    myArtifact = artifact;
    myObserver = new AspectObserver();
    myMouseListeningComponents = new ArrayList<>();

    myArtifactView = buildComponent();
    myArtifactView.setBackground(HOVERED_SESSION_COLOR);
    myArtifactView.setOpaque(false);
    setLayout(new BorderLayout());
    add(myArtifactView, BorderLayout.CENTER);

    // Context menus set up
    ContextMenuInstaller contextMenuInstaller = artifactDrawInfo.mySessionsView.getIdeProfilerComponents().createContextMenuInstaller();
    getContextMenus().forEach(menu -> contextMenuInstaller.installGenericContextMenu(this, menu));

    addMouseListeningComponents(this);
    initializeMouseListeners();

    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        // When a component is first constructed, we need to check whether the mouse is already hovered. If so, draw the hover effect.
        PointerInfo info = MouseInfo.getPointerInfo();
        if (info != null) {
          Point mousePosition = info.getLocation();
          SwingUtilities.convertPointFromScreen(mousePosition, SessionArtifactView.this);
          showHoverState(SessionArtifactView.this.contains(mousePosition));
        }
      }
    });
  }

  /**
   * Adding other mouse listeners (e.g. setting tooltips) in children components within the SessionArtifactView will prevent mouse events
   * to propagate to the view itself for handling common logic such as selection and hover. Here we can add those children components to
   * also handle the common logic on their own.
   */
  protected void addMouseListeningComponents(@NotNull JComponent comp) {
    myMouseListeningComponents.add(comp);
  }

  private void initializeMouseListeners() {
    for (JComponent comp : myMouseListeningComponents) {
      // Mouse listener to handle selection and hover effects.
      comp.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          if (SwingUtilities.isLeftMouseButton(e)) {
            myArtifact.onSelect();
          }
        }

        @Override
        public void mouseEntered(MouseEvent e) {
          showHoverState(true);
        }

        @Override
        public void mouseExited(MouseEvent e) {
          showHoverState(false);
        }
      });
    }
  }

  @NotNull
  public SessionsView getSessionsView() {
    return myArtifactDrawInfo.mySessionsView;
  }

  @NotNull
  public T getArtifact() {
    return myArtifact;
  }

  @NotNull
  public StudioProfilers getProfilers() {
    return myArtifactDrawInfo.mySessionsView.getProfilers();
  }

  public boolean isSessionSelected() {
    return myArtifact.getSession().equals(myArtifact.getProfilers().getSessionsManager().getSelectedSession());
  }

  public int getIndex() {
    return myArtifactDrawInfo.myIndex;
  }

  protected java.util.List<ContextMenuItem> getContextMenus() {
    return Collections.emptyList();
  }

  protected abstract JComponent buildComponent();

  protected void showHoverState(boolean hover) {
    myArtifactView.setOpaque(hover);
    myArtifactView.repaint();
  }

  /**
   * Helper method to generate a standard view to display a session's capture artifact.
   */
  protected JComponent buildCaptureArtifactView(@NotNull String name,
                                                @NotNull String subtitle,
                                                @NotNull Icon icon,
                                                boolean isOngoing) {
    // 1st column for artifact's icon, 2nd column for texts
    // 1st row for showing name, 2nd row for time.
    JPanel panel = new JPanel(new TabularLayout("Fit,*", "Fit,Fit"));

    if (isOngoing) {
      AsyncProcessIcon loadingIcon = new AsyncProcessIcon("");
      loadingIcon.setBorder(ARTIFACT_ICON_BORDER);
      panel.add(loadingIcon, new TabularLayout.Constraint(0, 0));
    }
    else {
      JLabel iconLabel = new JLabel(icon);
      iconLabel.setBorder(ARTIFACT_ICON_BORDER);
      panel.add(iconLabel, new TabularLayout.Constraint(0, 0));
    }

    JLabel artifactName = new JLabel(name);
    artifactName.setBorder(LABEL_PADDING);
    artifactName.setFont(TITLE_FONT);

    JLabel artifactTime = new JLabel(subtitle);
    artifactTime.setBorder(LABEL_PADDING);
    artifactTime.setFont(STATUS_FONT);
    panel.add(artifactName, new TabularLayout.Constraint(0, 1));
    panel.add(artifactTime, new TabularLayout.Constraint(1, 1));

    // Listen to selected session changed so we can update the selection visuals accordingly.
    final Border selectedBorder = BorderFactory.createCompoundBorder(SELECTED_BORDER, ARTIFACT_PADDING);
    final Border unSelectedBorder = BorderFactory.createCompoundBorder(UNSELECTED_BORDER, ARTIFACT_PADDING);
    getProfilers().getSessionsManager().addDependency(myObserver).onChange(SessionAspect.SELECTED_SESSION, () ->
      panel.setBorder(isSessionSelected() ? selectedBorder : unSelectedBorder)
    );
    panel.setBorder(isSessionSelected() ? selectedBorder : unSelectedBorder);

    return panel;
  }

  /**
   * Helper object to wrap information related to the states of the cell in which a {@link SessionArtifactView} belongs.
   */
  public static class ArtifactDrawInfo {
    @NotNull final SessionsView mySessionsView;
    final int myIndex;

    ArtifactDrawInfo(@NotNull SessionsView sessionsView, int index) {
      mySessionsView = sessionsView;
      myIndex = index;
    }
  }
}
