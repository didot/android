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
package com.android.tools.idea.npw.dynamicapp;

import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.tools.idea.wizard.model.ModelWizardStep;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class ConfigureModuleDownloadOptionsStep extends ModelWizardStep<DynamicFeatureModel> {
  private JPanel myRootPanel;

  public ConfigureModuleDownloadOptionsStep(@NotNull DynamicFeatureModel model) {
    super(model, message("android.wizard.module.new.dynamic.download.options"));

  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }
}
