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
package com.android.tools.idea.editors.gfxtrace.service.snippets;

import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class PartKind {
  public static final PartKind KeyPart = new PartKind(0, "KeyPart");
  public static final int KeyPartValue = 0;
  public static final PartKind ElemPart = new PartKind(1, "ElemPart");
  public static final int ElemPartValue = 1;
  public static final PartKind RangePart = new PartKind(2, "RangePart");
  public static final int RangePartValue = 2;

  private static final ImmutableMap<Integer, PartKind> VALUES = ImmutableMap.<Integer, PartKind>builder()
    .put(0, KeyPart)
    .put(1, ElemPart)
    .put(2, RangePart)
    .build();

  private final int myValue;
  private final String myName;

  private PartKind(int v, String n) {
    myValue = v;
    myName = n;
  }

  public int getValue() {
    return myValue;
  }

  public String getName() {
    return myName;
  }

  public void encode(@NotNull Encoder e) throws IOException {
    e.int32(myValue);
  }

  public static PartKind decode(@NotNull Decoder d) throws IOException {
    return findOrCreate(d.int32());
  }

  public static PartKind find(int value) {
    return VALUES.get(value);
  }

  public static PartKind findOrCreate(int value) {
    PartKind result = VALUES.get(value);
    return (result == null) ? new PartKind(value, null) : result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || !(o instanceof PartKind)) return false;
    return myValue == ((PartKind)o).myValue;
  }

  @Override
  public int hashCode() {
    return myValue;
  }

  @Override
  public String toString() {
    return (myName == null) ? "PartKind(" + myValue + ")" : myName;
  }
}
