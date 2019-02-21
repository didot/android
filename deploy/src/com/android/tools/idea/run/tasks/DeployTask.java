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
package com.android.tools.idea.run.tasks;

import com.android.ddmlib.IDevice;
import com.android.tools.deployer.Deployer;
import com.android.tools.deployer.DeployerException;
import com.android.tools.deployer.InstallMetric;
import com.android.tools.deployer.InstallOptions;
import com.android.tools.idea.flags.StudioFlags;
import com.google.wireless.android.sdk.stats.LaunchTaskDetail;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class DeployTask extends AbstractDeployTask {

  private static final Logger LOG = Logger.getInstance(DeployTask.class);
  private static final String ID = "DEPLOY";

  private final String userInstallOptions;

  /**
   * Creates a task to deploy a list of apks.
   *
   * @param project  the project that this task is running within.
   * @param packages a map of application ids to apks representing the packages this task will deploy.
   */
  public DeployTask(@NotNull Project project, @NotNull Map<String, List<File>> packages, String userInstallOptions) {
    super(project, packages);
    this.userInstallOptions = userInstallOptions;
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @Override
  protected void perform(IDevice device, Deployer deployer, String applicationId, List<File> files) throws DeployerException {
    InstallOptions.Builder options = InstallOptions.builder().setAllowDebuggable();

    // Embedded devices (Android Things) have all runtime permissions granted since there's no requirement for user
    // interaction/display. However, regular installation will not grant some permissions until the next device reboot.
    // Installing with "-g" guarantees that the permissions are properly granted at install time.
    if (device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)) {
      options.setGrantAllPermissions();
    }

    // API 28 changes how the instant property is set on app install.
    // We can add --full to pmInstallOptions to restore the previous behavior,
    // where an app's instant flag is reset on install, and avoid errors installing
    // a non-instant app over its instant version with the device still treating
    // the app as instant.
    if (device.getVersion().isGreaterOrEqualThan(28)) {
      options.setInstallFullApk();
    }

    // We can just append this, since all these options get string-joined in the end anyways.
    if (userInstallOptions != null) {
      options.setUserInstallOptions(userInstallOptions);
    }

    LOG.info("Installing application: " + applicationId);
    Deployer.InstallMode installMode = Deployer.InstallMode.FULL;
    if (StudioFlags.DELTA_INSTALL.get()) {
        installMode = Deployer.InstallMode.DELTA;
    }

    List<InstallMetric> metrics = deployer.install(applicationId, getPathsToInstall(files), options.build(), installMode);
    for(InstallMetric metric : metrics) {
      LaunchTaskDetail detail = LaunchTaskDetail.newBuilder()
        .setId(getId() + "." + metric.getName())
        .setStartTimestampMs(metric.getStartTimeMs())
        .setEndTimestampMs(metric.getEndTimeMs())
        .setStatus(metric.getStatus())
        .setTid((int)metric.getThreadId())
        .build();
      mySubTaskDetails.add(detail);
    }
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Install";
  }
}