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
package com.android.tools.idea.devicemanager.virtualtab;

import com.android.tools.idea.devicemanager.legacy.AvdActionPanel;
import org.jetbrains.annotations.NotNull;

final class AvdActionPanels {
  private AvdActionPanels() {
  }

  static void selectNextComponent(@NotNull AvdActionPanel panel) {
    int i = panel.getFocusedComponent() + 1;

    if (i < panel.getVisibleComponentCount()) {
      panel.setFocusedComponent(i);
      panel.repaint();
    }
  }

  static void selectPreviousComponent(@NotNull AvdActionPanel panel) {
    int i = panel.getFocusedComponent() - 1;

    if (i >= 0) {
      panel.setFocusedComponent(i);
      panel.repaint();
    }
  }
}
