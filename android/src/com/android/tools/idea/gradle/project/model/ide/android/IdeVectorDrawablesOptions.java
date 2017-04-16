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

import com.android.annotations.Nullable;
import com.android.builder.model.VectorDrawablesOptions;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Set;

/**
 * Creates a deep copy of {@link VectorDrawablesOptions}.
 *
 * @see IdeAndroidProject
 */
public class IdeVectorDrawablesOptions extends IdeModel implements VectorDrawablesOptions {
  @Nullable private Set<String> myGeneratedDensities;
  @Nullable private Boolean myUseSupportLibrary;

  public IdeVectorDrawablesOptions(@NotNull VectorDrawablesOptions options, @NotNull ModelCache modelCache) {
    super(options, modelCache);
    myGeneratedDensities = copy(options.getGeneratedDensities());
    myUseSupportLibrary = options.getUseSupportLibrary();
  }

  @Override
  @Nullable
  public Set<String> getGeneratedDensities() {
    return myGeneratedDensities;
  }

  @Override
  @Nullable
  public Boolean getUseSupportLibrary() {
    return myUseSupportLibrary;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof IdeVectorDrawablesOptions)) return false;
    IdeVectorDrawablesOptions options = (IdeVectorDrawablesOptions)o;
    return Objects.equals(getGeneratedDensities(), options.getGeneratedDensities()) &&
           Objects.equals(getUseSupportLibrary(), options.getUseSupportLibrary());
  }
}
