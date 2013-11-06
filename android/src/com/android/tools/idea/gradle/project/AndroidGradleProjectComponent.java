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
package com.android.tools.idea.gradle.project;

import com.android.tools.idea.gradle.GradleImportNotificationListener;
import com.android.tools.idea.gradle.service.notification.CustomNotificationListener;
import com.android.tools.idea.gradle.service.notification.NotificationHyperlink;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.android.tools.idea.startup.AndroidStudioSpecificInitializer;
import com.google.common.collect.Lists;
import com.intellij.ProjectTopics;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.Function;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.ArrayList;
import java.util.List;

public class AndroidGradleProjectComponent extends AbstractProjectComponent {
  private static final Logger LOG = Logger.getInstance(AndroidGradleProjectComponent.class);

  @NonNls private static final String SHOW_MIGRATE_TO_GRADLE_POPUP = "show.migrate.to.gradle.popup";

  @Nullable private Disposable myDisposable;

  @NotNull
  public static AndroidGradleProjectComponent getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, AndroidGradleProjectComponent.class);
  }

  public AndroidGradleProjectComponent(Project project) {
    super(project);
    // Register a task that will be executed after project build (e.g. make, rebuild, generate sources.)
    CompilerManager.getInstance(project).addAfterTask(new CompileTask() {
      @Override
      public boolean execute(CompileContext context) {
        Project contextProject = context.getProject();
        if (Projects.isGradleProject(contextProject)) {
          // Refresh Studio's view of the file system after a compile. This is necessary for Studio to see generated code.
          Projects.refresh(contextProject);

          BuildMode buildMode = Projects.getBuildModeFrom(contextProject);
          Projects.removeBuildDataFrom(contextProject);

          if (BuildMode.SOURCE_GEN.equals(buildMode)) {
            // Notify facets after project was synced. This only happens after importing a project.
            // Importing a project means:
            // * Creating a new project
            // * Importing an existing project
            // * Syncing with Gradle files
            // * Opening Studio with an already imported project
            Projects.notifyProjectSyncCompleted(contextProject, true);
          }
        }
        return true;
      }
    });
  }

  /**
   * This method is called when a project is created and when it is opened.
   */
  @Override
  public void projectOpened() {
    checkForSupportedModules();

    if (shouldShowMigrateToGradleNotification()
        && AndroidStudioSpecificInitializer.isAndroidStudio()
        && Projects.isIdeaAndroidProject(myProject)) {
      // Suggest that Android Studio users use Gradle instead of IDEA project builder.
      showMigrateToGradleWarning();
      return;
    }
    if (Projects.isGradleProject(myProject)) {
      configureGradleProject(true);
    }
  }

  private boolean shouldShowMigrateToGradleNotification() {
    return PropertiesComponent.getInstance(myProject).getBoolean(SHOW_MIGRATE_TO_GRADLE_POPUP, true);
  }

  private void showMigrateToGradleWarning() {
    NotificationHyperlink moreInfoHyperlink = new OpenMigrationToGradleUrlHyperlink();
    NotificationHyperlink doNotShowAgainHyperlink = new NotificationHyperlink("do.not.show", "Don't show this message again.") {
      @Override
      protected void execute(@NotNull Project project) {
        PropertiesComponent.getInstance(myProject).setValue(SHOW_MIGRATE_TO_GRADLE_POPUP, Boolean.FALSE.toString());
      }
    };
    NotificationListener notificationListener = new CustomNotificationListener(myProject, moreInfoHyperlink, doNotShowAgainHyperlink);

    // We need both "<br>" and "\n" to separate lines. IDEA will show this message in a balloon (which respects "<br>", and in the
    // 'Event Log' tool window, which respects "\n".)
    String errMsg =
      "This project does not use the Gradle build system. We recommend that you migrate to using the Gradle build system.<br>\n" +
      moreInfoHyperlink.toString() + "<br>\n" +
      doNotShowAgainHyperlink.toString();

    AndroidGradleNotification notification = AndroidGradleNotification.getInstance(myProject);
    notification.showBalloon("Migrate Project to Gradle?", errMsg, NotificationType.WARNING, notificationListener);
  }

  public void configureGradleProject(boolean reImportProject) {
    if (myDisposable != null) {
      return;
    }
    myDisposable = new Disposable() {
      @Override
      public void dispose() {
      }
    };

    listenForProjectChanges(myProject, myDisposable);

    GradleImportNotificationListener.attachToManager();
    Projects.ensureExternalBuildIsEnabledForGradleProject(myProject);

    if (reImportProject) {
      try {
        // Prevent IDEA from refreshing project. We want to do it ourselves.
        myProject.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, Boolean.TRUE);

        GradleProjectImporter.getInstance().reImportProject(myProject, null);
      }
      catch (ConfigurationException e) {
        Messages.showErrorDialog(e.getMessage(), e.getTitle());
        LOG.info(e);
      }
    }
  }

  private static void listenForProjectChanges(@NotNull Project project, @NotNull Disposable disposable) {
    GradleBuildFileUpdater buildFileUpdater = new GradleBuildFileUpdater(project);

    GradleModuleListener moduleListener = new GradleModuleListener();
    moduleListener.addModuleListener(buildFileUpdater);

    MessageBusConnection connection = project.getMessageBus().connect(disposable);
    connection.subscribe(ProjectTopics.MODULES, moduleListener);
    connection.subscribe(VirtualFileManager.VFS_CHANGES, buildFileUpdater);
  }

  @Override
  public void projectClosed() {
    if (myDisposable != null) {
      Disposer.dispose(myDisposable);
    }
  }

  /**
   * Verifies that the project, if it is an Android Gradle project, does not have any modules that are not known by Gradle. For example,
   * when adding a plain IDEA Java module.
   * Do not call this method from {@link ModuleListener#moduleAdded(Project, Module)} because the settings that this method look for are
   * not present when importing a valid Gradle-aware module, resulting in false positives.
   */
  public void checkForSupportedModules() {
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    if (modules.length == 0 || !Projects.isGradleProject(myProject)) {
      return;
    }
    final List<Module> unsupportedModules = new ArrayList<Module>();

    for (Module module : modules) {
      final ModuleType moduleType = ModuleType.get(module);

      if (moduleType instanceof JavaModuleType) {
        final String externalSystemId = module.getOptionValue(ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY);

        if (!GradleConstants.SYSTEM_ID.getId().equals(externalSystemId)) {
          unsupportedModules.add(module);
        }
      }
    }

    if (unsupportedModules.size() == 0) {
      return;
    }
    final String s = StringUtil.join(modules, new Function<Module, String>() {
      @Override
      public String fun(Module module) {
        return module.getName();
      }
    }, ", ");
    AndroidGradleNotification.getInstance(myProject).showBalloon(
      "Unsupported Modules Detected",
      "Compilation is not supported for following modules: " + s +
      ". Unfortunately you can't have non-Gradle Java modules and Android-Gradle modules in one project.",
      NotificationType.ERROR);
  }

  private static class GradleModuleListener implements ModuleListener {
    @NotNull private final List<ModuleListener> additionalListeners = Lists.newArrayList();

    @Override
    public void moduleAdded(Project project, Module module) {
      updateBuildVariantView(project);
      for (ModuleListener listener : additionalListeners) {
        listener.moduleAdded(project, module);
      }
    }

    @Override
    public void beforeModuleRemoved(Project project, Module module) {
      for (ModuleListener listener : additionalListeners) {
        listener.beforeModuleRemoved(project, module);
      }
    }

    @Override
    public void modulesRenamed(Project project, List<Module> modules, Function<Module, String> oldNameProvider) {
      updateBuildVariantView(project);
      for (ModuleListener listener : additionalListeners) {
        listener.modulesRenamed(project, modules, oldNameProvider);
      }
    }

    @Override
    public void moduleRemoved(Project project, Module module) {
      updateBuildVariantView(project);
      for (ModuleListener listener : additionalListeners) {
        listener.moduleRemoved(project, module);
      }
    }

    private static void updateBuildVariantView(@NotNull Project project) {
      BuildVariantView.getInstance(project).updateContents();
    }

    void addModuleListener(@NotNull ModuleListener listener) {
      additionalListeners.add(listener);
    }
  }
}
