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
package com.android.tools.idea.imports

import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.flags.override
import com.android.tools.idea.testing.getIntentionAction
import com.android.tools.idea.testing.highlightedAs
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.moveCaret
import com.intellij.lang.annotation.HighlightSeverity.ERROR
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.replaceService
import org.jetbrains.android.dom.inspections.AndroidDomInspection
import org.jetbrains.android.dom.inspections.AndroidUnresolvableTagInspection
import java.util.concurrent.TimeUnit

class AndroidMavenImportFixTest : AndroidGradleTestCase() {
  private val JavaCodeInsightTestFixture.fileEditor: TextEditor
    get() = TextEditorProvider.getInstance().getTextEditor(this.editor)

  fun testLegacyAddLibraryQuickfix() {
    StudioFlags.ENABLE_SUGGESTED_IMPORT.override(false, myFixture.testRootDisposable)
    ApplicationManager.getApplication().replaceService(
      MavenClassRegistryManager::class.java,
      fakeMavenClassRegistryManager,
      myFixture.testRootDisposable
    )

    val inspection = AndroidUnresolvableTagInspection()
    myFixture.enableInspections(inspection)

    loadProject(TestProjectPaths.MIGRATE_TO_APP_COMPAT) // project not using AndroidX
    assertBuildGradle(project) { !it.contains("com.android.support:recyclerview-v7:") } // not already using recyclerview

    myFixture.loadNewFile(
      "app/src/main/res/layout/my_layout.xml",
      """
    <?xml version="1.0" encoding="utf-8"?>
    <${
        "android.support.v7.widget.RecyclerView"
          .highlightedAs(ERROR, "Cannot resolve class android.support.v7.widget.RecyclerView")
      } />
    """.trimIndent()
    )

    myFixture.checkHighlighting(true, false, false)
    myFixture.moveCaret("Recycler|View")
    val action = myFixture.getIntentionAction("Add dependency on com.android.support:recyclerview-v7")!!

    assertTrue(action.isAvailable(myFixture.project, myFixture.editor, myFixture.file))
    WriteCommandAction.runWriteCommandAction(myFixture.project, Runnable {
      action.invoke(myFixture.project, myFixture.editor, myFixture.file)
    })

    // Wait for the sync
    requestSyncAndWait() // this is redundant but we can't get a handle on the internal sync state of the first action

    assertBuildGradle(project) { it.contains("implementation 'com.android.support:recyclerview-v7:") }
  }

  fun testSuggestedImport_unresolvedViewTag() {
    // Unresolved view class: <androidx.recyclerview.widget.RecyclerView .../>.
    StudioFlags.ENABLE_SUGGESTED_IMPORT.override(true, myFixture.testRootDisposable)
    ApplicationManager.getApplication().replaceService(
      MavenClassRegistryManager::class.java,
      fakeMavenClassRegistryManager,
      myFixture.testRootDisposable
    )

    val inspection = AndroidUnresolvableTagInspection()
    myFixture.enableInspections(inspection)

    loadProject(TestProjectPaths.ANDROIDX_SIMPLE) // project using AndroidX
    assertBuildGradle(project) { !it.contains("androidx.recyclerview:recyclerview:") } // not already using recyclerview

    myFixture.loadNewFile(
      "app/src/main/res/layout/my_layout.xml",
      """
    <?xml version="1.0" encoding="utf-8"?>
    <${
        "androidx.recyclerview.widget.RecyclerView"
          .highlightedAs(ERROR, "Cannot resolve class androidx.recyclerview.widget.RecyclerView")
      } />
    """.trimIndent()
    )

    myFixture.checkHighlighting(true, false, false)
    myFixture.moveCaret("Recycler|View")
    val action = myFixture.getIntentionAction("Add dependency on androidx.recyclerview:recyclerview")!!

    assertTrue(action.isAvailable(myFixture.project, myFixture.editor, myFixture.file))
    WriteCommandAction.runWriteCommandAction(myFixture.project, Runnable {
      action.invoke(myFixture.project, myFixture.editor, myFixture.file)
    })

    // Wait for the sync
    requestSyncAndWait() // this is redundant but we can't get a handle on the internal sync state of the first action

    assertBuildGradle(project) { it.contains("implementation 'androidx.recyclerview:recyclerview:") }
  }

  fun testSuggestedImport_unresolvedAttrName() {
    // Unresolved fragment class: <fragment android:name="com.google.android.gms.maps.SupportMapFragment" .../>.
    StudioFlags.ENABLE_SUGGESTED_IMPORT.override(true, myFixture.testRootDisposable)
    ApplicationManager.getApplication().replaceService(
      MavenClassRegistryManager::class.java,
      fakeMavenClassRegistryManager,
      myFixture.testRootDisposable
    )

    val inspection = AndroidDomInspection()
    myFixture.enableInspections(inspection)

    loadProject(TestProjectPaths.ANDROIDX_SIMPLE) // project using AndroidX
    assertBuildGradle(project) { !it.contains("com.google.android.gms:play-services-maps:") } // not already using SupportMapFragment

    myFixture.loadNewFile(
      "app/src/main/res/layout/my_layout.xml",
      """
        <?xml version="1.0" encoding="utf-8"?>
        <fragment xmlns:android="http://schemas.android.com/apk/res/android"
          android:name="com.google.${
            "android".highlightedAs(ERROR, "Unresolved package 'android'")
          }.${
            "gms".highlightedAs(ERROR, "Unresolved package 'gms'")
          }.${
            "maps".highlightedAs(ERROR, "Unresolved package 'maps'")
          }.${
            "SupportMapFragment".highlightedAs(ERROR, "Unresolved class 'SupportMapFragment'")
          }" android:layout_width="match_parent" android:layout_height="match_parent" />
      """.trimIndent()
    )

    myFixture.checkHighlighting(true, false, false)
    myFixture.moveCaret("gm|s")
    val actionOnPackage = myFixture.getIntentionAction("Add dependency on com.google.android.gms:play-services-maps")!!
    assertTrue(actionOnPackage.isAvailable(myFixture.project, myFixture.editor, myFixture.file))

    myFixture.moveCaret("SupportMap|Fragment")
    val actionOnClass = myFixture.getIntentionAction("Add dependency on com.google.android.gms:play-services-maps")!!
    assertTrue(actionOnClass.isAvailable(myFixture.project, myFixture.editor, myFixture.file))

    WriteCommandAction.runWriteCommandAction(myFixture.project, Runnable {
      actionOnClass.invoke(myFixture.project, myFixture.editor, myFixture.file)
    })

    // Wait for the sync
    requestSyncAndWait() // this is redundant but we can't get a handle on the internal sync state of the first action

    assertBuildGradle(project) { it.contains("implementation 'com.google.android.gms:play-services-maps:") }
  }

  fun testSuggestedImport_undo() {
    // Unresolved view class: <androidx.recyclerview.widget.RecyclerView .../>.
    StudioFlags.ENABLE_SUGGESTED_IMPORT.override(true, myFixture.testRootDisposable)
    ApplicationManager.getApplication().replaceService(
      MavenClassRegistryManager::class.java,
      fakeMavenClassRegistryManager,
      myFixture.testRootDisposable
    )

    val inspection = AndroidUnresolvableTagInspection()
    myFixture.enableInspections(inspection)

    loadProject(TestProjectPaths.ANDROIDX_SIMPLE) // project using AndroidX
    assertBuildGradle(project) { !it.contains("androidx.recyclerview:recyclerview:") } // not already using recyclerview

    myFixture.loadNewFile(
      "app/src/main/res/layout/my_layout.xml",
      """
    <?xml version="1.0" encoding="utf-8"?>
    <${
        "androidx.recyclerview.widget.RecyclerView"
          .highlightedAs(ERROR, "Cannot resolve class androidx.recyclerview.widget.RecyclerView")
      } />
    """.trimIndent()
    )

    myFixture.checkHighlighting(true, false, false)
    myFixture.moveCaret("Recycler|View")
    val action = myFixture.getIntentionAction("Add dependency on androidx.recyclerview:recyclerview")!!

    assertTrue(action.isAvailable(myFixture.project, myFixture.editor, myFixture.file))
    WriteCommandAction.runWriteCommandAction(myFixture.project, Runnable {
      action.invoke(myFixture.project, myFixture.editor, myFixture.file)
    })

    // Wait for the sync
    requestSyncAndWait() // this is redundant but we can't get a handle on the internal sync state of the first action
    assertBuildGradle(project) { it.contains("implementation 'androidx.recyclerview:recyclerview:") }

    // Undo.
    UndoManager.getInstance(myFixture.project).undo(myFixture.fileEditor)
    waitForCondition(1, TimeUnit.SECONDS) {
      checkBuildGradle(project) { !it.contains("implementation 'androidx.recyclerview:recyclerview:") }
    }
  }

  fun testSuggestedImport_redo() {
    // Unresolved view class: <androidx.recyclerview.widget.RecyclerView .../>.
    StudioFlags.ENABLE_SUGGESTED_IMPORT.override(true, myFixture.testRootDisposable)
    ApplicationManager.getApplication().replaceService(
      MavenClassRegistryManager::class.java,
      fakeMavenClassRegistryManager,
      myFixture.testRootDisposable
    )

    val inspection = AndroidUnresolvableTagInspection()
    myFixture.enableInspections(inspection)

    loadProject(TestProjectPaths.ANDROIDX_SIMPLE) // project using AndroidX
    assertBuildGradle(project) { !it.contains("androidx.recyclerview:recyclerview:") } // not already using recyclerview

    myFixture.loadNewFile(
      "app/src/main/res/layout/my_layout.xml",
      """
    <?xml version="1.0" encoding="utf-8"?>
    <${
        "androidx.recyclerview.widget.RecyclerView"
          .highlightedAs(ERROR, "Cannot resolve class androidx.recyclerview.widget.RecyclerView")
      } />
    """.trimIndent()
    )

    myFixture.checkHighlighting(true, false, false)
    myFixture.moveCaret("Recycler|View")
    val action = myFixture.getIntentionAction("Add dependency on androidx.recyclerview:recyclerview")!!
    val undoManager = UndoManager.getInstance(myFixture.project)

    assertTrue(action.isAvailable(myFixture.project, myFixture.editor, myFixture.file))
    WriteCommandAction.runWriteCommandAction(myFixture.project, Runnable {
      action.invoke(myFixture.project, myFixture.editor, myFixture.file)
    })

    // Undo.
    waitForCondition(1, TimeUnit.SECONDS) {
      undoManager.isUndoAvailable(myFixture.fileEditor)
    }
    undoManager.undo(myFixture.fileEditor)

    // Wait for the sync
    requestSyncAndWait() // this is redundant but we can't get a handle on the internal sync state of the first action
    assertBuildGradle(project) { !it.contains("implementation 'androidx.recyclerview:recyclerview:") }

    // Redo.
    undoManager.redo(myFixture.fileEditor)
    waitForCondition(1, TimeUnit.SECONDS) {
      checkBuildGradle(project) { it.contains("implementation 'androidx.recyclerview:recyclerview:") }
    }
  }
}
