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
package com.android.tools.idea.flags;

import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.rendering.RenderSettings;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.TitledSeparator;
import java.util.Hashtable;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class ExperimentalSettingsConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  @NotNull private final GradleExperimentalSettings mySettings;
  @NotNull private final RenderSettings myRenderSettings;

  private JPanel myPanel;
  private JCheckBox myUseL2DependenciesCheckBox;
  private JCheckBox myUseSingleVariantSyncCheckbox;
  private JSlider myLayoutEditorQualitySlider;
  private JCheckBox myNewPsdCheckbox;
  private TitledSeparator myNewPsdSeparator;

  @SuppressWarnings("unused") // called by IDE
  public ExperimentalSettingsConfigurable(@NotNull Project project) {
    this(GradleExperimentalSettings.getInstance(), RenderSettings.getProjectSettings(project));
  }

  @VisibleForTesting
  ExperimentalSettingsConfigurable(@NotNull GradleExperimentalSettings settings,
                                   @NotNull RenderSettings renderSettings) {
    mySettings = settings;
    myRenderSettings = renderSettings;

    // TODO make visible once Gradle Sync switches to L2 dependencies
    myUseL2DependenciesCheckBox.setVisible(false);

    Hashtable qualityLabels = new Hashtable();
    qualityLabels.put(new Integer(0), new JLabel("Fastest"));
    qualityLabels.put(new Integer(100), new JLabel("Slowest"));
    myLayoutEditorQualitySlider.setLabelTable(qualityLabels);
    myLayoutEditorQualitySlider.setPaintLabels(true);
    myLayoutEditorQualitySlider.setPaintTicks(true);
    myLayoutEditorQualitySlider.setMajorTickSpacing(25);
    myNewPsdSeparator.setVisible(StudioFlags.NEW_PSD_ENABLED.get());
    myNewPsdCheckbox.setVisible(StudioFlags.NEW_PSD_ENABLED.get());

    reset();
  }

  @Override
  @NotNull
  public String getId() {
    return "gradle.experimental";
  }

  @Override
  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }

  @Override
  @Nls
  public String getDisplayName() {
    return "Experimental";
  }

  @Override
  @Nullable
  public String getHelpTopic() {
    return null;
  }

  @Override
  @NotNull
  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public boolean isModified() {
    return (mySettings.USE_L2_DEPENDENCIES_ON_SYNC != isUseL2DependenciesInSync() ||
            mySettings.USE_SINGLE_VARIANT_SYNC != isUseSingleVariantSync() ||
            (int)(myRenderSettings.getQuality() * 100) != getQualitySetting() ||
            mySettings.USE_NEW_PSD != isUseNewPsd());
  }

  private int getQualitySetting() {
    return myLayoutEditorQualitySlider.getValue();
  }

  @Override
  public void apply() throws ConfigurationException {
    mySettings.USE_L2_DEPENDENCIES_ON_SYNC = isUseL2DependenciesInSync();
    mySettings.USE_SINGLE_VARIANT_SYNC = isUseSingleVariantSync();

    myRenderSettings.setQuality(getQualitySetting() / 100f);
    mySettings.USE_NEW_PSD = isUseNewPsd();
  }

  @VisibleForTesting
  boolean isUseL2DependenciesInSync() {
    return myUseL2DependenciesCheckBox.isSelected();
  }

  @TestOnly
  void setUseL2DependenciesInSync(boolean value) {
    myUseL2DependenciesCheckBox.setSelected(value);
  }

  boolean isUseSingleVariantSync() {
    return myUseSingleVariantSyncCheckbox.isSelected();
  }

  @TestOnly
  void setUseSingleVariantSync(boolean value) {
    myUseSingleVariantSyncCheckbox.setSelected(value);
  }

  boolean isUseNewPsd() {
    return myNewPsdCheckbox.isSelected();
  }

  @TestOnly
  void setUseNewPsd(boolean value) {
    myNewPsdCheckbox.setSelected(value);
  }

  @Override
  public void reset() {
    myUseL2DependenciesCheckBox.setSelected(mySettings.USE_L2_DEPENDENCIES_ON_SYNC);
    myUseSingleVariantSyncCheckbox.setSelected(mySettings.USE_SINGLE_VARIANT_SYNC);
    myLayoutEditorQualitySlider.setValue((int)(myRenderSettings.getQuality() * 100));
    myNewPsdCheckbox.setSelected(mySettings.USE_NEW_PSD);
  }
}
