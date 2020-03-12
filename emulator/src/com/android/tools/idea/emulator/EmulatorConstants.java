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
package com.android.tools.idea.emulator;

import com.intellij.openapi.actionSystem.DataKey;

/**
 * Constants used by the classes in this package.
 */
class EmulatorConstants {
  public static final DataKey<EmulatorController> EMULATOR_CONTROLLER_KEY = DataKey.create("emulator");
  public static final DataKey<EmulatorView> EMULATOR_VIEW_KEY = DataKey.create("emulatorView");

  public static final String EMULATOR_TOOLBAR_ID = "EmulatorToolbar";
}
