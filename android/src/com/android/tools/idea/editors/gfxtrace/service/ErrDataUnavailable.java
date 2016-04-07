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

import com.android.tools.rpclib.rpccore.RpcException;
import org.jetbrains.annotations.NotNull;

import com.android.tools.rpclib.binary.*;
import com.android.tools.rpclib.schema.*;
import com.android.tools.idea.editors.gfxtrace.service.msg.Msg;

import java.io.IOException;

public final class ErrDataUnavailable extends RpcException implements BinaryObject {
  //<<<Start:Java.ClassBody:1>>>
  private Msg myReason;
  private boolean myTransient;

  // Constructs a default-initialized {@link ErrDataUnavailable}.
  public ErrDataUnavailable() {}


  public Msg getReason() {
    return myReason;
  }

  public ErrDataUnavailable setReason(Msg v) {
    myReason = v;
    return this;
  }

  public boolean getTransient() {
    return myTransient;
  }

  public ErrDataUnavailable setTransient(boolean v) {
    myTransient = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }


  private static final Entity ENTITY = new Entity("service", "ErrDataUnavailable", "", "");

  static {
    ENTITY.setFields(new Field[]{
      new Field("Reason", new Pointer(new Struct(Msg.Klass.INSTANCE.entity()))),
      new Field("Transient", new Primitive("bool", Method.Bool)),
    });
    Namespace.register(Klass.INSTANCE);
  }
  public static void register() {}
  //<<<End:Java.ClassBody:1>>>

  @Override
  public String getMessage() {
    return myReason.toString();
  }

  public enum Klass implements BinaryClass {
    //<<<Start:Java.KlassBody:2>>>
    INSTANCE;

    @Override @NotNull
    public Entity entity() { return ENTITY; }

    @Override @NotNull
    public BinaryObject create() { return new ErrDataUnavailable(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      ErrDataUnavailable o = (ErrDataUnavailable)obj;
      e.object(o.myReason);
      e.bool(o.myTransient);
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      ErrDataUnavailable o = (ErrDataUnavailable)obj;
      o.myReason = (Msg)d.object();
      o.myTransient = d.bool();
    }
    //<<<End:Java.KlassBody:2>>>
  }
}
