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

final class CallImportCapture implements BinaryObject {
  //<<<Start:Java.ClassBody:1>>>
  String myName;
  byte[] myData;

  // Constructs a default-initialized {@link CallImportCapture}.
  public CallImportCapture() {}


  public String getName() {
    return myName;
  }

  public CallImportCapture setName(String v) {
    myName = v;
    return this;
  }

  public byte[] getData() {
    return myData;
  }

  public CallImportCapture setData(byte[] v) {
    myData = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }

  private static final byte[] IDBytes = {-30, -38, -88, 71, 75, -101, -36, 106, -40, -92, -7, -102, -89, -109, -23, 77, 72, 7, 123, -114, };
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
    public BinaryObject create() { return new CallImportCapture(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      CallImportCapture o = (CallImportCapture)obj;
      e.string(o.myName);
      e.uint32(o.myData.length);
      e.write(o.myData, o.myData.length);

    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      CallImportCapture o = (CallImportCapture)obj;
      o.myName = d.string();
      o.myData = new byte[d.uint32()];
      d.read(o.myData, o.myData.length);

    }
    //<<<End:Java.KlassBody:2>>>
  }
}
