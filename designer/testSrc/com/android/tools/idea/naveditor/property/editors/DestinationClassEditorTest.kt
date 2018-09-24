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
package com.android.tools.idea.naveditor.property.editors

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LAYOUT
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TOOLS_URI
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.common.property.editors.EnumEditor
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.property.inspector.SimpleProperty
import com.android.tools.idea.uibuilder.property.editors.NlEditingListener
import com.android.tools.idea.uibuilder.property.editors.support.ValueWithDisplayString
import com.android.tools.idea.uibuilder.property.fixtures.EnumEditorFixture
import com.intellij.psi.PsiClass
import com.intellij.psi.util.ClassUtil
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

private val constructEditor =
  { listener: NlEditingListener, comboBox: EnumEditor.CustomComboBox -> DestinationClassEditor(listener, comboBox) }

class DestinationClassEditorTest : NavTestCase() {
  fun testFragment() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1")
        activity("activity1")
      }
    }
    val property = mock(NlProperty::class.java)
    `when`(property.components).thenReturn(listOf(model.find("f1")))

    var choices = EnumEditorFixture.create(constructEditor).use {
      it.setProperty(property)
        .showPopup()
        .choices
    }

    assertContainsElements(choices,
        "none" displayFor null,
        "mytest.navtest.BlankFragment" displayFor "mytest.navtest.BlankFragment")
    assertDoesntContain(choices, "mytest.navtest.MainActivity" displayFor "mytest.navtest.MainActivity")

    `when`(property.components).thenReturn(listOf(model.find("activity1")))

    choices = EnumEditorFixture.create(constructEditor).use {
      it.setProperty(property)
        .showPopup()
        .choices
    }

    assertContainsElements(choices,
        "none" displayFor null,
        "mytest.navtest.MainActivity" displayFor "mytest.navtest.MainActivity")
    assertDoesntContain(choices, "mytest.navtest.BlankFragment" displayFor "mytest.navtest.BlankFragment")
  }

  fun testSetsLayout() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1")
        activity("activity1")
      }
    }
    val property = SimpleProperty(ATTR_NAME, listOf(model.find("f1")!!), ANDROID_URI, null)

    EnumEditorFixture.create { _, combo -> DestinationClassEditor(DestinationClassEditor.Listener, combo)}.use {
      it.setProperty(property)
        .gainFocus()
        .setSelectedModelItem(ValueWithDisplayString("mytest.navtest.MainActivity", "mytest.navtest.MainActivity"))
        .loseFocus()
    }
    assertEquals("@layout/activity_main", model.find("f1")?.getAttribute(TOOLS_URI, ATTR_LAYOUT))
  }

  fun testNavHostFragment() {
    val relativePath = "src/mytest/navtest/NavHostFragmentChild.java"
    val fileText = """
      .package mytest.navtest;
      .import androidx.navigation.fragment.NavHostFragment;
      .
      .public class NavHostFragmentChild extends NavHostFragment {
      .}
      """.trimMargin(".")

    myFixture.addFileToProject(relativePath, fileText)

    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1")
        activity("activity1")
      }
    }

    val property = mock(NlProperty::class.java)
    `when`(property.components).thenReturn(listOf(model.find("f1")))

    val choices = EnumEditorFixture.create(constructEditor).use {
      it.setProperty(property)
        .showPopup()
        .choices
    }

    assertContainsElements(choices,
                           "none" displayFor null,
                           "mytest.navtest.BlankFragment" displayFor "mytest.navtest.BlankFragment")
    assertDoesntContain(choices, "mytest.navtest.NavHostFragmentChild" displayFor "mytest.navtest.NavHostFragmentChild")
  }

  fun testProjectSorting() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1")
        activity("activity1")
      }
    }
    val property = mock(NlProperty::class.java)
    `when`(property.components).thenReturn(listOf(model.find("f1")))

    var isInProject: (PsiClass) -> Boolean = { psiClass -> psiClass.qualifiedName == "mytest.navtest.BlankFragment" }

    var choices = EnumEditorFixture.create { listener, comboBox -> DestinationClassEditor(listener, comboBox, isInProject) }.use {
      it.setProperty(property)
        .showPopup()
        .choices
    }

    assertOrderedEquals(choices,
                        "none" displayFor null,
                        "mytest.navtest.BlankFragment" displayFor "mytest.navtest.BlankFragment",
                        "android.support.v4.app.Fragment" displayFor "android.support.v4.app.Fragment")

    isInProject = { psiClass -> psiClass.qualifiedName == "android.support.v4.app.Fragment" }

    choices = EnumEditorFixture.create { listener, comboBox -> DestinationClassEditor(listener, comboBox, isInProject) }.use {
      it.setProperty(property)
        .showPopup()
        .choices
    }

    assertOrderedEquals(choices,
                        "none" displayFor null,
                        "android.support.v4.app.Fragment" displayFor "android.support.v4.app.Fragment",
                        "mytest.navtest.BlankFragment" displayFor "mytest.navtest.BlankFragment")
  }

  private infix fun String.displayFor(value: String?): ValueWithDisplayString {
    return when (value) {
      "-" -> ValueWithDisplayString.SEPARATOR
      null -> ValueWithDisplayString.UNSET
      else -> ValueWithDisplayString("${ClassUtil.extractClassName(value)} (${ClassUtil.extractPackageName(value)})", value)
    }
  }
}
