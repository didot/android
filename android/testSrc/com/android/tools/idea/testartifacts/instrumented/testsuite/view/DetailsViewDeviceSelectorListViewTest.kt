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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view

import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt.eq
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.DetailsViewDeviceSelectorListView.DetailsViewDeviceSelectorListViewListener
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

/**
 * Unit tests for [DetailsViewDeviceSelectorListView].
 */
@RunWith(JUnit4::class)
@RunsInEdt
class DetailsViewDeviceSelectorListViewTest {
  @get:Rule val edtRule = EdtRule()

  @Mock lateinit var mockListener: DetailsViewDeviceSelectorListViewListener

  @Before
  fun setup() {
    MockitoAnnotations.initMocks(this)
  }

  @Test
  fun deviceListIsEmptyByDefault() {
    val view = DetailsViewDeviceSelectorListView(mockListener)
    assertThat(view.deviceListForTesting.itemsCount).isEqualTo(0)
  }

  @Test
  fun addDevice() {
    val view = DetailsViewDeviceSelectorListView(mockListener)
    val device = device(id = "device id", name = "device name")

    view.addDevice(device)

    assertThat(view.deviceListForTesting.itemsCount).isEqualTo(1)
    assertThat(view.deviceListForTesting.model.getElementAt(0)).isEqualTo(device)
  }

  @Test
  fun selectDevice() {
    val view = DetailsViewDeviceSelectorListView(mockListener)
    val device1 = device(id = "device id 1", name = "device name 1")
    val device2 = device(id = "device id 2", name = "device name 2")

    view.addDevice(device1)
    view.addDevice(device2)

    assertThat(view.deviceListForTesting.itemsCount).isEqualTo(2)
    assertThat(view.deviceListForTesting.selectedIndices).isEmpty()  // Nothing is selected initially.

    view.deviceListForTesting.selectedIndex = 0

    verify(mockListener).onDeviceSelected(eq(device1))
  }

  private fun device(id: String, name: String): AndroidDevice {
    return AndroidDevice(id, name, AndroidDeviceType.LOCAL_EMULATOR, AndroidVersion(28))
  }
}