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

import static com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestSuiteConstantsKt.ANDROID_TEST_RESULT_LISTENER_KEY;

import com.android.annotations.concurrency.AnyThread;
import com.android.annotations.concurrency.UiThread;
import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.stdui.CommonToggleButton;
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultListener;
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResults;
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice;
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase;
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult;
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuite;
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteDetailsView.AndroidTestSuiteDetailsViewListener;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.largeFilesEditor.GuiUtils;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.progress.util.ColorProgressBar;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.SortedComboBoxModel;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A console view to display a test execution and result of Android instrumentation tests.
 *
 * Note: This view is under development and most of methods are not implemented yet.
 */
public class AndroidTestSuiteView implements ConsoleView, AndroidTestResultListener, AndroidTestResultsTableListener,
                                             AndroidTestSuiteDetailsViewListener {
  /**
   * Minimum height of swing components in ThreeComponentsSplitter container in pixel.
   */
  private static final int MIN_COMPONENT_HEIGHT_IN_SPLITTER = 48;

  /**
   * A ComboBox item to be used in API level filter ComboBox.
   */
  @VisibleForTesting
  static final class ApiLevelFilterComboBoxItem implements Comparable<ApiLevelFilterComboBoxItem> {
    @NotNull final Comparator<AndroidVersion> myComparator = Comparator.nullsFirst(Comparator.naturalOrder());
    @Nullable final AndroidVersion myVersion;

    /**
     * @param androidVersion an Android version to be shown or null for "All API versions".
     */
    ApiLevelFilterComboBoxItem(@Nullable AndroidVersion androidVersion) {
      myVersion = androidVersion;
    }

    @Override
    public int compareTo(@NotNull ApiLevelFilterComboBoxItem o) {
      return Objects.compare(myVersion, o.myVersion, myComparator);
    }

    @Override
    public String toString() {
      if (myVersion == null) {
        return "All API levels";
      } else {
        return String.format(Locale.US, "API %s", myVersion.getApiString());
      }
    }

    @Override
    public int hashCode() {
      return Objects.hash(myVersion);
    }
  }

  /**
   * A ComboBox item to be used in device filter ComboBox.
   */
  @VisibleForTesting
  static final class DeviceFilterComboBoxItem implements Comparable<DeviceFilterComboBoxItem> {
    @NotNull final Comparator<String> myComparator = Comparator.nullsFirst(Comparator.naturalOrder());
    @Nullable final AndroidDevice myDevice;

    /**
     * @param androidDevice an Android device to be shown or null for "All devices".
     */
    DeviceFilterComboBoxItem(@Nullable AndroidDevice androidDevice) {
      myDevice = androidDevice;
    }

    @Override
    public int compareTo(@NotNull DeviceFilterComboBoxItem o) {
      String lhs = null;
      if (myDevice != null) {
        lhs = myDevice.getName();
      }
      String rhs = null;
      if (o.myDevice != null) {
        rhs = o.myDevice.getName();
      }
      return Objects.compare(lhs, rhs, myComparator);
    }

    @Override
    public String toString() {
      if (myDevice == null) {
        return "All devices";
      } else {
        return myDevice.getName();
      }
    }

    @Override
    public int hashCode() {
      return Objects.hash(myDevice);
    }
  }

  // Those properties are initialized by IntelliJ form editor before the constructor using reflection.
  private JPanel myRootPanel;
  private JProgressBar myProgressBar;
  private JBLabel myStatusText;
  private JBLabel myStatusBreakdownText;
  private JPanel myTableViewContainer;
  private JPanel myStatusPanel;
  private MyItemSeparator myStatusSeparator;
  private JPanel myFilterPanel;
  private CommonToggleButton myAllToggleButton;
  private CommonToggleButton myFailedToggleButton;
  private MyItemSeparator myFilterSeparator;
  private ComboBox<DeviceFilterComboBoxItem> myDeviceFilterComboBox;
  @VisibleForTesting SortedComboBoxModel<DeviceFilterComboBoxItem> myDeviceFilterComboBoxModel;
  private MyItemSeparator myDeviceFilterSeparator;
  private ComboBox<ApiLevelFilterComboBoxItem> myApiLevelFilterComboBox;
  @VisibleForTesting SortedComboBoxModel<ApiLevelFilterComboBoxItem> myApiLevelFilterComboBoxModel;
  @VisibleForTesting CommonToggleButton myPassedToggleButton;
  private CommonToggleButton myInProgressToggleButton;

  private final ThreeComponentsSplitter myComponentsSplitter;
  private final AndroidTestResultsTableView myTable;
  private final AndroidTestSuiteDetailsView myDetailsView;

  private int scheduledTestCases = 0;
  private int passedTestCases = 0;
  private int failedTestCases = 0;
  private int skippedTestCases = 0;

  /**
   * This method is invoked before the constructor by IntelliJ form editor runtime.
   */
  private void createUIComponents() {
    myStatusSeparator = new MyItemSeparator();
    myFilterSeparator = new MyItemSeparator();
    myDeviceFilterSeparator = new MyItemSeparator();
    myAllToggleButton = new CommonToggleButton("All", null);
    myAllToggleButton.setBorder(JBUI.Borders.empty(10));
    myAllToggleButton.setSelected(true);
    myFailedToggleButton = new CommonToggleButton(null, AllIcons.RunConfigurations.TestFailed);
    myFailedToggleButton.setBorder(JBUI.Borders.empty(10));
    myPassedToggleButton = new CommonToggleButton(null, AllIcons.RunConfigurations.TestPassed);
    myPassedToggleButton.setBorder(JBUI.Borders.empty(10));
    myInProgressToggleButton = new CommonToggleButton(null, AllIcons.Process.Step_1);
    myInProgressToggleButton.setBorder(JBUI.Borders.empty(10));

    myDeviceFilterComboBoxModel = new SortedComboBoxModel<>(Comparator.naturalOrder());
    DeviceFilterComboBoxItem allDevicesItem = new DeviceFilterComboBoxItem(null);
    myDeviceFilterComboBoxModel.add(allDevicesItem);
    myDeviceFilterComboBoxModel.setSelectedItem(allDevicesItem);
    myDeviceFilterComboBox = new ComboBox<>(myDeviceFilterComboBoxModel);

    myApiLevelFilterComboBoxModel = new SortedComboBoxModel<>(Comparator.naturalOrder());
    ApiLevelFilterComboBoxItem allApiLevelsItem = new ApiLevelFilterComboBoxItem(null);
    myApiLevelFilterComboBoxModel.add(allApiLevelsItem);
    myApiLevelFilterComboBoxModel.setSelectedItem(allApiLevelsItem);
    myApiLevelFilterComboBox = new ComboBox<>(myApiLevelFilterComboBoxModel);
  }

  /**
   * Constructs AndroidTestSuiteView.
   *
   * @param parentDisposable a parent disposable which this view's lifespan is tied with.
   * @param project a project which this test suite view belongs to, or null.
   */
  @UiThread
  public AndroidTestSuiteView(@NotNull Disposable parentDisposable, @NotNull Project project) {
    GuiUtils.setStandardLineBorderToPanel(myStatusPanel, 0, 0, 1, 0);
    GuiUtils.setStandardLineBorderToPanel(myFilterPanel, 0, 0, 1, 0);

    myAllToggleButton.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          myFailedToggleButton.setSelected(false);
          myPassedToggleButton.setSelected(false);
          myInProgressToggleButton.setSelected(false);
        }
      }
    });
    ItemListener resultFilterButtonItemListener = new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          myAllToggleButton.setSelected(false);
        } else if (e.getStateChange() == ItemEvent.DESELECTED) {
          // If all test result filters are deselected, select "All" toggle button automatically.
          if (!myFailedToggleButton.isSelected()
              && !myPassedToggleButton.isSelected()
              && !myInProgressToggleButton.isSelected()) {
            myAllToggleButton.setSelected(true);
          }
        }
      }
    };
    myFailedToggleButton.addItemListener(resultFilterButtonItemListener);
    myPassedToggleButton.addItemListener(resultFilterButtonItemListener);
    myInProgressToggleButton.addItemListener(resultFilterButtonItemListener);

    myTable = new AndroidTestResultsTableView(this);
    myTable.setRowFilter(testResults -> {
      if (myAllToggleButton.isSelected()) {
        return true;
      }
      if (myFailedToggleButton.isSelected()
          && testResults.getTestResultSummary() == AndroidTestCaseResult.FAILED) {
        return true;
      }
      if (myPassedToggleButton.isSelected()
          && testResults.getTestResultSummary() == AndroidTestCaseResult.PASSED) {
        return true;
      }
      if (myInProgressToggleButton.isSelected()
          && testResults.getTestResultSummary() == AndroidTestCaseResult.IN_PROGRESS) {
        return true;
      }
      return false;
    });
    myTable.setColumnFilter(androidDevice -> {
      DeviceFilterComboBoxItem deviceItem = myDeviceFilterComboBoxModel.getSelectedItem();
      if (deviceItem != null && deviceItem.myDevice != null) {
        if (!androidDevice.getId().equals(deviceItem.myDevice.getId())) {
          return false;
        }
      }

      ApiLevelFilterComboBoxItem apiItem = myApiLevelFilterComboBoxModel.getSelectedItem();
      if (apiItem != null && apiItem.myVersion != null) {
        if (!androidDevice.getVersion().equals(apiItem.myVersion)) {
          return false;
        }
      }
      return true;
    });
    ItemListener tableUpdater = new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        myTable.refreshTable();
      }
    };
    myAllToggleButton.addItemListener(tableUpdater);
    myFailedToggleButton.addItemListener(tableUpdater);
    myPassedToggleButton.addItemListener(tableUpdater);
    myInProgressToggleButton.addItemListener(tableUpdater);
    myDeviceFilterComboBox.addItemListener(tableUpdater);
    myApiLevelFilterComboBox.addItemListener(tableUpdater);
    myTableViewContainer.add(myTable.getComponent());

    myComponentsSplitter = new ThreeComponentsSplitter(/*vertical=*/true,
                                                       /*onePixelDividers=*/true);
    myComponentsSplitter.setOpaque(false);
    myComponentsSplitter.setMinSize(MIN_COMPONENT_HEIGHT_IN_SPLITTER);
    myComponentsSplitter.setHonorComponentsMinimumSize(true);
    Disposer.register(this, myComponentsSplitter);
    myComponentsSplitter.setFirstComponent(myRootPanel);

    myDetailsView = new AndroidTestSuiteDetailsView(parentDisposable, this, project);
    myDetailsView.getRootPanel().setVisible(false);
    myComponentsSplitter.setLastComponent(myDetailsView.getRootPanel());

    updateProgress();

    Disposer.register(parentDisposable, this);
  }

  @UiThread
  private void updateProgress() {
    int completedTestCases = passedTestCases + failedTestCases + skippedTestCases;
    if (scheduledTestCases == 0) {
      myProgressBar.setValue(0);
      myProgressBar.setIndeterminate(false);
      myProgressBar.setForeground(ColorProgressBar.BLUE);
    } else {
      myProgressBar.setMaximum(scheduledTestCases);
      myProgressBar.setValue(completedTestCases);
      myProgressBar.setIndeterminate(false);
      if (failedTestCases > 0) {
        myProgressBar.setForeground(ColorProgressBar.RED);
      } else if (completedTestCases == scheduledTestCases) {
        myProgressBar.setForeground(ColorProgressBar.GREEN);
      }
    }

    myStatusText.setText(AndroidBundle.message("android.testartifacts.instrumented.testsuite.status.summary", completedTestCases));
    myStatusBreakdownText.setText(AndroidBundle.message("android.testartifacts.instrumented.testsuite.status.breakdown",
                                                        failedTestCases, passedTestCases, skippedTestCases, /*errorTestCases=*/0));
  }

  @Override
  @AnyThread
  public void onTestSuiteScheduled(@NotNull AndroidDevice device) {
    AppUIUtil.invokeOnEdt(() -> {
      DeviceFilterComboBoxItem deviceFilterItem = new DeviceFilterComboBoxItem(device);
      if (myDeviceFilterComboBoxModel.indexOf(deviceFilterItem) == -1) {
        myDeviceFilterComboBoxModel.add(deviceFilterItem);
      }

      ApiLevelFilterComboBoxItem apiLevelFilterItem = new ApiLevelFilterComboBoxItem(device.getVersion());
      if (myApiLevelFilterComboBoxModel.indexOf(apiLevelFilterItem) == -1) {
        myApiLevelFilterComboBoxModel.add(apiLevelFilterItem);
      }

      myTable.addDevice(device);
      myDetailsView.addDevice(device);
    });
  }

  @Override
  @AnyThread
  public void onTestSuiteStarted(@NotNull AndroidDevice device, @NotNull AndroidTestSuite testSuite) {
    AppUIUtil.invokeOnEdt(() -> {
      scheduledTestCases += testSuite.getTestCaseCount();
      updateProgress();
    });
  }

  @Override
  @AnyThread
  public void onTestCaseStarted(@NotNull AndroidDevice device, @NotNull AndroidTestSuite testSuite, @NotNull AndroidTestCase testCase) {
    AppUIUtil.invokeOnEdt(() -> {
      myTable.addTestCase(device, testCase);
      myDetailsView.reloadAndroidTestResults();
    });
  }

  @Override
  @AnyThread
  public void onTestCaseFinished(@NotNull AndroidDevice device,
                                 @NotNull AndroidTestSuite testSuite,
                                 @NotNull AndroidTestCase testCase) {
    AppUIUtil.invokeOnEdt(() -> {
      switch (Preconditions.checkNotNull(testCase.getResult())) {
        case PASSED:
          passedTestCases++;
          break;
        case FAILED:
          failedTestCases++;
          break;
        case SKIPPED:
          skippedTestCases++;
          break;
      }
      updateProgress();
      myTable.refreshTable();
      myDetailsView.reloadAndroidTestResults();
    });
  }

  @Override
  @AnyThread
  public void onTestSuiteFinished(@NotNull AndroidDevice device, @NotNull AndroidTestSuite testSuite) {
    AppUIUtil.invokeOnEdt(() -> {
      myTable.refreshTable();
      myDetailsView.reloadAndroidTestResults();
    });
  }

  @Override
  @UiThread
  public void onAndroidTestResultsRowSelected(@NotNull AndroidTestResults selectedResults,
                                              @Nullable AndroidDevice selectedDevice) {
    openAndroidTestSuiteDetailsView(selectedResults, selectedDevice);
  }

  @Override
  @UiThread
  public void onAndroidTestSuiteDetailsViewCloseButtonClicked() {
    closeAndroidTestSuiteDetailsView();
  }

  @UiThread
  private void openAndroidTestSuiteDetailsView(@NotNull AndroidTestResults results,
                                               @Nullable AndroidDevice selectedDevice) {
    myDetailsView.setAndroidTestResults(results);
    if (selectedDevice != null) {
      myDetailsView.selectDevice(selectedDevice);
    }
    myDetailsView.getRootPanel().setVisible(true);

    // ComponentsSplitter doesn't update child components' size automatically when you change its visibility.
    // So we have to update them manually here otherwise the size can be invalid (e.g. smaller than the min size).
    myComponentsSplitter.setFirstSize(myComponentsSplitter.getFirstSize());
    myComponentsSplitter.setLastSize(myComponentsSplitter.getLastSize());
  }

  @UiThread
  private void closeAndroidTestSuiteDetailsView() {
    myDetailsView.getRootPanel().setVisible(false);

    // ComponentsSplitter doesn't update child components' size automatically when you change its visibility.
    // So we have to update them manually here otherwise the size can be invalid (e.g. smaller than the min size).
    myComponentsSplitter.setFirstSize(myComponentsSplitter.getFirstSize());
    myComponentsSplitter.setLastSize(myComponentsSplitter.getLastSize());
  }

  @Override
  public void print(@NotNull String text, @NotNull ConsoleViewContentType contentType) { }

  @Override
  public void clear() { }

  @Override
  public void scrollTo(int offset) { }

  @Override
  public void attachToProcess(ProcessHandler processHandler) {
    // Put this test suite view to the process handler as AndroidTestResultListener so the view
    // is notified the test results and to be updated.
    processHandler.putCopyableUserData(ANDROID_TEST_RESULT_LISTENER_KEY, this);
  }

  @Override
  public void setOutputPaused(boolean value) { }

  @Override
  public boolean isOutputPaused() {
    return false;
  }

  @Override
  public boolean hasDeferredOutput() {
    return false;
  }

  @Override
  public void performWhenNoDeferredOutput(@NotNull Runnable runnable) {
    runnable.run();
  }

  @Override
  public void setHelpId(@NotNull String helpId) { }

  @Override
  public void addMessageFilter(@NotNull Filter filter) { }

  @Override
  public void printHyperlink(@NotNull String hyperlinkText, @Nullable HyperlinkInfo info) { }

  @Override
  public int getContentSize() {
    return 0;
  }

  @Override
  public boolean canPause() {
    return false;
  }

  @NotNull
  @Override
  public AnAction[] createConsoleActions() {
    return AnAction.EMPTY_ARRAY;
  }

  @Override
  public void allowHeavyFilters() { }

  @Override
  public JComponent getComponent() {
    return myComponentsSplitter;
  }

  @Override
  public JComponent getPreferredFocusableComponent() {
    return myRootPanel;
  }

  @Override
  public void dispose() { }

  @VisibleForTesting
  public AndroidTestResultsTableView getTableForTesting() {
    return myTable;
  }

  @VisibleForTesting
  public AndroidTestSuiteDetailsView getDetailsViewForTesting() {
    return myDetailsView;
  }

  public final class MyItemSeparator extends JComponent {
    @Override
    public Dimension getPreferredSize() {
      int gap = JBUIScale.scale(2);
      int center = JBUIScale.scale(3);
      int width = gap * 2 + center;
      int height = JBUIScale.scale(24);

      return new JBDimension(width, height, /*preScaled=*/true);
    }

    @Override
    protected void paintComponent(final Graphics g) {
      if (getParent() == null) return;

      int gap = JBUIScale.scale(2);
      int center = JBUIScale.scale(3);

      g.setColor(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground());
      int y2 = myStatusPanel.getHeight() - gap * 2;
      LinePainter2D.paint((Graphics2D)g, center, gap, center, y2);
    }
  }
}
