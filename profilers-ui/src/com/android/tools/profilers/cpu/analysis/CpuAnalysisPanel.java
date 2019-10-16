/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.cpu.analysis;

import com.android.tools.adtui.TabbedToolbar;
import com.android.tools.adtui.common.StudioColorsKt;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.profilers.StudioProfilersView;
import com.android.tools.profilers.ViewBinder;
import com.android.tools.profilers.cpu.CpuCaptureStage;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.jetbrains.annotations.NotNull;

/**
 * This class is responsible for creating the view associated with the current capture. The class looks at the {@link CpuCaptureStage} and
 * adds a {@link TabbedToolbar} tab for each {@link CpuAnalysisModel}. Multiple {@link CpuAnalysisModel}'s are expected. We always have a
 * base of "Full Trace" then additional can be added when selection is made.
 */
public class CpuAnalysisPanel extends AspectObserver {

  private final TabbedToolbar myTabs = new TabbedToolbar("Analysis");
  private final JBTabbedPane myTabView = new JBTabbedPane();
  private final JPanel myPanel = new JPanel(new BorderLayout());
  private final CpuCaptureStage myStage;
  private final StudioProfilersView myProfilersView;
  private CpuAnalysisModel mySelectedModel;
  private ViewBinder<StudioProfilersView, CpuAnalysisTabModel, CpuAnalysisTab> myTabViewsBinder;

  public CpuAnalysisPanel(@NotNull StudioProfilersView view, @NotNull CpuCaptureStage stage) {
    myStage = stage;
    myProfilersView = view;
    setupBindings();
    stage.getAspect().addDependency(this).onChange(CpuCaptureStage.Aspect.ANALYSIS_MODEL_UPDATED, this::updateComponents);
    // TODO (b/139295622): Add action items and actions to analysis panel.
    // Need proper icons for configure and minimize.
    // myTabs.addAction(StudioIcons.Logcat.SETTINGS, (e) -> { });
    myTabs.setBorder(JBUI.Borders.customLine(StudioColorsKt.getBorder(), 0, 1, 1, 0));
    myPanel.add(myTabs, BorderLayout.NORTH);
    myPanel.add(myTabView, BorderLayout.CENTER);
    // Remove default border added by JBTabbedPane.
    myTabView.setTabComponentInsets(JBUI.insets(0));
    myTabView.addChangeListener(new TabChangeListener());
  }

  private void setupBindings() {
    myTabViewsBinder = new ViewBinder<>();
    myTabViewsBinder.bind(CpuAnalysisChartModel.class, CpuAnalysisChart::new);
    myTabViewsBinder.bind(CpuAnalysisTabModel.class, CpuAnalysisSummaryTab::new);
  }

  @NotNull
  @VisibleForTesting
  TabbedToolbar getTabbedToolbar() {
    return myTabs;
  }

  @NotNull
  @VisibleForTesting
  JBTabbedPane getTabView() {
    return myTabView;
  }

  /**
   * Update components is called when the {@link CpuCaptureStage} changes state to analyzing a capture.
   */
  private void updateComponents() {
    myTabs.clearTabs();
    List<CpuAnalysisModel> models = myStage.getAnalysisModels();
    if (models.isEmpty()) {
      return;
    }

    for (CpuAnalysisModel model : models) {
      myTabs.addTab(model.getName(), () -> onSelectAnalysis(model));
    }
    // When tabs are updated auto select the latest tab.
    onSelectAnalysis(models.get(models.size() - 1));
  }

  /**
   * This function is called when the user selects an analysis tab (eg "Full trace").
   * We update and display the child tabs (eg "Summary", "Flame Chart").
   */
  private void onSelectAnalysis(@NotNull CpuAnalysisModel model) {
    mySelectedModel = model;
    // Reset state.
    myTabView.removeAll();

    // Create new child tabs. These tabs are things like "Flame Chart", "Top Down" etc...
    for (CpuAnalysisTabModel tab : model.getTabs()) {
      myTabView.insertTab(tab.getTitle(), null, new JPanel(), tab.getTitle(), myTabView.getTabCount());
    }

    myTabView.revalidate();
    myTabView.repaint();
  }

  @NotNull
  public JComponent getComponent() {
    return myPanel;
  }

  /**
   * Change listener class that manages creating / destroying the views for each tab as users cycle between them.
   */
  private class TabChangeListener implements ChangeListener {
    private int myLastSelectedIndex = -1;

    @Override
    public void stateChanged(ChangeEvent e) {
      int newIndex = myTabView.getSelectedIndex();
      if (newIndex == myLastSelectedIndex || mySelectedModel == null) {
        return;
      }

      // We reset the last tab to an empty panel so when range / data changes hidden panels are not updating UI.
      if (myLastSelectedIndex >= 0 && myLastSelectedIndex < myTabView.getTabCount()) {
        myTabView.setComponentAt(myLastSelectedIndex, new JPanel());
      }
      if (newIndex >= 0 && newIndex < myTabView.getTabCount()) {
        myTabView.setComponentAt(newIndex, myTabViewsBinder.build(myProfilersView, mySelectedModel.getTabs().get(newIndex)));
      }
      myLastSelectedIndex = newIndex;
    }
  }
}