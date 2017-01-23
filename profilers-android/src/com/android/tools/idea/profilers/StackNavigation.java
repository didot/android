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
package com.android.tools.idea.profilers;

import com.android.tools.profilers.common.CodeLocation;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface StackNavigation {
  String UNKONWN_CLASS = "<unkonwn class>";
  String UNKNOWN_PACKAGE = "<unknown package>";
  String NO_PACKAGE = "<no package>";
  String UNKNOWN_METHOD = "<unknown method>";

  @NotNull CodeLocation getCodeLocation();

  @NotNull String getPackageName();

  @NotNull String getSimpleClassName();

  @NotNull String getMethodName();

  @Nullable Navigatable[] getNavigatable(@Nullable Runnable preNavigate);

  @Nullable VirtualFile findClassFile();
}
