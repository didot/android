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
package com.android.tools.idea.compose.preview.util.device

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object DeviceSpecFileType : LanguageFileType(DeviceSpecLanguage) {

  override fun getName(): String {
    return "DeviceSpecFile"
  }

  override fun getDescription(): String {
    return "Device spec"
  }

  override fun getDefaultExtension(): String {
    return "dspec"
  }

  override fun getIcon(): Icon? {
    return AllIcons.FileTypes.Text
  }
}
