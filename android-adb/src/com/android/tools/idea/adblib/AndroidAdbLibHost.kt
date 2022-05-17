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

import com.android.adblib.AdbLibHost
import com.android.adblib.AdbLoggerFactory
import com.android.tools.idea.concurrency.AndroidDispatchers
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Implementation of [AdbLibHost] that integrates with the IntelliJ/Android Studio platform.
 *
 * See also [AndroidAdbLoggerFactory] and [AndroidDispatchers].
 */
internal class AndroidAdbLibHost : AdbLibHost() {
  override val loggerFactory: AdbLoggerFactory by lazy {
    AndroidAdbLoggerFactory()
  }

  override val ioDispatcher: CoroutineDispatcher
    get() = AndroidDispatchers.ioThread

  override fun close() {
    // Nothing to do
  }
}