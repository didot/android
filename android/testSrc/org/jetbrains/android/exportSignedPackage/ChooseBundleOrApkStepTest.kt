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
package org.jetbrains.android.exportSignedPackage

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.testing.IdeComponents
import com.intellij.testFramework.IdeaTestCase
import org.mockito.Mockito

class ChooseBundleOrApkStepTest : IdeaTestCase() {
  private lateinit var ideComponents: IdeComponents

  override fun setUp() {
    super.setUp()
    ideComponents = IdeComponents(myProject)
  }

  fun testSufficientGradleVersion() {
    val wizard = Mockito.mock(ExportSignedPackageWizard::class.java)
    Mockito.`when`(wizard.project).thenReturn(myProject)

    val chooseStep = ChooseBundleOrApkStep(wizard, GradleVersion.parse("3.2.0"))
    assertTrue(chooseStep.myBundleButton.isEnabled)
    assertTrue(chooseStep.myBundleButton.isSelected)
    assertFalse(chooseStep.myGradleErrorLabel.isVisible)
  }

  fun testInsufficientGradleVersion() {
    val wizard = Mockito.mock(ExportSignedPackageWizard::class.java)
    Mockito.`when`(wizard.project).thenReturn(myProject)

    val chooseStep = ChooseBundleOrApkStep(wizard, GradleVersion.parse("3.1.0"))
    assertFalse(chooseStep.myBundleButton.isEnabled)
    assertTrue(chooseStep.myApksButton.isSelected)
    assertTrue(chooseStep.myGradleErrorLabel.isVisible)
  }
}
