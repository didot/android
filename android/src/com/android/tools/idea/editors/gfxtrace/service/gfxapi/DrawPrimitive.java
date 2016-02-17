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
package com.android.tools.idea.editors.gfxtrace.service.gfxapi;

import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class DrawPrimitive {
  public static final DrawPrimitive Points = new DrawPrimitive((byte)0, "Points");
  public static final byte PointsValue = 0;
  public static final DrawPrimitive Lines = new DrawPrimitive((byte)1, "Lines");
  public static final byte LinesValue = 1;
  public static final DrawPrimitive LineStrip = new DrawPrimitive((byte)2, "LineStrip");
  public static final byte LineStripValue = 2;
  public static final DrawPrimitive LineLoop = new DrawPrimitive((byte)3, "LineLoop");
  public static final byte LineLoopValue = 3;
  public static final DrawPrimitive Triangles = new DrawPrimitive((byte)4, "Triangles");
  public static final byte TrianglesValue = 4;
  public static final DrawPrimitive TriangleStrip = new DrawPrimitive((byte)5, "TriangleStrip");
  public static final byte TriangleStripValue = 5;
  public static final DrawPrimitive TriangleFan = new DrawPrimitive((byte)6, "TriangleFan");
  public static final byte TriangleFanValue = 6;

  private static final ImmutableMap<Byte, DrawPrimitive> VALUES = ImmutableMap.<Byte, DrawPrimitive>builder()
    .put((byte)0, Points)
    .put((byte)1, Lines)
    .put((byte)2, LineStrip)
    .put((byte)3, LineLoop)
    .put((byte)4, Triangles)
    .put((byte)5, TriangleStrip)
    .put((byte)6, TriangleFan)
    .build();

  private final byte myValue;
  private final String myName;

  private DrawPrimitive(byte v, String n) {
    myValue = v;
    myName = n;
  }

  public byte getValue() {
    return myValue;
  }

  public String getName() {
    return myName;
  }

  public void encode(@NotNull Encoder e) throws IOException {
    e.uint8(myValue);
  }

  public static DrawPrimitive decode(@NotNull Decoder d) throws IOException {
    return findOrCreate(d.uint8());
  }

  public static DrawPrimitive find(byte value) {
    return VALUES.get(value);
  }

  public static DrawPrimitive findOrCreate(byte value) {
    DrawPrimitive result = VALUES.get(value);
    return (result == null) ? new DrawPrimitive(value, null) : result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || !(o instanceof DrawPrimitive)) return false;
    return myValue == ((DrawPrimitive)o).myValue;
  }

  @Override
  public int hashCode() {
    return myValue;
  }

  @Override
  public String toString() {
    return (myName == null) ? "DrawPrimitive(" + myValue + ")" : myName;
  }
}
