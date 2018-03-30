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
package org.jetbrains.android.dom

import com.android.builder.model.AndroidProject
import com.android.tools.idea.model.TestAndroidModel
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.goToElementAtCaret
import com.android.tools.idea.testing.highlightedAs
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.lang.annotation.HighlightSeverity.ERROR
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.inspections.AndroidDomInspection
import org.jetbrains.android.inspections.AndroidElementNotAllowedInspection
import org.jetbrains.android.inspections.AndroidUnknownAttributeInspection

/**
 * Tests for code editor features when working with value resources XML files in namespaced projects.
 *
 * Namespaced equivalent of [AndroidXmlResourcesDomTest], covers features that have been fixed to work in namespaced projects.
 */
class AndroidNamespacedXmlResourcesDomTest : AndroidTestCase() {

  private val libRes get() = getAdditionalModulePath("lib") + "/res"

  override fun configureAdditionalModules(
    projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture>,
    modules: MutableList<MyAdditionalModuleData>
  ) {
    addModuleWithAndroidFacet(
      projectBuilder,
      modules,
      "lib",
      AndroidProject.PROJECT_TYPE_LIBRARY,
      true
    )
  }

  override fun setUp() {
    super.setUp()

    myFixture.enableInspections(
      AndroidDomInspection::class.java,
      AndroidUnknownAttributeInspection::class.java,
      AndroidElementNotAllowedInspection::class.java
    )

    runUndoTransparentWriteAction {
      myFacet.run {
        configuration.model = TestAndroidModel.namespaced(this)
        manifest!!.`package`.value = "com.example.app"
      }

      AndroidFacet.getInstance(getAdditionalModuleByName("lib")!!)!!.run {
        configuration.model = TestAndroidModel.namespaced(this)
        manifest!!.`package`.value = "com.example.lib"
      }
    }

    myFixture.addFileToProject(
      "$libRes/values/strings.xml",
      // language=xml
      """
        <resources>
          <string name="hello">Hello from lib</string>
        </resources>
      """.trimIndent()
    )

  }

  fun testDifferentNamespacesCompletion() {
    val values = myFixture.addFileToProject(
      "res/values/values.xml",
      """
        <resources>
          <string name="some_string">Some string</string>
          <string name="app_string">@${caret}</string>
        </resources>
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(values.virtualFile)
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).containsExactly(
      "@android:",
      "@string/app_string",
      "@string/some_string",
      "@com.example.lib:string/hello"
    )
  }

  fun testDifferentNamespacesResolution() {
    val values = myFixture.addFileToProject(
      "res/values/values.xml",
      """
        <resources>
          <string name="s1">@android:color/black</string>
          <string name="s2">@com.example.lib:string/hello</string>
          <string name="s3">@string/s1</string>
          <string name="s4">${"@android:string/made_up" highlightedAs ERROR}</string>
          <string name="s5">${"@string/made_up" highlightedAs ERROR}</string>
          <string name="s6">${"@com.example.lib:string/made_up" highlightedAs ERROR}</string>
          <string name="s7">${"@${"made_up" highlightedAs ERROR}:string/s1" highlightedAs ERROR}</string>
        </resources>
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(values.virtualFile)
    myFixture.checkHighlighting(true, false, false)
  }

  fun testDifferentNamespacesPrefixCompletion() {
    val values = myFixture.addFileToProject(
      "res/values/values.xml",
      """
        <resources xmlns:lib="http://schemas.android.com/apk/res/com.example.lib" xmlns:a="http://schemas.android.com/apk/res/android">
          <string name="some_string">Some string</string>
          <string name="app_string">@${caret}</string>
        </resources>
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(values.virtualFile)
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).containsExactly(
      "@a:",
      "@string/app_string",
      "@string/some_string",
      "@lib:string/hello"
    )

    myFixture.type("a:")
    myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).contains("@a:string/cancel")
  }

  fun testDifferentNamespacesPrefixResolution() {
    val values = myFixture.addFileToProject(
      "res/values/values.xml",
      """
        <resources xmlns:lib="http://schemas.android.com/apk/res/com.example.lib" xmlns:a="http://schemas.android.com/apk/res/android">
          <string name="s1">@a:color/black</string>
          <string name="s2">@lib:string/hello</string>
          <string name="s3">@string/s1</string>
          <string name="s4">${"@a:string/made_up" highlightedAs ERROR}</string>
          <string name="s5">${"@string/made_up" highlightedAs ERROR}</string>
          <string name="s6">${"@lib:string/made_up" highlightedAs ERROR}</string>
          <string name="s7">${"@${"made_up" highlightedAs ERROR}:string/s1" highlightedAs ERROR}</string>
        </resources>
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(values.virtualFile)
    myFixture.checkHighlighting(true, false, false)
  }

  fun testNamespacePrefixReferences_localXmlNs() {
    val values = myFixture.addFileToProject(
      "res/values/values.xml",
      """
        <resources xmlns:lib="http://schemas.android.com/apk/res/com.example.lib" xmlns:a="http://schemas.android.com/apk/res/android">
          <string name="some_string">Some string</string>
          <string name="app_string">@${caret}lib:string/hello</string>
        </resources>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(values.virtualFile)
    myFixture.checkHighlighting()

    myFixture.renameElementAtCaret("newName")
    myFixture.checkResult(
      """
        <resources xmlns:newName="http://schemas.android.com/apk/res/com.example.lib" xmlns:a="http://schemas.android.com/apk/res/android">
          <string name="some_string">Some string</string>
          <string name="app_string">@${caret}newName:string/hello</string>
        </resources>
      """.trimIndent()
    )

    myFixture.goToElementAtCaret()
    myFixture.checkResult(
      """
        <resources xmlns:${caret}newName="http://schemas.android.com/apk/res/com.example.lib" xmlns:a="http://schemas.android.com/apk/res/android">
          <string name="some_string">Some string</string>
          <string name="app_string">@newName:string/hello</string>
        </resources>
      """.trimIndent()
    )
  }

  fun testNamespacePrefixReferences_packageName() {
    val values = myFixture.addFileToProject(
      "res/values/values.xml",
      """
        <resources>
          <string name="some_string">Some string</string>
          <string name="app_string">@${caret}com.example.lib:string/hello</string>
        </resources>
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(values.virtualFile)
    myFixture.checkHighlighting()

    myFixture.goToElementAtCaret()
    myFixture.checkResult(
      """
        <resources>
          <string name="some_string">Some string</string>
          <string name="app_string">@${caret}com.example.lib:string/hello</string>
        </resources>
      """.trimIndent()
    )
  }
}
