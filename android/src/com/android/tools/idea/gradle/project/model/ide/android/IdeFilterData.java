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

import com.android.build.FilterData;
import org.jetbrains.annotations.NotNull;

/**
 * Creates a deep copy of {@link FilterData}.
 *
 * @see IdeAndroidProject
 */
public class IdeFilterData extends IdeModel implements FilterData {
  @NotNull private final String myIdentifier;
  @NotNull private final String myFilterType;

  public IdeFilterData(@NotNull FilterData data) {
    myIdentifier = data.getIdentifier();
    myFilterType = data.getFilterType();
  }

  @Override
  @NotNull
  public String getIdentifier() {
    return myIdentifier;
  }

  @Override
  @NotNull
  public String getFilterType() {
    return myFilterType;
  }
}
