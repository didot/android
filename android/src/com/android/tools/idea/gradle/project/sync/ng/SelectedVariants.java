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
package com.android.tools.idea.gradle.project.sync.ng;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class SelectedVariants implements Serializable {
  // Key: module's Gradle ID, value: selected variant.
  /**
   * @see com.android.tools.idea.gradle.project.sync.Modules#createUniqueModuleId(File, String)
   */
  private final Map<String, String> mySelectedVariantByModule = new HashMap<>();

  void addSelectedVariant(@NotNull String moduleId, @NotNull String selectedVariant) {
    mySelectedVariantByModule.put(moduleId, selectedVariant);
  }

  public int size() {
    return mySelectedVariantByModule.size();
  }

  @Nullable
  public String getSelectedVariant(@NotNull String moduleId) {
    return mySelectedVariantByModule.get(moduleId);
  }
}
