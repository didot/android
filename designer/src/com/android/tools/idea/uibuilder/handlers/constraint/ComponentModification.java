/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.constraint;

import com.android.tools.idea.common.command.NlWriteCommandAction;
import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.model.NlAttributesHolder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlComponentDelegate;
import com.android.tools.idea.rendering.parsers.AttributeSnapshot;
import com.android.utils.Pair;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;

/**
 * Encapsulate write operations on components
 * Give a chance to an eventual delegate to do something.
 */
public class ComponentModification implements NlAttributesHolder {

  private final NlComponent myComponent;
  private final NlComponentDelegate myComponentDelegate;
  private final String myLabel;

  public ComponentModification(@NotNull NlComponent component, @NotNull String label) {
    myComponent = component;
    myLabel = label;
    myComponentDelegate = myComponent.getDelegate();
    List<AttributeSnapshot> attributeSnapshots = component.getAttributes();
    for (AttributeSnapshot snapshot : attributeSnapshots) {
      setAttribute(snapshot.namespace, snapshot.name, snapshot.value);
    }
  }

  public NlComponent getComponent() {
    return myComponent;
  }

  HashMap<Pair<String, String>, String> myAttributes = new HashMap<>();

  @Override
  public void setAttribute(@Nullable String namespace, @NotNull String name, @Nullable String value) {
    myAttributes.put(Pair.of(namespace, name), value);
  }

  @Override
  public String getAttribute(@Nullable String namespace, @NotNull String attribute) {
    return myAttributes.get(Pair.of(namespace, attribute));
  }

  public HashMap<Pair<String, String>, String> getAttributes() { return myAttributes; }

  @Override
  public void removeAttribute(@NotNull String namespace, @NotNull String name) {
    myAttributes.remove(Pair.of(namespace, name));
  }

  public void apply() {
    if (myComponentDelegate != null && myComponentDelegate.handlesApply(this)) {
      myComponentDelegate.apply(this);
      AttributesTransaction transaction = myComponent.startAttributeTransaction();
      for (Pair<String, String> key : myAttributes.keySet()) {
        String value = myAttributes.get(key);
        transaction.setAttribute(key.getFirst(), key.getSecond(), value);
      }
      transaction.apply();
    } else {
        AttributesTransaction transaction = myComponent.startAttributeTransaction();
        for (Pair<String, String> key : myAttributes.keySet()) {
          String value = myAttributes.get(key);
          transaction.setAttribute(key.getFirst(), key.getSecond(), value);
        }
        transaction.apply();
    }
  }

  public void commit() {
    if (myComponentDelegate != null && myComponentDelegate.handlesCommit(this)) {
      myComponentDelegate.commit(this);
    } else {
      AttributesTransaction transaction = myComponent.startAttributeTransaction();
      for (Pair<String, String> key : myAttributes.keySet()) {
        String value = myAttributes.get(key);
        transaction.setAttribute(key.getFirst(), key.getSecond(), value);
      }
      transaction.apply();
      NlWriteCommandAction.run(myComponent, myLabel, transaction::commit);
    }
  }

  public void commitTo(XmlTag view) {
    for (Pair<String, String> key : myAttributes.keySet()) {
      String value = myAttributes.get(key);
      view.setAttribute(key.getSecond(), key.getFirst(), value);
    }
  }
}
