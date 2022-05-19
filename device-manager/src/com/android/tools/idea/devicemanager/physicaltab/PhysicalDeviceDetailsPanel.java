/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.physicaltab;

import com.android.tools.idea.devicemanager.DetailsPanel;
import com.android.tools.idea.devicemanager.DeviceManagerFutureCallback;
import com.android.tools.idea.devicemanager.DeviceType;
import com.android.tools.idea.devicemanager.InfoSection;
import com.android.tools.idea.devicemanager.PairedDevicesPanel;
import com.android.tools.idea.devicemanager.ScreenDiagram;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.wearpairing.WearPairingManager;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.concurrency.EdtExecutorService;
import java.awt.Component;
import java.util.concurrent.Executor;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.JLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PhysicalDeviceDetailsPanel extends DetailsPanel {
  private final boolean myOnline;
  private final @Nullable SummarySection mySummarySection;

  @VisibleForTesting
  static final class SummarySection extends InfoSection {
    @VisibleForTesting final @NotNull JLabel myApiLevelLabel;
    private final @NotNull JLabel myPowerLabel;
    @VisibleForTesting final @NotNull JLabel myResolutionLabel;
    @VisibleForTesting final @NotNull JLabel myDpLabel;
    @VisibleForTesting final @NotNull JLabel myAbiListLabel;
    private final @NotNull JLabel myAvailableStorageLabel;

    private SummarySection() {
      super("Summary");

      myApiLevelLabel = addNameAndValueLabels("API level");
      myPowerLabel = addNameAndValueLabels("Power");
      myResolutionLabel = addNameAndValueLabels("Resolution");
      myDpLabel = addNameAndValueLabels("dp");
      myAbiListLabel = addNameAndValueLabels("ABI list");
      myAvailableStorageLabel = addNameAndValueLabels("Available storage");

      setLayout();
    }
  }

  @VisibleForTesting
  interface NewInfoSectionCallback<S> {
    @NotNull FutureCallback<@NotNull PhysicalDevice> apply(@NotNull S section);
  }

  PhysicalDeviceDetailsPanel(@NotNull PhysicalDevice device, @Nullable Project project) {
    this(device, new AsyncDetailsBuilder(project, device));
  }

  @VisibleForTesting
  PhysicalDeviceDetailsPanel(@NotNull PhysicalDevice device, @NotNull AsyncDetailsBuilder builder) {
    this(device, builder, PhysicalDeviceDetailsPanel::newSummarySectionCallback, WearPairingManager.INSTANCE);
  }

  @VisibleForTesting
  PhysicalDeviceDetailsPanel(@NotNull PhysicalDevice device,
                             @NotNull AsyncDetailsBuilder builder,
                             @NotNull NewInfoSectionCallback<@NotNull SummarySection> newSummarySectionCallback,
                             @NotNull WearPairingManager manager) {
    super(device.getName());
    myOnline = device.isOnline();

    if (myOnline) {
      ListenableFuture<PhysicalDevice> future = builder.buildAsync();
      Executor executor = EdtExecutorService.getInstance();

      mySummarySection = new SummarySection();
      Futures.addCallback(future, newSummarySectionCallback.apply(mySummarySection), executor);

      myInfoSections.add(mySummarySection);
      InfoSection.newPairedDeviceSection(device, manager).ifPresent(myInfoSections::add);

      Futures.addCallback(future, newScreenDiagramCallback(), executor);

      if (StudioFlags.PAIRED_DEVICES_TAB_ENABLED.get() && device.getType().equals(DeviceType.PHONE)) {
        myPairedDevicesPanel = new PairedDevicesPanel(device.getKey(), this, builder.getProject());
      }
    }
    else {
      mySummarySection = null;
    }

    init();
  }

  @VisibleForTesting
  static @NotNull FutureCallback<@NotNull PhysicalDevice> newSummarySectionCallback(@NotNull SummarySection section) {
    return new DeviceManagerFutureCallback<>(PhysicalDeviceDetailsPanel.class, device -> {
      InfoSection.setText(section.myApiLevelLabel, device.getAndroidVersion().getApiString());
      InfoSection.setText(section.myPowerLabel, device.getPower());
      InfoSection.setText(section.myResolutionLabel, device.getResolution());
      InfoSection.setText(section.myDpLabel, device.getDp());
      InfoSection.setText(section.myAbiListLabel, device.getAbis());
      InfoSection.setText(section.myAvailableStorageLabel, device.getStorageDevice());
    });
  }

  private @NotNull FutureCallback<@NotNull PhysicalDevice> newScreenDiagramCallback() {
    return new DeviceManagerFutureCallback<>(PhysicalDeviceDetailsPanel.class, device -> {
      if (device.getDp() != null) {
        myScreenDiagram = new ScreenDiagram(device);
        setInfoSectionPanelLayout();
      }
    });
  }

  @Override
  protected void setInfoSectionPanelLayout() {
    if (myOnline) {
      super.setInfoSectionPanelLayout();
      return;
    }

    Component label = new JBLabel("Details unavailable for offline devices");
    GroupLayout layout = new GroupLayout(myInfoSectionPanel);

    Group horizontalGroup = layout.createSequentialGroup()
      .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
      .addComponent(label)
      .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE);

    Group verticalGroup = layout.createSequentialGroup()
      .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
      .addComponent(label)
      .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE);

    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    myInfoSectionPanel.setLayout(layout);
  }

  @VisibleForTesting
  @NotNull SummarySection getSummarySection() {
    assert mySummarySection != null;
    return mySummarySection;
  }
}
