/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.instantapp.provision;

import com.android.ddmlib.IDevice;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;

class GmsCorePackage extends ProvisionPackage {
  @NotNull private static final String PKG_DESCRIPTION = "Gms Core";
  @NotNull private static final String APK_PREFIX = "GmsCore_prodmnc_xxhdpi";
  @NotNull private static final String PKG_NAME = "com.google.android.gms";
  
  // See dev/modules/instantapps/src/com/google/android/gms/instantapps/common/G.java in AIA project for details.
  @NotNull private static final String WHITELISTED_SERVICES = "68,23,4,71,8,123,46,95,91,93,40,24,64,65,67,11,44,112";

  GmsCorePackage(@NotNull File instantAppSdk) {
    super(instantAppSdk);
  }

  @Override
  void setFlags(@NotNull IDevice device) throws ProvisionException {
    String osBuildType = getOsBuildType(device);
    getLogger().info("Setting flags for build type \"" + osBuildType + "\"");

    if (osBuildType.compareTo(RELEASE_TYPE) == 0) {
      // For a release-keys build, we rely on OTA updates to set these flags
      return;
    }

    executeShellCommand(device,
                        "CLASSPATH=/system/framework/am.jar su root app_process " +
                        "/system/bin com.android.commands.am.Am broadcast " +
                        "-a com.google.gservices.intent.action.GSERVICES_OVERRIDE " +
                        "-e gms:wh:enable_westinghouse_support true " +
                        "-e gms:wh:whitelistedServiceIds " + WHITELISTED_SERVICES,
                        true /* root is required */);
  }

  @NotNull
  @Override
  String getApkPrefix() {
    return APK_PREFIX;
  }

  @NotNull
  @Override
  String getPkgName() {
    return PKG_NAME;
  }

  @NotNull
  @Override
  String getDescription() {
    return PKG_DESCRIPTION;
  }
}
