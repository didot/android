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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.Range;
import com.android.tools.profilers.ContextMenuInstaller;
import com.android.tools.profilers.IdeProfilerComponents;
import com.android.tools.profilers.ProfilerAction;
import com.android.tools.profilers.stacktrace.ContextMenuItem;
import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.KeyEvent;

import static java.awt.event.InputEvent.SHIFT_DOWN_MASK;

/**
 * Installs context menus for CPU Profiler.
 * This class should not be created directly instead {@link #install(CpuProfilerStageView, JComponent)} should be used.
 */
class CpuProfilerContextMenuInstaller {
  @NotNull private final CpuProfilerStage myStage;
  @NotNull private final ContextMenuInstaller myInstaller;
  @NotNull private final JComponent myComponent;
  @NotNull private final JComponent myContainerComponent;
  @NotNull private final IdeProfilerComponents myIdeComponents;

  private CpuProfilerContextMenuInstaller(@NotNull CpuProfilerStage stage,
                                          @NotNull IdeProfilerComponents ideComponents,
                                          @NotNull JComponent component,
                                          @NotNull JComponent containerComponent) {
    myInstaller = ideComponents.createContextMenuInstaller();
    myIdeComponents = ideComponents;
    myStage = stage;
    myComponent = component;
    myContainerComponent = containerComponent;
  }

  /**
   * Installs a context menu on {@link #myComponent}.
   */
  private void install() {
    // Add the item to trigger a recording
    installRecordMenuItem();

    // Add the item to export a trace file.
    if (myStage.getStudioProfilers().getIdeServices().getFeatureConfig().isExportCpuTraceEnabled()) {
      installExportTraceMenuItem();
    }

    installCaptureNavigationMenuItems();
  }

  /**
   * Installs both {@link ContextMenuItem} corresponding to the CPU capture navigation feature on {@link #myComponent}.
   */
  private void installCaptureNavigationMenuItems() {
    int shortcutModifier = AdtUiUtils.getActionMask() | SHIFT_DOWN_MASK;

    ProfilerAction navigateNext =
      new ProfilerAction.Builder("Next capture")
        .setContainerComponent(myContainerComponent)
        .setActionRunnable(() -> myStage.navigateNext())
        .setEnableBooleanSupplier(() -> !myStage.isImportTraceMode() && myStage.getTraceIdsIterator().hasNext())
        .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, shortcutModifier)).build();

    ProfilerAction navigatePrevious =
      new ProfilerAction.Builder("Previous capture")
        .setContainerComponent(myContainerComponent)
        .setActionRunnable(() -> myStage.navigatePrevious())
        .setEnableBooleanSupplier(() -> !myStage.isImportTraceMode()
                                        && myStage.getTraceIdsIterator().hasPrevious())
        .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, shortcutModifier)).build();

    myInstaller.installGenericContextMenu(myComponent, navigateNext);
    myInstaller.installGenericContextMenu(myComponent, navigatePrevious);
    myInstaller.installGenericContextMenu(myComponent, ContextMenuItem.SEPARATOR);
  }

  /**
   * Installs the {@link ContextMenuItem} corresponding to the "Export Trace" feature on {@link #myComponent}.
   */
  private void installExportTraceMenuItem() {
    // Call setEnableBooleanSupplier() on ProfilerAction.Builder to make it easier to test.
    ProfilerAction exportTrace = new ProfilerAction.Builder("Export trace...").setIcon(AllIcons.ToolbarDecorator.Export)
                                                                              .setContainerComponent(myContainerComponent)
                                                                              .setEnableBooleanSupplier(() -> !myStage.isImportTraceMode())
                                                                              .build();
    myInstaller.installGenericContextMenu(
      myComponent, exportTrace,
      x -> exportTrace.isEnabled() && getTraceIntersectingWithMouseX(x) != null,
      x -> myIdeComponents.createExportDialog().open(
        () -> "Export trace as",
        () -> CpuProfiler.generateCaptureFileName(getTraceIntersectingWithMouseX(x).getProfilerType()),
        () -> "trace",
        file -> myStage.getStudioProfilers().getIdeServices().saveFile(
          file, (output) -> CpuProfiler.saveCaptureToFile(getTraceIntersectingWithMouseX(x).getTraceInfo(), output), null)));
    myInstaller.installGenericContextMenu(myComponent, ContextMenuItem.SEPARATOR);
  }

  /**
   * Install the {@link ContextMenuItem} corresponding to the Start/Stop recording action on {@link #myComponent}.
   */
  private void installRecordMenuItem() {
    ProfilerAction record = new ProfilerAction.Builder(() -> myStage.getCaptureState() == CpuProfilerStage.CaptureState.CAPTURING
                                                             ? "Stop recording" : "Record CPU trace")
      .setContainerComponent(myContainerComponent)
      .setEnableBooleanSupplier(() -> shouldEnableCaptureButton() && !myStage.isImportTraceMode()
                                      && (myStage.getCaptureState() == CpuProfilerStage.CaptureState.CAPTURING
                                          || myStage.getCaptureState() == CpuProfilerStage.CaptureState.IDLE))
      .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_R, AdtUiUtils.getActionMask()))
      .setActionRunnable(() -> myStage.toggleCapturing())
      .build();

    myInstaller.installGenericContextMenu(myComponent, record);
    myInstaller.installGenericContextMenu(myComponent, ContextMenuItem.SEPARATOR);
  }

  private boolean shouldEnableCaptureButton() {
    return myStage.getStudioProfilers().getSessionsManager().isSessionAlive() && !myStage.isApiInitiatedTracingInProgress();
  }

  /**
   * Returns the trace ID of a capture that intersects with the mouse X coordinate within {@link #mySelection}.
   */
  private CpuTraceInfo getTraceIntersectingWithMouseX(int mouseXLocation) {
    Range range = myStage.getStudioProfilers().getTimeline().getViewRange();
    double pos = mouseXLocation / myComponent.getSize().getWidth() * range.getLength() + range.getMin();
    return myStage.getIntersectingTraceInfo(new Range(pos, pos));
  }

  /**
   * Installs CPU specific context menus.
   * @param component - a component where context menus should be installed.
   * @param containerComponent - a container component where context menu will be rendered.
   */
  public static void install(@NotNull CpuProfilerStage stage,
                             @NotNull IdeProfilerComponents ideComponents,
                             @NotNull JComponent component,
                             @NotNull JComponent containerComponent) {
    CpuProfilerContextMenuInstaller menus = new CpuProfilerContextMenuInstaller(stage, ideComponents, component,
                                                                                containerComponent);
    menus.install();
  }
}
