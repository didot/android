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
package com.android.tools.profilers.cpu;

import static com.android.tools.adtui.common.DataVisualizationColors.PRIMARY_DATA_COLOR;

import com.android.tools.adtui.chart.statechart.StateChart;
import com.android.tools.adtui.chart.statechart.StateChartColorProvider;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.common.DataVisualizationColors;
import com.android.tools.adtui.model.trackgroup.TrackModel;
import com.android.tools.adtui.trackgroup.TrackRenderer;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerTrackRendererType;
import com.android.tools.profilers.cpu.atrace.CpuThreadSliceInfo;
import java.awt.Color;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

/**
 * Track renderer for CPU cores in CPU capture stage.
 */
public class CpuCoreTrackRenderer implements TrackRenderer<CpuCoreTrackModel, ProfilerTrackRendererType> {
  @NotNull
  @Override
  public JComponent render(@NotNull TrackModel<CpuCoreTrackModel, ProfilerTrackRendererType> trackModel) {
    CpuCoreTrackModel dataModel = trackModel.getDataModel();
    StateChart<CpuThreadSliceInfo> stateChart =
      new StateChart<>(dataModel.getStateChartModel(), new CpuCoreColorProvider(dataModel.getAppProcessId()));
    stateChart.setRenderMode(StateChart.RenderMode.TEXT);
    stateChart.setOpaque(true);
    return stateChart;
  }

  private static class CpuCoreColorProvider extends StateChartColorProvider<CpuThreadSliceInfo> {
    private final int myAppProcessId;

    CpuCoreColorProvider(int appProcessId) {
      myAppProcessId = appProcessId;
    }

    @NotNull
    @Override
    public Color getColor(boolean isMouseOver, @NotNull CpuThreadSliceInfo value) {
      // On the null thread return the background color.
      if (value == CpuThreadSliceInfo.NULL_THREAD) {
        return ProfilerColors.DEFAULT_BACKGROUND;
      }
      int nameHash = value.getProcessName().hashCode();
      DataVisualizationColors dataColors = DataVisualizationColors.INSTANCE;
      // Return other process colors.
      if (value.getProcessId() != myAppProcessId) {
        return dataColors.getColor(nameHash, isMouseOver ? 1 : 0);
      }
      // Return app process color.
      return dataColors.getColor(PRIMARY_DATA_COLOR, isMouseOver ? 1 : 0);
    }

    @NotNull
    @Override
    public Color getFontColor(boolean isMouseOver, @NotNull CpuThreadSliceInfo value) {
      // On the null thread return the default font color.
      if (value == CpuThreadSliceInfo.NULL_THREAD) {
        return AdtUiUtils.DEFAULT_FONT_COLOR;
      }
      // Return other process color.
      if (value.getProcessId() != myAppProcessId) {
        return isMouseOver ? ProfilerColors.CPU_KERNEL_OTHER_TEXT_HOVER : ProfilerColors.CPU_KERNEL_OTHER_TEXT;
      }
      // Return app process color.
      return isMouseOver ? ProfilerColors.CPU_KERNEL_APP_TEXT_HOVER : ProfilerColors.CPU_KERNEL_APP_TEXT;
    }
  }
}
