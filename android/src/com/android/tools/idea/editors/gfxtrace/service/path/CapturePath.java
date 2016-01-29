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
import com.android.tools.rpclib.binary.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class CapturePath extends Path {
  @Override
  public StringBuilder stringPath(StringBuilder builder) {
    return builder.append("Capture(").append(myID).append(")");
  }

  @Override
  public Path getParent() {
    return null;
  }

  public AtomsPath atoms() {
    return new AtomsPath().setCapture(this);
  }

  public static AtomsPath atoms(CapturePath capture) {
    return (capture == null) ? null : capture.atoms();
  }

  public HierarchyPath hierarchy() {
    return new HierarchyPath().setCapture(this);
  }

  public ResourcesPath resources() {
    return new ResourcesPath().setCapture(this);
  }

  public static ResourcesPath resources(CapturePath capture) {
    return (capture == null) ? null : capture.resources();
  }

  //<<<Start:Java.ClassBody:1>>>
  private BinaryID myID;

  // Constructs a default-initialized {@link CapturePath}.
  public CapturePath() {}


  public BinaryID getID() {
    return myID;
  }

  public CapturePath setID(BinaryID v) {
    myID = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }


  private static final Entity ENTITY = new Entity("path", "Capture", "", "");

  static {
    ENTITY.setFields(new Field[]{
      new Field("ID", new Array("id.ID", new Primitive("byte", Method.Uint8), 20)),
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
    public BinaryObject create() { return new CapturePath(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      CapturePath o = (CapturePath)obj;
      o.myID.write(e);

    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      CapturePath o = (CapturePath)obj;
      o.myID = new BinaryID(d);

    }
    //<<<End:Java.KlassBody:2>>>
  }
}
