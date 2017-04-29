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
package com.android.tools.idea.gradle.project.model.ide.android;

import com.android.builder.model.AndroidArtifactOutput;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Objects;

/**
 * Creates a deep copy of an {@link AndroidArtifactOutput}.
 */
public final class IdeAndroidArtifactOutput extends IdeVariantOutput implements AndroidArtifactOutput {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;

  @NotNull private final File myGeneratedManifest;
  @Nullable private final File myOutputFile;
  @Nullable private final String myAssembleTaskName;

  public IdeAndroidArtifactOutput(@NotNull AndroidArtifactOutput output, @NotNull ModelCache modelCache) {
    super(output, modelCache);
    String assembleTaskName;
    try {
      //noinspection deprecation
      assembleTaskName = output.getAssembleTaskName();
    }
    catch (RuntimeException e) {
      assembleTaskName = null;
    }
    myAssembleTaskName = assembleTaskName;
    myGeneratedManifest = output.getGeneratedManifest();
    myOutputFile = copyNewProperty(output::getOutputFile, null);
  }

  @Override
  @NotNull
  public String getAssembleTaskName() {
    if (myAssembleTaskName != null) {
      return myAssembleTaskName;
    }
    throw new RuntimeException("Method 'getAssembleTaskName' is no longer supported");
  }

  @Override
  @NotNull
  public File getGeneratedManifest() {
    return myGeneratedManifest;
  }

  @Override
  @NotNull
  public File getOutputFile() {
    if (myOutputFile != null) {
      return myOutputFile;
    }
    throw new UnsupportedMethodException("getOutputFile");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IdeAndroidArtifactOutput)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    IdeAndroidArtifactOutput output = (IdeAndroidArtifactOutput)o;
    return output.canEquals(this) &&
           Objects.equals(myAssembleTaskName, output.myAssembleTaskName) &&
           Objects.equals(myGeneratedManifest, output.myGeneratedManifest) &&
           Objects.equals(myOutputFile, output.myOutputFile);
  }

  @Override
  protected boolean canEquals(Object other) {
    return other instanceof IdeAndroidArtifactOutput;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), myAssembleTaskName, myGeneratedManifest, myOutputFile);
  }

  @Override
  public String toString() {
    return "IdeAndroidArtifactOutput{" +
           super.toString() +
           ", myAssembleTaskName='" + myAssembleTaskName + '\'' +
           ", myGeneratedManifest=" + myGeneratedManifest +
           ", myOutputFile=" + myOutputFile +
           "}";
  }
}
