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
package com.android.tools.idea.gradle.structure.model.helpers

import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.ValueAnnotation
import com.android.tools.idea.gradle.structure.model.meta.annotated
import com.intellij.pom.java.LanguageLevel
import junit.framework.Assert.*
import org.junit.Test
import java.io.File

class InputParsersTest {

  @Test
  fun string() {
    val parsed = parseString(null, "abc")
    assertTrue(parsed.value is ParsedValue.Set.Parsed)
    assertNull(parsed.annotation)
    assertEquals("abc", (parsed.value as ParsedValue.Set.Parsed).value)
  }

  @Test
  fun string_empty() {
    val parsed = parseString(null, "")
    assertTrue(parsed.value === ParsedValue.NotSet)
    assertNull(parsed.annotation)
  }

  @Test
  fun file() {
    val parsed = parseFile(null, "/tmp/abc")
    assertTrue(parsed.value is ParsedValue.Set.Parsed)
    assertNull(parsed.annotation)
    assertEquals(File("/tmp/abc"), (parsed.value as ParsedValue.Set.Parsed).value)
  }

  @Test
  fun file_empty() {
    val parsed = parseFile(null, "")
    assertTrue(parsed.value === ParsedValue.NotSet)
    assertNull(parsed.annotation)
  }

  @Test
  fun enum() {
    val parsed = parseEnum("1.7", LanguageLevel::parse)
    assertTrue(parsed.value is ParsedValue.Set.Parsed)
    assertNull(parsed.annotation)
    assertEquals(LanguageLevel.JDK_1_7, (parsed.value as ParsedValue.Set.Parsed).value)
  }

  @Test
  fun enum_empty() {
    val parsed = parseEnum("", LanguageLevel::parse)
    assertTrue(parsed.value === ParsedValue.NotSet)
    assertNull(parsed.annotation)
  }

  @Test
  fun enum_invalid() {
    val parsed = parseEnum("1_7", LanguageLevel::parse)
    assertTrue(parsed.value is ParsedValue.Set.Parsed)
    assertEquals(ValueAnnotation.Error("'1_7' is not a valid value of type LanguageLevel"), parsed.annotation)
    assertEquals(DslText.OtherUnparsedDslText("1_7"), (parsed.value as ParsedValue.Set.Parsed).dslText)
  }

  @Test
  fun boolean_empty() {
    val parsed = parseBoolean(null, "")
    assertTrue(parsed.value === ParsedValue.NotSet)
    assertNull(parsed.annotation)
  }

  @Test
  fun boolean_true() {
    val parsed = parseBoolean(null, "true")
    assertTrue(parsed.value is ParsedValue.Set.Parsed)
    assertNull(parsed.annotation)
    assertEquals(true, (parsed.value as ParsedValue.Set.Parsed).value)
  }

  @Test
  fun boolean_false() {
    val parsed = parseBoolean(null, "FALSE")
    assertTrue(parsed.value is ParsedValue.Set.Parsed)
    assertNull(parsed.annotation)
    assertEquals(false, (parsed.value as ParsedValue.Set.Parsed).value)
  }

  @Test
  fun boolean_invalid() {
    val parsed = parseBoolean(null, "yes")
    assertTrue(parsed.value is ParsedValue.Set.Parsed)
    assertEquals(ValueAnnotation.Error("Unknown boolean value: 'yes'. Expected 'true' or 'false'"), parsed.annotation)
    assertEquals(DslText.OtherUnparsedDslText("yes"), (parsed.value as ParsedValue.Set.Parsed).dslText)
  }

  @Test
  fun int_empty() {
    val parsed = parseInt(null, "")
    assertTrue(parsed.value === ParsedValue.NotSet)
    assertNull(parsed.annotation)
  }

  @Test
  fun int() {
    val parsed = parseInt(null, "123")
    assertTrue(parsed.value is ParsedValue.Set.Parsed)
    assertNull(parsed.annotation)
    assertEquals(123, (parsed.value as ParsedValue.Set.Parsed).value)
  }

  @Test
  fun int_invalid() {
    val parsed = parseInt(null, "123.4")
    assertTrue(parsed.value is ParsedValue.Set.Parsed)
    assertEquals(ValueAnnotation.Error("'123.4' is not a valid integer value"), parsed.annotation)
    assertEquals(DslText.OtherUnparsedDslText("123.4"), (parsed.value as ParsedValue.Set.Parsed).dslText)
  }

  @Test
  fun languageLevel_empty() {
    val parsed = parseLanguageLevel(null, "")
    assertTrue(parsed.value === ParsedValue.NotSet)
    assertNull(parsed.annotation)
  }

  @Test
  fun languageLevel() {
    assertEquals(parseLanguageLevel(null, "1.8"), ParsedValue.Set.Parsed(LanguageLevel.JDK_1_8, DslText.Literal).annotated())
    assertEquals(parseLanguageLevel(null, "VERSION_1_7"), ParsedValue.Set.Parsed(LanguageLevel.JDK_1_7, DslText.Literal).annotated())
    assertEquals(parseLanguageLevel(null, "JavaVersion.VERSION_1_6"),
                 ParsedValue.Set.Parsed(LanguageLevel.JDK_1_6, DslText.Literal).annotated())
  }

  @Test
  fun referenceOnly_empty() {
    val parsed = parseReferenceOnly(null, "")
    assertTrue(parsed.value === ParsedValue.NotSet)
    assertNull(parsed.annotation)
  }
}