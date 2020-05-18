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
package com.android.tools.idea.transport.poller


import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.pipeline.example.proto.Echo
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import junit.framework.TestCase.fail
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.util.ArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val TIMEOUT_SECONDS: Long = 10

class TransportEventPollerTest {

  private var timer = FakeTimer()
  private var transportService = FakeTransportService(timer, true)
  private var transportEventPoller: TransportEventPoller? = null

  @get:Rule
  val grpcServer = FakeGrpcServer.createFakeGrpcServer("TransportEventPollerTestChannel", transportService, transportService)!!

  @After
  fun close() {
    transportEventPoller?.let { TransportEventPoller.stopPoller(it) }
    transportEventPoller = null
  }

  private fun generateEchoEvent(ts: Long) = Common.Event.newBuilder()
    .setTimestamp(ts)
    .setKind(Common.Event.Kind.ECHO)
    .build()

  /**
   * Tests that a newly created listener with already-connected device+process
   * will receive the stream connected and process started events
   */
  @Test
  fun testStreamAndProcessListeners() {
    val transportClient = TransportClient(grpcServer.name)
    val latch = CountDownLatch(2)
    transportEventPoller = TransportEventPoller.createPoller(
      transportClient.transportStub,
      TimeUnit.MILLISECONDS.toNanos(250))

    // Create listener for STREAM connected
    val streamConnectedListener = TransportEventListener(
      eventKind = Common.Event.Kind.STREAM,
      callback = { event ->
        assertThat(event.stream.streamConnected.stream.streamId).isEqualTo(FakeTransportService.FAKE_DEVICE_ID)
        latch.countDown()
        false
      },
      executor = MoreExecutors.directExecutor(),
      filter = { event -> event.stream.hasStreamConnected() })
    transportEventPoller!!.registerListener(streamConnectedListener)

    // Create listener for PROCESS started
    val processStartedListener = TransportEventListener(
      eventKind = Common.Event.Kind.PROCESS,
      callback = { event ->
        assertThat(event.process.processStarted.process.pid).isEqualTo(1)
        assertThat(event.process.processStarted.process.deviceId).isEqualTo(FakeTransportService.FAKE_DEVICE_ID)
        latch.countDown()
        false
      }, executor = MoreExecutors.directExecutor(),
      filter = { event -> event.process.hasProcessStarted() })
    transportEventPoller!!.registerListener(processStartedListener)

    // Receive
    try {
      assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo(true)
    }
    catch (e: InterruptedException) {
      e.printStackTrace()
      fail("Test interrupted")
    }
  }

  /**
   * Tests that listener receives events from both before and after it was created
   */
  @Test
  fun testEventListeners() {
    val transportClient = TransportClient(grpcServer.name)
    val eventLatch = CountDownLatch(3)
    val waitLatch = CountDownLatch(1)
    transportEventPoller = TransportEventPoller.createPoller(
      transportClient.transportStub,
      TimeUnit.MILLISECONDS.toNanos(250))
    val expectedEvents = ArrayList<Common.Event>()

    // First event exists before listener is registered
    val echoEvent = Common.Event.newBuilder()
      .setTimestamp(0)
      .setKind(Common.Event.Kind.ECHO)
      .setGroupId(123)
      .build()
    expectedEvents.add(echoEvent)
    transportService.addEventToStream(FakeTransportService.FAKE_DEVICE_ID, echoEvent)

    // Create listener for ECHO event
    val echoListener = TransportEventListener(eventKind = Common.Event.Kind.ECHO,
                                              callback = { event ->
                                                assertThat(event).isEqualTo(expectedEvents.removeAt(0))
                                                waitLatch.countDown()
                                                eventLatch.countDown()
                                                false
                                              },
                                              executor = MoreExecutors.directExecutor())
    transportEventPoller!!.registerListener(echoListener)

    // Wait for the first event to be received
    try {
      assertThat(waitLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo(true)
    }
    catch (e: InterruptedException) {
      e.printStackTrace()
      fail("Test interrupted")
    }

    // Second event created after first is received
    val echoEvent2 = Common.Event.newBuilder()
      .setTimestamp(3)
      .setKind(Common.Event.Kind.ECHO)
      .setGroupId(456)
      .build()
    expectedEvents.add(echoEvent2)
    transportService.addEventToStream(FakeTransportService.FAKE_DEVICE_ID, echoEvent2)

    // Third event with the same group ID
    val echoEvent3 = Common.Event.newBuilder()
      .setTimestamp(3)
      .setKind(Common.Event.Kind.ECHO)
      .setGroupId(456)
      .build()
    expectedEvents.add(echoEvent3)
    transportService.addEventToStream(FakeTransportService.FAKE_DEVICE_ID, echoEvent3)

    // Receive the last 2 events
    try {
      assertThat(eventLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo(true)
    }
    catch (e: InterruptedException) {
      e.printStackTrace()
      fail("Test interrupted")
    }
  }

  /**
   * Tests that a registered listener is removed when the callback returns true;
   */
  @Test
  fun testRemoveEventListener() {
    val transportClient = TransportClient(grpcServer.name)
    transportEventPoller = TransportEventPoller.createPoller(
      transportClient.transportStub,
      TimeUnit.MILLISECONDS.toNanos(250)
    )

    // First event exists before listener is registered
    val echoEvents = mutableListOf<Common.Event>()
    echoEvents.add(generateEchoEvent(0))
    transportService.addEventToStream(FakeTransportService.FAKE_DEVICE_ID, echoEvents[0])

    // Create listener for ECHO event that should remove itself after 3 callbacks.
    val eventLatch1 = CountDownLatch(3)
    var receivedEventsCount1 = 0
    val echoListener1 = TransportEventListener(
      eventKind = Common.Event.Kind.ECHO,
      startTime = { 0L },
      endTime = { 3L },
      callback = { event ->
        assertThat(event).isEqualTo(echoEvents[receivedEventsCount1])
        // Update count before countDown() which could trigger await() immediately
        receivedEventsCount1++
        eventLatch1.countDown()
        eventLatch1.count == 0L
      },
      executor = MoreExecutors.directExecutor()
    )
    val eventLatch2 = CountDownLatch(5)
    var receivedEventsCount2 = 0
    val echoListener2 = TransportEventListener(
      eventKind = Common.Event.Kind.ECHO,
      startTime = { 0L },
      endTime = { 5L },
      callback = { event ->
        assertThat(event).isEqualTo(echoEvents[receivedEventsCount2])
        // Update count before countDown() which could trigger await() immediately
        receivedEventsCount2++
        eventLatch2.countDown()
        echoEvents.add(generateEchoEvent(receivedEventsCount2.toLong()))
        transportService.addEventToStream(FakeTransportService.FAKE_DEVICE_ID, echoEvents[receivedEventsCount2])
        false
      },
      executor = MoreExecutors.directExecutor()
    )
    transportEventPoller!!.registerListener(echoListener1)
    transportEventPoller!!.registerListener(echoListener2)

    // Wait until both latches are done.
    try {
      eventLatch1.await()
      eventLatch2.await()
    } catch (e: InterruptedException) {
      e.printStackTrace()
      fail("Test interrupted")
    }

    // we should have stopped triggering the callback after 3 counts.
    assertThat(receivedEventsCount1).isEqualTo(3)
    assertThat(receivedEventsCount2).isEqualTo(5)
  }

  @Test
  fun pollerTracksEventListenerTimestamp() {
    val transportClient = TransportClient(grpcServer.name)
    transportEventPoller = TransportEventPoller.createPoller(
      transportClient.transportStub,
      TimeUnit.MILLISECONDS.toNanos(250)
    )

    val latch1 = CountDownLatch(1)
    val latch2 = CountDownLatch(1)
    var eventsSeen = 0
    TransportEventListener(
      eventKind = Common.Event.Kind.ECHO,
      startTime = { 0L },
      callback = {
        if (it.timestamp == 10L) {
          eventsSeen ++
          latch1.countDown()
        } else if (it.timestamp == 20L) {
          eventsSeen ++
          latch2.countDown()
        }
        false
      },
      executor = MoreExecutors.directExecutor()
    ).also { transportEventPoller!!.registerListener(it) }

    // Add event with timestamp 10
    transportService.addEventToStream(FakeTransportService.FAKE_DEVICE_ID, generateEchoEvent(10))
    latch1.await()

    // Add event with timestamp 5. This shouldn't be picked up by poller because last seen event was ts=10.
    transportService.addEventToStream(FakeTransportService.FAKE_DEVICE_ID, generateEchoEvent(5))

    // Add event with timestamp 20
    transportService.addEventToStream(FakeTransportService.FAKE_DEVICE_ID, generateEchoEvent(20))
    latch2.await()

    assertThat(eventsSeen).isEqualTo(2)
  }

  /**
   * Tests that listeners receive the right events
   */
  private fun checkNonCustomFilter(
    eventKind: Common.Event.Kind? = null,
    groupId: Long? = null,
    processId: Int? = null
  ) {
    val transportClient = TransportClient(grpcServer.name)
    val positiveLatch = CountDownLatch(2)
    val negativeLatch = CountDownLatch(2)

    transportEventPoller = TransportEventPoller.createPoller(
      transportClient.transportStub,
      TimeUnit.MILLISECONDS.toNanos(250))

    val otherEventKind = if (eventKind != null) {
      // get the next kind, but skip 0 (so wrap one place early and then add one after)
      val nextKindId = eventKind.ordinal.rem(Common.Event.Kind.values().size - 1) + 1
      Common.Event.Kind.values()[nextKindId]
    }
    else {
      Common.Event.Kind.ECHO
    }
    val otherGroupId = groupId?.let { it + 1 } ?: 0
    val otherProcessId = processId?.let { it + 1 } ?: 0

    val realEventKind = eventKind ?: Common.Event.Kind.ECHO
    val realGroupId = groupId ?: 0
    val realProcessId = processId ?: 0

    val positiveEventListener = TransportEventListener(
      streamId = { 1 },
      eventKind = realEventKind,
      groupId = { realGroupId },
      processId = { realProcessId },
      callback = { event ->
        assertThat(event.pid).isEqualTo(realProcessId)
        assertThat(event.groupId).isEqualTo(realGroupId)
        assertThat(event.kind).isEqualTo(realEventKind)
        positiveLatch.countDown()
        false
      },
      executor = MoreExecutors.directExecutor())
    transportEventPoller!!.registerListener(positiveEventListener)

    val negativeEventListener = TransportEventListener(
      streamId = { 1 },
      eventKind = otherEventKind,
      groupId = { otherGroupId },
      processId = { otherProcessId },
      callback = { event ->
        assertThat(event.pid).isEqualTo(otherProcessId)
        assertThat(event.groupId).isEqualTo(otherGroupId)
        assertThat(event.kind).isEqualTo(otherEventKind)
        negativeLatch.countDown()
        false
      },
      executor = MoreExecutors.directExecutor())
    transportEventPoller!!.registerListener(negativeEventListener)

    val positiveEvent1Builder =
      Common.Event.newBuilder()
        .setTimestamp(1)
        .setKind(realEventKind)
        .setGroupId(realGroupId)
        .setPid(realProcessId)

    val negativeEvent1Builder = Common.Event.newBuilder()
      .setTimestamp(2)
      .setKind(otherEventKind)
      .setGroupId(otherGroupId)
      .setPid(otherProcessId)

    val positiveEvent2Builder = Common.Event.newBuilder()
      .setTimestamp(3)
      .setKind(realEventKind)
      .setGroupId(realGroupId)
      .setPid(realProcessId)

    val negativeEvent2Builder = Common.Event.newBuilder()
      .setTimestamp(4)
      .setKind(otherEventKind)
      .setGroupId(otherGroupId)
      .setPid(otherProcessId)

    transportService.addEventToStream(1L, positiveEvent1Builder.build())
    transportService.addEventToStream(1L, negativeEvent1Builder.build())
    transportService.addEventToStream(1L, positiveEvent2Builder.build())
    transportService.addEventToStream(1L, negativeEvent2Builder.build())

    assertThat(positiveLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo(true)
    assertThat(negativeLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo(true)
  }

  @Test
  fun testKindFilter() {
    checkNonCustomFilter(eventKind = Common.Event.Kind.CPU_USAGE)
  }

  @Test
  fun testProcessFilter() {
    checkNonCustomFilter(processId = 123)
  }

  @Test
  fun testGroupFilter() {
    checkNonCustomFilter(groupId = 321L)
  }

  @Test
  fun testAllFilters() {
    checkNonCustomFilter(eventKind = Common.Event.Kind.CPU_USAGE, processId = 123, groupId = 321L)
  }

  @Test
  fun testCustomFilter() {

    val transportClient = TransportClient(grpcServer.name)
    val latch = CountDownLatch(2)

    transportEventPoller = TransportEventPoller.createPoller(
      transportClient.transportStub,
      TimeUnit.MILLISECONDS.toNanos(250))

    val positiveEventListener = TransportEventListener(
      eventKind = Common.Event.Kind.ECHO,
      streamId = { 1 },
      filter = { event -> event.echo.data == "blah" },
      callback = { event ->
        assertThat(event.echo.data).isEqualTo("blah")
        latch.countDown()
        false
      },
      executor = MoreExecutors.directExecutor())
    transportEventPoller!!.registerListener(positiveEventListener)

    val positiveEvent1Builder =
      Common.Event.newBuilder()
        .setTimestamp(1)
        .setKind(Common.Event.Kind.ECHO)
        .setEcho(Echo.EchoData.newBuilder().setData("blah"))

    val negativeEvent1Builder = Common.Event.newBuilder()
      .setTimestamp(2)
      .setKind(Common.Event.Kind.ECHO)
      .setEcho(Echo.EchoData.newBuilder().setData("foo"))

    val negativeEvent2Builder = Common.Event.newBuilder()
      .setTimestamp(3)
      .setKind(Common.Event.Kind.ECHO)

    val positiveEvent2Builder = Common.Event.newBuilder()
      .setTimestamp(4)
      .setKind(Common.Event.Kind.ECHO)
      .setEcho(Echo.EchoData.newBuilder().setData("blah"))


    transportService.addEventToStream(1L, positiveEvent1Builder.build())
    transportService.addEventToStream(1L, negativeEvent1Builder.build())
    transportService.addEventToStream(1L, negativeEvent2Builder.build())
    transportService.addEventToStream(1L, positiveEvent2Builder.build())

    assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo(true)
  }

  @Test
  fun testLastTimestampNotRecordedIfListenerIsNotRegistered() {
    val transportClient = TransportClient(grpcServer.name)
    transportEventPoller = TransportEventPoller.createPoller(transportClient.transportStub, TimeUnit.MILLISECONDS.toNanos(250))
    var latch = CountDownLatch(1)
    var runnable = Runnable {}
    val events = mutableListOf<Common.Event>()
    val listener = TransportEventListener(
      eventKind = Common.Event.Kind.ECHO,
      executor = MoreExecutors.directExecutor(),
      callback = {
        events.add(it)
        runnable.run()
        latch.countDown()
        false
      })
    // Simulate that the listener is being unregistered during a poll:
    runnable = Runnable { transportEventPoller!!.unregisterListener(listener) }

    val event1 = Common.Event.newBuilder().apply {
      timestamp = 4
      kind = Common.Event.Kind.ECHO
      echo = Echo.EchoData.newBuilder().apply { data = "blah" }.build()
    }.build()
    transportService.addEventToStream(1L, event1)

    // Register the listener and simulate an event from a device at time = 4
    transportEventPoller!!.registerListener(listener)

    assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo(true)
    assertThat(events.size).isEqualTo(1)
    assertThat(events[0]).isEqualTo(event1)

    // Register the same listener and simulate an event from a different device at time = 1
    events.clear()
    latch = CountDownLatch(2)
    runnable = Runnable {}
    val event2 = Common.Event.newBuilder().apply {
      timestamp = 1
      kind = Common.Event.Kind.ECHO
      echo = Echo.EchoData.newBuilder().apply { data = "doh" }.build()
    }.build()
    transportService.addEventToStream(1L, event2)
    transportEventPoller!!.registerListener(listener)

    assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isEqualTo(true)
    assertThat(events.size).isEqualTo(2)
    assertThat(events[0]).isEqualTo(event2)
    assertThat(events[1]).isEqualTo(event1)
  }
}
