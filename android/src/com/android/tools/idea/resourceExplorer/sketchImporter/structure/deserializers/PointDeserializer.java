/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.sketchImporter.structure.deserializers;

import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchPoint2D;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PointDeserializer implements JsonDeserializer<SketchPoint2D> {
  static final Pattern pattern = Pattern.compile("\\{([+-]?[0-9.]+e?[+-]?[0-9]*),\\s*([+-]?[0-9.]+e?[+-]?[0-9]*)}");

  @Override
  public SketchPoint2D deserialize(@NotNull JsonElement json,
                                   @NotNull Type typeOfT,
                                   @NotNull JsonDeserializationContext context) {
    final String positionString = json.getAsString();

    // Turn sketch coordinate string (e.g. "{0.5, 0.67135115527602085}") into a point
    Matcher matcher = pattern.matcher(positionString);

    if (matcher.matches()) {
      double x = matcher.group(1).contains("e-") ? 0 : Double.parseDouble(matcher.group(1));
      double y = matcher.group(2).contains("e-") ? 0 : Double.parseDouble(matcher.group(2));

      return new SketchPoint2D(x, y);
    }
    else {
      Logger.getInstance(PointDeserializer.class).warn("Bad point format: " + positionString);
    }

    return null;
  }
}
