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
package com.android.tools.idea.whatsnew.assistant

import com.android.repository.Revision
import com.android.testutils.TestUtils
import com.android.tools.idea.assistant.AssistSidePanel
import com.android.tools.idea.assistant.AssistantBundleCreator
import com.android.tools.idea.assistant.AssistantGetBundleTask
import com.android.tools.idea.assistant.DefaultTutorialBundle
import com.android.tools.idea.assistant.datamodel.TutorialBundleData
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.util.FutureUtils
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.project.Project
import junit.framework.TestCase
import org.apache.http.concurrent.FutureCallback
import org.jetbrains.android.AndroidTestCase
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.stubbing.Answer
import java.io.File
import java.io.InputStream
import java.net.URL
import java.util.concurrent.TimeUnit

class WhatsNewAssistantSidePanelTest : AndroidTestCase() {
  private val TIMEOUT_MILLISECONDS: Long = 30000
  private lateinit var mockUrlProvider: WhatsNewAssistantURLProvider

  private val studioRevision = Revision.parseRevision("3.3.0")

  override fun setUp() {
    super.setUp()
    StudioFlags.WHATS_NEW_ASSISTANT_ENABLED.override(true)
    StudioFlags.WHATS_NEW_ASSISTANT_DOWNLOAD_CONTENT.override(true)

    // Mock url provider to simulate webserver and also class resource file
    mockUrlProvider = mock(WhatsNewAssistantURLProvider::class.java)

    val serverFile = File(myFixture.testDataPath).resolve("whatsnewassistant/server-3.3.0.xml")
    Mockito.`when`(mockUrlProvider.getWebConfig(ArgumentMatchers.anyString())).thenReturn(URL("file:" + serverFile.path))

    val resourceFile = File(myFixture.testDataPath).resolve("whatsnewassistant/defaultresource-3.3.0.xml")
    Mockito.`when`(mockUrlProvider.getResourceFileAsStream(ArgumentMatchers.any(), ArgumentMatchers.anyString()))
      .thenAnswer(Answer<InputStream> {
        URL("file:" + resourceFile.path).openStream()
      })

    val tmpDir = TestUtils.createTempDirDeletedOnExit()
    val localPath = tmpDir.toPath().resolve("local-3.3.0.xml")
    Mockito.`when`(mockUrlProvider.getLocalConfig(ArgumentMatchers.anyString())).thenReturn(localPath)
  }

  override fun tearDown() {
    super.tearDown()
    StudioFlags.WHATS_NEW_ASSISTANT_ENABLED.clearOverride()
    StudioFlags.WHATS_NEW_ASSISTANT_DOWNLOAD_CONTENT.clearOverride()
  }

  /**
   * Test that the additional title for Assistant panel displays the same as bundle name
   */
  @Test
  fun testPanelTitle() {
    val bundleCreator: WhatsNewAssistantBundleCreator? = AssistantBundleCreator.EP_NAME
      .findExtension(WhatsNewAssistantBundleCreator::class.java)
    bundleCreator!!.setURLProvider(mockUrlProvider)
    bundleCreator.setStudioRevision(studioRevision)

    val completeFuture = SettableFuture.create<String>()
    val callback = object: FutureCallback<String> {
      override fun completed(result: String?) {
        completeFuture.set(result)
      }

      override fun cancelled() {
        completeFuture.set("")
      }

      override fun failed(ex: Exception?) {
        completeFuture.set("")
        ex?.printStackTrace()
      }
    }

    // Tab title will be set after assistant content finishes loading
    AssistSidePanel(WhatsNewAssistantBundleCreator.BUNDLE_ID, project, callback)
    FutureUtils.pumpEventsAndWaitForFuture(completeFuture, TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
    TestCase.assertEquals("Test What's New from Server", completeFuture.get())
  }

  /**
   * Test that the asynchronous loading for Assistant bundle works
   */
  @Test
  fun testAsyncLoadBundle() {
    val mockBundle = mock(DefaultTutorialBundle::class.java)
    val mockBundleCreator = mock(AssistantBundleCreator::class.java)
    `when`(mockBundleCreator.getBundle(Mockito.any(Project::class.java))).thenReturn(mockBundle)

    val completeFuture = SettableFuture.create<Boolean>()

    val callback = object: FutureCallback<TutorialBundleData> {
      override fun cancelled() {
        completeFuture.set(false)
      }

      override fun completed(result: TutorialBundleData?) {
        completeFuture.set(true) // Should complete
      }

      override fun failed(ex: Exception?) {
        completeFuture.set(false)
        ex?.printStackTrace()
      }
    }

    AssistantGetBundleTask(project, mockBundleCreator, callback).queue()
    FutureUtils.pumpEventsAndWaitForFuture(completeFuture, TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
    TestCase.assertTrue(completeFuture.get())
  }

  /**
   * Test asynchronous loading for Assistant bundle throwing an exception
   */
  @Test
  fun testAsyncLoadNullBundle() {
    val mockBundleCreator = mock(AssistantBundleCreator::class.java)

    val completeFuture = SettableFuture.create<Boolean>()

    val callback = object: FutureCallback<TutorialBundleData> {
      override fun cancelled() {
        completeFuture.set(false)
      }

      override fun completed(result: TutorialBundleData?) {
        completeFuture.set(false)
      }

      override fun failed(ex: Exception?) {
        completeFuture.set(true) // Should fail
      }
    }

    AssistantGetBundleTask(project, mockBundleCreator, callback).queue()
    FutureUtils.pumpEventsAndWaitForFuture(completeFuture, TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
    TestCase.assertTrue(completeFuture.get())
  }
}
