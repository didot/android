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
package com.android.tools.idea.layoutinspector.pipeline

import com.android.ddmlib.testing.FakeAdbRule
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.test.TestProcessNotifier
import com.android.tools.idea.layoutinspector.LEGACY_DEVICE
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.properties.PropertiesProvider
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

class InspectorClientLauncherTest {
  @get:Rule
  val adbRule = FakeAdbRule()

  @get:Rule
  val disposableRule = DisposableRule()

  private class FakeInspectorClient(
    val name: String,
    process: ProcessDescriptor,
    private val onDisconnected: () -> Unit = {})
    : AbstractInspectorClient(process) {

    override fun startFetching() = throw NotImplementedError()
    override fun stopFetching() = throw NotImplementedError()
    override fun refresh() = throw NotImplementedError()

    override fun doConnect() {}
    override fun doDisconnect(): Future<*> {
      onDisconnected()
      return CompletableFuture.completedFuture(null)
    }

    override val capabilities
      get() = throw NotImplementedError()
    override val treeLoader: TreeLoader get() = throw NotImplementedError()
    override val isCapturing: Boolean get() = throw NotImplementedError()
    override val provider: PropertiesProvider get() = throw NotImplementedError()
  }

  @Test
  fun initialInspectorLauncherStartsWithDisconnectedClient() {
    val processes = ProcessesModel(TestProcessNotifier()) { listOf() }
    val launcher = InspectorClientLauncher(adbRule.bridge, processes, listOf(), disposableRule.disposable)

    assertThat(launcher.activeClient).isInstanceOf(DisconnectedClient::class.java)
  }

  @Test
  fun emptyInspectorLauncherIgnoresProcessChanges() {
    val processes = ProcessesModel(TestProcessNotifier()) { listOf() }
    val launcher = InspectorClientLauncher(adbRule.bridge, processes, listOf(), disposableRule.disposable)

    var clientChangedCount = 0
    launcher.addClientChangedListener { clientChangedCount++ }

    processes.selectedProcess = MODERN_DEVICE.createProcess()

    assertThat(launcher.activeClient).isInstanceOf(DisconnectedClient::class.java)
    assertThat(clientChangedCount).isEqualTo(0)
  }

  @Test
  fun inspectorWithNoMatchReturnsDisconnectedClient() {
    val processes = ProcessesModel(TestProcessNotifier()) { listOf() }
    val launcher = InspectorClientLauncher(
      adbRule.bridge,
      processes,
      listOf { params ->
        if (params.process.device.apiLevel == MODERN_DEVICE.apiLevel) FakeInspectorClient(
          "Modern client", params.process)
        else null
      },
      disposableRule.disposable)

    assertThat(launcher.activeClient).isInstanceOf(DisconnectedClient::class.java)

    processes.selectedProcess = MODERN_DEVICE.createProcess()
    assertThat(launcher.activeClient).isInstanceOf(FakeInspectorClient::class.java)

    processes.selectedProcess = LEGACY_DEVICE.createProcess()
    assertThat(launcher.activeClient).isInstanceOf(DisconnectedClient::class.java)
  }

  @Test
  fun disposingLauncherDisconnectsAndDisposesActiveClient() {
    val processes = ProcessesModel(TestProcessNotifier()) { listOf() }

    val launcherDispoable = Disposer.newDisposable()
    var clientWasDisposed = false
    val launcher = InspectorClientLauncher(
      adbRule.bridge,
      processes,
      listOf { params -> FakeInspectorClient("Client", params.process) { clientWasDisposed = true } },
      launcherDispoable)

    processes.selectedProcess = MODERN_DEVICE.createProcess()
    assertThat(launcher.activeClient.isConnected).isTrue()

    assertThat(clientWasDisposed).isFalse()
    Disposer.dispose(launcherDispoable)
    assertThat(clientWasDisposed).isTrue()
    assertThat(launcher.activeClient.isConnected).isFalse()
  }

  @Test
  fun inspectorLauncherUsesFirstMatchingClient() {
    val processes = ProcessesModel(TestProcessNotifier()) { listOf() }

    var creatorCount1 = 0
    var creatorCount2 = 0
    var creatorCount3 = 0

    val launcher = InspectorClientLauncher(
      adbRule.bridge,
      processes,
      listOf(
        { params ->
          creatorCount1++
          if (params.process.device.apiLevel == MODERN_DEVICE.apiLevel) FakeInspectorClient("Modern client", params.process) else null
        },
        { params ->
          creatorCount2++
          if (params.process.device.apiLevel == LEGACY_DEVICE.apiLevel) FakeInspectorClient("Legacy client", params.process) else null
        },
        { params ->
          creatorCount3++
          FakeInspectorClient("Fallback client", params.process)
        }
      ),
      disposableRule.disposable)

    var clientChangedCount = 0
    launcher.addClientChangedListener { ++clientChangedCount }

    assertThat(!launcher.activeClient.isConnected)

    processes.selectedProcess = MODERN_DEVICE.createProcess()
    (launcher.activeClient as FakeInspectorClient).let { activeClient ->
      assertThat(activeClient.name).isEqualTo("Modern client")
    }
    assertThat(clientChangedCount).isEqualTo(1)
    assertThat(creatorCount1).isEqualTo(1)
    assertThat(creatorCount2).isEqualTo(0)
    assertThat(creatorCount3).isEqualTo(0)

    processes.selectedProcess = LEGACY_DEVICE.createProcess()
    (launcher.activeClient as FakeInspectorClient).let { activeClient ->
      assertThat(activeClient.name).isEqualTo("Legacy client")
    }

    assertThat(clientChangedCount).isEqualTo(2)
    assertThat(creatorCount1).isEqualTo(2)
    assertThat(creatorCount2).isEqualTo(1)
    assertThat(creatorCount3).isEqualTo(0)
  }
}