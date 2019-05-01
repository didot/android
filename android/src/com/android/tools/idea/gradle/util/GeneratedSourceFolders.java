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
package com.android.tools.idea.gradle.util;

import com.android.ide.common.gradle.model.IdeAndroidProject;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.android.builder.model.AndroidProject.FD_GENERATED;
import static com.intellij.openapi.util.io.FileUtil.isAncestor;

public class GeneratedSourceFolders {
  public boolean isFolderGeneratedInCorrectLocation(@NotNull File folderPath, @NotNull IdeAndroidProject androidProject) {
    File generatedFolderPath = new File(androidProject.getBuildFolder(), FD_GENERATED);
    return isAncestor(generatedFolderPath, folderPath, false);
  }
}
