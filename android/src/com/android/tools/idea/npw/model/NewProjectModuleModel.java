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
package com.android.tools.idea.npw.model;

import com.android.SdkConstants;
import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.platform.AndroidVersionsInfo;
import com.android.tools.idea.npw.template.TemplateHandle;
import com.android.tools.idea.npw.template.TemplateValueInjector;
import com.android.tools.idea.observable.core.*;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.android.tools.idea.templates.*;
import com.android.tools.idea.wizard.model.WizardModel;
import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

import static com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createDefaultTemplateAt;
import static com.android.tools.idea.templates.Template.CATEGORY_APPLICATION;
import static com.android.tools.idea.templates.TemplateManager.CATEGORY_ACTIVITY;
import static com.android.tools.idea.templates.TemplateMetadata.*;

public final class NewProjectModuleModel extends WizardModel {
  public static final String EMPTY_ACTIVITY = "Empty Activity";
  public static final String ANDROID_MODULE = "Android Module";

  @NotNull private final NewProjectModel myProjectModel;
  @NotNull private final NewModuleModel myNewModuleModel;
  @NotNull private final OptionalProperty<AndroidVersionsInfo.VersionItem> myAndroidSdkInfo = new OptionalValueProperty<>();
  @NotNull private final ObjectProperty<FormFactor> myFormFactor = new ObjectValueProperty<>(FormFactor.MOBILE);
  @NotNull private final OptionalProperty<TemplateHandle> myRenderTemplateHandle = new OptionalValueProperty<>();

  @NotNull private final BoolProperty myHasCompanionApp = new BoolValueProperty();

  public NewProjectModuleModel(@NotNull NewProjectModel projectModel) {
    myProjectModel = projectModel;
    myNewModuleModel = new NewModuleModel(myProjectModel, new File(""));
  }

  @NotNull
  public BoolProperty instantApp() {
    return myNewModuleModel.instantApp();
  }

  @NotNull
  public BoolProperty includeNavController() {
    return myNewModuleModel.includeNavController();
  }

  @NotNull
  public OptionalProperty<AndroidVersionsInfo.VersionItem> androidSdkInfo() {
    return myAndroidSdkInfo;
  }

  @NotNull
  public OptionalProperty<File> moduleTemplateFile() {
    return myNewModuleModel.templateFile();
  }

  @NotNull
  public OptionalProperty<TemplateHandle> renderTemplateHandle() {
    return myRenderTemplateHandle;
  }

  @NotNull
  public BoolProperty hasCompanionApp() {
    return myHasCompanionApp;
  }

  @NotNull
  public ObjectProperty<FormFactor> formFactor() {
    return myFormFactor;
  }

  @Override
  protected void handleFinished() {
    myProjectModel.getNewModuleModels().clear();

    Project project = myNewModuleModel.getProject().getValueOrNull();
    String projectLocation = myProjectModel.projectLocation().get();
    boolean hasCompanionApp = myHasCompanionApp.get();

    initMainModule();

    Map<String, Object> projectTemplateValues = myProjectModel.getTemplateValues();
    addModuleToProject(myNewModuleModel, myFormFactor.get(), myProjectModel, projectTemplateValues);

    int formFactorsCount = 1;
    if (hasCompanionApp) {
      formFactorsCount++;
      NewModuleModel companionModuleModel = createCompanionModuleModel(myProjectModel);
      RenderTemplateModel companionRenderModel = createCompanionRenderModel(projectLocation, companionModuleModel);
      addModuleToProject(companionModuleModel, FormFactor.MOBILE, myProjectModel, projectTemplateValues);

      companionRenderModel.androidSdkInfo().setValue(androidSdkInfo().getValue());
      companionModuleModel.getRenderTemplateValues().setValue(companionRenderModel.getTemplateValues());

      companionModuleModel.handleFinished();
      companionRenderModel.handleFinished();
    }
    projectTemplateValues.put(ATTR_NUM_ENABLED_FORM_FACTORS, formFactorsCount);

    RenderTemplateModel newRenderTemplateModel = createMainRenderModel(projectLocation);
    myNewModuleModel.getRenderTemplateValues().setValue(newRenderTemplateModel.getTemplateValues());

    boolean noActivitySelected = newRenderTemplateModel.getTemplateHandle() == null;
    if (noActivitySelected) {
      myNewModuleModel.setDefaultRenderTemplateValues(newRenderTemplateModel, project);
    }
    else {
      addRenderDefaultTemplateValues(newRenderTemplateModel);
      myNewModuleModel.getRenderTemplateValues().setValue(newRenderTemplateModel.getTemplateValues());
    }

    new TemplateValueInjector(myNewModuleModel.getTemplateValues())
      .setProjectDefaults(project, myNewModuleModel.applicationName().get(), myNewModuleModel.instantApp().get());

    myNewModuleModel.handleFinished();
    if (noActivitySelected) {
      newRenderTemplateModel.handleSkipped();
    }
    else {
      newRenderTemplateModel.handleFinished();
    }
  }

  private void initMainModule() {
    String moduleName;
    if (myHasCompanionApp.get()) {
      moduleName = getModuleName(myFormFactor.get());
    }
    else if (myNewModuleModel.instantApp().get()) {
      moduleName = myNewModuleModel.splitName().get();
      myNewModuleModel.packageName().set(myNewModuleModel.computedFeatureModulePackageName().get());
    }
    else {
      moduleName = SdkConstants.APP_PREFIX;
    }

    myNewModuleModel.moduleName().set(moduleName);
  }

  @NotNull
  private RenderTemplateModel createMainRenderModel(String projectLocation) {
    RenderTemplateModel newRenderTemplateModel;
    if (myProjectModel.enableCppSupport().get()) {
      newRenderTemplateModel = createCompanionRenderModel(projectLocation, myNewModuleModel);
    }
    else {
      File moduleRoot = new File(projectLocation, myNewModuleModel.moduleName().get());
      newRenderTemplateModel = new RenderTemplateModel(myNewModuleModel, null, createDefaultTemplateAt(moduleRoot), "");
      newRenderTemplateModel.setTemplateHandle(renderTemplateHandle().getValueOrNull());
    }
    newRenderTemplateModel.androidSdkInfo().setValue(androidSdkInfo().getValue());
    return newRenderTemplateModel;
  }

  private static void addModuleToProject(@NotNull NewModuleModel moduleModel, @NotNull FormFactor formFactor,
                                         @NotNull NewProjectModel projectModel, @NotNull Map<String, Object> projectTemplateValues) {
    projectTemplateValues.put(formFactor.id + ATTR_INCLUDE_FORM_FACTOR, true);
    projectTemplateValues.put(formFactor.id + ATTR_MODULE_NAME, moduleModel.moduleName().get());
    projectModel.getNewModuleModels().add(moduleModel);
  }

  @NotNull
  private static NewModuleModel createCompanionModuleModel(@NotNull NewProjectModel projectModel) {
    // Note: The companion Module is always a Mobile app
    File moduleTemplateFile = TemplateManager.getInstance().getTemplateFile(CATEGORY_APPLICATION, ANDROID_MODULE);
    NewModuleModel companionModuleModel = new NewModuleModel(projectModel, moduleTemplateFile);
    companionModuleModel.moduleName().set(getModuleName(FormFactor.MOBILE));

    new TemplateValueInjector(companionModuleModel.getTemplateValues())
      .setProjectDefaults(projectModel.project().getValueOrNull(), companionModuleModel.applicationName().get(), false);

    return companionModuleModel;
  }

  @NotNull
  private static RenderTemplateModel createCompanionRenderModel(@NotNull String projectLocation, @NotNull NewModuleModel moduleModel) {
    // Note: The companion Render is always a "Empty Activity"
    File mobileModuleRoot = new File(projectLocation, moduleModel.moduleName().get());
    NamedModuleTemplate namedModuleTemplate = createDefaultTemplateAt(mobileModuleRoot);
    File renderTemplateFile = TemplateManager.getInstance().getTemplateFile(CATEGORY_ACTIVITY, EMPTY_ACTIVITY);
    TemplateHandle renderTemplateHandle = new TemplateHandle(renderTemplateFile);

    RenderTemplateModel companionRenderModel = new RenderTemplateModel(moduleModel, renderTemplateHandle, namedModuleTemplate, "");
    addRenderDefaultTemplateValues(companionRenderModel);

    return companionRenderModel;
  }

  @NotNull
  private static String getModuleName(@NotNull FormFactor formFactor) {
    if (formFactor.baseFormFactor != null) {
      // Form factors like Android Auto build upon another form factor
      formFactor = formFactor.baseFormFactor;
    }
    return formFactor.id.replaceAll("\\s", "_").toLowerCase(Locale.US);
  }

  private static void addRenderDefaultTemplateValues(RenderTemplateModel renderTemplateModel) {
    Map<String, Object>  templateValues = renderTemplateModel.getTemplateValues();
    TemplateMetadata templateMetadata = renderTemplateModel.getTemplateHandle().getMetadata();
    Map<Parameter, Object> userValues = Maps.newHashMap();
    Map<String, Object>  additionalValues = Maps.newHashMap();

    String packageName = renderTemplateModel.packageName().get();
    boolean isInstantApp = renderTemplateModel.instantApp().get();
    new TemplateValueInjector(additionalValues)
      .addTemplateAdditionalValues(packageName, isInstantApp, renderTemplateModel.getTemplate());
    additionalValues.put(ATTR_PACKAGE_NAME, renderTemplateModel.packageName().get());

    try {
      Collection<Parameter> renderParameters = templateMetadata.getParameters();
      Map<Parameter, Object> parameterValues = ParameterValueResolver.resolve(renderParameters, userValues, additionalValues);
      parameterValues.forEach(((parameter, value) -> templateValues.put(parameter.id, value)));
    } catch (CircularParameterDependencyException e) {
      getLog().error("Circular dependency between parameters in template %1$s", e, templateMetadata.getTitle());
    }
  }

  private static Logger getLog() {
    return Logger.getInstance(NewProjectModuleModel.class);
  }
}
