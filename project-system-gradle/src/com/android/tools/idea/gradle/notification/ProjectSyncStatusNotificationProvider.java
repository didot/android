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
package com.android.tools.idea.gradle.notification;

import static com.android.tools.idea.gradle.util.AndroidProjectUtilKt.isAndroidProject;
import static com.android.utils.BuildScriptUtil.isDefaultGradleBuildFile;
import static com.android.utils.BuildScriptUtil.isGradleSettingsFile;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_USER_STALE_CHANGES;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_USER_TRY_AGAIN;
import static com.intellij.openapi.module.ModuleUtilCore.findModuleForFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.ThreeState.YES;

import com.android.annotations.concurrency.AnyThread;
import com.android.repository.Revision;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.GradleVersionCatalogDetector;
import com.android.tools.idea.gradle.project.sync.GradleFiles;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.idea.AndroidGradleProjectResolverKeys;
import com.android.tools.idea.projectsystem.AndroidProjectSettingsService;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.build.BuildContentManager;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.ThreeState;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.awt.Color;
import java.io.File;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Notifies users that a Gradle project "sync" is required (because of changes to build files, or because the last attempt failed) or
 * in progress; if no sync is required or active, displays hints and/or diagnostics about editing the Project Structure.
 */
public class ProjectSyncStatusNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {
  @NotNull private static final Key<EditorNotificationPanel> KEY = Key.create("android.gradle.sync.status");

  @NotNull private final GradleProjectInfo myProjectInfo;
  @NotNull private final GradleSyncState mySyncState;
  @NotNull private final GradleVersionCatalogDetector myVersionCatalogDetector;

  @SuppressWarnings("unused") // Invoked by IDEA
  public ProjectSyncStatusNotificationProvider(@NotNull Project project) {
    this(GradleProjectInfo.getInstance(project), GradleSyncState.getInstance(project), GradleVersionCatalogDetector.getInstance(project));
  }

  @NonInjectable
  public ProjectSyncStatusNotificationProvider(@NotNull GradleProjectInfo projectInfo,
                                               @NotNull GradleSyncState syncState,
                                               @NotNull GradleVersionCatalogDetector versionCatalogDetector) {
    myProjectInfo = projectInfo;
    mySyncState = syncState;
    myVersionCatalogDetector = versionCatalogDetector;
  }

  @Override
  @NotNull
  public final Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @AnyThread
  @Override
  @Nullable
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor editor, @NotNull Project project) {
    NotificationPanel.Type newPanelType = notificationPanelType(project);
    return newPanelType.create(project, file, myProjectInfo);
  }

  @NotNull
  private NotificationPanel.Type notificationPanelType(@NotNull Project project) {
    if (IdeInfo.getInstance().isAndroidStudio() || isAndroidProject(project)) {
      return notificationPanelType();
    } else {
      return NotificationPanel.Type.NONE;
    }
  }

  @VisibleForTesting
  @NotNull
  NotificationPanel.Type notificationPanelType() {
    if (!myProjectInfo.isBuildWithGradle()) {
      return NotificationPanel.Type.NONE;
    }
    if (mySyncState.isSyncInProgress()) {
      return NotificationPanel.Type.IN_PROGRESS;
    }
    if (mySyncState.lastSyncFailed()) {
      return NotificationPanel.Type.FAILED;
    }

    ThreeState gradleSyncNeeded = mySyncState.isSyncNeeded();
    if (gradleSyncNeeded == YES) {
      return NotificationPanel.Type.SYNC_NEEDED;
    }

    if (myVersionCatalogDetector.isVersionCatalogProject()) {
      return NotificationPanel.Type.COMPLICATED_PROJECT;
    }

    return NotificationPanel.Type.PROJECT_STRUCTURE;
  }

  @VisibleForTesting
  static class NotificationPanel extends EditorNotificationPanel {
    enum Type {
      NONE() {
        @Override
        @Nullable NotificationPanel create(@NotNull Project project, @NotNull VirtualFile file, @NotNull GradleProjectInfo projectInfo) {
          return null;
        }
      },
      COMPLICATED_PROJECT() {
        @Override
        @Nullable NotificationPanel create(@NotNull Project project, @NotNull VirtualFile file, @NotNull GradleProjectInfo projectInfo) {
          if (!IdeInfo.getInstance().isAndroidStudio()) return null;
          if (ComplicatedProjectNotificationPanel.userAllowsShow(project)) {
            File ioFile = virtualToIoFile(file);
            if (!isDefaultGradleBuildFile(ioFile) && !isGradleSettingsFile(ioFile) && !ioFile.getName().endsWith("versions.toml")) {
              return null;
            }
            return new ComplicatedProjectNotificationPanel(project, this);
          }
          return PROJECT_STRUCTURE.create(project, file, projectInfo);
        }
      },
      PROJECT_STRUCTURE() {
        @Override
        @Nullable
        NotificationPanel create(@NotNull Project project, @NotNull VirtualFile file, @NotNull GradleProjectInfo projectInfo) {
          if (!IdeInfo.getInstance().isAndroidStudio()) return null;
          if (ProjectStructureNotificationPanel.userAllowsShow()) {
            File ioFile = virtualToIoFile(file);
            if (!isDefaultGradleBuildFile(ioFile) && !isGradleSettingsFile(ioFile)) {
              return null;
            }

            Module module = findModuleForFile(file, project);
            if (module == null) {
              if (ApplicationManager.getApplication().isUnitTestMode()) {
                module = ModuleManager.getInstance(project).getModules()[0]; // arbitrary module
              }
              else {
                return null;
              }
            }
            return new ProjectStructureNotificationPanel(project, this, module);
          }
          return null;
        }
      },
      IN_PROGRESS() {
        @Override
        @NotNull
        NotificationPanel create(@NotNull Project project, @NotNull VirtualFile file, @NotNull GradleProjectInfo projectInfo) {
          return new NotificationPanel(this, "Gradle project sync in progress...");
        }
      },
      FAILED() {
        @Override
        @NotNull
        NotificationPanel create(@NotNull Project project, @NotNull VirtualFile file, @NotNull GradleProjectInfo projectInfo) {
          String text = "Gradle project sync failed. Basic functionality (e.g. editing, debugging) will not work properly.";
          return new SyncProblemNotificationPanel(project, this, text);
        }
      },
      SYNC_NEEDED() {
        @Override
        @NotNull
        NotificationPanel create(@NotNull Project project, @NotNull VirtualFile file, @NotNull GradleProjectInfo projectInfo) {
          boolean buildFilesModified = GradleFiles.getInstance(project).areExternalBuildFilesModified();
          String text = (buildFilesModified ? "External build files" : "Gradle files") +
                        " have changed since last project sync. A project sync may be necessary for the IDE to work properly.";
          return new StaleGradleModelNotificationPanel(project, this, text);
        }
      };

      @Nullable
      abstract NotificationPanel create(@NotNull Project project, @NotNull VirtualFile file, @NotNull GradleProjectInfo projectInfo);
    }

    @NotNull private final Type type;

    NotificationPanel(@NotNull Type type, @NotNull String text) {
      super(JBUI.CurrentTheme.Banner.WARNING_BACKGROUND, Status.Info);
      this.type = type;
      setText(text);
    }
  }

  @VisibleForTesting
  static class StaleGradleModelNotificationPanel extends NotificationPanel {
    StaleGradleModelNotificationPanel(@NotNull Project project, @NotNull Type type, @NotNull String text) {
      super(type, text);
      if (GradleFiles.getInstance(project).areExternalBuildFilesModified()) {
        // Set this to true so that the request sent to gradle daemon contains arg -Pandroid.injected.refresh.external.native.model=true,
        // which would refresh the C++ project. See com.android.tools.idea.gradle.project.sync.common.CommandLineArgs for related logic.
        project.putUserData(AndroidGradleProjectResolverKeys.REFRESH_EXTERNAL_NATIVE_MODELS_KEY, true);
      }
      createActionLabel("Sync Now",
                        () -> GradleSyncInvoker.getInstance()
                          .requestProjectSync(project, new GradleSyncInvoker.Request(TRIGGER_USER_STALE_CHANGES), null));
      createActionLabel("Ignore these changes", () -> {
        GradleFiles.getInstance(project).removeChangedFiles();
        this.setVisible(false);
      });
    }
  }

  @VisibleForTesting
  static class SyncProblemNotificationPanel extends NotificationPanel {
    SyncProblemNotificationPanel(@NotNull Project project, @NotNull Type type, @NotNull String text) {
      super(type, text);

      createActionLabel("Try Again",
                        () -> GradleSyncInvoker.getInstance()
                          .requestProjectSync(project, new GradleSyncInvoker.Request(TRIGGER_USER_TRY_AGAIN), null));

      createActionLabel("Open 'Build' View", () -> {
        ToolWindow tw = BuildContentManager.getInstance(project).getOrCreateToolWindow();
        if (tw != null && !tw.isActive()) {
          tw.activate(null, false);
        }
      });

      createActionLabel("Show Log in " + RevealFileAction.getFileManagerName(), () -> {
        File logFile = new File(PathManager.getLogPath(), "idea.log");
        RevealFileAction.openFile(logFile);
      });
    }
  }

  @VisibleForTesting
  static class ComplicatedProjectNotificationPanel extends NotificationPanel {
    private static final String TEXT = "Project uses Gradle Version Catalogs: some editor tools may not work as expected";

    ComplicatedProjectNotificationPanel(@NotNull Project project, @NotNull Type type) {
      super(type, TEXT);
      createActionLabel("Hide notification", () -> {
        String version = ApplicationInfo.getInstance().getShortVersion();
        PropertiesComponent.getInstance(project).setValue("PROJECT_COMPLICATED_NOTIFICATION_LAST_HIDDEN_VERSION", version);
        setVisible(false);
      });
    }

    static boolean userAllowsShow(@NotNull Project project) {
      String lastHiddenValue = PropertiesComponent.getInstance(project).getValue("PROJECT_COMPLICATED_NOTIFICATION_LAST_HIDDEN_VERSION", "0.0");
      Revision revision = Revision.safeParseRevision(lastHiddenValue);
      return revision.compareTo(Revision.safeParseRevision(ApplicationInfo.getInstance().getShortVersion())) < 0;
    }
  }

  @VisibleForTesting
  static class ProjectStructureNotificationPanel extends NotificationPanel {
    private static final String TEXT = "You can use the Project Structure dialog to view and edit your project configuration";
    private static final long RESHOW_TIMEOUT_MS = TimeUnit.DAYS.toMillis(30);

    ProjectStructureNotificationPanel(@NotNull Project project, @NotNull Type type, @NotNull Module module) {
      super(type, TEXT);

      String shortcutText = KeymapUtil.getFirstKeyboardShortcutText("ShowProjectStructureSettings");
      String label = "Open";
      if (!"".equals(shortcutText)) {
        label += " (" + shortcutText + ")";
      }
      createActionLabel(label, () -> {
        ProjectSettingsService projectSettingsService = ProjectSettingsService.getInstance(project);
        if (projectSettingsService instanceof AndroidProjectSettingsService) {
          projectSettingsService.openModuleSettings(module);
        }
      });
      createActionLabel("Hide notification", () -> {
        PropertiesComponent.getInstance().setValue("PROJECT_STRUCTURE_NOTIFICATION_LAST_HIDDEN_TIMESTAMP",
                                                   Long.toString(System.currentTimeMillis()));
        setVisible(false);
      });
    }

    @NotNull
    @Override
    public Color getFallbackBackgroundColor() {
      return UIUtil.getPanelBackground();
    }

    static boolean userAllowsShow() {
      long now = System.currentTimeMillis();
      String lastHiddenValue = PropertiesComponent.getInstance().getValue("PROJECT_STRUCTURE_NOTIFICATION_LAST_HIDDEN_TIMESTAMP", "0");
      long lastHidden = Long.parseLong(lastHiddenValue);
      return (now - lastHidden) > RESHOW_TIMEOUT_MS;
    }
  }
}
