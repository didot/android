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

import com.android.builder.model.SyncIssue
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.IdeaTestCase
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class SyncIssueRegisterTest : IdeaTestCase() {
  private var register : SyncIssueRegister? = null

  @Before
  override fun setUp() {
    super.setUp()
    register = SyncIssueRegister(project)
  }

  @Test
  fun testRegisterSyncIssues() {
    val syncIssue = mock(SyncIssue::class.java)
    register!!.register(myModule, listOf(syncIssue))
    register!!.seal()
    val result = register!!.get()
    assertThat(result.entries).hasSize(1)
    assertThat(result[myModule]).containsExactly(syncIssue)
  }

  @Test
  fun testClear() {
    val syncIssue = mock(SyncIssue::class.java)
    register!!.register(myModule, listOf(syncIssue))

    register!!.unsealAndClear()
    register!!.seal()
    assertThat(register!!.get()).isEmpty()
  }
}
