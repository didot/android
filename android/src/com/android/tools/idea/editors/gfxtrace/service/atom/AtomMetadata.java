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

import com.android.tools.idea.editors.gfxtrace.service.ApiID;
import com.android.tools.rpclib.schema.*;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import com.android.tools.rpclib.binary.BinaryClass;
import com.android.tools.rpclib.binary.BinaryObject;
import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;
import com.android.tools.rpclib.binary.Namespace;

import java.io.IOException;

public final class AtomMetadata implements BinaryObject {
  public static final AtomMetadata NO_METADATA = new AtomMetadata().setDisplayName("<unknown>");

  public static AtomMetadata find(Entity c) {
    for (BinaryObject o : c.getMetadata()) {
      if (o instanceof AtomMetadata) {
        AtomMetadata meta = (AtomMetadata)o;
        meta.prepare(c);
        return meta;
      }
    }
    return NO_METADATA;
  }

  boolean myIsPrepared = false;
  int myResultIndex = -1;
  int myExtrasIndex = -1;
  @NotNull private static final Logger LOG = Logger.getInstance(AtomMetadata.class);

  private void prepare(Entity c) {
    if (myIsPrepared) return;
    myIsPrepared = true;
    for (int index = 0; index < c.getFields().length; index++) {
      Field field = c.getFields()[index];
      if (field.getDeclared().equals("Result")) {
        myResultIndex = index;
      }
      if (field.getType() instanceof Slice) {
        Type vt = ((Slice)field.getType()).getValueType();
        if (vt instanceof Interface) {
          if ("atom.Extra".equals(((Interface)vt).name)) {
            myExtrasIndex = index;
          }
        }
      }
    }
    if (myDisplayName == null) {
      myDisplayName = "<unknown>";
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AtomMetadata that = (AtomMetadata)o;

    if (myIsPrepared != that.myIsPrepared) return false;
    if (myResultIndex != that.myResultIndex) return false;
    if (myExtrasIndex != that.myExtrasIndex) return false;
    if (myEndOfFrame != that.myEndOfFrame) return false;
    if (myDrawCall != that.myDrawCall) return false;
    if (myAPI != null ? !myAPI.equals(that.myAPI) : that.myAPI != null) return false;
    if (myDisplayName != null ? !myDisplayName.equals(that.myDisplayName) : that.myDisplayName != null) return false;
    if (myDocumentationUrl != null ? !myDocumentationUrl.equals(that.myDocumentationUrl) : that.myDocumentationUrl != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = (myIsPrepared ? 1 : 0);
    result = 31 * result + myResultIndex;
    result = 31 * result + myExtrasIndex;
    result = 31 * result + (myAPI != null ? myAPI.hashCode() : 0);
    result = 31 * result + (myDisplayName != null ? myDisplayName.hashCode() : 0);
    result = 31 * result + (myEndOfFrame ? 1 : 0);
    result = 31 * result + (myDrawCall ? 1 : 0);
    result = 31 * result + (myDocumentationUrl != null ? myDocumentationUrl.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "AtomMetadata{" +
           "prepared=" + myIsPrepared +
           ", resultIndex=" + myResultIndex +
           ", extrasIndex=" + myExtrasIndex +
           ", API=" + myAPI +
           ", displayName='" + myDisplayName + '\'' +
           ", endOfFrame=" + myEndOfFrame +
           ", drawCall=" + myDrawCall +
           ", documentationUrl='" + myDocumentationUrl + '\'' +
           '}';
  }

  //<<<Start:Java.ClassBody:1>>>
  private ApiID myAPI;
  private String myDisplayName;
  private boolean myEndOfFrame;
  private boolean myDrawCall;
  private String myDocumentationUrl;

  // Constructs a default-initialized {@link AtomMetadata}.
  public AtomMetadata() {}


  public ApiID getAPI() {
    return myAPI;
  }

  public AtomMetadata setAPI(ApiID v) {
    myAPI = v;
    return this;
  }

  public String getDisplayName() {
    return myDisplayName;
  }

  public AtomMetadata setDisplayName(String v) {
    myDisplayName = v;
    return this;
  }

  public boolean getEndOfFrame() {
    return myEndOfFrame;
  }

  public AtomMetadata setEndOfFrame(boolean v) {
    myEndOfFrame = v;
    return this;
  }

  public boolean getDrawCall() {
    return myDrawCall;
  }

  public AtomMetadata setDrawCall(boolean v) {
    myDrawCall = v;
    return this;
  }

  public String getDocumentationUrl() {
    return myDocumentationUrl;
  }

  public AtomMetadata setDocumentationUrl(String v) {
    myDocumentationUrl = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }


  private static final Entity ENTITY = new Entity("atom", "Metadata", "", "");

  static {
    ENTITY.setFields(new Field[]{
      new Field("API", new Array("gfxapi.ID", new Primitive("byte", Method.Uint8), 20)),
      new Field("DisplayName", new Primitive("string", Method.String)),
      new Field("EndOfFrame", new Primitive("bool", Method.Bool)),
      new Field("DrawCall", new Primitive("bool", Method.Bool)),
      new Field("DocumentationUrl", new Primitive("string", Method.String)),
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
    public BinaryObject create() { return new AtomMetadata(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      AtomMetadata o = (AtomMetadata)obj;
      o.myAPI.write(e);

      e.string(o.myDisplayName);
      e.bool(o.myEndOfFrame);
      e.bool(o.myDrawCall);
      e.string(o.myDocumentationUrl);
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      AtomMetadata o = (AtomMetadata)obj;
      o.myAPI = new ApiID(d);

      o.myDisplayName = d.string();
      o.myEndOfFrame = d.bool();
      o.myDrawCall = d.bool();
      o.myDocumentationUrl = d.string();
    }
    //<<<End:Java.KlassBody:2>>>
  }
}
