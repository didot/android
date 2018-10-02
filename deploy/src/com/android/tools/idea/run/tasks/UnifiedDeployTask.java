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
import com.android.tools.deploy.swapper.SQLiteDexArchiveDatabase;
import com.android.tools.deployer.AdbClient;
import com.android.tools.deploy.swapper.DexArchiveDatabase;
import com.android.tools.deploy.swapper.InMemoryDexArchiveDatabase;
import com.android.tools.deployer.ApkDiffer;
import com.android.tools.deployer.Deployer;
import com.android.tools.deployer.Installer;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import com.intellij.openapi.application.PathManager;
import java.nio.file.Paths;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.diagnostic.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class UnifiedDeployTask implements LaunchTask, Deployer.InstallerCallBack {

  public static enum DeployType {
    // When there is no previous APK Install.
    INSTALL,

    // Only update Java classes. No resource change, now activity restarts.
    CODE_SWAP,

    // Everything, including resource changes.
    FULL_SWAP
  }

  private static final String ID = "UNIFIED_DEPLOY";

  private final Collection<ApkInfo> myApks;

  // TODO: Move this to an an application component.
  private static DexArchiveDatabase myDb = new SQLiteDexArchiveDatabase(
    new File(Paths.get(PathManager.getSystemPath(), ".deploy.db").toString()));

  public static final Logger LOG = Logger.getInstance(UnifiedDeployTask.class);

  private final DeployType type;

  /**
   * Creates a task to deploy a list of apks.
   *
   * @param apks the apks to deploy.
   * @param swap whether to perform swap on a running app or to just install and restart.
   * @param activityRestart whether to restart activity upon swap.
   */
  public UnifiedDeployTask(@NotNull Collection<ApkInfo> apks, DeployType type) {
    myApks = apks;
    this.type = type;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Installing APK";
  }

  @Override
  public int getDuration() {
    return 20;
  }

  private String getLocalInstaller() {
    File path = new File(PathManager.getHomePath(), "plugins/android/resources/installer");
    if (!path.exists()) {
      // Development mode
      path = new File(PathManager.getHomePath(), "../../bazel-bin/tools/base/deploy/installer/android");
    }
    return path.getAbsolutePath();
  }

  @Override
  public boolean perform(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {

    boolean error = false;
    for (ApkInfo apk : myApks) {
      System.err.println("Processing application:" + apk.getApplicationId());

      List<String> paths = apk.getFiles().stream().map(
        apkunit -> apkunit.getApkFile().getPath()).collect(Collectors.toList());
      AdbClient adb = new AdbClient(device);
      Installer installer = new Installer(getLocalInstaller(), adb);
      Deployer deployer = new Deployer(apk.getApplicationId(), paths, this, adb, myDb, installer);
      Deployer.RunResponse response = null;
      try {
        switch (type) {
          case INSTALL:
            response = deployer.install();
            break;
          case CODE_SWAP:
            response = deployer.codeSwap();
            break;
          case FULL_SWAP:
            response = deployer.fullSwap();
            break;
          default:
            throw new UnsupportedOperationException("Not supported deployment type");
        }
      } catch (IOException e) {
        LOG.error("Error deploying APK", e);
        return false;
      }

      // TODO: shows the error somewhere other than System.err
      if (response.status == Deployer.RunResponse.Status.ERROR) {
        System.err.println(response.errorMessage);
        return error;
      }

      if (response.status == Deployer.RunResponse.Status.NOT_INSTALLED) {
        // TODO: Skip code swap and resource swap altogether.
        // Save localApk using localApkHash key.
        for (String apkAnalysisKey : response.result.keySet()) {
          Deployer.RunResponse.Analysis analysis = response.result.get(apkAnalysisKey);
          System.err.println("Apk: " + apkAnalysisKey);
          System.err.println("    local apk id: " + analysis.localApkHash);
        }
        continue;
      }

      // For each APK, a diff, a local if and a remote id were generated.
      for (String apkAnalysisKey : response.result.keySet()) {
        // TODO: Analysis diff, see if resource or code swap are needed. Use local and remote hash as key
        // to query the apk database.
        Deployer.RunResponse.Analysis analysis = response.result.get(apkAnalysisKey);
        System.err.println("Apk: " + apkAnalysisKey);
        System.err.println("    local apk id: " + analysis.localApkHash);
        System.err.println("    remot apk id: " + analysis.remoteApkHash);

        for (Map.Entry<String, ApkDiffer.ApkEntryStatus> statusEntry : analysis.diffs.entrySet()) {
          System.err.println("  " + statusEntry.getKey() +
                             " [" + statusEntry.getValue().toString().toLowerCase() + "]");
        }
      }
    }

    return true;
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @Override
  public void onInstallationFinished(boolean status) {
    System.err.println("Installation finished");
  }
}
