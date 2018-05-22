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
package com.android.tools.idea.gradle.structure.model.meta

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.INTEGER_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.android.tools.idea.gradle.structure.model.android.asParsed
import com.android.tools.idea.gradle.structure.model.helpers.parseBoolean
import com.android.tools.idea.gradle.structure.model.helpers.parseInt
import com.android.tools.idea.gradle.structure.model.helpers.parseString
import com.android.tools.lint.client.api.TYPE_OBJECT
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class ModelSimplePropertyImplTest : GradleFileModelTestCase() {

  object Model : ModelDescriptor<Model, Model, Model> {
    override fun getResolved(model: Model): Model? = this
    override fun getParsed(model: Model): Model? = this
    override fun setModified(model: Model) = Unit
  }

  private fun <T : Any> GradlePropertyModel.wrap(
    parse: (Nothing?, String) -> Annotated<ParsedValue<T>>,
    caster: ResolvedPropertyModel.() -> T?,
    resolvedValue: T? = null
  ): ModelSimpleProperty<Nothing?, Model, T> {
    val resolved = resolve()
    return Model.property(
      "description",
      resolvedValueGetter = { resolvedValue },
      parsedPropertyGetter = { resolved },
      getter = { caster() },
      setter = { setValue(it) },
      parser = { context, value -> parse(context, value) }
    )
  }

  private fun <T : Any> ModelSimpleProperty<Nothing?, Model, T>.testValue() = bind(Model).testValue()
  private fun <T : Any> ModelSimpleProperty<Nothing?, Model, T>.testIsModified() = bind(Model).isModified
  private fun <T : Any> ModelSimpleProperty<Nothing?, Model, T>.testSetValue(value: T?) = bind(Model).testSetValue(value)
  private fun <T : Any> ModelSimpleProperty<Nothing?, Model, T>.testSetReference(value: String) = bind(Model).testSetReference(value)
  private fun <T : Any> ModelSimpleProperty<Nothing?, Model, T>.testSetInterpolatedString(value: String) =
    bind(Model).testSetInterpolatedString(value)

  @Test
  fun testPropertyValues() {
    val text = """
               ext {
                 propValue = 'value'
                 prop25 = 25
                 propTrue = true
                 propRef = propValue
                 propInterpolated = "${'$'}{prop25}th"
                 propUnresolved = unresolvedReference
                 propOtherExpression1 = z(1)
                 propOtherExpression2 = 1 + 2
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()

    val propValue = extModel.findProperty("propValue").wrap(::parseString, ResolvedPropertyModel::asString, resolvedValue = "value")
    val prop25 = extModel.findProperty("prop25").wrap(::parseInt, ResolvedPropertyModel::asInt, resolvedValue = 26)
    val propTrue = extModel.findProperty("propTrue").wrap(::parseBoolean, ResolvedPropertyModel::asBoolean)
    val propRef = extModel.findProperty("propRef").wrap(::parseString, ResolvedPropertyModel::asString, resolvedValue = "value")
    val propInterpolated = extModel.findProperty("propInterpolated").wrap(::parseString, ResolvedPropertyModel::asString)
    val propUnresolved = extModel.findProperty("propUnresolved").wrap(::parseString, ResolvedPropertyModel::asString)
    val propOtherExpression1 = extModel.findProperty("propOtherExpression1").wrap(::parseString, ResolvedPropertyModel::asString)
    val propOtherExpression2 = extModel.findProperty("propOtherExpression2").wrap(::parseString, ResolvedPropertyModel::asString)

    assertThat(propValue.testValue(), equalTo("value"))
    assertThat(propValue.testIsModified(), equalTo(false))
    assertThat(prop25.testValue(), equalTo(25))
    assertThat(prop25.testIsModified(), equalTo(false))
    assertThat(propTrue.testValue(), equalTo(true))
    assertThat(propTrue.testIsModified(), equalTo(false))
    assertThat(propRef.testValue(), equalTo("value"))
    assertThat(propRef.testIsModified(), equalTo(false))
    assertThat(propInterpolated.testValue(), equalTo("25th"))
    assertThat(propInterpolated.testIsModified(), equalTo(false))
    assertThat(propUnresolved.testValue(), nullValue())
    assertThat(propUnresolved.testIsModified(), equalTo(false))
    assertThat(propOtherExpression1.testValue(), nullValue())
    assertThat(propOtherExpression1.testIsModified(), equalTo(false))
    assertThat(propOtherExpression2.testValue(), nullValue())
    assertThat(propOtherExpression2.testIsModified(), equalTo(false))
  }

  @Test
  fun testResolvedValueMatching() {
    val text = """
               ext {
                 propValue = 'value'
                 prop25 = 25
                 propTrue = true
                 propRef = propValue
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()

    val propValue = extModel.findProperty("propValue").wrap(::parseString, ResolvedPropertyModel::asString, resolvedValue = "value")
    val prop25 = extModel.findProperty("prop25").wrap(::parseInt, ResolvedPropertyModel::asInt, resolvedValue = 26)
    val propTrue = extModel.findProperty("propTrue").wrap(::parseBoolean, ResolvedPropertyModel::asBoolean)
    val propRef = extModel.findProperty("propRef").wrap(::parseString, ResolvedPropertyModel::asString, resolvedValue = "value")

    assertThat(propValue.bind(Model).getValue().annotation, nullValue())
    assertThat(prop25.bind(Model).getValue().annotation, equalTo<ValueAnnotation?>(ValueAnnotation.Error("Resolved: 26")))
    assertThat(propTrue.bind(Model).getValue().annotation,
               equalTo<ValueAnnotation?>(ValueAnnotation.Warning("Resolved value is unavailable.")))
    assertThat(propRef.bind(Model).getValue().annotation, nullValue())
  }

  @Test
  fun testWritePropertyValues() {
    val text = """
               ext {
                 propValue = 'value'
                 prop25 = 25
                 propTrue = true
                 propInterpolated = "${'$'}{prop25}th"
                 propUnresolved = unresolvedReference
                 propRef = propValue
               }""".trimIndent()
    writeToBuildFile(text)

    val extModel = gradleBuildModel.ext()

    val propValue = extModel.findProperty("propValue").wrap(::parseString, ResolvedPropertyModel::asString)
    val prop25 = extModel.findProperty("prop25").wrap(::parseInt, ResolvedPropertyModel::asInt)
    val propTrue = extModel.findProperty("propTrue").wrap(::parseBoolean, ResolvedPropertyModel::asBoolean)
    val propInterpolated = extModel.findProperty("propInterpolated").wrap(::parseString, ResolvedPropertyModel::asString)
    val propUnresolved = extModel.findProperty("propUnresolved").wrap(::parseString, ResolvedPropertyModel::asString)
    val propRef = extModel.findProperty("propRef").wrap(::parseString, ResolvedPropertyModel::asString)

    propValue.testSetValue("changed")
    assertThat(propValue.testValue(), equalTo("changed"))
    assertThat(propValue.testIsModified(), equalTo(true))
    assertThat(propRef.testIsModified(), equalTo(false))  // Changing a dependee does not make the dependent modified.

    prop25.testSetValue(26)
    assertThat(prop25.testValue(), equalTo(26))
    assertThat(prop25.testIsModified(), equalTo(true))

    propTrue.testSetValue(null)
    assertThat(propTrue.testValue(), nullValue())
    assertThat(propTrue.testIsModified(), equalTo(true))

    propInterpolated.testSetInterpolatedString("${'$'}{prop25} items")
    assertThat(propInterpolated.testValue(), equalTo("26 items"))
    assertThat(propInterpolated.testIsModified(), equalTo(true))

    propUnresolved.testSetValue("reset")
    assertThat(propUnresolved.testValue(), equalTo("reset"))
    assertThat(propUnresolved.testIsModified(), equalTo(true))

    propRef.testSetReference("propInterpolated")
    assertThat(propRef.testValue(), equalTo("26 items"))
    assertThat(propRef.testIsModified(), equalTo(true))

    prop25.testSetReference("25")
    assertThat(prop25.testValue(), equalTo(25))

    propTrue.testSetReference("2 + 2")
    assertThat<Annotated<ParsedValue<Boolean>>>(
      propTrue.bind(Model).getParsedValue(),
      equalTo<Annotated<ParsedValue<Boolean>>>(ParsedValue.Set.Parsed<Boolean>(null, DslText.OtherUnparsedDslText("2 + 2")).annotated()))
  }

  @Test
  fun testRebindResolvedProperty() {
    val text = """
               ext {
                 prop25 = 25
               }""".trimIndent()
    writeToBuildFile(text)

    val buildModelInstance = gradleBuildModel
    val extModel = buildModelInstance.ext()

    val prop25 = extModel.findProperty("prop25").wrap(::parseInt, ResolvedPropertyModel::asInt, resolvedValue = 26).bind(Model)
    val newResolvedProperty = extModel.findProperty("newVar").resolve()
    var localModified = false
    @Suppress("UNCHECKED_CAST")
    val reboundProp = (prop25 as GradleModelCoreProperty<Int, ModelPropertyCore<Int>>).rebind(newResolvedProperty, { localModified = true })
    assertThat(reboundProp.getParsedValue(), equalTo<Annotated<ParsedValue<Int>>>(ParsedValue.NotSet.annotated()))
    reboundProp.setParsedValue(1.asParsed())
    assertThat(reboundProp.getParsedValue(), equalTo<Annotated<ParsedValue<Int>>>(1.asParsed().annotated()))
    assertThat(localModified, equalTo(true))
    assertThat(newResolvedProperty.isModified, equalTo(true))
    assertThat(newResolvedProperty.getValue(INTEGER_TYPE), equalTo(1))

    applyChangesAndReparse(buildModelInstance)

    val expected = """
               ext {
                 prop25 = 25
                 newVar = 1
               }""".trimIndent()
    verifyFileContents(myBuildFile, expected)
  }
}
