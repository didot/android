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
package com.android.tools.idea.gradle.structure.model.meta

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.OBJECT_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.google.common.util.concurrent.Futures.immediateFuture
import com.google.common.util.concurrent.ListenableFuture
import kotlin.reflect.KProperty


fun <T : ModelDescriptor<ModelT, ResolvedT, ParsedT>, ModelT, ResolvedT, ParsedT, ValueT : Any, ContextT> T.mapProperty(
  description: String,
  resolvedValueGetter: ResolvedT.() -> Map<String, ValueT>?,
  getter: ResolvedPropertyModel.() -> ValueT?,
  setter: ResolvedPropertyModel.(ValueT) -> Unit,
  parsedPropertyGetter: ParsedT.() -> ResolvedPropertyModel,
  parser: (ContextT, String) -> ParsedValue<ValueT>,
  formatter: (ContextT, ValueT) -> String = { _, value -> value.toString() },
  knownValuesGetter: ((ContextT, ModelT) -> ListenableFuture<List<ValueDescriptor<ValueT>>>)? = null
) =
  ModelMapPropertyImpl(
    this,
    description,
    resolvedValueGetter,
    { parsedPropertyGetter().asParsedMapValue(getter, setter) },
    { key -> parsedPropertyGetter().addEntry(key, getter, setter) },
    { key -> parsedPropertyGetter().deleteEntry(key) },
    { old, new -> parsedPropertyGetter().changeEntryKey(old, new, getter, setter) },
    { parsedPropertyGetter().dslText() },
    { parsedPropertyGetter().delete() },
    { parsedPropertyGetter().setDslText(it) },
    { context: ContextT, value -> if (value.isBlank()) ParsedValue.NotSet else parser(context, value.trim()) },
    formatter,
    { context: ContextT, model -> if (knownValuesGetter != null) knownValuesGetter(context, model) else immediateFuture(listOf()) }
  )

class ModelMapPropertyImpl<in ContextT, in ModelT, ResolvedT, ParsedT, ValueT : Any>(
  override val modelDescriptor: ModelDescriptor<ModelT, ResolvedT, ParsedT>,
  override val description: String,
  val getResolvedValue: ResolvedT.() -> Map<String, ValueT>?,
  private val getParsedCollection: ParsedT.() -> Map<String, ModelPropertyParsedCore<ValueT>>?,
  private val addEntry: ParsedT.(String) -> ModelPropertyParsedCore<ValueT>,
  private val entryDeleter: ParsedT.(String) -> Unit,
  private val changeEntryKey: ParsedT.(String, String) -> ModelPropertyParsedCore<ValueT>,
  private val getParsedRawValue: ParsedT.() -> DslText?,
  override val clearParsedValue: ParsedT.() -> Unit,
  override val setParsedRawValue: (ParsedT.(DslText) -> Unit),
  override val parser: (ContextT, String) -> ParsedValue<ValueT>,
  override val formatter: (ContextT, ValueT) -> String,
  override val knownValuesGetter: (ContextT, ModelT) -> ListenableFuture<List<ValueDescriptor<ValueT>>>
) : ModelCollectionPropertyBase<ContextT, ModelT, ResolvedT, ParsedT, Map<String, ValueT>, ValueT>(), ModelMapProperty<ContextT, ModelT, ValueT> {

  override fun getValue(thisRef: ModelT, property: KProperty<*>): ParsedValue<Map<String, ValueT>> = getParsedValue(thisRef)

  override fun setValue(thisRef: ModelT, property: KProperty<*>, value: ParsedValue<Map<String, ValueT>>) = setParsedValue(thisRef, value)

  private fun getEditableValues(model: ModelT): Map<String, ModelPropertyCore<ValueT>> {
    fun getResolvedValue(key: String): ValueT? = modelDescriptor.getResolved(model)?.getResolvedValue()?.get(key)
    return modelDescriptor
      .getParsed(model)
      ?.getParsedCollection()
             ?.mapValues { it.value.makePropertyCore { getResolvedValue(it.key) } }
      ?.mapValues { it.value.makeSetModifiedAware(model) }
        ?: mapOf()
  }

  private fun addEntry(model: ModelT, key: String): ModelPropertyCore<ValueT> =
      // No need to mark the model modified here since adding an empty property does not really affect its state. However, TODO(b/73059531).
    modelDescriptor.getParsed(model)?.addEntry(key)?.makePropertyCore { null }?.makeSetModifiedAware(model)
    ?: throw IllegalStateException()

  private fun deleteEntry(model: ModelT, key: String) =
    modelDescriptor.getParsed(model)?.entryDeleter(key).also { model.setModified() } ?: throw IllegalStateException()

  private fun changeEntryKey(model: ModelT, old: String, new: String): ModelPropertyCore<ValueT> =
      // Both make the property modify-aware and make the model modified since both operations involve changing the model.
    modelDescriptor.getParsed(model)?.changeEntryKey(old, new)?.makePropertyCore { null }?.makeSetModifiedAware(model).also { model.setModified() }
        ?: throw IllegalStateException()

  private fun getParsedValue(model: ModelT): ParsedValue<Map<String, ValueT>> {
    val parsedModel = modelDescriptor.getParsed(model)
    val parsedGradleValue: Map<String, ModelPropertyParsedCore<ValueT>>? = parsedModel?.getParsedCollection()
    val parsed: Map<String, ValueT>? =
      parsedGradleValue
        ?.mapNotNull {
          (it.value.getParsedValue() as? ParsedValue.Set.Parsed<ValueT>)?.value?.let { v -> it.key to v }
        }
        ?.toMap()
    val dslText: DslText? = parsedModel?.getParsedRawValue()
    return makeParsedValue(parsed, dslText)
  }

  private fun getResolvedValue(model: ModelT): ResolvedValue<Map<String, ValueT>> {
    val resolvedModel = modelDescriptor.getResolved(model)
    val resolved: Map<String, ValueT>? = resolvedModel?.getResolvedValue()
    return when (resolvedModel) {
      null -> ResolvedValue.NotResolved()
      else -> ResolvedValue.Set(resolved)
    }
  }

  override fun bind(model: ModelT): ModelMapPropertyCore<ValueT> = object: ModelMapPropertyCore<ValueT> {
    override fun getParsedValue(): ParsedValue<Map<String, ValueT>> = this@ModelMapPropertyImpl.getParsedValue(model)
    override fun setParsedValue(value: ParsedValue<Map<String, ValueT>>) = this@ModelMapPropertyImpl.setParsedValue(model, value)
    override fun getResolvedValue(): ResolvedValue<Map<String, ValueT>> = this@ModelMapPropertyImpl.getResolvedValue(model)
    override fun getEditableValues(): Map<String, ModelPropertyCore<ValueT>> = this@ModelMapPropertyImpl.getEditableValues(model)
    override fun addEntry(key: String): ModelPropertyCore<ValueT> = this@ModelMapPropertyImpl.addEntry(model, key)
    override fun deleteEntry(key: String) = this@ModelMapPropertyImpl.deleteEntry(model, key)
    override fun changeEntryKey(old: String, new: String): ModelPropertyCore<ValueT> = this@ModelMapPropertyImpl.changeEntryKey(model, old, new)
    override val defaultValueGetter: (() -> Map<String, ValueT>?)? = null
  }
}

fun <T : Any> ResolvedPropertyModel?.asParsedMapValue(
  getTypedValue: ResolvedPropertyModel.() -> T?,
  setTypedValue: ResolvedPropertyModel.(T) -> Unit
): Map<String, ModelPropertyParsedCore<T>>? =
  this
    ?.takeIf { valueType == GradlePropertyModel.ValueType.MAP }
    ?.getValue(GradlePropertyModel.MAP_TYPE)
    ?.mapValues { it.value.resolve() }
    ?.mapValues { makeItemProperty(it.value, getTypedValue, setTypedValue) }

fun <T : Any> ResolvedPropertyModel.addEntry(
  key: String,
  getTypedValue: ResolvedPropertyModel.() -> T?,
  setTypedValue: ResolvedPropertyModel.(T) -> Unit
): ModelPropertyParsedCore<T> =
  makeItemProperty(getMapValue(key).resolve(), getTypedValue, setTypedValue)

fun ResolvedPropertyModel.deleteEntry(key: String) = getMapValue(key).delete()

fun <T : Any> ResolvedPropertyModel.changeEntryKey(
  old: String,
  new: String,
  getTypedValue: ResolvedPropertyModel.() -> T?,
  setTypedValue: ResolvedPropertyModel.(T) -> Unit
): ModelPropertyParsedCore<T> {
  val oldProperty = getMapValue(old)
  // TODO(b/73057388): Simplify to plain oldProperty.getRawValue(OBJECT_TYPE).
  val oldValue = when (oldProperty.valueType) {
    GradlePropertyModel.ValueType.REFERENCE -> oldProperty.getRawValue(STRING_TYPE)?.let { ReferenceTo(it) }
    else -> oldProperty.getRawValue(OBJECT_TYPE)
  }

  oldProperty.delete()
  val newProperty = getMapValue(new)
  if (oldValue != null) newProperty.setValue(oldValue)
  return makeItemProperty(newProperty.resolve(), getTypedValue, setTypedValue)
}
