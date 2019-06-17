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

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.formatter.TimeFormatter;
import com.android.tools.profilers.cpu.*;
import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.TimeUnit;

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
      .onChange(CpuProfilerAspect.CAPTURE_STATE, this::onCaptureStateChanged)
      .onChange(CpuProfilerAspect.CAPTURE_SELECTION, this::updateCapturePane);
    myStage.getCaptureParser().getAspect().addDependency(myObserver).onChange(CpuProfilerAspect.CAPTURE_PARSING, this::updateCapturePane);
    updateCapturePane();
  }

  private void updateCaptureDetails() {
    myCapturePane.updateView();
  }

  private void onCaptureStateChanged() {
    if (myStage.getCaptureState() == CpuProfilerStage.CaptureState.STARTING
        || myStage.getCaptureState() == CpuProfilerStage.CaptureState.STOPPING) {
      // STARTING and STOPPING shouldn't change the panel displayed, so we return early.
      return;
    }

    updateCapturePane();
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
      return new ParsingPane(myStageView);
    }

    if (myStage.getCaptureState() == CpuProfilerStage.CaptureState.CAPTURING) {
      return new RecordingPane(myStageView);
    }

    if (myStage.getCapture() == null) {
      return new RecordingInitiatorPane(myStageView);
    }
    else {
      return new DetailsCapturePane(myStageView);
    }
  }

  @NotNull
  public JComponent getComponent() {
    return myPanel;
  }

  /**
   * A {@link StatusPane} representing the {@link CpuProfilerStage.CaptureState#CAPTURING} state.
   */
  @VisibleForTesting
  static class RecordingPane extends StatusPane {
    /**
     * {@link JButton} used to stop recording.
     */
    private JButton myStopRecordingButton;

    public RecordingPane(@NotNull CpuProfilerStageView stageView) {
      super(stageView, "Recording");
      // Disable the stop recording button on state transition.
      myStage.getAspect().addDependency(myObserver)
        .onChange(CpuProfilerAspect.CAPTURE_STATE, () -> myStopRecordingButton.setEnabled(false));
    }

    @Override
    protected JButton createAbortButton() {
      myStopRecordingButton = new JButton(CpuProfilerToolbar.STOP_TEXT);
      myStopRecordingButton.addActionListener((event) -> myStage.toggleCapturing());
      myStopRecordingButton.setEnabled(!myStage.isApiInitiatedTracingInProgress());
      return myStopRecordingButton;
    }

    @NotNull
    @Override
    protected String getDurationText() {
      return TimeFormatter.getMultiUnitDurationString(myStage.getCaptureElapsedTimeUs());
    }
  }

  /**
   * A {@link StatusPane} representing the {@link CpuCaptureParser#isParsing()} state.
   */
  @VisibleForTesting
  static class ParsingPane extends StatusPane {

    static final String ABORT_BUTTON_TEXT = "Abort";

    public ParsingPane(@NotNull CpuProfilerStageView stageView) {
      super(stageView, "Parsing");
    }

    @NotNull
    @Override
    protected String getDurationText() {
      return TimeFormatter.getMultiUnitDurationString(TimeUnit.MILLISECONDS.toMicros(myStage.getCaptureParser().getParsingElapsedTimeMs()));
    }

    @Override
    protected JButton createAbortButton() {
      JButton abortButton = new JButton(ABORT_BUTTON_TEXT);
      abortButton.addActionListener((event) -> {
        myStage.getCaptureParser().abortParsing();
        myStage.setCaptureState(CpuProfilerStage.CaptureState.IDLE);
        abortButton.setEnabled(false);
      });
      return abortButton;
    }
  }
}
