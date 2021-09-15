/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.dependencies;

import com.android.tools.idea.gradle.dsl.api.dependencies.PlatformDependencyModel;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslClosure;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class PlatformArtifactDependencyModelImpl extends ArtifactDependencyModelImpl implements PlatformDependencyModel {
  public PlatformArtifactDependencyModelImpl(@Nullable GradleDslClosure configurationElement,
                                             @NotNull String configurationName,
                                             @NotNull Maintainer maintainer) {
    super(configurationElement, configurationName, maintainer);
  }

  static class MapNotation extends ArtifactDependencyModelImpl.MapNotation implements PlatformDependencyModel {
    String methodName;

    MapNotation(@NotNull String configurationName,
                @NotNull GradleDslExpressionMap dslElement,
                @Nullable GradleDslClosure configurationElement,
                @NotNull Maintainer maintainer,
                @NotNull String methodName) {
      super(configurationName, dslElement, configurationElement, maintainer);
      this.methodName = methodName;
    }

    @Override
    public boolean enforced() {
      return methodName.equals("enforcedPlatform");
    }
  }

  static class CompactNotation extends ArtifactDependencyModelImpl.CompactNotation implements PlatformDependencyModel {
    String methodName;

    CompactNotation(@NotNull String configurationName,
                    @NotNull GradleDslSimpleExpression dslExpression,
                    @Nullable GradleDslClosure configurationElement,
                    @NotNull Maintainer maintainer,
                    @NotNull String methodName) {
      super(configurationName, dslExpression, configurationElement, maintainer);
      this.methodName = methodName;
    }

    @Override
    public boolean enforced() {
      return methodName.equals("enforcedPlatform");
    }
  }
}
