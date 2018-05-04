/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.connection.assistant.actions

import com.android.tools.idea.assistant.datamodel.ActionData
import com.android.tools.idea.assistant.datamodel.DefaultActionState
import com.android.tools.usb.Platform
import com.android.tools.usb.UsbDevice
import com.android.tools.usb.UsbDeviceCollector
import junit.framework.TestCase
import org.jetbrains.android.AndroidTestCase
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.IOException
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.collections.ArrayList

class ListUsbDevicesActionStateManagerTest : AndroidTestCase() {
  private lateinit var testUsbDeviceCollector: UsbDeviceCollector
  private lateinit var emptyActionData: ActionData
  private lateinit var myStateManager: ListUsbDevicesActionStateManager

  override fun setUp() {
    super.setUp()
    emptyActionData = mock(ActionData::class.java)
    myStateManager = ListUsbDevicesActionStateManager()
    testUsbDeviceCollector = mock(UsbDeviceCollector::class.java)
    `when`(testUsbDeviceCollector.listUsbDevices()).thenReturn(CompletableFuture.completedFuture(ArrayList()))
    myStateManager.init(project, emptyActionData, testUsbDeviceCollector)
  }

  @Test
  fun testDefaultState() {
    `when`(testUsbDeviceCollector.listUsbDevices()).thenReturn(CompletableFuture.completedFuture(ArrayList()))
    myStateManager.refresh()
    TestCase.assertEquals(myStateManager.getState(project, emptyActionData), DefaultActionState.ERROR_RETRY)
  }

  @Test
  fun testLoadingState() {
    `when`(testUsbDeviceCollector.listUsbDevices()).thenReturn(CompletableFuture())
    myStateManager.refresh()
    TestCase.assertEquals(myStateManager.getState(project, emptyActionData), DefaultActionState.IN_PROGRESS)
  }

  @Test
  fun testSingleDevice() {
    val devices = ArrayList<UsbDevice>()
    devices.add(UsbDevice("test", "test", "test"))
    `when`(testUsbDeviceCollector.listUsbDevices()).thenReturn(CompletableFuture.completedFuture(devices))
    myStateManager.refresh()
    TestCase.assertEquals(myStateManager.getState(project, emptyActionData), CustomSuccessState)
  }

  @Test
  fun testWindowNoButton() {
    `when`(testUsbDeviceCollector.getPlatform()).thenReturn(Platform.Windows)
    `when`(testUsbDeviceCollector.listUsbDevices()).thenReturn(CompletableFuture.completedFuture(Collections.emptyList()))
    myStateManager.refresh()
    TestCase.assertEquals(myStateManager.getState(project, emptyActionData), DefaultActionState.ERROR)
  }

  @Test
  fun testException() {
    val exceptionFuture = CompletableFuture<List<UsbDevice>>()
    exceptionFuture.completeExceptionally(IOException())
    `when`(testUsbDeviceCollector.listUsbDevices()).thenReturn(exceptionFuture)
    myStateManager.refresh()
    TestCase.assertEquals(myStateManager.getState(project, emptyActionData), DefaultActionState.ERROR_RETRY)
  }
}
