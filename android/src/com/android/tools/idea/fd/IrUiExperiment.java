/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.fd;

import org.jetbrains.annotations.NotNull;

public enum IrUiExperiment {
  DEFAULT("Option 1"), // Default UI shipped from 2.0 to 2.2
  HOTSWAP("Option 2"), // Run = install apk, new hotswap action
  STOP_AND_RUN("Option 3"); // Run = Instant Run, new stop and run action

  @NotNull public final String displayText;

  IrUiExperiment(@NotNull String displayText) {
    this.displayText = displayText;
  }
}
