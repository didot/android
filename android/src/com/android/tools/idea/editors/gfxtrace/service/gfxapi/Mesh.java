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

import com.android.tools.idea.editors.gfxtrace.service.vertex.VertexBuffer;
import org.jetbrains.annotations.NotNull;

import com.android.tools.rpclib.binary.*;
import com.android.tools.rpclib.schema.*;
import com.android.tools.rpclib.schema.Pointer;

import java.io.IOException;

public final class Mesh implements BinaryObject {
  //<<<Start:Java.ClassBody:1>>>
  private DrawPrimitive myDrawPrimitive;
  private VertexBuffer myVertexBuffer;
  private IndexBuffer myIndexBuffer;

  // Constructs a default-initialized {@link Mesh}.
  public Mesh() {}


  public DrawPrimitive getDrawPrimitive() {
    return myDrawPrimitive;
  }

  public Mesh setDrawPrimitive(DrawPrimitive v) {
    myDrawPrimitive = v;
    return this;
  }

  public VertexBuffer getVertexBuffer() {
    return myVertexBuffer;
  }

  public Mesh setVertexBuffer(VertexBuffer v) {
    myVertexBuffer = v;
    return this;
  }

  public IndexBuffer getIndexBuffer() {
    return myIndexBuffer;
  }

  public Mesh setIndexBuffer(IndexBuffer v) {
    myIndexBuffer = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }


  private static final Entity ENTITY = new Entity("gfxapi", "Mesh", "", "");

  static {
    ENTITY.setFields(new Field[]{
      new Field("DrawPrimitive", new Primitive("DrawPrimitive", Method.Uint8)),
      new Field("VertexBuffer", new Pointer(new Struct(VertexBuffer.Klass.INSTANCE.entity()))),
      new Field("IndexBuffer", new Pointer(new Struct(IndexBuffer.Klass.INSTANCE.entity()))),
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
    public BinaryObject create() { return new Mesh(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      Mesh o = (Mesh)obj;
      o.myDrawPrimitive.encode(e);
      e.object(o.myVertexBuffer);
      e.object(o.myIndexBuffer);
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      Mesh o = (Mesh)obj;
      o.myDrawPrimitive = DrawPrimitive.decode(d);
      o.myVertexBuffer = (VertexBuffer)d.object();
      o.myIndexBuffer = (IndexBuffer)d.object();
    }
    //<<<End:Java.KlassBody:2>>>
  }
}
