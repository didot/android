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
package com.android.tools.idea.adb.wireless

import com.android.flags.junit.RestoreFlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.ui.DialogWrapperFactory
import com.android.tools.idea.ui.FakeDialogWrapper
import com.android.tools.idea.ui.FakeDialogWrapperRule
import com.google.common.truth.Truth
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.testFramework.TestActionEvent
import org.junit.Rule
import org.junit.Test

class PairDevicesUsingWiFiActionTest : LightPlatform4TestCase() {
  /** Ensures feature flag is reset after test */
  @get:Rule
  val restoreFlagRule = RestoreFlagRule(StudioFlags.ADB_WIRELESS_PAIRING_ENABLED)

  /** Use [DialogWrapperFactory] that is compatible with unit tests */
  @get:Rule
  val testingDialogWrapperRule = FakeDialogWrapperRule()

  @Test
  fun actionShouldBeEnabledIfFlagIsSet() {
    // Prepare
    StudioFlags.ADB_WIRELESS_PAIRING_ENABLED.override(true)
    val action = PairDevicesUsingWiFiAction()
    val event = TestActionEvent(action)

    // Act
    action.update(event)

    // Assert
    Truth.assertThat(event.presentation.isEnabled).isTrue()
    Truth.assertThat(event.presentation.isVisible).isTrue()
  }

  @Test
  fun actionShouldBeDisabledIfFlagIsNotSet() {
    // Prepare
    StudioFlags.ADB_WIRELESS_PAIRING_ENABLED.override(false)
    val action = PairDevicesUsingWiFiAction()
    val event = TestActionEvent(action)

    // Act
    action.update(event)

    // Assert
    Truth.assertThat(event.presentation.isEnabled).isFalse()
    Truth.assertThat(event.presentation.isVisible).isFalse()
  }

  @Test
  fun dialogShouldShowWhenInvokingAction() {
    // Prepare
    StudioFlags.ADB_WIRELESS_PAIRING_ENABLED.override(true)
    val action = PairDevicesUsingWiFiAction()
    val event = TestActionEvent(action)

    // Act
    action.update(event)
    action.actionPerformed(event)

    // Assert
    Truth.assertThat(FakeDialogWrapper.ourLastInstance).isNotNull()
    Truth.assertThat(FakeDialogWrapper.ourLastInstance!!.title).isEqualTo("Pair devices for wireless debugging")
    Truth.assertThat(FakeDialogWrapper.ourLastInstance!!.okButtonText).isEqualTo("Close")
    Truth.assertThat(FakeDialogWrapper.ourLastInstance!!.initCalled).isTrue()
    Truth.assertThat(FakeDialogWrapper.ourLastInstance!!.showCalled).isTrue()
    Truth.assertThat(FakeDialogWrapper.ourLastInstance!!.panel).isNotNull()
  }
}