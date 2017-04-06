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
package com.android.tools.idea.npw.project;

import com.android.SdkConstants;
import com.android.repository.io.FileOpUtils;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.importing.GradleProjectImporter;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.npw.module.NewModuleModel;
import com.android.tools.idea.npw.template.MultiTemplateRenderer;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.recipe.RenderingContext;
import com.android.tools.idea.ui.properties.core.*;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.model.WizardModel;
import com.google.common.collect.Maps;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static com.android.tools.idea.instantapp.InstantApps.getInstantAppSdkLocation;
import static com.android.tools.idea.templates.TemplateMetadata.*;
import static org.jetbrains.android.util.AndroidBundle.message;


public class NewProjectModel extends WizardModel {
  private static final String PROPERTIES_DOMAIN_KEY = "SAVED_COMPANY_DOMAIN";
  private static final String EXAMPLE_DOMAIN = "example.com";
  private static final Pattern DISALLOWED_IN_DOMAIN = Pattern.compile("[^a-zA-Z0-9_]");

  private final StringProperty myApplicationName = new StringValueProperty(message("android.wizard.module.config.new.application"));
  private final StringProperty myCompanyDomain = new StringValueProperty(getInitialDomain(true));
  private final StringProperty myPackageName = new StringValueProperty();
  private final StringProperty myProjectLocation = new StringValueProperty();
  private final BoolProperty myEnableCppSupport = new BoolValueProperty();
  private final StringProperty myCppFlags = new StringValueProperty();
  private final OptionalProperty<Project> myProject = new OptionalValueProperty<>();
  private final Map<String, Object> myTemplateValues = Maps.newHashMap();
  private final Set<NewModuleModel> myNewModels = new HashSet<>();
  private final MultiTemplateRenderer myMultiTemplateRenderer = new MultiTemplateRenderer();

  private static Logger getLogger() {
    return Logger.getInstance(NewProjectModel.class);
  }

  public NewProjectModel() {
    // Save entered company domain
    myCompanyDomain.addListener(sender -> {
      String domain = myCompanyDomain.get();
      if (AndroidUtils.isValidAndroidPackageName(domain)) {
        PropertiesComponent.getInstance().setValue(PROPERTIES_DOMAIN_KEY, domain);
      }
    });

    myApplicationName.addConstraint(String::trim);
  }

  public StringProperty packageName() {
    return myPackageName;
  }

  public StringProperty applicationName() {
    return myApplicationName;
  }

  public StringProperty companyDomain() {
    return myCompanyDomain;
  }

  public StringProperty projectLocation() {
    return myProjectLocation;
  }

  public BoolProperty enableCppSupport() {
    return myEnableCppSupport;
  }

  public StringProperty cppFlags() {
    return myCppFlags;
  }

  public OptionalProperty<Project> project() {
    return myProject;
  }

  @NotNull
  public Map<String, Object> getTemplateValues() {
    return myTemplateValues;
  }

  /**
   * When the project is created, it contains the list of new Module that should also be created.
   */
  // TODO: is not yet clear what the project needs from the modules. At the moment gets the module hash table, but different modules may
  // have the same key/values... and some of these key values should actually be the same... for example, if one module needs a gradle plugin
  // version, shouldn't all the modules use the same version?
  public Set<NewModuleModel> getNewModuleModels() {
    return myNewModels;
  }

  public MultiTemplateRenderer getMultiTemplateRenderer() {
    return myMultiTemplateRenderer;
  }

  /**
   * Loads saved company domain, or generates a dummy one if no domain has been saved
   *
   * @param includeUserName This is used to implement legacy behaviour. When creating a new project the package name includes the user name
   *                        (if available), but when creating a new Module, the user name is not used.
   */
  @NotNull
  public static String getInitialDomain(boolean includeUserName) {
    String domain = PropertiesComponent.getInstance().getValue(PROPERTIES_DOMAIN_KEY);
    if (domain != null) {
      return domain;
    }

    // TODO: Figure out if this legacy behaviour, of including User Name, can be removed.
    String userName = includeUserName ? System.getProperty("user.name") : null;
    return userName == null ? EXAMPLE_DOMAIN : toPackagePart(userName) + '.' + EXAMPLE_DOMAIN;
  }

  @NotNull
  public static String toPackagePart(@NotNull String s) {
    s = s.replace('-', '_');
    String name = DISALLOWED_IN_DOMAIN.matcher(s).replaceAll("").toLowerCase(Locale.US);
    if (!name.isEmpty() && AndroidUtils.isReservedKeyword(name) != null) {
      name = StringUtil.fixVariableNameDerivedFromPropertyName(name).toLowerCase(Locale.US);
    }
    return name;
  }

  @NotNull
  public static String sanitizeApplicationName(@NotNull String s) {
    return DISALLOWED_IN_DOMAIN.matcher(s).replaceAll("");
  }

  @Override
  protected void handleFinished() {
    myMultiTemplateRenderer.requestRender(new ProjectTemplateRenderer());
  }

  private class ProjectTemplateRenderer implements MultiTemplateRenderer.TemplateRenderer {

    @Override
    public boolean doDryRun() {
      final String projectLocation = projectLocation().get();
      final String projectName = applicationName().get();

      boolean couldEnsureLocationExists = WriteCommandAction.runWriteCommandAction(null, new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          // We generally assume that the path has passed a fair amount of prevalidation checks
          // at the project configuration step before. Write permissions check can be tricky though in some cases,
          // e.g., consider an unmounted device in the middle of wizard execution or changed permissions.
          // Anyway, it seems better to check that we were really able to create the target location and are able to
          // write to it right here when the wizard is about to close, than running into some internal IDE errors
          // caused by these problems downstream
          // Note: this change was originally caused by http://b.android.com/219851, but then
          // during further discussions that a more important bug was in path validation in the old wizards,
          // where File.canWrite() always returned true as opposed to the correct Files.isWritable(), which is
          // already used in new wizard's PathValidator.
          // So the change below is therefore a more narrow case than initially supposed (however it still needs to be handled)
          try {
            if (VfsUtil.createDirectoryIfMissing(projectLocation) != null && FileOpUtils.create().canWrite(new File(projectLocation))) {
              return true;
            }
          } catch (Exception e) {
            getLogger().error(String.format("Exception thrown when creating target project location: %1$s", projectLocation), e);
          }
          return false;
        }
      });
      if(!couldEnsureLocationExists) {
        String msg = "Could not ensure the target project location exists and is accessible:\n\n%1$s\n\nPlease try to specify another path.";
        Messages.showErrorDialog(String.format(msg, projectLocation), "Error Creating Project");
        // TODO: Is this available on the New Wizard?
        //navigateToNamedStep(com.android.tools.idea.npw.deprecated.ConfigureAndroidProjectStep.STEP_NAME, true);
        //myHost.shakeWindow();
        return false;
      }

      Project project = UIUtil.invokeAndWaitIfNeeded(() -> ProjectManager.getInstance().createProject(projectName, projectLocation));
      project().setValue(project);

      performCreateProject(true);
      return true;
    }

    @Override
    public void render() {
      performCreateProject(false);

      try {
        File projectRoot = VfsUtilCore.virtualToIoFile(project().getValue().getBaseDir());
        AndroidGradleModuleUtils.setGradleWrapperExecutable(projectRoot);
      }
      catch (IOException e) {
        getLogger().warn("Failed to update Gradle wrapper permissions", e);
      }

      // Allow all other Wizard models to run handleFinished() (and the Wizard to close), before starting the (slow) import process.
      ApplicationManager.getApplication().invokeLater(this::performGradleImport);
    }

    private boolean performCreateProject(boolean dryRun) {
      Project project = project().getValue();

      // Cpp Apps attributes are needed to generate the Module and to generate the Render Template files (activity and layout)
      myTemplateValues.put(ATTR_CPP_SUPPORT, myEnableCppSupport.get());
      myTemplateValues.put(ATTR_CPP_FLAGS, myCppFlags.get());
      myTemplateValues.put(ATTR_TOP_OUT, project.getBasePath());

      Map<String, Object> params = Maps.newHashMap(myTemplateValues);
      boolean hasInstantApp = false;
      for (NewModuleModel newModuleModel : getNewModuleModels()) {
        params.putAll(newModuleModel.getTemplateValues());
        hasInstantApp |= newModuleModel.instantApp().get();

        // Set global parameters
        newModuleModel.getRenderTemplateValues().getValue().putAll(myTemplateValues);
        newModuleModel.getTemplateValues().putAll(myTemplateValues);
      }

      // TODO: Maybe we should not reuse ATTR_IS_INSTANT_APP. A new ATTR_IS_INSTANT_PROJECT?
      params.put(ATTR_IS_INSTANT_APP, hasInstantApp);
    if (hasInstantApp) {
      params.put(ATTR_INSTANT_APP_SDK_DIR, getInstantAppSdkLocation());
    }

      Template projectTemplate = Template.createFromName(Template.CATEGORY_PROJECTS, WizardConstants.PROJECT_TEMPLATE_NAME);
      // @formatter:off
      final RenderingContext context = RenderingContext.Builder.newContext(projectTemplate, project)
        .withCommandName("New Project")
        .withDryRun(dryRun)
        .withShowErrors(true)
        .withParams(params)
        .build();
      // @formatter:on
      return projectTemplate.render(context);
    }

    private void performGradleImport() {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        return;
      }

      GradleProjectImporter projectImporter = GradleProjectImporter.getInstance();
      File rootLocation = new File(projectLocation().get());
      File wrapperPropertiesFilePath = GradleWrapper.getDefaultPropertiesFilePath(rootLocation);
      try {
        GradleWrapper.get(wrapperPropertiesFilePath).updateDistributionUrl(SdkConstants.GRADLE_LATEST_VERSION);
      }
      catch (IOException e) {
        // Unlikely to happen. Continue with import, the worst-case scenario is that sync fails and the error message has a "quick fix".
        getLogger().warn("Failed to update Gradle wrapper file", e);
      }

      // Pick the highest language level of all the modules/form factors.
      // We have to pick the language level up front while creating the project rather than
      // just reacting to it during sync, because otherwise the user gets prompted with
      // a changing-language-level-requires-reopening modal dialog box and have to reload
      // the project
      // TODO: We don't have a way at the moment of getting the language level of each module.
      // Possible solutions:
      // 1 - We have already a Set of <NewModuleModel>, and from there we can get templateFile, but maybe what we should get is a formFactor
      // 2 - Add a new field to NewModuleModel with the FormFactor or something similar... we may need this anyway when we check if we need
      // to install a new API (That step is missing at the moment)
      LanguageLevel initialLanguageLevel = null;
      //for (FormFactor factor : FormFactor.values()) {
      //  Object version = getState().get(FormFactorUtils.getLanguageLevelKey(factor));
      //  if (version != null) {
      //    LanguageLevel level = LanguageLevel.parse(version.toString());
      //    if (level != null && (initialLanguageLevel == null || level.isAtLeast(initialLanguageLevel))) {
      //      initialLanguageLevel = level;
      //    }
      //  }
      //}

      // This is required for Android plugin in IDEA
      if (!IdeInfo.getInstance().isAndroidStudio()) {
        final Sdk jdk = IdeSdks.getInstance().getJdk();
        if (jdk != null) {
          ApplicationManager.getApplication().runWriteAction(() -> ProjectRootManager.getInstance(project().getValue()).setProjectSdk(jdk));
        }
      }
      try {
        GradleProjectImporter.Request request = new GradleProjectImporter.Request();
        request.setLanguageLevel(initialLanguageLevel).setProject(project().getValue());
        projectImporter.importProject(applicationName().get(), rootLocation, request, null);
      }
      catch (IOException | ConfigurationException e) {
        Messages.showErrorDialog(e.getMessage(), message("android.wizard.project.create.error"));
        getLogger().error(e);
      }
    }
  }
}
