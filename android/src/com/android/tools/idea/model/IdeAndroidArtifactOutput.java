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
package com.android.tools.idea.model;

import com.android.builder.model.AndroidArtifactOutput;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;

/**
 * Creates a deep copy of {@link AndroidArtifactOutput}.
 *
 * @see IdeAndroidProject
 */
public class IdeAndroidArtifactOutput extends IdeVariantOutput implements AndroidArtifactOutput, Serializable {
  @NotNull private final String myAssembleTaskName;
  @NotNull private final File myGeneratedManifest;
  @NotNull private final File myOutputFile;

  public IdeAndroidArtifactOutput(@NotNull AndroidArtifactOutput output) {
    super(output);
    myAssembleTaskName = output.getAssembleTaskName();
    myGeneratedManifest = output.getGeneratedManifest();
    myOutputFile = output.getOutputFile();
  }

  @Override
  @NotNull
  public String getAssembleTaskName() {
    return myAssembleTaskName;
  }

  @Override
  @NotNull
  public File getGeneratedManifest() {
    return myGeneratedManifest;
  }

  @Override
  @NotNull
  public File getOutputFile() {
    return myOutputFile;
  }
}
