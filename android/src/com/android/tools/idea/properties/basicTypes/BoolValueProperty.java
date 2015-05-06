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
package com.android.tools.idea.properties.basicTypes;

import com.android.tools.idea.properties.ObservableValue;
import com.android.tools.idea.properties.ObservableProperty;
import com.android.tools.idea.properties.expressions.bool.AndExpression;
import com.android.tools.idea.properties.expressions.bool.BooleanExpression;
import com.android.tools.idea.properties.expressions.bool.NotExpression;
import com.android.tools.idea.properties.expressions.bool.OrExpression;
import org.jetbrains.annotations.NotNull;

/**
 * A boolean-backed {@link ObservableProperty}.
 */
public final class BoolValueProperty extends ObservableProperty<Boolean> implements BooleanExpression {

  private Boolean myValue;

  public BoolValueProperty(final Boolean value) {
    myValue = value;
  }

  public BoolValueProperty() {
    this(false);
  }

  @NotNull
  @Override
  public Boolean get() {
    return myValue;
  }

  @NotNull
  @Override
  public BooleanExpression not() {
    return new NotExpression(this);
  }

  @NotNull
  @Override
  public BooleanExpression or(@NotNull ObservableValue<Boolean> other) {
    return new OrExpression(this, other);
  }

  @NotNull
  @Override
  public BooleanExpression and(@NotNull ObservableValue<Boolean> other) {
    return new AndExpression(this, other);
  }

  @Override
  protected void setDirectly(@NotNull Boolean value) {
    myValue = value;
  }
}
