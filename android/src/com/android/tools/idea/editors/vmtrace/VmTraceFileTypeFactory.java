/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.vmtrace;

import com.android.tools.idea.flags.StudioFlags;
import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import org.jetbrains.annotations.NotNull;

public class VmTraceFileTypeFactory extends FileTypeFactory {
  @Override
  public void createFileTypes(@NotNull FileTypeConsumer consumer) {
    // Only consume .trace files as this legacy FileType if PROFILER_OPEN_CAPTURES flag is disabled.
    // If the flag is enabled, these files are going to be consumed by Android Profiler instead.
    if (!StudioFlags.PROFILER_OPEN_CAPTURES.get()) {
      consumer.consume(VmTraceFileType.INSTANCE, VmTraceFileType.INSTANCE.getDefaultExtension());
    }
  }
}
