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
package com.android.tools.idea.devicemanager;

import com.android.tools.idea.devicemanager.virtualtab.VirtualDevicePanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FrameWrapper;
import org.jetbrains.annotations.Nullable;

public final class DeviceManagerWelcomeScreenFrame extends FrameWrapper {
  public DeviceManagerWelcomeScreenFrame(@Nullable Project project) {
    super(project, "com.android.tools.idea.devicemanager.DeviceManagerWelcomeScreenFrame", false, "Device Manager");
    closeOnEsc();

    setComponent(new VirtualDevicePanel(project, this));
  }
}
