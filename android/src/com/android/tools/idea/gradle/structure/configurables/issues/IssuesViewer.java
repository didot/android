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
package com.android.tools.idea.gradle.structure.configurables.issues;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.ui.CollapsiblePanel;
import com.android.tools.idea.gradle.structure.model.PsIssue;
import com.android.tools.idea.gradle.structure.model.PsPath;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.components.JBLabel;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.android.tools.adtui.HtmlLabel.setUpAsHtmlLabel;
import static com.android.tools.idea.gradle.structure.configurables.ui.UiUtil.revalidateAndRepaint;
import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;
import static com.intellij.util.ui.UIUtil.getTreeFont;

public class IssuesViewer {
  @NotNull private final PsContext myContext;
  @NotNull private final DependencyViewIssuesRenderer myRenderer;

  private JBLabel myEmptyIssuesLabel;

  private JPanel myIssuesPanel1;
  private JPanel myIssuesPanel2;
  private JPanel myIssuesPanel3;
  private JPanel myIssuesPanel4;
  private JPanel myMainPanel;

  private JEditorPane myIssuesView1;
  private JEditorPane myIssuesView2;
  private JEditorPane myIssuesView3;
  private JEditorPane myIssuesView4;

  private boolean myShowEmptyText;

  public IssuesViewer(@NotNull PsContext context, @NotNull DependencyViewIssuesRenderer renderer) {
    myContext = context;
    myRenderer = renderer;
  }

  public void display(@NotNull Collection<PsIssue> issues, @Nullable PsPath scope) {
    // TODO(xof): all of this is horrible: rewrite so that it is less horrible
    if (issues.isEmpty()) {
      if (myShowEmptyText) {
        myEmptyIssuesLabel.setVisible(true);
      }
      myIssuesPanel1.setVisible(false);
      myIssuesPanel2.setVisible(false);
      myIssuesPanel3.setVisible(false);
      myIssuesPanel4.setVisible(false);
      revalidateAndRepaintPanels();
      return;
    }
    else {
      myEmptyIssuesLabel.setVisible(false);
      myIssuesPanel1.setVisible(true);
      myIssuesPanel2.setVisible(true);
      myIssuesPanel3.setVisible(true);
      myIssuesPanel4.setVisible(true);
    }

    removeListeners((CollapsiblePanel) myIssuesPanel1);
    removeListeners((CollapsiblePanel) myIssuesPanel2);
    removeListeners((CollapsiblePanel) myIssuesPanel3);
    removeListeners((CollapsiblePanel) myIssuesPanel4);

    Map<PsIssue.Severity, List<PsIssue>> issuesBySeverity = Maps.newHashMap();
    for (PsIssue issue : issues) {
      PsIssue.Severity severity = issue.getSeverity();
      List<PsIssue> currentIssues = issuesBySeverity.get(severity);
      if (currentIssues == null) {
        currentIssues = Lists.newArrayList();
        issuesBySeverity.put(severity, currentIssues);
      }
      currentIssues.add(issue);
    }

    List<PsIssue.Severity> severities = Lists.newArrayList(issuesBySeverity.keySet());
    Collections.sort(severities, (t1, t2) -> t1.getPriority() - t2.getPriority());

    int typeCount = severities.size();
    assert typeCount < 5; // There are only 4 types of issues

    // Start displaying from last to first
    int currentIssueIndex = typeCount - 1;
    PsIssue.Severity severity4 = severities.get(currentIssueIndex);
    List<PsIssue> group4 = issuesBySeverity.get(severity4);
    myIssuesPanel4.addPropertyChangeListener("expanded", new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        updateTitle((CollapsiblePanel) myIssuesPanel4, severity4, group4);
      }
    });
    updateTitle(((CollapsiblePanel)myIssuesPanel4), severity4, group4);
    renderIssues(group4, scope, myIssuesView4);

    currentIssueIndex--;
    if (currentIssueIndex < 0) {
      myIssuesPanel1.setVisible(false);
      myIssuesPanel2.setVisible(false);
      myIssuesPanel3.setVisible(false);
      revalidateAndRepaintPanels();
      return;
    }

    PsIssue.Severity severity3 = severities.get(currentIssueIndex);
    List<PsIssue> group3 = issuesBySeverity.get(severity3);
    myIssuesPanel3.addPropertyChangeListener("expanded", new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        updateTitle((CollapsiblePanel) myIssuesPanel3, severity3, group3);
      }
    });
    updateTitle(((CollapsiblePanel)myIssuesPanel3), severity3, group3);
    renderIssues(group3, scope, myIssuesView3);

    currentIssueIndex--;
    if (currentIssueIndex < 0) {
      myIssuesPanel1.setVisible(false);
      myIssuesPanel2.setVisible(false);
      revalidateAndRepaintPanels();
      return;
    }

    PsIssue.Severity severity2 = severities.get(currentIssueIndex);
    List<PsIssue> group2 = issuesBySeverity.get(severity2);
    myIssuesPanel2.addPropertyChangeListener("expanded", new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        updateTitle((CollapsiblePanel) myIssuesPanel2, severity2, group2);
      }
    });
    updateTitle(((CollapsiblePanel)myIssuesPanel2), severity2, group2);
    renderIssues(group2, scope, myIssuesView2);

    currentIssueIndex--;
    if (currentIssueIndex < 0) {
      myIssuesPanel1.setVisible(false);
      revalidateAndRepaintPanels();
      return;
    }

    PsIssue.Severity severity1 = severities.get(currentIssueIndex);
    List<PsIssue> group1 = issuesBySeverity.get(severity1);
    myIssuesPanel1.addPropertyChangeListener("expanded", new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        updateTitle((CollapsiblePanel) myIssuesPanel1, severity1, group1);
      }
    });
    updateTitle(((CollapsiblePanel)myIssuesPanel1), severity1, group1);
    renderIssues(group1, scope, myIssuesView1);

    revalidateAndRepaintPanels();
  }

  private void renderIssues(@NotNull List<PsIssue> group, @Nullable PsPath scope, @NotNull JEditorPane view) {
    view.setText(myRenderer.render(group, scope));
    view.setCaretPosition(0);
  }

  private void revalidateAndRepaintPanels() {
    revalidateAndRepaint(myIssuesPanel1);
    revalidateAndRepaint(myIssuesPanel2);
    revalidateAndRepaint(myIssuesPanel4);
    revalidateAndRepaint(myMainPanel);
  }

  private static void removeListeners(@NotNull CollapsiblePanel panel) {
    for (PropertyChangeListener l : panel.getPropertyChangeListeners("expanded")) {
      panel.removePropertyChangeListener("expanded", l);
    }
  }

  private static void updateTitle(@NotNull CollapsiblePanel panel, @NotNull PsIssue.Severity severity, @NotNull List<PsIssue> issues) {
    SimpleColoredComponent title = panel.getTitleComponent();
    title.clear();
    title.setIcon(severity.getIcon());
    title.append(severity.getText(), REGULAR_ATTRIBUTES);
    int issueCount = issues.size();
    if (!panel.isExpanded()) {
      title.append(" (" + issueCount + " item" + (issueCount == 1 ? "" : "s") + ")", GRAY_ATTRIBUTES);
    }
  }

  @NotNull
  public JPanel getPanel() {
    return myMainPanel;
  }

  private void createUIComponents() {
    Font font = getTreeFont();
    NavigationHyperlinkListener hyperlinkListener = new NavigationHyperlinkListener(myContext);

    myIssuesPanel1 = new CollapsiblePanel();
    myIssuesView1 = new JEditorPane();
    myIssuesView1.setFocusable(false);
    myIssuesView1.addHyperlinkListener(hyperlinkListener);
    setUpAsHtmlLabel(myIssuesView1, font);
    ((CollapsiblePanel)myIssuesPanel1).setContents(myIssuesView1);

    myIssuesPanel2 = new CollapsiblePanel();
    myIssuesView2 = new JEditorPane();
    myIssuesView2.setFocusable(false);
    myIssuesView2.addHyperlinkListener(hyperlinkListener);
    setUpAsHtmlLabel(myIssuesView2, font);
    ((CollapsiblePanel)myIssuesPanel2).setContents(myIssuesView2);

    myIssuesPanel3 = new CollapsiblePanel();
    myIssuesView3 = new JEditorPane();
    myIssuesView3.setFocusable(false);
    myIssuesView3.addHyperlinkListener(hyperlinkListener);
    setUpAsHtmlLabel(myIssuesView3, font);
    ((CollapsiblePanel)myIssuesPanel3).setContents(myIssuesView3);

    myIssuesPanel4 = new CollapsiblePanel();
    myIssuesView4 = new JEditorPane();
    myIssuesView4.setFocusable(false);
    myIssuesView4.addHyperlinkListener(hyperlinkListener);
    setUpAsHtmlLabel(myIssuesView4, font);
    ((CollapsiblePanel)myIssuesPanel4).setContents(myIssuesView4);
  }

  public void setShowEmptyText(boolean showEmptyText) {
    myShowEmptyText = showEmptyText;
    if (!myShowEmptyText) {
      myEmptyIssuesLabel.setVisible(false);
    }
  }
}
