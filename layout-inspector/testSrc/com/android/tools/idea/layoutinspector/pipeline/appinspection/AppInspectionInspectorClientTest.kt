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
package com.android.tools.idea.layoutinspector.pipeline.appinspection

import com.android.ddmlib.testing.FakeAdbRule
import com.android.fakeadbserver.DeviceState
import com.android.repository.Revision
import com.android.repository.api.LocalPackage
import com.android.repository.api.RemotePackage
import com.android.repository.impl.meta.RepositoryPackages
import com.android.repository.impl.meta.TypeDetails
import com.android.repository.testframework.FakePackage
import com.android.repository.testframework.FakeRepoManager
import com.android.repository.testframework.MockFileOp
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdManager
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.IdDisplay
import com.android.sdklib.repository.targets.SystemImage
import com.android.sdklib.repository.targets.SystemImage.DEFAULT_TAG
import com.android.sdklib.repository.targets.SystemImage.GOOGLE_APIS_TAG
import com.android.sdklib.repository.targets.SystemImage.PLAY_STORE_TAG
import com.android.testutils.MockitoKt.mock
import com.android.testutils.file.createInMemoryFileSystemAndFolder
import com.android.testutils.file.someRoot
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.app.inspection.AppInspection
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.test.DEFAULT_TEST_INSPECTION_STREAM
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.layoutinspector.AdbServiceRule
import com.android.tools.idea.layoutinspector.InspectorClientProvider
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLaunchMonitor
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.adb.executeShellCommand
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.INCOMPATIBLE_LIBRARY_MESSAGE
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.PROGUARDED_LIBRARY_MESSAGE
import com.android.tools.idea.layoutinspector.pipeline.appinspection.inspectors.sendEvent
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.ViewLayoutInspectorClient
import com.android.tools.idea.layoutinspector.ui.InspectorBanner
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.android.tools.idea.layoutinspector.util.ReportingCountDownLatch
import com.android.tools.idea.project.AndroidRunConfigurations
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.testing.addManifest
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo
import com.intellij.execution.RunManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.ui.HyperlinkLabel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import layoutinspector.view.inspection.LayoutInspectorViewProtocol.ProgressEvent.ProgressCheckpoint.START_RECEIVED
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.verify
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import javax.swing.JPanel
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol as ComposeProtocol
import layoutinspector.view.inspection.LayoutInspectorViewProtocol as ViewProtocol

private val MODERN_PROCESS = MODERN_DEVICE.createProcess(streamId = DEFAULT_TEST_INSPECTION_STREAM.streamId)
private val OTHER_MODERN_PROCESS = MODERN_DEVICE.createProcess(name = "com.other", streamId = DEFAULT_TEST_INSPECTION_STREAM.streamId)

/** Timeout used in this test. While debugging, you may want to extend the timeout */
private const val TIMEOUT = 1L
private val TIMEOUT_UNIT = TimeUnit.SECONDS

class AppInspectionInspectorClientTest {
  private val monitor = mock<InspectorClientLaunchMonitor>()
  private var preferredProcess: ProcessDescriptor? = MODERN_PROCESS

  private val disposableRule = DisposableRule()
  private val inspectionRule = AppInspectionInspectorRule(disposableRule.disposable)
  private val inspectorRule = LayoutInspectorRule(object : InspectorClientProvider {
    override fun create(params: InspectorClientLauncher.Params, inspector: LayoutInspector): InspectorClient {
      return AppInspectionInspectorClient(params.process, params.isInstantlyAutoConnected, inspector.layoutInspectorModel,
                                          inspector.stats, disposableRule.disposable, inspectionRule.inspectionService.apiServices,
                                          inspectionRule.inspectionService.scope).apply {
        launchMonitor = monitor
      }
    }
  }) { it == preferredProcess}

  @get:Rule
  val ruleChain = RuleChain.outerRule(inspectionRule).around(inspectorRule).around(disposableRule)!!

  @Before
  fun before() {
    inspectorRule.projectRule.replaceService(PropertiesComponent::class.java, PropertiesComponentMock())
  }

  @Test
  fun clientCanConnectDisconnectAndReconnect() {
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    assertThat(inspectorRule.inspectorClient.isConnected).isTrue()

    inspectorRule.processNotifier.fireDisconnected(MODERN_PROCESS)
    assertThat(inspectorRule.inspectorClient.isConnected).isFalse()

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    assertThat(inspectorRule.inspectorClient.isConnected).isTrue()
  }

  @Test
  fun inspectorHandlesProgressEvents() = runBlocking {
    val progressSent = CompletableDeferred<Unit>()
    inspectionRule.viewInspector.listenWhen({ it.hasStartFetchCommand() }) {
      (inspectorRule.inspectorClient as AppInspectionInspectorClient).launchMonitor = monitor

      inspectionRule.viewInspector.connection.sendEvent {
        progressEventBuilder.apply {
          checkpoint = START_RECEIVED
        }
      }

      progressSent.complete(Unit)
    }
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    progressSent.await()
    verify(monitor).updateProgress(DynamicLayoutInspectorErrorInfo.AttachErrorState.START_RECEIVED)
  }

  @Test
  fun inspectorStartsFetchingContinuouslyOnConnectIfLiveMode() = runBlocking {
    InspectorClientSettings.isCapturingModeOn = true

    val startFetchReceived = CompletableDeferred<Unit>()
    inspectionRule.viewInspector.listenWhen({ it.hasStartFetchCommand() }) { command ->
      assertThat(command.startFetchCommand.continuous).isTrue()
      startFetchReceived.complete(Unit)
    }

    // Initial fetch additionally triggers requests for composables
    val composeCommands = ArrayBlockingQueue<ComposeProtocol.Command>(1)
    inspectionRule.composeInspector.listenWhen({ true }) { command ->
      composeCommands.add(command)
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    startFetchReceived.await() // If here, we already successfully connected (and sent an initial command)
    assertThat(inspectorRule.inspectorClient).isInstanceOf(AppInspectionInspectorClient::class.java)

    // View Inspector layout event -> Compose Inspector get composables command
    composeCommands.take().let { command ->
      assertThat(command.specializedCase).isEqualTo(ComposeProtocol.Command.SpecializedCase.GET_COMPOSABLES_COMMAND)
    }
  }

  @Test
  fun inspectorRequestsSingleFetchIfSnapshotMode() = runBlocking {
    InspectorClientSettings.isCapturingModeOn = false

    val startFetchReceived = CompletableDeferred<Unit>()
    inspectionRule.viewInspector.listenWhen({ it.hasStartFetchCommand() }) { command ->
      assertThat(command.startFetchCommand.continuous).isFalse()
      startFetchReceived.complete(Unit)
    }

    // Initial fetch additionally triggers requests for composables
    val composeCommands = ArrayBlockingQueue<ComposeProtocol.Command>(2)
    inspectionRule.composeInspector.listenWhen({ true }) { command ->
      composeCommands.add(command)
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    startFetchReceived.await() // If here, we already successfully connected (and sent an initial command)
    assertThat(inspectorRule.inspectorClient).isInstanceOf(AppInspectionInspectorClient::class.java)

    // View Inspector layout event -> Compose Inspector get composables command
    composeCommands.take().let { command ->
      assertThat(command.specializedCase).isEqualTo(ComposeProtocol.Command.SpecializedCase.GET_COMPOSABLES_COMMAND)
    }
    // View Inspector properties event -> Compose Inspector get all parameters
    composeCommands.take().let { command ->
      assertThat(command.specializedCase).isEqualTo(ComposeProtocol.Command.SpecializedCase.GET_ALL_PARAMETERS_COMMAND)
    }
  }

  @Test
  fun testViewDebugAttributesApplicationPackageSetAndReset() {
    inspectorRule.attachDevice(MODERN_DEVICE)
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isEqualTo(MODERN_PROCESS.name)

    // Imitate that the adb server was killed.
    // We expect the ViewDebugAttributes to be cleared anyway since a new adb bridge should be created.
    inspectorRule.adbService.killServer()

    // Disconnect directly instead of calling fireDisconnected - otherwise, we don't have an easy way to wait for the disconnect to
    // happen on a background thread
    inspectorRule.launcher.disconnectActiveClient()
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isNull()
    // No other attributes were modified
    assertThat(inspectorRule.adbProperties.debugViewAttributesChangesCount).isEqualTo(2)
  }

  @Test
  fun testViewDebugAttributesApplicationUntouchedIfAlreadySet() {
    inspectorRule.adbProperties.debugViewAttributesApplicationPackage = MODERN_PROCESS.name

    inspectorRule.attachDevice(MODERN_DEVICE)
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    assertThat(inspectorRule.adbProperties.debugViewAttributesChangesCount).isEqualTo(0)
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isEqualTo(MODERN_PROCESS.name)

    // Disconnect directly instead of calling fireDisconnected - otherwise, we don't have an easy way to wait for the disconnect to
    // happen on a background thread
    inspectorRule.launcher.disconnectActiveClient()
    assertThat(inspectorRule.adbProperties.debugViewAttributesChangesCount).isEqualTo(0)
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isEqualTo(MODERN_PROCESS.name)
  }

  @Test
  fun testViewDebugAttributesApplicationPackageOverriddenAndReset() {
    inspectorRule.attachDevice(MODERN_PROCESS.device)
    inspectorRule.adbRule.bridge.executeShellCommand(MODERN_PROCESS.device,
                                                     "settings put global debug_view_attributes_application_package com.example.another-app")

    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isEqualTo("com.example.another-app")

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isEqualTo(MODERN_PROCESS.name)

    inspectorRule.launcher.disconnectActiveClient()
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isNull()
  }

  @Test
  fun testViewDebugAttributesApplicationPackageNotOverriddenIfMatching() {
    inspectorRule.attachDevice(MODERN_PROCESS.device)
    inspectorRule.adbRule.bridge.executeShellCommand(MODERN_PROCESS.device,
                                                     "settings put global debug_view_attributes_application_package ${MODERN_PROCESS.name}")

    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isEqualTo(MODERN_PROCESS.name)
    assertThat(inspectorRule.adbProperties.debugViewAttributesChangesCount).isEqualTo(1)

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isEqualTo(MODERN_PROCESS.name)
    assertThat(inspectorRule.adbProperties.debugViewAttributesChangesCount).isEqualTo(1)

    inspectorRule.launcher.disconnectActiveClient()
    assertThat(inspectorRule.adbProperties.debugViewAttributesApplicationPackage).isEqualTo(MODERN_PROCESS.name)
    assertThat(inspectorRule.adbProperties.debugViewAttributesChangesCount).isEqualTo(1)
  }

  @Test
  fun inspectorSendsStopFetchCommand() = runBlocking {
    val stopFetchReceived = CompletableDeferred<Unit>()
    inspectionRule.viewInspector.listenWhen({ it.hasStopFetchCommand() }) {
      stopFetchReceived.complete(Unit)
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    inspectorRule.inspectorClient.stopFetching()
    stopFetchReceived.await()
  }

  @Test
  fun inspectorFiresErrorOnErrorEvent() = runBlocking {
    val startFetchError = "Failed to start fetching or whatever"

    inspectionRule.viewInspector.listenWhen({ it.hasStartFetchCommand() }) {
      inspectionRule.viewInspector.connection.sendEvent {
        errorEventBuilder.apply {
          message = startFetchError
        }
      }

      ViewProtocol.Response.newBuilder().setStartFetchResponse(ViewProtocol.StartFetchResponse.getDefaultInstance()).build()
    }

    val error = CompletableDeferred<String>()
    inspectorRule.launcher.addClientChangedListener { client ->
      client.registerErrorCallback { error.complete(it) }
    }
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    assertThat(error.await()).isEqualTo(startFetchError)
  }

  @Test
  fun composeClientShowsMessageIfOlderComposeUiLibrary() {
    inspectionRule.composeInspector.createResponseStatus = AppInspection.CreateInspectorResponse.Status.VERSION_INCOMPATIBLE
    val banner = InspectorBanner(inspectorRule.project)

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)

    assertThat(banner.text.text).isEqualTo(INCOMPATIBLE_LIBRARY_MESSAGE)
  }

  @Test
  fun composeClientShowsMessageIfProguardedComposeUiLibrary() {
    inspectionRule.composeInspector.createResponseStatus = AppInspection.CreateInspectorResponse.Status.APP_PROGUARDED
    val banner = InspectorBanner(inspectorRule.project)

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)

    assertThat(banner.text.text).isEqualTo(PROGUARDED_LIBRARY_MESSAGE)
  }

  @Test
  fun inspectorTreeEventIncludesUpdateScreenshotTypeCallback() {
    val screenshotTypeUpdated = ReportingCountDownLatch(1)
    inspectionRule.viewInspector.listenWhen({ it.hasUpdateScreenshotTypeCommand() }) { command ->
      assertThat(command.updateScreenshotTypeCommand.type).isEqualTo(ViewProtocol.Screenshot.Type.BITMAP)
      assertThat(command.updateScreenshotTypeCommand.scale).isEqualTo(1.0f)
      screenshotTypeUpdated.countDown()
    }

    inspectorRule.launcher.addClientChangedListener { client ->
      client.registerTreeEventCallback {
        (client as AppInspectionInspectorClient).updateScreenshotType(AndroidWindow.ImageType.BITMAP_AS_REQUESTED)
      }
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    screenshotTypeUpdated.await(TIMEOUT, TIMEOUT_UNIT)
  }

  @Test
  fun viewClientOnlyHandlesMostRecentLayoutEvent() {
    // This test will send two batches of layout events, to verify that we not only process the last event of a
    // batch, but that once processed, new events can be handled afterwards.
    var handlingFirstBatch = true

    inspectionRule.viewInspector.interceptWhen({ it.hasStartFetchCommand() }) {
      inspectionRule.viewInspector.connection.sendEvent {
        // We must always send roots at least once before the very first layout event
        rootsEventBuilder.apply {
          addIds(1)
        }
      }

      for (i in 0..10) {
        inspectionRule.viewInspector.connection.sendEvent {
          layoutEventBuilder.apply {
            rootViewBuilder.apply {
              id = 1
            }
            screenshotBuilder.apply {
              bytes = ByteString.copyFrom(byteArrayOf(i.toByte()))
            }
          }
        }
      }

      ViewProtocol.Response.newBuilder().setStartFetchResponse(ViewProtocol.StartFetchResponse.getDefaultInstance()).build()
    }

    inspectionRule.viewInspector.interceptWhen({ it.hasStopFetchCommand() }) {
      // Note: We don't normally spam a bunch of layout events when you stop fetching, but we do it
      // here for convenience, as stopFetching is easy to trigger in the test.
      for (i in 11..20) {
        inspectionRule.viewInspector.connection.sendEvent {
          layoutEventBuilder.apply {
            rootViewBuilder.apply {
              id = 1
            }
            screenshotBuilder.apply {
              bytes = ByteString.copyFrom(byteArrayOf(i.toByte()))
            }
          }
        }
      }

      ViewProtocol.Response.newBuilder().setStopFetchResponse(ViewProtocol.StopFetchResponse.getDefaultInstance()).build()
    }

    val treeEventsHandled = ReportingCountDownLatch(1)
    inspectorRule.launcher.addClientChangedListener { client ->
      client.registerTreeEventCallback { data ->
        (data as ViewLayoutInspectorClient.Data).viewEvent.let { viewEvent ->
          assertThat(viewEvent.rootView.id).isEqualTo(1)

          if (handlingFirstBatch) {
            handlingFirstBatch = false
            assertThat(viewEvent.screenshot.bytes.byteAt(0)).isEqualTo(10.toByte())
            client.stopFetching() // Triggers second batch of layout events
          }
          else {
            assertThat(viewEvent.screenshot.bytes.byteAt(0)).isEqualTo(20.toByte())
            treeEventsHandled.countDown()
          }
        }
      }
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS) // Triggers first batch of layout events
    treeEventsHandled.await(TIMEOUT, TIMEOUT_UNIT)
  }

  @Test
  fun testCapabilitiesUpdateWithoutComposeNodes() {
    val inspectorState = FakeInspectorState(inspectionRule.viewInspector, inspectionRule.composeInspector)
    inspectorState.createFakeViewTree()

    val modelUpdatedLatch = ReportingCountDownLatch(2) // We'll get two tree layout events on start fetch
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedLatch.countDown()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedLatch.await(TIMEOUT, TIMEOUT_UNIT)
    assertThat(inspectorRule.inspectorClient.capabilities).containsNoneOf(Capability.SUPPORTS_COMPOSE, Capability.SUPPORTS_SEMANTICS)
  }

  @Test
  fun testCapabilitiesUpdateWithComposeNodes() {
    val inspectorState = FakeInspectorState(inspectionRule.viewInspector, inspectionRule.composeInspector)
    inspectorState.createFakeViewTree()
    inspectorState.createFakeComposeTree(withSemantics = false)

    val modelUpdatedLatch = ReportingCountDownLatch(2) // We'll get two tree layout events on start fetch
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedLatch.countDown()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedLatch.await(TIMEOUT, TIMEOUT_UNIT)
    assertThat(inspectorRule.inspectorClient.capabilities).contains(Capability.SUPPORTS_COMPOSE)
    assertThat(inspectorRule.inspectorClient.capabilities).doesNotContain(Capability.SUPPORTS_SEMANTICS)
  }

  @Test
  fun testCapabilitiesUpdateWithComposeNodesWithSemantics() {
    val inspectorState = FakeInspectorState(inspectionRule.viewInspector, inspectionRule.composeInspector)
    inspectorState.createFakeViewTree()
    inspectorState.createFakeComposeTree(withSemantics = true)

    val modelUpdatedLatch = ReportingCountDownLatch(2) // We'll get two tree layout events on start fetch
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedLatch.countDown()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedLatch.await(TIMEOUT, TIMEOUT_UNIT)
    assertThat(inspectorRule.inspectorClient.capabilities).containsAllOf(Capability.SUPPORTS_COMPOSE, Capability.SUPPORTS_SEMANTICS)
  }

  @Test
  fun testTextViewUnderComposeNode() {
    val inspectorState = FakeInspectorState(inspectionRule.viewInspector, inspectionRule.composeInspector)
    inspectorState.createFakeViewTree()
    inspectorState.createFakeComposeTree()
    val modelUpdatedLatch = ReportingCountDownLatch(2) // We'll get two tree layout events on start fetch
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedLatch.countDown()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedLatch.await(TIMEOUT, TIMEOUT_UNIT)

    // Verify that the MaterialTextView from the views were placed under the ComposeViewNode: "ComposeNode" with id of -7
    val composeNode = inspectorRule.inspectorModel[-7]!!
    ViewNode.readAccess {
      assertThat(composeNode.parent?.qualifiedName).isEqualTo("AndroidView")
      assertThat(composeNode.qualifiedName).isEqualTo("ComposeNode")
      assertThat(composeNode.children.single().qualifiedName).isEqualTo("com.google.android.material.textview.MaterialTextView")

      // Also verify that the ComposeView do not contain the MaterialTextView nor the RippleContainer in its children:
      val composeView = inspectorRule.inspectorModel[6]!!
      assertThat(composeView.qualifiedName).isEqualTo("android.view.ComposeView")
      assertThat(composeView.children.single().qualifiedName).isEqualTo("Surface")
    }
  }

  @Test
  fun errorShownOnConnectException() {
    InspectorClientSettings.isCapturingModeOn = true
    val banner = InspectorBanner(inspectorRule.project)
    inspectionRule.viewInspector.interceptWhen({ it.hasStartFetchCommand() }) {
      layoutinspector.view.inspection.LayoutInspectorViewProtocol.Response.newBuilder().apply {
        startFetchResponseBuilder.error = "here's my error"
      }.build()
    }
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)

    assertThat(banner.text.text).isEqualTo("here's my error")
    assertThat(inspectorRule.inspectorClient.isConnected).isFalse()
  }

  @Test
  fun errorShownOnRefreshException() {
    InspectorClientSettings.isCapturingModeOn = false
    val banner = InspectorBanner(inspectorRule.project)
    inspectionRule.viewInspector.interceptWhen({ it.hasStartFetchCommand() }) {
      layoutinspector.view.inspection.LayoutInspectorViewProtocol.Response.newBuilder().apply {
        startFetchResponseBuilder.error = "here's my error"
      }.build()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)

    assertThat(banner.text.text).isEqualTo("here's my error")
    assertThat(inspectorRule.inspectorClient.isConnected).isFalse()
  }

  @Test
  fun testActivityRestartBannerShown() {
    setUpRunConfiguration()
    preferredProcess = null
    inspectorRule.attachDevice(MODERN_PROCESS.device)
    val banner = InspectorBanner(inspectorRule.project)
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    inspectorRule.processes.selectedProcess = MODERN_PROCESS
    verifyActivityRestartBanner(banner, runConfigActionExpected = true)
  }

  @Test
  fun testNoActivityRestartBannerShownIfOptedOut() {
    setUpRunConfiguration()
    preferredProcess = null
    inspectorRule.attachDevice(MODERN_PROCESS.device)
    val banner = InspectorBanner(inspectorRule.project)
    PropertiesComponent.getInstance().setValue(KEY_HIDE_ACTIVITY_RESTART_BANNER, true)
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    inspectorRule.processes.selectedProcess = MODERN_PROCESS
    assertThat(banner.isVisible).isFalse()
  }

  @Test
  fun testOptOutOfActivityRestartBanner() {
    setUpRunConfiguration()
    preferredProcess = null
    inspectorRule.attachDevice(MODERN_PROCESS.device)
    val banner = InspectorBanner(inspectorRule.project)
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    inspectorRule.processes.selectedProcess = MODERN_PROCESS
    val actionPanel = banner.components[1] as JPanel
    val doNotShowAction = actionPanel.components[1] as HyperlinkLabel
    doNotShowAction.doClick()
    assertThat(PropertiesComponent.getInstance().getBoolean(KEY_HIDE_ACTIVITY_RESTART_BANNER)).isTrue()
  }

  @Test
  fun testNoActivityRestartBannerShownDuringAutoConnect() {
    setUpRunConfiguration()
    inspectorRule.attachDevice(MODERN_PROCESS.device)
    val banner = InspectorBanner(inspectorRule.project)
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    assertThat(banner.isVisible).isFalse()
  }

  @Test
  fun testNoActivityRestartBannerShownWhenDebugAttributesAreAlreadySet() {
    inspectorRule.adbProperties.debugViewAttributesApplicationPackage = MODERN_PROCESS.name
    setUpRunConfiguration()
    preferredProcess = null
    inspectorRule.attachDevice(MODERN_PROCESS.device)
    val banner = InspectorBanner(inspectorRule.project)
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    inspectorRule.processes.selectedProcess = MODERN_PROCESS
    assertThat(banner.isVisible).isFalse()
  }

  @Test
  fun testActivityRestartBannerShownIfRunConfigAreAlreadySetButAttributeIsMissing() {
    setUpRunConfiguration(enableInspectionWithoutRestart = true)
    preferredProcess = null
    inspectorRule.attachDevice(MODERN_PROCESS.device)
    val banner = InspectorBanner(inspectorRule.project)
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    inspectorRule.processes.selectedProcess = MODERN_PROCESS
    verifyActivityRestartBanner(banner, runConfigActionExpected = false)
  }

  @Test
  fun testActivityRestartBannerShownFromOtherAppProcess() {
    setUpRunConfiguration()
    preferredProcess = null
    inspectorRule.attachDevice(OTHER_MODERN_PROCESS.device)
    val banner = InspectorBanner(inspectorRule.project)
    inspectorRule.processNotifier.fireConnected(OTHER_MODERN_PROCESS)
    inspectorRule.processes.selectedProcess = OTHER_MODERN_PROCESS
    verifyActivityRestartBanner(banner, runConfigActionExpected = false)
  }

  private fun setUpRunConfiguration(enableInspectionWithoutRestart: Boolean = false) {
    addManifest(inspectorRule.projectRule.fixture)
    AndroidRunConfigurations.getInstance().createRunConfiguration(AndroidFacet.getInstance(inspectorRule.projectRule.module)!!)
    if (enableInspectionWithoutRestart) {
      val runManager = RunManager.getInstance(inspectorRule.project)
      val config = runManager.allConfigurationsList.filterIsInstance<AndroidRunConfiguration>().firstOrNull { it.name == "app" }
      config!!.INSPECTION_WITHOUT_ACTIVITY_RESTART = true
    }
  }

  private fun verifyActivityRestartBanner(banner: InspectorBanner, runConfigActionExpected: Boolean) {
    assertThat(banner.isVisible).isTrue()
    assertThat(banner.text.text).isEqualTo("The activity was restarted. This can be avoided by enabling " +
                                           "\"Connect without restarting activity\" in the run configuration options.")
    val service = InspectorBannerService.getInstance(inspectorRule.project)
    service.DISMISS_ACTION.actionPerformed(mock())
    val actionPanel = banner.getComponent(1) as JPanel
    if (runConfigActionExpected) {
      assertThat(actionPanel.componentCount).isEqualTo(3)
      assertThat((actionPanel.components[0] as HyperlinkLabel).text).isEqualTo("Open Run Configuration")
      assertThat((actionPanel.components[1] as HyperlinkLabel).text).isEqualTo("Don't Show Again")
      assertThat((actionPanel.components[2] as HyperlinkLabel).text).isEqualTo("Dismiss")
    }
    else {
      assertThat(actionPanel.componentCount).isEqualTo(2)
      assertThat((actionPanel.components[0] as HyperlinkLabel).text).isEqualTo("Don't Show Again")
      assertThat((actionPanel.components[1] as HyperlinkLabel).text).isEqualTo("Dismiss")
    }
  }
}

class AppInspectionInspectorClientWithUnsupportedApi29 {
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()
  private val adbRule = FakeAdbRule()
  private val adbService = AdbServiceRule(projectRule::project, adbRule)

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(adbRule).around(adbService).around(disposableRule)!!

  @Test
  fun testApi29VersionBanner() = runBlocking {
    val processDescriptor = setUpDevice(29)
    val sdkRoot = createInMemoryFileSystemAndFolder("sdk")

    checkBannerForTag(processDescriptor, sdkRoot, DEFAULT_TAG, MIN_API_29_AOSP_SYSIMG_REV, true)
    checkBannerForTag(processDescriptor, sdkRoot, GOOGLE_APIS_TAG, MIN_API_29_GOOGLE_APIS_SYSIMG_REV, true)
    checkBannerForTag(processDescriptor, sdkRoot, PLAY_STORE_TAG, 999, false)

    // Set up an API 30 device and the inspector should be created successfully
    val processDescriptor2 = setUpDevice(30)

    val sdkPackage = setUpSdkPackage(sdkRoot, 1, 30, null, false) as LocalPackage
    val avdInfo = setUpAvd(sdkPackage, null, 30)
    val packages = RepositoryPackages(listOf(sdkPackage), listOf())
    val sdkHandler = AndroidSdkHandler(sdkRoot, null, MockFileOp(sdkRoot.fileSystem), FakeRepoManager(sdkRoot, packages))
    val banner = InspectorBanner(projectRule.project)
    assertThat(banner.isVisible).isFalse()

    setUpAvdManagerAndRun(sdkHandler, avdInfo, suspend {
      val client = AppInspectionInspectorClient(processDescriptor2, isInstantlyAutoConnected = false, model(projectRule.project) {},
                                                mock(), disposableRule.disposable, mock(), sdkHandler = sdkHandler)
      // shouldn't get an exception
      client.connect()
    })

  }

  private suspend fun checkBannerForTag(
    processDescriptor: ProcessDescriptor,
    sdkRoot: Path,
    tag: IdDisplay?,
    minRevision: Int,
    checkUpdate: Boolean
  ) {
    // Set up an AOSP api 29 device below the required system image revision, with no update available
    val sdkPackage = setUpSdkPackage(sdkRoot, minRevision - 1, 29, tag, false) as LocalPackage
    val avdInfo = setUpAvd(sdkPackage, tag, 29)
    val packages = RepositoryPackages(listOf(sdkPackage), listOf())
    val sdkHandler = AndroidSdkHandler(sdkRoot, null, MockFileOp(sdkRoot.fileSystem), FakeRepoManager(sdkRoot, packages))
    val banner = InspectorBanner(projectRule.project)
    assertThat(banner.isVisible).isFalse()

    setUpAvdManagerAndRun(sdkHandler, avdInfo, suspend {
      val client = AppInspectionInspectorClient(processDescriptor, isInstantlyAutoConnected = false, model(projectRule.project) {},
                                                mock(), disposableRule.disposable, mock(), sdkHandler = sdkHandler)
      client.connect()
      waitForCondition(1, TimeUnit.SECONDS) { client.state == InspectorClient.State.DISCONNECTED }
      assertThat(banner.isVisible).isTrue()
      assertThat(banner.text.text).isEqualTo(API_29_BUG_MESSAGE)
    })
    banner.isVisible = false

    if (!checkUpdate) {
      return
    }

    // Now there is an update available
    val remotePackage = setUpSdkPackage(sdkRoot, minRevision, 29, tag, true) as RemotePackage
    packages.setRemotePkgInfos(listOf(remotePackage))
    setUpAvdManagerAndRun(sdkHandler, avdInfo, suspend {
      val client = AppInspectionInspectorClient(processDescriptor, isInstantlyAutoConnected = false, model(projectRule.project) {},
                                                mock(), disposableRule.disposable, mock(), sdkHandler = sdkHandler)
      client.connect()
      waitForCondition(1, TimeUnit.SECONDS) { client.state == InspectorClient.State.DISCONNECTED }
      assertThat(banner.isVisible).isTrue()
      assertThat(banner.text.text).isEqualTo("$API_29_BUG_MESSAGE $API_29_BUG_UPGRADE")
    })
    banner.isVisible = false
  }

  private suspend fun setUpAvdManagerAndRun(sdkHandler: AndroidSdkHandler, avdInfo: AvdInfo, body: suspend () -> Unit) {
    val connection = object : AvdManagerConnection(sdkHandler, sdkHandler.fileOp.fileSystem.someRoot.resolve("android/avds"),
                                                   MoreExecutors.newDirectExecutorService()) {
      fun setFactory() {
        setConnectionFactory { _, _ -> this }
      }

      override fun findAvd(avdId: String) = if (avdId == avdInfo.name) avdInfo else null

      fun resetFactory() {
        resetConnectionFactory()
      }
    }
    try {
      connection.setFactory()
      body()
    }
    finally {
      connection.resetFactory()
    }
  }

  private fun setUpAvd(sdkPackage: LocalPackage, tag: IdDisplay?, apiLevel: Int): AvdInfo {
    val systemImage = SystemImage(sdkPackage.location, tag, null, "x86", arrayOf(), sdkPackage)
    val properties = mutableMapOf<String, String>()
    if (tag != null) {
      properties[AvdManager.AVD_INI_TAG_ID] = tag.id
      properties[AvdManager.AVD_INI_TAG_DISPLAY] = tag.display
    }
    return AvdInfo("myAvd-$apiLevel", File("myIni"), "/android/avds/myAvd-$apiLevel", systemImage, properties)
  }

  private fun setUpSdkPackage(sdkRoot: Path, revision: Int, apiLevel: Int, tag: IdDisplay?, isRemote: Boolean): FakePackage {
    val sdkPackage =
      if (isRemote) FakePackage.FakeRemotePackage("mySysImg-$apiLevel")
      else FakePackage.FakeLocalPackage("mySysImg-$apiLevel", sdkRoot.resolve("mySysImg"))
    sdkPackage.setRevision(Revision(revision))
    val packageDetails = AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType()
    packageDetails.apiLevel = apiLevel
    tag?.let { packageDetails.tags.add(it) }
    sdkPackage.typeDetails = packageDetails as TypeDetails
    return sdkPackage
  }

  private fun setUpDevice(apiLevel: Int): ProcessDescriptor {
    val processDescriptor = object : ProcessDescriptor {
      override val device = object: DeviceDescriptor {
        override val manufacturer = "mfg"
        override val model = "model"
        override val serial = "emulator-$apiLevel"
        override val isEmulator = true
        override val apiLevel = apiLevel
        override val version = "10.0.0"
        override val codename: String? = null
      }
      override val abiCpuArch = "x86_64"
      override val name = "my name"
      override val isRunning = true
      override val pid = 1234
      override val streamId = 4321L
    }

    adbRule.attachDevice(processDescriptor.device.serial, processDescriptor.device.manufacturer, processDescriptor.device.model,
                         processDescriptor.device.version, processDescriptor.device.apiLevel.toString(),
                         DeviceState.HostConnectionType.LOCAL, "myAvd-$apiLevel", "/android/avds/myAvd-$apiLevel")

    return processDescriptor
  }
}

class AppInspectionInspectorClientWithFailingClientTest {
  private val disposableRule = DisposableRule()
  private val inspectionRule = AppInspectionInspectorRule(disposableRule.disposable)
  private val inspectorRule = LayoutInspectorRule(
    AppInspectionClientProvider({ mock() }, { inspectionRule.inspectionService.scope }, disposableRule.disposable)) {
    it.name == MODERN_PROCESS.name
  }

  @get:Rule
  val ruleChain = RuleChain.outerRule(inspectionRule).around(inspectorRule).around(disposableRule)!!

  @Test
  fun errorShownOnNoAgentWithApi29() {
    val banner = InspectorBanner(inspectorRule.project)
    inspectionRule.viewInspector.interceptWhen({ it.hasStartFetchCommand() }) {
      throw Exception()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    assertThat(banner.text.text).isEqualTo("Unable to detect a live inspection service. To enable live inspections, restart the device.")
    assertThat(inspectorRule.inspectorClient.isConnected).isFalse()
  }
}
