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
package com.android.tools.idea.tests.gui.naveditor

import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.designer.naveditor.DestinationListFixture
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import com.intellij.util.ui.UIUtil
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(GuiTestRemoteRunner::class)
class DestinationListTest {
  @Rule @JvmField val guiTest = GuiTestRule()

  /**
   * Make sure the DestinationList is updated correctly when the nav file is updated outside studio.
   */
  @Test
  fun testExternalUpdate() {
    val frame = guiTest.importProject("Navigation")
    // Open file as XML and switch to design tab, wait for successful render
    val editor = guiTest.ideFrame().editor
    editor.open("app/src/main/res/navigation/mobile_navigation.xml", EditorFixture.Tab.DESIGN)
    val layout = editor.getLayoutEditor(true)

    // This is separate to catch the case where we have a problem opening the file before sync is complete.
    frame.waitForGradleProjectSyncToFinish()

    layout.waitForRenderToFinish()

    ApplicationManager.getApplication().invokeAndWait {
      UIUtil.dispatchAllInvocationEvents()
      ApplicationManager.getApplication().runWriteAction {
        editor.currentFile?.setBinaryContent("""
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:app="http://schemas.android.com/apk/res-auto"
              xmlns:tools="http://schemas.android.com/tools"
              app:startDestination="@+id/main_activity">
            <fragment android:id="@+id/new_fragment"
                    android:name="google.navigation.NextFragment"
                    tools:layout="@layout/fragment_next">
            </fragment>
        </navigation>
        """.trimIndent().toByteArray())
        val project = layout.surface.target().project
        PsiDocumentManager.getInstance(project).commitAllDocuments()
      }
      UIUtil.dispatchAllInvocationEvents()
    }
    val destinationListFixture = DestinationListFixture.create(guiTest.robot())
    assertEquals(listOf("new_fragment"), destinationListFixture.components.map { it.id })
  }
}