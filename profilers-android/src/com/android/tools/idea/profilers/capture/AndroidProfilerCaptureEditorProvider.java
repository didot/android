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
package com.android.tools.idea.profilers.capture;

import com.android.tools.idea.fileTypes.profiler.CpuCaptureFileType;
import com.android.tools.idea.fileTypes.profiler.MemoryAllocationFileType;
import com.android.tools.idea.fileTypes.profiler.MemoryCaptureFileType;
import com.android.tools.idea.flags.StudioFlags;
import com.google.common.base.Strings;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Custom {@link FileEditorProvider} which allows the editor pane to intercept the opening of certain file types and import them as capture
 * artifacts in Android Profiler.
 */
public class AndroidProfilerCaptureEditorProvider implements FileEditorProvider, DumbAware {

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    String extension = file.getExtension();
    return !Strings.isNullOrEmpty(extension) &&
           (extension.equals(CpuCaptureFileType.EXTENSION) ||
            extension.equals(MemoryAllocationFileType.EXTENSION) ||
            extension.equals(MemoryCaptureFileType.EXTENSION) ||
            (StudioFlags.PROFILER_ENABLE_NATIVE_SAMPLE.get() && extension.equals(HeapProfdMemoryCaptureFileType.EXTENSION)));
  }

  @NotNull
  @Override
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    return new AndroidProfilerCaptureEditor(project, file);
  }

  @NotNull
  @Override
  public String getEditorTypeId() {
    return "AndroidProfilerCaptureEditorProvider";
  }

  @NotNull
  @Override
  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
  }
}
