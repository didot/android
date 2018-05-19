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
package com.android.tools.idea.common.property2.api

import com.intellij.util.text.Matcher

private const val ERROR_NOT_SEARCHABLE = "Component is not searchable"

/**
 * The model of a line in an inspector.
 *
 * The inspector is generated from a list of [InspectorBuilder]s which
 * in turn generates a list of lines in the inspector.
 */
interface InspectorLineModel {
  /**
   * Controls the visibility of a line in the inspector.
   *
   * The inspector will set this property based on sections being collapsed
   * or search filters being applied.
   */
  var visible: Boolean

  /**
   * Parent if this line is a subsection of [parent].
   *
   * The inspector will set this property when a parent is adding a child.
   */
  var parent: InspectorLineModel?

  /**
   * If true, this line should be hidden at all times.
   *
   * An [InspectorBuilder] may choose to hide certain lines based on the
   * value of certain properties. Some components have properties that only
   * make sense if another property has certain values.
   * When this property is true, [visible] should return false independently
   * of inspector filters and collapsible sections.
   */
  var hidden: Boolean

  /**
   * Returns true if this line can receive focus.
   */
  val focusable: Boolean

  /**
   * Request focus to be placed on the next line of the lines containing this line.
   */
  var gotoNextLine: (InspectorLineModel) -> Unit

  /**
   * Request the focus to be placed in this line.
   */
  fun requestFocus()

  /**
   * Return true if it is possible to search inside this line.
   *
   * Most lines are not searchable. Some lines may contain complex content,
   * where searching is possible.
   */
  val isSearchable: Boolean
    get() = false

  /**
   * The filter for a search can be specified by setting [filter].
   *
   * This method should only be used uf [isSearchable] returns true.
   */
  var filter: String
    get() = ""
    set(value) = error(ERROR_NOT_SEARCHABLE)


  /**
   * Return true, if this line should be shown for the specified search filter.
   *
   * For implementing search in the inspector, allow a line to control
   * whether it is a search match. This method will never be called if
   * [isSearchable] is true.
   * @param matcher the current string matcher.
   * @return true if this line is a match, false if not.
   */
  fun isMatch(matcher: Matcher): Boolean = false

  /**
   * Refresh the content after a potential property value change.
   */
  fun refresh() {}

  /**
   * Make this line expandable.
   *
   * Must be called before calling addChild.
   * Note: Use with care since not all lines can be made expandable. If
   * [initiallyExpanded] is true the group should be "open" initially
   * unless we are restoring this state from earlier.
   */
  fun makeExpandable(initiallyExpanded: Boolean) { throw IllegalStateException() }

  /**
   * Add a line as a child in a sub section under this line.
   */
  fun addChild(child: InspectorLineModel) { throw IllegalStateException() }
}
