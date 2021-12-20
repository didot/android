/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.wearpairing

import com.android.ddmlib.IDevice
import com.android.ddmlib.IShellOutputReceiver
import com.android.sdklib.AndroidVersion
import com.android.sdklib.ISystemImage
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdManager
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.analytics.LoggedUsage
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.observable.BatchInvoker
import com.android.tools.idea.observable.TestInvokeStrategy
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.WearPairingEvent
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.ui.components.JBLabel
import org.jetbrains.android.util.AndroidBundle.message
import org.junit.Test
import org.mockito.Mockito
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import javax.swing.JButton

class EndToEndIntegrationTest : LightPlatform4TestCase() {
  private val invokeStrategy = TestInvokeStrategy()
  private val usageTracker = TestUsageTracker(VirtualTimeScheduler())

  override fun setUp() {
    super.setUp()

    BatchInvoker.setOverrideStrategy(invokeStrategy)
    UsageTracker.setWriterForTest(usageTracker)
    enableHeadlessDialogs(testRootDisposable)
  }

  override fun tearDown() {
    try {
      BatchInvoker.clearOverrideStrategy()
      usageTracker.close()
      UsageTracker.cleanAfterTesting()
    }
    finally {
      super.tearDown()
    }
  }

  @Test
  fun allInstalledQuickPathToSuccess() {
    val phoneIDevice = Mockito.mock(IDevice::class.java).apply {
      Mockito.`when`(arePropertiesSet()).thenReturn(true)
      Mockito.`when`(isOnline).thenReturn(true)
      Mockito.`when`(name).thenReturn("MyPhone")
      Mockito.`when`(serialNumber).thenReturn("serialNumber")
      Mockito.`when`(state).thenReturn(IDevice.DeviceState.ONLINE)
      Mockito.`when`(version).thenReturn(AndroidVersion(28, null))
      Mockito.`when`(getProperty("dev.bootcomplete")).thenReturn("1")

      addExecuteShellCommandReply { request ->
        when {
          request == "cat /proc/uptime" -> "500"
          request.contains("grep versionName") -> "versionName=1.0.0"
          request.contains("grep versionCode") -> "versionCode=${PairingFeature.MULTI_WATCH_SINGLE_PHONE_PAIRING.minVersion}"
          request.contains("grep 'cloud network id: '") -> "cloud network id: CloudID"
          else -> "Unknown executeShellCommand request $request"
        }
      }
    }

    val wearPropertiesMap = mapOf(AvdManager.AVD_INI_TAG_ID to "android-wear")
    val avdWearInfo = AvdInfo("My Wear", Paths.get("ini"), Paths.get("folder"), Mockito.mock(ISystemImage::class.java), wearPropertiesMap)

    val wearIDevice = Mockito.mock(IDevice::class.java).apply {
      Mockito.`when`(arePropertiesSet()).thenReturn(true)
      Mockito.`when`(isOnline).thenReturn(true)
      Mockito.`when`(isEmulator).thenReturn(true)
      Mockito.`when`(name).thenReturn(avdWearInfo.name)
      Mockito.`when`(serialNumber).thenReturn("serialNumber")
      Mockito.`when`(state).thenReturn(IDevice.DeviceState.ONLINE)
      Mockito.`when`(getProperty("dev.bootcomplete")).thenReturn("1")
      Mockito.`when`(getSystemProperty("ro.oem.companion_package")).thenReturn(Futures.immediateFuture(""))
      addExecuteShellCommandReply { request ->
        when {
          request == "cat /proc/uptime" -> "500"
          request == "am force-stop com.google.android.gms" -> "OK"
          request.contains("grep versionCode") -> "versionCode=${PairingFeature.REVERSE_PORT_FORWARD.minVersion}"
          request.contains("grep 'cloud network id: '") -> "cloud network id: CloudID"
          request.contains("settings get secure") -> "null"
          else -> "Unknown executeShellCommand request $request"
        }
      }
    }

    WearPairingManager.setDataProviders({ listOf(avdWearInfo) }, { listOf(phoneIDevice, wearIDevice) })
    assertThat(WearPairingManager.getPairedDevices(wearIDevice.name)).isNull()

    createModalDialogAndInteractWithIt({ WearDevicePairingWizard().show(null, null) }) {
      FakeUi(it.contentPane).apply {
        waitLabelText(message("wear.assistant.device.list.title"))
        clickButton("Next")
        waitLabelText(message("wear.assistant.device.connection.start.device.title"))
        waitLabelText(message("wear.assistant.device.connection.connecting.device.top.label"))
        waitLabelText(message("wear.assistant.device.connection.pairing.success.title"))
        clickButton("Finish")
      }
    }

    waitForCondition(5, TimeUnit.SECONDS) { getWearPairingTrackingEvents().size >= 2 }
    val usages = getWearPairingTrackingEvents()
    assertThat(usages[0].studioEvent.wearPairingEvent.kind).isEqualTo(WearPairingEvent.EventKind.SHOW_ASSISTANT_FULL_SELECTION)
    assertThat(usages[1].studioEvent.wearPairingEvent.kind).isEqualTo(WearPairingEvent.EventKind.SHOW_SUCCESSFUL_PAIRING)
    val phoneWearPair = WearPairingManager.getPairedDevices(wearIDevice.name)
    assertThat(phoneWearPair).isNotNull()
    assertThat(phoneWearPair!!.pairingStatus).isEqualTo(WearPairingManager.PairingState.CONNECTED)
    assertThat(phoneWearPair.getPeerDevice(wearIDevice.name).displayName).isEqualTo(phoneIDevice.name)
  }

  private fun FakeUi.clickButton(text: String) {
    waitForCondition(5, TimeUnit.SECONDS) {
      invokeStrategy.updateAllSteps()
      layoutAndDispatchEvents()
      findComponent<JButton> { text == it.text && it.isEnabled }?.apply { clickOn(this) } != null
    }
  }

  // The UI loads on asynchronous coroutine, we need to wait
  private fun FakeUi.waitLabelText(text: String) = waitForCondition(5, TimeUnit.SECONDS) {
    invokeStrategy.updateAllSteps()
    layoutAndDispatchEvents()
    findComponent<JBLabel> { it.text == text } != null
  }

  private fun getWearPairingTrackingEvents(): List<LoggedUsage> =
    usageTracker.usages.filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.WEAR_PAIRING}

  private fun IDevice.addExecuteShellCommandReply(requestHandler: (request: String) -> String) {
    Mockito.`when`(executeShellCommand(Mockito.anyString(), Mockito.any())).thenAnswer { invocation ->
      val request = invocation.arguments[0] as String
      val receiver = invocation.arguments[1] as IShellOutputReceiver
      val reply = requestHandler(request)

      val byteArray = "$reply\n".toByteArray(Charsets.UTF_8)
      receiver.addOutput(byteArray, 0, byteArray.size)
    }
  }
}