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
package com.android.tools.idea.layoutinspector

import com.android.tools.idea.layoutinspector.ui.SelectProcessAction
import com.android.tools.idea.stats.AnonymizerUtil
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DeviceInfo
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.ATTACH_REQUEST
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.ATTACH_SUCCESS
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.concurrent.TimeUnit

class MetricsTest {

  @get:Rule
  val inspectorRule = LayoutInspectorTransportRule().withDefaultDevice(connected = false)

  @Rule
  @JvmField
  val usageTrackerRule = MetricsTrackerRule()

  @Test
  fun testAttachSuccessViaSelectProcess() {
    val event = mock(AnActionEvent::class.java)
    val presentation = Presentation()
    `when`(event.presentation).thenReturn(presentation)

    SelectProcessAction.ConnectAction(DEFAULT_PROCESS, DEFAULT_STREAM,
                                      inspectorRule.inspectorClient).setSelected(event, true)

    inspectorRule.advanceTime(110, TimeUnit.MILLISECONDS)
    inspectorRule.waitForStart()

    val usages = usageTrackerRule.testTracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT }
    assertEquals(2, usages.size)
    var studioEvent = usages[0].studioEvent

    val deviceInfo = studioEvent.deviceInfo
    assertEquals(AnonymizerUtil.anonymizeUtf8("1234"), deviceInfo.anonymizedSerialNumber)
    assertEquals("My Model", deviceInfo.model)
    assertEquals("Google", deviceInfo.manufacturer)
    assertEquals(DeviceInfo.DeviceType.LOCAL_PHYSICAL, deviceInfo.deviceType)

    val inspectorEvent = studioEvent.dynamicLayoutInspectorEvent
    assertEquals(ATTACH_REQUEST, inspectorEvent.type)

    studioEvent = usages[1].studioEvent
    assertEquals(deviceInfo, studioEvent.deviceInfo)
    assertEquals(ATTACH_SUCCESS, studioEvent.dynamicLayoutInspectorEvent.type)
  }

  @Test
  fun testAttachFailViaSelectProcess() {
    inspectorRule.shouldConnectSuccessfully = false
    val event = mock(AnActionEvent::class.java)
    val presentation = Presentation()
    `when`(event.presentation).thenReturn(presentation)

    SelectProcessAction.ConnectAction(DEFAULT_PROCESS, DEFAULT_STREAM, inspectorRule.inspectorClient).setSelected(event, true)

    inspectorRule.advanceTime(1100, TimeUnit.MILLISECONDS)
    inspectorRule.waitForStart()

    val usages = usageTrackerRule.testTracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT }
    assertEquals(1, usages.size)
    val studioEvent = usages[0].studioEvent

    val deviceInfo = studioEvent.deviceInfo
    assertEquals(AnonymizerUtil.anonymizeUtf8("1234"), deviceInfo.anonymizedSerialNumber)
    assertEquals("My Model", deviceInfo.model)
    assertEquals("Google", deviceInfo.manufacturer)
    assertEquals(DeviceInfo.DeviceType.LOCAL_PHYSICAL, deviceInfo.deviceType)

    val inspectorEvent = studioEvent.dynamicLayoutInspectorEvent
    assertEquals(ATTACH_REQUEST, inspectorEvent.type)
  }
}

class MetricsTest2 {
  @get:Rule
  val inspectorRule = LayoutInspectorTransportRule()

  @Rule
  @JvmField
  val usageTrackerRule = MetricsTrackerRule()

  @Test
  fun testAttachOnLaunchWithDelay() {
    val preferredProcess = LayoutInspectorPreferredProcess(DEFAULT_DEVICE.manufacturer, DEFAULT_DEVICE.model, DEFAULT_DEVICE.serial,
                                                           DEFAULT_PROCESS.name, DEFAULT_DEVICE.featureLevel)
    inspectorRule.inspectorClient.attachIfSupported(preferredProcess)!!.get()
    inspectorRule.advanceTime(1100, TimeUnit.MILLISECONDS)

    var usages = usageTrackerRule.testTracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT }
    // The process hasn't started on the device yet, so we haven't logged anything yet.
    assertEquals(0, usages.size)

    inspectorRule.advanceTime(1100, TimeUnit.MILLISECONDS)
    usages = usageTrackerRule.testTracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT }
    // Still nothing
    assertEquals(0, usages.size)

    // Now start the process
    inspectorRule.addProcess(DEFAULT_DEVICE, DEFAULT_PROCESS)
    inspectorRule.advanceTime(1100, TimeUnit.MILLISECONDS)
    usages = usageTrackerRule.testTracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT }
    // We should have the attach request and success event now
    assertEquals(listOf(ATTACH_REQUEST, ATTACH_SUCCESS), usages.map { it.studioEvent.dynamicLayoutInspectorEvent.type })

    inspectorRule.advanceTime(1100, TimeUnit.MILLISECONDS)
    usages = usageTrackerRule.testTracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT }
    // And we shouldn't get any more than the two events
    assertEquals(2, usages.size)
  }
}
