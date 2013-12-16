/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.sdklib.SdkManager;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.sdk.DefaultSdks;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateMetadata;
import com.intellij.ide.util.projectWizard.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Condition;
import icons.AndroidIcons;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.sdk.MessageBuildingSdkLog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.List;

import static com.android.tools.idea.templates.Template.CATEGORY_ACTIVITIES;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_LAUNCHER;
import static com.android.tools.idea.wizard.NewProjectWizardState.ATTR_MODULE_NAME;

public class TemplateWizardModuleBuilder extends ModuleBuilder implements TemplateWizardStep.UpdateListener {
  private static final Logger LOG = Logger.getInstance("#" + TemplateWizardModuleBuilder.class.getName());
  private final TemplateMetadata myMetadata;
  @NotNull final List<ModuleWizardStep> mySteps;
  @Nullable private Project myProject;

  NewModuleWizardState myWizardState;
  ConfigureAndroidModuleStep myConfigureAndroidModuleStep;
  TemplateParameterStep myTemplateParameterStep;
  LauncherIconStep myLauncherIconStep;
  ChooseTemplateStep myChooseActivityStep;
  TemplateParameterStep myActivityTemplateParameterStep;
  boolean myInitializationComplete = false;

  public TemplateWizardModuleBuilder(@Nullable File templateFile,
                                     @Nullable TemplateMetadata metadata,
                                     @Nullable Project project,
                                     @Nullable Icon sidePanelIcon,
                                     @NotNull List<ModuleWizardStep> steps,
                                     boolean hideModuleName) {
    myMetadata = metadata;
    myProject = project;
    mySteps = steps;

    myWizardState = new NewModuleWizardState() {
      @Override
      public void setTemplateLocation(@NotNull File file) {
        super.setTemplateLocation(file);
        update();
      }
    };
    myWizardState.put(ATTR_IS_LAUNCHER, project == null);
    myWizardState.updateParameters();

    if (templateFile != null) {
      myWizardState.setTemplateLocation(templateFile);
    }
    if (hideModuleName) {
      myWizardState.myHidden.add(ATTR_MODULE_NAME);
    }

    myWizardState.convertApisToInt();

    myConfigureAndroidModuleStep = new ConfigureAndroidModuleStep(myWizardState, myProject, sidePanelIcon, this);
    myTemplateParameterStep = new TemplateParameterStep(myWizardState, myProject, sidePanelIcon, this);
    myLauncherIconStep = new LauncherIconStep(myWizardState.getLauncherIconState(), myProject, sidePanelIcon, this);
    myChooseActivityStep = new ChooseTemplateStep(myWizardState.getActivityTemplateState(), CATEGORY_ACTIVITIES, myProject, sidePanelIcon,
                                                  this, null);
    myActivityTemplateParameterStep = new TemplateParameterStep(myWizardState.getActivityTemplateState(), myProject, sidePanelIcon, this);

    mySteps.add(myConfigureAndroidModuleStep);
    mySteps.add(myTemplateParameterStep);
    mySteps.add(myLauncherIconStep);
    mySteps.add(myChooseActivityStep);
    mySteps.add(myActivityTemplateParameterStep);

    if (project != null) {
      myWizardState.put(NewModuleWizardState.ATTR_PROJECT_LOCATION, project.getBasePath());
    }
    myWizardState.put(TemplateMetadata.ATTR_GRADLE_VERSION, GradleUtil.GRADLE_LATEST_VERSION);
    myWizardState.put(TemplateMetadata.ATTR_GRADLE_PLUGIN_VERSION, GradleUtil.GRADLE_PLUGIN_LATEST_VERSION);
    myWizardState.put(TemplateMetadata.ATTR_V4_SUPPORT_LIBRARY_VERSION, TemplateMetadata.V4_SUPPORT_LIBRARY_VERSION);

    update();

    myInitializationComplete = true;
  }

  @Nullable
  @Override
  public String getBuilderId() {
    assert myMetadata != null;
    return myMetadata.getTitle();
  }

  @Override
  @NotNull
  public ModuleWizardStep[] createWizardSteps(@NotNull WizardContext wizardContext, @NotNull ModulesProvider modulesProvider) {
    update();
    myConfigureAndroidModuleStep.setWizardContext(wizardContext);
    return mySteps.toArray(new ModuleWizardStep[mySteps.size()]);
  }

  @Nullable
  @Override
  public ModuleWizardStep modifySettingsStep(@NotNull SettingsStep settingsStep) {
    if (DefaultSdks.getDefaultJdk() == null || DefaultSdks.getDefaultAndroidHome() == null) {
      return new MyModuleWizardStep(settingsStep);
    }
    return null;
  }

  @Override
  public void update() {
    if (!myInitializationComplete) {
      return;
    }
    myConfigureAndroidModuleStep.setVisible(myWizardState.myIsAndroidModule);
    myTemplateParameterStep.setVisible(!myWizardState.myIsAndroidModule);
    myLauncherIconStep.setVisible(myWizardState.myIsAndroidModule && myWizardState.getBoolean(TemplateMetadata.ATTR_CREATE_ICONS));
    myChooseActivityStep.setVisible(
      myWizardState.myIsAndroidModule && myWizardState.getBoolean(NewModuleWizardState.ATTR_CREATE_ACTIVITY));
    myActivityTemplateParameterStep.setVisible(
      myWizardState.myIsAndroidModule && myWizardState.getBoolean(NewModuleWizardState.ATTR_CREATE_ACTIVITY));
  }

  @Override
  public void setupRootModel(final @NotNull ModifiableRootModel rootModel) throws ConfigurationException {
    final Project project = rootModel.getProject();

    // in IntelliJ wizard user is able to choose SDK (i.e. for "java library" module), so set it
    if (myJdk != null){
      rootModel.setSdk(myJdk);
    } else {
      rootModel.inheritSdk();
    }
    if (myProject == null) {
      project.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, Boolean.TRUE);
    }
    StartupManager.getInstance(project).runWhenProjectIsInitialized(new DumbAwareRunnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              @Override
              public void run() {
                if (myProject == null) {
                  myWizardState.putSdkDependentParams();
                  myWizardState.put(NewModuleWizardState.ATTR_PROJECT_LOCATION, project.getBasePath());
                  NewProjectWizard.createProject(myWizardState, project);
                }
                else {
                  createModule();
                }
              }
            });
          }
        });
      }
    });
    }

  @Nullable
  static SdkManager getSdkManager(@Nullable Sdk sdk) {
    if (sdk == null) {
      return null;
    }
    final AndroidPlatform platform = AndroidPlatform.getInstance(sdk);
    return platform != null ? platform.getSdkData().getSdkManager() : null;
  }

  @Override
  @NotNull
  public ModuleType getModuleType() {
    return StdModuleTypes.JAVA;
  }

  @Override
  public Icon getBigIcon() {
    return AndroidIcons.Android24;
  }

  @Override
  public Icon getNodeIcon() {
    return AndroidIcons.Android;
  }

  @Override
  public void setName(@NotNull String name) {
    super.setName(name);
    myConfigureAndroidModuleStep.setModuleName(name);
  }

  public void createModule() {
    try {
      myWizardState.populateDirectoryParameters();
      File projectRoot = new File(myProject.getBasePath());
      File moduleRoot = new File(projectRoot, myWizardState.getString(NewProjectWizardState.ATTR_MODULE_NAME));
      // TODO: handle return type of "mkdirs".
      projectRoot.mkdirs();
      if (myLauncherIconStep.isStepVisible() && myWizardState.getBoolean(TemplateMetadata.ATTR_CREATE_ICONS)) {
        myWizardState.getLauncherIconState().outputImages(moduleRoot);
      }
      myWizardState.updateParameters();
      myWizardState.myTemplate.render(projectRoot, moduleRoot, myWizardState.myParameters);
      if (myActivityTemplateParameterStep.isStepVisible() && myWizardState.getBoolean(NewModuleWizardState.ATTR_CREATE_ACTIVITY)) {
        TemplateWizardState activityTemplateState = myWizardState.getActivityTemplateState();
        Template template = activityTemplateState.getTemplate();
        assert template != null;
        template.render(moduleRoot, moduleRoot, activityTemplateState.myParameters);
      }
      GradleProjectImporter.getInstance().reImportProject(myProject, null);
    } catch (Exception e) {
      Messages.showErrorDialog(e.getMessage(), "New Module");
      LOG.error(e);
    }
  }

  @Override
  public boolean isSuitableSdkType(SdkTypeId sdkType) {
    return myWizardState.myIsAndroidModule
           ? AndroidSdkType.getInstance().equals(sdkType)
           : sdkType instanceof JavaSdkType;
  }

  private class MyModuleWizardStep extends JavaSettingsStep {

    private TextFieldWithBrowseButton myAndroidSdkLocationField;

    public MyModuleWizardStep(@NotNull SettingsStep settingsStep) {
      super(settingsStep, TemplateWizardModuleBuilder.this, new Condition<SdkTypeId>() {
        @Override
        public boolean value(SdkTypeId id) {
          return JavaSdk.getInstance() == id;
        }
      });

      if (DefaultSdks.getDefaultAndroidHome() == null) {
        myAndroidSdkLocationField = new TextFieldWithBrowseButton();
        myAndroidSdkLocationField.addBrowseFolderListener(new TextBrowseFolderListener(
          new FileChooserDescriptor(false, true, false, false, false, false)));
        settingsStep.addSettingsField("An\u001Bdroid SDK location:", myAndroidSdkLocationField);
      }
    }

    @Nullable
    private String getAndroidSdkLocation() {
      return myAndroidSdkLocationField != null
             ? myAndroidSdkLocationField.getText().trim()
             : null;
    }

    @Override
    public void updateDataModel() {
      super.updateDataModel();
      final String location = getAndroidSdkLocation();

      if (location != null) {
        final Sdk javaSdk = myJdk != null ? myJdk : myWizardContext.getProjectJdk();
        DefaultSdks.setDefaultAndroidHome(new File(location), false, javaSdk);
      }
    }

    @NotNull
    protected String getSdkFieldLabel(@Nullable Project project) {
      return "Java \u001BSDK:";
    }

    @Override
    public boolean validate() throws ConfigurationException {
      if (myJdkComboBox.getSelectedJdk() == null) {
        throw new ConfigurationException("Specify Java SDK");
      }
      myModel.apply(null, true);
      final String location = getAndroidSdkLocation();

      if (location != null) {
        if (location.length() == 0) {
          throw new ConfigurationException("Specify Android SDK location");
        }
        if (!new File(location).isDirectory()) {
          throw new ConfigurationException(location + " is not directory");
        }
        final MessageBuildingSdkLog log = new MessageBuildingSdkLog();
        final AndroidSdkData sdkData = AndroidSdkData.parse(location, log);

        if (sdkData == null) {
          final String message = log.getErrorMessage();

          if (message.length() > 0) {
            throw new ConfigurationException("Android SDK is parsed incorrectly. Parsing log:\n" + message);
          }
          else {
            throw new ConfigurationException("Invalid Android SDK");
          }
        }
      }
      return true;
    }
  }
}
