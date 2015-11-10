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
package com.android.tools.idea.ui.properties.core;

import com.android.tools.idea.ui.properties.ObservableProperty;
import com.android.tools.idea.ui.properties.expressions.bool.BooleanExpression;
import com.google.common.base.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An {@link ObservableProperty} backed by an optional value.
 */
public final class OptionalValueProperty<T> extends OptionalProperty<T> {

  @NotNull private Optional<T> myOptional;

  public OptionalValueProperty() {
    myOptional = Optional.absent();
  }

  public OptionalValueProperty(@NotNull T value) {
    myOptional = Optional.of(value);
  }

  public static <T> OptionalValueProperty<T> of(@NotNull T value) {
    return new OptionalValueProperty<T>(value);
  }

  public static <T> OptionalValueProperty<T> fromNullable(@Nullable T value) {
    if (value != null) {
      return of(value);
    }
    else {
      return absent();
    }
  }

  public static <T> OptionalValueProperty<T> absent() {
    return new OptionalValueProperty<T>();
  }

  @NotNull
  @Override
  public Optional<T> get() {
    return myOptional;
  }

  @Override
  @NotNull
  public T getValue() {
    return myOptional.get();
  }

  @Override
  public void setValue(@NotNull T value) {
    if (!myOptional.isPresent() || !myOptional.get().equals(value)) {
      set(Optional.of(value));
    }
  }

  @Override
  public void clear() {
    if (myOptional.isPresent()) {
      set(Optional.<T>absent());
    }
  }

  @Override
  public void setNullableValue(@Nullable T value) {
    if (value != null) {
      setValue(value);
    }
    else {
      clear();
    }
  }

  @Override
  @NotNull
  public T getValueOr(@NotNull T defaultValue) {
    return myOptional.or(defaultValue);
  }

  @Override
  @Nullable
  public T getValueOrNull() {
    return myOptional.orNull();
  }

  @NotNull
  @Override
  public ObservableBool isPresent() {
    return new BooleanExpression(this) {
      @NotNull
      @Override
      public Boolean get() {
        return myOptional.isPresent();
      }
    };
  }

  @Override
  protected void setDirectly(@NotNull Optional<T> value) {
    myOptional = value;
  }
}
