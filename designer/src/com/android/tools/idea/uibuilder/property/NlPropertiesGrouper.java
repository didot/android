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
package com.android.tools.idea.uibuilder.property;

import com.android.ide.common.rendering.api.ResourceReference;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlPropertyAccumulator.PropertyNamePrefixAccumulator;
import com.android.tools.adtui.ptable.PTableItem;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.android.SdkConstants.*;

public class NlPropertiesGrouper {
  public List<PTableItem> group(@NotNull List<NlPropertyItem> properties,
                                @SuppressWarnings("UnusedParameters") @NotNull List<NlComponent> components) {
    List<PTableItem> result = new ArrayList<>(properties.size());

    // group theme attributes together
    NlPropertyAccumulator themePropertiesAccumulator = new NlPropertyAccumulator(
      "Theme", "", p -> p != null && (isThemeAttribute(p) || p.getName().equalsIgnoreCase("theme")));

    // Disable this for now...
    //
    // group attributes that correspond to this component together
    //NlPropertyAccumulator customViewPropertiesAccumulator = null;
    //String className = getCommonTagName(components);
    //if (className != null) {
    //  customViewPropertiesAccumulator = new NlPropertyAccumulator(className, p -> p != null && p.getParentStyleables().contains(className));
    //}

    // group margin, padding and layout attributes together
    NlPropertyAccumulator paddingPropertiesAccumulator = new NlMarginPropertyAccumulator("Padding", ATTR_PADDING, ATTR_PADDING_LEFT, ATTR_PADDING_RIGHT, ATTR_PADDING_START, ATTR_PADDING_END, ATTR_PADDING_TOP, ATTR_PADDING_BOTTOM);
    NlPropertyAccumulator layoutViewPropertiesAccumulator = new NlMarginPropertyAccumulator("Layout_Margin", ATTR_LAYOUT_MARGIN, ATTR_LAYOUT_MARGIN_LEFT, ATTR_LAYOUT_MARGIN_RIGHT, ATTR_LAYOUT_MARGIN_START, ATTR_LAYOUT_MARGIN_END, ATTR_LAYOUT_MARGIN_TOP, ATTR_LAYOUT_MARGIN_BOTTOM);

    PropertyNamePrefixAccumulator constraintPropertiesAccumulator = new PropertyNamePrefixAccumulator("Constraints", "layout_constraint");

    NlPropertyAccumulator[] accumulators = new NlPropertyAccumulator[] {
        themePropertiesAccumulator,
        paddingPropertiesAccumulator,
        layoutViewPropertiesAccumulator,
        constraintPropertiesAccumulator
    };

    for (NlPropertyItem p : properties) {
      boolean added = false;
      for (NlPropertyAccumulator accumulator : accumulators) {
        added = accumulator.process(p);
        if (added) {
          break;
        }
      }

      if (!added) {
        result.add(p);
      }
    }

    int insertionPoint = findInsertionPoint(result);

    for (NlPropertyAccumulator accumulator : accumulators) {
      if (accumulator.hasItems()) {
        result.add(insertionPoint, accumulator.getGroupNode());
      }
    }

    return result;
  }

  private static boolean isThemeAttribute(NlPropertyItem item) {
    return item.getParentStyleables().stream().map(ResourceReference::getName).anyMatch(s -> s.equals("Theme"));
  }

  @Nullable
  public static String getCommonTagName(@NotNull List<NlComponent> components) {
    String commonTagName = null;
    for (NlComponent component : components) {
      String tagName = component.getTagName();
      if (commonTagName == null) {
        commonTagName = tagName;
      }
      else if (!tagName.equals(commonTagName)) {
        return null;
      }
    }
    return commonTagName;
  }

  private static List<String> MOST_IMPORTANT_ATTRIBUTES = ImmutableList.of(ATTR_ID, ATTR_LAYOUT_WIDTH, ATTR_LAYOUT_HEIGHT);

  private static int findInsertionPoint(@NotNull List<PTableItem> properties) {
    for (int index = 0; index < MOST_IMPORTANT_ATTRIBUTES.size(); index++) {
      if (properties.size() <= index || !properties.get(index).getName().equals(MOST_IMPORTANT_ATTRIBUTES.get(index))) {
        return index;
      }
    }
    return MOST_IMPORTANT_ATTRIBUTES.size();
  }
}
