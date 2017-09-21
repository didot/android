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
package com.android.tools.idea.uibuilder.property.editors.support;

import com.android.tools.idea.common.property.NlProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SimpleValuePairEnumSupport extends EnumSupport {
  private final Map<String, String> myPossibleValues;

  public SimpleValuePairEnumSupport(@NotNull NlProperty property, @NotNull Map<String, String> possibleValues) {
    super(property);
    myPossibleValues = possibleValues;
  }

  @Override
  @NotNull
  public List<ValueWithDisplayString> getAllValues() {
    List<ValueWithDisplayString> values = new ArrayList<>(myPossibleValues.size());
    for (Map.Entry<String, String> valuePair : myPossibleValues.entrySet()) {
      values.add(new ValueWithDisplayString(valuePair.getValue(), valuePair.getKey()));
    }
    return values;
  }

  @Override
  @NotNull
  protected ValueWithDisplayString createFromResolvedValue(@NotNull String resolvedValue, @Nullable String value, @Nullable String hint) {
    String displayValue = myPossibleValues.get(resolvedValue);
    if (displayValue == null) {
      displayValue = resolvedValue;
    }
    return new ValueWithDisplayString(displayValue, value, hint);
  }
}
