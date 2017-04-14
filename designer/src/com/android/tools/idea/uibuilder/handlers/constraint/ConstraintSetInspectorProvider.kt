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
package com.android.tools.idea.uibuilder.handlers.constraint

import com.android.SdkConstants
import com.android.SdkConstants.*
import com.android.tools.idea.uibuilder.model.NlComponent
import com.android.tools.idea.uibuilder.property.EmptyProperty
import com.android.tools.idea.uibuilder.property.NlPropertiesManager
import com.android.tools.idea.uibuilder.property.NlProperty
import com.android.tools.idea.uibuilder.property.editors.NlBaseComponentEditor
import com.android.tools.idea.uibuilder.property.editors.NlComponentEditor
import com.android.tools.idea.uibuilder.property.editors.NlEditingListener
import com.android.tools.idea.uibuilder.property.editors.NlEditingListener.DEFAULT_LISTENER
import com.android.tools.idea.uibuilder.property.inspector.InspectorComponent
import com.android.tools.idea.uibuilder.property.inspector.InspectorPanel
import com.android.tools.idea.uibuilder.property.inspector.InspectorProvider
import java.awt.BorderLayout
import java.util.*
import java.util.Arrays.asList
import javax.swing.*
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener

/**
 * Implements an inspector provider for the ConstraintSet in ConstraintLayout.
 */
class ConstraintSetInspectorProvider : InspectorProvider {
  private var inspectorComponent: InspectorComponent = ConstraintLayoutInspectorComponent()

  /**
   * Returns true if this {@link InspectorProvider} should be used for the given
   * components and properties.
   */
  override fun isApplicable(components: MutableList<NlComponent>,
                            properties: MutableMap<String, NlProperty>,
                            propertiesManager: NlPropertiesManager): Boolean {
    if (components.size != 1) {
      return false;
    }
    val component = components[0]
    if (!component.isOrHasSuperclass(SdkConstants.CLASS_CONSTRAINT_LAYOUT)) {
      return false
    }
    if (component.getLiveAttribute(SHERPA_URI, ATTR_LAYOUT_CONSTRAINTSET) == null) {
      return false
    }
    return true
  }

  /**
   * Return an {@link InspectorComponent} for editing a subset of properties for
   * the given components.<br/>
   * The provider may choose to cache a {@link InspectorComponent} with editors
   * for a given set of component types and properties.
   */
  override fun createCustomInspector(components: MutableList<NlComponent>,
                                     properties: MutableMap<String, NlProperty>,
                                     propertiesManager: NlPropertiesManager): InspectorComponent {
    inspectorComponent.updateProperties(components, properties, propertiesManager)
    return inspectorComponent
  }

  /**
   * Get rid of cache that a provider may maintain.
   */
  override fun resetCache() {
    // Do nothing for now
  }

  /**
   * Combobox editor for the constraint set attribute
   */
  private class ConstraintSetEditor(listener: NlEditingListener, inspector: ConstraintLayoutInspectorComponent) : NlBaseComponentEditor(listener), PopupMenuListener {
    var myPanel: JPanel = JPanel(BorderLayout())
    var myProperty: NlProperty = EmptyProperty.INSTANCE
    var myComboBox: JComboBox<String> = JComboBox<String>()
    private var myList: ArrayList<String> = ArrayList()
    private var myInspector: ConstraintLayoutInspectorComponent
    private var myPopupChanged = false

    init {
      myInspector = inspector
      myPanel.add(myComboBox)
      myComboBox.addPopupMenuListener(this)
      myComboBox.addActionListener { myPopupChanged = true }
    }

    override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent?) {
      if (myPopupChanged) {
        myPopupChanged = false
        var value: String = (myComboBox.selectedItem as String)
        stopEditing(value)
      }
    }

    override fun popupMenuCanceled(e: PopupMenuEvent?) {
      myPopupChanged = false
    }

    override fun popupMenuWillBecomeVisible(e: PopupMenuEvent?) {
      // Do nothing
    }

    override fun getComponent(): JComponent {
      return myPanel
    }

    override fun getProperty(): NlProperty {
      return myProperty
    }

    override fun setProperty(property: NlProperty) {
      myProperty = property
    }

    /**
     * Fill the combobox with the given array
     */
    fun setList(list: ArrayList<String>, current: String?) {
      myList = list
      myComboBox.removeAllItems()
      for (element in myList) {
        myComboBox.addItem(element)
      }
      myComboBox.selectedItem = current
    }
  }

  /**
   * Inspector for the constraint set attribute.
   */
  private class ConstraintLayoutInspectorComponent() : InspectorComponent {

    private val myConstraintSetEditor: ConstraintSetEditor
    private var myConstraintSet: NlProperty? = null
    private var myComponent: NlComponent? = null

    init {
      myConstraintSetEditor = ConstraintSetEditor(DEFAULT_LISTENER, this)
    }

    override fun refresh() {
      // do nothing
    }

    override fun updateProperties(components: MutableList<NlComponent>,
                                  properties: MutableMap<String, NlProperty>,
                                  propertiesManager: NlPropertiesManager) {
      myConstraintSet = properties[ATTR_LAYOUT_CONSTRAINTSET]
      myComponent = components[0]
      myConstraintSetEditor.property = myConstraintSet!!
      populate()
    }

    override fun getMaxNumberOfRows(): Int {
      return 2
    }

    override fun attachToInspector(inspector: InspectorPanel) {
      inspector.addTitle("ConstraintSet")
      refresh()
      myConstraintSetEditor.setLabel(inspector.addComponent(ATTR_LAYOUT_CONSTRAINTSET, myConstraintSet?.getTooltipText(), myConstraintSetEditor.getComponent()))
    }

    override fun getEditors(): MutableList<NlComponentEditor> {
      return asList(myConstraintSetEditor)
    }

    /**
     * Populate the inspector's combobox by looking at the constraints children of the element
     */
    fun populate() {
      if (myComponent == null) {
        return
      }

      var list: ArrayList<String> = ArrayList()
      for (child in myComponent!!.getChildren()) {
        if (child.isOrHasSuperclass(CLASS_CONSTRAINT_LAYOUT_CONSTRAINTS)) {
          var attribute = child.getLiveAttribute(ANDROID_URI, ATTR_ID)
          if (attribute != null) {
            list.add(attribute)
          }
        }
      }
      var constraintSet = myComponent!!.getLiveAttribute(SHERPA_URI, ATTR_LAYOUT_CONSTRAINTSET)
      myConstraintSetEditor.setList(list, constraintSet)
    }
  }
}