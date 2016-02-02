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
package com.android.tools.idea.gradle.structure.configurables.buildtypes;

import com.android.tools.idea.gradle.structure.configurables.BaseNamedConfigurable;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidModuleModel;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class BuildTypesConfigurable extends BaseNamedConfigurable<PsdAndroidModuleModel> {
  BuildTypesConfigurable(@NotNull PsdAndroidModuleModel moduleEditor) {
    super(moduleEditor);
  }

  @Override
  public JComponent createOptionsPanel() {
    return new JPanel();
  }

  @Override
  public void setHistory(History history) {

  }

  @Override
  public ActionCallback navigateTo(@Nullable Place place, boolean requestFocus) {
    // TODO implement.
    return ActionCallback.DONE;
  }

  @Override
  public void queryPlace(@NotNull Place place) {

  }

  @Override
  @NotNull
  public String getId() {
    return "module.buildtypes" + getDisplayName();
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {

  }

  @Override
  public void reset() {

  }

  @Override
  public void disposeUIResources() {

  }
}
