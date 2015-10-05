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
package com.android.tools.idea.editors.gfxtrace.service.atom;

import com.android.tools.rpclib.schema.*;
import com.android.tools.rpclib.binary.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import java.io.IOException;

public final class AtomList implements BinaryObject {
  public Atom get(long index) {
    return myAtoms[(int)index];
  }

  public void addAtoms(@NotNull DefaultMutableTreeNode parent, long start, long end) {
    for (long index = start; index < end;  ++index) {
      get(index).buildTree(parent, index);
    }
  }


  //<<<Start:Java.ClassBody:1>>>
  private Atom[] myAtoms;

  // Constructs a default-initialized {@link AtomList}.
  public AtomList() {}


  public Atom[] getAtoms() {
    return myAtoms;
  }

  public AtomList setAtoms(Atom[] v) {
    myAtoms = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }


  private static final Entity ENTITY = new Entity("atom","List","","");

  static {
    Namespace.register(Klass.INSTANCE);
    ENTITY.setFields(new Field[]{
      new Field("Atoms", new Slice("", new Variant("Atom"))),
    });
  }
  public static void register() {}
  //<<<End:Java.ClassBody:1>>>
  public enum Klass implements BinaryClass {
    //<<<Start:Java.KlassBody:2>>>
    INSTANCE;

    @Override @NotNull
    public Entity entity() { return ENTITY; }

    @Override @NotNull
    public BinaryObject create() { return new AtomList(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      AtomList o = (AtomList)obj;
      e.uint32(o.myAtoms.length);
      for (int i = 0; i < o.myAtoms.length; i++) {
        e.variant(o.myAtoms[i].unwrap());
      }
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      AtomList o = (AtomList)obj;
      o.myAtoms = new Atom[d.uint32()];
      for (int i = 0; i <o.myAtoms.length; i++) {
        o.myAtoms[i] = Atom.wrap(d.variant());
      }
    }
    //<<<End:Java.KlassBody:2>>>
  }
}
