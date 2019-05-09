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
package com.android.tools.idea.layoutinspector.transport

import com.android.tools.idea.layoutinspector.LayoutInspectorPreferredProcess
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.LayoutInspectorCommand
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.LayoutInspectorEvent
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.Event.EventGroupIds
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.project.Project

/**
 * Client for communicating with the agent.
 */
interface InspectorClient {
  /**
   * Register a handler for a specific groupId.
   */
  fun register(groupId: EventGroupIds, callback: (LayoutInspectorEvent) -> Unit)

  /**
   * Register a handler for when the current process ends.
   */
  fun registerProcessEnded(callback: () -> Unit)

  /**
   * Find all processes that the inspector can attach to.
   */
  fun loadProcesses(): Map<Common.Stream, List<Common.Process>>

  /**
   * Attach to a preferred process.
   */
  fun attach(preferredProcess: LayoutInspectorPreferredProcess)

  /**
   * Attach to a specific process.
   */
  fun attach(stream: Common.Stream, process: Common.Process)

  /**
   * Send a command to the agent.
   */
  fun execute(command: LayoutInspectorCommand)

  /**
   * Send simple command to agent without arguments.
   */
  fun execute(commandType: LayoutInspectorCommand.Type) {
    execute(LayoutInspectorCommand.newBuilder().setType(commandType).build())
  }

  /**
   * Fetch the payload from a given payload [id].
   */
  fun getPayload(id: Int): ByteArray

  /**
   * True, if a connection to a device is currently open.
   */
  val isConnected: Boolean

  /**
   * True, if the current connection is currently receiving live updates.
   */
  val isCapturing: Boolean

  companion object {

    fun createOrGetDefaultInstance(project: Project): InspectorClient {
      val client = currentInstance ?: DefaultInspectorClient(project)
      currentInstance = client
      return client
    }

    @VisibleForTesting
    internal var currentInstance: InspectorClient? = null
  }
}
