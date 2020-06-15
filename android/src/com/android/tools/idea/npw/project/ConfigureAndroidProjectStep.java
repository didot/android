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
package com.android.tools.idea.npw.project;

import static com.android.sdklib.AndroidVersion.VersionCodes.Q;
import static com.android.tools.adtui.validation.Validator.Result.OK;
import static com.android.tools.adtui.validation.Validator.Severity.ERROR;
import static com.android.tools.adtui.validation.Validator.Severity.WARNING;
import static com.android.tools.idea.npw.model.NewProjectModel.nameToJavaPackage;
import static com.android.tools.idea.npw.platform.AndroidVersionsInfoKt.getSdkManagerLocalPath;
import static com.android.tools.idea.npw.ui.ActivityGallery.getCppIcon;
import static com.android.tools.idea.templates.TemplateMetadata.TemplateConstraint.ANDROIDX;
import static com.android.tools.idea.templates.TemplateMetadata.TemplateConstraint.KOTLIN;
import static com.android.tools.idea.ui.wizard.StudioWizardStepPanel.wrappedWithVScroll;
import static com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFolderDescriptor;
import static java.lang.String.format;
import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.repository.api.RemotePackage;
import com.android.repository.api.UpdatablePackage;
import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.model.NewProjectModel;
import com.android.tools.idea.npw.model.NewProjectModuleModel;
import com.android.tools.idea.npw.platform.AndroidVersionsInfo.VersionItem;
import com.android.tools.idea.npw.platform.Language;
import com.android.tools.idea.npw.template.TemplateHandle;
import com.android.tools.idea.npw.template.components.LanguageComboProvider;
import com.android.tools.idea.npw.ui.ActivityGallery;
import com.android.tools.idea.npw.ui.TemplateIcon;
import com.android.tools.idea.npw.validator.ProjectNameValidator;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.core.OptionalProperty;
import com.android.tools.idea.observable.expressions.Expression;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.wizard.InstallSelectedPackagesStep;
import com.android.tools.idea.sdk.wizard.LicenseAgreementModel;
import com.android.tools.idea.sdk.wizard.LicenseAgreementStep;
import com.android.tools.idea.ui.validation.validators.PathValidator;
import com.android.tools.idea.ui.wizard.WizardUtils;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.template.Template;
import com.android.tools.idea.wizard.template.TemplateConstraint;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.ide.HelpTooltip;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * First page in the New Project wizard that sets project/module name, location, and other project-global
 * parameters.
 */
public class ConfigureAndroidProjectStep extends ModelWizardStep<NewProjectModuleModel> {
  private final NewProjectModel myProjectModel;

  private final ValidatorPanel myValidatorPanel;
  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();
  private final List<UpdatablePackage> myInstallRequests = new ArrayList<>();
  private final List<RemotePackage> myInstallLicenseRequests = new ArrayList<>();

  private JPanel myPanel;
  private TextFieldWithBrowseButton myProjectLocation;
  private JTextField myAppName;
  private JTextField myPackageName;
  private JComboBox<Language> myProjectLanguage;
  private JBCheckBox myAppCompatCheck;
  private JBCheckBox myWearCheck;
  private JBCheckBox myTvCheck;
  private JBLabel myAppCompatHelp;
  private JBLabel myTemplateIconTitle;
  private JBLabel myTemplateIconDetail;
  private JPanel myFormFactorSdkControlsPanel;
  private FormFactorSdkControls myFormFactorSdkControls;

  public ConfigureAndroidProjectStep(@NotNull NewProjectModuleModel newProjectModuleModel, @NotNull NewProjectModel projectModel) {
    super(newProjectModuleModel, message("android.wizard.project.new.configure"));

    myProjectModel = projectModel;
    myValidatorPanel = new ValidatorPanel(this, wrappedWithVScroll(myPanel));

    myAppCompatHelp.setIcon(AllIcons.General.ContextHelp);
    HelpTooltip helpTooltip = new HelpTooltip()
      .setDescription(message("android.wizard.project.help.appcompat"));
    helpTooltip.installOn(myAppCompatCheck);
    helpTooltip.installOn(myAppCompatHelp);

    FormScalingUtil.scaleComponentTree(this.getClass(), myValidatorPanel);
  }

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep> createDependentSteps() {
    LicenseAgreementStep licenseAgreementStep =
      new LicenseAgreementStep(new LicenseAgreementModel(getSdkManagerLocalPath()), myInstallLicenseRequests);

    InstallSelectedPackagesStep installPackagesStep =
      new InstallSelectedPackagesStep(myInstallRequests, new HashSet<>(), AndroidSdks.getInstance().tryToChooseSdkHandler(), false);

    return Lists.newArrayList(licenseAgreementStep, installPackagesStep);
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    ((GridLayoutManager)myPanel.getLayout()).setVGap(2);

    myBindings.bindTwoWay(new TextProperty(myAppName), myProjectModel.getApplicationName());

    String basePackage = NewProjectModel.getSuggestedProjectPackage();

    Expression<String> computedPackageName = myProjectModel.getApplicationName()
                                                           .transform(appName -> format("%s.%s", basePackage, nameToJavaPackage(appName)));
    TextProperty packageNameText = new TextProperty(myPackageName);
    BoolProperty isPackageNameSynced = new BoolValueProperty(true);
    myBindings.bind(myProjectModel.getPackageName(), packageNameText);

    myBindings.bind(packageNameText, computedPackageName, isPackageNameSynced);
    myListeners.listen(packageNameText, value -> isPackageNameSynced.set(value.equals(computedPackageName.get())));

    Expression<String> computedLocation = myProjectModel.getApplicationName().transform(ConfigureAndroidProjectStep::findProjectLocation);
    TextProperty locationText = new TextProperty(myProjectLocation.getTextField());
    BoolProperty isLocationSynced = new BoolValueProperty(true);
    myBindings.bind(locationText, computedLocation, isLocationSynced);
    myBindings.bind(myProjectModel.getProjectLocation(), locationText);
    myListeners.listen(locationText, value -> isLocationSynced.set(value.equals(computedLocation.get())));

    OptionalProperty<VersionItem> androidSdkInfo = getModel().androidSdkInfo();
    myFormFactorSdkControls.init(androidSdkInfo, this);

    myBindings.bindTwoWay(new SelectedItemProperty<>(myProjectLanguage), myProjectModel.getLanguage());
    myBindings.bindTwoWay(myProjectModel.getUseAppCompat(), new SelectedProperty(myAppCompatCheck));

    myValidatorPanel.registerValidator(myProjectModel.getApplicationName(), new ProjectNameValidator());

    Expression<File> locationFile = myProjectModel.getProjectLocation().transform(File::new);
    myValidatorPanel.registerValidator(locationFile, PathValidator.createDefault("project location"));

    myValidatorPanel.registerValidator(myProjectModel.getPackageName(),
                                       value -> Validator.Result.fromNullableMessage(WizardUtils.validatePackageName(value)));

    myValidatorPanel.registerValidator(myProjectModel.getLanguage(), value ->
      value.isPresent() ? OK : new Validator.Result(ERROR, message("android.wizard.validate.select.language")));

    myValidatorPanel.registerValidator(androidSdkInfo, value ->
      value.isPresent() ? OK : new Validator.Result(ERROR, message("select.target.dialog.text")));

    myValidatorPanel.registerTest(myProjectModel.getUseAppCompat().not(), WARNING, message("android.wizard.validate.select.appcompat"));

    myProjectLocation.addBrowseFolderListener(null, null, null, createSingleFolderDescriptor());

    myListeners.listenAll(getModel().formFactor, myProjectModel.getEnableCppSupport()).withAndFire(() -> {
      FormFactor formFactor = getModel().formFactor.get();

      myFormFactorSdkControls.showStatsPanel(formFactor == FormFactor.MOBILE);
      myWearCheck.setVisible(formFactor == FormFactor.WEAR);
      myTvCheck.setVisible(formFactor == FormFactor.TV);
    });

    myListeners.listenAndFire(androidSdkInfo, () -> updateAppCompatCheckBox());
    myListeners.listenAndFire(androidSdkInfo, sender -> updateAppCompatCheckBox());
  }

  @Override
  protected void onEntering() {
    FormFactor formFactor = getModel().formFactor.get();
    TemplateHandle templateHandle = getModel().renderTemplateHandle.getValueOrNull();
    Template newTemplate = getModel().newRenderTemplate.getValueOrNull();
    assert(templateHandle == null || newTemplate == null);

    int minSdk = templateHandle == null
      ? newTemplate == null ? formFactor.getMinOfflineApiLevel() : newTemplate.getMinSdk()
      : templateHandle.getMetadata().getMinSdk();

    myFormFactorSdkControls.startDataLoading(formFactor, minSdk);
    boolean isKotlinOnly;
    if (newTemplate != null) {
      setTemplateThumbnail(newTemplate);
      isKotlinOnly = newTemplate.getConstraints().contains(TemplateConstraint.Kotlin);
    } else {
      setTemplateThumbnail(templateHandle);
      isKotlinOnly = templateHandle != null && templateHandle.getMetadata().getConstraints().contains(KOTLIN);
    }

    myProjectLanguage.setEnabled(!isKotlinOnly);
    if (isKotlinOnly) {
      myProjectModel.getLanguage().setValue(Language.KOTLIN);
    }
    updateAppCompatCheckBox();
  }

  @Override
  protected void onProceeding() {
    getModel().hasCompanionApp.set(
      (myWearCheck.isVisible() && myWearCheck.isSelected()) ||
      (myTvCheck.isVisible() && myTvCheck.isSelected()) ||
      getModel().formFactor.get() == FormFactor.AUTOMOTIVE // Automotive projects include a mobile module for Android Auto by default
    );

    myInstallRequests.clear();
    myInstallLicenseRequests.clear();

    myInstallRequests.addAll(myFormFactorSdkControls.getSdkInstallPackageList());
    myInstallLicenseRequests.addAll(ContainerUtil.map(myInstallRequests, UpdatablePackage::getRemote));
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myValidatorPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myAppName;
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @NotNull
  private static String findProjectLocation(@NotNull String applicationName) {
    applicationName = NewProjectModel.sanitizeApplicationName(applicationName);
    File baseDirectory = WizardUtils.getProjectLocationParent();
    File projectDirectory = new File(baseDirectory, applicationName);

    // Try appName, appName2, appName3, ...
    int counter = 2;
    while (projectDirectory.exists()) {
      projectDirectory = new File(baseDirectory, format(Locale.US, "%s%d", applicationName, counter++));
    }

    return projectDirectory.getPath();
  }

  private void setTemplateThumbnail(@Nullable TemplateHandle templateHandle) {
    setTemplateThumbnail(
      ActivityGallery.getTemplateIcon(templateHandle),
      ActivityGallery.getTemplateImageLabel(templateHandle),
      ActivityGallery.getTemplateDescription(templateHandle)
    );
  }

  private void setTemplateThumbnail(@NotNull Template template) {
    setTemplateThumbnail(ActivityGallery.getTemplateIcon(template), template.getName(), template.getDescription());
  }

  private void setTemplateThumbnail(@Nullable TemplateIcon defaultIcon, @NotNull String name, @NotNull String description) {
    boolean isCppTemplate = myProjectModel.getEnableCppSupport().get();
    TemplateIcon icon = isCppTemplate ? getCppIcon() : defaultIcon;
    if (icon != null) {
      icon.cropBlankWidth();
      icon.setHeight(256);
      myTemplateIconTitle.setIcon(icon);
      myTemplateIconTitle.setText(isCppTemplate ? message("android.wizard.gallery.item.add.cpp") : name);

      myTemplateIconDetail.setText(
        "<html>" + (isCppTemplate ? message("android.wizard.gallery.item.add.cpp.Desc") : description) + "</html>"
      );
    }
    myTemplateIconTitle.setVisible(icon != null);
    myTemplateIconDetail.setVisible(icon != null);
  }

  private void updateAppCompatCheckBox() {
    VersionItem androidVersion = getModel().androidSdkInfo().getValueOrNull();
    boolean isAndroidxApi = androidVersion != null && androidVersion.getMinApiLevel() >= Q; // No more app-compat after Q

    TemplateHandle templateHandle = getModel().renderTemplateHandle.getValueOrNull();
    Template newTemplate = getModel().newRenderTemplate.getValueOrNull();
    assert(templateHandle == null || newTemplate == null);

    boolean hasAndroidxConstraint;
    if (newTemplate != null) {
      hasAndroidxConstraint = newTemplate.getConstraints().contains(TemplateConstraint.AndroidX);
    } else {
      hasAndroidxConstraint = templateHandle != null && templateHandle.getMetadata().getConstraints().contains(ANDROIDX);
    }

    if (isAndroidxApi || hasAndroidxConstraint) {
      myAppCompatCheck.setSelected(false);
      myAppCompatCheck.setEnabled(false);
    }
    else {
      myAppCompatCheck.setEnabled(true);
    }
  }

  private void createUIComponents() {
    myProjectLanguage = new LanguageComboProvider().createComponent();
    myFormFactorSdkControls = new FormFactorSdkControls();
    myFormFactorSdkControlsPanel = myFormFactorSdkControls.getRoot();
  }
}
