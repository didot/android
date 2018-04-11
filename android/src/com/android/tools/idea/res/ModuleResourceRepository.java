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
package com.android.tools.idea.res;

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @see ResourceRepositoryManager#getModuleResources(boolean)
 */
final class ModuleResourceRepository extends MultiResourceRepository {
  private final AndroidFacet myFacet;
  private final ResourceFolderManager.ResourceFolderListener myResourceFolderListener = (facet, folders, added, removed) -> updateRoots();
  private final ResourceFolderManager myResourceFolderManager;

  /**
   * Creates a new resource repository for the given module, <b>not</b> including its dependent modules.
   *
   * @param facet the facet for the module
   * @return the resource repository
   */
  @NotNull
  static LocalResourceRepository create(@NotNull AndroidFacet facet) {
    if (!facet.requiresAndroidModel()) {
      // Always just a single resource folder: simple
      VirtualFile primaryResourceDir = ResourceFolderManager.getInstance(facet).getPrimaryFolder();
      if (primaryResourceDir == null) {
        return new EmptyRepository();
      }
      return ResourceFolderRegistry.get(facet, primaryResourceDir);
    }

    ResourceFolderManager folderManager = ResourceFolderManager.getInstance(facet);
    List<VirtualFile> resourceDirectories = folderManager.getFolders();
    List<LocalResourceRepository> resources = Lists.newArrayListWithExpectedSize(resourceDirectories.size() + 1);
    for (VirtualFile resourceDirectory : resourceDirectories) {
      ResourceFolderRepository repository = ResourceFolderRegistry.get(facet, resourceDirectory);
      resources.add(repository);
    }

    DynamicResourceValueRepository dynamicResources = DynamicResourceValueRepository.create(facet);
    resources.add(dynamicResources);

    // We create a ModuleResourceRepository even if resources.isEmpty(), because we may
    // dynamically add children to it later (in updateRoots)
    final ModuleResourceRepository repository = new ModuleResourceRepository(facet, resources);
    Disposer.register(repository, dynamicResources);

    return repository;
  }

  private ModuleResourceRepository(@NotNull AndroidFacet facet, @NotNull List<? extends LocalResourceRepository> delegates) {
    super(facet.getModule().getName(), delegates);
    myFacet = facet;

    // Subscribe to update the roots when the resource folders change
    myResourceFolderManager = ResourceFolderManager.getInstance(myFacet);
    myResourceFolderManager.addListener(myResourceFolderListener);
  }

  private void updateRoots() {
    updateRoots(myResourceFolderManager.getFolders());
  }

  @VisibleForTesting
  void updateRoots(List<VirtualFile> resourceDirectories) {
    // Non-folder repositories: Always kept last in the list
    List<LocalResourceRepository> other = null;

    // Compute current roots
    Map<VirtualFile, ResourceFolderRepository> map = Maps.newHashMap();
    for (LocalResourceRepository repository : getChildren()) {
      if (repository instanceof ResourceFolderRepository) {
        ResourceFolderRepository folderRepository = (ResourceFolderRepository)repository;
        VirtualFile resourceDir = folderRepository.getResourceDir();
        map.put(resourceDir, folderRepository);
      }
      else {
        assert repository instanceof DynamicResourceValueRepository;
        if (other == null) {
          other = Lists.newArrayList();
        }
        other.add(repository);
      }
    }

    // Compute new resource directories (it's possible for just the order to differ, or
    // for resource dirs to have been added and/or removed)
    Set<VirtualFile> newDirs = Sets.newHashSet(resourceDirectories);
    List<LocalResourceRepository> resources = Lists.newArrayListWithExpectedSize(newDirs.size() + (other != null ? other.size() : 0));
    for (VirtualFile dir : resourceDirectories) {
      ResourceFolderRepository repository = map.get(dir);
      if (repository == null) {
        repository = ResourceFolderRegistry.get(myFacet, dir);
      }
      else {
        map.remove(dir);
      }
      resources.add(repository);
    }

    if (other != null) {
      resources.addAll(other);
    }

    if (resources.equals(getChildren())) {
      // Nothing changed (including order); nothing to do
      assert map.isEmpty(); // shouldn't have created any new ones
      return;
    }

    for (ResourceFolderRepository removed : map.values()) {
      removed.removeParent(this);
    }

    setChildren(resources);
  }

  @Override
  public void dispose() {
    super.dispose();

    myResourceFolderManager.removeListener(myResourceFolderListener);
  }

  /**
   * For testing: creates a project with a given set of resource roots; this allows tests to check
   * this repository without creating a gradle project setup etc
   */
  @NotNull
  @VisibleForTesting
  public static ModuleResourceRepository createForTest(@NotNull AndroidFacet facet, @NotNull Collection<VirtualFile> resourceDirectories) {
    return createForTest(facet, resourceDirectories, ResourceNamespace.TODO, null);
  }

  @NotNull
  @VisibleForTesting
  public static ModuleResourceRepository createForTest(@NotNull AndroidFacet facet,
                                                       @NotNull Collection<VirtualFile> resourceDirectories,
                                                       @NotNull ResourceNamespace namespace,
                                                       @Nullable DynamicResourceValueRepository dynamicResourceValueRepository) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    List<LocalResourceRepository> delegates = new ArrayList<>(resourceDirectories.size() + 1);

    for (VirtualFile resourceDirectory : resourceDirectories) {
      delegates.add(ResourceFolderRegistry.get(facet, resourceDirectory, namespace));
    }

    if (dynamicResourceValueRepository != null) {
      delegates.add(dynamicResourceValueRepository);
    }
    return new ModuleResourceRepository(facet, delegates);
  }
}
