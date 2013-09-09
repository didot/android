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
import com.android.tools.idea.gradle.service.notification.OpenUrlHyperlink;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.android.tools.idea.startup.AndroidStudioSpecificInitializer;
import com.google.common.collect.Lists;
import com.intellij.ProjectTopics;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.Function;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AndroidGradleProjectComponent extends AbstractProjectComponent {
  private static final Logger LOG = Logger.getInstance(AndroidGradleProjectComponent.class);
  @NonNls private static final String SHOW_MIGRATE_TO_GRADLE_POPUP = "show.migrate.to.gradle.popup";

  @Nullable private Disposable myDisposable;

  public AndroidGradleProjectComponent(Project project) {
    super(project);
    // Register a task that refreshes Studio's view of the file system after a compile.
    // This is necessary for Studio to see generated code.
    CompilerManager.getInstance(project).addAfterTask(new CompileTask() {
      @Override
      public boolean execute(CompileContext context) {
        Project contextProject = context.getProject();
        if (Projects.isGradleProject(contextProject)) {
          String rootDirPath = contextProject.getBasePath();
          VirtualFile rootDir = LocalFileSystem.getInstance().findFileByPath(rootDirPath);
          if (rootDir != null && rootDir.isDirectory()) {
            rootDir.refresh(true, true);
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
    if (shouldShowMigrateToGradleNotification()
        && AndroidStudioSpecificInitializer.isAndroidStudio()
        && Projects.isIdeaAndroidProject(myProject)) {
      // Suggest that Android Studio users use Gradle instead of IDEA project builder
      Notification warning = new MigrateToGradleNotification(myProject).createWarning();
      warning.notify(myProject);
      return;
    }
    if (Projects.isGradleProject(myProject)) {
      configureGradleProject(true);
    }
  }

  private boolean shouldShowMigrateToGradleNotification() {
    return PropertiesComponent.getInstance(myProject).getBoolean(SHOW_MIGRATE_TO_GRADLE_POPUP, true);
  }

  private void disableShowMigrateToGradleNotification() {
    PropertiesComponent.getInstance(myProject).setValue(SHOW_MIGRATE_TO_GRADLE_POPUP, Boolean.FALSE.toString());
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
      Projects.setProjectBuildAction(myProject, Projects.BuildAction.SOURCE_GEN);
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

  private class MigrateToGradleNotification {
    private final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.balloonGroup("IDEA Android project detector");

    private final NotificationHyperlink myMoreInfoHyperlink =
      new OpenUrlHyperlink("http://tools.android.com/tech-docs/new-build-system/intellij_to_gradle",
                           "More Information about migrating to Gradle");

    private final NotificationHyperlink myDoNotShowAgainHyperlink =
      new NotificationHyperlink("do.not.show", "Don't show this message again.") {
        @Override
        protected void execute(@NotNull Project project) {
          disableShowMigrateToGradleNotification();
        }
      };

    private final String myErrorMessage;
    private final NotificationListener myListener;

    MigrateToGradleNotification(@NotNull Project project) {
      myListener = new CustomNotificationListener(project, myMoreInfoHyperlink, myDoNotShowAgainHyperlink);

      // We need both "<br>" and "\n" to separate lines. IDEA will show this message in a balloon (which respects "<br>", and in the
      // 'Event Log' tool window, which respects "\n".)
      myErrorMessage =
        "This project does not use the Gradle build system. We recommend that you migrate to using the Gradle build system.<br>\n" +
        myMoreInfoHyperlink.toString() + "<br>\n" +
        myDoNotShowAgainHyperlink.toString();
    }

    @NotNull
    Notification createWarning() {
      return NOTIFICATION_GROUP.createNotification("Migrate Project to Gradle?",
                                                   myErrorMessage, NotificationType.WARNING, myListener);
    }
  }
}
