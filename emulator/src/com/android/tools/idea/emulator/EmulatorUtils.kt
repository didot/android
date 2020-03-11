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
package com.android.tools.idea.emulator

import com.android.annotations.concurrency.UiThread
import com.android.emulator.control.KeyboardEvent
import com.android.emulator.control.KeyboardEvent.KeyEventType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState

/**
 * Invokes given function on the UI thread regardless of the modality state.
 */
fun invokeLater(@UiThread action: () -> Unit) {
  ApplicationManager.getApplication().invokeLater(action, ModalityState.any())
}

/**
 * Creates a [KeyboardEvent] for the given hardware key.
 * Key names are defined in https://developer.mozilla.org/en-US/docs/Web/API/KeyboardEvent/key/Key_Values.
 */
fun createHardwareKeyEvent(keyName: String, eventType: KeyEventType = KeyEventType.keypress): KeyboardEvent {
  return KeyboardEvent.newBuilder()
    .setKey(keyName)
    .setEventType(eventType)
    .build()
}
