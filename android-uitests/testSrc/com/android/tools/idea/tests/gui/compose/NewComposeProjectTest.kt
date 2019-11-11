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
package com.android.tools.idea.tests.gui.compose

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.npw.platform.Language
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.util.WizardUtils
import com.google.common.truth.Truth
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GuiTestRemoteRunner::class)
class NewComposeProjectTest {
  @Rule
  @JvmField
  val guiTest = GuiTestRule()

  @Before
  fun setup() {
    StudioFlags.COMPOSE_WIZARD_TEMPLATES.override(true)
  }

  @After
  fun cleanUp() {
    StudioFlags.COMPOSE_WIZARD_TEMPLATES.clearOverride()
  }

  /**
   * Verifies that user is able to create a new Compose Activity Project through the
   * new project wizard.
   * <p>TODO: TT ID:
   * Test steps:
   * 1. Create new default "Empty Compose Activity" Project
   * Verify:
   * 1. Check that app/build.gradle has dependencies for "androidx.ui:ui-framework" and "androidx.ui:ui-tooling"
   * 2. Check that the main activity has functions annotated with @Composable and @Preview
   */
  @Test
  fun newComposeProject() {
    WizardUtils.createNewProject(guiTest, "Empty Compose Activity", Language.KOTLIN)

    guiTest.getProjectFileText("app/build.gradle").run {
      Truth.assertThat(this).contains("implementation 'androidx.ui:ui-layout:")
      Truth.assertThat(this).contains("implementation 'androidx.ui:ui-material:")
      Truth.assertThat(this).contains("implementation 'androidx.ui:ui-tooling:")
    }
    guiTest.getProjectFileText("app/src/main/java/com/google/myapplication/MainActivity.kt").run {
      Truth.assertThat(this).contains("@Composable")
      Truth.assertThat(this).contains("@Preview")
    }
  }
}