/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.actions

import com.android.flags.junit.SetFlagRule
import com.android.tools.idea.compose.preview.fast.FastPreviewManager
import com.android.tools.idea.editors.literals.FastPreviewApplicationConfiguration
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.testFramework.TestActionEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class ToggleFastPreviewActionTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val flagRule = SetFlagRule(StudioFlags.COMPOSE_FAST_PREVIEW, true)

  @After
  fun tearDown() {
    FastPreviewApplicationConfiguration.getInstance().resetDefault()
  }

  @Test
  fun `is action visible when Fast Preview depending on the flag values`() {
    try {
      listOf(false, true).forEach {
        StudioFlags.COMPOSE_FAST_PREVIEW.override(it)

        val action = ToggleFastPreviewAction()
        val event = TestActionEvent()
        action.update(event)

        assertEquals(it, event.presentation.isVisible)
      }
    }
    finally {
      StudioFlags.COMPOSE_FAST_PREVIEW.clearOverride()
    }
  }

  @Test
  fun `action toggles FastPreviewManager enabled state`() {
    val manager = FastPreviewManager.getInstance(projectRule.project)
    assertTrue(manager.isEnabled)
    assertTrue(manager.isAvailable)
    val action = ToggleFastPreviewAction()
    val event = TestActionEvent()

    action.setSelected(event, false)
    assertFalse(manager.isEnabled)
    assertFalse(manager.isAvailable)

    action.setSelected(event, true)
    assertTrue(manager.isEnabled)
    assertTrue(manager.isAvailable)
  }
}