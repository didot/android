/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.resource

import com.android.testutils.TestUtils
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiElement
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class LambdaResolverTest {
  private val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val rules: RuleChain = RuleChain.outerRule(projectRule).around(EdtRule())

  @Before
  fun before() {
    val fixture = projectRule.fixture
    fixture.testDataPath = TestUtils.resolveWorkspacePath("tools/adt/idea/layout-inspector/testData/compose").toString()
    fixture.copyFileToProject("java/com/example/MyLambdas.kt")
  }

  @Test
  fun testFindLambdaLocation() {
    checkLambda("f1$1", 32, 32, "MyLambdas.kt:32", "{ 1 }")
    checkLambda("f1$2", 32, 32, "MyLambdas.kt:32", "{ 2 }")
    checkLambda("f1$3", 32, 32, "MyLambdas.kt:32", "{ f2({ 3 }, { 4 }) }")
    checkLambda("f1$3$1", 32, 32, "MyLambdas.kt:32", "{ 3 }")
    checkLambda("f1$3$2", 32, 32, "MyLambdas.kt:32", "{ 4 }")
    checkLambda("f2$1", 36, 36, "MyLambdas.kt:36", "{ -1 }")
    checkLambda("$1", 24, 24, "MyLambdas.kt:24", "{ 1 }")
    checkLambda("$2", 24, 24, "MyLambdas.kt:24", "{ 2 }")
    checkLambda("$3", 24, 24, "MyLambdas.kt:24", "{ f2({ 3 }, { 4 }) }")
    checkLambda("$3$1", 24, 24, "MyLambdas.kt:24", "{ 3 }")
    checkLambda("$3$2", 24, 24, "MyLambdas.kt:24", "{ 4 }")
    checkLambda("$9$2", 26, 26, "MyLambdas.kt:26", "{ 2 }")
    checkLambda("l1$1", 3, 3, "MyLambdas.kt:3", "{ it }")
    checkLambda("l2$1", 6, 7, "MyLambdas.kt:6", """
       { number ->
         // The line numbers from JVMTI of this lambda, starts AFTER this comment...
         number * number
       }
       """.trimIndent())
    checkLambda("l3$1", 8, 8, null, null) // A function reference should not be found as a lambda expr
  }

  @Test
  fun testFindFunctionReferenceLocation() {
    check("l3$1", "f3", 8, 8, "MyLambdas.kt:8", "::f3")
    check("l4$1", "fx", 21, 21, "MyLambdas.kt:21", "::fx")
    check("$4", "f3", 25, 25, "MyLambdas.kt:25", "::f3")
    check("$5", "f4", 25, 25, "MyLambdas.kt:25", "::f4")
    check("$6$1", "f5", 25, 25, "MyLambdas.kt:25", "::f5")
    check("$6$2", "f6", 25, 25, "MyLambdas.kt:25", "::f6")
    check("$8", "f6", 26, 26, "MyLambdas.kt:26", "::f6")
    check("l1$1", "f1", 3, 3, null, null) // A lambda expression should not be found as a fct ref
  }

  private fun checkLambda(lambdaName: String, startLine: Int, endLine: Int, expectedLocation: String?, expectedText: String?) =
    check(lambdaName, functionName = "", startLine, endLine, expectedLocation, expectedText)

  private fun check(
    lambdaName: String,
    functionName: String,
    startLine: Int,
    endLine: Int,
    expectedLocation: String?,
    expectedText: String?
  ) {
    val resourceLookup = ResourceLookup(projectRule.project)
    val result = resourceLookup.findLambdaLocation("com.example", "MyLambdas.kt", lambdaName, functionName, startLine, endLine)
    if (expectedLocation == null) {
      assertThat(result).isNull()
    }
    else {
      assertThat(result?.source).isEqualTo(expectedLocation)
      assertThat((result?.navigatable as? PsiElement)?.text).isEqualTo(expectedText)
    }
  }
}
