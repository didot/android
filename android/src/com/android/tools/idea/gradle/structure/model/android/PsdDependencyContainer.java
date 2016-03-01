/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.android;

import com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;

public class PsdDependencyContainer {
  @NotNull private final String myVariant;
  @NotNull private final String myArtifact;
  @NotNull private final String myName;

  PsdDependencyContainer(@NotNull PsdAndroidArtifactModel artifactModel) {
    myVariant = artifactModel.getParent().getName();
    myArtifact = artifactModel.getGradleModel().getName();
    myName = myVariant + " " + myArtifact;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getVariant() {
    return myVariant;
  }

  @NotNull
  public String getArtifact() {
    return myArtifact;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PsdDependencyContainer container = (PsdDependencyContainer)o;
    return Objects.equal(myVariant, container.myVariant) &&
           Objects.equal(myArtifact, container.myArtifact);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myVariant, myArtifact);
  }

  @Override
  public String toString() {
    return getName();
  }
}
