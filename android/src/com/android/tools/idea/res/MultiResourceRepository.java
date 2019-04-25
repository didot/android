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
package com.android.tools.idea.res;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceTable;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.resources.ResourceType;
import com.android.tools.idea.resources.aar.AarResourceRepository;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.SetMultimap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.concurrent.GuardedBy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A super class for several of the other repositories. Its only purpose is to be able to combine
 * multiple resource repositories and expose it as a single one, applying the “override” semantics
 * of resources: earlier children defining the same resource namespace/type/name combination will
 * replace/hide any subsequent definitions of the same resource.
 *
 * <p>In the resource repository hierarchy, MultiResourceRepository is an internal node, never a leaf.
 */
@SuppressWarnings("InstanceGuardedByStatic") // TODO: The whole locking scheme for resource repositories needs to be reworked.
public abstract class MultiResourceRepository extends LocalResourceRepository {
  @GuardedBy("ITEM_MAP_LOCK")
  private ImmutableList<LocalResourceRepository> myLocalResources;
  @GuardedBy("ITEM_MAP_LOCK")
  private ImmutableList<AarResourceRepository> myLibraryResources;
  /** A concatenation of {@link #myLocalResources} and {@link #myLibraryResources}. */
  @GuardedBy("ITEM_MAP_LOCK")
  private ImmutableList<ResourceRepository> myChildren;
  /** Leaf resource repositories keyed by namespace. */
  @GuardedBy("ITEM_MAP_LOCK")
  private ImmutableListMultimap<ResourceNamespace, SingleNamespaceResourceRepository> myLeafsByNamespace;
  /** Contained single-namespace resource repositories keyed by namespace. */
  @GuardedBy("ITEM_MAP_LOCK")
  private ImmutableListMultimap<ResourceNamespace, SingleNamespaceResourceRepository> myRepositoriesByNamespace;

  @GuardedBy("ITEM_MAP_LOCK")
  private long[] myModificationCounts;

  @GuardedBy("ITEM_MAP_LOCK")
  private ResourceTable myFullTable;

  @GuardedBy("ITEM_MAP_LOCK")
  private final ResourceTable myCachedMaps = new ResourceTable();

  @GuardedBy("ITEM_MAP_LOCK")
  private Map<String, DataBindingLayoutInfo> myDataBindingResourceFiles = new HashMap<>();

  @GuardedBy("ITEM_MAP_LOCK")
  private long myDataBindingResourceFilesModificationCount = Long.MIN_VALUE;

  MultiResourceRepository(@NotNull String displayName) {
    super(displayName);
  }

  protected void setChildren(@NotNull List<? extends LocalResourceRepository> localResources,
                             @NotNull Collection<? extends AarResourceRepository> libraryResources) {
    synchronized (ITEM_MAP_LOCK) {
      if (myLocalResources != null) {
        for (LocalResourceRepository child : myLocalResources) {
          child.removeParent(this);
        }
      }
      setModificationCount(ourModificationCounter.incrementAndGet());
      myLocalResources = ImmutableList.copyOf(localResources);
      myLibraryResources = ImmutableList.copyOf(libraryResources);
      myChildren = ImmutableList.<ResourceRepository>builder().addAll(myLocalResources).addAll(myLibraryResources).build();

      ImmutableListMultimap.Builder<ResourceNamespace, SingleNamespaceResourceRepository> mapBuilder = ImmutableListMultimap.builder();
      computeLeafs(this, mapBuilder);
      myLeafsByNamespace = mapBuilder.build();

      mapBuilder = ImmutableListMultimap.builder();
      computeNamespaceMap(this, mapBuilder);
      myRepositoriesByNamespace = mapBuilder.build();

      myModificationCounts = new long[localResources.size()];
      if (localResources.size() == 1) {
        // Make sure that the modification count of the child and the parent are same. This is
        // done so that we can return child's modification count, instead of ours.
        LocalResourceRepository child = localResources.get(0);
        child.setModificationCount(getModificationCount());
      }
      int i = 0;
      for (LocalResourceRepository child : myLocalResources) {
        child.addParent(this);
        myModificationCounts[i++] = child.getModificationCount();
      }
      myFullTable = null;
      myCachedMaps.clear();

      invalidateParentCaches();
    }
  }

  @GuardedBy("ITEM_MAP_LOCK")
  private static void computeLeafs(@NotNull ResourceRepository repository,
                                   @NotNull ImmutableListMultimap.Builder<ResourceNamespace, SingleNamespaceResourceRepository> result) {
    if (repository instanceof MultiResourceRepository) {
      for (ResourceRepository child : ((MultiResourceRepository)repository).myChildren) {
        computeLeafs(child, result);
      }
    } else {
      for (SingleNamespaceResourceRepository resourceRepository : repository.getLeafResourceRepositories()) {
        result.put(resourceRepository.getNamespace(), resourceRepository);
      }
    }
  }

  @GuardedBy("ITEM_MAP_LOCK")
  private static void computeNamespaceMap(
      @NotNull ResourceRepository repository,
      @NotNull ImmutableListMultimap.Builder<ResourceNamespace, SingleNamespaceResourceRepository> result) {
    if (repository instanceof SingleNamespaceResourceRepository) {
      SingleNamespaceResourceRepository singleNamespaceRepository = (SingleNamespaceResourceRepository)repository;
      ResourceNamespace namespace = singleNamespaceRepository.getNamespace();
      result.put(namespace, singleNamespaceRepository);
    }
    else if (repository instanceof MultiResourceRepository) {
      for (ResourceRepository child : ((MultiResourceRepository)repository).myChildren) {
        computeNamespaceMap(child, result);
      }
    }
  }

  public ImmutableList<LocalResourceRepository> getLocalResources() {
    synchronized (ITEM_MAP_LOCK) {
      return myLocalResources;
    }
  }

  public ImmutableList<AarResourceRepository> getLibraryResources() {
    synchronized (ITEM_MAP_LOCK) {
      return myLibraryResources;
    }
  }

  @NotNull
  public final List<ResourceRepository> getChildren() {
    synchronized (ITEM_MAP_LOCK) {
      return myChildren;
    }
  }

  /**
   * Returns resource repositories for the given namespace. Each of the returned repositories is guaranteed to implement
   * the {@link SingleNamespaceResourceRepository} interface. In case of nested single-namespace repositories only the outermost
   * repositories are returned. Collectively the returned repositories are guaranteed to contain all resources in the given namespace
   * contained in this repository.
   *
   * @param namespace the namespace to return resource repositories for
   * @return a list of namespaces for the given namespace
   */
  @NotNull
  public final List<ResourceRepository> getRepositoriesForNamespace(@NotNull ResourceNamespace namespace) {
    synchronized (ITEM_MAP_LOCK) {
      return ImmutableList.copyOf(myRepositoriesByNamespace.get(namespace));
    }
  }

  @Override
  public long getModificationCount() {
    synchronized (ITEM_MAP_LOCK) {
      if (myLocalResources.size() == 1) {
        return myLocalResources.get(0).getModificationCount();
      }

      // See if any of the delegates have changed.
      boolean changed = false;
      for (int i = 0; i < myLocalResources.size(); i++) {
        LocalResourceRepository child = myLocalResources.get(i);
        long rev = child.getModificationCount();
        if (rev != myModificationCounts[i]) {
          myModificationCounts[i] = rev;
          changed = true;
        }
      }

      if (changed) {
        setModificationCount(ourModificationCounter.incrementAndGet());
      }

      return super.getModificationCount();
    }
  }

  @Override
  @Nullable
  public DataBindingLayoutInfo getDataBindingLayoutInfo(String layoutName) {
    synchronized (ITEM_MAP_LOCK) {
      for (LocalResourceRepository child : myLocalResources) {
        DataBindingLayoutInfo info = child.getDataBindingLayoutInfo(layoutName);
        if (info != null) {
          return info;
        }
      }
      return null;
    }
  }

  @Override
  @NotNull
  public Map<String, DataBindingLayoutInfo> getDataBindingResourceFiles() {
    synchronized (ITEM_MAP_LOCK) {
      long modificationCount = getModificationCount();
      if (myDataBindingResourceFilesModificationCount == modificationCount) {
        return myDataBindingResourceFiles;
      }
      Map<String, DataBindingLayoutInfo> selected = new HashMap<>();
      for (LocalResourceRepository child : myLocalResources) {
        Map<String, DataBindingLayoutInfo> childFiles = child.getDataBindingResourceFiles();
        if (childFiles != null) {
          selected.putAll(childFiles);
        }
      }
      myDataBindingResourceFiles = Collections.unmodifiableMap(selected);
      myDataBindingResourceFilesModificationCount = modificationCount;
      return myDataBindingResourceFiles;
    }
  }

  @Override
  @NotNull
  public Set<ResourceNamespace> getNamespaces() {
    synchronized (ITEM_MAP_LOCK) {
      return ImmutableSet.copyOf(myRepositoriesByNamespace.keySet());
    }
  }

  @NotNull
  @Override
  protected ResourceTable getFullTable() {
    synchronized (ITEM_MAP_LOCK) {
      if (myFullTable == null) {
        if (myLocalResources.size() == 1 && myLibraryResources.isEmpty()) {
          myFullTable = myLocalResources.get(0).getFullTablePackageAccessible();
        }
        else {
          myFullTable = new ResourceTable();
          for (ResourceNamespace namespace : getNamespaces()) {
            for (ResourceType type : ResourceType.values()) {
              ListMultimap<String, ResourceItem> map = getMap(namespace, type, false);
              if (map != null) {
                myFullTable.put(namespace, type, map);
              }
            }
          }
        }
      }

      return myFullTable;
    }
  }

  @Override
  @Nullable
  protected ListMultimap<String, ResourceItem> getMap(@NotNull ResourceNamespace namespace,
                                                      @NotNull ResourceType type,
                                                      boolean create) {
    synchronized (ITEM_MAP_LOCK) {
      // Should I assert !create here? If we try to manipulate the cache it won't work right...
      ListMultimap<String, ResourceItem> map = myCachedMaps.get(namespace, type);
      if (map != null) {
        return map;
      }

      if (myLocalResources.size() == 1 && myLibraryResources.isEmpty()) {
        return myLocalResources.get(0).getOrCreateMapPackageAccessible(namespace, type);
      }

      ImmutableList<SingleNamespaceResourceRepository> repositoriesForNamespace = myLeafsByNamespace.get(namespace);
      if (repositoriesForNamespace.size() == 1) {
        return ArrayListMultimap.create(repositoriesForNamespace.get(0).getResources(namespace, type));
      } else {
        // Merge all items of the given type.
        map = ArrayListMultimap.create();
        SetMultimap<String, String> seenQualifiers = HashMultimap.create();
        for (ResourceRepository child : repositoriesForNamespace) {
          ListMultimap<String, ResourceItem> items = child.getResources(namespace, type);
          for (ResourceItem item : items.values()) {
            String name = item.getName();
            String qualifiers = item.getConfiguration().getQualifierString();
            if (type == ResourceType.STYLEABLE || type == ResourceType.ID || !map.containsKey(name) ||
                !seenQualifiers.containsEntry(name, qualifiers)) {
              // We only add a duplicate item if there isn't an item with the same qualifiers and it is
              // not a styleable or an id. Styleables and ids are allowed to be defined in multiple
              // places even with the same qualifiers.
              map.put(name, item);
              seenQualifiers.put(name, qualifiers);
            }
          }
        }
      }

      myCachedMaps.put(namespace, type, map);

      return map;
    }
  }

  @Override
  public boolean hasResources(@NotNull ResourceNamespace namespace, @NotNull ResourceType type) {
    synchronized (ITEM_MAP_LOCK) {
      if (myChildren.size() == 1) {
        return myChildren.get(0).hasResources(namespace, type);
      }

      if (this instanceof SingleNamespaceResourceRepository) {
        if (namespace.equals(((SingleNamespaceResourceRepository)this).getNamespace())) {
          for (ResourceRepository child : myChildren) {
            if (child.hasResources(namespace, type)) {
              return true;
            }
          }
        }
        return false;
      }

      Collection<SingleNamespaceResourceRepository> repositories = myRepositoriesByNamespace.get(namespace);
      for (ResourceRepository repository : repositories) {
        if (repository.hasResources(namespace, type)) {
          return true;
        }
      }
      return false;
    }
  }

  @Override
  public void dispose() {
    synchronized (ITEM_MAP_LOCK) {
      for (LocalResourceRepository child : myLocalResources) {
        child.removeParent(this);
      }
    }
  }

  /**
   * Notifies this delegating repository that the given dependent repository has invalidated all resources.
   */
  public void invalidateCache(@NotNull LocalResourceRepository repository) {
    synchronized (ITEM_MAP_LOCK) {
      assert myChildren.contains(repository) : repository;

      myCachedMaps.clear();
      myFullTable = null;
      setModificationCount(ourModificationCounter.incrementAndGet());

      invalidateParentCaches();
    }
  }

  /**
   * Notifies this delegating repository that the given dependent repository has invalidated
   * resources of the given types in the given namespace.
   */
  public void invalidateCache(@NotNull LocalResourceRepository repository, @NotNull ResourceNamespace namespace,
                              @NotNull ResourceType... types) {
    synchronized (ITEM_MAP_LOCK) {
      assert myChildren.contains(repository) : repository;

      for (ResourceType type : types) {
        myCachedMaps.remove(namespace, type);
      }

      myFullTable = null;
      setModificationCount(ourModificationCounter.incrementAndGet());

      invalidateParentCaches(namespace, types);
    }
  }

  @Override
  boolean isScanPending(@NotNull PsiFile psiFile) {
    synchronized (ITEM_MAP_LOCK) {
      assert ApplicationManager.getApplication().isUnitTestMode();
      for (LocalResourceRepository child : myLocalResources) {
        if (child.isScanPending(psiFile)) {
          return true;
        }
      }

      return false;
    }
  }

  @Override
  public void sync() {
    super.sync();

    for (LocalResourceRepository childRepository : getLocalResources()) {
      childRepository.sync();
    }
  }

  @Override
  @NotNull
  protected Set<VirtualFile> computeResourceDirs() {
    synchronized (ITEM_MAP_LOCK) {
      Set<VirtualFile> result = new HashSet<>();
      for (LocalResourceRepository resourceRepository : myLocalResources) {
        result.addAll(resourceRepository.computeResourceDirs());
      }
      return result;
    }
  }

  @Override
  @NotNull
  public Collection<SingleNamespaceResourceRepository> getLeafResourceRepositories() {
    synchronized (ITEM_MAP_LOCK) {
      return myLeafsByNamespace.values();
    }
  }
}
