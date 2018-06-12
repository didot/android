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
package com.android.tools.idea.common.property2.api

import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.intellij.openapi.actionSystem.AnAction
import javax.swing.Icon

/**
 * Model for a property editor.
 *
 * Instances of this class will be created for each property editor in the
 * [InspectorPanel] by [InspectorBuilder]s.
 * If only builtin editors are created (preferred) then clients should
 * never need to implement this interface.
 */
interface PropertyEditorModel {
  /**
   * The property this editor is for.
   *
   * This [property] is usually readonly except for models used during
   * table cell rendering where we want to cache editors for displaying
   * properties of the same type.
   */
  var property: PropertyItem

  /**
   * The value shown in the editor.
   */
  val value: String

  /**
   * Returns the line (if known) where this editor is kept.
   */
  var lineModel: InspectorLineModel?

  var onEnter: () -> Unit

  /**
   * Returns true if the action button should be focusable.
   *
   * Each editor can optionally display a button on the right of the editor.
   * This button is controlled by [ActionButtonSupport] which a [PropertyItem]
   * may implement.
   */
  val actionButtonFocusable: Boolean
    get() = (property as? ActionButtonSupport)?.actionButtonFocusable == true

  /**
   * The icon to show in the action button if present.
   */
  fun getActionIcon(focused: Boolean): Icon? {
    return (property as? ActionButtonSupport)?.getActionIcon(focused)
  }

  /**
   * The action performed when user presses the action button if any.
   */
  val buttonAction: AnAction?
    get() = (property as? ActionButtonSupport)?.getAction()

  /**
   * Controls the visibility of the editor.
   *
   * The editor should show/hide itself based on this value.
   */
  var visible: Boolean

  /**
   * Returns true if the editor currently has the focus.
   */
  val hasFocus: Boolean

  /**
   * Request focus to be placed on this editor.
   */
  fun requestFocus()

  /**
   * Toggle the value of this editor.
   *
   * This is a noop for most editors.
   * Boolean editors should implement this method.
   */
  fun toggleValue()

  /**
   * Update the value shown in the editor.
   */
  fun refresh()

  /**
   * Cancel editing and revert to the value of the property.
   *
   * i.e. discard changes by the editor that are not yet applied.
   */
  fun cancelEditing()

  /**
   * Add a listener that respond to value changes.
   */
  fun addListener(listener: ValueChangedListener)

  /**
   * Remove the value listener.
   */
  fun removeListener(listener: ValueChangedListener)
}
