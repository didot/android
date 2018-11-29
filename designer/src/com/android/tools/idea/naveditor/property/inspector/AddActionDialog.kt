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
package com.android.tools.idea.naveditor.property.inspector

import com.android.SdkConstants.*
import com.android.annotations.VisibleForTesting
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.naveditor.analytics.NavUsageTracker
import com.android.tools.idea.naveditor.model.*
import com.android.tools.idea.naveditor.property.editors.getAnimatorsPopupContent
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.uibuilder.property.editors.support.ValueWithDisplayString
import com.google.wireless.android.sdk.stats.NavEditorEvent
import com.google.wireless.android.sdk.stats.NavEditorEvent.NavEditorEventType.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Computable
import com.intellij.ui.ListCellRendererWrapper
import com.intellij.util.text.nullize
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.jetbrains.android.dom.navigation.NavigationSchema.*
import java.awt.Font
import java.awt.event.ActionListener
import java.awt.event.ItemListener
import javax.swing.Action
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JList

/**
 * Shows an [AddActionDialog] and then updates the corresponding model.
 */
@VisibleForTesting
fun showAndUpdateFromDialog(actionDialog: AddActionDialog, surface: DesignSurface?, source: NavEditorEvent.Source, hadExisting: Boolean) {
  if (actionDialog.showAndGet()) {
    val action = actionDialog.writeUpdatedAction()
    surface?.selectionModel?.setSelection(listOf(action))
    NavUsageTracker.getInstance(surface).createEvent(if (hadExisting) EDIT_ACTION else CREATE_ACTION)
      .withActionInfo(action)
      .withSource(source)
      .log()
  }
}

/**
 * Create a new action for the given component
 */
// Open for testing only
open class AddActionDialog(
  defaultsType: Defaults,
  private val existingAction: NlComponent?,
  private val parent: NlComponent
) : DialogWrapper(false) {

  private var previousPopTo: DestinationListEntry? = null
  private var previousInclusive: Boolean = false
  private var generatedId: String = ""

  @VisibleForTesting
  val dialog = AddActionDialogUI()

  // Open for testing
  open val id: String?
    get() = dialog.myIdTextField.text

  // Open for testing
  open val source: NlComponent
    get() = dialog.myFromComboBox.selectedItem as NlComponent

  // Open for testing
  open val destination: NlComponent?
    get() {
      val item = dialog.myDestinationComboBox.selectedItem as DestinationListEntry?
      return item?.component
    }

  // Open for testing
  open val enterTransition: String?
    get() = (dialog.myEnterComboBox.selectedItem as ValueWithDisplayString?)?.value

  // Open for testing
  open val exitTransition: String?
    get() = (dialog.myExitComboBox.selectedItem as ValueWithDisplayString?)?.value

  // Open for testing
  open val popTo: String?
    get() = (dialog.myPopToComboBox.selectedItem as? DestinationListEntry)?.component?.id

  // Open for testing
  open val isInclusive: Boolean
    get() = dialog.myInclusiveCheckBox.isSelected

  // Open for testing
  open val popEnterTransition: String?
    get() = (dialog.myPopEnterComboBox.selectedItem as ValueWithDisplayString?)?.value

  // Open for testing
  open val popExitTransition: String?
    get() = (dialog.myPopExitComboBox.selectedItem as ValueWithDisplayString?)?.value

  // Open for testing
  open val isSingleTop: Boolean
    get() = dialog.mySingleTopCheckBox.isSelected

  enum class Defaults {
    NORMAL, RETURN_TO_SOURCE, GLOBAL
  }

  init {
    val model = parent.model
    setUpComponents(model)

    dialog.myFromComboBox.addItem(parent)

    if (existingAction != null) {
      setupFromExisting()
    } else {
      setDefaults(defaultsType)
      generatedId = dialog.myIdTextField.text
    }

    init()

    title = if (existingAction == null) {
      myOKAction.putValue(Action.NAME, "Add")
      "Add Action"
    } else {
      myOKAction.putValue(Action.NAME, "Update")
      "Update Action"
    }

    val idUpdater = ItemListener {
      if (dialog.myIdTextField.text == generatedId) {
        generatedId = generateActionId(source, destination?.id, popTo, isInclusive)
        dialog.myIdTextField.text = generatedId
        // once the text is generated, don't show the empty text again.
        dialog.myIdTextField.emptyText.text = ""
      }
    }
    dialog.myDestinationComboBox.addItemListener(idUpdater)
    dialog.myPopToComboBox.addItemListener(idUpdater)
    dialog.myInclusiveCheckBox.addItemListener(idUpdater)

    dialog.myIdTextField.emptyText.text = "generated"
  }

  final override fun init() {
    super.init()
  }

  private fun setDefaults(type: Defaults) {
    populateDestinations()
    if (type == Defaults.GLOBAL) {
      val sourceNav = parent.parent!!
      dialog.myFromComboBox.addItem(sourceNav)
      dialog.myFromComboBox.selectedIndex = dialog.myFromComboBox.itemCount - 1
      selectItem(dialog.myDestinationComboBox, { it.component }, parent)
    } else if (type == Defaults.RETURN_TO_SOURCE) {
      selectItem(dialog.myPopToComboBox, { it.component }, parent)
      dialog.myInclusiveCheckBox.isSelected = true
      selectItem(dialog.myDestinationComboBox, { entry -> entry.isReturnToSource }, true)
    }
    dialog.myIdTextField.text = generateActionId(source, destination?.id, popTo, isInclusive)
  }

  private fun populateDestinations() {
    dialog.myDestinationComboBox.addItem(null)
    dialog.myDestinationComboBox.addItem(RETURN_TO_SOURCE)
    dialog.myDestinationComboBox.addItem(SEPARATOR)

    populateComboBox(dialog.myDestinationComboBox, { true })
  }

  private fun populatePopTo() {
    dialog.myPopToComboBox.addItem(null)

    populateComboBox(dialog.myPopToComboBox, { it.isFragment || it.isNavigation })
  }

  private fun populateComboBox(comboBox: JComboBox<DestinationListEntry>, filter: (NlComponent) -> Boolean) {
    val visibleDestinations = parent.visibleDestinations

    parent.parentSequence().forEach {
      comboBox.addItem(
        if (it.isNavigation) DestinationListEntry.Parent(it)
        else DestinationListEntry(it))

      visibleDestinations[it]?.filter(filter)
        ?.forEach {
          comboBox.addItem(DestinationListEntry(it))
        }
    }
  }

  private fun setupFromExisting() {
    if (existingAction == null) {
      return
    }

    dialog.myFromComboBox.addItem(existingAction.parent)

    if (!existingAction.parent!!.isRoot) {
      dialog.myFromComboBox.addItem(existingAction.parent)
    }

    val destination = existingAction.actionDestinationId
    dialog.myDestinationComboBox.addItem(
      DestinationListEntry(destination?.let { existingAction.parent!!.findVisibleDestination(destination) })
    )
    dialog.myDestinationComboBox.selectedIndex = 0
    dialog.myDestinationComboBox.isEnabled = false

    selectItem(dialog.myPopToComboBox, { it.component?.getAttribute(ANDROID_URI, ATTR_ID) }, NavigationSchema.ATTR_POP_UP_TO, AUTO_URI, existingAction)
    dialog.myInclusiveCheckBox.isSelected = existingAction.inclusive
    selectItem(dialog.myEnterComboBox, { it.value }, ATTR_ENTER_ANIM, AUTO_URI, existingAction)
    selectItem(dialog.myExitComboBox, { it.value }, ATTR_EXIT_ANIM, AUTO_URI, existingAction)
    selectItem(dialog.myPopEnterComboBox, { it.value }, ATTR_POP_ENTER_ANIM, AUTO_URI, existingAction)
    selectItem(dialog.myPopExitComboBox, { it.value }, ATTR_POP_EXIT_ANIM, AUTO_URI, existingAction)
    dialog.mySingleTopCheckBox.isSelected = existingAction.singleTop
    dialog.myIdTextField.text = existingAction.id
  }

  private fun <T, U> selectItem(
    comboBox: JComboBox<T>,
    valueGetter: (T) -> U,
    targetValue: U?
  ) {
    for (i in 0 until comboBox.itemCount) {
      val item = comboBox.getItemAt(i)
      val value = if (item == null) null else valueGetter(item)
      if (targetValue == value) {
        comboBox.selectedIndex = i
        return
      }
    }
  }

  private fun <T> selectItem(
    comboBox: JComboBox<T>,
    valueGetter: (T) -> String?,
    attrName: String,
    namespace: String?,
    component: NlComponent
  ) {
    var targetValue = component.getAttribute(namespace, attrName)
    targetValue = stripPlus(targetValue)
    selectItem(comboBox, { c -> stripPlus(valueGetter(c)) }, targetValue)
  }

  private fun stripPlus(targetValue: String?): String? {
    var result = targetValue
    if (result != null) {
      if (result.startsWith("@+")) {
        result = "@" + result.substring(2)
      }
    }
    return result
  }


  private fun setUpComponents(model: NlModel) {
    val sourceRenderer = object : ListCellRendererWrapper<NlComponent>() {
      override fun customize(list: JList<*>, value: NlComponent?, index: Int, selected: Boolean, hasFocus: Boolean) {
        if (value == null) {
          setText("None")
        } else {
          setText(value.uiName)
        }
      }
    }

    dialog.myFromComboBox.renderer = sourceRenderer
    dialog.myFromComboBox.isEnabled = false

    val destinationRenderer = object : ListCellRendererWrapper<DestinationListEntry>() {
      override fun customize(list: JList<*>, value: DestinationListEntry?, index: Int, selected: Boolean, hasFocus: Boolean) {
        when {
          value == null -> setText("None")
          value.isReturnToSource -> setText("↵ Source")
          value.isSeparator -> setSeparator()
          else -> {
            val component = value.component
            var text = if (component?.parent == null) "Root" else component.uiName
            if (value.isParent) {
              setFont(list.font.deriveFont(Font.BOLD))
            }
            if (component === parent) {
              text += " (Self)"
            }
            else if (index != -1 && !value.isParent) {
              text = "  " + text
            }
            setText(text)
          }
        }
      }
    }

    dialog.myDestinationComboBox.renderer = destinationRenderer

    val repoManager = ResourceRepositoryManager.getOrCreateInstance(model.module)
    val destinationListener = ActionListener {
      dialog.myEnterComboBox.removeAllItems()
      dialog.myExitComboBox.removeAllItems()
      dialog.myEnterComboBox.addItem(ValueWithDisplayString("None", null))
      dialog.myExitComboBox.addItem(ValueWithDisplayString("None", null))

      dialog.myPopEnterComboBox.removeAllItems()
      dialog.myPopExitComboBox.removeAllItems()
      dialog.myPopEnterComboBox.addItem(ValueWithDisplayString("None", null))
      dialog.myPopExitComboBox.addItem(ValueWithDisplayString("None", null))

      val component = (dialog.myDestinationComboBox.selectedItem as? DestinationListEntry)?.component
                      ?: (dialog.myPopToComboBox.selectedItem as? DestinationListEntry)?.component
                      ?: return@ActionListener

      if (repoManager != null) {
        getAnimatorsPopupContent(repoManager, component.isFragment)
          .forEach { item ->
            dialog.myEnterComboBox.addItem(item)
            dialog.myExitComboBox.addItem(item)
            dialog.myPopEnterComboBox.addItem(item)
            dialog.myPopExitComboBox.addItem(item)
          }
      }
    }

    dialog.myDestinationComboBox.addActionListener(destinationListener)
    dialog.myPopToComboBox.addActionListener(destinationListener)

    dialog.myDestinationComboBox.addActionListener {
      val item = dialog.myDestinationComboBox.selectedItem as? DestinationListEntry
      if (item != null && item.isReturnToSource) {
        previousPopTo = dialog.myPopToComboBox.selectedItem as DestinationListEntry?
        previousInclusive = dialog.myInclusiveCheckBox.isSelected
        selectItem(dialog.myPopToComboBox, {it.component }, parent)
        dialog.myPopToComboBox.isEnabled = false
        dialog.myInclusiveCheckBox.isSelected = true
        dialog.myInclusiveCheckBox.isEnabled = false
      } else {
        if (!dialog.myPopToComboBox.isEnabled) {
          selectItem(dialog.myPopToComboBox, { it }, previousPopTo)
          dialog.myPopToComboBox.selectedItem = previousPopTo
          dialog.myInclusiveCheckBox.isSelected = previousInclusive
          dialog.myPopToComboBox.isEnabled = true
          dialog.myInclusiveCheckBox.isEnabled = true
        }
      }
    }

    dialog.myEnterComboBox.addItem(ValueWithDisplayString("None", null))
    dialog.myExitComboBox.addItem(ValueWithDisplayString("None", null))
    dialog.myPopEnterComboBox.addItem(ValueWithDisplayString("None", null))
    dialog.myPopExitComboBox.addItem(ValueWithDisplayString("None", null))

    populatePopTo()
    dialog.myPopToComboBox.renderer = destinationRenderer
  }

  override fun doValidate(): ValidationInfo? {
    return if (destination == null && popTo == null) {
      ValidationInfo("Destination must be set!", dialog.myDestinationComboBox)
    } else if (id.isNullOrBlank()) {
      ValidationInfo("ID must be set!", dialog.myIdTextField)
    } else if (destination != null && destination?.id == null) {
      ValidationInfo("Destination has no ID", dialog.myDestinationComboBox)
    } else if (dialog.myPopToComboBox.selectedItem != null && popTo == null) {
      // TODO: it would be nice if we could just disable those items in the popups, but JComboBox doesn't support disabling items
      ValidationInfo("Pop To destination has no ID", dialog.myDestinationComboBox)
    }
    else super.doValidate()
  }

  override fun createActions(): Array<Action> {
    return arrayOf(okAction, cancelAction)
  }

  override fun createCenterPanel(): JComponent? {
    return dialog.myContentPanel
  }

  // Open for testing
  @VisibleForTesting
  open fun writeUpdatedAction(): NlComponent {
    return WriteCommandAction.runWriteCommandAction(
      parent.model.project, Computable<NlComponent> {
      val actionSetup: NlComponent.() -> Unit = {
        actionDestinationId = destination?.id
        enterAnimation = enterTransition
        exitAnimation = exitTransition
        popUpTo = popTo
        inclusive = isInclusive
        popEnterAnimation = popEnterTransition
        popExitAnimation = popExitTransition
        singleTop = isSingleTop
      }
      existingAction?.apply(actionSetup) ?: source.createAction(actionSetup = actionSetup, id = id.nullize(true))
    })
  }

  @VisibleForTesting
  open class DestinationListEntry(component: NlComponent?) {
    var component: NlComponent? = null

    open val isSeparator: Boolean
      get() = false

    open val isParent: Boolean
      get() = false

    open val isReturnToSource: Boolean
      get() = false

    internal class Parent(component: NlComponent?) : DestinationListEntry(component) {

      override val isParent: Boolean
        get() = true
    }

    init {
      this.component = component
    }
  }
}

private val SEPARATOR = object : AddActionDialog.DestinationListEntry(null) {
  override val isSeparator: Boolean
    get() = true
}

private val RETURN_TO_SOURCE = object : AddActionDialog.DestinationListEntry(null) {
  override val isReturnToSource: Boolean
    get() = true
}
