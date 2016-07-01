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
import com.android.tools.rpclib.schema.Entity;
import com.android.tools.rpclib.schema.Field;
import com.android.tools.rpclib.schema.Pointer;
import com.android.tools.rpclib.schema.Struct;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class ResourceBundlesPath extends Path {
  @Override
  public String getSegmentString() {
    return "ResourceBundles";
  }

  @Override
  public Path getParent() {
    return myCapture;
  }

  public ResourcesPath asResourcesPath() {
    return myCapture.resources();
  }

  //<<<Start:Java.ClassBody:1>>>
  private CapturePath myCapture;

  // Constructs a default-initialized {@link ResourceBundlesPath}.
  public ResourceBundlesPath() {}


  public CapturePath getCapture() {
    return myCapture;
  }

  public ResourceBundlesPath setCapture(CapturePath v) {
    myCapture = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }


  private static final Entity ENTITY = new Entity("path", "ResourceBundles", "", "");

  static {
    ENTITY.setFields(new Field[]{
      new Field("Capture", new Pointer(new Struct(CapturePath.Klass.INSTANCE.entity()))),
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
    public BinaryObject create() { return new ResourceBundlesPath(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      ResourceBundlesPath o = (ResourceBundlesPath)obj;
      e.object(o.myCapture);
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      ResourceBundlesPath o = (ResourceBundlesPath)obj;
      o.myCapture = (CapturePath)d.object();
    }
    //<<<End:Java.KlassBody:2>>>
  }
}
