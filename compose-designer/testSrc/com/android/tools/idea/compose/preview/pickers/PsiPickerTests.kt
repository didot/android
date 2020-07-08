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
package com.android.tools.idea.compose.preview.pickers

import com.android.tools.idea.compose.ComposeProjectRule
import com.android.tools.idea.compose.preview.AnnotationFilePreviewElementFinder
import com.android.tools.idea.compose.preview.pickers.properties.PsiCallPropertyModel
import com.android.tools.idea.compose.preview.pickers.properties.PsiPropertyModel
import com.android.tools.idea.compose.preview.util.PreviewElement
import com.intellij.openapi.application.ReadAction
import org.intellij.lang.annotations.Language
import org.jetbrains.android.compose.ComposeLibraryNamespace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

private fun PreviewElement.annotationText(): String = ReadAction.compute<String, Throwable> {
  previewElementDefinitionPsi?.element?.text ?: ""
}

@RunWith(Parameterized::class)
class PsiPickerTests(libraryNamespace: ComposeLibraryNamespace) {
  companion object {
    @Suppress("unused") // Used by JUnit via reflection
    @JvmStatic
    @get:Parameterized.Parameters(name = "{0}")
    val namespaces = listOf(ComposeLibraryNamespace.ANDROIDX_UI, ComposeLibraryNamespace.ANDROIDX_COMPOSE)
  }

  private val PREVIEW_TOOLING_PACKAGE = libraryNamespace.previewPackage

  @get:Rule
  val projectRule = ComposeProjectRule(libraryNamespace = libraryNamespace)
  private val fixture get() = projectRule.fixture
  private val project get() = projectRule.project

  @Test
  fun `the psi model reads the preview annotation correctly`() {
    @Language("kotlin")
    val fileContent = """
      import androidx.compose.Composable
      import $PREVIEW_TOOLING_PACKAGE.Preview

      @Composable
      @Preview
      fun PreviewNoParameters() {
      }

      @Composable
      @Preview("named")
      fun PreviewWithName() {
      }

      @Composable
      @Preview
      fun PreviewParameters() {
      }
      
      private const val nameFromConst = "Name from Const"
      
      @Composable
      @Preview(nameFromConst)
      fun PreviewWithNameFromConst() {
      }
    """.trimIndent()

    val file = fixture.configureByText("Test.kt", fileContent)
    val previews = AnnotationFilePreviewElementFinder.findPreviewMethods(fixture.project, file.virtualFile).toList()
    ReadAction.run<Throwable> {
      previews[0].also { noParametersPreview ->
        val parsed = PsiCallPropertyModel.fromPreviewElement(project, noParametersPreview)
        assertNotNull(parsed.properties["", "name"])
        assertNull(parsed.properties.getOrNull("", "name2"))
      }
      previews[1].also { namedPreview ->
        val parsed = PsiCallPropertyModel.fromPreviewElement(project, namedPreview)
        assertEquals("named", parsed.properties["", "name"].value)
        // Check default value
        assertEquals("-1", parsed.properties["", "apiLevel"].value)
      }
      previews[3].also { namedPreviewFromConst ->
        val parsed = PsiCallPropertyModel.fromPreviewElement(project, namedPreviewFromConst)
        assertEquals("Name from Const", parsed.properties["", "name"].value)
      }
    }
  }

  @Test
  fun `updating model updates the psi correctly`() {
    @Language("kotlin")
    val fileContent = """
      import androidx.compose.Composable
      import $PREVIEW_TOOLING_PACKAGE.Preview

      @Composable
      @Preview(name = "Test")
      fun PreviewNoParameters() {
      }
    """.trimIndent()

    val file = fixture.configureByText("Test.kt", fileContent)
    val noParametersPreview = AnnotationFilePreviewElementFinder.findPreviewMethods(fixture.project, file.virtualFile).first()
    val model = ReadAction.compute<PsiPropertyModel, Throwable> { PsiCallPropertyModel.fromPreviewElement(project, noParametersPreview) }
    model.properties["", "name"].value = "NoHello"

    // Try to override our previous write. Only the last one should persist
    model.properties["", "name"].value = "Hello"
    assertEquals("@Preview(name = \"Hello\")", noParametersPreview.annotationText())

    // Add other properties
    model.properties["", "group"].value = "Group2"
    model.properties["", "widthDp"].value = "32"
    assertEquals("@Preview(name = \"Hello\", group = \"Group2\", widthDp = 32)",
      noParametersPreview.annotationText())

    // Set back to the default value
    model.properties["", "group"].value = null
    model.properties["", "widthDp"].value = null
    assertEquals("@Preview(name = \"Hello\")", noParametersPreview.annotationText())

    model.properties["", "name"].value = null
    try {
      model.properties["", "notexists"].value = "3"
      fail("Nonexistent property should throw NoSuchElementException")
    } catch (expected: NoSuchElementException) {
    }
    assertEquals("@Preview", noParametersPreview.annotationText())
  }
}