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

import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.google.common.collect.Maps;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.ui.CollectionComboBoxModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;

public class AndroidDebuggerPanel {
  private final AndroidDebuggerContext myAndroidDebuggerContext;
  private JPanel myPanel;
  private JComboBox myDebuggerType;

  private JPanel myOptionPanel;
  private JComponent myOptionComponent;
  private final Map<String, AndroidDebuggerConfigurable<AndroidDebuggerState>> myConfigurables = Maps.newHashMap();

  public AndroidDebuggerPanel(@NotNull RunConfiguration runConfiguration, @NotNull AndroidDebuggerContext androidDebuggerContext) {
    myAndroidDebuggerContext = androidDebuggerContext;

    myDebuggerType.setModel(new CollectionComboBoxModel(myAndroidDebuggerContext.getAndroidDebuggers()));
    myDebuggerType.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        AndroidDebugger androidDebugger = (AndroidDebugger)myDebuggerType.getSelectedItem();
        if (androidDebugger != null) {
          switchDebugOption(androidDebugger);
        }
      }
    });
    myDebuggerType.setRenderer(new AndroidDebugger.Renderer());

    for (AndroidDebugger androidDebugger: myAndroidDebuggerContext.getAndroidDebuggers()) {
      AndroidDebuggerConfigurable<AndroidDebuggerState> configurable = androidDebugger.createConfigurable(runConfiguration);
      configurable.resetFrom(myAndroidDebuggerContext.getAndroidDebuggerState(androidDebugger.getId()));
      myConfigurables.put(androidDebugger.getId(), configurable);
    }
  }

  public JComponent getComponent() {
    return myPanel;
  }

  private void switchDebugOption(@NotNull AndroidDebugger<AndroidDebuggerState> androidDebugger) {
    if (myOptionComponent != null) {
      myOptionPanel.remove(myOptionComponent);
      myOptionComponent = null;
    }

    AndroidDebuggerConfigurable<AndroidDebuggerState> configurable = getConfigurable(androidDebugger);
    configurable.resetFrom(myAndroidDebuggerContext.getAndroidDebuggerState(androidDebugger.getId()));

    myOptionComponent = configurable.getComponent();
    if (myOptionComponent != null) {
      myOptionPanel.add(myOptionComponent);
    }
  }

  void resetFrom(@NotNull AndroidDebuggerContext androidDebuggerContext) {
    AndroidDebugger<AndroidDebuggerState> debugOption = androidDebuggerContext.getAndroidDebugger();
    if (debugOption != null) {
      myDebuggerType.setSelectedItem(debugOption);
      switchDebugOption(debugOption);
    }
  }

  void applyTo(@NotNull AndroidDebuggerContext androidDebuggerContext) {
    AndroidDebugger<AndroidDebuggerState> androidDebugger = (AndroidDebugger)myDebuggerType.getSelectedItem();
    androidDebuggerContext.setDebuggerType(androidDebugger.getId());
    AndroidDebuggerConfigurable<AndroidDebuggerState> configurable = getConfigurable(androidDebugger);

    if (configurable != null) {
      configurable.applyTo(myAndroidDebuggerContext.getAndroidDebuggerState(androidDebugger.getId()));
    }
  }

  @Nullable
  AndroidDebuggerConfigurable<AndroidDebuggerState> getConfigurable(@NotNull AndroidDebugger<AndroidDebuggerState> androidDebugger) {
    return myConfigurables.get(androidDebugger.getId());
  }
}
