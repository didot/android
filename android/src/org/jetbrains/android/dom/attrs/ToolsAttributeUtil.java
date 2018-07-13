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
package org.jetbrains.android.dom.attrs;

import com.android.ide.common.rendering.api.AttributeFormat;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.resources.ResourceType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.converters.*;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.SdkConstants.*;
import static java.util.Collections.singletonList;

/**
 * Class containing utility methods to handle XML attributes in the "tools" namespace.
 * <p/>
 * Tools attributes are described in several documents:
 * <ul>
 *   <li><a href="https://developer.android.com/studio/write/tool-attributes.html#design-time_view_attributes">layout attributes</a></li>
 *   <li><a href="https://developer.android.com/studio/build/manifest-merge.html#merge_rule_markers">manifest attributes</a></li>
 * </ul>
 */
public class ToolsAttributeUtil {
  private static final ResolvingConverter LAYOUT_REFERENCE_CONVERTER =
    new ResourceReferenceConverter(EnumSet.of(ResourceType.LAYOUT));
  private static final ResolvingConverter ACTIVITY_CLASS_CONVERTER = new PackageClassConverter(true, false, AndroidUtils.ACTIVITY_BASE_CLASS_NAME);
  private static final ResolvingConverter VIEW_CONVERTER = new ViewClassConverter();
  private static final ResolvingConverter VIEW_GROUP_CONVERTER = new ViewGroupClassConverter();

  private static final List<AttributeFormat> NO_FORMATS = Collections.emptyList();

  // Manifest merger attribute names
  public static final String ATTR_NODE = "node";
  public static final String ATTR_STRICT = "strict";
  public static final String ATTR_REMOVE = "remove";
  public static final String ATTR_REPLACE = "replace";
  public static final String ATTR_OVERRIDE_LIBRARY = "overrideLibrary";

  /** List of all the tools namespace attributes and their formats. */
  private static final ImmutableMap<String, List<AttributeFormat>> ATTRIBUTES = ImmutableMap.<String, List<AttributeFormat>>builder()
    // Layout files attributes
    .put(ATTR_ACTION_BAR_NAV_MODE, singletonList(AttributeFormat.FLAGS))
    .put(ATTR_CONTEXT, ImmutableList.of(AttributeFormat.REFERENCE, AttributeFormat.STRING))
    .put(ATTR_IGNORE, NO_FORMATS)
    .put(ATTR_LISTFOOTER,  singletonList(AttributeFormat.REFERENCE))
    .put(ATTR_LISTHEADER, singletonList(AttributeFormat.REFERENCE))
    .put(ATTR_LISTITEM, singletonList(AttributeFormat.REFERENCE))
    .put(ATTR_LAYOUT, singletonList(AttributeFormat.REFERENCE))
    .put(ATTR_LOCALE, NO_FORMATS)
    .put(ATTR_MENU, NO_FORMATS)
    .put(ATTR_MOCKUP, singletonList(AttributeFormat.STRING))
    .put(ATTR_MOCKUP_OPACITY, singletonList(AttributeFormat.FLOAT))
    .put(ATTR_MOCKUP_CROP, singletonList(AttributeFormat.STRING))
    .put(ATTR_OPEN_DRAWER, singletonList(AttributeFormat.ENUM))
    .put(ATTR_PARENT_TAG, singletonList(AttributeFormat.STRING))
    .put(ATTR_SHOW_IN, singletonList(AttributeFormat.REFERENCE))
    .put(ATTR_TARGET_API, NO_FORMATS)
    // Manifest merger attributes
    .put(ATTR_NODE, singletonList(AttributeFormat.ENUM))
    .put(ATTR_STRICT, NO_FORMATS)
    .put(ATTR_REMOVE, NO_FORMATS)
    .put(ATTR_REPLACE, NO_FORMATS)
    .put(ATTR_OVERRIDE_LIBRARY, NO_FORMATS)
    // Raw files attributes
    .put(ATTR_SHRINK_MODE, singletonList(AttributeFormat.ENUM))
    .put(ATTR_KEEP, NO_FORMATS)
    .put(ATTR_DISCARD, NO_FORMATS)
    .put(ATTR_USE_HANDLER, singletonList(AttributeFormat.REFERENCE))
    // AppCompatImageView srcCompat attribute
    // TODO: Remove this definition and make sure the app namespace attributes are handled by AndroidDomUtil#getAttributeDefinition
    .put(ATTR_SRC_COMPAT, singletonList(AttributeFormat.REFERENCE))
    .build();
  /** List of converters to be applied to some of the attributes */
  private static final ImmutableMap<String, ResolvingConverter> CONVERTERS = ImmutableMap.<String, ResolvingConverter>builder()
    .put(ATTR_ACTION_BAR_NAV_MODE, new StaticEnumConverter("standard", "list", "tabs"))
    .put(ATTR_OPEN_DRAWER, new StaticEnumConverter("start", "end", "left", "right"))
    .put(ATTR_CONTEXT, ACTIVITY_CLASS_CONVERTER)
    .put(ATTR_LISTFOOTER, LAYOUT_REFERENCE_CONVERTER)
    .put(ATTR_LISTHEADER, LAYOUT_REFERENCE_CONVERTER)
    .put(ATTR_LISTITEM, LAYOUT_REFERENCE_CONVERTER)
    .put(ATTR_LAYOUT, LAYOUT_REFERENCE_CONVERTER)
    .put(ATTR_SHOW_IN, LAYOUT_REFERENCE_CONVERTER)
    .put(ATTR_NODE, new StaticEnumConverter("merge", "replace", "strict", "merge-only-attributes", "remove", "removeAll"))
    .put(ATTR_TARGET_API, new TargetApiConverter())
    .put(ATTR_SHRINK_MODE, new StaticEnumConverter(VALUE_STRICT, VALUE_SAFE))
    .put(ATTR_USE_HANDLER, VIEW_CONVERTER)
    .put(ATTR_PARENT_TAG, VIEW_GROUP_CONVERTER)
    .build();

  /**
   * Returns a {@link ResolvingConverter} for the given attribute definition
   */
  @Nullable
  public static ResolvingConverter getConverter(@NotNull AttributeDefinition attrDef) {
    String name = attrDef.getName();
    ResolvingConverter converter = CONVERTERS.get(name);

    return converter != null ? converter : AndroidDomUtil.getConverter(attrDef);
  }

  /**
   * Returns a set with the names of all the tools namespace attributes.
   */
  @NotNull
  public static Set<String> getAttributeNames() {
    return ATTRIBUTES.keySet();
  }

  /**
   * Returns an {@link AttributeDefinition} for the attribute with the given name. If the attribute is not defined
   * in the tools namespace, null will be returned.
   */
  @Nullable
  public static AttributeDefinition getAttrDefByName(@NotNull String name) {
    if (!ATTRIBUTES.containsKey(name)) {
      return null;
    }

    Collection<AttributeFormat> formats = ATTRIBUTES.get(name);
    return new AttributeDefinition(ResourceNamespace.TOOLS, name, null, formats);
  }
}
