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
package com.android.tools.idea.npw.template.components;

import com.android.tools.idea.observable.AbstractProperty;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.SimpleListCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * Provides a combobox which presents the user with a list of source sets.
 *
 * @see NamedModuleTemplate
 */
// Disable JComboBox warnings. We have no choice but to use it as we're stuck with JDK6
@SuppressWarnings({"UndesirableClassUsage", "unchecked"})
public final class ModuleTemplateComboProvider extends ComponentProvider<JComboBox> {
  @NotNull private final List<NamedModuleTemplate> myTemplates;

  public ModuleTemplateComboProvider(@NotNull List<NamedModuleTemplate> templates) {
    myTemplates = templates;
  }

  @NotNull
  @Override
  public JComboBox createComponent() {
    DefaultComboBoxModel comboBoxModel = new DefaultComboBoxModel();
    for (NamedModuleTemplate template : myTemplates) {
      comboBoxModel.addElement(template);
    }

    JComboBox<NamedModuleTemplate> moduleTemplateCombo = new ComboBox<>(comboBoxModel);
    moduleTemplateCombo.setRenderer(SimpleListCellRenderer.create("", NamedModuleTemplate::getName));
    moduleTemplateCombo.setToolTipText("<html>The source set within which to generate new project files.<br>" +
                                  "If you specify a source set that does not yet exist on disk, a folder will be created for it.</html>");
    return moduleTemplateCombo;
  }

  @Nullable
  @Override
  public AbstractProperty<?> createProperty(@NotNull JComboBox moduleTemplateCombo) {
    return new SelectedItemProperty<NamedModuleTemplate>(moduleTemplateCombo);
  }
}

