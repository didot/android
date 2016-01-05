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

import com.android.tools.rpclib.schema.*;
import com.android.tools.idea.editors.gfxtrace.service.memory.MemoryRange;
import com.android.tools.idea.editors.gfxtrace.service.memory.PoolID;
import com.android.tools.rpclib.binary.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class AtomPath extends Path {
  @Override
  public StringBuilder stringPath(StringBuilder builder) {
    return myAtoms.stringPath(builder).append("[").append(myIndex).append("]");
  }

  @Override
  public Path getParent() {
    return myAtoms;
  }

  public StatePath stateAfter() {
    return new StatePath().setAfter(this);
  }

  public static StatePath stateAfter(AtomPath atomPath) {
    return (atomPath == null) ? null : atomPath.stateAfter();
  }

  public ResourcePath resourceAfter(ResourceID id) {
    return new ResourcePath().setAfter(this).setID(id);
  }

  public MemoryRangePath memoryAfter(PoolID pool, MemoryRange range) {
    return new MemoryRangePath().setAfter(this).setPool(pool.getValue()).setAddress(range.getBase()).setSize(range.getSize());
  }

  public FieldPath field(String name) {
    return new FieldPath().setStruct(this).setName(name);
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


  private static final Entity ENTITY = new Entity("path","Atom","","");

  static {
    ENTITY.setFields(new Field[]{
      new Field("Atoms", new Pointer(new Struct(AtomsPath.Klass.INSTANCE.entity()))),
      new Field("Index", new Primitive("uint64", Method.Uint64)),
    });
    Namespace.register(Klass.INSTANCE);
  }
  public static void register() {}
  //<<<End:Java.ClassBody:1>>>
  public enum Klass implements BinaryClass {
    //<<<Start:Java.KlassBody:2>>>
    INSTANCE;

    @Override @NotNull
    public Entity entity() { return ENTITY; }

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
