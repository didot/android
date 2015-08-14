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

import com.android.tools.rpclib.binary.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class AtomRangeTimer implements BinaryObject {
  //<<<Start:Java.ClassBody:1>>>
  long myFromAtomIndex;
  long myToAtomIndex;
  long myNanoseconds;

  // Constructs a default-initialized {@link AtomRangeTimer}.
  public AtomRangeTimer() {}


  public long getFromAtomIndex() {
    return myFromAtomIndex;
  }

  public AtomRangeTimer setFromAtomIndex(long v) {
    myFromAtomIndex = v;
    return this;
  }

  public long getToAtomIndex() {
    return myToAtomIndex;
  }

  public AtomRangeTimer setToAtomIndex(long v) {
    myToAtomIndex = v;
    return this;
  }

  public long getNanoseconds() {
    return myNanoseconds;
  }

  public AtomRangeTimer setNanoseconds(long v) {
    myNanoseconds = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }

  private static final byte[] IDBytes = {116, 0, -57, -68, -128, -37, -22, -76, 91, -108, -28, -49, 100, 97, -68, 95, -100, -54, 28, -14, };
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
    public BinaryObject create() { return new AtomRangeTimer(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      AtomRangeTimer o = (AtomRangeTimer)obj;
      e.uint64(o.myFromAtomIndex);
      e.uint64(o.myToAtomIndex);
      e.uint64(o.myNanoseconds);
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      AtomRangeTimer o = (AtomRangeTimer)obj;
      o.myFromAtomIndex = d.uint64();
      o.myToAtomIndex = d.uint64();
      o.myNanoseconds = d.uint64();
    }
    //<<<End:Java.KlassBody:2>>>
  }
}
