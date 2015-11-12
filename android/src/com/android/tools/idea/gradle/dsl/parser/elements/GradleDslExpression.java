/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * Represents a {@link GrExpression} element.
 */
public abstract class GradleDslExpression extends GradleDslElement {
  @Nullable protected GrExpression myExpression;

  protected GradleDslExpression(@Nullable GradleDslElement parent,
                                @Nullable GroovyPsiElement psiElement,
                                @NotNull String name,
                                @Nullable GrExpression expression) {
    super(parent, psiElement, name);
    myExpression = expression;
  }

  @Nullable
  public GrExpression getExpression() {
    return myExpression;
  }

  @Nullable
  public abstract Object getValue();

  @Nullable
  public abstract <T> T getValue(@NotNull Class<T> clazz);

  public abstract void setValue(@NotNull Object value);
}
