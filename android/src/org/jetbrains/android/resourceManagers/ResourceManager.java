/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.resourceManagers;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.AbstractResourceRepository;
import com.android.ide.common.resources.ResourceItem;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.res.ResourceHelper;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.Predicate;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.wrappers.FileResourceElementWrapper;
import org.jetbrains.android.dom.wrappers.LazyValueResourceElementWrapper;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class ResourceManager {
  private interface FileResourceProcessor {
    void process(@NotNull VirtualFile resFile, @NotNull String resName, @Nullable String libraryName);
  }

  protected final Project myProject;

  protected ResourceManager(@NotNull Project project) {
    myProject = project;
  }

  /**
   * Returns the primary namespace associated with this resource manager.
   * @deprecated This method is temporary and may be removed in future.
   */
  @Deprecated
  @NotNull
  protected abstract ResourceNamespace getResourceNamespace();

  /**
   * Returns the resource repository associated with this resource manager.
   */
  @NotNull
  public abstract AbstractResourceRepository getResourceRepository();

  /**
   * Returns all the resource directories for this module <b>and all of its module dependencies</b>
   * grouped by library name. A {@code null} key is used for the library name for system, application
   * and folder resources.
   */
  @NotNull
  public abstract Multimap<String, VirtualFile> getAllResourceDirs();

  /** Returns all the resource directories for this module only */
  @NotNull
  public abstract List<VirtualFile> getResourceDirs();

  /** Returns true if the given directory is a resource directory in this module */
  public abstract boolean isResourceDir(@NotNull VirtualFile dir);

  @Nullable
  public abstract AttributeDefinitions getAttributeDefinitions();

  private void processFileResources(@NotNull Multimap<String, VirtualFile> resDirs,
                                    @NotNull ResourceFolderType folderType,
                                    @NotNull FileResourceProcessor processor) {
    for (Map.Entry<String, Collection<VirtualFile>> entry : resDirs.asMap().entrySet()) {
      for (VirtualFile resSubdir : AndroidResourceUtil.getResourceSubdirs(folderType,  entry.getValue())) {
        ResourceFolderType resType = ResourceFolderType.getFolderType(resSubdir.getName());

        if (resType != null) {
          assert folderType.equals(resType);
          String resTypeName = resType.getName();
          for (VirtualFile resFile : resSubdir.getChildren()) {
            String resName = AndroidCommonUtils.getResourceName(resTypeName, resFile.getName());

            if (!resFile.isDirectory() && isResourcePublic(resTypeName, resName)) {
              processor.process(resFile, resName, entry.getKey());
            }
          }
        }
      }
    }
  }

  @NotNull
  private Multimap<String, VirtualFile> getResourceDirsByLibraryName(boolean withDependencies) {
    Multimap<String, VirtualFile> resDirs;
    if (withDependencies) {
      resDirs = getAllResourceDirs();
    } else {
      resDirs = HashMultimap.create();
      resDirs.putAll(null, getResourceDirs());
    }
    return resDirs;
  }

  public boolean isResourcePublic(@NotNull String type, @NotNull String name) {
    return true;
  }

  @NotNull
  public List<PsiFile> findResourceFiles(@NotNull ResourceFolderType resourceType) {
    return findResourceFiles(resourceType, null, true, true);
  }

  @NotNull
  public List<PsiFile> findResourceFiles(@NotNull ResourceFolderType resourceFolderType,
                                         @Nullable String resName1,
                                         boolean distinguishDelimitersInName,
                                         boolean withDependencies) {
    List<PsiFile> result = new ArrayList<>();
    processFileResources(getResourceDirsByLibraryName(withDependencies), resourceFolderType, (resFile, resName, libraryName) -> {
      if (resName1 == null || AndroidUtils.equal(resName1, resName, distinguishDelimitersInName)) {
        PsiFile file = AndroidPsiUtils.getPsiFileSafely(myProject, resFile);
        if (file != null) {
          result.add(file);
        }
      }
    });
    return result;
  }

  @NotNull
  protected final Multimap<String, XmlFile> findValueResourcesByLibraryName() {
    Multimap<String, XmlFile> result = HashMultimap.create();
    processFileResources(getAllResourceDirs(), ResourceFolderType.VALUES, (resFile, resName, libraryName) -> {
        PsiFile file = AndroidPsiUtils.getPsiFileSafely(myProject, resFile);
        if (file instanceof XmlFile) {
          result.put(libraryName, (XmlFile)file);
        }
      });
    return result;
  }

  @Nullable
  public ResourceType getValueResourceType(@NotNull XmlTag tag) {
    ResourceFolderType fileResType = getFileResourceFolderType(tag.getContainingFile());
    if (ResourceFolderType.VALUES == fileResType) {
      return AndroidResourceUtil.getResourceTypeForResourceTag(tag);
    }
    return null;
  }

  @Nullable
  public ResourceFolderType getFileResourceFolderType(@NotNull PsiFile file) {
    return ApplicationManager.getApplication().runReadAction((Computable<ResourceFolderType>)() -> {
      PsiDirectory dir = file.getContainingDirectory();
      if (dir == null) {
        return null;
      }

      PsiDirectory possibleResDir = dir.getParentDirectory();
      if (possibleResDir == null || !isResourceDir(possibleResDir.getVirtualFile())) {
        return null;
      }
      return ResourceFolderType.getFolderType(dir.getName());
    });
  }

  @Nullable
  public String getFileResourceType(@NotNull PsiFile file) {
    ResourceFolderType folderType = getFileResourceFolderType(file);
    return folderType == null ? null : folderType.getName();
  }

  @NotNull
  private Collection<AbstractResourceRepository> getLeafResourceRepositories() {
    List<AbstractResourceRepository> result = new ArrayList<>();
    getResourceRepository().getLeafResourceRepositories(result);
    return result;
  }

  /**
   * Searches only declarations such as "@+id/...".
   */
  @NotNull
  public List<XmlAttributeValue> findIdDeclarations(@NotNull ResourceNamespace namespace, @NotNull String id) {
    if (!isResourcePublic(ResourceType.ID.getName(), id)) {
      return Collections.emptyList();
    }

    Set<VirtualFile> files = getFilesDeclaringId(namespace, id);

    return findIdUsagesFromFiles(files, attributeValue -> {
      if (AndroidResourceUtil.isIdDeclaration(attributeValue)) {
        String idInAttr = AndroidResourceUtil.getResourceNameByReferenceText(attributeValue.getValue());
        return id.equals(idInAttr);
      }
      return false;
    });
  }

  /**
   * Searches only usages of the given id such as app:constraint_referenced_ids="[id1],[id2],...".
   */
  @NotNull
  public List<XmlAttributeValue> findConstraintReferencedIds(@NotNull ResourceNamespace namespace, @NotNull String id) {
    if (!isResourcePublic(ResourceType.ID.getName(), id)) {
      return Collections.emptyList();
    }

    Set<VirtualFile> files = getFilesDeclaringId(namespace, id);

    return findIdUsagesFromFiles(files, attributeValue -> {
      if (AndroidResourceUtil.isConstraintReferencedIds(attributeValue)) {
        String ids = attributeValue.getValue();
        if (ids != null) {
          return Arrays.stream(ids.split(",")).anyMatch(s -> s.equals(id));
        }
      }
      return false;
    });
  }

  @NotNull
  private Set<VirtualFile> getFilesDeclaringId(@NotNull ResourceNamespace namespace, @NotNull String id) {
    Set<VirtualFile> files = new HashSet<>();
    for (AbstractResourceRepository repository : getLeafResourceRepositories()) {
      List<ResourceItem> items = repository.getResourceItems(namespace, ResourceType.ID, id);
      for (ResourceItem item : items) {
        VirtualFile file = ResourceHelper.getSourceAsVirtualFile(item);
        if (file != null) {
          files.add(file);
        }
      }
    }
    return files;
  }

  private List<XmlAttributeValue> findIdUsagesFromFiles(@NotNull Set<VirtualFile> fileSet,
                                                        @NotNull Predicate<XmlAttributeValue> condition) {
    List<XmlAttributeValue> usages = new ArrayList<>();

    PsiManager psiManager = PsiManager.getInstance(myProject);

    for (VirtualFile file : fileSet) {
      if (fileSet.contains(file)) {
        PsiFile psiFile = psiManager.findFile(file);
        if (psiFile instanceof XmlFile) {
          psiFile.accept(new XmlRecursiveElementVisitor() {
            @Override
            public void visitXmlAttributeValue(XmlAttributeValue attributeValue) {
              if (condition.apply(attributeValue)) {
                usages.add(attributeValue);
              }
            }
          });
        }
      }
    }
    return usages;
  }

  public List<ResourceElement> findValueResources(@NotNull ResourceNamespace namespace, @NotNull String resType, @NotNull String resName) {
    return findValueResources(namespace, resType, resName, true);
  }

  // Not recommended to use, because it is too slow.
  @NotNull
  public List<ResourceElement> findValueResources(@NotNull ResourceNamespace namespace, @NotNull String resourceType,
                                                  @NotNull String resourceName, boolean distinguishDelimitersInName) {
    List<ValueResourceInfoImpl> resources =
        findValueResourceInfos(namespace, resourceType, resourceName, distinguishDelimitersInName, false);
    List<ResourceElement> result = new ArrayList<>();

    for (ValueResourceInfoImpl resource : resources) {
      ResourceElement domElement = resource.computeDomElement();

      if (domElement != null) {
        result.add(domElement);
      }
    }
    return result;
  }

  /**
   * @deprecated Use {@link #collectLazyResourceElements(ResourceNamespace, String, String, boolean, PsiElement, Collection)}
   * Preserved temporarily for compatibility with the Kotlin plugin.
   */
  @Deprecated
  public void collectLazyResourceElements(@NotNull String resType, @NotNull String resName,
                                          boolean withAttrs, @NotNull PsiElement context, @NotNull Collection<PsiElement> elements) {
    collectLazyResourceElements(getResourceNamespace(), resType, resName, withAttrs, context, elements);
  }

  public void collectLazyResourceElements(@NotNull ResourceNamespace namespace, @NotNull String resType, @NotNull String resName,
                                          boolean withAttrs, @NotNull PsiElement context, @NotNull Collection<PsiElement> elements) {
    List<ValueResourceInfoImpl> valueResources = findValueResourceInfos(namespace, resType, resName, false, withAttrs);

    for (ValueResourceInfo resource : valueResources) {
      elements.add(new LazyValueResourceElementWrapper(resource, context));
    }
    if (resType.equals("id")) {
      elements.addAll(findIdDeclarations(namespace, resName));
    }
    if (elements.isEmpty()) {
      ResourceFolderType folderType = ResourceFolderType.getTypeByName(resType);
      if (folderType != null) {
        for (PsiFile file : findResourceFiles(folderType, resName, false, true)) {
          elements.add(new FileResourceElementWrapper(file));
        }
      }
    }
  }

  /**
   * Finds resources defined in the current project matching the given namespace, type and name.
   *
   * @param namespace the namespace of the resources to find
   * @param resourceType the type of the resources to find, '+' first character means "id".
   * @param resourceName the name of the resources to find
   * @param distinguishDelimitersInName true for exact name match, false for considering all word
   *     delimiters equivalent, e.g. for matching an identifier from R.java
   * @param searchAttrs whether to consider "attr" resources or not
   * @return the matching resources
   */
  @NotNull
  public List<ValueResourceInfoImpl> findValueResourceInfos(@NotNull ResourceNamespace namespace, @NotNull String resourceType,
                                                            @NotNull String resourceName, boolean distinguishDelimitersInName,
                                                            boolean searchAttrs) {
    ResourceType type = resourceType.startsWith("+") ? ResourceType.ID : ResourceType.fromClassName(resourceType);
    if (type == null) {
      return Collections.emptyList();
    }

    return findValueResourceInfos(namespace, type, resourceName, distinguishDelimitersInName, searchAttrs);
  }

  /**
   * Finds resources defined in the current project matching the given namespace, type and name.
   *
   * @param namespace the namespace of the resources to find
   * @param resourceType the type of the resources to find
   * @param resourceName the name of the resources to find
   * @param distinguishDelimitersInName true for exact name match, false for considering all word
   *     delimiters equivalent, e.g. for matching an identifier from R.java
   * @param searchAttrs whether to consider "attr" resources or not
   * @return the matching resources
   */
  @NotNull
  public List<ValueResourceInfoImpl> findValueResourceInfos(@NotNull ResourceNamespace namespace, @NotNull ResourceType resourceType,
                                                            @NotNull String resourceName, boolean distinguishDelimitersInName,
                                                            boolean searchAttrs) {
    if (!AndroidResourceUtil.VALUE_RESOURCE_TYPES.contains(resourceType) && (resourceType != ResourceType.ATTR || !searchAttrs)) {
      return Collections.emptyList();
    }

    List<ValueResourceInfoImpl> result = new ArrayList<>();
    for (AbstractResourceRepository repository : getLeafResourceRepositories()) {
      List<ResourceItem> items;
      if (distinguishDelimitersInName) {
        items = repository.getResourceItems(namespace, resourceType, resourceName);
      } else {
        items = repository.getResourceItems(namespace, resourceType, item -> AndroidUtils.equal(resourceName, item.getName(), false));
      }
      for (ResourceItem item : items) {
        VirtualFile file = ResourceHelper.getSourceAsVirtualFile(item);
        if (file != null && isValueResourceFile(file)) {
          result.add(new ValueResourceInfoImpl(item, file, myProject));
        }
      }
    }

    return result;
  }

  private static boolean isValueResourceFile(@NotNull VirtualFile file) {
    return ResourceFolderType.getFolderType(file.getParent().getName()) == ResourceFolderType.VALUES;
  }
}
