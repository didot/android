/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.logcat;

import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.actions.BrowserHelpAction;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.ddms.actions.ScreenRecorderAction;
import com.android.tools.idea.ddms.actions.ScreenshotAction;
import com.android.tools.idea.ddms.actions.TerminateVMAction;
import com.intellij.diagnostic.logging.LogConsoleBase;
import com.intellij.diagnostic.logging.LogFormatter;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

import static javax.swing.BoxLayout.X_AXIS;

/**
 * A UI panel which wraps a console that prints output from Android's logging system.
 */
public class AndroidLogcatView implements Disposable {
  public static final Key<AndroidLogcatView> ANDROID_LOGCAT_VIEW_KEY = Key.create("ANDROID_LOGCAT_VIEW_KEY");

  static final String SELECTED_APP_FILTER = AndroidBundle.message("android.logcat.filters.selected");
  static final String NO_FILTERS = AndroidBundle.message("android.logcat.filters.none");
  static final String EDIT_FILTER_CONFIGURATION = AndroidBundle.message("android.logcat.filters.edit");

  private final Project myProject;
  private final DeviceContext myDeviceContext;
  private final String myToolWindowId;
  private final boolean myHideMonitors;

  private JPanel myPanel;

  // TODO Use a more specific type parameter
  private DefaultComboBoxModel<Object> myFilterComboBoxModel;

  private volatile IDevice myDevice;
  private final AndroidLogConsole myLogConsole;
  private final FormattedLogcatReceiver myLogcatReceiver;
  private final AndroidLogFilterModel myLogFilterModel;

  /**
   * A default filter which will always let everything through.
   */
  @NotNull
  private final AndroidLogcatFilter myNoFilter;

  /**
   * Called internally when the device may have changed, or been significantly altered.
   *
   * @param forceReconnect Forces the logcat connection to restart even if the device has not changed.
   */
  private void notifyDeviceUpdated(final boolean forceReconnect) {
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      if (myProject.isDisposed()) {
        return;
      }
      if (forceReconnect) {
        if (myDevice != null) {
          AndroidLogcatService.getInstance().removeListener(myDevice, myLogcatReceiver);
        }
        myDevice = null;
      }
      updateLogConsole();
    });
  }

  @NotNull
  public final Project getProject() {
    return myProject;
  }

  @NotNull
  public final LogConsoleBase getLogConsole() {
    return myLogConsole;
  }

  /**
   * Logcat view with device obtained from {@link DeviceContext}
   */
  public AndroidLogcatView(@NotNull final Project project,
                           @NotNull DeviceContext deviceContext,
                           @NotNull String toolWindowId,
                           boolean hideMonitors) {
    myDeviceContext = deviceContext;
    myProject = project;
    myToolWindowId = toolWindowId;
    myHideMonitors = hideMonitors;

    Disposer.register(myProject, this);

    myLogFilterModel =
      new AndroidLogFilterModel() {

        @NotNull
        private AndroidLogcatPreferences getPreferences() {
          return AndroidLogcatPreferences.getInstance(project);
        }

        @Override
        protected void saveLogLevel(String logLevelName) {
          getPreferences().TOOL_WINDOW_LOG_LEVEL = logLevelName;
        }

        @Override
        public String getSelectedLogLevelName() {
          return getPreferences().TOOL_WINDOW_LOG_LEVEL;
        }

        @Override
        protected void saveConfiguredFilterName(String filterName) {
          getPreferences().TOOL_WINDOW_CONFIGURED_FILTER = filterName;
        }
      };

    AndroidLogcatFormatter logFormatter = new AndroidLogcatFormatter(AndroidLogcatPreferences.getInstance(project));
    myLogConsole = new AndroidLogConsole(project, myLogFilterModel, logFormatter);
    myLogcatReceiver = new FormattedLogcatReceiver() {
      @Override
      protected void receiveFormattedLogLine(@NotNull String line) {
        myLogConsole.addLogLine(line);
      }

      @Override
      public void onCleared() {
        myLogFilterModel.beginRejectingOldMessages();
        // We check for null, because myLogConsole.clear() depends on myLogConsole.getConsole() not being null
        if (myLogConsole.getConsole() != null) {
          myLogConsole.clear();
        }
      }
    };

    DeviceContext.DeviceSelectionListener deviceSelectionListener =
      new DeviceContext.DeviceSelectionListener() {
        @Override
        public void deviceSelected(@Nullable IDevice device) {
          notifyDeviceUpdated(false);
        }

        @Override
        public void deviceChanged(@NotNull IDevice device, int changeMask) {
          if (device == myDevice && ((changeMask & IDevice.CHANGE_STATE) == IDevice.CHANGE_STATE)) {
            notifyDeviceUpdated(true);
          }
        }

        @Override
        public void clientSelected(@Nullable final Client c) {
          if (myFilterComboBoxModel == null) {
            return;
          }

          AndroidLogcatFilter selected = (AndroidLogcatFilter)myFilterComboBoxModel.getSelectedItem();
          updateDefaultFilters(c != null ? c.getClientData() : null);

          // Attempt to preserve selection as best we can. Often we don't have to do anything,
          // but it's possible an old filter was replaced with an updated version - so, new
          // instance, but the same name.
          if (selected != null && myFilterComboBoxModel.getSelectedItem() != selected) {
            selectFilterByName(selected.getName());
          }
        }
      };
    deviceContext.addListener(deviceSelectionListener, this);

    myNoFilter = new DefaultAndroidLogcatFilter.Builder(NO_FILTERS).build();

    JComponent consoleComponent = myLogConsole.getComponent();

    final ConsoleView console = myLogConsole.getConsole();
    if (console != null) {
      final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("LogcatToolbar",
                                                                                    myLogConsole.getOrCreateActions(), false);
      toolbar.setTargetComponent(console.getComponent());
      final JComponent tbComp1 = toolbar.getComponent();
      myPanel.add(tbComp1, BorderLayout.WEST);
    }

    myPanel.add(consoleComponent, BorderLayout.CENTER);
    Disposer.register(this, myLogConsole);

    updateLogConsole();
  }

  @NotNull
  public final JPanel createSearchComponent() {
    final JPanel panel = new JPanel();
    panel.add(createEditFiltersComboBox());

    final JPanel searchComponent = new JPanel();
    searchComponent.setLayout(new BoxLayout(searchComponent, X_AXIS));
    searchComponent.add(myLogConsole.getSearchComponent());
    searchComponent.add(panel);

    return searchComponent;
  }

  @NotNull
  public Component createEditFiltersComboBox() {
    // TODO Use a more specific type parameter
    final ComboBox<Object> editFiltersCombo = new ComboBox<>();
    myFilterComboBoxModel = new DefaultComboBoxModel<>();
    myFilterComboBoxModel.addElement(myNoFilter);
    myFilterComboBoxModel.addElement(EDIT_FILTER_CONFIGURATION);

    updateDefaultFilters(null);
    updateUserFilters();
    String selectName = AndroidLogcatPreferences.getInstance(myProject).TOOL_WINDOW_CONFIGURED_FILTER;
    if (StringUtil.isEmpty(selectName)) {
      selectName = myDeviceContext != null ? SELECTED_APP_FILTER : NO_FILTERS;
    }
    selectFilterByName(selectName);

    editFiltersCombo.setModel(myFilterComboBoxModel);
    applySelectedFilter();
    // note: the listener is added after the initial call to populate the combo
    // boxes in the above call to updateConfiguredFilters
    editFiltersCombo.addItemListener(new ItemListener() {
      @Nullable private AndroidLogcatFilter myLastSelected;

      @Override
      public void itemStateChanged(ItemEvent e) {
        Object item = e.getItem();
        if (e.getStateChange() == ItemEvent.DESELECTED) {
          if (item instanceof AndroidLogcatFilter) {
            myLastSelected = (AndroidLogcatFilter)item;
          }
        }
        else if (e.getStateChange() == ItemEvent.SELECTED) {

          if (item instanceof AndroidLogcatFilter) {
            applySelectedFilter();
          }
          else {
            assert EDIT_FILTER_CONFIGURATION.equals(item);
            final EditLogFilterDialog dialog =
              new EditLogFilterDialog(AndroidLogcatView.this, myLastSelected == null ? null : myLastSelected.getName());
            dialog.setTitle(AndroidBundle.message("android.logcat.new.filter.dialog.title"));
            if (dialog.showAndGet()) {
              final PersistentAndroidLogFilters.FilterData filterData = dialog.getActiveFilter();
              updateUserFilters();
              if (filterData != null) {
                selectFilterByName(filterData.getName());
              }
            }
            else {
              editFiltersCombo.setSelectedItem(myLastSelected);
            }
          }
        }
      }
    });

    editFiltersCombo.setRenderer(new ColoredListCellRenderer<Object>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof AndroidLogcatFilter) {
          setBorder(null);
          append(((AndroidLogcatFilter)value).getName());
        }
        else {
          setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
          append(value.toString());
        }
      }
    });

    return editFiltersCombo;
  }

  private boolean isActive() {
    ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(myToolWindowId);
    return window.isVisible();
  }

  public final void activate() {
    if (isActive()) {
      updateLogConsole();
    }
    if (myLogConsole != null) {
      myLogConsole.activate();
    }
  }

  private void updateLogConsole() {
    IDevice device = getSelectedDevice();
    if (myDevice != device) {
      AndroidLogcatService androidLogcatService = AndroidLogcatService.getInstance();
      if (myDevice != null) {
        androidLogcatService.removeListener(myDevice, myLogcatReceiver);
      }
      // We check for null, because myLogConsole.clear() depends on myLogConsole.getConsole() not being null
      if (myLogConsole.getConsole() != null) {
        myLogConsole.clear();
      }
      myLogFilterModel.processingStarted();
      myDevice = device;
      androidLogcatService.addListener(myDevice, myLogcatReceiver, true);
    }
  }

  @Nullable
  private IDevice getSelectedDevice() {
    if (myDeviceContext != null) {
      return myDeviceContext.getSelectedDevice();
    }
    else {
      return null;
    }
  }


  private void applySelectedFilter() {
    final Object filter = myFilterComboBoxModel.getSelectedItem();
    if (filter instanceof AndroidLogcatFilter) {
      ProgressManager.getInstance().run(new Task.Backgroundable(myProject, "Applying Filter...") {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          myLogFilterModel.updateLogcatFilter((AndroidLogcatFilter)filter);
        }
      });
    }
  }

  /**
   * Update the list of filters which are provided by default (selected app and filters provided
   * by plugins). These show up in the top half of the filter pulldown.
   */
  private void updateDefaultFilters(@Nullable ClientData client) {
    int noFilterIndex = myFilterComboBoxModel.getIndexOf(myNoFilter);
    for (int i = 0; i < noFilterIndex; i++) {
      myFilterComboBoxModel.removeElementAt(0);
    }

    int insertIndex = 0;

    DefaultAndroidLogcatFilter.Builder selectedAppFilterBuilder = new DefaultAndroidLogcatFilter.Builder(SELECTED_APP_FILTER);
    if (client != null) {
      selectedAppFilterBuilder.setPid(client.getPid());
    }
    // Even if "client" is null, create a dummy "Selected app" filter as a placeholder which will
    // be replaced when a client is eventually created.
    myFilterComboBoxModel.insertElementAt(selectedAppFilterBuilder.build(), insertIndex++);

    for (LogcatFilterProvider filterProvider : LogcatFilterProvider.EP_NAME.getExtensions()) {
      AndroidLogcatFilter filter = filterProvider.getFilter(client);
      myFilterComboBoxModel.insertElementAt(filter, insertIndex++);
    }
  }


  /**
   * Update the list of filters which have been created by the user. These show up in the bottom
   * half of the filter pulldown.
   */
  private void updateUserFilters() {

    assert myFilterComboBoxModel.getIndexOf(EDIT_FILTER_CONFIGURATION) >= 0;

    int userFiltersStartIndex = myFilterComboBoxModel.getIndexOf(EDIT_FILTER_CONFIGURATION) + 1;
    while (myFilterComboBoxModel.getSize() > userFiltersStartIndex) {
      myFilterComboBoxModel.removeElementAt(userFiltersStartIndex);
    }

    final List<PersistentAndroidLogFilters.FilterData> filters = PersistentAndroidLogFilters.getInstance(myProject).getFilters();
    for (PersistentAndroidLogFilters.FilterData filter : filters) {
      final String name = filter.getName();
      assert name != null; // The UI that creates filters should ensure a name was created

      AndroidLogcatFilter compiled = DefaultAndroidLogcatFilter.compile(filter, name);
      myFilterComboBoxModel.addElement(compiled);
    }
  }

  private void selectFilterByName(String name) {
    for (int i = 0; i < myFilterComboBoxModel.getSize(); i++) {
      Object element = myFilterComboBoxModel.getElementAt(i);
      if (element instanceof AndroidLogcatFilter) {
        if (((AndroidLogcatFilter)element).getName().equals(name)) {
          myFilterComboBoxModel.setSelectedItem(element);
          break;
        }
      }
    }
  }

  @NotNull
  public final JPanel getContentPanel() {
    return myPanel;
  }

  @Override
  public final void dispose() {
    if (myDevice != null) {
      AndroidLogcatService.getInstance().removeListener(myDevice, myLogcatReceiver);
    }
  }

  private final class MyRestartAction extends AnAction {
    public MyRestartAction() {
      super(AndroidBundle.message("android.restart.logcat.action.text"), AndroidBundle.message("android.restart.logcat.action.description"),
            AllIcons.Actions.Restart);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      notifyDeviceUpdated(true);
    }
  }

  private final class MyConfigureLogcatHeaderAction extends AnAction {
    public MyConfigureLogcatHeaderAction() {
      super(AndroidBundle.message("android.configure.logcat.header.text"),
            AndroidBundle.message("android.configure.logcat.header.description"), AllIcons.General.GearPlain);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      ConfigureLogcatFormatDialog dialog = new ConfigureLogcatFormatDialog(myProject);
      if (dialog.showAndGet()) {
        myLogConsole.refresh();
      }
    }
  }

  public final class AndroidLogConsole extends LogConsoleBase {
    private final RegexFilterComponent myRegexFilterComponent = new RegexFilterComponent("LOG_FILTER_HISTORY", 5);
    private final AndroidLogcatPreferences myPreferences;

    public AndroidLogConsole(Project project, AndroidLogFilterModel logFilterModel, LogFormatter logFormatter) {
      super(project, null, "", false, logFilterModel, GlobalSearchScope.allScope(project), logFormatter);
      ConsoleView console = getConsole();
      if (console instanceof ConsoleViewImpl) {
        ConsoleViewImpl c = ((ConsoleViewImpl)console);
        c.addCustomConsoleAction(new Separator());
        c.addCustomConsoleAction(new MyRestartAction());
        c.addCustomConsoleAction(new MyConfigureLogcatHeaderAction());
        if (myHideMonitors) {
          // TODO: Decide if these should be part of the profiler window
          c.addCustomConsoleAction(new Separator());
          c.addCustomConsoleAction(new ScreenshotAction(project, myDeviceContext));
          c.addCustomConsoleAction(new ScreenRecorderAction(project, myDeviceContext));
          c.addCustomConsoleAction(new Separator());
          c.addCustomConsoleAction(new TerminateVMAction(myDeviceContext));
        }
        c.addCustomConsoleAction(new Separator());
        c.addCustomConsoleAction(new BrowserHelpAction("logcat", "http://developer.android.com/r/studio-ui/am-logcat.html"));
      }
      myPreferences = AndroidLogcatPreferences.getInstance(project);
      myRegexFilterComponent.setFilter(myPreferences.TOOL_WINDOW_CUSTOM_FILTER);
      myRegexFilterComponent.setIsRegex(myPreferences.TOOL_WINDOW_REGEXP_FILTER);
      myRegexFilterComponent.addRegexListener(filter -> {
        myPreferences.TOOL_WINDOW_CUSTOM_FILTER = filter.getFilter();
        myPreferences.TOOL_WINDOW_REGEXP_FILTER = filter.isRegex();
        myLogFilterModel.updateCustomPattern(filter.getPattern());
      });
    }

    @Override
    public boolean isActive() {
      return AndroidLogcatView.this.isActive();
    }

    public void clearLogcat() {
      if (getSelectedDevice() != null) {
        AndroidLogcatService.getInstance().clearLogcat(getSelectedDevice(), myProject);
      }
    }

    @NotNull
    public Component getLogFilterComboBox() {
      Container component = getSearchComponent();
      assert component != null;

      return component.getComponent(0);
    }

    @NotNull
    @Override
    public Component getTextFilterComponent() {
      return myRegexFilterComponent;
    }

    public void addLogLine(@NotNull String line) {
      super.addMessage(line);
    }

    /**
     * Clear the current logs and replay all old messages. This is useful to do if the display
     * format of the logs have changed, for example.
     */
    public void refresh() {
      // Even if we haven't changed any filter, calling this method quickly refreshes the log as a
      // side effect.
      onTextFilterChange();
    }
  }
}
