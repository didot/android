/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.monitor.cpu;

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.monitor.*;
import com.android.tools.idea.monitor.actions.RecordingAction;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithActions;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.HierarchyEvent;

public class CpuMonitorView extends BaseMonitorView implements TimelineEventListener, DeviceContext.DeviceSelectionListener {
  /**
   * Maximum number of samples to keep in memory. We not only sample at {@code SAMPLE_FREQUENCY_MS} but we also receive
   * a sample on every GC.
   */
  public static final int SAMPLES = 2048;
  private static final Color BACKGROUND_COLOR = UIUtil.getTextFieldBackground();
  private static final int SAMPLE_FREQUENCY_MS = 500;
  @NotNull private final CpuSampler myCpuSampler;

  public CpuMonitorView(@NotNull Project project, @NotNull DeviceContext deviceContext, @NotNull DeviceSamplerView deviceSamplerView) {
    super(project, deviceSamplerView);

    // Buffer at one and a half times the sample frequency.
    float bufferTimeInSeconds = SAMPLE_FREQUENCY_MS * 1.5f / 1000.f;
    float initialMax = 100.0f;
    float initialMarker = 10.0f;

    TimelineData data = new TimelineData(2, SAMPLES);
    TimelineComponent timelineComponent = new TimelineComponent(data, bufferTimeInSeconds, initialMax, 100, initialMarker);

    timelineComponent.configureUnits("%");
    timelineComponent.configureStream(0, "Kernel", new JBColor(0x78abd9, 0x78abd9));
    timelineComponent.configureStream(1, "User", new JBColor(0xbaccdc, 0x51585c));
    timelineComponent.setBackground(BACKGROUND_COLOR);

    setComponent(timelineComponent);

    myCpuSampler = new CpuSampler(data, SAMPLE_FREQUENCY_MS);
    myCpuSampler.addListener(this);

    myDeviceSamplerView.registerView(this);

    deviceContext.addListener(this, project);
  }

  @NotNull
  public ActionGroup getToolbarActions() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new RecordingAction(myCpuSampler));
    return group;
  }

  @NotNull
  public ComponentWithActions createComponent() {
    return new ComponentWithActions.Impl(getToolbarActions(), null, null, null, myContentPane);
  }

  @Override
  public void deviceSelected(@Nullable IDevice device) {

  }

  @Override
  public void deviceChanged(@NotNull IDevice device, int changeMask) {

  }

  @Override
  public void clientSelected(@Nullable Client c) {
    myCpuSampler.setClient(c);
  }

  @Override
  public void onStart() {
  }

  @Override
  public void onStop() {
  }

  @Override
  public void onEvent(@NotNull TimelineEvent event) {
  }

  @Override
  protected DeviceSampler getSampler() {
    return myCpuSampler;
  }
}
