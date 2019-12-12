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
package com.android.tools.idea.uibuilder.editor.multirepresentation.sourcecode

import com.android.tools.idea.flags.StudioFlags
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import junit.framework.TestCase

class SourceCodeEditorProviderTest : LightJavaCodeInsightFixtureTestCase(){

  lateinit var provider: SourceCodeEditorProvider

  override fun setUp() {
    super.setUp()

    provider = SourceCodeEditorProvider()
  }

  fun testOffByDefault() {
    val file = myFixture.addFileToProject("src/Preview.kt", "")

    assertFalse(provider.accept(file.project, file.virtualFile))
  }


  fun testAcceptsKotlinFile() {
    StudioFlags.NELE_SOURCE_CODE_EDITOR.override(true)

    val file = myFixture.addFileToProject("src/Preview.kt", "")

    assertTrue(provider.accept(file.project, file.virtualFile))
  }

  fun testAcceptsJavaFile() {
    StudioFlags.NELE_SOURCE_CODE_EDITOR.override(true)

    val file = myFixture.addFileToProject("src/Preview.java", "")

    assertTrue(provider.accept(file.project, file.virtualFile))
  }

  fun testDeclinesTxtFile() {
    StudioFlags.NELE_SOURCE_CODE_EDITOR.override(true)

    val file = myFixture.addFileToProject("src/Preview.txt", "")

    assertFalse(provider.accept(file.project, file.virtualFile))
  }

  fun testDeclinesXmlFile() {
    StudioFlags.NELE_SOURCE_CODE_EDITOR.override(true)

    val file = myFixture.addFileToProject("src/Preview.xml", "")

    assertFalse(provider.accept(file.project, file.virtualFile))
  }

  fun testCreatableForKotlinFile() {
    StudioFlags.NELE_SOURCE_CODE_EDITOR.override(true)

    val file = myFixture.addFileToProject("src/Preview.kt", "")

    val editor = provider.createEditor(file.project, file.virtualFile)

    TestCase.assertNotNull(editor)

    provider.disposeEditor(editor)
  }

  override fun tearDown() {
    StudioFlags.NELE_SOURCE_CODE_EDITOR.clearOverride()

    super.tearDown()
  }
}