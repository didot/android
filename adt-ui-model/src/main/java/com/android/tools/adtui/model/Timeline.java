/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.adtui.model;

import org.jetbrains.annotations.NotNull;

/**
 * A timeline that exposes range data.
 */
public interface Timeline {
  /**
   * @return entire range of the underlying data.
   */
  @NotNull
  Range getDataRange();

  /**
   * @return range of the currently visible section.
   */
  @NotNull
  Range getViewRange();

  /**
   * @return range of the data shown by tooltip.
   */
  @NotNull
  Range getTooltipRange();

  /**
   * @return range of currently selected data.
   */
  @NotNull
  Range getSelectionRange();
}
