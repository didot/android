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

import com.android.tools.idea.editors.gfxtrace.service.path.ResourceID;
import com.android.tools.rpclib.schema.*;
import org.jetbrains.annotations.NotNull;

import com.android.tools.rpclib.binary.BinaryClass;
import com.android.tools.rpclib.binary.BinaryID;
import com.android.tools.rpclib.binary.BinaryObject;
import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;
import com.android.tools.rpclib.binary.Namespace;

import java.io.IOException;

public final class ResourceInfo implements BinaryObject {
  public long getFirstAccess() {
    return myAccesses.length > 0 ? myAccesses[0] : 0;
  }

  //<<<Start:Java.ClassBody:1>>>
  private ResourceID myID;
  private String myName;
  private long[] myAccesses;

  // Constructs a default-initialized {@link ResourceInfo}.
  public ResourceInfo() {}


  public ResourceID getID() {
    return myID;
  }

  public ResourceInfo setID(ResourceID v) {
    myID = v;
    return this;
  }

  public String getName() {
    return myName;
  }

  public ResourceInfo setName(String v) {
    myName = v;
    return this;
  }

  public long[] getAccesses() {
    return myAccesses;
  }

  public ResourceInfo setAccesses(long[] v) {
    myAccesses = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }


  private static final Entity ENTITY = new Entity("service","ResourceInfo","","");

  static {
    ENTITY.setFields(new Field[]{
      new Field("ID", new Array("path.ResourceID", new Primitive("byte", Method.Uint8), 20)),
      new Field("Name", new Primitive("string", Method.String)),
      new Field("Accesses", new Slice("", new Primitive("uint64", Method.Uint64))),
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
    public BinaryObject create() { return new ResourceInfo(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      ResourceInfo o = (ResourceInfo)obj;
      o.myID.write(e);

      e.string(o.myName);
      e.uint32(o.myAccesses.length);
      for (int i = 0; i < o.myAccesses.length; i++) {
        e.uint64(o.myAccesses[i]);
      }
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      ResourceInfo o = (ResourceInfo)obj;
      o.myID = new ResourceID(d);

      o.myName = d.string();
      o.myAccesses = new long[d.uint32()];
      for (int i = 0; i <o.myAccesses.length; i++) {
        o.myAccesses[i] = d.uint64();
      }
    }
    //<<<End:Java.KlassBody:2>>>
  }
}
