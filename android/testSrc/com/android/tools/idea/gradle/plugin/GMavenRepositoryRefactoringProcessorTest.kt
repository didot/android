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
package com.android.tools.idea.gradle.plugin

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.GMavenRepositoryRefactoringProcessor
import com.intellij.testFramework.RunsInEdt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@RunsInEdt
class GMavenRepositoryRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
  @Test
  fun testIsEnabledForOldAGP() {
    val processor = GMavenRepositoryRefactoringProcessor(project, GradleVersion.parse("2.2.0"), GradleVersion.parse("4.0.0"), GradleVersion.parse("6.5"))
    assertTrue(processor.isEnabled)
  }

  @Test
  fun testIsDisabledForNewerAGP() {
    val processor = GMavenRepositoryRefactoringProcessor(project, GradleVersion.parse("3.2.0"), GradleVersion.parse("4.0.0"), GradleVersion.parse("6.5"))
    assertFalse(processor.isEnabled)
  }

  @Test
  fun testAGP2Project() {
    writeToBuildFile(TestFileName("GMavenRepository/AGP2Project"))
    val processor = GMavenRepositoryRefactoringProcessor(project, GradleVersion.parse("2.3.2"), GradleVersion.parse("4.2.0"), GradleVersion.parse("6.5"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("GMavenRepository/AGP2ProjectExpected"))
  }

  @Test
  fun testAGP2ProjectWithGMaven() {
    writeToBuildFile(TestFileName("GMavenRepository/AGP2ProjectWithGMaven"))
    val processor = GMavenRepositoryRefactoringProcessor(project, GradleVersion.parse("2.3.2"), GradleVersion.parse("4.2.0"), GradleVersion.parse("6.5"))
    processor.run()
    // if we already have a gmaven declaration, it won't be added again.
    verifyFileContents(buildFile, TestFileName("GMavenRepository/AGP2ProjectWithGMaven"))
  }

  @Test
  fun testAGP3Project() {
    writeToBuildFile(TestFileName("GMavenRepository/AGP3Project"))
    val processor = GMavenRepositoryRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.2.0"), GradleVersion.parse("6.5"))
    processor.run()
    verifyFileContents(buildFile, TestFileName("GMavenRepository/AGP3Project"))
  }

  @Test
  fun testAGP3ProjectOverrideIsEnabled() {
    writeToBuildFile(TestFileName("GMavenRepository/AGP3Project"))
    val processor = GMavenRepositoryRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.2.0"), GradleVersion.parse("6.5"))
    assertFalse(processor.isEnabled)
    processor.isEnabled = true
    processor.run()
    verifyFileContents(buildFile, TestFileName("GMavenRepository/AGP3ProjectOverrideIsEnabledExpected"))
  }
}
