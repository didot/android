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
package com.android.tools.idea.gradle.dsl.parser.repositories;

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElementList;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class RepositoriesDslElement extends GradleDslBlockElement {
  @NonNls public static final String REPOSITORIES_BLOCK_NAME = "repositories";

  public RepositoriesDslElement(@NotNull GradleDslElement parent) {
    super(parent, GradleNameElement.create(REPOSITORIES_BLOCK_NAME));
  }

  @Override
  public void setParsedElement(@NotNull GradleDslElement repository) {
    // Because we need to preserve the the order of the repositories defined, storing all the repository elements in a dummy element list.
    // TODO: Consider extending RepositoriesDslElement directly from GradleDslElementList instead of GradlePropertiesDslElement.
    GradleDslElementList repositoriesListElement = getOrCreateRepositoriesElement();
    repositoriesListElement.addParsedElement(repository);
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement repository) {
    GradleDslElementList repositoriesListElement = getOrCreateRepositoriesElement();
    repositoriesListElement.addParsedElement(repository);
  }

  @NotNull
  private GradleDslElementList getOrCreateRepositoriesElement() {
    GradleDslElementList elementList = getPropertyElement(REPOSITORIES_BLOCK_NAME, GradleDslElementList.class);
    if (elementList == null) {
      GradleNameElement name = GradleNameElement.create(REPOSITORIES_BLOCK_NAME);
      elementList = new GradleDslElementList(this, name);
      super.addParsedElement(elementList);
    }
    return elementList;
  }
}
