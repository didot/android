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

#include "input_manager.h"

#include "jvm.h"
#include "log.h"
#include "service_manager.h"

namespace screensharing {

InputManager::InputManager(Jni jni)
    : jni_(jni),
      input_manager_(ServiceManager::GetServiceAsInterface(jni, "input", "android/hardware/input/IInputManager")) {
  JClass input_manager_class = input_manager_.GetClass();
  inject_input_event_method_ = input_manager_class.GetMethodId("injectInputEvent", "(Landroid/view/InputEvent;I)Z");
  input_manager_.MakeGlobal();
}

InputManager::~InputManager() = default;

void InputManager::InjectInputEvent(const JObject& input_event, InputEventInjectionSync mode) {
  if (!input_manager_.CallBooleanMethod(jni_, inject_input_event_method_, input_event.ref(), static_cast<jint>(mode))) {
    Log::E("Unable to inject an input event %s", input_event.ToString().c_str());
  }
}

}  // namespace screensharing
