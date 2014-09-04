/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.ddms;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.ddms.actions.*;
import com.android.tools.idea.ddms.hprof.DumpHprofAction;
import com.android.tools.idea.ddms.hprof.SaveHprofHandler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.SortedListModel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class DevicePanel implements Disposable,
                                    AndroidDebugBridge.IClientChangeListener,
                                    AndroidDebugBridge.IDeviceChangeListener {
  private static final String NO_DEVICES = "No Connected Devices";
  private JPanel myPanel;
  private JComboBox myDevicesComboBox;
  private JBList myClientsList;

  private final DefaultComboBoxModel myComboBoxModel = new DefaultComboBoxModel();
  private final SortedListModel<Client> myClientsListModel = new SortedListModel<Client>(new ClientCellRenderer.ClientComparator());
  private boolean myIgnoreListeners;

  private final DeviceContext myDeviceContext;
  private final Project myProject;
  private AndroidDebugBridge myBridge;

  public DevicePanel(@NotNull Project project, @NotNull DeviceContext context) {
    myProject = project;
    myDeviceContext = context;
    Disposer.register(myProject, this);

    BridgeProvider bridgeProvider = new BridgeProvider(myProject);
    if (!AndroidSdkUtils.activateDdmsIfNecessary(project, bridgeProvider)) {
      myBridge = null;
      return;
    }

    myBridge = bridgeProvider.compute();
    if (myBridge == null) {
      return;
    }

    myBridge.addDeviceChangeListener(this);
    myBridge.addClientChangeListener(this);

    ClientData.setMethodProfilingHandler(new OpenVmTraceHandler(project));
    ClientData.setHprofDumpHandler(new SaveHprofHandler(project));
    ClientData.setAllocationTrackingHandler(new ShowAllocationsHandler(project));

    initializeDeviceCombo();
    initializeClientsList();
  }

  private void initializeDeviceCombo() {
    myDevicesComboBox.setModel(myComboBoxModel);
    myDevicesComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        Object sel = myDevicesComboBox.getSelectedItem();
        IDevice device = (sel instanceof IDevice) ? (IDevice)sel : null;
        updateClientsForDevice(device);
        myDeviceContext.fireDeviceSelected(device);
        myDeviceContext.fireClientSelected(null);
      }
    });
    myDevicesComboBox.setRenderer(new DeviceRenderer.DeviceComboBoxRenderer());

    IDevice[] devices = myBridge.getDevices();
    if (devices.length == 0) {
      myComboBoxModel.addElement(NO_DEVICES);
    } else {
      for (IDevice device : devices) {
        myComboBoxModel.addElement(device);
      }
    }
    myDevicesComboBox.setSelectedIndex(0);
  }

  public void selectDevice(@NotNull final IDevice device) {
    myDevicesComboBox.setSelectedItem(device);
  }

  private void initializeClientsList() {
    myClientsList.setModel(myClientsListModel);
    myClientsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myClientsList.setEmptyText("No debuggable applications");
    myClientsList.setCellRenderer(new ClientCellRenderer());
    new ListSpeedSearch(myClientsList) {
      @Override
      protected boolean isMatchingElement(Object element, String pattern) {
        if (element instanceof Client) {
          String pkg = ((Client)element).getClientData().getClientDescription();
          return pkg != null && pkg.contains(pattern);
        }
        return false;
      }
    };
    myClientsList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting() || myIgnoreListeners) {
          return;
        }
        Object sel = myClientsList.getSelectedValue();
        Client c = (sel instanceof Client) ? (Client)sel : null;
        myDeviceContext.fireClientSelected(c);
      }
    });
  }

  @Override
  public void dispose() {
    if (myBridge != null) {
      AndroidDebugBridge.removeDeviceChangeListener(this);
      AndroidDebugBridge.removeClientChangeListener(this);

      myBridge = null;
    }
  }

  public JPanel getContentPanel() {
    return myPanel;
  }

  @Override
  public void clientChanged(Client client, int changeMask) {
  }

  @Override
  public void deviceConnected(final IDevice device) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        myComboBoxModel.removeElement(NO_DEVICES);
        myComboBoxModel.addElement(device);
      }
    });
  }

  @Override
  public void deviceDisconnected(final IDevice device) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        myComboBoxModel.removeElement(device);
        if (myComboBoxModel.getSize() == 0) {
          myComboBoxModel.addElement(NO_DEVICES);
        }
      }
    });
  }

  @Override
  public void deviceChanged(final IDevice device, final int changeMask) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        myDevicesComboBox.repaint();
        if (!myDevicesComboBox.getSelectedItem().equals(device)) {
          return;
        }

        if ((changeMask & IDevice.CHANGE_CLIENT_LIST) == IDevice.CHANGE_CLIENT_LIST) {
          updateClientsForDevice(device);
        }

        myDeviceContext.fireDeviceChanged(device, changeMask);
      }
    });
  }

  private void updateClientsForDevice(@Nullable IDevice device) {
    if (device == null) {
      // Note: we do want listeners triggered when the device itself disappears.
      // so we don't set myIgnoreListeners
      myClientsListModel.clear();
      return;
    }

    Object selectedObject = myClientsList.getSelectedValue();
    Client[] clients = device.getClients();

    try {
      // we want to refresh the list of clients, however we don't want the listeners to
      // think that this is a user driven change to the list selection.
      // the only time this update should trigger the selection listener is if the currently selected client isn't there anymore
      myIgnoreListeners = ArrayUtil.contains(selectedObject, clients);
      myClientsListModel.clear();
      myClientsListModel.addAll(clients);
      myClientsList.setSelectedValue(selectedObject, true);
    } finally {
      myIgnoreListeners = false;
    }
  }

  @NotNull
  public ActionGroup getToolbarActions() {
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(new ScreenshotAction(myProject, myDeviceContext));
    group.add(new ScreenRecorderAction(myProject, myDeviceContext));
    group.add(DumpSysActions.create(myProject, myDeviceContext));
    //group.add(new MyFileExplorerAction());
    group.add(new Separator());

    group.add(new TerminateVMAction(myDeviceContext));
    group.add(new GcAction(myDeviceContext));
    group.add(new DumpHprofAction(myDeviceContext));
    //group.add(new MyAllocationTrackerAction());
    //group.add(new Separator());

    group.add(new ToggleMethodProfilingAction(myProject, myDeviceContext));
    //group.add(new MyThreadDumpAction()); // thread dump -> systrace

    group.add(new ToggleAllocationTrackingAction(myDeviceContext));

    return group;
  }

  private static class BridgeProvider implements Computable<AndroidDebugBridge> {
    private boolean myFirstCompute = true;
    private final Project myProject;
    private AndroidDebugBridge myBridge;

    public BridgeProvider(Project project) {
      myProject = project;
    }

    @Nullable
    @Override
    public AndroidDebugBridge compute() {
      boolean bridgeDisconnected = myBridge != null && !myBridge.isConnected();
      if (myFirstCompute || bridgeDisconnected) {
        myFirstCompute = false;
        myBridge = AndroidSdkUtils.getDebugBridge(myProject);
      }
      return myBridge;
    }
  }
}
