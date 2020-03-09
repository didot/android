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
package com.android.tools.idea.npw.importing;

import static com.intellij.openapi.project.ProjectUtil.guessProjectDir;
import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.gradle.project.ModuleImporter;
import com.android.tools.idea.gradle.project.ModuleToImport;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.ui.validation.validators.PathValidator;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.ui.wizard.WizardUtils;
import com.android.tools.idea.util.FormatUtil;
import com.android.tools.idea.wizard.model.ModelWizard.Facade;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBScrollPane;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wizard Step that allows the user to point to an existing source directory (ADT or Gradle) to import as a new Android Gradle module.
 * Also allows selection of sub-modules to import. Most functionality is contained within existing {@link ModulesTable} class.
 */
public final class SourceToGradleModuleStep extends SkippableWizardStep<SourceToGradleModuleModel> {
  private final ListenerManager myListeners = new ListenerManager();
  private final BindingsManager myBindings = new BindingsManager();

  @NotNull private final StudioWizardStepPanel myRootPanel;
  @NotNull private ValidatorPanel myValidatorPanel;

  private final BoolProperty myCanGoForward = new BoolValueProperty();

  // Facade is initialised dynamically
  @Nullable private Facade myFacade;

  private JPanel myPanel;
  private TextFieldWithBrowseButton mySourceLocation;
  private JBScrollPane myModulesScroller;
  private ModulesTable myModulesPanel;
  private JLabel myRequiredModulesLabel;

  // TODO(qumeric): Improve logic so these don't need to all be set to null in applyValidationResult
  @Nullable private VirtualFile myVFile;
  @Nullable private ModuleImporter myImporter;
  @Nullable private Collection<ModuleToImport> myModules;

  public SourceToGradleModuleStep(@NotNull SourceToGradleModuleModel model) {
    super(model, message("android.wizard.module.import.source.title"));

    mySourceLocation.addBrowseFolderListener(message("android.wizard.module.import.source.browse.title"),
                                             message("android.wizard.module.import.source.browse.description"),
                                             getModel().getProject(),
                                             FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor());

    myBindings.bindTwoWay(new TextProperty(mySourceLocation.getTextField()), model.sourceLocation);

    myModulesPanel.bindPrimaryModuleEntryComponents(new PrimaryModuleImportSettings(), myRequiredModulesLabel);
    myModulesPanel.addPropertyChangeListener(ModulesTable.PROPERTY_SELECTED_MODULES, event -> {
      if (ModulesTable.PROPERTY_SELECTED_MODULES.equals(event.getPropertyName())) {
        updateStepStatus();
      }
    });

    myValidatorPanel = new ValidatorPanel(this, myPanel);
    myValidatorPanel.registerValidator(model.sourceLocation, value -> checkPath(model.sourceLocation.get()));

    myListeners.listen(myValidatorPanel.getValidationResult(), result -> applyValidationResult(result));

    myRootPanel = new StudioWizardStepPanel(myValidatorPanel);
    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
  }

  @Override
  protected void onWizardStarting(@NotNull Facade wizard) {
    myFacade = wizard;
  }

  @Override
  protected void onProceeding() {
    getModel().setModulesToImport(myModulesPanel.getSelectedModulesMap());
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myCanGoForward;
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return mySourceLocation;
  }

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep<ModuleWizardStepAdapter.AdapterModel>> createDependentSteps() {
    WizardContext context = getModel().getContext();
    ArrayList<ModelWizardStep<ModuleWizardStepAdapter.AdapterModel>> wrappedSteps = new ArrayList<>();

    for (ModuleImporter importer : ModuleImporter.getAllImporters(context)) {
      for (ModuleWizardStep inputStep : importer.createWizardSteps()) {
        wrappedSteps.add(new ModuleWizardStepAdapter(context, inputStep));
      }
    }

    return wrappedSteps;
  }

  private void applyValidationResult(@NotNull PathValidator.Result result) {
    if (result.getSeverity().equals(Validator.Severity.ERROR)) {
      myVFile = null;
      myModules = null;
      myImporter = null;
    }
    myModulesPanel.setModules(getModel().getProject(), myVFile, myModules);
    myModulesScroller.setVisible(myModulesPanel.getComponentCount() > 0);

    // Setting the active importer affects the visibility of other steps in the wizard so we need to call updateNavigationProperties
    // to make sure Finish / Next is displayed correctly
    ModuleImporter.setImporter(getModel().getContext(), myImporter);
    assert myFacade != null;
    myFacade.updateNavigationProperties();

    updateStepStatus();
  }

  private void updateStepStatus() {
    // Validation of import location can be superseded by lack of modules selected for import
    if (myModulesPanel.getSelectedModules().isEmpty()) {
      myCanGoForward.set(false);
      return;
    }

    myCanGoForward.set(myValidatorPanel.hasErrors().not().get() && myModulesPanel.canImport());
  }

  @NotNull
  @VisibleForTesting
  Validator.Result checkPath(@NotNull String path) {
    if (Strings.isNullOrEmpty(path)) {
      return new Validator.Result(Validator.Severity.ERROR, message("android.wizard.module.import.source.browse.no.location"));
    }
    myVFile = VfsUtil.findFileByIoFile(new File(path), false);
    if (myVFile == null || !myVFile.exists()) {
      return new Validator.Result(Validator.Severity.ERROR, message("android.wizard.module.import.source.browse.invalid.location"));
    }
    else if (isProjectOrModule(myVFile)) {
      return new Validator.Result(Validator.Severity.ERROR, message("android.wizard.module.import.source.browse.taken.location"));
    }
    myImporter = ModuleImporter.importerForLocation(getModel().getContext(), myVFile);
    if (!myImporter.isValid()) {
      return new Validator.Result(Validator.Severity.ERROR, message("android.wizard.module.import.source.browse.cant.import"));
    }
    myModules = ApplicationManager.getApplication().runReadAction((Computable<Collection<ModuleToImport>>)() -> {
      try {
        return myImporter.findModules(myVFile);
      }
      catch (IOException e) {
        Logger.getInstance(SourceToGradleModuleStep.class).error(e);
        return null;
      }
    });
    if (myModules == null) {
      return new Validator.Result(Validator.Severity.ERROR, message("android.wizard.module.import.source.browse.error"));
    }
    Set<String> missingSourceModuleNames = Sets.newTreeSet();
    for (ModuleToImport module : myModules) {
      if (module.location == null || !module.location.exists()) {
        missingSourceModuleNames.add(module.name);
      }
    }
    if (!missingSourceModuleNames.isEmpty()) {
      final String formattedMessage = FormatUtil.formatElementListString(
        missingSourceModuleNames,
        message("android.wizard.module.import.source.browse.bad.modules.1"),
        message("android.wizard.module.import.source.browse.bad.modules.2"),
        message("android.wizard.module.import.source.browse.bad.modules.more")
      );
      String htmlFormattedMessage =  WizardUtils.toHtmlString(formattedMessage);
      return new Validator.Result(Validator.Severity.WARNING, htmlFormattedMessage);
    }
    return Validator.Result.OK;
  }

  private boolean isProjectOrModule(@NotNull VirtualFile dir) {
    Project project = getModel().getProject();
    if (dir.equals(guessProjectDir(project))) {
      return true;
    }

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (ModuleUtilCore.isModuleDir(module, dir)) {
        return true;
      }
    }

    return false;
  }
}
