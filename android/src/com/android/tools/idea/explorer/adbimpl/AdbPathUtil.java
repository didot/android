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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtilRt;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

import static com.android.ddmlib.FileListingService.FILE_SEPARATOR;


/**
 * Utilities to manipulate paths of Android Device file system entries.
 *
 * Since paths on Android devices are posix-like, we can't use the {@link java.nio.file.Path}
 * or {@link java.io.File} class to manipulate them, as these classes are platform dependent.
 */
public class AdbPathUtil {
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
