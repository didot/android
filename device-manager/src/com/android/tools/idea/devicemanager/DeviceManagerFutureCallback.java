/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.google.common.util.concurrent.FutureCallback;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

public abstract class DeviceManagerFutureCallback<V> implements FutureCallback<V> {
  private final @NotNull Class<?> myClass;

  protected DeviceManagerFutureCallback(@NotNull Class<?> c) {
    myClass = c;
  }

  @Override
  public final void onFailure(@NotNull Throwable throwable) {
    Logger.getInstance(myClass).warn(throwable);
  }
}
