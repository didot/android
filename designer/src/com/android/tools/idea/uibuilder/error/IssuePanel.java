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
package com.android.tools.idea.uibuilder.error;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.surface.DesignSurface;
import com.google.common.collect.HashBiMap;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import icons.StudioIcons;
import org.jetbrains.android.uipreview.AndroidEditorSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Panel that displays a list of {@link NlIssue}.
 */
public class IssuePanel extends JPanel implements Disposable {
  private static final String ISSUE_PANEL_NAME = "Layout Editor Error Panel";
  private static final String TITLE_NO_ISSUES = "No issues";
  private static final String TITLE_NO_IMPORTANT_ISSUE = "Issues";
  private static final String WARNING = "Warning";
  private static final String ERROR = "Error";
  private static final String ACTION_PREVIOUS = "PREVIOUS";
  private static final String ACTION_NEXT = "next";
  private static final String ACTION_EXPAND = "expand";
  private static final String ACTION_COLLAPSE = "collapse";
  private static final String SHOW_ISSUES_CHECKBOX_TEXT = "Show issues on the preview";
  private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");

  private final HashBiMap<NlIssue, IssueView> myDisplayedError = HashBiMap.create();
  private final IssueModel myIssueModel;
  private final JPanel myErrorListPanel;
  private final JBLabel myTitleLabel;
  private final IssueModel.IssueModelListener myIssueModelListener;
  private final JBScrollPane myScrollPane;
  private final DesignSurface mySurface;
  @Nullable private MinimizeListener myMinimizeListener;
  @Nullable private IssueView mySelectedIssueView;

  /**
   * Whether the user has seen the issues or not. We consider the issues "seen" if the panel is not minimized
   */
  private boolean hasUserSeenNewErrors;
  private boolean isMinimized;

  public IssuePanel(@NotNull DesignSurface designSurface, @NotNull IssueModel issueModel) {
    super(new BorderLayout());
    setName(ISSUE_PANEL_NAME);
    myIssueModel = issueModel;
    mySurface = designSurface;

    myTitleLabel = createTitleLabel();
    JComponent titlePanel = createTitlePanel(myTitleLabel);
    add(titlePanel, BorderLayout.NORTH);

    myErrorListPanel = createErrorListPanel();
    myScrollPane = createListScrollPane(myErrorListPanel);
    add(myScrollPane, BorderLayout.CENTER);
    updateTitlebarStyle();

    myIssueModelListener = this::updateErrorList;
    myIssueModel.addErrorModelListener(myIssueModelListener);
    updateErrorList();

    setFocusable(true);
    setRequestFocusEnabled(true);
    registerKeyboardActions();
    addFocusListener(createFocusListener());
    setMinimized(true);
  }

  @NotNull
  private FocusListener createFocusListener() {
    return new FocusListener() {

      @Override
      public void focusGained(FocusEvent e) {
        if (mySelectedIssueView != null) {
          mySelectedIssueView.setFocused(true);
        }
      }

      @Override
      public void focusLost(FocusEvent e) {
        if (mySelectedIssueView != null) {
          mySelectedIssueView.setFocused(false);
        }
      }
    };
  }

  private void registerKeyboardActions() {
    getActionMap().put(ACTION_PREVIOUS, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        keyboardSelect(-1);
      }
    });
    getActionMap().put(ACTION_NEXT, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        keyboardSelect(1);
      }
    });
    getActionMap().put(ACTION_EXPAND, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        expandSelectedIssue(true);
      }
    });
    getActionMap().put(ACTION_COLLAPSE, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        expandSelectedIssue(false);
      }
    });
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), ACTION_PREVIOUS);
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), ACTION_NEXT);
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), ACTION_EXPAND);
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), ACTION_COLLAPSE);
  }

  private void expandSelectedIssue(boolean expanded) {
    if (mySelectedIssueView != null) {
      mySelectedIssueView.setExpanded(expanded);
    }
  }

  private void keyboardSelect(int direction) {
    if (!myDisplayedError.isEmpty()) {
      if (mySelectedIssueView == null) {
        Component component = myErrorListPanel.getComponent(0);
        if (component instanceof IssueView) {
          setSelectedIssue((IssueView)component);
          return;
        }
      }
    }
    Component[] components = myErrorListPanel.getComponents();
    for (int i = 0; i < components.length; i++) {
      Component component = components[i];
      if (component == mySelectedIssueView) {
        int selectedIndex = (i + (direction >= 0 ? 1 : -1)) % myDisplayedError.size();
        if (selectedIndex < 0) selectedIndex += myDisplayedError.size();
        assert components[i] instanceof IssueView;
        setSelectedIssue(((IssueView)components[selectedIndex]));
        mySelectedIssueView.scrollRectToVisible(mySelectedIssueView.getBounds());
        myScrollPane.getViewport().setViewPosition(mySelectedIssueView.getLocation());
        return;
      }
    }
  }

  @NotNull
  private ActionToolbar createToolbar() {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(new MinimizeAction());
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actionGroup, true);
    toolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    return toolbar;
  }

  @NotNull
  private JComponent createTitlePanel(@NotNull JBLabel titleLabel) {
    JBCheckBox checkBox = new JBCheckBox(SHOW_ISSUES_CHECKBOX_TEXT,
                                         AndroidEditorSettings.getInstance().getGlobalState().isShowLint());
    checkBox.addItemListener(e -> {
      AndroidEditorSettings.getInstance().getGlobalState()
        .setShowLint(e.getStateChange() == ItemEvent.SELECTED);
      mySurface.repaint();
    });
    JPanel titlePanel = new JPanel(new BorderLayout());
    titlePanel.add(titleLabel, BorderLayout.WEST);
    JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
    rightPanel.add(checkBox);
    rightPanel.add(createToolbar().getComponent());
    titlePanel.add(rightPanel, BorderLayout.EAST);
    titlePanel.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    return titlePanel;
  }

  @NotNull
  private static JBScrollPane createListScrollPane(@NotNull JPanel content) {
    JBScrollPane pane = new JBScrollPane(content);
    pane.setBorder(null);
    pane.setAlignmentX(CENTER_ALIGNMENT);
    return pane;
  }

  @NotNull
  private static JPanel createErrorListPanel() {
    JPanel panel = new JPanel(null, true);
    panel.setBackground(UIUtil.getEditorPaneBackground());
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    return panel;
  }

  @NotNull
  private static JBLabel createTitleLabel() {
    JBLabel label = new JBLabel(TITLE_NO_IMPORTANT_ISSUE, SwingConstants.LEFT);
    label.setBorder(JBUI.Borders.empty(0, 5, 0, 20));
    return label;
  }

  private void updateErrorList() {
    UIUtil.invokeLaterIfNeeded(() -> {
      if (!myIssueModel.hasIssues()) {
        myTitleLabel.setText(TITLE_NO_ISSUES);
        myDisplayedError.clear();
        myErrorListPanel.removeAll();
        setMinimized(true);
        return;
      }
      updateTitlebarStyle();
      boolean needsRevalidate = false;
      List<NlIssue> nlIssues = myIssueModel.getNlErrors();
      if (myDisplayedError.isEmpty()) {
        nlIssues.forEach(this::addErrorEntry);
      }
      else {
        removeOldIssues(nlIssues);
        needsRevalidate = displayNewIssues(nlIssues);
      }
      if (needsRevalidate) {
        revalidate();
        repaint();
      }
    });
  }

  /**
   * Updates the titlebar style depending on the current panel state (whether is minimized or has new elements).
   */
  private void updateTitlebarStyle() {
    // If there are new errors and the panel is minimized, set the title to bold
    myTitleLabel.setFont(myTitleLabel.getFont().deriveFont(!isMinimized() || hasUserSeenNewErrors ? Font.PLAIN : Font.BOLD));

    int warningCount = myIssueModel.getWarningCount();
    int errorCount = myIssueModel.getErrorCount();
    if (warningCount == 0 && errorCount == 0) {
      myTitleLabel.setText(TITLE_NO_IMPORTANT_ISSUE);
    }
    else {
      StringBuilder title = new StringBuilder();
      if (warningCount > 0) {
        title.append(warningCount)
          .append(' ').append(StringUtil.pluralize(WARNING, warningCount)).append(' ');
      }
      if (errorCount > 0) {
        title.append(errorCount)
          .append(' ').append(StringUtil.pluralize(ERROR, errorCount));
      }
      myTitleLabel.setText(title.toString());
    }
  }

  private boolean displayNewIssues(@NotNull List<NlIssue> nlIssues) {
    boolean needsRevalidate = false;
    for (NlIssue error : nlIssues) {
      if (!myDisplayedError.containsKey(error)) {
        addErrorEntry(error);
        needsRevalidate = true;
      }
    }
    return needsRevalidate;
  }

  private void removeOldIssues(@NotNull List<NlIssue> nlIssues) {
    Iterator<Map.Entry<NlIssue, IssueView>> iterator = myDisplayedError.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<NlIssue, IssueView> entry = iterator.next();
      NlIssue nlIssue = entry.getKey();
      if (!nlIssues.contains(nlIssue)) {
        IssueView issueView = entry.getValue();
        myErrorListPanel.remove(issueView);
        iterator.remove();
      }
    }
  }

  private void addErrorEntry(@NotNull NlIssue error) {
    if (myErrorListPanel.getComponentCount() == 0) {
      myErrorListPanel.add(Box.createVerticalGlue(), -1);
    }
    IssueView issueView = new IssueView(error, this);
    myDisplayedError.put(error, issueView);
    myErrorListPanel.add(issueView, getInsertionIndex(issueView));
  }

  private int getInsertionIndex(IssueView issueView) {
    int insertIndex = 0;
    for (int i = 0; i < myErrorListPanel.getComponentCount(); i++) {
      Component component = myErrorListPanel.getComponent(i);
      if (component instanceof IssueView) {
        if (((IssueView)component).getDisplayPriority() <= issueView.getDisplayPriority()) {
          insertIndex++;
        }
        else {
          break;
        }
      }
    }
    return insertIndex;
  }

  @Nullable
  public IssueView getSelectedIssueView() {
    return mySelectedIssueView;
  }

  @Override
  public void doLayout() {
    // Compute the Category and source column size so they take the minimum space
    int categoryColumnSize = 0;
    int sourceColumnSize = 0;
    Collection<IssueView> values = myDisplayedError.values();
    for (IssueView view : values) {
      categoryColumnSize = Math.max(categoryColumnSize, view.getCategoryLabelWidth());
      sourceColumnSize = Math.max(sourceColumnSize, view.getSourceLabelWidth());
    }
    for (IssueView view : values) {
      view.setCategoryLabelSize(categoryColumnSize);
      view.setSourceLabelSize(sourceColumnSize);
    }
    super.doLayout();
  }

  public void setMinimized(boolean minimized) {
    if (minimized == isMinimized) {
      return;
    }

    if (!minimized) {
      hasUserSeenNewErrors = true;
    }

    isMinimized = minimized;
    setVisible(!isMinimized);
    revalidate();
    repaint();

    if (myMinimizeListener != null) {
      UIUtil.invokeLaterIfNeeded(() -> myMinimizeListener.onMinimizeChanged(isMinimized));
    }
  }

  public void setMinimizeListener(@Nullable MinimizeListener listener) {
    myMinimizeListener = listener;
  }

  /**
   * Listener to be notified when this panel is minimized
   */
  @Override
  public void dispose() {
    myMinimizeListener = null;
    myIssueModel.removeErrorModelListener(myIssueModelListener);
  }

  public boolean isMinimized() {
    return isMinimized;
  }


  public void setSelectedIssue(@Nullable IssueView selectedIssue) {
    if (mySelectedIssueView != selectedIssue) {
      if (mySelectedIssueView != null) {
        mySelectedIssueView.setSelected(false);
      }
      mySelectedIssueView = selectedIssue;
      if (mySelectedIssueView != null) {
        mySelectedIssueView.setSelected(true);
        NlIssue issue = myDisplayedError.inverse().get(mySelectedIssueView);
        if (issue == null) {
          return;
        }
        NlComponent source = issue.getSource();
        if (source != null) {
          mySurface.getSelectionModel().setSelection(Collections.singletonList(source));
        }
      }
    }
  }

  @VisibleForTesting
  public String getTitleText() {
    return myTitleLabel.getText();
  }

  @VisibleForTesting
  public IssueModel getIssueModel() {
    return myIssueModel;
  }

  /**
   * Lookup the title and description of every shown error and tries to find the provided text
   */
  @VisibleForTesting
  public boolean containsErrorWithText(@NotNull String text) {
    return myDisplayedError.values()
      .stream()
      .anyMatch(
        view -> view.getIssueTitle().contains(text) || MULTIPLE_SPACES.matcher(view.getIssueDescription()).replaceAll(" ").contains(text));
  }

  /**
   * Select the first issue related to the provided component and scroll the viewport to this issue
   *
   * @param component      The component owning the issue to show
   * @param collapseOthers if true, all other issues will be collapsed
   */
  public void showIssueForComponent(NlComponent component, boolean collapseOthers) {
    NlIssue issue = myIssueModel.findIssue(component);
    if (issue != null) {
      IssueView issueView = myDisplayedError.get(issue);
      if (issueView != null) {
        setMinimized(false);
        setSelectedIssue(issueView);
        issueView.setExpanded(true);
        myScrollPane.getViewport().setViewPosition(issueView.getLocation());

        // Collapse all other issue
        if (collapseOthers) {
          for (IssueView other : myDisplayedError.values()) {
            if (other != issueView) {
              other.setExpanded(false);
            }
          }
        }
      }
    }
  }

  /**
   * @return The height that the panel should take to display the selected issue if there is one
   * or the full list of collapsed issues
   */
  public int getSuggestedHeight() {
    int suggestedHeight = myTitleLabel.getHeight();
    if (mySelectedIssueView != null) {
      suggestedHeight += mySelectedIssueView.getHeight();
    }
    else {
      suggestedHeight += myDisplayedError.size() * 30;
    }
    return Math.max(getHeight(), suggestedHeight);
  }

  public interface MinimizeListener {
    void onMinimizeChanged(boolean isMinimized);
  }

  /**
   * Action invoked by the user to minimize or restore the errors panel
   */
  private class MinimizeAction extends AnAction {
    private static final String DESCRIPTION = "Hide the render errors panel";

    private MinimizeAction() {
      super(DESCRIPTION, DESCRIPTION, StudioIcons.Common.CLOSE);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      setMinimized(true);
    }
  }
}
