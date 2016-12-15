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
package com.android.tools.profilers.memory.adapters;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A {@link MemoryObject} that describes an object instance having a reference to another.
 */
public interface ReferenceObject extends InstanceObject {
  /**
   * @return the names of the fields of this object instance that holds a reference to the referree.
   * If this object is an array, then a list of indices pointing to the referree is returned.
   */
  @NotNull
  List<String> getReferenceFieldNames();
}
