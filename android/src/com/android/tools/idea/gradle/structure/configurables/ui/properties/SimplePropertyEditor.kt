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
package com.android.tools.idea.gradle.structure.configurables.ui.properties

import com.android.annotations.VisibleForTesting
import com.android.tools.idea.gradle.structure.configurables.ui.RenderedComboBox
import com.android.tools.idea.gradle.structure.configurables.ui.TextRenderer
import com.android.tools.idea.gradle.structure.configurables.ui.continueOnEdt
import com.android.tools.idea.gradle.structure.configurables.ui.invokeLater
import com.android.tools.idea.gradle.structure.configurables.ui.toRenderer
import com.android.tools.idea.gradle.structure.model.PsVariablesScope
import com.android.tools.idea.gradle.structure.model.meta.Annotated
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyContext
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyCore
import com.android.tools.idea.gradle.structure.model.meta.ModelSimpleProperty
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.PropertyValue
import com.android.tools.idea.gradle.structure.model.meta.ValueAnnotation
import com.android.tools.idea.gradle.structure.model.meta.annotated
import com.android.tools.idea.gradle.structure.model.meta.getText
import com.android.tools.idea.gradle.structure.model.meta.getValue
import com.android.tools.idea.gradle.structure.model.meta.valueFormatter
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultComboBoxModel
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.table.TableCellEditor

/**
 * A property editor [ModelPropertyEditor] for properties of simple (not complex) types.
 *
 * This is a [ComboBox] based editor allowing manual text entry as well as entry by selecting an item from the list of values provided by
 * [ModelSimpleProperty.getKnownValues]. Text free text input is parsed by [ModelSimpleProperty.parse].
 */
class SimplePropertyEditor<PropertyT : Any, ModelPropertyT : ModelPropertyCore<PropertyT>>(
  property: ModelPropertyT,
  propertyContext: ModelPropertyContext<PropertyT>,
  variablesScope: PsVariablesScope?,
  private val extensions: List<EditorExtensionAction<PropertyT, ModelPropertyT>>,
  cellEditor: TableCellEditor? = null,
  private val logValueEdited: () -> Unit = {}
) :
  PropertyEditorBase<ModelPropertyT, PropertyT>(property, propertyContext, variablesScope),
  ModelPropertyEditor<PropertyT>,
  ModelPropertyEditorFactory<PropertyT, ModelPropertyT> {

  init {
    check(
      extensions.count { it.isMainAction } <= 1) {
      "Multiple isMainAction == true editor extensions: ${extensions.filter { it.isMainAction }}"
    }
  }

  private var knownValueRenderers: Map<ParsedValue<PropertyT>, ValueRenderer> = mapOf()
  private var disposed = false
  private var knownValuesFuture: ListenableFuture<Unit>? = null  // Accessed only from the EDT.
  private val formatter = propertyContext.valueFormatter()

  private val renderedComboBox = object : RenderedComboBox<Annotated<ParsedValue<PropertyT>>>(DefaultComboBoxModel()) {

    override fun getPreferredSize(): Dimension {
      val dimensions = super.getPreferredSize()
      return if (dimensions.width < 200) {
        Dimension(200, dimensions.height)
      }
      else dimensions
    }

    override fun parseEditorText(text: String): Annotated<ParsedValue<PropertyT>>? = propertyContext.parseEditorText(text)

    override fun toEditorText(anObject: Annotated<ParsedValue<PropertyT>>?): String = when (anObject) {
      null -> ""
      // Annotations are not part of the value.
      else -> anObject.value.getText(formatter)
    }

    override fun TextRenderer.renderCell(value: Annotated<ParsedValue<PropertyT>>?) {
      (value ?: ParsedValue.NotSet.annotated()).renderTo(this, formatter, knownValueRenderers)
    }

    override fun createEditorExtensions(): List<Extension> =
      extensions
        .filter { !it.isMainAction }
        .map { action ->
      object : Extension {
        override fun getIcon(hovered: Boolean): Icon = action.icon
        override fun getTooltip(): String = action.tooltip
        override fun getActionOnClick(): Runnable = Runnable {
          action.invoke(property, this@SimplePropertyEditor, this@SimplePropertyEditor)
        }
      }
    }

    fun loadKnownValues() {
      val availableVariables: List<Annotated<ParsedValue.Set.Parsed<PropertyT>>>? = getAvailableVariables()

      knownValuesFuture?.cancel(false)

      knownValuesFuture =
        propertyContext.getKnownValues().continueOnEdt { knownValues ->
          val possibleValues = buildKnownValueRenderers(knownValues!!, formatter, property.defaultValueGetter?.invoke())
          knownValueRenderers = possibleValues
          knownValues to possibleValues
        }.invokeLater { (knownValues, possibleValues) ->
          setKnownValues(
            (possibleValues.keys.toList().map { it.annotated() } +
             availableVariables?.filter { knownValues.isSuitableVariable(it) }.orEmpty()
            )
          )
          knownValuesFuture = null
        }
    }

    private fun setStatus(status: ValueRenderer) {
      statusComponent.clear()
      status.renderTo(statusComponentRenderer)
    }

    private fun getStatusRenderer(valueAnnotation: ValueAnnotation?): ValueRenderer =
      (valueAnnotation as? ValueAnnotation.Error).let {
        if (it != null) {
          object: ValueRenderer {
            override fun renderTo(textRenderer: TextRenderer): Boolean {
              textRenderer.append(it.message, SimpleTextAttributes.ERROR_ATTRIBUTES)
              return true
            }
          }
        } else {
          object: ValueRenderer {
            override fun renderTo(textRenderer: TextRenderer): Boolean = false
          }
        }
      }

    internal fun reloadValue(annotatedPropertyValue: Annotated<PropertyValue<PropertyT>>) {
      setValue(annotatedPropertyValue.value.parsedValue.let { annotatedParsedValue ->
        if (annotatedParsedValue.annotation != null && knownValueRenderers.containsKey(annotatedParsedValue.value))
          annotatedParsedValue.value.annotated()
        else annotatedParsedValue
      })
      setStatus(getStatusRenderer(annotatedPropertyValue.annotation))
      updateModified()
    }

    internal fun applyChanges(annotatedValue: Annotated<ParsedValue<PropertyT>>) {
      property.setParsedValue(annotatedValue.value)
    }

    private fun onEditorChanged() =
      when (updateProperty()) {
        UpdatePropertyOutcome.UPDATED -> reloadValue(property.getValue())
        UpdatePropertyOutcome.NOT_CHANGED -> Unit
        UpdatePropertyOutcome.INVALID -> Unit
      }

    private fun getAvailableVariables(): List<Annotated<ParsedValue.Set.Parsed<PropertyT>>>? =
      variablesScope?.getAvailableVariablesFor(propertyContext)

    fun addFocusGainedListener(listener: () -> Unit) {
      val focusListener = object : FocusListener {
        override fun focusLost(e: FocusEvent?) = Unit
        override fun focusGained(e: FocusEvent?) = listener()
      }
      editor.editorComponent.addFocusListener(focusListener)
      addFocusListener(focusListener)
    }

    /**
     * Returns [true] if the value currently being edited in the combo-box editor differs the last manually set value.
     *
     * (Returns [false] if the editor has not yet been initialized).
     */
    fun isEditorChanged() = lastValueSet != null && getValue().value != lastValueSet?.value

    init {
      if (cellEditor != null) {
        registerTableCellEditor(cellEditor)
      }
      setEditable(true)

      addActionListener {
        if (!disposed && !beingLoaded) {
          onEditorChanged()
        }
      }

      addFocusGainedListener {
        if (!disposed) {
          reloadIfNotChanged()
        }
      }
    }
  }

  @VisibleForTesting
  val testRenderedComboBox: RenderedComboBox<Annotated<ParsedValue<PropertyT>>> = renderedComboBox

  override val component: JComponent = EditorWrapper(property)

  inner class EditorWrapper(private val property: ModelPropertyT) : JPanel(BorderLayout()) {
    init {
      add(renderedComboBox)
      extensions.firstOrNull { it.isMainAction }?.let {
        add(createMiniButton(it), BorderLayout.EAST)
      }
    }

    private fun createMiniButton(extensionAction: EditorExtensionAction<PropertyT, ModelPropertyT>): JComponent =
      JLabel().apply {
        icon = StudioIcons.Common.PROPERTY_UNBOUND
        toolTipText = extensionAction.tooltip
        addMouseListener(object : MouseAdapter() {
          override fun mousePressed(event: MouseEvent) {
            extensionAction.invoke(property, this@SimplePropertyEditor, this@SimplePropertyEditor)
          }
        })
      }

    override fun requestFocus() {
      renderedComboBox.requestFocus()
    }
  }

  override val statusComponent: SimpleColoredComponent = SimpleColoredComponent()
  private val statusComponentRenderer = statusComponent.toRenderer()

  override fun getValue(): Annotated<ParsedValue<PropertyT>> =
    @Suppress("UNCHECKED_CAST")
    (renderedComboBox.editor.item as Annotated<ParsedValue<PropertyT>>)

  override fun updateProperty(): UpdatePropertyOutcome {
    if (disposed) throw IllegalStateException()
    // It is important to avoid applying the unchanged values since the application
    // process while not intended may change the "psi"-representation of the value.
    // it is especially important in the case of invalid/unparsed values.
    if (renderedComboBox.isEditorChanged()) {
      val annotatedValue = getValue()
      if (annotatedValue.annotation is ValueAnnotation.Error) return UpdatePropertyOutcome.INVALID
      renderedComboBox.applyChanges(annotatedValue)
      logValueEdited()
      return UpdatePropertyOutcome.UPDATED
    }
    return UpdatePropertyOutcome.NOT_CHANGED
  }

  override fun dispose() {
    knownValuesFuture?.cancel(false)
    disposed = true
  }

  override fun reload() {
    renderedComboBox.loadKnownValues()
    renderedComboBox.reloadValue(property.getValue())
  }

  override fun reloadIfNotChanged() {
    renderedComboBox.loadKnownValues()
    if (!renderedComboBox.isEditorChanged()) {  // Do not override a not applied invalid value.
      renderedComboBox.reloadValue(property.getValue())
    }
  }

  override fun createNew(
    property: ModelPropertyT,
    cellEditor: TableCellEditor?,
    isPropertyContext: Boolean
  ): ModelPropertyEditor<PropertyT> =
    SimplePropertyEditor(
      property,
      propertyContext,
      variablesScope,
      extensions.filter { isPropertyContext || it.availableInNonPropertyContext },
      cellEditor) { /* no usage tracking */ }

  init {
    reload()
  }
}
