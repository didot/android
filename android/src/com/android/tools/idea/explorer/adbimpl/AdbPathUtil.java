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
package com.android.tools.idea.explorer.adbimpl;

import com.android.ddmlib.FileListingService;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtilRt;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.stream.Collectors;

/**
 * Utilities to manipulate paths of Android Device file system entries.
 *
 * Since paths on Android devices are posix-like, we can't use the {@link Path}
 * or {@link java.io.File} class to manipulate them, as these classes are platform dependent.
 */
public class AdbPathUtil {
  @NotNull
  public static final String FILE_SEPARATOR = FileListingService.FILE_SEPARATOR;

  @NotNull
  public static final String DEVICE_TEMP_DIRECTORY = "/data/local/tmp";

  /**
   * Returns the file name part of a path, i.e. the last segment.
   * Returns the empty string for the root path "/".
   */
  @NotNull
  public static String getFileName(@NotNull String path) {
    return PathUtilRt.getFileName(path);
  }

  /**
   * Resolve the path {@code other} within the context of {@code basePath}. This is similar
   * to {@link Path#resolve(Path)}.
   */
  @NotNull
  public static String resolve(@NotNull String basePath, @NotNull String other) {
    if (isEmpty(other)) {
      return basePath;
    }
    if (isAbsolute(other)) {
      return other;
    }
    if (isSuffixed(basePath)) {
      return basePath + other;
    }

    return basePath + FILE_SEPARATOR + other;
  }

  @NotNull
  public static String getEscapedPath(@NotNull String path) {
    // Special case for root
    if (FILE_SEPARATOR.equals(path)) {
      return path;
    }

    // Escape each segment, then re-join them by file separator
    return StringUtil.split(path, FILE_SEPARATOR)
      .stream()
      .map(x -> FILE_SEPARATOR + FileListingService.FileEntry.escape(x))
      .collect(Collectors.joining());
  }

  private static boolean isEmpty(@NotNull String path) {
    return StringUtil.isEmpty(path);
  }

  private static boolean isAbsolute(@NotNull String path) {
    return path.startsWith(FILE_SEPARATOR);
  }

  private static boolean isSuffixed(@NotNull String path) {
    return path.endsWith(FILE_SEPARATOR);
  }
}
