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
package com.android.tools.idea.devicemanager.legacy;

import com.android.ddmlib.IDevice;
import com.google.common.util.concurrent.FutureCallback;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LegacyAvdManagerUtils {
  public static @NotNull FutureCallback<@NotNull IDevice> newCallback(@Nullable Project project) {
    return new ShowErrorDialogCallback<>("Device Manager",
                                         "There was an unspecified error in the device manager. Please consult idea.log for more " +
                                         "information.",
                                         project);
  }
}
