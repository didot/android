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
package org.jetbrains.android.run;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.repository.descriptors.IdDisplay;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.run.*;
import com.google.common.base.Predicate;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ThreeState;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.run.testing.AndroidTestRunConfiguration;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * @author yole
 */
public class AndroidRunConfigurationEditor<T extends AndroidRunConfigurationBase> extends SettingsEditor<T> implements PanelWithAnchor {
  private final Project myProject;

  private JPanel myPanel;
  private JComboBox myModulesComboBox;
  private JPanel myConfigurationSpecificPanel;
  private JCheckBox myWipeUserDataCheckBox;
  private JComboBox myNetworkSpeedCombo;
  private JComboBox myNetworkLatencyCombo;
  private JCheckBox myDisableBootAnimationCombo;
  private JCheckBox myClearLogCheckBox;
  private JBLabel myModuleJBLabel;
  private JRadioButton myShowChooserRadioButton;
  private JRadioButton myEmulatorRadioButton;
  private JRadioButton myUsbDeviceRadioButton;
  private ComboboxWithBrowseButton myAvdComboComponent;
  private JBLabel myMinSdkInfoMessageLabel;
  private JBCheckBox myUseAdditionalCommandLineOptionsCheckBox;
  private RawCommandLineEditor myCommandLineField;
  private JCheckBox myShowLogcatCheckBox;
  private JCheckBox myFilterLogcatCheckBox;
  private JCheckBox myUseLastSelectedDeviceCheckBox;
  private JRadioButton myRunTestsInGoogleCloudRadioButton;

  private CloudMatrixConfigurationComboBox myCloudConfigurationCombo;
  private JBLabel myCloudProjectLabel;
  private JBLabel myMatrixConfigLabel;
  private CloudProjectIdLabel myCloudProjectIdLabel;
  private ActionButton myCloudProjectIdUpdateButton;
  @Nullable private final CloudTestConfigurationProvider myCloudConfigurationFactory;

  private AvdComboBox myAvdCombo;
  private String incorrectPreferredAvd;
  private JComponent anchor;

  @NonNls private final static String[] NETWORK_SPEEDS = new String[]{"Full", "GSM", "HSCSD", "GPRS", "EDGE", "UMTS", "HSPDA"};
  @NonNls private final static String[] NETWORK_LATENCIES = new String[]{"None", "GPRS", "EDGE", "UMTS"};

  private final ConfigurationModuleSelector myModuleSelector;
  private ConfigurationSpecificEditor<T> myConfigurationSpecificEditor;

  public void setConfigurationSpecificEditor(ConfigurationSpecificEditor<T> configurationSpecificEditor) {
    myConfigurationSpecificEditor = configurationSpecificEditor;
    myConfigurationSpecificPanel.add(configurationSpecificEditor.getComponent());
    setAnchor(myConfigurationSpecificEditor.getAnchor());
    myShowLogcatCheckBox.setVisible(configurationSpecificEditor instanceof ApplicationRunParameters);
    myFilterLogcatCheckBox.setVisible(configurationSpecificEditor instanceof ApplicationRunParameters);
  }

  public AndroidRunConfigurationEditor(final Project project, final Predicate<AndroidFacet> libraryProjectValidator) {
    myProject = project;
    $$$setupUI$$$(); // Create UI components after myProject is available. Also see https://youtrack.jetbrains.com/issue/IDEA-67765

    myCloudConfigurationFactory = CloudTestConfigurationProvider.getCloudTestingProvider();

    myCommandLineField.setDialogCaption("Emulator Additional Command Line Options");

    myModuleSelector = new ConfigurationModuleSelector(project, myModulesComboBox) {
      @Override
      public boolean isModuleAccepted(Module module) {
        if (module == null || !super.isModuleAccepted(module)) {
          return false;
        }

        final AndroidFacet facet = AndroidFacet.getInstance(module);
        if (facet == null) {
          return false;
        }

        return !facet.isLibraryProject() || libraryProjectValidator.apply(facet);
      }
    };

    myAvdCombo.getComboBox().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final String warning = myEmulatorRadioButton.isSelected() ? getAvdCompatibilityWarning() : null;
        resetAvdCompatibilityWarningLabel(warning);
      }
    });
    myMinSdkInfoMessageLabel.setBorder(IdeBorderFactory.createEmptyBorder(10, 0, 0, 0));
    myMinSdkInfoMessageLabel.setIcon(AllIcons.General.BalloonWarning);

    Disposer.register(this, myAvdCombo);

    final ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateEnabled();
      }
    };
    myModulesComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        Module module = getModuleSelector().getModule();
        AndroidFacet facet = module != null ? AndroidFacet.getInstance(module) : null;
        if (facet == null) {
          updateCloudProviderEnabled(false);
        } else {
          myCloudProjectIdLabel.setFacet(facet);
          myCloudConfigurationCombo.setFacet(facet);
          updateCloudProviderEnabled(myRunTestsInGoogleCloudRadioButton.isSelected());
        }
        myAvdCombo.startUpdatingAvds(ModalityState.current());
      }
    });
    myShowChooserRadioButton.addActionListener(listener);
    myEmulatorRadioButton.addActionListener(listener);
    myUsbDeviceRadioButton.addActionListener(listener);
    myUseLastSelectedDeviceCheckBox.addActionListener(listener);
    myRunTestsInGoogleCloudRadioButton.addActionListener(listener);

    myNetworkSpeedCombo.setModel(new DefaultComboBoxModel(NETWORK_SPEEDS));
    myNetworkLatencyCombo.setModel(new DefaultComboBoxModel(NETWORK_LATENCIES));

    myUseAdditionalCommandLineOptionsCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myCommandLineField.setEnabled(myUseAdditionalCommandLineOptionsCheckBox.isSelected());
      }
    });

    updateEnabled();
  }

  private void $$$setupUI$$$() {
  }

  private void createUIComponents() {
    myAvdCombo = new AvdComboBox(myProject, true, false) {
      @Override
      public Module getModule() {
        return getModuleSelector().getModule();
      }
    };

    myAvdComboComponent = new ComboboxWithBrowseButton(myAvdCombo.getComboBox());

    myCloudProjectIdLabel = new CloudProjectIdLabel();
    AnAction action = new SelectCloudProjectAction();
    myCloudProjectIdUpdateButton =
      new ActionButton(action, new PresentationFactory().getPresentation(action), "MyPlace", new Dimension(25, 25));
    myCloudConfigurationCombo = new CloudMatrixConfigurationComboBox();
    Disposer.register(this, myCloudConfigurationCombo);
  }

  private void updateGoogleCloudVisible(AndroidRunConfigurationBase configuration) {
    boolean shouldShow = configuration instanceof AndroidTestRunConfiguration && CloudTestConfigurationProvider.SHOW_CLOUD_TESTING_OPTION;
    myRunTestsInGoogleCloudRadioButton.setVisible(shouldShow);
    myCloudConfigurationCombo.setVisible(shouldShow);
    myCloudProjectLabel.setVisible(shouldShow);
    myMatrixConfigLabel.setVisible(shouldShow);
    myCloudProjectIdLabel.setVisible(shouldShow);
    myCloudProjectIdUpdateButton.setVisible(shouldShow);
  }

  private void updateEnabled() {
    boolean emulatorSelected = myEmulatorRadioButton.isSelected();
    myAvdComboComponent.setEnabled(emulatorSelected);
    updateCloudProviderEnabled(myRunTestsInGoogleCloudRadioButton.isSelected());

    final String warning = emulatorSelected ? getAvdCompatibilityWarning() : null;
    resetAvdCompatibilityWarningLabel(warning);
  }

  private void updateCloudProviderEnabled(boolean isEnabled) {
    myCloudConfigurationCombo.setEnabled(isEnabled);
    myCloudProjectLabel.setEnabled(isEnabled);
    myMatrixConfigLabel.setEnabled(isEnabled);
    myCloudProjectIdLabel.setEnabled(isEnabled);
    myCloudProjectIdUpdateButton.setEnabled(isEnabled);
  }

  private void resetAvdCompatibilityWarningLabel(@Nullable String warning) {
    if (warning != null) {
      myMinSdkInfoMessageLabel.setVisible(true);
      myMinSdkInfoMessageLabel.setText(warning);
    }
    else {
      myMinSdkInfoMessageLabel.setVisible(false);
    }
  }

  private CloudTestConfiguration getCloudConfigurationComboSelection() {
    return (CloudTestConfiguration) myCloudConfigurationCombo.getComboBox().getSelectedItem();
  }

  public int getSelectedMatrixConfigurationId() {
    CloudTestConfiguration selection = getCloudConfigurationComboSelection();
    if (selection == null) {
      return -1;
    }
    return selection.getId();
  }

  private boolean isValidCloudSelection() {
    CloudTestConfiguration selection = getCloudConfigurationComboSelection();
    return selection != null && selection.getDeviceConfigurationCount() > 0 && myCloudProjectIdLabel.isProjectSpecified();
  }

  private String getInvalidSelectionErrorMessage() {
    CloudTestConfiguration selection = getCloudConfigurationComboSelection();
    if (selection == null) {
      return "Matrix configuration not specified";
    }
    if (selection.getDeviceConfigurationCount() < 1) {
      return "Selected matrix configuration is empty";
    }
    if (!myCloudProjectIdLabel.isProjectSpecified()) {
      return "Cloud project not specified";
    }
    return "";
  }

  @Nullable
  private String getAvdCompatibilityWarning() {
    IdDisplay selectedItem = (IdDisplay)myAvdCombo.getComboBox().getSelectedItem();

    if (selectedItem != null) {
      final String selectedAvdName = selectedItem.getId();
      final Module module = getModuleSelector().getModule();
      if (module == null) {
        return null;
      }

      final AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet == null) {
        return null;
      }

      final AvdManager avdManager = facet.getAvdManagerSilently();
      if (avdManager == null) {
        return null;
      }

      final AvdInfo avd = avdManager.getAvd(selectedAvdName, false);
      if (avd == null || avd.getTarget() == null) {
        return null;
      }

      AndroidPlatform platform = facet.getConfiguration().getAndroidPlatform();
      if (platform == null) {
        return null;
      }

      AndroidVersion minSdk = AndroidModuleInfo.get(facet).getRuntimeMinSdkVersion();
      LaunchCompatibility compatibility = LaunchCompatibility.canRunOnAvd(minSdk, platform.getTarget(), avd.getTarget());
      if (compatibility.isCompatible() == ThreeState.NO) {
        // todo: provide info about current module configuration
        return String.format("'%1$s' may be incompatible with your configuration (%2$s)", selectedAvdName,
                             StringUtil.notNullize(compatibility.getReason()));
      }
    }
    return null;
  }

  @Override
  public JComponent getAnchor() {
    return anchor;
  }

  @Override
  public void setAnchor(JComponent anchor) {
    this.anchor = anchor;
    myModuleJBLabel.setAnchor(anchor);
  }

  private static boolean containsItem(JComboBox combo, @NotNull Object item) {
    for (int i = 0, n = combo.getItemCount(); i < n; i++) {
      if (item.equals(combo.getItemAt(i))) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected void resetEditorFrom(T configuration) {
    myModuleSelector.reset(configuration);

    updateGoogleCloudVisible(configuration);

    final String avd = configuration.PREFERRED_AVD;
    if (avd != null) {
      JComboBox combo = myAvdCombo.getComboBox();
      if (containsItem(combo, avd)) {
        combo.setSelectedItem(avd);
      }
      else {
        combo.setRenderer(new ListCellRendererWrapper() {
          @Override
          public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
            if (value == null) {
              setText("<html><font color='red'>" + avd + "</font></html>");
            }
            else if (value instanceof IdDisplay) {
              setText(((IdDisplay)value).getDisplay());
            }
            else {
              setText(value.toString());
            }
          }
        });
        combo.setSelectedItem(null);
        incorrectPreferredAvd = avd;
      }
    }

    final TargetSelectionMode targetSelectionMode = configuration.getTargetSelectionMode();

    myShowChooserRadioButton.setSelected(targetSelectionMode == TargetSelectionMode.SHOW_DIALOG);
    myEmulatorRadioButton.setSelected(targetSelectionMode == TargetSelectionMode.EMULATOR);
    myUsbDeviceRadioButton.setSelected(targetSelectionMode == TargetSelectionMode.USB_DEVICE);
    myUseLastSelectedDeviceCheckBox.setSelected(configuration.USE_LAST_SELECTED_DEVICE);

    myRunTestsInGoogleCloudRadioButton.setSelected(targetSelectionMode == TargetSelectionMode.CLOUD_TEST_OPTION);
    myCloudConfigurationCombo.selectConfiguration(configuration.SELECTED_MATRIX_CONFIGURATION_ID);
    myCloudProjectIdLabel.updateCloudProjectId(configuration.SELECTED_CLOUD_PROJECT_ID);

    myAvdComboComponent.setEnabled(targetSelectionMode == TargetSelectionMode.EMULATOR);

    resetAvdCompatibilityWarningLabel(targetSelectionMode == TargetSelectionMode.EMULATOR
                                      ? getAvdCompatibilityWarning()
                                      : null);

    myUseAdditionalCommandLineOptionsCheckBox.setSelected(configuration.USE_COMMAND_LINE);
    myCommandLineField.setText(configuration.COMMAND_LINE);
    myConfigurationSpecificEditor.resetFrom(configuration);
    myWipeUserDataCheckBox.setSelected(configuration.WIPE_USER_DATA);
    myDisableBootAnimationCombo.setSelected(configuration.DISABLE_BOOT_ANIMATION);
    selectItemCaseInsensitively(myNetworkSpeedCombo, configuration.NETWORK_SPEED);
    selectItemCaseInsensitively(myNetworkLatencyCombo, configuration.NETWORK_LATENCY);
    myClearLogCheckBox.setSelected(configuration.CLEAR_LOGCAT);
    myShowLogcatCheckBox.setSelected(configuration.SHOW_LOGCAT_AUTOMATICALLY);
    myFilterLogcatCheckBox.setSelected(configuration.FILTER_LOGCAT_AUTOMATICALLY);

    updateEnabled();
  }

  private static void selectItemCaseInsensitively(@NotNull JComboBox comboBox, @Nullable String item) {
    if (item == null) {
      comboBox.setSelectedItem(null);
      return;
    }

    final ComboBoxModel model = comboBox.getModel();

    for (int i = 0, n = model.getSize(); i < n; i++) {
      final Object element = model.getElementAt(i);
      if (element instanceof String && item.equalsIgnoreCase((String)element)) {
        comboBox.setSelectedItem(element);
        return;
      }
    }
  }

  @Override
  protected void applyEditorTo(T configuration) throws ConfigurationException {
    myModuleSelector.applyTo(configuration);

    if (myShowChooserRadioButton.isSelected()) {
      configuration.setTargetSelectionMode(TargetSelectionMode.SHOW_DIALOG);
    }
    else if (myEmulatorRadioButton.isSelected()) {
      configuration.setTargetSelectionMode(TargetSelectionMode.EMULATOR);
    }
    else if (myUsbDeviceRadioButton.isSelected()) {
      configuration.setTargetSelectionMode(TargetSelectionMode.USB_DEVICE);
    }
    else if (myRunTestsInGoogleCloudRadioButton.isSelected()) {
      configuration.setTargetSelectionMode(TargetSelectionMode.CLOUD_TEST_OPTION);
    }

    configuration.SELECTED_MATRIX_CONFIGURATION_ID = getSelectedMatrixConfigurationId();
    configuration.SELECTED_CLOUD_PROJECT_ID = myCloudProjectIdLabel.getText();
    configuration.IS_VALID_CLOUD_SELECTION = isValidCloudSelection();
    configuration.INVALID_CLOUD_SELECTION_ERROR = getInvalidSelectionErrorMessage();

    configuration.USE_LAST_SELECTED_DEVICE = myUseLastSelectedDeviceCheckBox.isSelected();
    configuration.COMMAND_LINE = myCommandLineField.getText();
    configuration.USE_COMMAND_LINE = myUseAdditionalCommandLineOptionsCheckBox.isSelected();
    configuration.PREFERRED_AVD = "";
    configuration.WIPE_USER_DATA = myWipeUserDataCheckBox.isSelected();
    configuration.DISABLE_BOOT_ANIMATION = myDisableBootAnimationCombo.isSelected();
    configuration.NETWORK_SPEED = ((String)myNetworkSpeedCombo.getSelectedItem()).toLowerCase();
    configuration.NETWORK_LATENCY = ((String)myNetworkLatencyCombo.getSelectedItem()).toLowerCase();
    configuration.CLEAR_LOGCAT = myClearLogCheckBox.isSelected();
    configuration.SHOW_LOGCAT_AUTOMATICALLY = myShowLogcatCheckBox.isSelected();
    configuration.FILTER_LOGCAT_AUTOMATICALLY = myFilterLogcatCheckBox.isSelected();
    if (myAvdComboComponent.isEnabled()) {
      JComboBox combo = myAvdCombo.getComboBox();
      IdDisplay preferredAvd = (IdDisplay)combo.getSelectedItem();
      if (preferredAvd == null) {
        configuration.PREFERRED_AVD = incorrectPreferredAvd != null ? incorrectPreferredAvd : "";
      }
      else {
        configuration.PREFERRED_AVD = preferredAvd.getId();
      }
    }
    myConfigurationSpecificEditor.applyTo(configuration);
  }

  @Override
  @NotNull
  protected JComponent createEditor() {
    return myPanel;
  }

  public ConfigurationModuleSelector getModuleSelector() {
    return myModuleSelector;
  }

  private class SelectCloudProjectAction extends AnAction {
    @Override
    public void actionPerformed(AnActionEvent e) {
      if (myCloudConfigurationFactory == null) {
        return;
      }

      String selectedProjectId = myCloudConfigurationFactory.openCloudProjectConfigurationDialog(myProject, myCloudProjectIdLabel.getText());
      if (selectedProjectId != null) {
        myCloudProjectIdLabel.updateCloudProjectId(selectedProjectId);
        // Simulate a change event such that it is picked up by the editor validation mechanisms.
        for (ItemListener itemListener : myCloudConfigurationCombo.getComboBox().getItemListeners()) {
          itemListener.itemStateChanged(new ItemEvent(myCloudConfigurationCombo.getComboBox(),
                                                      ItemEvent.ITEM_STATE_CHANGED,
                                                      myCloudConfigurationCombo.getComboBox(),
                                                      ItemEvent.SELECTED));
        }
      }
    }

    @Override
    public void update(AnActionEvent event) {
      Presentation presentation = event.getPresentation();
      presentation.setIcon(AllIcons.General.Settings);
    }
  }
}
