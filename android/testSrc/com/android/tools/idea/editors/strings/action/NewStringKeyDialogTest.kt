/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.editors.strings.action

import com.android.resources.ResourceType
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.idea.editors.strings.model.StringResourceKey
import com.android.tools.idea.res.IdeResourceNameValidator
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.ui.EditorTextField
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.ResourceFolderManager
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import javax.swing.JButton

@RunWith(JUnit4::class)
@RunsInEdt
class NewStringKeyDialogTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()
  @get:Rule
  val edtRule: EdtRule = EdtRule()

  private lateinit var facet: AndroidFacet

  @Before
  fun setUp() {
    facet = projectRule.module.androidFacet!!
    enableHeadlessDialogs(projectRule.testRootDisposable)
  }

  @Test
  fun cancel() {
    val dialog = NewStringKeyDialog(facet, listOf())

    createModalDialogAndInteractWithIt({ dialog.show() }) { dialogWrapper ->
      val fakeUi = FakeUi(dialogWrapper.rootPane)
      val cancelButton: JButton = fakeUi.getComponent { it.name == CANCEL_BUTTON_ID }
      fakeUi.clickOn(cancelButton)
    }

    assertThat(dialog.isOK).isFalse()
  }

  @Test
  fun emptyKey() {
    val dialog = NewStringKeyDialog(facet, listOf())

    createModalDialogAndInteractWithIt({ dialog.show() }) { dialogWrapper ->
      val fakeUi = FakeUi(dialogWrapper.rootPane)
      val okButton: JButton = fakeUi.getComponent { it.name == OK_BUTTON_ID }
      fakeUi.clickOn(okButton)
    }

    val validationInfo = dialog.doValidate()
    assertThat(validationInfo).isNotNull()
    assertThat(validationInfo!!.message).isEqualTo("Key cannot be empty")
    assertThat(dialog.isOK).isFalse()
  }

  @Test
  fun badResourceName() {
    val bogusResourceName = "bogus resource name with spaces"
    val dialog = NewStringKeyDialog(facet, listOf())

    createModalDialogAndInteractWithIt({ dialog.show() }) { dialogWrapper ->
      val fakeUi = FakeUi(dialogWrapper.rootPane)
      fakeUi.keyField.text = bogusResourceName

      val okButton: JButton = fakeUi.getComponent { it.name == OK_BUTTON_ID }
      fakeUi.clickOn(okButton)
    }

    val validationInfo = dialog.doValidate()
    assertThat(validationInfo).isNotNull()
    assertThat(validationInfo!!.message)
      .isEqualTo(IdeResourceNameValidator.forResourceName(ResourceType.STRING).getErrorText(bogusResourceName))
    assertThat(dialog.isOK).isFalse()
  }

  @Test
  fun emptyDefaultValue() {
    val resourceName = "my_excellent_resource_name"
    val dialog = NewStringKeyDialog(facet, listOf())

    createModalDialogAndInteractWithIt({ dialog.show() }) { dialogWrapper ->
      val fakeUi = FakeUi(dialogWrapper.rootPane)
      fakeUi.keyField.text = resourceName

      val okButton: JButton = fakeUi.getComponent { it.name == OK_BUTTON_ID }
      fakeUi.clickOn(okButton)
    }

    val validationInfo = dialog.doValidate()
    assertThat(validationInfo).isNotNull()
    assertThat(validationInfo!!.message).isEqualTo("Default Value cannot be empty")
    assertThat(dialog.isOK).isFalse()
  }

  @Test
  fun resourceExists() {
    val resourceDirName = "res"
    val resourceDirectory: VirtualFile = mock()
    whenever(resourceDirectory.path).thenReturn("${projectRule.project.basePath}/$resourceDirName")
    val resourceFolderManager: ResourceFolderManager = mock()
    projectRule.module.replaceService(
      ResourceFolderManager::class.java, resourceFolderManager, projectRule.testRootDisposable)
    whenever(resourceFolderManager.folders).thenReturn(listOf(resourceDirectory))
    val resourceName = "my_excellent_resource_name"
    val dialog = NewStringKeyDialog(facet, listOf(StringResourceKey(resourceName, resourceDirectory)))

    createModalDialogAndInteractWithIt({ dialog.show() }) { dialogWrapper ->
      val fakeUi = FakeUi(dialogWrapper.rootPane)
      fakeUi.keyField.text = resourceName
      fakeUi.defaultValueField.text = "This is such an amazing default value!"
      val okButton: JButton = fakeUi.getComponent { it.name == OK_BUTTON_ID }
      fakeUi.clickOn(okButton)
    }

    val validationInfo = dialog.doValidate()
    assertThat(validationInfo).isNotNull()
    assertThat(validationInfo!!.message).isEqualTo("$resourceName already exists in $resourceDirName")
    assertThat(dialog.isOK).isFalse()
  }

  @Test
  fun ok() {
    val resourceName = "my_excellent_resource_name"
    val dialog = NewStringKeyDialog(facet, listOf())

    createModalDialogAndInteractWithIt({ dialog.show() }) { dialogWrapper ->
      val fakeUi = FakeUi(dialogWrapper.rootPane)
      fakeUi.keyField.text = resourceName
      fakeUi.defaultValueField.text = "This is such an amazing default value!"
      val okButton: JButton = fakeUi.getComponent { it.name == OK_BUTTON_ID }
      fakeUi.clickOn(okButton)
    }

    val validationInfo = dialog.doValidate()
    assertThat(validationInfo).isNull()
    assertThat(dialog.isOK).isTrue()
  }

  private val FakeUi.keyField: EditorTextField
    get() = getComponent { it.name == KEY_FIELD_ID }

  private val FakeUi.defaultValueField: EditorTextField
    get() = getComponent { it.name == DEFAULT_VALUE_FIELD_ID }

  companion object {
    private const val OK_BUTTON_ID = "okButton"
    private const val CANCEL_BUTTON_ID = "cancelButton"
    private const val KEY_FIELD_ID = "keyTextField"
    private const val DEFAULT_VALUE_FIELD_ID = "defaultValueTextField"
  }
}