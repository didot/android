/*
 * Copyright (C) 2015 The Android Open Source Project
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
 *
 * THIS FILE WAS GENERATED BY codergen. EDIT WITH CARE.
 */
package com.android.tools.idea.editors.gfxtrace.service;

import org.jetbrains.annotations.NotNull;
import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;
import java.io.IOException;

public final class WireframeMode {
  public static final int NoWireframe = 0;
  public static WireframeMode noWireframe() { return new WireframeMode(NoWireframe); }
  public static final int WireframeOverlay = 1;
  public static WireframeMode wireframeOverlay() { return new WireframeMode(WireframeOverlay); }
  public static final int AllWireframe = 2;
  public static WireframeMode allWireframe() { return new WireframeMode(AllWireframe); }

  public final int value;

  public WireframeMode(int value) {
    this.value = value;
  }

  public void encode(@NotNull Encoder e) throws IOException {
    e.int32(value);
  }

  public static WireframeMode decode(@NotNull Decoder d) throws IOException {
    int value = d.int32();
    return new WireframeMode(value);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || !(o instanceof WireframeMode)) return false;
    return value == ((WireframeMode)o).value;
  }

  @Override
  public int hashCode() {
    return value;
  }

  @Override
  public String toString() {
    switch(value) {
      case NoWireframe: return "NoWireframe";
      case WireframeOverlay: return "WireframeOverlay";
      case AllWireframe: return "AllWireframe";
      default: return "WireframeMode(" + value + ")";
    }
  }
}
