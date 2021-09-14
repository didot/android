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
package com.android.tools.idea.testing

import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.concurrency.AndroidIoManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.mockito.Mockito.`when`
import org.mockito.Mockito.spy
import java.util.concurrent.Executor

/**
 * A rule that allows testing code that depends on [AndroidExecutors].
 *
 * Registers AndroidExecutors with its dependency [AndroidIoManager].
 *
 * This rule also allows tests to inject custom executors by passing optional replacements in the constructor.
 */
class AndroidExecutorsRule(
  private val workerThreadExecutor: Executor? = null,
  private val ioThreadExecutor: Executor? = null,
  private val uiThreadExecutor: ((ModalityState, Runnable) -> Unit)? = null,
) : TestRule {
  override fun apply(base: Statement, description: Description): Statement {
    return object : Statement() {
      override fun evaluate() {
        TempDisposable().use {
          val application = ApplicationManager.getApplication()
          application.registerServiceInstance(AndroidIoManager::class.java, AndroidIoManager(), it)
          val androidExecutors = spy(AndroidExecutors())
          if (workerThreadExecutor != null) {
            `when`(androidExecutors.workerThreadExecutor).thenReturn(workerThreadExecutor)
          }
          if (ioThreadExecutor != null) {
            `when`(androidExecutors.ioThreadExecutor).thenReturn(ioThreadExecutor)
          }
          if (uiThreadExecutor != null) {
            `when`(androidExecutors.uiThreadExecutor).thenReturn(uiThreadExecutor)
          }
          application.registerServiceInstance(AndroidExecutors::class.java, androidExecutors, it)
          base.evaluate()
        }
      }
    }
  }
}