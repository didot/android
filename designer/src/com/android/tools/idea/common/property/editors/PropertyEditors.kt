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
package com.android.tools.idea.common.property.editors

import com.android.tools.idea.common.property.NlProperty
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager

/**
 * Facility for providing [NlComponentEditor]s for [NlProperty]s.
 */
abstract class PropertyEditors : LafManagerListener {
  init {
    val app = ApplicationManager.getApplication()
    if (!app.isHeadlessEnvironment) {
      @Suppress("LeakingThis")
      app.messageBus.connect().subscribe(LafManagerListener.TOPIC, this)
    }
  }

  protected abstract fun resetCachedEditors()

  abstract fun create(property: NlProperty): NlComponentEditor

  override fun lookAndFeelChanged(source: LafManager) = resetCachedEditors()
}