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
package com.android.tools.adtui.model.formatter

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UserCounterAxisFormatterTest {

  @Test
  fun testUserCounterisNone() {
    assertThat(UserCounterAxisFormatter.DEFAULT.getFormattedString(0.0, 0.0, false)).isEqualTo("None")
  }


  @Test
  fun testUserCounterIsLight() {
    assertThat(UserCounterAxisFormatter.DEFAULT.getFormattedString(0.0, 1.0, false)).isEqualTo("Light")
    assertThat(UserCounterAxisFormatter.DEFAULT.getFormattedString(0.0, 3.0, false)).isEqualTo("Light")
  }

  @Test
  fun testUserCounterisMedium() {
    assertThat(UserCounterAxisFormatter.DEFAULT.getFormattedString(0.0, 4.0, false)).isEqualTo("Medium")
    assertThat(UserCounterAxisFormatter.DEFAULT.getFormattedString(0.0, 6.0, false)).isEqualTo("Medium")
  }

  @Test
  fun testUserCounterIsHeavy() {
    assertThat(UserCounterAxisFormatter.DEFAULT.getFormattedString(0.0, 7.0, false)).isEqualTo("Heavy")
    assertThat(UserCounterAxisFormatter.DEFAULT.getFormattedString(0.0, 12.0, false)).isEqualTo("Heavy")
    assertThat(UserCounterAxisFormatter.DEFAULT.getFormattedString(0.0, 50.0, false)).isEqualTo("Heavy")
  }
}