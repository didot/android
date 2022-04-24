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

#pragma once

#include <cstdint>
#include <string>

#include "geom.h"

namespace screensharing {

// Native code analogue of the android.view.DisplayInfo class.
struct DisplayInfo {
  DisplayInfo(int32_t logical_width, int32_t logical_height, int32_t rotation, int32_t layer_stack, int32_t flags);

  // Returns the display dimensions in the canonical orientation.
  Size NaturalSize() const {
    return logical_size.Rotated(-rotation);
  }

  std::string ToDebugString() const;

  Size logical_size;
  int32_t rotation;
  int32_t layer_stack;
  int32_t flags;
};

}  // namespace screensharing