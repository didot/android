/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.adblib

import com.android.adblib.AdbChannelProviderFactory
import com.android.adblib.AdbLibSession
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.DdmPreferences
import com.android.tools.idea.adb.AdbFileProvider
import com.android.tools.idea.adb.AdbService
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.time.Duration

/**
 * [Project] service that provides access to the corresponding [AdbLibSession] for that project.
 *
 * Example: `AdbLibService.getInstance(project).hostServices`
 *
 * If a [Project] instance is not available, use [AdbLibApplicationService] instead, but it is then
 * the caller's responsibility to manage [AndroidDebugBridge] initialization.
 */
@Service
class AdbLibService(val project: Project) : Disposable {
  private val host
    get() = AdbLibApplicationService.instance.session.host // re-use host from application service

  private val channelProvider = AdbChannelProviderFactory.createConnectAddresses(host) {
    listOf(getAdbSocketAddress())
  }

  val session: AdbLibSession = AdbLibSession.create(
    host = host,
    channelProvider = channelProvider,
    connectionTimeout = Duration.ofMillis(DdmPreferences.getTimeOut ().toLong())
  )

  override fun dispose() {
    session.close()
  }

  private suspend fun getAdbSocketAddress(): InetSocketAddress {
    return withContext(host.ioDispatcher) {
      // Ensure ddmlib is initialized with path to ADB server from current project
      val adbFile = AdbFileProvider.fromProject(project)?.adbFile
                    ?: throw IllegalStateException("ADB has not been initialized for this project")
      AdbService.getInstance().getDebugBridge(adbFile).await()

      AndroidDebugBridge.getSocketAddress()
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<AdbLibService>()

    @JvmStatic
    fun getSession(project: Project) = getInstance(project).session
  }
}
