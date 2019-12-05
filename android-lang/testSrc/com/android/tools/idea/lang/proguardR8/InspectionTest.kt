/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lang.proguardR8

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.CodeShrinker
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.highlightedAs
import com.intellij.lang.annotation.HighlightSeverity.ERROR
import com.intellij.lang.annotation.HighlightSeverity.WARNING
import com.intellij.lang.annotation.HighlightSeverity.WEAK_WARNING
import org.jetbrains.android.AndroidTestCase

class InspectionTest : ProguardR8TestCase() {
  fun testUnresolvedClassName() {
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class ${"test.MyNotExistingClass".highlightedAs(WEAK_WARNING, "Unresolved class name")} {
        long myBoolean;
      }
      """.trimIndent())

    myFixture.checkHighlighting()

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class test.MyNotExistingClass.WithWildCard** {
        long myBoolean;
      }
      """.trimIndent())

    myFixture.checkHighlighting()

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class java.lang.String {
        ${"test.MyNotExistingClass".highlightedAs(WEAK_WARNING, "Unresolved class name")} 
        ${"myVal".highlightedAs(WEAK_WARNING, "The rule matches no class members")};
      }
      """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testInvalidFlag() {
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        ${"-invalidflag".highlightedAs(ERROR, "Invalid flag")}
      """.trimIndent()
    )

    myFixture.checkHighlighting()
  }

  fun testSpacesInArrayType() {
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
      -keep class java.lang.String {
        ${"int []".highlightedAs(ERROR, "White space between type and array annotation is not allowed, use 'type[]'")} method;
        int${"[\n]".highlightedAs(ERROR, "White space is not allowed in array annotation, use 'type[]'")} method;
        int ${"[  ]".highlightedAs(ERROR, "White space is not allowed in array annotation, use 'type[]'")} method;
        int[] method;
      }
      """.trimIndent())

    // checks only errors
    myFixture.checkHighlighting(false, false, false)
  }

}

class ProguardR8IgnoredFlagInspectionTest : AndroidTestCase() {
  override fun setUp() {
    StudioFlags.R8_SUPPORT_ENABLED.override(true)
    super.setUp()
    myFixture.enableInspections(ProguardR8IgnoredFlagInspection::class.java)
  }

  override fun tearDown() {
    StudioFlags.R8_SUPPORT_ENABLED.clearOverride()
    super.tearDown()
  }

  fun testIgnoredFlag() {
    (myModule.getModuleSystem() as DefaultModuleSystem).codeShrinker = CodeShrinker.R8

    val flag = PROGUARD_FLAGS.minus(R8_FLAGS).first()
    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        ${"-${flag}".highlightedAs(WARNING, "Flag ignored by R8")}
      """.trimIndent()
    )

    myFixture.checkHighlighting()

    (myModule.getModuleSystem() as DefaultModuleSystem).codeShrinker = CodeShrinker.PROGUARD

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -${flag}
      """.trimIndent()
    )

    myFixture.checkHighlighting()

    (myModule.getModuleSystem() as DefaultModuleSystem).codeShrinker = null

    myFixture.configureByText(
      ProguardR8FileType.INSTANCE,
      """
        -${flag}
      """.trimIndent()
    )

    myFixture.checkHighlighting()
  }
}