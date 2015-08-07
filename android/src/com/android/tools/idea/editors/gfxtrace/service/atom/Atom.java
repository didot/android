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
 */
package com.android.tools.idea.editors.gfxtrace.service.atom;

import com.android.tools.rpclib.binary.BinaryObject;
import com.android.tools.rpclib.schema.Field;
import org.jetbrains.annotations.NotNull;

public abstract class Atom {
  public static Atom wrap(BinaryObject object) {
    return null;
  }

  @NotNull
  public abstract BinaryObject unwrap();

  public String getName() {
    return "*unknown*";
  }

  public abstract int getFieldCount();

  public abstract Field getFieldInfo(int index);

  public abstract Object getFieldValue(int index);

  public abstract Observations getObservations();

  public abstract boolean getIsEndOfFrame();
}
