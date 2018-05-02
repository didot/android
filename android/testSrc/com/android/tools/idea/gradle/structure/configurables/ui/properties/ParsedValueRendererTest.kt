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
package com.android.tools.idea.gradle.structure.configurables.ui.properties

import com.android.tools.idea.gradle.structure.configurables.ui.TextRenderer
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.ValueDescriptor
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test

class ParsedValueRendererTest {

  @Test
  fun testBuildKnownValueRenderers_empty() {
    val renderers = buildKnownValueRenderers(listOf(), Any::testToString, null)
    assertThat(renderers.size, equalTo(0))
  }

  @Test
  fun testBuildKnownValueRenderers_noDefault() {
    val valueDescriptors = listOf(ValueDescriptor(ParsedValue.NotSet, "Null"), ValueDescriptor(1, "One"), ValueDescriptor(2, "Two"))
    val renderers = buildKnownValueRenderers(valueDescriptors, Any::testToString, null)
    assertThat(renderers.size, equalTo(3))

    assertThat(testRender(renderers[ParsedValue.NotSet]), equalTo("<comment>(Null)"))
    assertThat(testRender(renderers[1.asParsed()]), equalTo("1@ <comment>(One)"))
    assertThat(testRender(renderers[2.asParsed()]), equalTo("2@ <comment>(Two)"))
  }

  @Test
  fun testBuildKnownValueRenderers_describedDefault() {
    val valueDescriptors = listOf(ValueDescriptor(1, "One"), ValueDescriptor(2, "Two"))
    val renderers = buildKnownValueRenderers(valueDescriptors, Any::testToString, 1)
    assertThat(renderers.size, equalTo(3))

    assertThat(testRender(renderers[ParsedValue.NotSet]), equalTo("<i><comment>1@ (One)"))
    assertThat(testRender(renderers[1.asParsed()]), equalTo("1@ <comment>(One)"))
    assertThat(testRender(renderers[2.asParsed()]), equalTo("2@ <comment>(Two)"))
  }

  @Test
  fun testBuildKnownValueRenderers_notSetAndDescribedDefault() {
    val valueDescriptors = listOf(ValueDescriptor(ParsedValue.NotSet, "Null"), ValueDescriptor(1, "One"), ValueDescriptor(2, "Two"))
    val renderers = buildKnownValueRenderers(valueDescriptors, Any::testToString, 1)
    assertThat(renderers.size, equalTo(3))

    assertThat(testRender(renderers[ParsedValue.NotSet]), equalTo("<comment>(Null)"))
    assertThat(testRender(renderers[1.asParsed()]), equalTo("1@ <comment>(One)"))
    assertThat(testRender(renderers[2.asParsed()]), equalTo("2@ <comment>(Two)"))
  }

  @Test
  fun testBuildKnownValueRenderers_undescribedDefault() {
    val valueDescriptors = listOf(ValueDescriptor(1, "One"), ValueDescriptor(2, "Two"))
    val renderers = buildKnownValueRenderers(valueDescriptors, Any::testToString, -1)
    assertThat(renderers.size, equalTo(3))

    assertThat(testRender(renderers[ParsedValue.NotSet]), equalTo("<i><comment>-1@"))
    assertThat(testRender(renderers[1.asParsed()]), equalTo("1@ <comment>(One)"))
    assertThat(testRender(renderers[2.asParsed()]), equalTo("2@ <comment>(Two)"))
  }

  @Test
  fun testBuildKnownValueRenderers_notDescribedKnownValues() {
    val valueDescriptors = listOf(ValueDescriptor(true), ValueDescriptor(false))
    val renderers = buildKnownValueRenderers(valueDescriptors, Any::testToString, false)
    assertThat(renderers.size, equalTo(3))

    assertThat(testRender(renderers[ParsedValue.NotSet]), equalTo("<i><comment>false@"))
    assertThat(testRender(renderers[true.asParsed()]), equalTo("true@"))
    assertThat(testRender(renderers[false.asParsed()]), equalTo("false@"))
  }

  @Test
  fun testBuildKnownValueRenderers_withVariables() {
    val notDescribedTrue = ParsedValue.Set.Parsed(true, DslText.Reference("notDescribedTrue"))
    val describedFalse = ParsedValue.Set.Parsed(false, DslText.Reference("describedFalse"))
    val valueDescriptors = listOf(
      ValueDescriptor(true),
      ValueDescriptor(false),
      ValueDescriptor(notDescribedTrue),
      ValueDescriptor(describedFalse, "Lie")
    )
    val renderers = buildKnownValueRenderers(valueDescriptors, Any::testToString, false)
    assertThat(renderers.size, equalTo(5))

    assertThat(testRender(renderers[ParsedValue.NotSet]), equalTo("<i><comment>false@"))
    assertThat(testRender(renderers[true.asParsed()]), equalTo("true@"))
    assertThat(testRender(renderers[false.asParsed()]), equalTo("false@"))
    assertThat(testRender(renderers[notDescribedTrue]), equalTo("<b><var>\$notDescribedTrue</b><comment> : true@"))
    assertThat(testRender(renderers[describedFalse]), equalTo("<b><var>\$describedFalse</b><comment> : false@<nocolor> <comment>(Lie)"))
  }

  @Test
  fun testRenderParsedValue_notSet() {
    assertThat(ParsedValue.NotSet.testRenderWith<Int>(buildKnownValueRenderers(listOf(), Any::testToString, null)), equalTo(""))
    assertThat(
      ParsedValue.NotSet.testRenderWith<Int>(
        buildKnownValueRenderers(listOf(ValueDescriptor(ParsedValue.NotSet, "Null")), Any::testToString, null)),
      equalTo("<comment>(Null)"))
    assertThat(ParsedValue.NotSet.testRenderWith<Int>(buildKnownValueRenderers(listOf(ValueDescriptor(1, "One")), Any::testToString, 1)),
               equalTo("<i><comment>1@ (One)"))
  }

  @Test
  fun testRenderParsedValue_setValue() {
    val one = ParsedValue.Set.Parsed(1, DslText.Literal)
    assertThat(one.testRenderWith<Int>(buildKnownValueRenderers(listOf(), Any::testToString, null)), equalTo("1@"))
    assertThat(one.testRenderWith<Int>(buildKnownValueRenderers(listOf(ValueDescriptor(1, "One")), Any::testToString, null)),
               equalTo("1@ <comment>(One)"))
  }

  @Test
  fun testRenderParsedValue_setEmptyValue() {
    val one = ParsedValue.Set.Parsed("", DslText.Literal)
    assertThat(
      testRender { one.renderTo(this, Any::toString, buildKnownValueRenderers<String>(listOf(), Any::toString, null)) },
      equalTo(""))
    assertThat(
      testRender { one.renderTo(this, Any::toString, buildKnownValueRenderers(listOf(ValueDescriptor("", "One")), Any::toString, null)) },
      equalTo("<comment>(One)"))
  }

  @Test
  fun testRenderParsedValue_setValueWitLiteral() {
    val one = ParsedValue.Set.Parsed(1, DslText.Literal)
    assertThat(one.testRenderWith<Int>(buildKnownValueRenderers(listOf(), Any::testToString, null)), equalTo("1@"))
    assertThat(one.testRenderWith<Int>(buildKnownValueRenderers(listOf(ValueDescriptor(1, "One")), Any::testToString, null)),
               equalTo("1@ <comment>(One)"))
  }

  @Test
  fun testRenderParsedValue_setReference() {
    val var1 = ParsedValue.Set.Parsed(1, DslText.Reference("var1"))
    assertThat(var1.testRenderWith<Int>(buildKnownValueRenderers(listOf(), Any::testToString, null)),
               equalTo("<b><var>\$var1</b><comment> : 1@"))
    assertThat(var1.testRenderWith<Int>(buildKnownValueRenderers(listOf(ValueDescriptor(1, "One")), Any::testToString, null)),
               equalTo("<b><var>\$var1</b><comment> : 1@ (One)"))
  }

  @Test
  fun testRenderParsedValue_setReferenceToEmpty() {
    val var1 = ParsedValue.Set.Parsed("", DslText.Reference("var1"))
    assertThat(
      testRender { var1.renderTo(this, Any::toString, buildKnownValueRenderers<String>(listOf(), Any::toString, null)) },
      equalTo("<b><var>\$var1"))
  }

  @Test
  fun testRenderParsedValue_setInterpolatedString() {
    val interpolated = ParsedValue.Set.Parsed("a nd b", DslText.InterpolatedString("\$var1 and \$var2"))
    // TODO(b/77618752): Variable references should be highlighted separately.
    assertThat(interpolated.testRenderWith<String>(buildKnownValueRenderers(listOf(), Any::testToString, null)),
               equalTo("<b><var>\"\$var1 and \$var2\"</b><comment> : \"a nd b@\""))
    assertThat(interpolated.testRenderWith<String>(
      buildKnownValueRenderers(listOf(ValueDescriptor("a and b", "does not matter")), Any::testToString, null)),
               equalTo("<b><var>\"\$var1 and \$var2\"</b><comment> : \"a nd b@\""))
  }

  @Test
  fun testRenderParsedValue_setOtherDsl() {
    val dsl = ParsedValue.Set.Parsed(null, DslText.OtherUnparsedDslText("doSomething()"))
    assertThat(dsl.testRenderWith<String>(buildKnownValueRenderers(listOf(), Any::testToString, null)),
               equalTo("<b><var>\$\$</b><w>doSomething()"))
  }

  @Test
  fun testRenderParsedValue_setInvalid() {
    val error = ParsedValue.Set.Invalid<String>("something bad", "error message")
    assertThat(error.testRenderWith<String>(buildKnownValueRenderers(listOf(), Any::testToString, null)),
               equalTo("something bad <error>(error message)"))
  }

  @Test
  fun testRenderParsedValue_setInvalidNoMessage() {
    val error = ParsedValue.Set.Invalid<String>("something bad", "")
    assertThat(error.testRenderWith<String>(buildKnownValueRenderers(listOf(), Any::testToString, null)),
               equalTo("something bad <error>(invalid value)"))
  }
}


private fun testRender(action: TextRenderer.() -> Unit): String =
  object : TextRenderer {
    var result = ""

    var bold: Boolean = false
    var italic: Boolean = false
    var waved: Boolean = false
    var color: String = ""

    override fun append(text: String, attributes: SimpleTextAttributes) {
      if (attributes.style and SimpleTextAttributes.STYLE_BOLD == 0 && bold) {
        result += "</b>"; bold = false
      }
      if (attributes.style and SimpleTextAttributes.STYLE_ITALIC == 0 && italic) {
        result += "</i>"; italic = false
      }
      if (attributes.style and SimpleTextAttributes.STYLE_WAVED == 0 && waved) {
        result += "</w>"; waved = false
      }
      if (attributes.style and SimpleTextAttributes.STYLE_BOLD != 0 && !bold) {
        result += "<b>"; bold = true
      }
      if (attributes.style and SimpleTextAttributes.STYLE_ITALIC != 0 && !italic) {
        result += "<i>"; italic = true
      }
      if (attributes.style and SimpleTextAttributes.STYLE_WAVED != 0 && !waved) {
        result += "<w>"; waved = true
      }
      if (attributes.fgColor == SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor && color != "comment") {
        result += "<comment>"; color = "comment"
      }
      if (attributes.fgColor == SimpleTextAttributes.LINK_ATTRIBUTES.fgColor && color != "var") {
        result += "<var>"; color = "var"
      }
      if (attributes.fgColor == SimpleTextAttributes.ERROR_ATTRIBUTES.fgColor && color != "error") {
        result += "<error>"; color = "error"
      }
      if (attributes.fgColor == JBColor.BLACK && color != "") {
        result += "<nocolor>"; color = ""
      }
      result += text
    }
  }
    .apply(action)
    .result

private fun <T : Any> T.asParsed(): ParsedValue<T> = ParsedValue.Set.Parsed(this, DslText.Literal)
private fun testRender(valueRenderer: ValueRenderer?): String = testRender { valueRenderer?.renderTo(this) }

private fun <T : Any> ParsedValue<T>.testRenderWith(valueToText: Map<ParsedValue<T>, ValueRenderer>): String = let { value ->
  testRender { value.renderTo(this, Any::testToString, valueToText) }
}

// Use custom toString() in tests to ensure it is called when it is appropriate.
private fun Any.testToString() = toString() + "@"
