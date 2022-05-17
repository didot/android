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
package com.android.tools.adtui.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsConfiguration;
import java.awt.peer.ComponentPeer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.AWTAccessor;

class ComponentAccessor {
  public static void setGraphicsConfiguration(@NotNull Component component, @Nullable GraphicsConfiguration gc) {
    AWTAccessor.getComponentAccessor().setGraphicsConfiguration(component, gc);
  }

  static void setParent(@NotNull Component component, @Nullable Container parent) {
    AWTAccessor.getComponentAccessor().setParent(component, parent);
  }

  static void setPeer(@NotNull Component component, @Nullable ComponentPeer peer) {
    AWTAccessor.getComponentAccessor().setPeer(component, peer);
  }
}
