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
package com.android.tools.idea.completion

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.project.guessProjectDir

class AndroidKotlinCompletionContributorTest : AndroidGradleTestCase() {

  override fun setUp() {
    super.setUp()
    StudioFlags.KOTLIN_INCORRECT_SCOPE_CHECK_IN_TESTS.override(true)
  }

  override fun tearDown() {
    super.tearDown()
    try {
      StudioFlags.KOTLIN_INCORRECT_SCOPE_CHECK_IN_TESTS.clearOverride()
    }
    finally {
      super.tearDown()
    }
  }

  fun testAndroidTestIncorrectScopeCompletion() {
    loadProject(TestProjectPaths.TEST_ARTIFACTS_KOTLIN)
    val unitTestPath = "app/src/androidTest/java/com/example/android/kotlin/ExampleInstrumentedTest.kt"
    val file = project.guessProjectDir()!!.findFileByRelativePath(unitTestPath)
    myFixture.openFileInEditor(file!!)

    myFixture.moveCaret("assertEquals(\"com.example.android.kotlin\", appContext.packageName)|")
    myFixture.type("\nExampleUnitTes")

    myFixture.complete(CompletionType.BASIC)
    assertThat(myFixture.lookupElementStrings).doesNotContain("ExampleUnitTest")
  }

  fun testUnitTestIncorrectScopeCompletion() {
    loadProject(TestProjectPaths.TEST_ARTIFACTS_KOTLIN)
    val unitTestPath = "app/src/test/java/com/example/android/kotlin/ExampleUnitTest.kt"
    val file = project.guessProjectDir()!!.findFileByRelativePath(unitTestPath)
    myFixture.openFileInEditor(file!!)

    myFixture.moveCaret("assertEquals(4, 2 + 2)|")
    myFixture.type("\nExampleInstrumentedTes")

    myFixture.complete(CompletionType.BASIC)
    assertThat(myFixture.lookupElementStrings).doesNotContain("ExampleInstrumentedTest")
  }

  fun testFilteredPrivateResources() {
    loadProject(TestProjectPaths.TEST_ARTIFACTS_KOTLIN)
    val activityPath = "app/src/main/java/com/example/android/kotlin/MainActivity.kt"
    val file = project.guessProjectDir()!!.findFileByRelativePath(activityPath)
    myFixture.openFileInEditor(file!!)

    myFixture.moveCaret("setContentView(R.layout.|activity_main)")

    myFixture.complete(CompletionType.BASIC)
    assertThat(myFixture.lookupElementStrings).doesNotContain("abc_action_mode_bar")
    assertThat(myFixture.lookupElementStrings).contains("activity_main")
  }

  fun testFilteredPrivateResourcesInTests() {
    loadProject(TestProjectPaths.TEST_ARTIFACTS_KOTLIN)
    val activityPath = "app/src/androidTest/java/com/example/android/kotlin/ExampleInstrumentedTest.kt"
    val file = project.guessProjectDir()!!.findFileByRelativePath(activityPath)
    myFixture.openFileInEditor(file!!)

    myFixture.moveCaret("assertEquals(\"com.example.android.kotlin\", appContext.packageName)|")
    myFixture.type("\nR.layout.")

    myFixture.complete(CompletionType.BASIC)
    assertThat(myFixture.lookupElementStrings).doesNotContain("abc_action_mode_bar")
    assertThat(myFixture.lookupElementStrings).contains("activity_main")
  }

  fun testFilteredPrivateResourcesAliasedR() {
    loadProject(TestProjectPaths.TEST_ARTIFACTS_KOTLIN)
    val activityPath = "app/src/main/java/com/example/android/kotlin/MainActivity.kt"
    val file = project.guessProjectDir()!!.findFileByRelativePath(activityPath)
    myFixture.openFileInEditor(file!!)

    myFixture.moveCaret("import android.os.Bundle|")
    myFixture.type("\nimport com.example.android.kotlin.R as Q")
    myFixture.moveCaret("setContentView(R.layout.activity_main)|")
    myFixture.type("\nsetContentView(Q.layout.")

    myFixture.complete(CompletionType.BASIC)
    assertThat(myFixture.lookupElementStrings).doesNotContain("abc_action_mode_bar")
    assertThat(myFixture.lookupElementStrings).contains("activity_main")
  }
}
