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
package com.android.tools.idea.gradle.structure.configurables;

import com.android.tools.idea.gradle.structure.configurables.editor.dependencies.DependenciesEditor;
import com.android.tools.idea.gradle.structure.configurables.model.ModuleMergedModel;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectStructureElementConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DependenciesConfigurable extends ProjectStructureElementConfigurable<ModuleMergedModel>
  implements SearchableConfigurable, Place.Navigator {
  @NotNull private final ModuleMergedModel myModel;
  @NotNull private final DependenciesEditor myEditor;

  private String myDisplayName;

  public DependenciesConfigurable(@NotNull ModuleMergedModel model) {
    myModel = model;
    myDisplayName = model.getModuleName();
    myEditor = new DependenciesEditor(model);
  }

  @Override
  public ActionCallback navigateTo(@Nullable Place place, boolean requestFocus) {
    return ActionCallback.DONE;
  }

  @Override
  public void queryPlace(@NotNull Place place) {
  }

  @Override
  @NotNull
  public String getId() {
    return "module.dependencies" + myDisplayName;
  }

  @Override
  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }

  @Override
  @Nls
  public String getDisplayName() {
    return myDisplayName;
  }

  @Override
  @Nullable
  public String getHelpTopic() {
    return null;
  }

  @Override
  public void setDisplayName(String name) {
    myDisplayName = name;
  }

  @Override
  public ModuleMergedModel getEditableObject() {
    return myModel;
  }

  @Override
  public String getBannerSlogan() {
    return "Module '" + myDisplayName + "'";
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public JComponent createOptionsPanel() {
    return myEditor.createComponent();
  }

  @Override
  public void apply() throws ConfigurationException {
  }

  @Override
  public void reset() {
  }

  @Override
  public void disposeUIResources() {}

  @Nullable
  @Override
  public ProjectStructureElement getProjectStructureElement() {
    return null;
  }

  @Override
  @Nullable
  public Icon getIcon(boolean expanded) {
    return myModel.getIcon();
  }

  @Override
  public void setHistory(History history) {
  }
}
