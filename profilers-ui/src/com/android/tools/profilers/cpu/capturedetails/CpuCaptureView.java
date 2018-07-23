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
package com.android.tools.profilers.cpu.capturedetails;

import com.android.tools.adtui.instructions.InstructionsPanel;
import com.android.tools.adtui.instructions.NewRowInstruction;
import com.android.tools.adtui.instructions.TextInstruction;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.profilers.ProfilerFonts;
import com.android.tools.profilers.cpu.*;
import com.android.tools.profilers.stacktrace.LoadingPanel;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class CpuCaptureView {
  @NotNull
  private final CpuProfilerStageView myStageView;

  @NotNull
  private final CpuProfilerStage myStage;

  @NotNull
  private final JPanel myPanel;

  @NotNull
  private CapturePane myCapturePane;

  @SuppressWarnings("FieldCanBeLocal")
  @NotNull
  private final AspectObserver myObserver;

  public CpuCaptureView(@NotNull CpuProfilerStageView stageView) {
    myStageView = stageView;
    myStage = stageView.getStage();
    myPanel = new JPanel(new BorderLayout());
    myObserver = new AspectObserver();
    myCapturePane = createCapturePane();

    myStage.getAspect().addDependency(myObserver)
           .onChange(CpuProfilerAspect.CAPTURE_DETAILS, this::updateCaptureDetails)
           .onChange(CpuProfilerAspect.CAPTURE_STATE, this::updateCapturePane)
           .onChange(CpuProfilerAspect.CAPTURE_SELECTION, this::updateCapturePane);
    myStage.getCaptureParser().getAspect().addDependency(myObserver).onChange(CpuProfilerAspect.CAPTURE_PARSING, this::updateCapturePane);
    updateCapturePane();
  }

  private void updateCaptureDetails() {
    myCapturePane.updateView();
  }

  private void updateCapturePane() {
    myPanel.removeAll();
    myCapturePane = createCapturePane();
    myPanel.add(myCapturePane, BorderLayout.CENTER);
    myPanel.revalidate();
  }

  @NotNull
  private CapturePane createCapturePane() {
    if (myStage.getCaptureParser().isParsing()) {
      return new LoadingPane(myStageView);
    }

    if (myStage.getCapture() == null) {
      // TODO(b/111779496): check if status is recording and not API initiated, and update the panel accordingly (status: recording).
      return new HelpTipPane(myStageView);
    }
    else {
      return new DetailsCapturePane(myStageView);
    }
  }

  @NotNull
  public JComponent getComponent() {
    return myPanel;
  }

  static class LoadingPane extends CapturePane {
    private static final String LOADING_TEXT = "Parsing capture...";

    @NotNull private final LoadingPanel myCaptureViewLoading;

    LoadingPane(@NotNull CpuProfilerStageView stageView) {
      super(stageView);
      myCaptureViewLoading = stageView.getProfilersView().getIdeProfilerComponents().createLoadingPanel(-1);
      myCaptureViewLoading.setLoadingText(LOADING_TEXT);

      addHierarchyListener(new HierarchyListener() {
        @Override
        public void hierarchyChanged(HierarchyEvent e) {
          if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0 && !isDisplayable()) {
            // LoadingPane was removed from the hierarchy, so stop the animations.
            myCaptureViewLoading.stopLoading();
          }
        }
      });

      disableInteraction();
      updateView();
    }

    @Override
    void populateContent(@NotNull JPanel panel) {
      myCaptureViewLoading.startLoading();
      panel.add(myCaptureViewLoading.getComponent(), BorderLayout.CENTER);
    }
  }

  /**
   * A {@link CapturePane} that is used to display a help message when there is no selected capture.
   */
  static class HelpTipPane extends CapturePane {
    HelpTipPane(@NotNull CpuProfilerStageView stageView) {
      super(stageView);
      disableInteraction();
      updateView();
    }

    @Override
    void populateContent(@NotNull JPanel panel) {
      FontMetrics headerMetrics = SwingUtilities2.getFontMetrics(this, ProfilerFonts.H3_FONT);
      FontMetrics bodyMetrics = SwingUtilities2.getFontMetrics(this, ProfilerFonts.STANDARD_FONT);
      InstructionsPanel infoMessage = new InstructionsPanel.Builder(
        new TextInstruction(headerMetrics, "Thread details unavailable"),
        new NewRowInstruction(NewRowInstruction.DEFAULT_ROW_MARGIN),
        new TextInstruction(bodyMetrics, "Click Record to start capturing CPU activity"),
        new NewRowInstruction(NewRowInstruction.DEFAULT_ROW_MARGIN),
        new TextInstruction(bodyMetrics, "or select a capture in the timeline."))
        .setColors(JBColor.foreground(), null)
        .build();

      panel.add(infoMessage, BorderLayout.CENTER);
    }
  }
}
