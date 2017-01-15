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
package com.android.tools.profilers;

import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.model.AspectObserver;
import com.intellij.ui.components.JBPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public abstract class StageView<T extends Stage> extends AspectObserver {
  private final T myStage;
  private final JPanel myComponent;
  private final StudioProfilersView myProfilersView;

  public StageView(@NotNull StudioProfilersView profilersView, @NotNull T stage) {
    myProfilersView = profilersView;
    myStage = stage;
    myComponent = new JBPanel(new BorderLayout());
    myComponent.setBackground(ProfilerColors.MONITOR_BACKGROUND);
  }

  @NotNull
  public T getStage() {
    return myStage;
  }

  @NotNull
  public StudioProfilersView getProfilersView() {
    return myProfilersView;
  }

  @NotNull
  public IdeProfilerComponents getIdeComponents() {
    return myProfilersView.getIdeProfilerComponents();
  }

  @NotNull
  public final JComponent getComponent() {
    return myComponent;
  }

  @NotNull
  public final ProfilerTimeline getTimeline() {
    return myStage.getStudioProfilers().getTimeline();
  }

  @NotNull
  protected AxisComponent buildTimeAxis(StudioProfilers profilers) {
    AxisComponent timeAxis = new AxisComponent(profilers.getViewAxis(), AxisComponent.AxisOrientation.BOTTOM);
    timeAxis.setShowAxisLine(false);
    timeAxis.setMinimumSize(new Dimension(0, ProfilerLayout.TIME_AXIS_HEIGHT));
    timeAxis.setPreferredSize(new Dimension(Integer.MAX_VALUE, ProfilerLayout.TIME_AXIS_HEIGHT));
    return timeAxis;
  }

  abstract public JComponent getToolbar();

  /**
   * A purely visual concept as to whether this stage wants the "process and devices" selection being shown to the user.
   * It is not possible to assume processes won't change while a stage is running. For example: a process dying.
   */
  public boolean needsProcessSelection() {
    return false;
  }
}
