/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.profilers.capture;

import com.android.tools.idea.fileTypes.profiler.AndroidProfilerCaptureFileType;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a heapprofd recording file that can be imported into memory profiler.
 */
public class HeapProfdMemoryCaptureFileType extends AndroidProfilerCaptureFileType {
  public static final HeapProfdMemoryCaptureFileType INSTANCE = new HeapProfdMemoryCaptureFileType();
  public static final String EXTENSION = "heapprofd";

  // FIXME-ank4: add private constructor (filetypes are singletones)

  @NotNull
  @Override
  public String getName() {
    return "HeapProfd";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Android Profiler Memory capture file";
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return EXTENSION;
  }
}
