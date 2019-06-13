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
package com.android.tools.idea.resources.aar;

import com.android.ide.common.rendering.api.ArrayResourceValue;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.intellij.util.containers.ObjectIntHashMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resource item representing an array resource.
 */
final class AarArrayResourceItem extends AbstractAarValueResourceItem implements ArrayResourceValue {
  @NotNull private final List<String> myElements;

  /**
   * Initializes the resource.
   *
   * @param name the name of the resource
   * @param sourceFile the source file containing definition of the resource
   * @param visibility the visibility of the resource
   * @param elements the elements  or the array
   */
  AarArrayResourceItem(@NotNull String name,
                       @NotNull AarSourceFile sourceFile,
                       @NotNull ResourceVisibility visibility,
                       @NotNull List<String> elements) {
    super(ResourceType.ARRAY, name, sourceFile, visibility);
    myElements = elements;
  }

  @Override
  public int getElementCount() {
    return myElements.size();
  }

  @Override
  @NotNull
  public String getElement(int index) {
    return myElements.get(index);
  }

  @Override
  public Iterator<String> iterator() {
    return myElements.iterator();
  }

  @Override
  @Nullable
  public String getValue() {
    return myElements.isEmpty() ? null : myElements.get(0);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    AarArrayResourceItem other = (AarArrayResourceItem) obj;
    return myElements.equals(other.myElements);
  }

  @Override
  void serialize(@NotNull Base128OutputStream stream,
                 @NotNull ObjectIntHashMap<String> configIndexes,
                 @NotNull ObjectIntHashMap<AarSourceFile> sourceFileIndexes,
                 @NotNull ObjectIntHashMap<ResourceNamespace.Resolver> namespaceResolverIndexes) throws IOException {
    super.serialize(stream, configIndexes, sourceFileIndexes, namespaceResolverIndexes);
    stream.writeInt(myElements.size());
    for (String element : myElements) {
      stream.writeString(element);
    }
  }

  /**
   * Creates an AarArrayResourceItem by reading its contents of the given stream.
   */
  @NotNull
  static AarArrayResourceItem deserialize(@NotNull Base128InputStream stream,
                                          @NotNull String name,
                                          @NotNull ResourceVisibility visibility,
                                          @NotNull AarSourceFile sourceFile,
                                          @NotNull ResourceNamespace.Resolver resolver) throws IOException {
    int n = stream.readInt();
    List<String> elements = n == 0 ? Collections.emptyList() : new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      elements.add(stream.readString());
    }
    AarArrayResourceItem item = new AarArrayResourceItem(name, sourceFile, visibility, elements);
    item.setNamespaceResolver(resolver);
    return item;
  }
}
