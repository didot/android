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
package com.android.tools.idea.gradle.service.notification.hyperlink;

import com.android.SdkConstants;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.android.SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION;

public class FixAndroidGradlePluginVersionHyperlink extends NotificationHyperlink {
  @NotNull private final GradleVersion myPluginVersion;
  @Nullable private final GradleVersion myGradleVersion;

  /**
   * Creates a new {@link FixAndroidGradlePluginVersionHyperlink}. This constructor updates the Gradle model to the version in
   * {@link SdkConstants#GRADLE_PLUGIN_RECOMMENDED_VERSION} and Gradle to the version in {@link SdkConstants#GRADLE_LATEST_VERSION}.
   *
   */
  public FixAndroidGradlePluginVersionHyperlink() {
    this(GradleVersion.parse(GRADLE_PLUGIN_RECOMMENDED_VERSION), GradleVersion.parse(GRADLE_LATEST_VERSION));
  }

  /**
   * Creates a new {@link FixAndroidGradlePluginVersionHyperlink}.
   *  @param pluginVersion  the version to update the Android Gradle plugin to.
   * @param gradleVersion the version of Gradle to update to. This can be {@code null} if only the model version needs to be updated.
   */
  public FixAndroidGradlePluginVersionHyperlink(@NotNull GradleVersion pluginVersion, @Nullable GradleVersion gradleVersion) {
    super("fixGradleElements", "Fix plugin version and sync project");
    myPluginVersion = pluginVersion;
    myGradleVersion = gradleVersion;
  }

  @Override
  public void execute(@NotNull Project project) {
    AndroidPluginVersionUpdater updater = AndroidPluginVersionUpdater.getInstance(project);
    updater.updatePluginVersionAndSync(myPluginVersion, myGradleVersion, false);
  }
}
