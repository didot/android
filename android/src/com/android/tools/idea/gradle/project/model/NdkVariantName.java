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
package com.android.tools.idea.gradle.project.model;

import java.io.Serializable;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.project.model.NdkModuleModel.getNdkVariantName;

public class NdkVariantName implements Serializable {
  @NotNull String variant;
  @NotNull String abi;
  @NotNull String displayName;

  // Used for serialization by the IDE.#
  @SuppressWarnings("unused")
  public NdkVariantName() {
    variant = "";
    abi = "";
    displayName = "";
  }

  public NdkVariantName(@NotNull String variant, @NotNull String abi) {
    this.variant = variant;
    this.abi = abi;
    this.displayName = getNdkVariantName(variant, abi);
  }
}