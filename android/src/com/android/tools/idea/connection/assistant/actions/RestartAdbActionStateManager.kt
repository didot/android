/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.connection.assistant.actions

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.assistant.AssistActionState
import com.android.tools.idea.assistant.AssistActionStateManager
import com.android.tools.idea.assistant.datamodel.ActionData
import com.android.tools.idea.assistant.datamodel.DefaultActionState
import com.android.tools.idea.assistant.view.StatefulButtonMessage
import com.android.tools.idea.concurrent.EdtExecutor
import com.android.utils.HtmlBuilder
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.project.Project
import org.jetbrains.android.sdk.AndroidSdkUtils
import org.jetbrains.android.util.AndroidBundle

/**
 * StateManager for RestartAdbAction, displays if there are any connected devices to the user through the
 * state message.
 */
class RestartAdbActionStateManager : AssistActionStateManager(), AndroidDebugBridge.IDebugBridgeChangeListener {
  private lateinit var myProject: Project
  private var myAdbFuture: ListenableFuture<AndroidDebugBridge>? = null
  private var myLoading: Boolean = false

  private fun generateMessage(devices: Array<IDevice>): String {
    if (devices.isEmpty()) {
      return AndroidBundle.message("connection.assistant.adb.no_devices")
    }
    else {
      val builder = HtmlBuilder() // skip open and close htmlbody because the StatefulButtonMessage will add it instead
      builder
          .addHtml(AndroidBundle.message("connection.assistant.adb.devices"))
      for (device in devices) {
        builder.addHtml("<p><span style=\"font-size: 150%;\">" + device.name + "</span>")
            .newline()
            .addHtml("<span style=\"font-size: 80%; font-weight:lighter;\">" + device.version.toString() + "</span></p>")
            .newline()
      }
      return builder.html
    }
  }

  override fun getId(): String {
    return RestartAdbAction.ACTION_ID
  }

  override fun init(project: Project, actionData: ActionData) {
    myProject = project
    initDebugBridge(myProject)
    AndroidDebugBridge.addDebugBridgeChangeListener(this)
  }

  override fun getState(project: Project, actionData: ActionData): AssistActionState {
    if (myLoading) return DefaultActionState.IN_PROGRESS
    if (myAdbFuture == null) return DefaultActionState.INCOMPLETE
    if (!myAdbFuture!!.isDone) return DefaultActionState.IN_PROGRESS

    val adb = AndroidDebugBridge.getBridge()
    if (adb == null || adb.devices.isEmpty()) {
      return DefaultActionState.ERROR_RETRY
    }
    return DefaultActionState.PARTIALLY_COMPLETE
  }

  override fun getStateDisplay(project: Project, actionData: ActionData, message: String?): StatefulButtonMessage {
    var returnMessage = message ?: ""
    val state = getState(project, actionData)
    when (state) {
      DefaultActionState.IN_PROGRESS -> returnMessage = AndroidBundle.message("connection.assistant.loading")
      DefaultActionState.PARTIALLY_COMPLETE, DefaultActionState.ERROR_RETRY -> {
        val adb = AndroidDebugBridge.getBridge()
        if (adb != null) {
          returnMessage = generateMessage(adb.devices)
        }
        else {
          returnMessage = AndroidBundle.message("connection.assistant.adb.failure")
        }
      }
    }

    return StatefulButtonMessage(returnMessage, state)
  }

  private fun setLoading(loading: Boolean) {
    myLoading = loading
    refreshDependencyState(myProject)
  }

  private fun initDebugBridge(project: Project) {
    val adb = AndroidSdkUtils.getAdb(project) ?: return
    myAdbFuture = AdbService.getInstance().getDebugBridge(adb)
    if (myAdbFuture == null) return
    Futures.addCallback(myAdbFuture, object : FutureCallback<AndroidDebugBridge> {
      override fun onSuccess(bridge: AndroidDebugBridge?) {
        refreshDependencyState(project)
      }

      override fun onFailure(t: Throwable?) {
        refreshDependencyState(project)
      }
    }, EdtExecutor.INSTANCE)
  }

  override fun bridgeChanged(bridge: AndroidDebugBridge?) {}

  override fun restartInitiated() {
    setLoading(true)
  }

  override fun restartCompleted(isSuccessful: Boolean) {
    setLoading(false)
  }
}