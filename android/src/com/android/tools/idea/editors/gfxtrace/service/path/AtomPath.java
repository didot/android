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
package com.android.tools.idea.editors.gfxtrace.service.path;

import com.android.tools.rpclib.binary.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class AtomPath extends Path {
  @Override
  public StringBuilder stringPath(StringBuilder builder) {
    return myAtoms.stringPath(builder).append("[").append(myIndex).append("]");
  }

  public StatePath stateAfter() {
    return new StatePath ().setAfter(this);
  }

  //<<<Start:Java.ClassBody:1>>>
  private AtomsPath myAtoms;
  private long myIndex;

  // Constructs a default-initialized {@link AtomPath}.
  public AtomPath() {}


  public AtomsPath getAtoms() {
    return myAtoms;
  }

  public AtomPath setAtoms(AtomsPath v) {
    myAtoms = v;
    return this;
  }

  public long getIndex() {
    return myIndex;
  }

  public AtomPath setIndex(long v) {
    myIndex = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }

  private static final byte[] IDBytes = {88, 17, -66, 109, -4, -30, -32, 18, 108, 66, 85, 45, -13, 94, 17, 26, -63, 107, -2, 59, };
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
    public BinaryObject create() { return new AtomPath(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      AtomPath o = (AtomPath)obj;
      e.object(o.myAtoms);
      e.uint64(o.myIndex);
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      AtomPath o = (AtomPath)obj;
      o.myAtoms = (AtomsPath)d.object();
      o.myIndex = d.uint64();
    }
    //<<<End:Java.KlassBody:2>>>
  }
}
