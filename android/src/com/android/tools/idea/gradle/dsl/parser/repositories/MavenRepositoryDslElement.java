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

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MavenRepositoryDslElement extends GradlePropertiesDslElement {
  @NonNls public static final String MAVEN_BLOCK_NAME = "maven";
  @NonNls public static final String JCENTER_BLOCK_NAME = "jcenter";

  public MavenRepositoryDslElement(@Nullable GradleDslElement parent, @NotNull  String name) {
    super(parent, null, name);
    assert MAVEN_BLOCK_NAME.equals(name) || JCENTER_BLOCK_NAME.equals(name);
  }

  @Override
  public boolean isBlockElement() {
    return true;
  }

  @Override
  public void addParsedElement(@NotNull String property, @NotNull GradleDslElement element) {
    if (property.equals("artifactUrls")) {
      addToParsedExpressionList(property, element);
      return;
    }
    super.addParsedElement(property, element);
  }
}
