/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.visual;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.annotations.Transient;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

@State(name = "VisualizationTool", storages = @Storage("visualizationTool.xml"))
public class VisualizationToolSettings implements PersistentStateComponent<VisualizationToolSettings.MyState> {
  private static final ConfigurationSet DEFAULT_CONFIGURATION_SET = ConfigurationSet.PIXEL_DEVICES;

  private GlobalState myGlobalState = new GlobalState();

  public static VisualizationToolSettings getInstance() {
    return ApplicationManager.getApplication().getService(VisualizationToolSettings.class);
  }

  @NotNull
  public GlobalState getGlobalState() {
    return myGlobalState;
  }

  @Override
  public MyState getState() {
    final MyState state = new MyState();
    state.setState(myGlobalState);
    return state;
  }

  @Override
  public void loadState(@NotNull MyState state) {
    myGlobalState = state.getState();
  }

  public static class MyState {
    private GlobalState myGlobalState = new GlobalState();

    public GlobalState getState() {
      return myGlobalState;
    }

    public void setState(GlobalState state) {
      myGlobalState = state;
    }
  }

  public static class GlobalState {
    private boolean myFirstTimeOpen = true;
    private boolean myVisible = false;
    private double myScale = 0.25;
    private boolean myShowDecoration = false;
    @NotNull private String myConfigurationSetName = ConfigurationSet.PIXEL_DEVICES.name();
    @NotNull private List<CustomConfigurationAttribute> myCustomConfigurationAttributes = new ArrayList<>();

    public boolean isFirstTimeOpen() {
      return myFirstTimeOpen;
    }

    public void setFirstTimeOpen(boolean firstTimeOpen) {
      myFirstTimeOpen = firstTimeOpen;
    }

    public boolean isVisible() {
      return myVisible;
    }

    public void setVisible(boolean visible) {
      myVisible = visible;
    }

    public double getScale() {
      return myScale;
    }

    public void setScale(double scale) {
      myScale = scale;
    }

    public boolean getShowDecoration() {
      return myShowDecoration;
    }

    public void setShowDecoration(boolean showDecoration) {
      myShowDecoration = showDecoration;
    }

    /**
     * Get the name of {@link ConfigurationSet}. This function is public just because it is part of JavaBean.
     * Do not use this function; For getting {@link ConfigurationSet}, use {@link #getConfigurationSet} instead.
     *
     * Because {@link ConfigurationSet} is an enum class, once the saved {@link ConfigurationSet} is renamed or deleted, the fatal error
     * may happen due to parsing persistent state failed. Thus, use {@link #getConfigurationSet} instead which handles the exception cases.
     */
    @SuppressWarnings("unused") // Used by JavaBeans
    @NotNull
    public String getConfigurationSetName() {
      return myConfigurationSetName;
    }

    /**
     * Set the name of {@link ConfigurationSet}. This function is public just because it is part of JavaBean.
     * Do not use this function; For setting {@link ConfigurationSet}, use {@link #setConfigurationSet(ConfigurationSet)} instead.
     */
    @SuppressWarnings("unused") // Used by JavaBeans
    public void setConfigurationSetName(@NotNull String configurationSetName) {
      myConfigurationSetName = configurationSetName;
    }

    @NotNull
    public List<CustomConfigurationAttribute> getCustomConfigurationAttributes() {
      return myCustomConfigurationAttributes;
    }

    public void setCustomConfigurationAttributes(@NotNull List<CustomConfigurationAttribute> configurationStrings) {
      myCustomConfigurationAttributes = configurationStrings;
    }

    /**
     * Helper function to get {@link ConfigurationSet}. This function handles the illegal name case which happens when saved
     * {@link ConfigurationSet} is renamed or deleted.
     */
    @Transient
    @NotNull
    public ConfigurationSet getConfigurationSet() {
      try {
        ConfigurationSet set = ConfigurationSet.valueOf(myConfigurationSetName);
        if (!set.getVisible()) {
          set = DEFAULT_CONFIGURATION_SET;
          myConfigurationSetName = DEFAULT_CONFIGURATION_SET.name();
        }
        return set;
      }
      catch (IllegalArgumentException e) {
        // The saved configuration set may be renamed or deleted, use default one instead.
        myConfigurationSetName = DEFAULT_CONFIGURATION_SET.name();
        return DEFAULT_CONFIGURATION_SET;
      }
    }

    /**
     * Helper function to set {@link ConfigurationSet}.
     */
    @Transient
    public void setConfigurationSet(@NotNull ConfigurationSet configurationSet) {
      myConfigurationSetName = configurationSet.name();
    }
  }
}
