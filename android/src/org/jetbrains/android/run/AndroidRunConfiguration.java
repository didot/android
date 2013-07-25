/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android.run;

import com.android.SdkConstants;
import com.android.ddmlib.*;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.listeners.RefactoringElementAdapter;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.manifest.Activity;
import org.jetbrains.android.dom.manifest.IntentFilter;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

import static com.intellij.execution.process.ProcessOutputTypes.STDERR;
import static com.intellij.execution.process.ProcessOutputTypes.STDOUT;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidRunConfiguration extends AndroidRunConfigurationBase implements RefactoringListenerProvider {
  @NonNls public static final String LAUNCH_DEFAULT_ACTIVITY = "default_activity";
  @NonNls public static final String LAUNCH_SPECIFIC_ACTIVITY = "specific_activity";
  @NonNls public static final String DO_NOTHING = "do_nothing";

  public String ACTIVITY_CLASS = "";
  public String MODE = LAUNCH_DEFAULT_ACTIVITY;
  public boolean DEPLOY = true;

  public AndroidRunConfiguration(String name, Project project, ConfigurationFactory factory) {
    super(name, project, factory);
  }

  @Override
  protected void checkConfiguration(@NotNull AndroidFacet facet) throws RuntimeConfigurationException {
    final boolean packageContainMavenProperty = doesPackageContainMavenProperty(facet);
    final JavaRunConfigurationModule configurationModule = getConfigurationModule();
    if (MODE.equals(LAUNCH_SPECIFIC_ACTIVITY)) {
      Project project = configurationModule.getProject();
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      PsiClass activityClass = facade.findClass(AndroidUtils.ACTIVITY_BASE_CLASS_NAME, ProjectScope.getAllScope(project));
      if (activityClass == null) {
        throw new RuntimeConfigurationError(AndroidBundle.message("cant.find.activity.class.error"));
      }
      PsiClass c = configurationModule.checkClassName(ACTIVITY_CLASS, AndroidBundle.message("activity.class.not.specified.error"));
      if (!c.isInheritor(activityClass, true)) {
        throw new RuntimeConfigurationError(AndroidBundle.message("not.activity.subclass.error", ACTIVITY_CLASS));
      }

      if (!packageContainMavenProperty) {
        final Activity activity = AndroidDomUtil.getActivityDomElementByClass(facet.getModule(), c);
        if (activity == null) {
          throw new RuntimeConfigurationError(AndroidBundle.message("activity.not.declared.in.manifest", c.getName()));
        }
        if (!isActivityLaunchable(activity)) {
          throw new RuntimeConfigurationError(AndroidBundle.message("activity.not.launchable.error", AndroidUtils.LAUNCH_ACTION_NAME));
        }
      }
    }
    else if (MODE.equals(LAUNCH_DEFAULT_ACTIVITY)) {
      Manifest manifest = facet.getManifest();
      if (manifest != null) {
        if (packageContainMavenProperty || AndroidUtils.getDefaultActivityName(manifest) != null) return;
      }
      throw new RuntimeConfigurationError(AndroidBundle.message("default.activity.not.found.error"));
    }
  }

  private static boolean doesPackageContainMavenProperty(@NotNull AndroidFacet facet) {
    final Manifest manifest = facet.getManifest();

    if (manifest == null) {
      return false;
    }
    final String aPackage = manifest.getPackage().getStringValue();
    return aPackage != null && aPackage.contains("${");
  }

  @Override
  public AndroidRunningState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    AndroidRunningState state = super.getState(executor, env);
    if (state != null) {
      state.setDeploy(DEPLOY);
    }
    return state;
  }

  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    Project project = getProject();
    AndroidRunConfigurationEditor<AndroidRunConfiguration> editor = new AndroidRunConfigurationEditor<AndroidRunConfiguration>(project);
    editor.setConfigurationSpecificEditor(new ApplicationRunParameters(project, editor.getModuleSelector()));
    return editor;
  }

  @Override
  @Nullable
  public RefactoringElementListener getRefactoringElementListener(PsiElement element) {
    if (element instanceof PsiClass && Comparing.strEqual(((PsiClass)element).getQualifiedName(), ACTIVITY_CLASS, true)) {
      return new RefactoringElementAdapter() {
        @Override
        public void elementRenamedOrMoved(@NotNull PsiElement newElement) {
          ACTIVITY_CLASS = ((PsiClass)newElement).getQualifiedName();
        }

        @Override
        public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
          ACTIVITY_CLASS = oldQualifiedName;
        }
      };
    }
    return null;
  }

  @NotNull
  @Override
  protected ConsoleView attachConsole(AndroidRunningState state, Executor executor) {
    Project project = getConfigurationModule().getProject();
    final TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
    ConsoleView console = builder.getConsole();
    console.attachToProcess(state.getProcessHandler());
    return console;
  }

  @Override
  protected boolean supportMultipleDevices() {
    return true;
  }

  @Nullable
  @Override
  protected AndroidApplicationLauncher getApplicationLauncher(final AndroidFacet facet) {
    return new MyApplicationLauncher() {
      @Nullable
      @Override
      protected String getActivityName(@Nullable ProcessHandler processHandler) {
        return getActivityToLaunch(facet, processHandler);
      }
    };
  }

  @Nullable
  private String getActivityToLaunch(@NotNull final AndroidFacet facet, @Nullable ProcessHandler processHandler) {
    String activityToLaunch = null;

    if (MODE.equals(LAUNCH_DEFAULT_ACTIVITY)) {
      final String defaultActivityName = computeDefaultActivity(facet, processHandler);

      if (defaultActivityName != null) {
        activityToLaunch = defaultActivityName;
      }
      else {
        if (processHandler != null) {
          processHandler.notifyTextAvailable(AndroidBundle.message("default.activity.not.found.error"), STDERR);
        }
        return null;
      }
    }
    else if (MODE.equals(LAUNCH_SPECIFIC_ACTIVITY)) {
      activityToLaunch = ACTIVITY_CLASS;
    }
    if (activityToLaunch != null) {
      final String finalActivityToLaunch = activityToLaunch;

      final String activityRuntimeQName = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        @Override
        public String compute() {
          final GlobalSearchScope scope = facet.getModule().getModuleWithDependenciesAndLibrariesScope(false);
          final PsiClass activityClass = JavaPsiFacade.getInstance(getProject()).findClass(finalActivityToLaunch, scope);

          if (activityClass != null) {
            return JavaExecutionUtil.getRuntimeQualifiedName(activityClass);
          }
          return null;
        }
      });
      if (activityRuntimeQName != null) {
        return activityRuntimeQName;
      }
    }
    return activityToLaunch;
  }

  @Nullable
  private static String computeDefaultActivity(@NotNull final AndroidFacet facet, @Nullable final ProcessHandler processHandler) {
    File manifestCopy = null;
    final VirtualFile manifestVFile;

    try {
      if (facet.getProperties().USE_CUSTOM_COMPILER_MANIFEST) {
        final Pair<File,String> pair = getCopyOfCompilerManifestFile(facet, processHandler);
        manifestCopy = pair != null ? pair.getFirst() : null;
        manifestVFile = manifestCopy != null
                        ? LocalFileSystem.getInstance().findFileByIoFile(manifestCopy)
                        : null;
      }
      else {
        manifestVFile = AndroidRootUtil.getManifestFile(facet);
      }
      return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        @Override
        public String compute() {
          final Manifest manifest = manifestVFile != null
                                    ? AndroidUtils.loadDomElement(facet.getModule(), manifestVFile, Manifest.class)
                                    : null;

          if (manifest == null) {
            if (processHandler != null) {
              processHandler.notifyTextAvailable("Cannot find " + SdkConstants.FN_ANDROID_MANIFEST_XML + " file\n", STDERR);
            }
            return null;
          }
          return AndroidUtils.getDefaultActivityName(manifest);
        }
      });
    }
    finally {
      if (manifestCopy != null) {
        FileUtil.delete(manifestCopy.getParentFile());
      }
    }
  }

  private static boolean isActivityLaunchable(Activity activity) {
    for (IntentFilter filter : activity.getIntentFilters()) {
      if (AndroidDomUtil.containsAction(filter, AndroidUtils.LAUNCH_ACTION_NAME)) {
        return true;
      }
    }
    return false;
  }

  private static abstract class MyApplicationLauncher extends AndroidApplicationLauncher {
    private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.run.AndroidRunConfiguration.MyApplicationLauncher");

    @Nullable
    protected abstract String getActivityName(@Nullable ProcessHandler processHandler);

    @SuppressWarnings({"EnumSwitchStatementWhichMissesCases"})
    @Override
    public boolean isReadyForDebugging(ClientData data, ProcessHandler processHandler) {
      final String activityName = getActivityName(processHandler);
      if (activityName == null) {
        ClientData.DebuggerStatus status = data.getDebuggerConnectionStatus();
        switch (status) {
          case ERROR:
            if (processHandler != null) {
              processHandler.notifyTextAvailable("Debug port is busy\n", STDOUT);
            }
            LOG.info("Debug port is busy");
            return false;
          case ATTACHED:
            if (processHandler != null) {
              processHandler.notifyTextAvailable("Debugger already attached\n", STDOUT);
            }
            LOG.info("Debugger already attached");
            return false;
          default:
            return true;
        }
      }
      return super.isReadyForDebugging(data, processHandler);
    }

    @Override
    public LaunchResult launch(@NotNull AndroidRunningState state, @NotNull IDevice device)
      throws IOException, AdbCommandRejectedException, TimeoutException {
      ProcessHandler processHandler = state.getProcessHandler();
      String activityName = getActivityName(processHandler);
      if (activityName == null) return LaunchResult.NOTHING_TO_DO;
      activityName = activityName.replace("$", "\\$");
      final String activityPath = state.getPackageName() + '/' + activityName;
      if (state.isStopped()) return LaunchResult.STOP;
      processHandler.notifyTextAvailable("Launching application: " + activityPath + ".\n", STDOUT);
      AndroidRunningState.MyReceiver receiver = state.new MyReceiver();
      boolean debug = state.isDebugMode();
      while (true) {
        if (state.isStopped()) return LaunchResult.STOP;
        String command = "am start " +
                         (debug ? "-D " : "") +
                         "-n \"" + activityPath + "\" " +
                         "-a android.intent.action.MAIN " +
                         "-c android.intent.category.LAUNCHER";
        boolean deviceNotResponding = false;
        try {
          state.executeDeviceCommandAndWriteToConsole(device, command, receiver);
        }
        catch (ShellCommandUnresponsiveException e) {
          LOG.info(e);
          deviceNotResponding = true;
        }
        if (!deviceNotResponding && receiver.getErrorType() != 2) {
          break;
        }
        processHandler.notifyTextAvailable("Device is not ready. Waiting for " + AndroidRunningState.WAITING_TIME + " sec.\n", STDOUT);
        synchronized (state.getRunningLock()) {
          try {
            state.getRunningLock().wait(AndroidRunningState.WAITING_TIME * 1000);
          }
          catch (InterruptedException e) {
          }
        }
        receiver = state.new MyReceiver();
      }
      boolean success = receiver.getErrorType() == AndroidRunningState.NO_ERROR;
      if (success) {
        processHandler.notifyTextAvailable(receiver.getOutput().toString(), STDOUT);
      }
      else {
        processHandler.notifyTextAvailable(receiver.getOutput().toString(), STDERR);
      }
      return success ? LaunchResult.SUCCESS : LaunchResult.STOP;
    }
  }
}
