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
package com.android.tools.property.panel.impl.table

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_TEXT
import com.android.tools.property.panel.impl.model.util.FakePropertyItem
import com.android.tools.property.panel.impl.ui.PropertyTooltip
import com.android.tools.property.ptable2.PTable
import com.android.tools.property.ptable2.item.PTableTestModel
import com.android.tools.property.testing.ApplicationRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.IdeTooltipManager
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JTable

class DefaultNameTableCellRendererTest {

  @JvmField @Rule
  val appRule = ApplicationRule()

  var manager: IdeTooltipManager? = null

  @Before
  fun setUp() {
    manager = mock(IdeTooltipManager::class.java)
    appRule.testApplication.registerService(IdeTooltipManager::class.java, manager!!)
  }

  @After
  fun tearDown() {
    manager = null
  }

  @Test
  fun testGetToolTipText() {
    val renderer = DefaultNameTableCellRenderer()
    val table = createTable()
    val event = MouseEvent(table, 0, 0L, 0, 10, 10, 1, false)
    renderer.getToolTipText(event)

    val captor = ArgumentCaptor.forClass(PropertyTooltip::class.java)
    verify(manager!!).setCustomTooltip(any(JComponent::class.java), captor.capture())
    val tip = captor.value.tip
    assertThat(tip.text).isEqualTo("<html>Help on id</html>")
  }

  private fun createTable(): JTable {
    val property1 = FakePropertyItem(ANDROID_URI, ATTR_ID, "@id/text1")
    val property2 = FakePropertyItem(ANDROID_URI, ATTR_TEXT, "Hello")
    val model = PTableTestModel(property1, property2)
    property1.tooltipForName = "Help on id"
    property1.tooltipForValue = "Help on id value"
    property2.tooltipForName = "Help on text"
    property2.tooltipForValue = "Help on text value"
    return PTable.create(model) as JTable
  }
}
