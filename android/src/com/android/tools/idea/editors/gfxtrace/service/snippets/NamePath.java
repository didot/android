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

import org.jetbrains.annotations.NotNull;

import com.android.tools.rpclib.binary.*;
import com.android.tools.rpclib.schema.*;
import com.android.tools.idea.editors.gfxtrace.service.snippets.SnippetsProtos.SymbolCategory;

import java.io.IOException;

final class NamePath extends Pathway implements BinaryObject {
  public NamePath(SymbolCategory cat, String name) {
    this.myCat = cat;
    this.myName = name;
  }

  //<<<Start:Java.ClassBody:1>>>
  private SymbolCategory myCat;
  private String myName;

  // Constructs a default-initialized {@link NamePath}.
  public NamePath() {}


  public SymbolCategory getCat() {
    return myCat;
  }

  public NamePath setCat(SymbolCategory v) {
    myCat = v;
    return this;
  }

  public String getName() {
    return myName;
  }

  public NamePath setName(String v) {
    myName = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }


  private static final Entity ENTITY = new Entity("snippets", "namePath", "", "");

  static {
    ENTITY.setFields(new Field[]{
      new Field("cat", new Primitive("SymbolCategory", Method.Int32)),
      new Field("name", new Primitive("string", Method.String)),
    });
    Namespace.register(Klass.INSTANCE);
  }
  public static void register() {}
  //<<<End:Java.ClassBody:1>>>

  @Override
  public Pathway base() {
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    NamePath namePath = (NamePath)o;

    if (myCat != null ? !myCat.equals(namePath.myCat) : namePath.myCat != null) return false;
    if (myName != null ? !myName.equals(namePath.myName) : namePath.myName != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myCat != null ? myCat.hashCode() : 0;
    result = 31 * result + (myName != null ? myName.hashCode() : 0);
    return result;
  }

  public enum Klass implements BinaryClass {
    //<<<Start:Java.KlassBody:2>>>
    INSTANCE;

    @Override @NotNull
    public Entity entity() { return ENTITY; }

    @Override @NotNull
    public BinaryObject create() { return new NamePath(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      NamePath o = (NamePath)obj;
      e.int32(o.myCat.getNumber());
      e.string(o.myName);
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      NamePath o = (NamePath)obj;
      o.myCat = SymbolCategory.valueOf(d.int32());
      o.myName = d.string();
    }
    //<<<End:Java.KlassBody:2>>>

  }
}
