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
package com.android.tools.idea.appinspection.transport

import com.android.tools.app.inspection.AppInspection
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.poller.TransportEventListener
import com.android.tools.idea.transport.poller.TransportEventPoller
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport
import com.google.common.annotations.VisibleForTesting
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Small helper class to work with the one exact process and app-inspection events & commands.
 */
@VisibleForTesting
class AppInspectionTransport(
  val client: TransportClient,
  private val stream: Common.Stream,
  private val process: Common.Process,
  val executorService: ExecutorService,
  val poller: TransportEventPoller = TransportEventPoller.createPoller(client.transportStub, TimeUnit.MILLISECONDS.toNanos(100))
) {

  companion object {
    private val commandIdGenerator = AtomicInteger(1)

    /**
     * A method which generates a new unique ID each time, to be assigned to an outgoing inspector command.
     *
     * This ID is used to map events from the agent to the correct handler. This method is thread-safe.
     */
    fun generateNextCommandId() = commandIdGenerator.getAndIncrement()

    /**
     * The last value generated by calling [generateNextCommandId]
     *
     * This method is thread-safe.
     */
    @VisibleForTesting
    fun lastGeneratedCommandId() = commandIdGenerator.get() - 1
  }

  fun createEventListener(
    filter: (Common.Event) -> Boolean = { true },
    eventKind: Common.Event.Kind = Common.Event.Kind.APP_INSPECTION,
    callback: (Common.Event) -> Boolean
  ) = TransportEventListener(eventKind = eventKind,
                             executor = executorService,
                             streamId = stream::getStreamId,
                             filter = filter,
                             processId = process::getPid,
                             callback = callback)

  fun registerEventListener(
    filter: (Common.Event) -> Boolean = { true },
    eventKind: Common.Event.Kind = Common.Event.Kind.APP_INSPECTION,
    callback: (Common.Event) -> Boolean
  ): TransportEventListener {
    val listener = createEventListener(filter, eventKind, callback)
    poller.registerListener(listener)
    return listener
  }

  fun executeCommand(appInspectionCommand: AppInspection.AppInspectionCommand): Int {
    val command = Commands.Command.newBuilder()
      .setType(Commands.Command.CommandType.APP_INSPECTION)
      .setStreamId(stream.streamId)
      .setPid(process.pid)
      .setAppInspectionCommand(appInspectionCommand.toBuilder().setCommandId(generateNextCommandId()).build())
    executorService.submit{ client.transportStub.execute(Transport.ExecuteRequest.newBuilder().setCommand(command).build()) }
    return command.appInspectionCommand.commandId
  }
}