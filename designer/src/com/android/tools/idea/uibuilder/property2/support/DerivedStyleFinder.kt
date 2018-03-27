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
package com.android.tools.idea.uibuilder.property2.support

import com.android.annotations.VisibleForTesting
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.ide.common.resources.ResourceResolver
import java.util.*

typealias StyleFilter = (StyleResourceValue) -> Boolean
typealias StyleOrder = (StyleResourceValue) -> Comparable<*>

/**
 * A class to find all styles that are derived from a given style
 * with transitive closure.
 */
class DerivedStyleFinder(private val myResolver: ResourceResolver?) {

  /**
   * Returns a [List] of styles that are derived from the `baseStyle`
   * specified. Known internal styles are filtered out. In addition the styles are filtered by the specified
   * filter.
   *
   * The result list is sorted as follows:
   *  1) User defined styles
   *  2) Styles grouped by namespace
   *
   * Each group is sorted by the specified sortOrder.
   *
   * @param baseStyle the style to find all derivatives of
   * @param filter apply this filter to remove any unwanted styles in the result
   * @param sortOrder the sort order to use. To sort by style name use: [.standardSortOrder]
   */
  fun find(baseStyle: StyleResourceValue, filter: StyleFilter, sortOrder: StyleOrder): List<StyleResourceValue> {
    return find(listOf(baseStyle), filter, sortOrder)
  }

  fun find(baseStyles: List<StyleResourceValue>, filter: StyleFilter, sortOrder: StyleOrder): List<StyleResourceValue> {
    if (myResolver == null) {
      return baseStyles
    }
    val bases = ArrayDeque(baseStyles)
    val styles = HashSet<StyleResourceValue>()
    while (!bases.isEmpty()) {
      val base = bases.pop()
      if (!styles.contains(base)) {
        styles.add(base)
        bases.addAll(myResolver.getChildren(base))
      }
    }
    return styles
      .filter { style -> !isHiddenStyle(style) && filter(style) }
      .sortedWith(compareBy({ !it.isUserDefined }, { it.namespace }, { sortOrder(it) }))
  }

  private fun isHiddenStyle(style: StyleResourceValue): Boolean {
    if (style.name.startsWith("Base.")) {
      // AppCompat contains several styles that serves as base styles and that should not be selectable:
      return true
    }
    // All lowercase styles in the framework should typically be hidden:
    return style.namespace == ResourceNamespace.ANDROID && style.name.toLowerCase(Locale.US) == style.name
  }
}
