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
package com.android.tools.idea.ui.resourcechooser.colorpicker2

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.ceil

class HueSliderComponentTest {

  @Test
  fun testChangeSliderValue() {
    val slider = HueSliderComponent()
    slider.setSize(1000, 100)

    slider.knobPosition = 0
    assertEquals(0f, slider.value)

    slider.knobPosition = slider.sliderWidth / 2
    assertEquals(0.5f, slider.value, 0.01f)

    slider.knobPosition = slider.sliderWidth
    assertEquals(1.0f, slider.value)
  }

  @Test
  fun testChangeValue() {
    val slider = HueSliderComponent()
    slider.setSize(1000, 100)

    slider.value = 0f
    assertEquals(0, slider.knobPosition)

    // The mapping between knobPosition and value may have some bias due to the floating issue. Bias is acceptable.
    slider.value = 0.5f
    assertEquals(slider.sliderWidth / 2, slider.knobPosition, ceil(slider.sliderWidth / 2f))

    slider.value = 1.0f
    assertEquals(slider.sliderWidth, slider.knobPosition)
  }

  private fun assertEquals(expect: Int, actual: Int, delta: Number) = assertEquals(expect.toDouble(), actual.toDouble(), delta.toDouble())
}
