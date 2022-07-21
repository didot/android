/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device

import com.intellij.openapi.util.SystemInfo

/**
 * Checks if the current platform is suitable for tests depending on the FFmpeg library.
 */
internal fun isFFmpegAvailableToTest(): Boolean {
  if (SystemInfo.isWindows) {
    // For some unclear reason FFmpeg-dependent tests fail on Windows with UnsatisfiedLinkError: no jniavcodec in java.library.path.
    return false
  }
  if (SystemInfo.isMac && !SystemInfo.isOsVersionAtLeast("10.15")) {
    return false // FFmpeg library requires Mac OS 10.15+.
  }
  return true
}