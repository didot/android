/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.wizard;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.intellij.openapi.Disposable;
import com.intellij.ui.components.JBList;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.wizard.WizardConstants.SELECTED_MODULE_TYPE_KEY;

/**
 * This step allows the user to select which type of module they want to create.
 */
public final class ChooseModuleTypeStep extends DynamicWizardStepWithDescription {
  private final Iterable<ModuleTemplateProvider> myModuleTypesProviders;
  private JPanel myPanel;
  private ASGallery<ModuleTemplate> myFormFactorGallery;

  public ChooseModuleTypeStep(Iterable<ModuleTemplateProvider> moduleTypesProviders, @Nullable Disposable parentDisposable) {
    super(parentDisposable);
    myModuleTypesProviders = moduleTypesProviders;
    myFormFactorGallery.setBorder(BorderFactory.createLineBorder(UIUtil.getBorderColor()));
    setBodyComponent(myPanel);
  }

  @Override
  public void init() {
    super.init();
    ImmutableList.Builder<ModuleTemplate> galleryTemplates = ImmutableList.builder();
    ImmutableList.Builder<ModuleTemplate> extrasTemplates = ImmutableList.builder();
    Set<FormFactorUtils.FormFactor> formFactorSet = Sets.newHashSet();
    // "Gallery" templates are shown first, with less important templates following
    for (ModuleTemplateProvider provider : myModuleTypesProviders) {
      for (ModuleTemplate moduleTemplate : provider.getModuleTemplates()) {
        if (moduleTemplate.isGalleryModuleType()) {
          galleryTemplates.add(moduleTemplate);
        }
        else {
          extrasTemplates.add(moduleTemplate);
        }
        FormFactorUtils.FormFactor formFactor = moduleTemplate.getFormFactor();
        if (formFactor != null) {
          formFactorSet.add(formFactor);
        }
      }
    }

    for (final FormFactorUtils.FormFactor formFactor : formFactorSet) {
      registerValueDeriver(FormFactorUtils.getInclusionKey(formFactor), new ValueDeriver<Boolean>() {
        @Nullable
        @Override
        public Boolean deriveValue(@NotNull ScopedStateStore state,
                                   @Nullable ScopedStateStore.Key changedKey,
                                   @Nullable Boolean currentValue) {
          ModuleTemplate moduleTemplate = myState.get(SELECTED_MODULE_TYPE_KEY);
          return moduleTemplate != null && Objects.equal(formFactor, moduleTemplate.getFormFactor());
        }
      });
    }

    List<ModuleTemplate> galleryTemplatesList = galleryTemplates.build();
    List<ModuleTemplate> extrasTemplatesList = extrasTemplates.build();

    Iterable<ModuleTemplate> allTemplates = Iterables.concat(galleryTemplatesList, extrasTemplatesList);
    myFormFactorGallery.setModel(JBList.createDefaultListModel(Iterables.toArray(allTemplates, ModuleTemplate.class)));
    ModuleTypeBinding binding = new ModuleTypeBinding();
    register(SELECTED_MODULE_TYPE_KEY, myPanel, binding);

    myFormFactorGallery.addListSelectionListener(new ModuleTypeSelectionListener());

    if (!galleryTemplatesList.isEmpty()) {
      myState.put(SELECTED_MODULE_TYPE_KEY, galleryTemplatesList.get(0));
    }
  }

  @Override
  public boolean commitStep() {
    ModuleTemplate selected = myState.get(SELECTED_MODULE_TYPE_KEY);
    if (selected != null) {
      selected.updateWizardStateOnSelection(myState);
    }
    return true;
  }

  @NotNull
  @Override
  public String getStepName() {
    return "Choose Module Type Step";
  }

  @NotNull
  @Override
  protected String getStepTitle() {
    return "New Module";
  }

  @Nullable
  @Override
  protected String getStepDescription() {
    return "Android Studio";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myFormFactorGallery;
  }

  private void createUIComponents() {
    myFormFactorGallery = new ASGallery<ModuleTemplate>(JBList.createDefaultListModel(), new Function<ModuleTemplate, Image>() {
      @Override
      public Image apply(ModuleTemplate input) {
        return IconUtil.toImage(input.getIcon());
      }
    }, new Function<ModuleTemplate, String>() {
      @Override
      public String apply(@Nullable ModuleTemplate input) {
        return input == null ? "<none>" : input.getName();
      }
    }, new Dimension(192, 192));
  }

  private class ModuleTypeBinding extends ComponentBinding<ModuleTemplate, JPanel> {
    @Override
    public void setValue(@Nullable ModuleTemplate newValue, @NotNull JPanel component) {
      myFormFactorGallery.setSelectedElement(newValue);
    }

    @Nullable
    @Override
    public ModuleTemplate getValue(@NotNull JPanel component) {
      return myFormFactorGallery.getSelectedElement();
    }
  }

  private class ModuleTypeSelectionListener implements ListSelectionListener {
    @Override
    public void valueChanged(ListSelectionEvent e) {
      ModuleTemplate moduleTemplate = myFormFactorGallery.getSelectedElement();
      if (moduleTemplate != null) {
        myState.put(KEY_DESCRIPTION, moduleTemplate.getDescription());
      }
      saveState(myPanel);
      invokeUpdate(SELECTED_MODULE_TYPE_KEY);
    }
  }
}
