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
package com.android.tools.idea.editors.theme;

import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.ide.common.resources.configuration.Configurable;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredElement;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.android.tools.idea.editors.theme.qualifiers.RestrictedConfiguration;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Helper class to get all the items and values defined in the given style, taking into account the inheritance
 */
public class ThemeAttributeResolver {
  private static final Logger LOG = Logger.getInstance(ThemeAttributeResolver.class);

  final private ThemeResolver myThemeResolver;
  final private ThemeEditorStyle myStyle;
  final private MultiMap<String, ConfiguredElement<ItemResourceValue>> myItemValueMap =
    new MultiMap<String, ConfiguredElement<ItemResourceValue>>();

  private ThemeAttributeResolver(ThemeEditorStyle style, ThemeResolver themeResolver) {
    myStyle = style;
    myThemeResolver = themeResolver;
  }

  /**
   * @return RestrictedConfiguration that matches to compatible and doesn't match to other FolderConfigurations where the style is defined
   */
  @Nullable("if there is no configuration that matches to restrictions")
  private static RestrictedConfiguration getRestrictedConfiguration(@NotNull ThemeEditorStyle style, @NotNull FolderConfiguration compatible) {
    ArrayList<FolderConfiguration> incompatibles = Lists.newArrayList();
    for (FolderConfiguration folder : style.getFolders()) {
      if (!compatible.equals(folder)) {
        incompatibles.add(folder);
      }
    }
    return RestrictedConfiguration.restrict(compatible, incompatibles);
  }

  private void resolveFromInheritance(@NotNull ThemeEditorStyle style,
                                      @NotNull FolderConfiguration configuration,
                                      @NotNull RestrictedConfiguration restricted,
                                      @NotNull Set<String> seenAttributes) {
    RestrictedConfiguration styleRestricted = getRestrictedConfiguration(style, configuration);
    if (styleRestricted == null) {
      LOG.warn(configuration + " is unreachable");
      return;
    }
    restricted = restricted.intersect(styleRestricted);
    if (restricted == null) {
      return;
    }

    Set<String> newSeenAttributes = new HashSet<String>(seenAttributes);
    for (ItemResourceValue item : style.getValues(configuration)) {
      String itemName = ResolutionUtils.getQualifiedItemName(item);
      if (!newSeenAttributes.contains(itemName)) {
        myItemValueMap.putValue(itemName, ConfiguredElement.create(restricted.getAny(), item));
        newSeenAttributes.add(itemName);
      }
    }
    String parentName = style.getParentName(configuration);

    if (parentName == null) {
      // We have reached the top of the theme hierarchy (i.e "android:Theme")
      return;
    }
    ThemeEditorStyle parent = myThemeResolver.getTheme(parentName);
    if (parent == null) {
      // We have hit a style that's not a theme, this should not normally happen, USER ERROR
      return;
    }
    for (FolderConfiguration folder : parent.getFolders()) {
      resolveFromInheritance(parent, folder, restricted, newSeenAttributes);
    }
  }

  @NotNull
  private List<EditedStyleItem> resolveAll() {
    for (FolderConfiguration folder : myStyle.getFolders()) {
      resolveFromInheritance(myStyle, folder, new RestrictedConfiguration(), new HashSet<String>());
    }

    List<EditedStyleItem> result = Lists.newArrayList();
    FolderConfiguration configuration = myStyle.getConfiguration().getFullConfig();
    for (String key : myItemValueMap.keySet()) {
      Collection<ConfiguredElement<ItemResourceValue>> itemValues = myItemValueMap.get(key);
      final ConfiguredElement<ItemResourceValue> selectedValue =
        (ConfiguredElement<ItemResourceValue>)configuration.findMatchingConfigurable(Lists.<Configurable>newArrayList(itemValues));
      if (selectedValue == null) {
        // TODO: there is NO value for this attribute in the current config,so instead we need to show "no value for current device"
        result.add(new EditedStyleItem(itemValues.iterator().next(), itemValues, myStyle));
      }
      else {
        itemValues.remove(selectedValue);
        assert !itemValues.contains(selectedValue);
        result.add(new EditedStyleItem(selectedValue, itemValues, myStyle));
      }
    }
    return result;
  }

  @NotNull
  public static List<EditedStyleItem> resolveAll(ThemeEditorStyle style, ThemeResolver resolver) {
    return new ThemeAttributeResolver(style, resolver).resolveAll();
  }
}
