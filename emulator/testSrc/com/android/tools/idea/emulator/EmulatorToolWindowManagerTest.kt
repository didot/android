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
package com.android.tools.idea.emulator

import com.android.ddmlib.IDevice
import com.android.emulator.control.KeyboardEvent
import com.android.emulator.control.PaneEntry
import com.android.emulator.control.PaneEntry.PaneIndex
import com.android.sdklib.internal.avd.AvdInfo
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.SetPortableUiFontRule
import com.android.tools.idea.avdmanager.AvdLaunchListener
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.device.FakeScreenSharingAgentRule
import com.android.tools.idea.protobuf.TextFormat
import com.android.tools.idea.run.DeviceHeadsUpListener
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import com.intellij.ui.content.ContentManager
import com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Dimension
import java.awt.Point
import java.util.concurrent.TimeUnit
import javax.swing.JViewport
import javax.swing.UIManager

/**
 * Tests for [EmulatorToolWindowManager] and [EmulatorToolWindowFactory].
 */
@RunsInEdt
class EmulatorToolWindowManagerTest {
  private val agentRule = FakeScreenSharingAgentRule()
  private val emulatorRule = FakeEmulatorRule()
  @get:Rule
  val ruleChain = RuleChain(agentRule, emulatorRule, SetPortableUiFontRule(), EdtRule())

  private val windowFactory: EmulatorToolWindowFactory by lazy { EmulatorToolWindowFactory() }
  private val toolWindow: ToolWindow by lazy { createToolWindow() }
  private val contentManager: ContentManager by lazy { toolWindow.contentManager }

  private var savedMirroringEnabledState = false

  private val project get() = agentRule.project
  private val testRootDisposable get() = agentRule.testRootDisposable

  @Before
  fun setUp() {
    val mockLafManager = mock<LafManager>()
    whenever(mockLafManager.currentLookAndFeel).thenReturn(UIManager.LookAndFeelInfo("IntelliJ Light", "Ignored className"))
    ApplicationManager.getApplication().replaceService(LafManager::class.java, mockLafManager, testRootDisposable)

    savedMirroringEnabledState = DeviceMirroringSettings.getInstance().deviceMirroringEnabled
    DeviceMirroringSettings.getInstance().deviceMirroringEnabled = true
  }

  @After
  fun tearDown() {
    toolWindow.hide()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue() // Finish asynchronous processing triggered by hiding the tool window.
    DeviceMirroringSettings.getInstance().deviceMirroringEnabled = savedMirroringEnabledState
  }

  @Test
  fun testTabManagement() {
    assertThat(windowFactory.shouldBeAvailable(project)).isTrue()
    windowFactory.createToolWindowContent(project, toolWindow)
    assertThat(contentManager.contents).isEmpty()

    val tempFolder = emulatorRule.root
    val emulator1 = emulatorRule.newEmulator(FakeEmulator.createPhoneAvd(tempFolder), standalone = false)
    val emulator2 = emulatorRule.newEmulator(FakeEmulator.createTabletAvd(tempFolder), standalone = true)
    val emulator3 = emulatorRule.newEmulator(FakeEmulator.createWatchAvd(tempFolder), standalone = false)

    // The Emulator tool window is closed.
    assertThat(toolWindow.isVisible).isFalse()

    // Start the first and the second emulators.
    emulator1.start()
    emulator2.start()

    // Send notification that the emulator has been launched.
    val avdInfo = AvdInfo(emulator1.avdId, emulator1.avdFolder.resolve("config.ini"), emulator1.avdFolder, mock(), null)
    val commandLine = GeneralCommandLine("/emulator_home/fake_emulator", "-avd", emulator1.avdId, "-qt-hide-window")
    project.messageBus.syncPublisher(AvdLaunchListener.TOPIC).avdLaunched(avdInfo, commandLine, project)
    dispatchAllInvocationEvents()

    // The Emulator tool window becomes visible when a headless emulator is launched from Studio.
    assertThat(toolWindow.isVisible).isTrue()
    waitForCondition(2, TimeUnit.SECONDS) { contentManager.contents.isNotEmpty() }
    assertThat(contentManager.contents).hasLength(1)
    waitForCondition(2, TimeUnit.SECONDS) { RunningEmulatorCatalog.getInstance().emulators.isNotEmpty() }
    emulator1.getNextGrpcCall(2, TimeUnit.SECONDS) { true } // Wait for the initial "getVmState" call.
    waitForCondition(2, TimeUnit.SECONDS) { contentManager.contents[0].displayName != "No Running Emulators" }
    assertThat(contentManager.contents[0].displayName).isEqualTo(emulator1.avdName)

    // Start the third emulator.
    emulator3.start()

    waitForCondition(3, TimeUnit.SECONDS) { contentManager.contents.size == 2 }

    // The second emulator panel is added but the first one is still selected.
    assertThat(contentManager.contents[0].displayName).isEqualTo(emulator3.avdName)
    assertThat(contentManager.contents[1].displayName).isEqualTo(emulator1.avdName)
    assertThat(contentManager.contents[1].isSelected).isTrue()

    for (emulator in listOf(emulator2, emulator3)) {
      val device = mock<IDevice>()
      whenever(device.isEmulator).thenReturn(true)
      whenever(device.serialNumber).thenReturn("emulator-${emulator.serialPort}")
      project.messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).deviceNeedsAttention(device, project)
    }

    // Deploying an app activates the corresponding emulator panel.
    waitForCondition(2, TimeUnit.SECONDS) { contentManager.contents[0].isSelected }

    assertThat(contentManager.contents).hasLength(2)

    // Stop the second emulator.
    emulator3.stop()

    // The panel corresponding the the second emulator goes away.
    waitForCondition(2, TimeUnit.SECONDS) { contentManager.contents.size == 1 }
    assertThat(contentManager.contents[0].displayName).isEqualTo(emulator1.avdName)
    assertThat(contentManager.contents[0].isSelected).isTrue()

    // Close the panel corresponding to emulator1.
    contentManager.removeContent(contentManager.contents[0], true)
    val call = emulator1.getNextGrpcCall(2, TimeUnit.SECONDS,
                                         FakeEmulator.defaultCallFilter.or("android.emulation.control.UiController/closeExtendedControls"))
    assertThat(call.methodName).isEqualTo("android.emulation.control.EmulatorController/setVmState")
    assertThat(TextFormat.shortDebugString(call.request)).isEqualTo("state: SHUTDOWN")

    // The panel corresponding the first emulator goes away and is replaced by the empty state panel.
    assertThat(contentManager.contents.size).isEqualTo(1)
    assertThat(contentManager.contents[0].component).isInstanceOf(EmptyStatePanel::class.java)
    assertThat(contentManager.contents[0].displayName).isNull()
  }

  @Test
  fun testEmulatorCrash() {
    assertThat(windowFactory.shouldBeAvailable(project)).isTrue()
    windowFactory.createToolWindowContent(project, toolWindow)
    assertThat(contentManager.contents).isEmpty()

    val tempFolder = emulatorRule.root
    val emulator = emulatorRule.newEmulator(FakeEmulator.createPhoneAvd(tempFolder))

    toolWindow.show()

    // Start the emulator.
    emulator.start()

    val controllers = RunningEmulatorCatalog.getInstance().updateNow().get()
    waitForCondition(3, TimeUnit.SECONDS) { contentManager.contents.isNotEmpty() }
    assertThat(contentManager.contents[0].displayName).isEqualTo(emulator.avdName)
    assertThat(controllers).isNotEmpty()
    waitForCondition(5, TimeUnit.SECONDS) { controllers.first().connectionState == EmulatorController.ConnectionState.CONNECTED }

    // Simulate an emulator crash.
    emulator.crash()
    controllers.first().sendKey(KeyboardEvent.newBuilder().setText(" ").build())
    waitForCondition(5, TimeUnit.SECONDS) { contentManager.contents[0].displayName == null }
    assertThat(contentManager.contents[0].component).isInstanceOf(EmptyStatePanel::class.java)
  }

  @Test
  fun testUiStatePreservation() {
    assertThat(windowFactory.shouldBeAvailable(project)).isTrue()
    windowFactory.createToolWindowContent(project, toolWindow)
    assertThat(contentManager.contents).isEmpty()

    val tempFolder = emulatorRule.root
    val emulator = emulatorRule.newEmulator(FakeEmulator.createPhoneAvd(tempFolder))

    toolWindow.show()

    // Start the emulator.
    emulator.start()

    waitForCondition(2, TimeUnit.SECONDS) { contentManager.contents.isNotEmpty() }
    assertThat(contentManager.contents).hasLength(1)
    waitForCondition(2, TimeUnit.SECONDS) { RunningEmulatorCatalog.getInstance().emulators.isNotEmpty() }
    val emulatorController = RunningEmulatorCatalog.getInstance().emulators.first()
    waitForCondition(4, TimeUnit.SECONDS) { emulatorController.connectionState == EmulatorController.ConnectionState.CONNECTED }
    waitForCondition(2, TimeUnit.SECONDS) { contentManager.contents[0].displayName != "No Running Emulators" }
    assertThat(contentManager.contents[0].displayName).isEqualTo(emulator.avdName)

    assertThat(emulator.extendedControlsVisible).isFalse()
    emulatorController.showExtendedControls(PaneEntry.newBuilder().setIndex(PaneIndex.KEEP_CURRENT).build())
    // Wait for the extended controls to show.
    waitForCondition(2, TimeUnit.SECONDS) { emulator.extendedControlsVisible }

    val panel = contentManager.contents[0].component as EmulatorToolWindowPanel

    toolWindow.hide()

    // Wait for the extended controls to close.
    waitForCondition(4, TimeUnit.SECONDS) { !emulator.extendedControlsVisible }
    // Wait for the prior visibility state of the extended controls to propagate to Studio.
    waitForCondition(2, TimeUnit.SECONDS) { panel.lastUiState?.extendedControlsShown ?: false }

    toolWindow.show()

    // Wait for the extended controls to show.
    waitForCondition(2, TimeUnit.SECONDS) { emulator.extendedControlsVisible }
  }

  @Test
  fun testZoomStatePreservation() {
    assertThat(windowFactory.shouldBeAvailable(project)).isTrue()
    windowFactory.createToolWindowContent(project, toolWindow)
    assertThat(contentManager.contents).isEmpty()

    val tempFolder = emulatorRule.root
    val emulator = emulatorRule.newEmulator(FakeEmulator.createPhoneAvd(tempFolder))

    toolWindow.show()

    // Start the emulator.
    emulator.start()

    waitForCondition(2, TimeUnit.SECONDS) { contentManager.contents.isNotEmpty() }
    assertThat(contentManager.contents).hasLength(1)
    waitForCondition(2, TimeUnit.SECONDS) { RunningEmulatorCatalog.getInstance().emulators.isNotEmpty() }
    val emulatorController = RunningEmulatorCatalog.getInstance().emulators.first()
    waitForCondition(4, TimeUnit.SECONDS) { emulatorController.connectionState == EmulatorController.ConnectionState.CONNECTED }
    waitForCondition(2, TimeUnit.SECONDS) { contentManager.contents[0].displayName != "No Running Emulators" }
    assertThat(contentManager.contents[0].displayName).isEqualTo(emulator.avdName)

    val panel = contentManager.contents[0].component as EmulatorToolWindowPanel
    panel.setSize(250, 500)
    val ui = FakeUi(panel)
    val emulatorView = ui.getComponent<EmulatorView>()
    waitForCondition(2, TimeUnit.SECONDS) { emulatorView.frameNumber > 0 }

    // Zoom in.
    emulatorView.zoom(ZoomType.IN)
    ui.layoutAndDispatchEvents()
    assertThat(emulatorView.scale).isWithin(0.0001).of(0.25)
    var viewport = emulatorView.parent as JViewport
    val viewportSize = viewport.viewSize
    assertThat(viewportSize).isEqualTo(Dimension(396, 811))
    // Scroll to the bottom.
    val scrollPosition = Point(viewport.viewPosition.x, viewport.viewSize.height - viewport.height)
    viewport.viewPosition = scrollPosition

    toolWindow.hide()
    toolWindow.show()

    ui.layoutAndDispatchEvents()
    assertThat(emulatorView.scale).isWithin(0.0001).of(0.25)
    viewport = emulatorView.parent as JViewport
    assertThat(viewport.viewSize).isEqualTo(viewportSize)
    assertThat(viewport.viewPosition).isEqualTo(scrollPosition)
  }

  @Test
  fun testPhysicalDevice() {
    if (SystemInfo.isWindows) {
      return // For some unclear reason the test fails on Windows with java.lang.UnsatisfiedLinkError: no jniavcodec in java.library.path.
    }
    if (SystemInfo.isMac && !SystemInfo.isOsVersionAtLeast("10.15")) {
      return // FFmpeg library requires Mac OS 10.15+.
    }
    assertThat(windowFactory.shouldBeAvailable(project)).isTrue()
    windowFactory.createToolWindowContent(project, toolWindow)
    assertThat(contentManager.contents).isEmpty()
    assertThat(toolWindow.isVisible).isFalse()

    val device = agentRule.connectDevice("Pixel 4", 30, Dimension(1080, 2280), "arm64-v8a")
    toolWindow.show()

    waitForCondition(10, TimeUnit.SECONDS) { device.agent.running }
    assertThat(toolWindow.isVisible).isTrue()
    assertThat(contentManager.contents.size).isEqualTo(1)
    assertThat(contentManager.contents[0].displayName).isEqualTo("Google Pixel 4")

    agentRule.disconnectDevice(device)
    waitForCondition(2, TimeUnit.SECONDS) { !device.agent.running }
  }

  private val FakeEmulator.avdName
    get() = avdId.replace('_', ' ')

  private fun createToolWindow(): ToolWindow {
    val windowManager = TestToolWindowManager(project)
    project.replaceService(ToolWindowManager::class.java, windowManager, testRootDisposable)
    return windowManager.toolWindow
  }

  private class TestToolWindowManager(project: Project) : ToolWindowHeadlessManagerImpl(project) {
    var toolWindow = TestToolWindow(project, this)

    override fun getToolWindow(id: String?): ToolWindow? {
      return if (id == EMULATOR_TOOL_WINDOW_ID) toolWindow else super.getToolWindow(id)
    }

    override fun invokeLater(runnable: Runnable) {
      ApplicationManager.getApplication().invokeLater(runnable)
    }
  }

  private class TestToolWindow(
    project: Project,
    private val manager: ToolWindowManager
  ) : ToolWindowHeadlessManagerImpl.MockToolWindow(project) {

    private var available = true
    private var visible = false

    override fun setAvailable(available: Boolean, runnable: Runnable?) {
      this.available = available
    }

    override fun setAvailable(available: Boolean) {
      this.available = available
    }

    override fun isAvailable(): Boolean {
      return available
    }

    override fun show(runnable: Runnable?) {
      if (!visible) {
        visible = true
        notifyStateChanged()
      }
    }

    override fun hide(runnable: Runnable?) {
      if (visible) {
        visible = false
        notifyStateChanged()
      }
    }

    override fun isVisible() = visible

    private fun notifyStateChanged() {
      project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).stateChanged(manager)
    }
  }
}
