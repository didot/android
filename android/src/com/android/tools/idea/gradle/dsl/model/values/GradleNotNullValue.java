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
package com.android.tools.idea.gradle.dsl.model.values;

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a {@link GradleValue} where {@link #value()} will never return a {@code null} value.
 *
 * @param <T> the type of the returned value.
 */
public class GradleNotNullValue<T> extends GradleValue {
  @NotNull private final T myValue;

  public GradleNotNullValue(@NotNull GradleDslElement dslElement, @NotNull T value) {
    super(dslElement);
    myValue = value;
  }

  public static <E> GradleNotNullValue<E> create(@NotNull GradleNullableValue<E> gradleValue) {
    E value = gradleValue.value();
    assert value != null;

    return new GradleNotNullValue<E>(gradleValue.myDslElement, value);
  }

  @NotNull
  public T value() {
    return myValue;
  }
}
