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
import com.android.tools.deployer.AdbClient;
import com.android.tools.deployer.Apk;
import com.android.tools.deployer.Deployer;
import com.android.tools.deployer.DeployerRunner;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class UnifiedDeployTask implements LaunchTask, Deployer.InstallerCallBack {

  private final Collection<ApkInfo> myApks;

  public UnifiedDeployTask(@NotNull Collection<ApkInfo> apks) {
    myApks = apks;
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

  @Override
  public boolean perform(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {

    boolean error = false;
    for (ApkInfo apk : myApks) {
      System.err.println("Processing application:" + apk.getApplicationId());

      List<String> paths = apk.getFiles().stream().map(
        apkunit -> apkunit.getApkFile().getPath()).collect(Collectors.toList());
      Deployer deployer = new Deployer(apk.getApplicationId(), paths, this, new AdbClient(device));
      Deployer.RunResponse response = deployer.run();

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

        for (Map.Entry<String, Apk.ApkEntryStatus> statusEntry : analysis.diffs.entrySet()) {
          System.err.println("  " + statusEntry.getKey() +
                             " [" + statusEntry.getValue().toString().toLowerCase() + "]");
        }
      }
    }

    return true;
  }

  @Override
  public void onInstallationFinished(boolean status) {
    System.err.println("Installation finished");
  }
}
