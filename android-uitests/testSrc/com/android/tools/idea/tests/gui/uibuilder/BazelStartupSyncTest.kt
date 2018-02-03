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
package com.android.tools.idea.tests.gui.uibuilder

import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.MultiBuildGuiTestRunner
import com.android.tools.idea.tests.gui.framework.guitestprojectsystem.TargetBuildSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(MultiBuildGuiTestRunner::class)
class BazelStartupSyncTest {
  @Rule @JvmField val guiTest = GuiTestRule()

  @Test
  @TargetBuildSystem(TargetBuildSystem.BuildSystem.BAZEL)
  fun startupBazelSyncSucceeds() {
    guiTest.importSimpleLocalApplication()
  }

}