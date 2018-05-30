/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.ui

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import org.jetbrains.ide.PooledThreadExecutor

internal fun <I, O> ListenableFuture<I>.continueOnEdt(continuation: (I) -> O) =
  Futures.transform(
    this, { continuation(it!!) },
    {
      val application = ApplicationManager.getApplication()
      if (application.isDispatchThread) {
        it.run()
      }
      else {
        application.invokeLater(it, ModalityState.any())
      }
    })

internal fun <I, O> ListenableFuture<I>.invokeLater(continuation: (I) -> O) =
  Futures.transform(
    this, { continuation(it!!) },
    {
      val application = ApplicationManager.getApplication()
      if (application.isUnitTestMode) {
        it.run()
      }
      else {
        application.invokeLater(it, ModalityState.any())
      }
    })

internal fun <T> readOnPooledThread(block: () -> T): ListenableFuture<T> =
  MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE).submit<T> { ReadAction.compute<T, Throwable>(block) }