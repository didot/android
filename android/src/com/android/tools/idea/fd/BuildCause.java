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
package com.android.tools.idea.fd;

import org.jetbrains.annotations.NotNull;

public enum BuildCause {
  // reasons for clean build
  USER_REQUESTED_CLEAN_BUILD(BuildMode.CLEAN),

  // reasons for full build
  USER_REQUESTED_FULL_BUILD(BuildMode.FULL),
  NO_DEVICE(BuildMode.FULL),
  APP_NOT_INSTALLED(BuildMode.FULL),
  MISMATCHING_TIMESTAMPS(BuildMode.FULL),
  API_TOO_LOW_FOR_INSTANT_RUN(BuildMode.FULL),
  FIRST_INSTALLATION_TO_DEVICE(BuildMode.FULL), // first installation in this Android Studio session
  MANIFEST_RESOURCE_CHANGED(BuildMode.FULL),
  FREEZE_SWAP_REQUIRES_API21(BuildMode.FULL),
  FREEZE_SWAP_REQUIRES_WORKING_RUN_AS(BuildMode.FULL),

  // reasons for forced cold swap build
  APP_NOT_RUNNING(BuildMode.COLD),
  APP_USES_MULTIPLE_PROCESSES(BuildMode.COLD),
  ANDROID_TV_UNSUPPORTED(BuildMode.COLD),

  INCREMENTAL_BUILD(BuildMode.HOT),
  ;

  @NotNull
  public BuildMode getBuildMode() {
    return myBuildMode;
  }

  @NotNull
  private final BuildMode myBuildMode;

  BuildCause(@NotNull BuildMode mode) {
    myBuildMode = mode;
  }

  @Override
  public String toString() {
    return "BuildCause: " + super.name() + ", BuildMode: " + myBuildMode.toString();
  }

}
