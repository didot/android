/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.whenever
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock

class ProjectNotificationsUtilsTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @JvmField
  @Rule
  val projectRule = ProjectRule()

  @Test
  fun testInvalidGradleJdkLinks() {
    val links = generateInvalidGradleJdkLinks(projectRule.project)
    assertThat(links).hasSize(0)
  }

  @Test
  fun testNotifyOnInvalidGradleJdkJdkUseEmbedded() {
    val mockGradleManager = mock(GradleInstallationManager::class.java)
    whenever(mockGradleManager.getGradleJvmPath(any(), any())).thenReturn("/path/to/invalid/jdk/")

    val project = projectRule.project
    ApplicationManager.getApplication().replaceService(GradleInstallationManager::class.java, mockGradleManager, project)

    assertThat(notifyOnInvalidGradleJdk(projectRule.project)).isTrue()
  }
}