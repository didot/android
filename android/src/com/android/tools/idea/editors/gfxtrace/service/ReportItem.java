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

import com.android.tools.rpclib.schema.*;
import com.android.tools.idea.editors.gfxtrace.service.log.Severity;
import com.android.tools.rpclib.binary.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class ReportItem implements BinaryObject {
  //<<<Start:Java.ClassBody:1>>>
  private Severity mySeverity;
  private String myMessage;
  private long myAtom;

  // Constructs a default-initialized {@link ReportItem}.
  public ReportItem() {}


  public Severity getSeverity() {
    return mySeverity;
  }

  public ReportItem setSeverity(Severity v) {
    mySeverity = v;
    return this;
  }

  public String getMessage() {
    return myMessage;
  }

  public ReportItem setMessage(String v) {
    myMessage = v;
    return this;
  }

  public long getAtom() {
    return myAtom;
  }

  public ReportItem setAtom(long v) {
    myAtom = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }


  private static final Entity ENTITY = new Entity("service","ReportItem","","");

  static {
    Namespace.register(Klass.INSTANCE);
    ENTITY.setFields(new Field[]{
      new Field("Severity", new Primitive("log.Severity", Method.Int32)),
      new Field("Message", new Primitive("string", Method.String)),
      new Field("Atom", new Primitive("uint64", Method.Uint64)),
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
    public BinaryObject create() { return new ReportItem(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      ReportItem o = (ReportItem)obj;
      o.mySeverity.encode(e);
      e.string(o.myMessage);
      e.uint64(o.myAtom);
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      ReportItem o = (ReportItem)obj;
      o.mySeverity = Severity.decode(d);
      o.myMessage = d.string();
      o.myAtom = d.uint64();
    }
    //<<<End:Java.KlassBody:2>>>
  }
}
