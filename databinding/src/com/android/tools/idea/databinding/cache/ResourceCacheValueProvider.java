/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.databinding.cache;

import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.util.CachedValueProvider;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract public class ResourceCacheValueProvider<T> implements CachedValueProvider<T>, ModificationTracker {
  ModificationTracker[] myAdditionalTrackers;
  private ModificationTracker myTracker = new ModificationTracker() {
    private long myLastVersion = -1;
    private long myVersion = 0;
    @Override
    public long getModificationCount() {
      LocalResourceRepository moduleResources = ResourceRepositoryManager.getInstance(myFacet).getExistingModuleResources();
      // make sure it changes if facet's module resource availability changes
      long version = moduleResources == null ? Integer.MIN_VALUE : moduleResources.getModificationCount();
      if (version != myLastVersion) {
        myLastVersion = version;
        myVersion ++;
      }
      return myVersion;
    }
  };
  private final AndroidFacet myFacet;
  private final Object myComputeLock;

  public ResourceCacheValueProvider(@NotNull AndroidFacet facet, @Nullable Object computeLock,
                                    @NotNull ModificationTracker... additionalTrackers) {
    myFacet = facet;
    myComputeLock = computeLock;
    myAdditionalTrackers = additionalTrackers;
  }

  public AndroidFacet getFacet() {
    return myFacet;
  }

  @Override
  public long getModificationCount() {
    return myTracker.getModificationCount();
  }

  @NotNull
  @Override
  public final Result<T> compute() {
    if (ResourceRepositoryManager.getInstance(myFacet).getExistingModuleResources() == null) {
      return Result.create(defaultValue(), myTracker, myAdditionalTrackers);
    }
    return Result.create(computeWithLock(), myTracker, myAdditionalTrackers);
  }

  private T computeWithLock() {
    if (myComputeLock == null) {
      return doCompute();
    }

    synchronized (myComputeLock) {
      return doCompute();
    }
  }

  protected abstract T doCompute();

  protected abstract T defaultValue();
}

