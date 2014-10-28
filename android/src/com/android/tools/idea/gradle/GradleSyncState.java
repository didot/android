/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.android.tools.idea.startup.AndroidStudioSpecificInitializer;
import com.android.tools.idea.stats.StatsKeys;
import com.android.tools.idea.stats.StatsTimeCollector;
import com.android.tools.lint.detector.api.LintUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.ProjectTopics;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.ThreeState;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

public class GradleSyncState {
  private static final Logger LOG = Logger.getInstance(GradleSyncState.class);

  private static final List<String> PROJECT_PREFERENCES_TO_REMOVE = Lists.newArrayList(
    "org.intellij.lang.xpath.xslt.associations.impl.FileAssociationsConfigurable", "com.intellij.uiDesigner.GuiDesignerConfigurable",
    "org.jetbrains.plugins.groovy.gant.GantConfigurable", "org.jetbrains.plugins.groovy.compiler.GroovyCompilerConfigurable",
    "org.jetbrains.android.compiler.AndroidDexCompilerSettingsConfigurable", "org.jetbrains.idea.maven.utils.MavenSettings",
    "com.intellij.compiler.options.CompilerConfigurable"
  );

  public static final Topic<GradleSyncListener> GRADLE_SYNC_TOPIC =
    new Topic<GradleSyncListener>("Project sync with Gradle", GradleSyncListener.class);

  private static final Key<Long> PROJECT_LAST_SYNC_TIMESTAMP_KEY = Key.create("android.gradle.project.last.sync.timestamp");

  @NotNull private final Project myProject;
  @NotNull private final MessageBus myMessageBus;

  private volatile boolean myGradleBasedIdeProjectModificationInProgress;
  private volatile boolean mySyncInProgress;
  private volatile boolean mySyncTransparentChangeInProgress;

  @NotNull
  public static GradleSyncState getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleSyncState.class);
  }

  public GradleSyncState(@NotNull Project project, @NotNull MessageBus messageBus) {
    myProject = project;
    myMessageBus = messageBus;
  }

  public void syncSkipped(long lastSyncTimestamp) {
    cleanUpProjectPreferences();
    setLastGradleSyncTimestamp(lastSyncTimestamp);
    syncPublisher(new Runnable() {
      @Override
      public void run() {
        myMessageBus.syncPublisher(GRADLE_SYNC_TOPIC).syncSkipped(myProject);
      }
    });
  }

  public void syncStarted(boolean notifyUser) {
    cleanUpProjectPreferences();
    StatsTimeCollector.start(StatsKeys.GRADLE_SYNC_PROJECT_TIME_MS);
    mySyncInProgress = true;
    if (notifyUser) {
      notifyUser();
    }
    syncPublisher(new Runnable() {
      @Override
      public void run() {
        myMessageBus.syncPublisher(GRADLE_SYNC_TOPIC).syncStarted(myProject);
      }
    });
  }

  public void syncFailed(@NotNull final String message) {
    syncFinished();
    syncPublisher(new Runnable() {
      @Override
      public void run() {
        myMessageBus.syncPublisher(GRADLE_SYNC_TOPIC).syncFailed(myProject, message);
      }
    });
  }

  public void syncEnded() {
    // Temporary: Clear resourcePrefix flag in case it was set to false when working with
    // an older model. TODO: Remove this when we no longer support models older than 0.10.
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    LintUtils.sTryPrefixLookup = true;

    syncFinished();
    syncPublisher(new Runnable() {
      @Override
      public void run() {
        myMessageBus.syncPublisher(GRADLE_SYNC_TOPIC).syncSucceeded(myProject);
      }
    });
  }

  private void syncFinished() {
    mySyncInProgress = false;
    setLastGradleSyncTimestamp(System.currentTimeMillis());
    StatsTimeCollector.stop(StatsKeys.GRADLE_SYNC_PROJECT_TIME_MS);
    notifyUser();
  }

  private void syncPublisher(@NotNull Runnable publishingTask) {
    AppUIUtil.invokeLaterIfProjectAlive(myProject, publishingTask);
  }

  public void notifyUser() {
    AppUIUtil.invokeLaterIfProjectAlive(myProject, new Runnable() {
      @Override
      public void run() {
        EditorNotifications notifications = EditorNotifications.getInstance(myProject);
        VirtualFile[] files = FileEditorManager.getInstance(myProject).getOpenFiles();
        for (VirtualFile file : files) {
          try {
            notifications.updateNotifications(file);
          }
          catch (Throwable e) {
            String filePath = FileUtil.toSystemDependentName(file.getPath());
            String msg = String.format("Failed to update editor notifications for file '%1$s'", filePath);
            LOG.info(msg, e);
          }
        }

        notifications.updateAllNotifications();
        BuildVariantView.getInstance(myProject).updateContents();
      }
    });
  }

  public boolean isSyncInProgress() {
    return mySyncInProgress;
  }

  private void setLastGradleSyncTimestamp(long timestamp) {
    myProject.putUserData(PROJECT_LAST_SYNC_TIMESTAMP_KEY, timestamp);
  }

  public long getLastGradleSyncTimestamp() {
    Long timestamp = myProject.getUserData(PROJECT_LAST_SYNC_TIMESTAMP_KEY);
    return timestamp != null ? timestamp.longValue() : -1L;
  }

  /**
   * Indicates whether a project sync with Gradle is needed. A Gradle sync is usually needed when a build.gradle or settings.gradle file has
   * been updated <b>after</b> the last project sync was performed.
   *
   * @return {@code YES} if a sync with Gradle is needed, {@code FALSE} otherwise, or {@code UNSURE} If the timestamp of the last Gradle
   * sync cannot be found.
   */
  @NotNull
  public ThreeState isSyncNeeded() {
    if (mySyncTransparentChangeInProgress) {
      return ThreeState.NO;
    }
    long lastSync = getLastGradleSyncTimestamp();
    if (lastSync < 0) {
      // Previous sync may have failed. We don't know if a sync is needed or not. Let client code decide.
      return ThreeState.UNSURE;
    }
    return isSyncNeeded(lastSync) ? ThreeState.YES : ThreeState.NO;
  }

  /**
   * Indicates whether a project sync with Gradle is needed if changes to build.gradle or settings.gradle files were made after the given
   * time.
   *
   * @param referenceTimeInMillis the given time, in milliseconds.
   * @return {@code true} if a sync with Gradle is needed, {@code false} otherwise.
   * @throws AssertionError if the given time is less than or equal to zero.
   */
  public boolean isSyncNeeded(long referenceTimeInMillis) {
    assert referenceTimeInMillis > 0;
    if (mySyncInProgress) {
      return false;
    }

    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    File settingsFilePath = new File(myProject.getBasePath(), SdkConstants.FN_SETTINGS_GRADLE);
    if (settingsFilePath.exists()) {
      VirtualFile settingsFile = VfsUtil.findFileByIoFile(settingsFilePath, true);
      if (settingsFile != null && fileDocumentManager.isFileModified(settingsFile)) {
        return true;
      }
      if (settingsFilePath.lastModified() > referenceTimeInMillis) {
        return true;
      }
    }

    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    for (Module module : moduleManager.getModules()) {
      VirtualFile buildFile = GradleUtil.getGradleBuildFile(module);
      if (buildFile != null) {
        if (fileDocumentManager.isFileModified(buildFile)) {
          return true;
        }

        File buildFilePath = VfsUtilCore.virtualToIoFile(buildFile);
        if (buildFilePath.lastModified() > referenceTimeInMillis) {
          return true;
        }
      }
    }
    return false;
  }

  private void cleanUpProjectPreferences() {
    if (!AndroidStudioSpecificInitializer.isAndroidStudio()) {
      return;
    }
    try {
      ExtensionPoint<ConfigurableEP<Configurable>>
        projectConfigurable = Extensions.getArea(myProject).getExtensionPoint(Configurable.PROJECT_CONFIGURABLE);

      GradleUtil.cleanUpPreferences(projectConfigurable, PROJECT_PREFERENCES_TO_REMOVE);
    }
    catch (Throwable e) {
      String msg = String.format("Failed to clean up preferences for project '%1$s'", myProject.getName());
      LOG.info(msg, e);
    }
  }

  /**
   * There is an API method {@link #isSyncNeeded()} which simply compares <code>'*.gradle'</code> files modification stamp vs
   * last gradle project refresh time and returns the result. I.e. it's assumed to say 'sync is needed' for a situation when
   * one of the <code>'*.gradle'</code> files related to the current project is modified after the last project sync.
   * <p/>
   * However, there is a possible case that we change <code>'*.gradle'</code> config programmatically and want to consider that
   * IDE and gradle projects are synced after that (e.g. when flushing IDE project structure changes into <code>'*.gradle'</code>
   * config).
   * <p/>
   * This method helps with that - it executes given action (assuming that it changes <code>'*.gradle'</code> file(s) internally
   * and updates current state in a way to consider that IDE and gradle projects are synced when the action completes.
   * <p/>
   * <b>Note:</b> it uses that behavior only when IDE and gradle projects are synced <code>before</code> given action execution.
   * It just executes it and doesn't update internal state otherwise.
   *
   * @param action  an action to execute
   */
  public void runSyncTransparentAction(@NotNull Runnable action) {
    boolean canRunActionTransparently = !mySyncTransparentChangeInProgress && isSyncNeeded() == ThreeState.NO;
    if (canRunActionTransparently) {
      mySyncTransparentChangeInProgress = true;
    }
    try {
      action.run();
    }
    finally {
      if (canRunActionTransparently) {
        mySyncTransparentChangeInProgress = false;
        myProject.putUserData(PROJECT_LAST_SYNC_TIMESTAMP_KEY, System.currentTimeMillis());
      }
    }
  }

  /**
   * There is a possible case that IDE project structure is modified by our code. Corresponding 'project modification' events are
   * sent then (see {@link ProjectTopics}). However, we want to differentiate between the changes made by us and all other changes.
   * <p/>
   * E.g. when a user, say, adds new dependency to a module we might try to propagate that change to gradle config files but we don't
   * want to react to project structure changes triggered by gradle integration itself.
   * <p/>
   * The main idea is that given action is executed during the current method call and
   * {@link #isGradleBasedIdeProjectModificationInProgress()} returns <code>true</code> during its execution.
   *
   * @param action  an action to execute
   */
  public void runIdeProjectModificationAction(@NotNull Runnable action) {
    myGradleBasedIdeProjectModificationInProgress = true;
    try {
      action.run();
    }
    finally {
      myGradleBasedIdeProjectModificationInProgress = false;
    }
  }

  /**
   * @return    <code>true</code> if gradle integration-based IDE project modification is in progress; <code>false</code> otherwise
   * @see #runIdeProjectModificationAction(Runnable)
   */
  public boolean isGradleBasedIdeProjectModificationInProgress() {
    return myGradleBasedIdeProjectModificationInProgress;
  }

  @VisibleForTesting
  public void resetTimestamp() {
    setLastGradleSyncTimestamp(-1L);
  }
}
