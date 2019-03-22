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
package com.android.tools.idea.gradle.project.sync.setup.post.upgrade;

import com.android.annotations.concurrency.Slow;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.plugin.AndroidPluginGeneration;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.setup.post.PluginVersionUpgradeStep;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.SearchInBuildFilesHyperlink;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.android.tools.idea.project.messages.MessageType.ERROR;

public class ForcedPluginVersionUpgradeStep implements PluginVersionUpgradeStep {
  public static final ExtensionPointName<ForcedPluginVersionUpgradeStep>
    EXTENSION_POINT_NAME = ExtensionPointName.create("com.android.gradle.sync.forcedPluginVersionUpgradeStep");

  @NotNull
  public static ForcedPluginVersionUpgradeStep[] getExtensions() {
    return EXTENSION_POINT_NAME.getExtensions();
  }

  @Override
  @Slow
  public boolean checkUpgradable(@NotNull Project project, @NotNull AndroidPluginInfo pluginInfo) {
    AndroidPluginGeneration pluginGeneration = pluginInfo.getPluginGeneration();
    GradleVersion recommended = GradleVersion.parse(pluginGeneration.getLatestKnownVersion());

    return shouldPreviewBeForcedToUpgradePluginVersion(recommended, pluginInfo.getPluginVersion());
  }

  @Override
  @Slow
  public boolean performUpgradeAndSync(@NotNull Project project, @NotNull AndroidPluginInfo pluginInfo) {
    if (!checkUpgradable(project, pluginInfo)) {
      return false;
    }

    AndroidPluginGeneration pluginGeneration = pluginInfo.getPluginGeneration();
    GradleVersion recommended = GradleVersion.parse(pluginGeneration.getLatestKnownVersion());

    GradleSyncState syncState = GradleSyncState.getInstance(project);
    syncState.syncEnded(); // Update the sync state before starting a new one.

    final Ref<Boolean> result = Ref.create();
    final ForcedPluginPreviewVersionUpgradeDialog dialog = new ForcedPluginPreviewVersionUpgradeDialog(project, pluginInfo);
    ApplicationManager.getApplication().invokeAndWait(() -> result.set(dialog.showAndGet()), ModalityState.NON_MODAL);
    boolean userAcceptsForcedUpgrade = result.get();

    if (userAcceptsForcedUpgrade) {
      AndroidPluginVersionUpdater versionUpdater = AndroidPluginVersionUpdater.getInstance(project);
      versionUpdater.updatePluginVersionAndSync(recommended, GradleVersion.parse(GRADLE_LATEST_VERSION), true);
    }
    else {
      String[] text = {
        "The project is using an incompatible version of the " + pluginGeneration.getDescription() + ".",
        "Please update your project to use version " + pluginGeneration.getLatestKnownVersion() + "."
      };
      SyncMessage msg = new SyncMessage(SyncMessage.DEFAULT_GROUP, ERROR, text);

      String pluginName = AndroidPluginGeneration.getGroupId() + GRADLE_PATH_SEPARATOR + pluginGeneration.getArtifactId();
      NotificationHyperlink quickFix = new SearchInBuildFilesHyperlink(pluginName);
      msg.add(quickFix);

      GradleSyncMessages.getInstance(project).report(msg);
      syncState.invalidateLastSync("Force plugin upgrade declined");
    }
    return true;
  }

  @VisibleForTesting
  static boolean shouldPreviewBeForcedToUpgradePluginVersion(@NotNull GradleVersion recommended, @Nullable GradleVersion current) {
    if (current != null) {
      if (current.getPreviewType() != null) {
        if (recommended.isSnapshot() && current.compareIgnoringQualifiers(recommended) == 0) {
          // e.g recommended: 2.3.0-dev and current: 2.3.0-alpha1
          return false;
        }
        if (recommended.isAtLeast(2, 4, 0, "alpha", 8, false)) {
          // 2.4.0-alpha8 introduces many API changes that may break users' builds. Because of this, Studio will allow users to
          // switch to older previews of 2.4.0.
          if (current.compareTo(recommended) >= 0) {
            // The plugin is newer or equal to 2.4.0-alpha8
            return false;
          }

          boolean isOlderPreviewAllowed = current.isPreview() && current.getMajor() == 2 && current.getMinor() == 4 &&
                                          current.compareTo(recommended) < 0;
          return !isOlderPreviewAllowed;
        }
        // current is a "preview" (alpha, beta, etc.)
        return current.compareTo(recommended) < 0;
      }
    }
    return false;
  }
}
