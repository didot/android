/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.run.editor;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class AndroidDebuggerContext implements JDOMExternalizable {
  public String DEBUGGER_TYPE;
  private final Map<String, AndroidDebuggerState> myAndroidDebuggerStates = Maps.newHashMap();
  private final String myDefaultDebuggerType;

  public AndroidDebuggerContext(@NotNull String defaultDebuggerType) {
    DEBUGGER_TYPE = getDefaultAndroidDebuggerType();
    for (AndroidDebugger androidDebugger : getAndroidDebuggers()) {
      myAndroidDebuggerStates.put(androidDebugger.getId(), androidDebugger.createState());
    }
    myDefaultDebuggerType = defaultDebuggerType;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);

    for (Map.Entry<String, AndroidDebuggerState> entry : myAndroidDebuggerStates.entrySet()) {
      Element optionElement = element.getChild(entry.getKey());
      if (optionElement != null) {
        entry.getValue().readExternal(optionElement);
      }
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);

    for (Map.Entry<String, AndroidDebuggerState> entry : myAndroidDebuggerStates.entrySet()) {
      Element optionElement = new Element(entry.getKey());
      element.addContent(optionElement);
      entry.getValue().writeExternal(optionElement);
    }
  }

  @NotNull
  public String getDebuggerType() {
    return DEBUGGER_TYPE;
  }

  public void setDebuggerType(@NotNull String debuggerType) {
    DEBUGGER_TYPE = debuggerType;
  }

  @NotNull
  public List<AndroidDebugger> getAndroidDebuggers() {
    return Lists.newArrayList(AndroidDebugger.EP_NAME.getExtensions());
  }

  @Nullable
  public AndroidDebugger getAndroidDebugger() {
    for (AndroidDebugger androidDebugger : getAndroidDebuggers()) {
      if (androidDebugger.getId().equals(DEBUGGER_TYPE)) {
        return androidDebugger;
      }
    }
    return null;
  }

  @Nullable
  public <T extends AndroidDebuggerState> T getAndroidDebuggerState(@NotNull String androidDebuggerId) {
    AndroidDebuggerState state = myAndroidDebuggerStates.get(androidDebuggerId);
    return (state != null) ? (T)state : null;
  }

  @Nullable
  public <T extends AndroidDebuggerState> T getAndroidDebuggerState() {
    return getAndroidDebuggerState(DEBUGGER_TYPE);
  }

  @NotNull
  protected String getDefaultAndroidDebuggerType() {
    for (AndroidDebugger androidDebugger : getAndroidDebuggers()) {
      if (androidDebugger.shouldBeDefault()) {
        return androidDebugger.getId();
      }
    }
    return myDefaultDebuggerType;
  }
}
