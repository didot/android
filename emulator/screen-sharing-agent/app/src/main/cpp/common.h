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

// A macro to disallow the assignment operator.
// Should be used in the private declarations for a class.
#define DISALLOW_ASSIGN(type) \
  void operator=(type const &) = delete

// A macro to disallow the copy constructor and the assignment operator.
// Should be used in the private declarations for a class.
#define DISALLOW_COPY_AND_ASSIGN(type) \
  type(type const &) = delete; \
  DISALLOW_ASSIGN(type)
