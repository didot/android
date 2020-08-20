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
package com.android.tools.idea.appinspection.inspectors.workmanager.model

import androidx.work.inspection.WorkManagerInspectorProtocol
import androidx.work.inspection.WorkManagerInspectorProtocol.Command
import androidx.work.inspection.WorkManagerInspectorProtocol.Event
import androidx.work.inspection.WorkManagerInspectorProtocol.TrackWorkManagerCommand
import androidx.work.inspection.WorkManagerInspectorProtocol.WorkInfo
import androidx.work.inspection.WorkManagerInspectorProtocol.WorkUpdatedEvent
import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.jcip.annotations.GuardedBy
import net.jcip.annotations.ThreadSafe

/**
 * Class used to send commands to and handle events from the on-device work manager inspector through its [client].
 */
@ThreadSafe
class WorkManagerInspectorClient(private val client: AppInspectorClient, private val clientScope: CoroutineScope) {
  companion object {
    private val logger: Logger = Logger.getInstance(WorkManagerInspectorClient::class.java)
  }

  private val lock = Any()

  @GuardedBy("lock")
  private val works = mutableListOf<WorkInfo>()

  private val _worksChangedListeners = mutableListOf<() -> Unit>()
  fun addWorksChangedListener(listener: () -> Unit) = _worksChangedListeners.add(listener)

  init {
    val command = Command.newBuilder().setTrackWorkManager(TrackWorkManagerCommand.getDefaultInstance()).build()
    clientScope.launch {
      client.sendRawCommand(command.toByteArray())
    }
    clientScope.launch {
      client.rawEventFlow.collect { eventData ->
        handleEvent(eventData)
      }
    }
  }

  fun getWorkInfoCount() = synchronized(lock) {
    works.size
  }

  /**
   * Returns a [WorkInfo] at the given [index] or `null` if the [index] is out of bounds of this list.
   */
  fun getWorkInfoOrNull(index: Int) = synchronized(lock) {
    works.getOrNull(index)
  }

  /**
   * Returns index of the first [WorkInfo] matching the given [predicate], or -1 if the list does not contain such element.
   */
  fun indexOfFirstWorkInfo(predicate: (WorkInfo) -> Boolean) = synchronized(lock) {
    works.indexOfFirst(predicate)
  }

  fun getWorkIdsWithUniqueName(uniqueName: String): List<String> = synchronized(lock) {
    works.filter { it.namesList.contains(uniqueName) }.map { it.id }.toList()
  }

  fun cancelWorkById(id: String) {
    val cancelCommand = WorkManagerInspectorProtocol.CancelWorkCommand.newBuilder().setId(id).build()
    val command = Command.newBuilder().setCancelWork(cancelCommand).build()
    clientScope.launch {
      client.sendRawCommand(command.toByteArray())
    }
  }

  private fun handleEvent(eventBytes: ByteArray) = synchronized(lock) {
    val event = Event.parseFrom(eventBytes)
    when (event.oneOfCase!!) {
      Event.OneOfCase.WORK_ADDED -> {
        works.add(event.workAdded.work)
      }
      Event.OneOfCase.WORK_REMOVED -> {
        works.removeAll {
          it.id == event.workRemoved.id
        }
      }
      Event.OneOfCase.WORK_UPDATED -> {
        val updateWorkEvent = event.workUpdated
        val index = works.indexOfFirst { it.id == updateWorkEvent.id }
        if (index != -1) {
          val newWork = works[index].toBuilder()
          when (updateWorkEvent.oneOfCase!!) {
            WorkUpdatedEvent.OneOfCase.STATE -> newWork.state = updateWorkEvent.state
            WorkUpdatedEvent.OneOfCase.DATA -> newWork.data = updateWorkEvent.data
            WorkUpdatedEvent.OneOfCase.RUN_ATTEMPT_COUNT -> {
              newWork.runAttemptCount = updateWorkEvent.runAttemptCount
            }
            WorkUpdatedEvent.OneOfCase.SCHEDULE_REQUESTED_AT -> {
              newWork.scheduleRequestedAt = updateWorkEvent.scheduleRequestedAt
            }
            WorkUpdatedEvent.OneOfCase.ONEOF_NOT_SET -> {
              logger.warn("Empty WorKUpdatedEvent")
            }
          }
          works[index] = newWork.build()
        }
      }
      Event.OneOfCase.ONEOF_NOT_SET -> {
        logger.warn("Empty Event")
      }
    }
    _worksChangedListeners.forEach { listener -> listener() }
  }
}
