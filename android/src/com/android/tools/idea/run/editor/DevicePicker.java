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
package com.android.tools.idea.run.editor;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.*;
import com.android.tools.idea.run.*;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.wireless.android.sdk.stats.AdbAssistantStats;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.Alarm;
import com.intellij.util.SmartList;
import com.intellij.util.ThreeState;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public class DevicePicker implements AndroidDebugBridge.IDebugBridgeChangeListener, AndroidDebugBridge.IDeviceChangeListener, Disposable,
                                     ActionListener, ListSelectionListener {
  private static final int UPDATE_DELAY_MILLIS = 250;
  private static final String DEVICE_PICKER_LAST_SELECTION = "device.picker.selection";
  private static final TIntObjectHashMap<Set<String>> ourSelectionsPerConfig = new TIntObjectHashMap<>();

  private JPanel myPanel;
  private JButton myCreateEmulatorButton;
  private HyperlinkLabel myHelpHyperlink;
  @SuppressWarnings("unused") // custom create
  private JScrollPane myScrollPane;
  private JPanel myNotificationPanel;
  private JBList<DevicePickerEntry> myDevicesList;
  private final AndroidDeviceRenderer myDeviceRenderer;
  private int myErrorGen;
  @NotNull
  private HelpHandler myHelpHandler;

  @NotNull private final AndroidFacet myFacet;
  private final int myRunContextId;
  private DevicePickerListModel myModel;
  @NotNull private final MergingUpdateQueue myUpdateQueue;
  private final LaunchCompatibilityChecker myCompatibilityChecker;

  private final AtomicReference<List<AvdInfo>> myAvdInfos = new AtomicReference<>();

  public DevicePicker(@NotNull Disposable parent,
                      int runContextId,
                      @NotNull final AndroidFacet facet,
                      @NotNull DeviceCount deviceCount,
                      @NotNull LaunchCompatibilityChecker compatibilityChecker,
                      @NotNull HelpHandler helpHandler) {
    myRunContextId = runContextId;
    myFacet = facet;

    myHelpHyperlink.addHyperlinkListener(e -> helpHandler.launchDiagnostics(AdbAssistantStats.Trigger.DONT_SEE_DEVICE));
    myCompatibilityChecker = compatibilityChecker;
    myHelpHandler = helpHandler;

    ListSpeedSearch speedSearch = new DeviceListSpeedSearch(myDevicesList);
    myDeviceRenderer = new AndroidDeviceRenderer(myCompatibilityChecker, speedSearch);

    setModel(new DevicePickerListModel());
    myDevicesList.setCellRenderer(myDeviceRenderer);
    //noinspection MagicConstant
    myDevicesList.setSelectionMode(getListSelectionMode(deviceCount));
    myDevicesList.addKeyListener(new MyListKeyListener(speedSearch));
    myDevicesList.addListSelectionListener(this);

    myNotificationPanel.setLayout(new BoxLayout(myNotificationPanel, BoxLayout.Y_AXIS));

    myCreateEmulatorButton.addActionListener(this);

    // the device change notifications from adb can sometimes be noisy (esp. when a device is [dis]connected)
    // we use this merging queue to collapse multiple updates to one
    myUpdateQueue = new MergingUpdateQueue("android.device.chooser", UPDATE_DELAY_MILLIS, true, null, this, null,
                                           Alarm.ThreadToUse.POOLED_THREAD);

    AndroidDebugBridge.addDebugBridgeChangeListener(this);
    AndroidDebugBridge.addDeviceChangeListener(this);

    Disposer.register(parent, this);
  }

  @Override
  public void dispose() {
    myDevicesList = null;
    AndroidDebugBridge.removeDebugBridgeChangeListener(this);
    AndroidDebugBridge.removeDeviceChangeListener(this);
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myDevicesList;
  }

  private void createUIComponents() {
    myDevicesList = new JBList<>();
    myScrollPane = ScrollPaneFactory.createScrollPane(myDevicesList);
    myHelpHyperlink = new HyperlinkLabel("Don't see your device?");
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    if (e.getSource() == myCreateEmulatorButton) {
      AvdOptionsModel avdOptionsModel = new AvdOptionsModel(null);
      ModelWizardDialog dialog = AvdWizardUtils.createAvdWizard(myPanel, myFacet.getModule().getProject(), avdOptionsModel);
      if (dialog.showAndGet()) {
        AvdInfo createdAvd = avdOptionsModel.getCreatedAvd();
        refreshAvds(createdAvd);
      }
    }
  }

  @Override
  public void valueChanged(ListSelectionEvent e) {
    if (e.getSource() == myDevicesList) {
      Set<String> selectedSerials = getSelectedSerials(myDevicesList.getSelectedValuesList());
      // Setting myDevicesList's model deselects everything. Ignore such events
      // so that we don't squash the meaningful selection that's already cached.
      if (selectedSerials.isEmpty()) {
        return;
      }
      ourSelectionsPerConfig.put(myRunContextId, selectedSerials);
      saveSelectionForProject(myFacet.getModule().getProject(), selectedSerials);
    }
  }

  @Override
  public void bridgeChanged(@Nullable AndroidDebugBridge bridge) {
    postUpdate();
  }

  @Override
  public void deviceConnected(@NotNull IDevice device) {
    postUpdate();
  }

  @Override
  public void deviceDisconnected(@NotNull final IDevice device) {
    postUpdate();
  }

  @Override
  public void deviceChanged(@NonNull IDevice device, int changeMask) {
    postUpdate();
  }

  public void refreshAvds(@Nullable AvdInfo avdToSelect) {
    myDevicesList.setPaintBusy(true);
    ApplicationManager.getApplication().executeOnPooledThread(() -> refreshAvdsNow(avdToSelect));
  }

  public void refreshAvdsNow(@Nullable AvdInfo avdToSelect) {
    List<AvdInfo> avdInfos = ImmutableList.copyOf(AvdManagerConnection.getDefaultAvdManagerConnection().getAvds(true));

    invokeLater(() -> {
      if (myDevicesList == null) { // Don't update anything after disposal of the dialog.
        return;
      }
      myAvdInfos.set(avdInfos);
      updateModelAndSelectAvd(avdToSelect);
      myDevicesList.setPaintBusy(false);
    });
  }

  private void updateErrorCheck() {
    myErrorGen++;
    myNotificationPanel.removeAll();
    if (myModel.getNumberOfConnectedDevices() == 0) {
      EditorNotificationPanel panel = new EditorNotificationPanel();
      panel.setText("No USB devices or running emulators detected");
      panel.createActionLabel("Troubleshoot", () -> myHelpHandler.launchDiagnostics(AdbAssistantStats.Trigger.NO_RUNNING_DEVICE));

      myNotificationPanel.add(panel);
    }
    List<AvdInfo> infos = myAvdInfos.get();
    if (infos != null && !infos.isEmpty()) {
      final int currentErrorGen = myErrorGen;
      executeOnPooledThread(() -> {
        final AccelerationErrorCode error = AvdManagerConnection.getDefaultAvdManagerConnection().checkAcceleration();
        if (error != AccelerationErrorCode.ALREADY_INSTALLED) {
          invokeLater(() -> {
            if (myErrorGen != currentErrorGen) {
              // The notification panel has been reset since we started this update.
              // Ignore this error, there is another request coming.
              return;
            }
            myNotificationPanel.add(new AccelerationErrorNotificationPanel(error, myFacet.getModule().getProject(),
                                                                           this::updateErrorCheck));
          });
        }
      });
    }
    myPanel.revalidate();
    myPanel.repaint();
  }

  /**
   * Schedules the given runnable for execution on a background thread.
   */
  private static void executeOnPooledThread(Runnable runnable) {
    ApplicationManager.getApplication().executeOnPooledThread(runnable);
  }

  /**
   * Schedules the given runnable for execution on the UI thread.
   */
  private static void invokeLater(Runnable runnable) {
    EdtInvocationManager.getInstance().invokeLater(runnable);
  }

  private void postUpdate() {
    myUpdateQueue.queue(new Update("updateDevicePickerModel") {
      @Override
      public void run() {
        updateModel();
      }

      @Override
      public boolean canEat(Update update) {
        return true;
      }
    });
  }

  private void selectAvd(@NotNull AvdInfo avdToSelect) {
    String serial = new LaunchableAndroidDevice(avdToSelect).getSerial();

    List<DevicePickerEntry> items = myModel.getItems();
    for (int i = 0; i < items.size(); i++) {
      DevicePickerEntry entry = items.get(i);
      if (entry.isMarker()) {
        continue;
      }

      AndroidDevice device = entry.getAndroidDevice();
      assert device != null : "Non marker entry cannot be null";
      if (serial.equals(device.getSerial())) {
        myDevicesList.setSelectedIndex(i);
        return;
      }
    }
  }

  private void updateModel() {
    updateModelAndSelectAvd(null);
  }

  private void updateModelAndSelectAvd(@Nullable AvdInfo avdToSelect) {
    AndroidDebugBridge bridge = AndroidDebugBridge.getBridge();
    if (bridge == null || !bridge.isConnected()) {
      return;
    }

    if (!ApplicationManager.getApplication().isDispatchThread()) {
      invokeLater(() -> updateModelAndSelectAvd(avdToSelect));
      return;
    }

    if (myDevicesList == null) { // Happens if the method is invoked after disposal of the dialog.
      return;
    }
    Set<String> selectedSerials = getSelectedSerials(myDevicesList.getSelectedValuesList());
    myDevicesList.setPaintBusy(true);

    // Look for connected devices and AVDs on a separate thread so we
    // do not delay the UI
    executeOnPooledThread(() -> {
      List<IDevice> connectedDevices = Arrays.asList(bridge.getDevices());
      // We should have the list of connected devices pretty quickly. Show
      // the connected devices now and then take more time to find the AVDs.
      DevicePickerListModel initialModel = new DevicePickerListModel(connectedDevices, Collections.emptyList());
      invokeLater(() -> {
        if (myDevicesList == null) { // Happens if the method is invoked after disposal of the dialog.
          return;
        }
        setModelAndSelectAvd(initialModel, avdToSelect, selectedSerials);
      });

      List<AvdInfo> infos = myAvdInfos.get();
      if (infos == null) {
        return;
      }
      DevicePickerListModel fullModel = new DevicePickerListModel(connectedDevices, infos);

      invokeLater(() -> {
        if (myDevicesList == null) { // Happens if the method is invoked after disposal of the dialog.
          return;
        }
        setModelAndSelectAvd(fullModel, avdToSelect, selectedSerials);

        // The help hyper link is shown only when there is no inline troubleshoot link.
        myHelpHyperlink.setVisible(myModel.getNumberOfConnectedDevices() == 0);

        updateErrorCheck();
        myDevicesList.setPaintBusy(false);
      });
    });
  }

  private void setModelAndSelectAvd(@NotNull DevicePickerListModel model,
                                    @Nullable AvdInfo avdToSelect,
                                    @NotNull Set<String> selectedSerials) {
    setModel(model);
    if (avdToSelect != null) {
      selectAvd(avdToSelect);
    }
    else {
      int[] selectedIndices = getIndices(myModel.getItems(), selectedSerials.isEmpty() ? getDefaultSelection() : selectedSerials);
      myDevicesList.setSelectedIndices(selectedIndices);
    }
  }

  private void setModel(@NotNull DevicePickerListModel model) {
    myDeviceRenderer.clearCache();
    myModel = model;
    myDevicesList.setModel(myModel);
  }

  @NotNull
  private static int[] getIndices(@NotNull List<DevicePickerEntry> items, @NotNull Set<String> selectedSerials) {
    TIntArrayList list = new TIntArrayList(selectedSerials.size());

    for (int i = 0; i < items.size(); i++) {
      DevicePickerEntry entry = items.get(i);
      if (entry.isMarker()) {
        continue;
      }

      AndroidDevice androidDevice = entry.getAndroidDevice();
      assert androidDevice != null : "An entry in the device picker must be either a marker or an AndroidDevice, got null";

      if (selectedSerials.contains(androidDevice.getSerial())) {
        list.add(i);
      }
    }

    return list.toNativeArray();
  }

  @NotNull
  private static Set<String> getSelectedSerials(@NotNull List<?> selectedValues) {
    Set<String> selection = new HashSet<>();

    for (Object o : selectedValues) {
      if (o instanceof DevicePickerEntry) {
        AndroidDevice device = ((DevicePickerEntry)o).getAndroidDevice();
        if (device != null) {
          selection.add(device.getSerial());
        }
      }
    }

    return selection;
  }

  @NotNull
  private Set<String> getDefaultSelection() {
    // first use the last selection for this config
    Set<String> lastSelection = ourSelectionsPerConfig.get(myRunContextId);

    // if this is the first time launching the dialog, pick up the previous selections from saved state
    if (lastSelection == null || lastSelection.isEmpty()) {
      lastSelection = getLastSelectionForProject(myFacet.getModule().getProject());
    }

    if (!lastSelection.isEmpty()) {
      // check if any of them actually present right now
      int[] indices = getIndices(myModel.getItems(), lastSelection);
      if (indices.length > 0) {
        return lastSelection;
      }
    }

    for (DevicePickerEntry entry : myModel.getItems()) {
      if (entry.isMarker()) {
        continue;
      }

      AndroidDevice androidDevice = entry.getAndroidDevice();
      assert androidDevice != null : "Non marker entry in the device picker doesn't contain an android device";
      if (myCompatibilityChecker.validate(androidDevice).isCompatible() != ThreeState.NO) {
        return ImmutableSet.of(androidDevice.getSerial());
      }
    }

    return Collections.emptySet();
  }

  private static int getListSelectionMode(@NotNull DeviceCount deviceCount) {
    return deviceCount.isMultiple() ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION : ListSelectionModel.SINGLE_SELECTION;
  }

  public ValidationInfo validate() {
    List<AndroidDevice> devices = getSelectedDevices();
    if (devices.isEmpty()) {
      return new ValidationInfo("No device selected", myDevicesList);
    }

    for (AndroidDevice device : devices) {
      LaunchCompatibility compatibility = myCompatibilityChecker.validate(device);
      if (compatibility.isCompatible() == ThreeState.NO) {
        String reason = StringUtil.notNullize(compatibility.getReason(), "Incompatible");
        if (devices.size() > 1) {
          reason = device.getName() + ": " + reason;
        }

        return new ValidationInfo(reason, myDevicesList);
      }
    }

    return null;
  }

  @NotNull
  public List<AndroidDevice> getSelectedDevices() {
    SmartList<AndroidDevice> devices = new SmartList<>();

    for (DevicePickerEntry value : myDevicesList.getSelectedValuesList()) {
      AndroidDevice device = value.getAndroidDevice();
      if (device != null) {
        devices.add(device);
      }
    }

    return devices;
  }

  public void installDoubleClickListener(@NotNull DoubleClickListener listener) {
    // wrap the incoming listener in a new listener so that we can remove the reference to the incoming object when we are disposed
    new MyDoubleClickListener(listener, this).installOn(myDevicesList);
  }

  private static Set<String> getLastSelectionForProject(@NotNull Project project) {
    String s = PropertiesComponent.getInstance(project).getValue(DEVICE_PICKER_LAST_SELECTION);
    return s == null ? Collections.emptySet() : ImmutableSet.copyOf(s.split(" "));
  }

  private static void saveSelectionForProject(@NotNull Project project, @NotNull Set<String> selectedSerials) {
    PropertiesComponent.getInstance(project).setValue(DEVICE_PICKER_LAST_SELECTION, Joiner.on(' ').join(selectedSerials));
  }

  /**
   * {@link MyListKeyListener} provides a custom key listener that makes sure that up/down key events don't end up selecting a marker.
   */
  private static class MyListKeyListener extends KeyAdapter {
    private final ListSpeedSearch mySpeedSearch;

    private MyListKeyListener(@NotNull ListSpeedSearch speedSearch) {
      mySpeedSearch = speedSearch;
    }

    @Override
    public void keyPressed(KeyEvent e) {
      if (mySpeedSearch.isPopupActive()) {
        return;
      }

      @SuppressWarnings("unchecked")
      JList<DevicePickerEntry> list = (JList<DevicePickerEntry>)e.getSource();
      if (allListElementsMatch(list, x -> x.isMarker())) {
        // If all elements are markers (e.g. when list is not populated yet, see bug 72018351),
        // we don't want to process up/down keys, as there is no appropriate entry to select.
        return;
      }

      int keyCode = e.getKeyCode();
      switch (keyCode) {
        case KeyEvent.VK_DOWN:
          ScrollingUtil.moveDown(list, e.getModifiersEx());
          break;
        case KeyEvent.VK_PAGE_DOWN:
          ScrollingUtil.movePageDown(list);
          break;
        case KeyEvent.VK_UP:
          ScrollingUtil.moveUp(list, e.getModifiersEx());
          break;
        case KeyEvent.VK_PAGE_UP:
          ScrollingUtil.movePageUp(list);
          break;
        default:
          // only interested in up/down actions
          return;
      }

      // move up or down further as long as the current selection is a marker
      for (DevicePickerEntry entry = list.getSelectedValue(); entry.isMarker(); entry = list.getSelectedValue()) {
        if (keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_PAGE_UP) {
          ScrollingUtil.moveUp(list, e.getModifiersEx());
        }
        else {
          ScrollingUtil.moveDown(list, e.getModifiersEx());
        }
      }

      e.consume();
    }

    private static <E> boolean allListElementsMatch(@NotNull JList<E> list, @NotNull Predicate<E> predicate) {
      for (int i = 0; i < list.getModel().getSize(); i++){
        if (!predicate.test(list.getModel().getElementAt(i))) {
          return false;
        }
      }

      // See https://stackoverflow.com/a/30223378
      return true;
    }
  }

  private static class DeviceListSpeedSearch extends ListSpeedSearch<DevicePickerEntry> {
    public DeviceListSpeedSearch(JBList<DevicePickerEntry> list) {
      super(list);
    }

    @Override
    protected String getElementText(Object element) {
      if (element instanceof DevicePickerEntry) {
        DevicePickerEntry entry = (DevicePickerEntry)element;
        if (!entry.isMarker()) {
          AndroidDevice device = entry.getAndroidDevice();
          assert device != null : "entry not a marker, yet device is null";
          return device.getName();
        }
      }
      return "";
    }
  }

  private static class MyDoubleClickListener extends DoubleClickListener implements Disposable {
    private DoubleClickListener myDelegate;

    public MyDoubleClickListener(DoubleClickListener delegate, Disposable parent) {
      myDelegate = delegate;
      Disposer.register(parent, this);
    }

    @Override
    protected boolean onDoubleClick(MouseEvent event) {
      return myDelegate != null && myDelegate.onClick(event, 2);
    }

    @Override
    public void dispose() {
      myDelegate = null;
    }
  }
}
