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

package com.android.tools.idea.testartifacts.instrumented;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_HIERARCHY;
import static com.intellij.openapi.util.text.StringUtil.getPackageName;
import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

import com.android.builder.model.AndroidArtifact;
import com.android.ide.common.gradle.model.IdeAndroidArtifact;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.ApkProvider;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.ConsoleProvider;
import com.android.tools.idea.run.LaunchOptions;
import com.android.tools.idea.run.NonGradleApkProvider;
import com.android.tools.idea.run.ValidationError;
import com.android.tools.idea.run.editor.AndroidRunConfigurationEditor;
import com.android.tools.idea.run.editor.TestRunParameters;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.ui.BaseAction;
import com.android.tools.idea.run.util.LaunchStatus;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.RefactoringListenerProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.listeners.RefactoringElementAdapter;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jetbrains.android.dom.manifest.Instrumentation;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Run Configuration for "Android Instrumented Tests"
 */
public class AndroidTestRunConfiguration extends AndroidRunConfigurationBase implements RefactoringListenerProvider {
  public static final int TEST_ALL_IN_MODULE = 0;
  public static final int TEST_ALL_IN_PACKAGE = 1;
  public static final int TEST_CLASS = 2;
  public static final int TEST_METHOD = 3;

  /** A default value for instrumentation runner class used in Android Gradle Plugin. */
  public static final String DEFAULT_INSTRUMENTATION_RUNNER_CLASS_IN_AGP = "android.test.InstrumentationTestRunner";

  public int TESTING_TYPE = TEST_ALL_IN_MODULE;
  @NotNull public String METHOD_NAME = "";
  @NotNull public String CLASS_NAME = "";
  @NotNull public String PACKAGE_NAME = "";

  /**
   * A fully qualified name of an instrumentation runner class to use. If this is an empty string, the value is inferred from the project:
   * 1) If this is gradle project, values in gradle.build file will be used.
   * 2) If this is non-gradle project, the first instrumentation in AndroidManifest of the instrumentation APK (not the application APK)
   *    will be used.
   *
   * TODO(b/37132226): This property is currently ignored in gradle project. Fix it with revised UI.
   */
  @NotNull public String INSTRUMENTATION_RUNNER_CLASS = "";

  /**
   * An extra instrumentation runner options. If this is an empty string, the value is inferred from the project:
   * 1) If this is gradle project, values in gradle.build file will be used.
   * 2) If this is non-gradle project, no extra options will be set.
   *
   * TODO(b/37132226): This property is currently ignored in gradle project. Fix it with revised UI.
   */
  @NotNull public String EXTRA_OPTIONS = "";

  public AndroidTestRunConfiguration(final Project project, final ConfigurationFactory factory) {
    super(project, factory, true);

    putUserData(BaseAction.SHOW_APPLY_CHANGES_UI, true);
  }

  @Override
  protected Pair<Boolean, String> supportsRunningLibraryProjects(@NotNull AndroidFacet facet) {
    if (!facet.requiresAndroidModel()) {
      // Non Gradle projects always require an application
      return Pair.create(Boolean.FALSE, AndroidBundle.message("android.cannot.run.library.project.error"));
    }

    // TODO: Resolve direct AndroidGradleModel dep (b/22596984)
    AndroidModuleModel androidModel = AndroidModuleModel.get(facet);
    if (androidModel == null) {
      return Pair.create(Boolean.FALSE, AndroidBundle.message("android.cannot.run.library.project.error"));
    }

    // Gradle only supports testing against a single build type (which could be anything, but is "debug" build type by default)
    // Currently, the only information the model exports that we can use to detect whether the current build type
    // is testable is by looking at the test task name and checking whether it is null.
    AndroidArtifact testArtifact = androidModel.getSelectedVariant().getAndroidTestArtifact();
    String testTask = testArtifact != null ? testArtifact.getAssembleTaskName() : null;
    return new Pair<>(testTask != null, AndroidBundle.message("android.cannot.run.library.project.in.this.buildtype"));
  }

  @Override
  public boolean isGeneratedName() {
    return Comparing.equal(getName(), suggestedName());
  }

  @Override
  public String suggestedName() {
    if (TESTING_TYPE == TEST_ALL_IN_PACKAGE) {
      return ExecutionBundle.message("test.in.scope.presentable.text", PACKAGE_NAME);
    } else if (TESTING_TYPE == TEST_CLASS) {
      return ProgramRunnerUtil.shortenName(JavaExecutionUtil.getShortClassName(CLASS_NAME), 0);
    } else if (TESTING_TYPE == TEST_METHOD) {
      return ProgramRunnerUtil.shortenName(METHOD_NAME, 2) + "()";
    }
    return ExecutionBundle.message("all.tests.scope.presentable.text");
  }

  @NotNull
  @Override
  public List<ValidationError> checkConfiguration(@NotNull AndroidFacet facet) {
    return checkConfiguration(facet, AndroidModuleModel.get(facet.getModule()));
  }

  @NotNull
  @VisibleForTesting
  List<ValidationError> checkConfiguration(@NotNull AndroidFacet facet, @Nullable AndroidModuleModel androidModel) {
    List<ValidationError> errors = Lists.newArrayList();

    Module module = facet.getModule();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(module.getProject());
    switch (TESTING_TYPE) {
      case TEST_ALL_IN_PACKAGE:
        final PsiPackage testPackage = facade.findPackage(PACKAGE_NAME);
        if (testPackage == null) {
          errors.add(ValidationError.warning(ExecutionBundle.message("package.does.not.exist.error.message", PACKAGE_NAME)));
        }
        break;
      case TEST_CLASS:
        PsiClass testClass = null;
        try {
          testClass =
            getConfigurationModule().checkModuleAndClassName(CLASS_NAME, ExecutionBundle.message("no.test.class.specified.error.text"));
        } catch (RuntimeConfigurationException e) {
          errors.add(ValidationError.fromException(e));
        }
        if (testClass != null && !JUnitUtil.isTestClass(testClass)) {
          errors.add(ValidationError.warning(ExecutionBundle.message("class.isnt.test.class.error.message", CLASS_NAME)));
        }
        break;
      case TEST_METHOD:
        errors.addAll(checkTestMethod());
        break;
    }

    final AndroidFacetConfiguration configuration = facet.getConfiguration();
    if (!facet.requiresAndroidModel() && !configuration.getState().PACK_TEST_CODE) {
      final int count = getTestSourceRootCount(module);
      if (count > 0) {
        final String shortMessage = "Test code not included into APK";
        final String fixMessage = "Code and resources under test source " + (count > 1 ? "roots" : "root") +
                                  " aren't included into debug APK.\nWould you like to include them and recompile " +
                                  module.getName() + " module?" + "\n(You may change this option in Android facet settings later)";
        Runnable quickFix = () -> {
          final int result =
            Messages.showYesNoCancelDialog(getProject(), fixMessage, shortMessage, Messages.getQuestionIcon());
          if (result == Messages.YES) {
            configuration.getState().PACK_TEST_CODE = true;
          }
        };
        errors.add(ValidationError.fatal(shortMessage, quickFix));
      }
    }

    if (androidModel != null) {
      IdeAndroidArtifact testArtifact = androidModel.getArtifactForAndroidTest();
      if (testArtifact == null) {
        IdeVariant selectedVariant = androidModel.getSelectedVariant();
        errors.add(ValidationError.warning("Active build variant \"" + selectedVariant.getName() + "\" does not have a test artifact."));
      }
    }

    return errors;
  }

  @Override
  @NotNull
  protected ApkProvider getApkProvider(@NotNull AndroidFacet facet,
                                       @NotNull ApplicationIdProvider applicationIdProvider,
                                       @NotNull List<AndroidDevice> targetDevices) {
    if (facet.getConfiguration().getModel() != null && facet.getConfiguration().getModel() instanceof AndroidModuleModel) {
      return createGradleApkProvider(facet, applicationIdProvider, true, targetDevices);
    }
    return new NonGradleApkProvider(facet, applicationIdProvider, null);
  }

  private static int getTestSourceRootCount(@NotNull Module module) {
    final ModuleRootManager manager = ModuleRootManager.getInstance(module);
    return manager.getSourceRoots(true).length - manager.getSourceRoots(false).length;
  }

  private List<ValidationError> checkTestMethod() {
    JavaRunConfigurationModule configurationModule = getConfigurationModule();
    final PsiClass testClass;
    try {
      testClass = configurationModule.checkModuleAndClassName(CLASS_NAME, ExecutionBundle.message("no.test.class.specified.error.text"));
    } catch (RuntimeConfigurationException e) {
      // We can't proceed without a test class.
      return ImmutableList.of(ValidationError.fromException(e));
    }
    List<ValidationError> errors = Lists.newArrayList();
    if (!JUnitUtil.isTestClass(testClass)) {
      errors.add(ValidationError.warning(ExecutionBundle.message("class.isnt.test.class.error.message", CLASS_NAME)));
    }
    if (isEmptyOrSpaces(METHOD_NAME)) {
      errors.add(ValidationError.fatal(ExecutionBundle.message("method.name.not.specified.error.message")));
    }
    final JUnitUtil.TestMethodFilter filter = new JUnitUtil.TestMethodFilter(testClass);
    boolean found = false;
    boolean testAnnotated = false;
    for (final PsiMethod method : testClass.findMethodsByName(METHOD_NAME, true)) {
      if (filter.value(method)) found = true;
      if (JUnitUtil.isTestAnnotated(method)) testAnnotated = true;
    }
    if (!found) {
      errors.add(ValidationError.warning(ExecutionBundle.message("test.method.doesnt.exist.error.message", METHOD_NAME)));
    }

    if (!AnnotationUtil.isAnnotated(testClass, JUnitUtil.RUN_WITH, CHECK_HIERARCHY) && !testAnnotated) {
      try {
        final PsiClass testCaseClass = JUnitUtil.getTestCaseClass(configurationModule.getModule());
        if (!testClass.isInheritor(testCaseClass, true)) {
          errors.add(ValidationError.fatal(ExecutionBundle.message("class.isnt.inheritor.of.testcase.error.message", CLASS_NAME)));
        }
      } catch (JUnitUtil.NoJUnitException e) {
        errors.add(ValidationError.warning(ExecutionBundle.message(AndroidBundle.message("cannot.find.testcase.error"))));
      }
    }
    return errors;
  }

  @NotNull
  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new AndroidRunConfigurationEditor<>(
      getProject(),
      facet -> facet != null && supportsRunningLibraryProjects(facet).getFirst(),
      this,
      false,
      moduleSelector -> new TestRunParameters(getProject(), moduleSelector));
  }

  @NotNull
  @Override
  protected ConsoleProvider getConsoleProvider() {
    return (parent, handler, executor) -> {
      AndroidTestConsoleProperties properties = new AndroidTestConsoleProperties(this, executor);
      ConsoleView consoleView = SMTestRunnerConnectionUtil.createAndAttachConsole("Android", handler, properties);
      Disposer.register(parent, consoleView);
      return consoleView;
    };
  }

  @Override
  protected boolean supportMultipleDevices() {
    return false;
  }

  @Override
  public boolean monitorRemoteProcess() {
    // Tests are run using the "am instrument" command. The output from the shell command is processed by AndroidTestListener,
    // which sends events over to the test UI via GeneralToSMTRunnerEventsConvertor.
    // If the process handler detects that the test process has terminated before all of the output from that shell process
    // makes its way through the AndroidTestListener, the test UI marks the test run as having "Terminated" instead of terminating
    // gracefully once all the test results have been parsed.
    // As a result, we don't want the process handler monitoring the test process at all in this case..
    // See https://code.google.com/p/android/issues/detail?id=201968
    return false;
  }

  @Nullable
  @Override
  protected LaunchTask getApplicationLaunchTask(@NotNull ApplicationIdProvider applicationIdProvider,
                                                @NotNull AndroidFacet facet,
                                                @NotNull String contributorsAmStartOptions,
                                                boolean waitForDebugger,
                                                @NotNull LaunchStatus launchStatus) {
    String runner = getInstrumentationRunner(facet);
    if (isEmptyOrSpaces(runner)) {
      launchStatus.terminateLaunch("Unable to determine instrumentation runner", true);
      return null;
    }

    String extraInstrumentationOptions = getExtraInstrumentationOptions(facet);

    String testAppId;
    try {
      testAppId = applicationIdProvider.getTestPackageName();
      if (testAppId == null) {
        launchStatus.terminateLaunch("Unable to determine test package name", true);
        return null;
      }
    } catch (ApkProvisionException e) {
      launchStatus.terminateLaunch("Unable to determine test package name", true);
      return null;
    }

    AndroidModuleModel moduleModel = AndroidModuleModel.get(facet);
    IdeAndroidArtifact testArtifact = null;
    if (moduleModel != null) {
      testArtifact = moduleModel.getArtifactForAndroidTest();
    }

    switch (TESTING_TYPE) {
      case TEST_ALL_IN_MODULE:
        return AndroidTestApplicationLaunchTask.allInModuleTest(runner,
                                                                testAppId,
                                                                waitForDebugger,
                                                                extraInstrumentationOptions,
                                                                testArtifact);
      case TEST_ALL_IN_PACKAGE:
        return AndroidTestApplicationLaunchTask.allInPackageTest(runner,
                                                                 testAppId,
                                                                 waitForDebugger,
                                                                 extraInstrumentationOptions,
                                                                 testArtifact,
                                                                 PACKAGE_NAME);

      case TEST_CLASS:
        return AndroidTestApplicationLaunchTask.classTest(runner,
                                                          testAppId,
                                                          waitForDebugger,
                                                          extraInstrumentationOptions,
                                                          testArtifact,
                                                          CLASS_NAME);

      case TEST_METHOD:
        return AndroidTestApplicationLaunchTask.methodTest(runner,
                                                           testAppId,
                                                           waitForDebugger,
                                                           extraInstrumentationOptions,
                                                           testArtifact,
                                                           CLASS_NAME,
                                                           METHOD_NAME);

      default:
        launchStatus.terminateLaunch("Unknown testing type is selected", true);
        return null;
    }
  }

  /**
   * Returns the qualified class name of instrumentation runner class to be used.
   *
   * @see #INSTRUMENTATION_RUNNER_CLASS
   */
  @NotNull
  private String getInstrumentationRunner(@NotNull AndroidFacet facet) {
    if (isNotEmpty(INSTRUMENTATION_RUNNER_CLASS) &&
        // TODO(b/37132226): This property is currently ignored in gradle project. Fix it with revised UI.
        !GradleProjectInfo.getInstance(getProject()).isBuildWithGradle()) {
      return INSTRUMENTATION_RUNNER_CLASS;
    }

    AndroidModuleModel androidModel = AndroidModuleModel.get(facet);
    if (androidModel != null) {
      // When a project is a gradle based project, instrumentation runner is always specified
      // by AGP DSL (even if you have androidTest/AndroidManifest.xml with instrumentation tag,
      // these values are always overwritten by AGP).
      String runner = androidModel.getSelectedVariant().getMergedFlavor().getTestInstrumentationRunner();
      if (isEmptyOrSpaces(runner)) {
        return DEFAULT_INSTRUMENTATION_RUNNER_CLASS_IN_AGP;
      } else {
        return runner;
      }
    } else {
      // For non-Gradle project, first check user provided instrumentation runner then fallback to AndroidManifest.
      if (!isEmptyOrSpaces(INSTRUMENTATION_RUNNER_CLASS)) {
        return INSTRUMENTATION_RUNNER_CLASS;
      }
      return DumbService.getInstance(facet.getModule().getProject()).runReadActionInSmartMode(() -> {
        Manifest manifest = facet.getManifest();
        if (manifest == null) {
          return "";
        }
        for (Instrumentation instrumentation : manifest.getInstrumentations()) {
          if (instrumentation != null) {
            PsiClass instrumentationClass = instrumentation.getInstrumentationClass().getValue();
            if (instrumentationClass != null) {
              return instrumentationClass.getQualifiedName();
            }
          }
        }
        return "";
      });
    }
  }

  /**
   * Returns the extra options string to be passed to the instrumentation runner.
   *
   * @see #EXTRA_OPTIONS
   */
  @NotNull
  private String getExtraInstrumentationOptions(@NotNull AndroidFacet facet) {
    if (isNotEmpty(EXTRA_OPTIONS) &&
        // TODO(b/37132226): This property is currently ignored in gradle project. Fix it with revised UI.
        !GradleProjectInfo.getInstance(getProject()).isBuildWithGradle()) {
      return EXTRA_OPTIONS;
    }

    AndroidModuleModel androidModel = AndroidModuleModel.get(facet);
    if (androidModel != null) {
      return androidModel.getSelectedVariant().getMergedFlavor().getTestInstrumentationRunnerArguments().entrySet().stream()
        .map(entry -> "-e " + entry.getKey() + " " + entry.getValue())
        .collect(Collectors.joining(" "));
    }

    return "";
  }

  /**
   * Returns a refactoring listener that listens to changes in either the package, class or method names
   * depending on the current {@link #TESTING_TYPE}.
   */
  @Nullable
  @Override
  public RefactoringElementListener getRefactoringElementListener(PsiElement element) {
    if (element instanceof PsiPackage) {
      String pkgName = ((PsiPackage)element).getQualifiedName();
      if (TESTING_TYPE == TEST_ALL_IN_PACKAGE && !Objects.equals(pkgName, PACKAGE_NAME)) {
        // testing package, but the refactored package does not match our package
        return null;
      } else if ((TESTING_TYPE != TEST_ALL_IN_PACKAGE) && !Objects.equals(pkgName, getPackageName(CLASS_NAME))) {
        // testing a class or a method, but the refactored package doesn't match our containing package
        return null;
      }

      return new RefactoringElementAdapter() {
        @Override
        protected void elementRenamedOrMoved(@NotNull PsiElement newElement) {
          if (newElement instanceof PsiPackage) {
            String newPkgName = ((PsiPackage)newElement).getQualifiedName();
            if (TESTING_TYPE == TEST_ALL_IN_PACKAGE) {
              PACKAGE_NAME = newPkgName;
            } else {
              CLASS_NAME = CLASS_NAME.replace(getPackageName(CLASS_NAME), newPkgName);
            }
          }
        }

        @Override
        public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
          if (newElement instanceof PsiPackage) {
            if (TESTING_TYPE == TEST_ALL_IN_PACKAGE) {
              PACKAGE_NAME = oldQualifiedName;
            } else {
              CLASS_NAME = CLASS_NAME.replace(getPackageName(CLASS_NAME), oldQualifiedName);
            }
          }
        }
      };
    } else if ((TESTING_TYPE == TEST_CLASS || TESTING_TYPE == TEST_METHOD) && element instanceof PsiClass) {
      if (!StringUtil.equals(JavaExecutionUtil.getRuntimeQualifiedName((PsiClass)element), CLASS_NAME)) {
        return null;
      }

      return new RefactoringElementAdapter() {
        @Override
        protected void elementRenamedOrMoved(@NotNull PsiElement newElement) {
          if (newElement instanceof PsiClass) {
            CLASS_NAME = JavaExecutionUtil.getRuntimeQualifiedName((PsiClass)newElement);
          }
        }

        @Override
        public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
          if (newElement instanceof PsiClass) {
            CLASS_NAME = oldQualifiedName;
          }
        }
      };
    } else if (TESTING_TYPE == TEST_METHOD && element instanceof PsiMethod) {
      PsiMethod psiMethod = (PsiMethod)element;
      if (!StringUtil.equals(psiMethod.getName(), METHOD_NAME)) {
        return null;
      }

      PsiClass psiClass = psiMethod.getContainingClass();
      if (psiClass == null) {
        return null;
      }

      String fqName = psiClass.getQualifiedName();
      if (fqName != null && !StringUtil.equals(fqName, CLASS_NAME)) {
        return null;
      }

      return new RefactoringElementAdapter() {
        @Override
        protected void elementRenamedOrMoved(@NotNull PsiElement newElement) {
          if (newElement instanceof PsiMethod) {
            METHOD_NAME = ((PsiMethod)newElement).getName();
          }
        }

        @Override
        public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
          if (newElement instanceof PsiMethod) {
            METHOD_NAME = oldQualifiedName;
          }
        }
      };
    }
    return null;
  }

  @NotNull
  @Override
  protected LaunchOptions.Builder getLaunchOptions() {
    // `am instrument` force stops the target package anyway, so there's no need for an explicit `am force-stop` for every APK involved.
    return super.getLaunchOptions().setForceStopRunningApp(false);
  }
}
