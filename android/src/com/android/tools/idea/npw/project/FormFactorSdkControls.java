/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npw.project;

import com.android.tools.idea.npw.ChooseApiLevelDialog;
import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.module.FormFactorApiComboBox;
import com.android.tools.idea.npw.platform.AndroidVersionsInfo;
import com.android.tools.idea.stats.DistributionService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.AsyncProcessIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.List;
import java.util.Locale;

import static com.android.tools.idea.npw.FormFactor.MOBILE;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static org.jetbrains.android.util.AndroidBundle.message;
import static com.android.tools.idea.instantapp.InstantApps.getAiaSdkLocation;

/**
 * Collection of controls for selecting whether a form factor should be selected, what the minimum api version should be, and
 * optionally some other info.
 */
final class FormFactorSdkControls {
  private final Disposable myDisposable;
  private final FormFactor myFormFactor;

  private JBLabel myLabel;
  private FormFactorApiComboBox myMinSdkCombobox;
  private JBLabel myHelpMeChooseLabel2;
  private HyperlinkLabel myHelpMeChooseLink;
  private JPanel myRootPanel;
  private JLabel myNotAvailableLabel;
  private JPanel myStatsPanel;
  private JPanel myLoadingStatsPanel;
  private JBLabel myStatsLoadFailedLabel;
  private JBLabel myHelpMeChooseLabel1;
  private JCheckBox myInclusionCheckBox;
  private JCheckBox myInstantAppCheckbox;

  /**
   * Creates a new FormFactorSdkControls.
   * Before displaying, you must call {@link #init} to populate content and set up listeners.
   *
   * @param disposable The parent Disposable for this component.
   * @param formFactor The FormFactor these controls govern.
   */
  public FormFactorSdkControls(@NotNull  Disposable disposable, @NotNull FormFactor formFactor) {
    myDisposable = disposable;
    myFormFactor = formFactor;
    myInclusionCheckBox.setText(formFactor.toString());
    myHelpMeChooseLabel2.setText(getApiHelpText(0, ""));
    myHelpMeChooseLink.setHyperlinkText(message("android.wizard.module.help.choose"));
    // Only show SDK selector for base form factors.
    if (myFormFactor.baseFormFactor != null) {
      myLabel.setVisible(false);
      myMinSdkCombobox.setVisible(false);
    }

    if (!myFormFactor.equals(MOBILE)) {
      myHelpMeChooseLabel1.setVisible(false);
      myStatsPanel.setVisible(false);
    }

    myMinSdkCombobox.setName(myFormFactor.id + ".minSdk");
    myInstantAppCheckbox.setName(myFormFactor.id + ".instantApp");
    myInstantAppCheckbox.setVisible((myFormFactor.equals(MOBILE) && isNotEmpty(getAiaSdkLocation())));
    myStatsLoadFailedLabel.setForeground(JBColor.GRAY);
  }

  /**
   * @param state The ScopedStateStore in which to store our selections
   * @param downloadSuccess A Runnable that will be run if any valid items were found for this form factor.
   * @param downloadFailed A Runnable that will be run if no valid items were found for this form factor.
   */
  public void init(@NotNull List<AndroidVersionsInfo.VersionItem> items) {
    myHelpMeChooseLink.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        int minApiLevel = ((AndroidVersionsInfo.VersionItem)myMinSdkCombobox.getSelectedItem()).getApiLevel();
        ChooseApiLevelDialog chooseApiLevelDialog = new ChooseApiLevelDialog(null, minApiLevel);
        Disposer.register(myDisposable, chooseApiLevelDialog.getDisposable());

        if (chooseApiLevelDialog.showAndGet()) {
          int selectedApiLevel = chooseApiLevelDialog.getSelectedApiLevel();
          for (AndroidVersionsInfo.VersionItem item : items) {
            if (item.getApiLevel() == selectedApiLevel) {
              myMinSdkCombobox.setSelectedItem(item);
              break;
            }
          }
        }
      }
    });

    if (myStatsPanel.isVisible()) {
      myMinSdkCombobox.addItemListener(itemEvent -> {
        if (itemEvent.getStateChange() == ItemEvent.SELECTED && itemEvent.getItem() != null) {
          AndroidVersionsInfo.VersionItem selectedItem = (AndroidVersionsInfo.VersionItem)itemEvent.getItem();
          String referenceString = getApiHelpText(selectedItem.getApiLevel(), selectedItem.getApiLevelStr());

          ApplicationManager.getApplication().invokeLater(() -> myHelpMeChooseLabel2.setText(referenceString));
        }
      });
    }

    myMinSdkCombobox.init(myFormFactor, items);

    boolean itemsFound = !items.isEmpty();
    myInclusionCheckBox.setEnabled(itemsFound);
    myLabel.setEnabled(itemsFound);
    myMinSdkCombobox.setEnabled(itemsFound);
    myNotAvailableLabel.setVisible(!itemsFound);

    if (myStatsPanel.isVisible()) {
      DistributionService.getInstance().refresh(
        () -> ApplicationManager.getApplication().invokeLater(() -> {
          ((CardLayout)myStatsPanel.getLayout()).show(myStatsPanel, "stats");
        }),
        () -> ApplicationManager.getApplication().invokeLater(() -> {
          ((CardLayout)myStatsPanel.getLayout()).show(myStatsPanel, "stats");
          myStatsLoadFailedLabel.setVisible(true);
        }));
    }
  }

  public JComponent getComponent() {
    return myRootPanel;
  }

  @NotNull
  JComboBox<AndroidVersionsInfo.VersionItem> getMinSdkCombobox() {
    return myMinSdkCombobox;
  }

  @NotNull
  JCheckBox getInclusionCheckBox() {
    return myInclusionCheckBox;
  }

  @NotNull
  JCheckBox getInstantAppCheckbox() {
    return myInstantAppCheckbox;
  }

  private static String getApiHelpText(int selectedApi, @NotNull String selectedApiName) {
    double percentage = DistributionService.getInstance().getSupportedDistributionForApiLevel(selectedApi) * 100;
    String percentageStr =  percentage < 1 ? "&lt; 1%" : String.format(Locale.getDefault(), "approximately <b>%.1f%%</b>", percentage);
    return String.format(Locale.getDefault(), "<html>By targeting API %1$s and later, your app will run on %2$s of the devices<br>" +
                         "that are active on the Google Play Store.</html>", selectedApiName, percentageStr);
  }

  private void createUIComponents() {
    myLoadingStatsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    AsyncProcessIcon refreshIcon = new AsyncProcessIcon(message("android.wizard.module.help.loading"));
    JLabel refreshingLabel = new JLabel(message("android.wizard.module.help.refreshing"));
    refreshingLabel.setForeground(JBColor.GRAY);
    myLoadingStatsPanel.add(refreshIcon);
    myLoadingStatsPanel.add(refreshingLabel);
  }
}
