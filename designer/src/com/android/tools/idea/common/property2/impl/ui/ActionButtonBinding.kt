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
package com.android.tools.idea.common.property2.impl.ui

import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.adtui.stdui.KeyStrokes
import com.android.tools.adtui.stdui.registerActionKey
import com.android.tools.idea.common.property2.api.HelpSupport
import com.android.tools.idea.common.property2.api.PropertyEditorModel
import com.android.tools.idea.common.property2.impl.support.ImageFocusListener
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent

/**
 * A standard class for implementing a browse button for an editor.
 *
 * The editor component is wrapped in panel with a possible icon to the right displaying of the editor.
 */
class ActionButtonBinding(private val model: PropertyEditorModel,
                          private val editor: JComponent) : CellPanel(), DataProvider {
  private val actionButton = ButtonWithCustomTooltip()
  private val actionButtonModel
    get() = model.property.browseButton

  init {
    add(editor, BorderLayout.CENTER)
    add(actionButton, BorderLayout.EAST)
    updateFromModel()

    actionButton.registerActionKey({ buttonPressed(null) }, KeyStrokes.SPACE, "space")
    actionButton.registerActionKey({ buttonPressed(null) }, KeyStrokes.ENTER, "enter")
    model.addListener(ValueChangedListener { updateFromModel() })

    actionButton.addMouseListener(object: MouseAdapter() {
      override fun mousePressed(event: MouseEvent) {
        buttonPressed(event)
      }
    })
    actionButton.isFocusable = actionButtonModel?.actionButtonFocusable ?: false
    actionButton.addFocusListener(ImageFocusListener(actionButton) { updateFromModel() })
  }

  override fun requestFocus() {
    editor.requestFocus()
  }

  private fun updateFromModel() {
    actionButton.icon = actionButtonModel?.getActionIcon(actionButton.hasFocus())
    isVisible = model.visible
  }

  override fun getData(dataId: String): Any? {
    if (HelpSupport.PROPERTY_ITEM.`is`(dataId)) {
      return model.property
    }
    return null
  }

  private fun buttonPressed(mouseEvent: MouseEvent?) {
    val action = actionButtonModel?.action ?: return
    if (action is ActionGroup) {
      val popupMenu = ActionManager.getInstance().createActionPopupMenu(ToolWindowContentUi.POPUP_PLACE, action)
      val location = locationFromEvent(mouseEvent)
      popupMenu.component.show(this, location.x, location.y)
    }
    else {
      val event = AnActionEvent.createFromAnAction(action, mouseEvent, ActionPlaces.UNKNOWN,
                                                   DataManager.getInstance().getDataContext(editor))
      action.actionPerformed(event)
      model.refresh()
    }
  }

  private fun locationFromEvent(mouseEvent: MouseEvent?): Point {
    if (mouseEvent != null) {
      return mouseEvent.locationOnScreen
    }
    val location = actionButton.locationOnScreen
    return Point(location.x + actionButton.width / 2, location.y + actionButton.height / 2)
  }

  private inner class ButtonWithCustomTooltip : JBLabel() {

    override fun getToolTipText(event: MouseEvent): String? {
      // Trick: Use the component from the event.source for tooltip in tables. See TableEditor.getToolTip().
      val component = event.source as? JComponent ?: this
      return PropertyTooltip.setToolTip(component, event, actionButtonModel?.action?.templatePresentation?.description)
    }
  }
}
