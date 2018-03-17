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
package com.android.tools.adtui

import com.android.tools.adtui.stdui.CommonToggleButton
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.awt.BorderLayout
import java.util.concurrent.CountDownLatch
import java.util.regex.Pattern
import javax.swing.JPanel

class FilterComponentTest {
  private lateinit var myPanel: JPanel
  private lateinit var myFilterComponent: FilterComponent
  private lateinit var myFilterButton: CommonToggleButton
  private lateinit var myPattern: Pattern
  private lateinit var myLatch: CountDownLatch
  @Before
  fun setUp() {
    myPanel = JPanel(BorderLayout())
    myFilterComponent = FilterComponent(245, 5, 0)
    myFilterButton = FilterComponent.createFilterToggleButton()
    myPanel.add(myFilterButton, BorderLayout.EAST)
    myPanel.add(myFilterComponent, BorderLayout.SOUTH)
    myFilterComponent.isVisible = false
    FilterComponent.configureKeyBindingAndFocusBehaviors(myPanel, myFilterComponent, myFilterButton)
    myPattern = Pattern.compile("")

    myFilterComponent.addOnFilterChange { p, _ ->
      run {
        myPattern = p
        myLatch.countDown()
      }
    }
  }

  @Test
  fun clicksFilterButton() {
    myFilterButton.doClick();
    assertThat(myFilterComponent.isVisible).isTrue()
    myFilterButton.doClick();
    assertThat(myFilterComponent.isVisible).isFalse()
  }

  @Test
  fun changeFilterContent() {
    myLatch = CountDownLatch(3)
    myFilterComponent.textEditor.text = "test[A-Z]ext";
    myFilterComponent.matchCaseCheckBox.isSelected = true
    myFilterComponent.regexCheckBox.isSelected = true
    myLatch.await()
    assertThat(myPattern.matcher("testText").matches()).isTrue()
    assertThat(myPattern.matcher("testAext").matches()).isTrue()
    assertThat(myPattern.matcher("testaext").matches()).isFalse()
    assertThat(myPattern.matcher("test.ext").matches()).isFalse()
  }
}