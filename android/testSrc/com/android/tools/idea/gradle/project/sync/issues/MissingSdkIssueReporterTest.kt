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
package com.android.tools.idea.gradle.project.sync.issues

import com.android.SdkConstants
import com.android.builder.model.SyncIssue
import com.android.tools.idea.gradle.project.sync.hyperlink.SetSdkDirHyperlink
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.COMPOSITE_BUILD
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File

class MissingSdkIssueReporterTest : AndroidGradleTestCase() {
  private lateinit var syncMessages: GradleSyncMessagesStub
  private lateinit var reporter: MissingSdkIssueReporter

  @Before
  override fun setUp() {
    super.setUp()

    syncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(project)
    reporter = MissingSdkIssueReporter()
  }

  @Test
  fun testWithSingleModule() {
    syncMessages.clearReportedMessages()
    loadSimpleApplication()

    val localPropertiesPath = File(projectFolderPath, SdkConstants.FN_LOCAL_PROPERTIES)
    val syncIssue = setUpMockSyncIssue(localPropertiesPath.absolutePath)

    reporter.report(syncIssue, getModule("app"), null)
    val notifications = syncMessages.notifications
    assertSize(1, notifications)
    val notification = notifications[0]

    assertEquals("Gradle Sync Issues", notification.title)
    assertEquals(
      "SDK location not found. Define a location by setting the ANDROID_SDK_ROOT environment variable or by setting the sdk.dir path in " +
      "your project's local.properties file.\n" +
      "Affected Modules: app", notification.message)
    assertEquals(NotificationCategory.ERROR, notification.notificationCategory)

    val notificationUpdate = syncMessages.notificationUpdate
    val quickFixes = notificationUpdate!!.fixes
    assertSize(1, quickFixes)
    assertInstanceOf(quickFixes[0], SetSdkDirHyperlink::class.java)
    val quickFixPaths = (quickFixes[0] as SetSdkDirHyperlink).localPropertiesPaths
    assertSize(1, quickFixPaths)
    assertContainsElements(quickFixPaths, localPropertiesPath.absolutePath)
  }

  @Test
  fun testWithCompositeBuild() {
    syncMessages.clearReportedMessages()
    prepareMultipleProjectsForImport(COMPOSITE_BUILD, "TestCompositeApp", "TestCompositeLib1", "TestCompositeLib3", "TestCompositeLib2",
                                     "TestCompositeLib4")
    importProject(project.name, File(COMPOSITE_BUILD), null)

    val localPropertiesPath = File(projectFolderPath, SdkConstants.FN_LOCAL_PROPERTIES)
    val localPropertiesPathTwo = File(projectFolderPath, "TestCompositeLib1/${SdkConstants.FN_LOCAL_PROPERTIES}")
    val localPropertiesPathThree = File(projectFolderPath, "TestCompositeLib3/${SdkConstants.FN_LOCAL_PROPERTIES}")

    val syncIssueOne = setUpMockSyncIssue(localPropertiesPath.absolutePath)
    val syncIssueTwo = setUpMockSyncIssue(localPropertiesPathTwo.absolutePath)
    val syncIssueThree = setUpMockSyncIssue(localPropertiesPathThree.absolutePath)


    val moduleMap = mapOf(
      syncIssueOne to getModule("testWithCompositeBuild"),
      syncIssueTwo to getModule("TestCompositeLib1"),
      syncIssueThree to getModule("TestCompositeLib3")
    )

    reporter.reportAll(listOf(syncIssueOne, syncIssueTwo, syncIssueThree), moduleMap, mapOf())

    val notifications = syncMessages.notifications
    assertSize(1, notifications)
    val notification = notifications[0]

    assertEquals("Gradle Sync Issues", notification.title)
    assertEquals(
      "SDK location not found. Define a location by setting the ANDROID_SDK_ROOT environment variable or by setting the sdk.dir path in " +
      "your project's local.properties files.\n" +
      "Affected Modules: TestCompositeLib1, TestCompositeLib3, testWithCompositeBuild", notification.message)
    assertEquals(NotificationCategory.ERROR, notification.notificationCategory)

    val notificationUpdate = syncMessages.notificationUpdate
    val quickFixes = notificationUpdate!!.fixes
    assertSize(1, quickFixes)
    assertInstanceOf(quickFixes[0], SetSdkDirHyperlink::class.java)
    val quickFixPaths = (quickFixes[0] as SetSdkDirHyperlink).localPropertiesPaths
    assertSize(3, quickFixPaths)
    assertContainsElements(quickFixPaths, localPropertiesPath.absolutePath, localPropertiesPathTwo.absolutePath,
                           localPropertiesPathThree.absolutePath)
  }

  private fun setUpMockSyncIssue(path: String): SyncIssue {
    val syncIssue = mock(SyncIssue::class.java)
    `when`(syncIssue.data).thenReturn(path)
    `when`(syncIssue.message).thenReturn("This is some message that is not used")
    `when`(syncIssue.severity).thenReturn(SyncIssue.SEVERITY_ERROR)
    `when`(syncIssue.type).thenReturn(SyncIssue.TYPE_SDK_NOT_SET)
    return syncIssue
  }
}