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

import com.android.tools.idea.devicemanager.physicaltab.ConnectionType;
import com.android.tools.idea.devicemanager.physicaltab.Key;
import com.android.tools.idea.devicemanager.physicaltab.SerialNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class VirtualDeviceName extends Key {
  private final @NotNull String myValue;

  VirtualDeviceName(@NotNull String value) {
    myValue = value;
  }

  @Override
  protected @NotNull ConnectionType getConnectionType() {
    throw new UnsupportedOperationException();
  }

  @Override
  protected @NotNull SerialNumber getSerialNumber() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int hashCode() {
    return myValue.hashCode();
  }

  @Override
  public boolean equals(@Nullable Object object) {
    return object instanceof VirtualDeviceName && myValue.equals(((VirtualDeviceName)object).myValue);
  }

  @Override
  public @NotNull String toString() {
    return myValue;
  }
}
