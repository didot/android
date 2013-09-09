/*
 * Copyright 2004-2005 Alexey Efimov
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
package com.android.tools.idea.structure;

import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * A project structure pane that lets you add and remove dependencies for individual modules. It is largely a wrapper for
 * {@linkplain ModuleDependenciesPanel}
 */
public class ModuleDependenciesEditor implements ModuleConfigurationEditor {
  private static final String NAME = ProjectBundle.message("modules.classpath.title");

  private ModuleDependenciesPanel myPanel;
  private final String myModulePath;
  private final Project myProject;

  public ModuleDependenciesEditor(Project project, String modulePath) {
    myModulePath = modulePath;
    myProject = project;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    myPanel = new ModuleDependenciesPanel(myProject, myModulePath);
    return myPanel;
  }

  @Override
  public boolean isModified() {
    return myPanel.isModified();
  }

  @Override
  @Nullable
  public String getHelpTopic() {
    return null;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return NAME;
  }

  @Override
  public void saveData() {
  }

  @Override
  public void moduleStateChanged() {
  }

  @Override
  public void apply() throws ConfigurationException {
    myPanel.apply();
  }

  @Override
  public void reset() {
  }

  @Override
  public void disposeUIResources() {
    myPanel = null;
  }

}
