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
package com.android.tools.adtui.testing

import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.intellij.ide.util.PropertiesComponent
import com.intellij.mock.MockApplication
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.registerServiceInstance
import org.junit.rules.ExternalResource

/**
 * An Application rule that sets up a test [com.intellij.openapi.application.Application].
 *
 * The application instance is based on [FakeActionManager] which is much lighter and faster
 * than using [com.intellij.idea.IdeaTestApplication].
 */
class ApplicationRule : ExternalResource() {

  private var rootDisposable: Disposable? = Disposer.newDisposable()
  private var application: MockApplication? = MockApplication(rootDisposable!!)

  val testRootDisposable: Disposable
    get() = rootDisposable!!

  val testApplication: MockApplication
    get() = application!!

  companion object {
    init {
      Logger.setFactory(TestLoggerFactory::class.java)
    }
  }

  /**
   * Setup a test Application instance with a few common services needed for property tests.
   */
  override fun before() {
    application!!.registerServiceInstance(PropertiesComponent::class.java, PropertiesComponentMock())
    application!!.registerServiceInstance(ActionManager::class.java, FakeActionManager())
    ApplicationManager.setApplication(application!!, rootDisposable!!)
  }

  override fun after() {
    Disposer.dispose(rootDisposable!!)
    rootDisposable = null
    application = null
    resetApplication()
  }

  /**
   * Null out the static reference in [ApplicationManager].
   *
   * Keeping a reference to a disposed object can cause problems for other tests.
   */
  private fun resetApplication() {
    val field = ApplicationManager::class.java.getDeclaredField("ourApplication")
    field.isAccessible = true
    field.set(null, null)
  }
}
