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
package com.android.tools.idea.appinspection.internal

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.app.inspection.AppInspection.AppInspectionEvent
import com.android.tools.app.inspection.AppInspection.CrashEvent
import com.android.tools.idea.appinspection.api.TestInspectorCommandHandler
import com.android.tools.idea.appinspection.inspector.api.AppInspectionConnectionException
import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.android.tools.idea.appinspection.test.AppInspectionServiceRule
import com.android.tools.idea.appinspection.test.AppInspectionTestUtils.createRawAppInspectionEvent
import com.android.tools.idea.appinspection.test.INSPECTOR_ID
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common.Event
import com.android.tools.profiler.proto.Common.Event.Kind.PROCESS
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import junit.framework.TestCase.fail
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class AppInspectorConnectionTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, false)

  private val grpcServerRule = FakeGrpcServer.createFakeGrpcServer("AppInspectorConnectionTest", transportService, transportService)!!
  private val appInspectionRule = AppInspectionServiceRule(timer, transportService, grpcServerRule)

  @get:Rule
  val ruleChain = RuleChain.outerRule(grpcServerRule).around(appInspectionRule)!!

  @Test
  fun disposeInspectorSucceeds() = runBlocking<Unit> {
    val connection = appInspectionRule.launchInspectorConnection()

    connection.messenger.disposeInspector()
  }

  @Test
  fun disposeFailsButInspectorIsDisposedAnyway() = runBlocking<Unit> {
    val connection = appInspectionRule.launchInspectorConnection(
      commandHandler = TestInspectorCommandHandler(timer, false, "error")
    )

    connection.messenger.disposeInspector()
  }

  @Test
  fun sendRawCommandSucceedWithCallback() = runBlocking<Unit> {
    val connection = appInspectionRule.launchInspectorConnection()

    assertThat(connection.messenger.sendRawCommand("TestData".toByteArray())).isEqualTo("TestData".toByteArray())
  }

  @Test
  fun sendRawCommandFailWithCallback() = runBlocking<Unit> {
    val connection = appInspectionRule.launchInspectorConnection(
      commandHandler = TestInspectorCommandHandler(timer, false, "error")
    )

    assertThat(connection.messenger.sendRawCommand("TestData".toByteArray())).isEqualTo("error".toByteArray())
  }


  @Test
  fun receiveGeneralEvent() = runBlocking<Unit> {
    assertThat(
      suspendCoroutine<ByteArray> { cont ->
        val eventListener = object : AppInspectionServiceRule.TestInspectorRawEventListener() {
          override fun onRawEvent(eventData: ByteArray) {
            super.onRawEvent(eventData)
            cont.resume(eventData)
          }
        }
        appInspectionRule.launchInspectorConnection(inspectorId = INSPECTOR_ID, eventListener = eventListener)
        appInspectionRule.addAppInspectionEvent(createRawAppInspectionEvent(byteArrayOf(0x12, 0x15)))
      }
    ).isEqualTo(byteArrayOf(0x12, 0x15))
  }

  @Test
  fun rawEventWithCommandIdOnlyTriggersEventListener() = runBlocking<Unit> {
    val eventListener = AppInspectionServiceRule.TestInspectorRawEventListener()
    val connection = appInspectionRule.launchInspectorConnection(eventListener = eventListener)

    assertThat(connection.messenger.sendRawCommand("TestData".toByteArray())).isEqualTo("TestData".toByteArray())
    assertThat(eventListener.rawEvents).isEmpty()
  }

  @Test
  fun disposeConnectionClosesConnection() = runBlocking<Unit> {
    val connection = appInspectionRule.launchInspectorConnection(INSPECTOR_ID)

    connection.messenger.disposeInspector()

    // connection should be closed
    try {
      connection.messenger.sendRawCommand("Test".toByteArray())
      fail()
    } catch (e: AppInspectionConnectionException) {
      assertThat(e.message).isEqualTo("Failed to send a command because the $INSPECTOR_ID connection is already closed.")
    }
  }

  @Test
  fun receiveCrashEventClosesConnection() = runBlocking<Unit> {
    val client = appInspectionRule.launchInspectorConnection(inspectorId = INSPECTOR_ID)

    appInspectionRule.addAppInspectionEvent(
      AppInspectionEvent.newBuilder()
        .setInspectorId(INSPECTOR_ID)
        .setCrashEvent(
          CrashEvent.newBuilder()
            .setErrorMessage("error")
            .build()
        )
        .build()
    )

    // connection should be closed
    try {
      client.messenger.disposeInspector()
      fail()
    } catch (e: AppInspectionConnectionException) {
      assertThat(e.message).isEqualTo("Inspector $INSPECTOR_ID has crashed.")
    }
  }

  @Test
  fun disposeUsingClosedConnectionThrowsException() = runBlocking<Unit> {
    val client = appInspectionRule.launchInspectorConnection(inspectorId = INSPECTOR_ID)

    appInspectionRule.addEvent(
      Event.newBuilder()
        .setKind(PROCESS)
        .setIsEnded(true)
        .setTimestamp(timer.currentTimeNs)
        .build()
    )

    // connection should be closed
    try {
      client.messenger.disposeInspector()
      fail()
    } catch (e: AppInspectionConnectionException) {
      assertThat(e.message).isEqualTo("Inspector $INSPECTOR_ID was disposed, because app process terminated.")
    }
  }

  @Test
  fun sendCommandUsingClosedConnectionThrowsException() = runBlocking<Unit> {
    val client = appInspectionRule.launchInspectorConnection(inspectorId = INSPECTOR_ID)

    appInspectionRule.addEvent(
      Event.newBuilder()
        .setKind(PROCESS)
        .setIsEnded(true)
        .build()
    )

    suspendCoroutine<Unit> { cont ->
      client.addServiceEventListener(object : AppInspectorClient.ServiceEventListener {
        override fun onDispose() {
          cont.resume(Unit)
        }
      }, MoreExecutors.directExecutor())
    }

    // connection should be closed
    try {
      client.messenger.sendRawCommand("Data".toByteArray())
      fail()
    } catch (e: AppInspectionConnectionException) {
      assertThat(e.message).isEqualTo("Failed to send a command because the $INSPECTOR_ID connection is already closed.")
    }
  }

  @Test
  fun crashSetsAllOutstandingFutures() = runBlocking<Unit> {
    val client = appInspectionRule.launchInspectorConnection(
      inspectorId = INSPECTOR_ID,
      commandHandler = object : CommandHandler(timer) {
        override fun handleCommand(command: Commands.Command, events: MutableList<Event>) {
          // do nothing
        }
      }
    )

    supervisorScope {
      val disposeDeferred = async { client.messenger.disposeInspector() }
      val commandDeferred = async { client.messenger.sendRawCommand("Blah".toByteArray()) }

      val checkResults = async {
        try {
          disposeDeferred.await()
          fail()
        } catch (e: AppInspectionConnectionException) {
          assertThat(e.message).isEqualTo("Inspector $INSPECTOR_ID has crashed.")
        }

        try {
          commandDeferred.await()
          fail()
        } catch (e: AppInspectionConnectionException) {
          assertThat(e.message).isEqualTo("Inspector $INSPECTOR_ID has crashed.")
        } catch (e: Exception) {
          println(e)
        }
      }

      appInspectionRule.addAppInspectionEvent(
        AppInspectionEvent.newBuilder()
          .setInspectorId(INSPECTOR_ID)
          .setCrashEvent(
            CrashEvent.newBuilder()
              .setErrorMessage("error")
              .build()
          )
          .build()
      )

      checkResults.await()
    }
  }

  @Test
  fun connectionDoesNotReceiveStaleEvents() = runBlocking<Unit> {
    val staleEventData = byteArrayOf(0x12, 0x15)
    val freshEventData = byteArrayOf(0x01, 0x02)

    timer.currentTimeNs = 3
    appInspectionRule.addAppInspectionEvent(createRawAppInspectionEvent(staleEventData))

    timer.currentTimeNs = 5
    suspendCoroutine<Unit> { cont ->
      val listener = object : AppInspectionServiceRule.TestInspectorRawEventListener() {
        override fun onRawEvent(eventData: ByteArray) {
          assertThat(eventData).isEqualTo(freshEventData)
          super.onRawEvent(eventData)
          cont.resumeWith(Result.success(Unit))
        }
      }
      appInspectionRule.launchInspectorConnection(
        inspectorId = INSPECTOR_ID,
        eventListener = listener
      )
      appInspectionRule.addAppInspectionEvent(createRawAppInspectionEvent(freshEventData))
    }
  }

  @ExperimentalCoroutinesApi
  @Test
  fun connectionDoesNotReceiveAlreadyReceivedEvents() = runBlocking<Unit> {
    val firstEventData = byteArrayOf(0x12, 0x15)
    val secondEventData = byteArrayOf(0x01, 0x02)

    // This test seems more complicated than it needs to be because we want to force the two events to be polled in separate cycles. We want
    // to check the subsequence polling cycle does not pick up the events already seen in the first cycle. Therefore, we use a flow here to
    // receive the first event before adding the second event to the service.
    val flow = callbackFlow {
      var count = 0
      val listener = object : AppInspectionServiceRule.TestInspectorRawEventListener() {
        override fun onRawEvent(eventData: ByteArray) {
          super.onRawEvent(eventData)
          count++
          offer(eventData)
          if (count == 2) {
            close()
          }
        }
      }

      appInspectionRule.launchInspectorConnection(eventListener = listener)

      appInspectionRule.addAppInspectionEvent(createRawAppInspectionEvent(firstEventData))

      awaitClose()
    }

    flow.withIndex().collect { indexedValue ->
      if (indexedValue.index == 0) {
        assertThat(indexedValue.value).isEqualTo(firstEventData)
        appInspectionRule.addAppInspectionEvent(createRawAppInspectionEvent(secondEventData))
      } else {
        assertThat(indexedValue.value).isEqualTo(secondEventData)
      }
    }
  }

  @ExperimentalCoroutinesApi
  @Test
  fun cancelRawCommandSendsCancellationCommand() = runBlocking<Unit> {
    val client = appInspectionRule.launchInspectorConnection(inspectorId = INSPECTOR_ID)

    val cancelledDeferred = CompletableDeferred<Unit>()
    var toBeCancelledCommandId: Int? = null
    var cancelledCommandId: Int? = null

    // Override App Inspection command handler to not respond to any commands, so the test can have control over timing of events.
    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, object : CommandHandler(timer) {
      override fun handleCommand(command: Commands.Command, events: MutableList<Event>) {
        if (command.appInspectionCommand.hasRawInspectorCommand()) {
          toBeCancelledCommandId = command.appInspectionCommand.commandId
        } else if (command.appInspectionCommand.hasCancellationCommand()) {
          cancelledCommandId = command.appInspectionCommand.cancellationCommand.cancelledCommandId
          cancelledDeferred.complete(Unit)
        }
      }
    })

    // start = CoroutineStart.UNDISPATCHED because we want the job to execute immediately. Otherwise, it may get cancelled without hitting
    // the target suspension point.
    val job = launch(start = CoroutineStart.UNDISPATCHED) {
      client.messenger.sendRawCommand(ByteString.copyFromUtf8("Blah").toByteArray())
    }
    job.cancel()

    cancelledDeferred.await()

    assertThat(toBeCancelledCommandId).isNotNull()
    assertThat(toBeCancelledCommandId).isEqualTo(cancelledCommandId)
  }
}