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
package com.android.tools.idea.configurations;

import com.android.annotations.Nullable;
import com.android.sdklib.devices.Device;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import icons.AndroidIcons;
import org.jetbrains.android.actions.RunAndroidAvdManagerAction;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

import static com.android.ide.common.rendering.HardwareConfigHelper.*;

public class DeviceMenuAction extends FlatComboAction {
  private final RenderContext myRenderContext;

  public DeviceMenuAction(@NotNull RenderContext renderContext) {
    myRenderContext = renderContext;
    Presentation presentation = getTemplatePresentation();
    presentation.setDescription("The virtual device to render the layout with");
    presentation.setIcon(AndroidIcons.Display);
    updatePresentation(presentation);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    updatePresentation(e.getPresentation());
  }

  private void updatePresentation(Presentation presentation) {
    Configuration configuration = myRenderContext.getConfiguration();
    boolean visible = configuration != null;
    if (visible) {
      String label = getDeviceLabel(configuration.getDevice(), true);
      presentation.setText(label);
    }
    if (visible != presentation.isVisible()) {
      presentation.setVisible(visible);
    }
  }

  /**
   * Returns a suitable label to use to display the given device
   *
   * @param device the device to produce a label for
   * @param brief  if true, generate a brief label (suitable for a toolbar
   *               button), otherwise a fuller name (suitable for a menu item)
   * @return the label
   */
  public static String getDeviceLabel(@Nullable Device device, boolean brief) {
    if (device == null) {
      return "";
    }
    String name = device.getName();

    if (brief) {
      // Produce a really brief summary of the device name, suitable for
      // use in the narrow space available in the toolbar for example
      int nexus = name.indexOf("Nexus"); //$NON-NLS-1$
      if (nexus != -1) {
        int begin = name.indexOf('(');
        if (begin != -1) {
          begin++;
          int end = name.indexOf(')', begin);
          if (end != -1) {
            return name.substring(begin, end).trim();
          }
        }
      }
    }

    return name;
  }

  @Override
  @NotNull
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    DefaultActionGroup group = new DefaultActionGroup(null, true);
    Configuration configuration = myRenderContext.getConfiguration();
    if (configuration == null) {
      return group;
    }
    Device current = configuration.getDevice();
    ConfigurationManager configurationManager = configuration.getConfigurationManager();
    List<Device> deviceList = configurationManager.getDevices();

    AndroidFacet facet = AndroidFacet.getInstance(configurationManager.getModule());
    assert facet != null;
    final AvdManager avdManager = facet.getAvdManagerSilently();
    if (avdManager != null) {
      boolean separatorNeeded = false;
      for (AvdInfo avd : avdManager.getValidAvds()) {
        Device device = configurationManager.createDeviceForAvd(avd);
        if (device != null) {
          String avdName = avd.getName();
          boolean selected = current != null && current.getName().equals(avdName);
          group.add(new SetDeviceAction(avdName, device, selected));
          separatorNeeded = true;
        }
      }

      if (separatorNeeded) {
        group.addSeparator();
      }
    }

    // Group the devices by manufacturer, then put them in the menu.
    // If we don't have anything but Nexus devices, group them together rather than
    // make many manufacturer submenus.
    boolean haveNexus = false;
    if (!deviceList.isEmpty()) {
      Map<String, List<Device>> manufacturers = new TreeMap<String, List<Device>>();
      for (Device device : deviceList) {
        List<Device> devices;
        if (isNexus(device)) {
          haveNexus = true;
        }
        if (manufacturers.containsKey(device.getManufacturer())) {
          devices = manufacturers.get(device.getManufacturer());
        }
        else {
          devices = new ArrayList<Device>();
          manufacturers.put(device.getManufacturer(), devices);
        }
        devices.add(device);
      }
      List<Device> nexus = new ArrayList<Device>();
      List<Device> generic = new ArrayList<Device>();
      if (haveNexus) {
        // Nexus
        for (List<Device> devices : manufacturers.values()) {
          for (Device device : devices) {
            if (isNexus(device)) {
              if (device.getManufacturer().equals(MANUFACTURER_GENERIC)) {
                generic.add(device);
              }
              else {
                nexus.add(device);
              }
            }
            else {
              generic.add(device);
            }
          }
        }
      }

      if (!nexus.isEmpty()) {
        sortNexusList(nexus);
        for (final Device device : nexus) {
          group.add(new SetDeviceAction(getNexusLabel(device), device, current == device));
        }

        group.addSeparator();
      }

      // Generate the generic menu.
      Collections.reverse(generic);
      for (final Device device : generic) {
        group.add(new SetDeviceAction(getGenericLabel(device), device, current == device));
      }
    }

    group.addSeparator();
    group.add(new RunAndroidAvdManagerAction("Add Device Definition..."));

// TODO: Add multiconfiguration editing
//    group.addSeparator();
//
//      ConfigurationMenuListener.addTogglePreviewModeAction(menu,
//              "Preview All Screens", chooser, RenderPreviewMode.SCREENS);
//

    return group;
  }

  private class SetDeviceAction extends ToggleAction {
    private final Device myDevice;
    private boolean mySelected;

    public SetDeviceAction(@NotNull final String title, @NotNull final Device device, final boolean select) {
      super(title);
      myDevice = device;
      mySelected = select;
      if (select) {
        getTemplatePresentation().setIcon(AllIcons.Actions.Checked);
      }
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return mySelected;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      mySelected = state;
      if (state) {
        Configuration configuration = myRenderContext.getConfiguration();
        if (configuration != null) {
          configuration.setDevice(myDevice, true);
        }
      }
    }
  }
}
