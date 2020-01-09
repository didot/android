/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.material.icons

import java.util.Locale

internal object MaterialIconsUtils {
  const val MATERIAL_ICONS_PATH = "images/material/icons/"

  const val METADATA_FILE_NAME = "icons_metadata.txt"

  fun String.toDirFormat(): String = this.toLowerCase(Locale.US).replace(" ", "")
}