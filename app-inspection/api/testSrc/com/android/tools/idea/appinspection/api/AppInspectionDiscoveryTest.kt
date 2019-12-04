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
package com.android.tools.idea.appinspection.api

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.appinspection.test.ASYNC_TIMEOUT_MS
import com.android.tools.idea.appinspection.test.AppInspectionServiceRule
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AppInspectionDiscoveryTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer)

  private val FAKE_PROCESS = AutoPreferredProcess(
    FakeTransportService.FAKE_DEVICE.manufacturer,
    FakeTransportService.FAKE_DEVICE.model,
    FakeTransportService.FAKE_DEVICE.serial,
    FakeTransportService.FAKE_PROCESS_NAME
  )

  private val ATTACH_HANDLER = object : CommandHandler(timer) {
    override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
      events.add(
        Common.Event.newBuilder()
          .setKind(Common.Event.Kind.AGENT)
          .setPid(FakeTransportService.FAKE_PROCESS.pid)
          .setAgentData(Common.AgentData.newBuilder().setStatus(Common.AgentData.Status.ATTACHED).build())
          .build()
      )
    }
  }

  @get:Rule
  val grpcServerRule = FakeGrpcServer.createFakeGrpcServer("AppInspectionDiscoveryTest", transportService, transportService)!!

  @get:Rule
  val timeoutRule = Timeout(ASYNC_TIMEOUT_MS, TimeUnit.MILLISECONDS)

  init {
    transportService.setCommandHandler(Commands.Command.CommandType.ATTACH_AGENT, ATTACH_HANDLER)
  }

  @Test
  fun makeNewConnectionFiresListener() {
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    val discoveryHost = AppInspectionDiscoveryHost(
      executor,
      object : AppInspectionDiscoveryHost.Channel {
        override val name = grpcServerRule.name
      }
    )

    val latch = CountDownLatch(1)
    discoveryHost.discovery.addTargetListener(executor) { latch.countDown() }

    discoveryHost.connect(AppInspectionServiceRule.TestTransportFileCopier(), FAKE_PROCESS).get()

    latch.await()
  }

  @Test
  fun connectionIsCached() {
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    val discoveryHost = AppInspectionDiscoveryHost(
      executor,
      object : AppInspectionDiscoveryHost.Channel {
        override val name = grpcServerRule.name
      }
    )

    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, TestInspectorCommandHandler(timer))

    // Attach to the process.
    val connection1 = discoveryHost.connect(AppInspectionServiceRule.TestTransportFileCopier(), FAKE_PROCESS).get()

    // Attach to the same process again.
    val connection2 = discoveryHost.connect(AppInspectionServiceRule.TestTransportFileCopier(), FAKE_PROCESS).get()

    assertThat(connection1).isSameAs(connection2)
  }

  @Test
  fun addListenerReceivesExistingConnections() {
    val executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1))
    val discoveryHost = AppInspectionDiscoveryHost(
      executor,
      object : AppInspectionDiscoveryHost.Channel {
        override val name = grpcServerRule.name
      }
    )

    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, TestInspectorCommandHandler(timer))

    // Attach to a new process.
    val connectionFuture = discoveryHost.connect(AppInspectionServiceRule.TestTransportFileCopier(), FAKE_PROCESS)
    connectionFuture.get()

    val latch = CountDownLatch(1)
    val connectionsList = mutableListOf<AppInspectionTarget>()
    discoveryHost.discovery.addTargetListener(executor) {
      connectionsList.add(it)
      latch.countDown()
    }

    // Wait for discovery to notify us of existing connections
    latch.await()

    // Verify
    assertThat(connectionsList).containsExactly(connectionFuture.get())
  }
}