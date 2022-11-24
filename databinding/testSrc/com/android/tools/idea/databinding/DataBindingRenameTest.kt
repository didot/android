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
package com.android.tools.idea.databinding

import com.android.tools.idea.databinding.module.LayoutBindingModuleCache
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.renameElementAtCaretUsingAndroidHandler
import com.android.tools.idea.util.androidFacet
import com.google.common.collect.Lists
import com.intellij.ide.DataManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.impl.DebugUtil
import com.intellij.refactoring.actions.RenameElementAction
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Test of rename refactoring involving Java language elements generated by Data Binding or methods declared in Java language elements.
 */
@RunWith(Parameterized::class)
@RunsInEdt
class DataBindingRenameTest(private val dataBindingMode: DataBindingMode) {
  companion object {
    @get:Parameterized.Parameters(name = "{0}")
    @get:JvmStatic
    val parameters: List<DataBindingMode>
      get() = Lists.newArrayList(DataBindingMode.SUPPORT, DataBindingMode.ANDROIDX)
  }

  private val projectRule = AndroidProjectRule.withSdk()

  // The tests need to run on the EDT thread but we must initialize the project rule off of it
  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  // Legal cast because project rule is initialized with onDisk
  private val fixture by lazy { projectRule.fixture as JavaCodeInsightTestFixture }

  private val facet
    get() = projectRule.module.androidFacet!!

  @Before
  fun setUp() {
    fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT + "/databinding"

    fixture.addFileToProject("AndroidManifest.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.db">
        <application />
      </manifest>
    """.trimIndent())

    LayoutBindingModuleCache.getInstance(facet).dataBindingMode = dataBindingMode
  }

  private fun checkAndRenameElementAtCaret(newName: String) {
    val action = RenameElementAction()
    TestActionEvent.createTestEvent(action, DataManager.getInstance().getDataContext(fixture.editor.component)).let { event ->
      action.update(event)
      assertTrue(event.presentation.isEnabled && event.presentation.isVisible)
    }

    // Having a performPsiModification block here is necessary or else IJ throws an "PSI invalidated outside transaction" error
    DebugUtil.performPsiModification<Throwable>(null) {
      if (!fixture.renameElementAtCaretUsingAndroidHandler(newName)) {
        fixture.renameElementAtCaret(newName)
      }
    }

    // Save the renaming changes to disk.
    saveAllDocuments()
  }

  private fun saveAllDocuments() = runWriteAction {
    FileDocumentManager.getInstance().saveAllDocuments()
  }

  /**
   * Checks renaming of a resource IDs when a Java field generated from that resource by Data Binding is renamed.
   *
   * @see com.android.tools.idea.databinding.DataBindingRenamer
   */
  @Test
  fun assertRenameFieldDerivedFromResourceId() {
    val layoutFile = fixture.addFileToProject(
      "res/layout/activity_main.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
            <Button
                android:id="@+id/button"
                android:layout_width="fill_parent"
                android:layout_height="fill_parent" />
        </LinearLayout>
      </layout>
    """.trimIndent())

    val classFile = fixture.addFileToProject(
      "src/java/test/db/MainActivity.java",
      // language=JAVA
      """
      package test.db;

      import android.app.Activity;
      import android.os.Bundle;

      import test.db.databinding.ActivityMainBinding;

      public class MainActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
              System.out.println(binding.button.getId());
              setContentView(binding.getRoot());
          }
      }
    """.trimIndent()
    )

    fixture.configureFromExistingVirtualFile(classFile.virtualFile)
    val editor = fixture.editor
    val javaTextSnapshot = editor.document.text
    val layoutTextSnapshot = VfsUtilCore.loadText(layoutFile.virtualFile)

    // Make rename in source
    fixture.moveCaret("binding.but|ton")
    checkAndRenameElementAtCaret("buttonAfterRename")

    // Check expected results
    assertEquals(javaTextSnapshot.replace("button", "buttonAfterRename"), VfsUtilCore.loadText(classFile.virtualFile))
    assertEquals(layoutTextSnapshot.replace("button", "button_after_rename"), VfsUtilCore.loadText(layoutFile.virtualFile))
  }

  /**
   * Checks renaming of method referenced from data binding expression.
   *
   * See also [assertRenameGetter].
   */
  @Test
  fun assertRenameNonGetterMethod() {
    val layoutFile = fixture.addFileToProject(
      "res/layout/activity_main.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name='model' type='test.db.Model' />
        </data>
        <LinearLayout
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
          <TextView
            android:text="@{model.generateStrnig()}"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content" />
        </LinearLayout>
      </layout>
    """.trimIndent())

    val classFile = fixture.addFileToProject(
      "src/java/test/db/Model.java",
      // language=JAVA
      """
      package test.db;

      public class Model {
        public String generateStrnig() {
          return "FAKE GENERATED STRING";
        }
      }
    """.trimIndent()
    )

    fixture.configureFromExistingVirtualFile(classFile.virtualFile)
    val editor = fixture.editor
    val javaTextSnapshot = editor.document.text
    val layoutTextSnapshot = VfsUtilCore.loadText(layoutFile.virtualFile)

    // Make rename in source
    fixture.moveCaret("generateStr|nig")
    checkAndRenameElementAtCaret("generateString")

    // Check expected results
    assertEquals(javaTextSnapshot.replace("generateStrnig", "generateString"), VfsUtilCore.loadText(classFile.virtualFile))
    assertEquals(layoutTextSnapshot.replace("generateStrnig", "generateString"), VfsUtilCore.loadText(layoutFile.virtualFile))
  }

  /**
   * Checks renaming of field referenced from data binding expression.
   */
  @Test
  fun assertRenameField() {
    val layoutFile = fixture.addFileToProject(
      "res/layout/activity_main.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name='user' type='test.db.User' />
        </data>
        <LinearLayout
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
          <TextView
            android:text="@{user.fristNaem}"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content" />
        </LinearLayout>
      </layout>
    """.trimIndent())

    val classFile = fixture.addFileToProject(
      "src/java/test/db/User.java",
      // language=JAVA
      """
      package test.db;

      public class User {
        public String fristNaem;
        public String lastName;
      }
    """.trimIndent()
    )

    fixture.configureFromExistingVirtualFile(classFile.virtualFile)
    val editor = fixture.editor
    val javaTextSnapshot = editor.document.text
    val layoutTextSnapshot = VfsUtilCore.loadText(layoutFile.virtualFile)

    // Make rename in source
    fixture.moveCaret("frist|Naem")
    checkAndRenameElementAtCaret("firstName")

    // Check expected results
    assertEquals(javaTextSnapshot.replace("fristNaem", "firstName"), VfsUtilCore.loadText(classFile.virtualFile))
    assertEquals(layoutTextSnapshot.replace("fristNaem", "firstName"), VfsUtilCore.loadText(layoutFile.virtualFile))
  }

  /**
   * Checks renaming of field getter referenced from data binding expression.
   */
  @Test
  fun assertRenameGetter() {
    val layoutFile = fixture.addFileToProject(
      "res/layout/activity_main.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name='user' type='test.db.User' />
        </data>
        <LinearLayout
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
          <TextView
            android:text="@{user.firstNaem}"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content" />
        </LinearLayout>
      </layout>
    """.trimIndent())

    val classFile = fixture.addFileToProject(
      "src/java/test/db/User.java",
      // language=JAVA
      """
      package test.db;

      public class User {
        public String getFirstNaem() {
          return "John";
        }

        public String getLastName() {
          return "Doe";
        }
      }
    """.trimIndent()
    )

    fixture.configureFromExistingVirtualFile(classFile.virtualFile)
    val editor = fixture.editor
    val javaTextSnapshot = editor.document.text
    val layoutTextSnapshot = VfsUtilCore.loadText(layoutFile.virtualFile)

    // Make rename in source
    fixture.moveCaret("get|FirstNaem")
    checkAndRenameElementAtCaret("getFirstName")

    // Check expected results
    assertEquals(javaTextSnapshot.replace("getFirstNaem", "getFirstName"), VfsUtilCore.loadText(classFile.virtualFile))
    assertEquals(layoutTextSnapshot.replace("firstNaem", "firstName"), VfsUtilCore.loadText(layoutFile.virtualFile))
  }

  /**
   * Checks renaming of resources referenced from data binding expression.
   */
  @Test
  fun assertRenameResource() {

    val stringsFile = fixture.addFileToProject(
      "res/values/strings.xml",
      // language=XML
      """
        <resources>
          <string name="app_name">appWithDataBinding</string>
          <string name="title_activity_main">MainActivity</string>
        
          <string name="hello_wrld">Hello world!</string>
          <string name="action_settings">Settings</string>
        </resources>

    """.trimIndent())

    val layoutFile = fixture.addFileToProject(
      "res/layout/activity_main.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
          <TextView
            android:text="@{@string/hello_wrld}"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content" />
        </LinearLayout>
      </layout>
    """.trimIndent())

    fixture.configureFromExistingVirtualFile(stringsFile.virtualFile)
    val editor = fixture.editor
    val stringsTextSnapshot = editor.document.text
    val layoutTextSnapshot = VfsUtilCore.loadText(layoutFile.virtualFile)

    // Make rename in source
    fixture.moveCaret("hello_w|rld")
    checkAndRenameElementAtCaret("hello_world")

    // Check expected results
    assertEquals(stringsTextSnapshot.replace("hello_wrld", "hello_world"), VfsUtilCore.loadText(stringsFile.virtualFile))
    assertEquals(layoutTextSnapshot.replace("hello_wrld", "hello_world"), VfsUtilCore.loadText(layoutFile.virtualFile))
  }
}
