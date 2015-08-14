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

import com.android.tools.idea.editors.gfxtrace.service.memory.Range;
import com.android.tools.rpclib.binary.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class MemoryInfo implements BinaryObject {
  //<<<Start:Java.ClassBody:1>>>
  byte[] myData;
  Range[] myReads;
  Range[] myWrites;
  Range[] myObserved;

  // Constructs a default-initialized {@link MemoryInfo}.
  public MemoryInfo() {}


  public byte[] getData() {
    return myData;
  }

  public MemoryInfo setData(byte[] v) {
    myData = v;
    return this;
  }

  public Range[] getReads() {
    return myReads;
  }

  public MemoryInfo setReads(Range[] v) {
    myReads = v;
    return this;
  }

  public Range[] getWrites() {
    return myWrites;
  }

  public MemoryInfo setWrites(Range[] v) {
    myWrites = v;
    return this;
  }

  public Range[] getObserved() {
    return myObserved;
  }

  public MemoryInfo setObserved(Range[] v) {
    myObserved = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }

  private static final byte[] IDBytes = {-48, 81, 77, -64, -21, -12, -69, 109, 70, -6, 62, 2, -108, -124, -52, -97, -126, -55, -60, -98, };
  public static final BinaryID ID = new BinaryID(IDBytes);

  static {
    Namespace.register(ID, Klass.INSTANCE);
  }
  public static void register() {}
  //<<<End:Java.ClassBody:1>>>
  public enum Klass implements BinaryClass {
    //<<<Start:Java.KlassBody:2>>>
    INSTANCE;

    @Override @NotNull
    public BinaryID id() { return ID; }

    @Override @NotNull
    public BinaryObject create() { return new MemoryInfo(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      MemoryInfo o = (MemoryInfo)obj;
      e.uint32(o.myData.length);
      e.write(o.myData, o.myData.length);

      e.uint32(o.myReads.length);
      for (int i = 0; i < o.myReads.length; i++) {
        e.value(o.myReads[i]);
      }
      e.uint32(o.myWrites.length);
      for (int i = 0; i < o.myWrites.length; i++) {
        e.value(o.myWrites[i]);
      }
      e.uint32(o.myObserved.length);
      for (int i = 0; i < o.myObserved.length; i++) {
        e.value(o.myObserved[i]);
      }
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      MemoryInfo o = (MemoryInfo)obj;
      o.myData = new byte[d.uint32()];
      d.read(o.myData, o.myData.length);

      o.myReads = new Range[d.uint32()];
      for (int i = 0; i <o.myReads.length; i++) {
        o.myReads[i] = new Range();
        d.value(o.myReads[i]);
      }
      o.myWrites = new Range[d.uint32()];
      for (int i = 0; i <o.myWrites.length; i++) {
        o.myWrites[i] = new Range();
        d.value(o.myWrites[i]);
      }
      o.myObserved = new Range[d.uint32()];
      for (int i = 0; i <o.myObserved.length; i++) {
        o.myObserved[i] = new Range();
        d.value(o.myObserved[i]);
      }
    }
    //<<<End:Java.KlassBody:2>>>
  }
}
