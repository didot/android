/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view;

import com.android.annotations.concurrency.UiThread;
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResults;
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice;
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.largeFilesEditor.GuiUtils;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import icons.StudioIcons;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.Locale;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shows a list of devices. This view is intended to be used in Android test suite details page
 * in conjunction with {@link DetailsViewContentView}.
 * A user can click on a device item to look up detailed test results for a selected device.
 */
public class DetailsViewDeviceSelectorListView {

  /**
   * Interface to listen events occurred in {@link DetailsViewDeviceSelectorListView}.
   */
  public interface DetailsViewDeviceSelectorListViewListener {
    /**
     * Called when a user selects a device for looking up test results for the device.
     */
    @UiThread
    void onDeviceSelected(@NotNull AndroidDevice selectedDevice);
  }

  // Those properties are initialized by IntelliJ form editor before the constructor using reflection.
  private JPanel myRootPanel;
  private JBList<AndroidDevice> myDeviceList;
  private DefaultListModel<AndroidDevice> myDeviceListModel;

  private AndroidDeviceListCellRenderer myCellRenderer;

  public DetailsViewDeviceSelectorListView(@NotNull DetailsViewDeviceSelectorListViewListener listener) {
    myDeviceList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent event) {
        AndroidDevice selectedItem = myDeviceList.getSelectedValue();
        if (selectedItem != null) {
          listener.onDeviceSelected(selectedItem);
        }
      }
    });
  }

  /**
   * Creates and initializes custom view components. This method is called by IntelliJ form editor runtime
   * before the constructor is called.
   */
  private void createUIComponents() {
    myDeviceListModel = new DefaultListModel<>();
    myDeviceList = new JBList<>(myDeviceListModel);
    myDeviceList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
    myDeviceList.putClientProperty(AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true);
    myCellRenderer = new AndroidDeviceListCellRenderer();
    myDeviceList.setCellRenderer(myCellRenderer);
  }

  /**
   * Returns the root panel.
   */
  @NotNull
  public JPanel getRootPanel() {
    return myRootPanel;
  }

  /**
   * Adds a given device to the list.
   */
  @UiThread
  public void addDevice(@NotNull AndroidDevice device) {
    myDeviceListModel.addElement(device);
  }

  /**
   * Select a given device in the list.
   * @param device a device to be selected
   */
  @UiThread
  public void selectDevice(@Nullable AndroidDevice device) {
    myDeviceList.setSelectedValue(device, true);
  }

  /**
   * Updates the view with a given AndroidTestResults.
   */
  @UiThread
  public void setAndroidTestResults(@NotNull AndroidTestResults results) {
    myCellRenderer.setAndroidTestResults(results);
    myDeviceList.updateUI();
  }

  @VisibleForTesting
  public JBList<AndroidDevice> getDeviceListForTesting() {
    return myDeviceList;
  }

  private static class AndroidDeviceListCellRenderer extends DefaultListCellRenderer {

    private final EmptyBorder myEmptyBorder = JBUI.Borders.empty(5, 10);
    private final JPanel myCellRendererComponent = new JPanel(new BorderLayout());
    private final JLabel myDeviceLabel = new JLabel();
    private final JLabel myTestResultLabel = new JLabel();

    @Nullable
    private AndroidTestResults myTestResults;

    private AndroidDeviceListCellRenderer() {
      JPanel left = new JPanel();
      left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
      setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
      left.add(this);
      left.add(myDeviceLabel);

      myCellRendererComponent.add(left, BorderLayout.WEST);
      myCellRendererComponent.add(myTestResultLabel, BorderLayout.EAST);
      GuiUtils.setStandardLineBorderToPanel(myCellRendererComponent, 0, 0, 1, 0);
    }

    public void setAndroidTestResults(@NotNull AndroidTestResults results) {
      myTestResults = results;
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list,
                                                  Object value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      super.getListCellRendererComponent(list, " ", index, isSelected, cellHasFocus);

      if (!(value instanceof AndroidDevice)) {
        return myCellRendererComponent;
      }
      AndroidDevice device = (AndroidDevice) value;

      myDeviceLabel.setText(
        String.format(Locale.US, "<html>%s<br>API %d</html>",
                      device.getName(), device.getVersion().getApiLevel()));
      myDeviceLabel.setIcon(getIconForDeviceType(device.getDeviceType()));
      myDeviceLabel.setIconTextGap(10);
      myDeviceLabel.setBorder(myEmptyBorder);

      if (myTestResults != null) {
        myTestResultLabel.setIcon(
          AndroidTestResultsTableViewKt.getIconFor(myTestResults.getTestCaseResult(device)));
      } else {
        myTestResultLabel.setIcon(null);
      }
      myTestResultLabel.setBorder(myEmptyBorder);

      return myCellRendererComponent;
    }

    @Nullable
    private static Icon getIconForDeviceType(@NotNull AndroidDeviceType deviceType) {
      switch (deviceType) {
        case LOCAL_EMULATOR:
          return StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE;
        case LOCAL_PHYSICAL_DEVICE:
          return StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE;
        default:
          return null;
      }
    }
  }
}
