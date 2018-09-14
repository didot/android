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
package com.android.tools.idea.res.aar;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.AbstractResourceRepository;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceTable;
import com.android.resources.ResourceType;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Common superclass for {@link AarSourceResourceRepository} and {@link AarProtoResourceRepository}.
 */
public abstract class AbstractAarResourceRepository extends AbstractResourceRepository implements AarResourceRepository {
  @NotNull private final ResourceNamespace myNamespace;
  @NotNull private final String myLibraryName;
  @NotNull protected final ResourceTable myFullTable = new ResourceTable();

  protected AbstractAarResourceRepository(@NotNull ResourceNamespace namespace, @NotNull String libraryName) {
    myNamespace = namespace;
    myLibraryName = libraryName;
  }

  @Override
  @NotNull
  protected final ResourceTable getFullTable() {
    return myFullTable;
  }

  @Override
  @Nullable
  @Contract("_, _, true -> !null")
  protected final ListMultimap<String, ResourceItem> getMap(@NotNull ResourceNamespace namespace, @NotNull ResourceType type,
                                                            boolean create) {
    ListMultimap<String, ResourceItem> multimap = myFullTable.get(namespace, type);
    if (multimap == null && create) {
      multimap = ArrayListMultimap.create();
      myFullTable.put(namespace, type, multimap);
    }
    return multimap;
  }

  @Override
  @NotNull
  public final ResourceNamespace getNamespace() {
    return myNamespace;
  }

  @Override
  @NotNull
  public final String getLibraryName() {
    return myLibraryName;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return myLibraryName;
  }
}
