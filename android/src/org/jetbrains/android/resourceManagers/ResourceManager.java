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

import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.AndroidPsiUtils;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.xml.DomElement;
import org.jetbrains.android.AndroidIdIndex;
import org.jetbrains.android.AndroidValueResourcesIndex;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.Resources;
import org.jetbrains.android.dom.wrappers.FileResourceElementWrapper;
import org.jetbrains.android.dom.wrappers.LazyValueResourceElementWrapper;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.android.util.ResourceEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static java.util.Collections.addAll;

/**
 * @author coyote
 */
public abstract class ResourceManager {
  protected final Project myProject;

  protected ResourceManager(@NotNull Project project) {
    myProject = project;
  }

  /** Returns all the resource directories for this module <b>and all of its module dependencies</b> */
  @NotNull
  public abstract VirtualFile[] getAllResourceDirs();

  /** Returns all the resource directories for this module only */
  @NotNull
  public abstract List<VirtualFile> getResourceDirs();

  /** Returns true if the given directory is a resource directory in this module */
  public abstract boolean isResourceDir(@NotNull VirtualFile dir);

  // TODO: Switch parameter type to ResourceFolderType to avoid mix & matching
  // ResourceType and ResourceFolderType
  public boolean processFileResources(@NotNull String resourceType, @NotNull FileResourceProcessor processor) {
    return processFileResources(resourceType, processor, true);
  }

  // TODO: Switch parameter type to ResourceFolderType to avoid mix & matching
  // ResourceType and ResourceFolderType
  public boolean processFileResources(@NotNull String resourceType, @NotNull FileResourceProcessor processor,
                                      boolean withDependencies) {
    ResourceFolderType folderType = ResourceFolderType.getTypeByName(resourceType);
    if (folderType == null) {
      return true;
    }
    return processFileResources(folderType, processor, withDependencies, true);
  }

  public boolean processFileResources(@NotNull ResourceFolderType folderType, @NotNull FileResourceProcessor processor,
                                       boolean withDependencies, boolean publicOnly) {
    final VirtualFile[] resDirs;
    if (withDependencies) {
      resDirs = getAllResourceDirs();
    } else {
      List<VirtualFile> resourceDirs = getResourceDirs();
      resDirs = resourceDirs.toArray(new VirtualFile[resourceDirs.size()]);
    }

    for (VirtualFile resSubdir : AndroidResourceUtil.getResourceSubdirs(folderType, resDirs)) {
      final ResourceFolderType resType = ResourceFolderType.getFolderType(resSubdir.getName());

      if (resType != null) {
        assert folderType.equals(resType);
        String resTypeName = resType.getName();
        for (VirtualFile resFile : resSubdir.getChildren()) {
          final String resName = AndroidCommonUtils.getResourceName(resTypeName, resFile.getName());

          if (!resFile.isDirectory() && (!publicOnly || isResourcePublic(resTypeName, resName))) {
            if (!processor.process(resFile, resName)) {
              return false;
            }
          }
        }
      }
    }
    return true;
  }

  @NotNull
  public VirtualFile[] getResourceOverlayDirs() {
    return VirtualFile.EMPTY_ARRAY;
  }

  protected boolean isResourcePublic(@NotNull String type, @NotNull String name) {
    return true;
  }

  @NotNull
  public List<VirtualFile> getResourceSubdirs(@NotNull ResourceFolderType resourceType) {
    return AndroidResourceUtil.getResourceSubdirs(resourceType, getAllResourceDirs());
  }

  @NotNull
  public List<PsiFile> findResourceFiles(@NotNull final String resType,
                                         @Nullable final String resName,
                                         final boolean distinguishDelimetersInName,
                                         @NotNull String... extensions) {
    return findResourceFiles(resType, resName, distinguishDelimetersInName, true, extensions);
  }

  @NotNull
  public List<PsiFile> findResourceFiles(@NotNull final String resType1,
                                         @Nullable final String resName1,
                                         final boolean distinguishDelimetersInName,
                                         final boolean withDependencies,
                                         @NotNull final String... extensions) {
    final List<PsiFile> result = new ArrayList<PsiFile>();
    final Set<String> extensionSet = new HashSet<String>();
    addAll(extensionSet, extensions);

    processFileResources(resType1, new FileResourceProcessor() {
      @Override
      public boolean process(@NotNull final VirtualFile resFile, @NotNull String resName) {
        final String extension = resFile.getExtension();

        if ((extensions.length == 0 || extensionSet.contains(extension)) &&
            (resName1 == null || AndroidUtils.equal(resName1, resName, distinguishDelimetersInName))) {
          final PsiFile file = AndroidPsiUtils.getPsiFileSafely(myProject, resFile);
          if (file != null) {
            result.add(file);
          }
        }
        return true;
      }
    }, withDependencies);
    return result;
  }

  @NotNull
  public List<PsiFile> findResourceFiles(@NotNull String resType) {
    return findResourceFiles(resType, null, true);
  }

  protected List<Pair<Resources, VirtualFile>> getResourceElements(@Nullable Set<VirtualFile> files) {
    return getRootDomElements(Resources.class, files);
  }

  private <T extends DomElement> List<Pair<T, VirtualFile>> getRootDomElements(@NotNull Class<T> elementType,
                                                                               @Nullable Set<VirtualFile> files) {
    final List<Pair<T, VirtualFile>> result = new ArrayList<Pair<T, VirtualFile>>();
    for (VirtualFile file : getAllValueResourceFiles()) {
      if ((files == null || files.contains(file)) && file.isValid()) {
        final T element = AndroidUtils.loadDomElement(myProject, file, elementType);
        if (element != null) {
          result.add(Pair.create(element, file));
        }
      }
    }
    return result;
  }

  @NotNull
  protected Set<VirtualFile> getAllValueResourceFiles() {
    final Set<VirtualFile> files = new HashSet<VirtualFile>();

    for (VirtualFile valueResourceDir : getResourceSubdirs(ResourceFolderType.VALUES)) {
      for (VirtualFile valueResourceFile : valueResourceDir.getChildren()) {
        if (!valueResourceFile.isDirectory() && valueResourceFile.getFileType().equals(StdFileTypes.XML)) {
          files.add(valueResourceFile);
        }
      }
    }
    return files;
  }

  protected List<ResourceElement> getValueResources(@NotNull final ResourceType resourceType, @Nullable Set<VirtualFile> files) {
    final List<ResourceElement> result = new ArrayList<ResourceElement>();
    List<Pair<Resources, VirtualFile>> resourceFiles = getResourceElements(files);
    for (final Pair<Resources, VirtualFile> pair : resourceFiles) {
      final Resources resources = pair.getFirst();
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          if (!resources.isValid() || myProject.isDisposed()) {
            return;
          }
          final List<ResourceElement> valueResources = AndroidResourceUtil.getValueResourcesFromElement(resourceType, resources);
          for (ResourceElement valueResource : valueResources) {
            final String resName = valueResource.getName().getValue();

            if (resName != null && isResourcePublic(resourceType.getName(), resName)) {
              result.add(valueResource);
            }
          }
        }
      });
    }
    return result;
  }

  @Nullable
  public String getValueResourceType(@NotNull XmlTag tag) {
    ResourceFolderType fileResType = getFileResourceFolderType(tag.getContainingFile());
    if (ResourceFolderType.VALUES == fileResType) {
      return tag.getName();
    }
    return null;
  }

  @Nullable
  public ResourceFolderType getFileResourceFolderType(@NotNull final PsiFile file) {
    return ApplicationManager.getApplication().runReadAction(new Computable<ResourceFolderType>() {
      @Nullable
      @Override
      public ResourceFolderType compute() {
        PsiDirectory dir = file.getContainingDirectory();
        if (dir == null) {
          return null;
        }

        PsiDirectory possibleResDir = dir.getParentDirectory();
        if (possibleResDir == null || !isResourceDir(possibleResDir.getVirtualFile())) {
          return null;
        }
        return ResourceFolderType.getFolderType(dir.getName());
      }
    });
  }

  @Nullable
  public String getFileResourceType(@NotNull final PsiFile file) {
    final ResourceFolderType folderType = getFileResourceFolderType(file);
    return folderType == null ? null : folderType.getName();
  }

  // TODO: Switch parameter type to ResourceFolderType to avoid mix & matching
  // ResourceType and ResourceFolderType
  @NotNull
  private Set<String> getFileResourcesNames(@NotNull final String resourceType) {
    final Set<String> result = new HashSet<String>();

    processFileResources(resourceType, new FileResourceProcessor() {
      @Override
      public boolean process(@NotNull VirtualFile resFile, @NotNull String resName) {
        result.add(resName);
        return true;
      }
    });
    return result;
  }

  @NotNull
  public Collection<String> getValueResourceNames(@NotNull final ResourceType resourceType) {
    final Set<String> result = new HashSet<String>();
    final boolean attr = ResourceType.ATTR == resourceType;

    for (ResourceEntry entry : getValueResourceEntries(resourceType)) {
      final String name = entry.getName();

      if (!attr || !name.startsWith("android:")) {
        result.add(name);
      }
    }
    return result;
  }

  @NotNull
  public Collection<ResourceEntry> getValueResourceEntries(@NotNull final ResourceType resourceType) {
    final FileBasedIndex index = FileBasedIndex.getInstance();
    final ResourceEntry typeMarkerEntry = AndroidValueResourcesIndex.createTypeMarkerKey(resourceType.getName());
    final GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);

    final Map<VirtualFile, Set<ResourceEntry>> file2resourceSet = new HashMap<VirtualFile, Set<ResourceEntry>>();

    index.processValues(AndroidValueResourcesIndex.INDEX_ID, typeMarkerEntry, null, new FileBasedIndex.ValueProcessor<ImmutableSet<AndroidValueResourcesIndex.MyResourceInfo>>() {
      @Override
      public boolean process(@NotNull VirtualFile file, ImmutableSet<AndroidValueResourcesIndex.MyResourceInfo> infos) {
        for (AndroidValueResourcesIndex.MyResourceInfo info : infos) {
          Set<ResourceEntry> resourcesInFile = file2resourceSet.get(file);

          if (resourcesInFile == null) {
            resourcesInFile = new HashSet<ResourceEntry>();
            file2resourceSet.put(file, resourcesInFile);
          }
          resourcesInFile.add(info.getResourceEntry());
        }
        return true;
      }
    }, scope);

    final List<ResourceEntry> result = new ArrayList<ResourceEntry>();

    for (VirtualFile file : getAllValueResourceFiles()) {
      final Set<ResourceEntry> entries = file2resourceSet.get(file);

      if (entries != null) {
        for (ResourceEntry entry : entries) {
          if (isResourcePublic(entry.getType(), entry.getName())) {
            result.add(entry);
          }
        }
      }
    }
    return result;
  }

  /**
   * Get the collection of resource names that match the given type.
   * @param type the type of resource
   * @return resource names
   */
  @NotNull
  public Collection<String> getResourceNames(@NotNull ResourceType type) {
    return getResourceNames(type, false);
  }

  @NotNull
  public Collection<String> getResourceNames(@NotNull ResourceType resourceType, boolean publicOnly) {
    final Set<String> result = new HashSet<String>();
    result.addAll(getValueResourceNames(resourceType));

    List<ResourceFolderType> folders = FolderTypeRelationship.getRelatedFolders(resourceType);
    if (!folders.isEmpty()) {
      for (ResourceFolderType folderType : folders) {
        if (folderType != ResourceFolderType.VALUES) {
          result.addAll(getFileResourcesNames(folderType.getName()));
        }
      }
    }
    if (resourceType == ResourceType.ID) {
      result.addAll(getIds(true));
    }
    return result;
  }

  @Nullable
  public abstract AttributeDefinitions getAttributeDefinitions();

  // searches only declarations such as "@+id/..."
  @NotNull
  public List<XmlAttributeValue> findIdDeclarations(@NotNull final String id) {
    if (!isResourcePublic(ResourceType.ID.getName(), id)) {
      return Collections.emptyList();
    }

    final List<XmlAttributeValue> declarations = new ArrayList<XmlAttributeValue>();
    final Collection<VirtualFile> files =
      FileBasedIndex.getInstance().getContainingFiles(AndroidIdIndex.INDEX_ID, "+" + id, GlobalSearchScope.allScope(myProject));
    final Set<VirtualFile> fileSet = new HashSet<VirtualFile>(files);
    final PsiManager psiManager = PsiManager.getInstance(myProject);

    for (VirtualFile subdir : getResourceSubdirsToSearchIds()) {
      for (VirtualFile file : subdir.getChildren()) {
        if (fileSet.contains(file)) {
          final PsiFile psiFile = psiManager.findFile(file);

          if (psiFile instanceof XmlFile) {
            psiFile.accept(new XmlRecursiveElementVisitor() {
              @Override
              public void visitXmlAttributeValue(XmlAttributeValue attributeValue) {
                if (AndroidResourceUtil.isIdDeclaration(attributeValue)) {
                  final String idInAttr = AndroidResourceUtil.getResourceNameByReferenceText(attributeValue.getValue());

                  if (id.equals(idInAttr)) {
                    declarations.add(attributeValue);
                  }
                }
              }
            });
          }
        }
      }
    }
    return declarations;
  }

  @NotNull
  public Collection<String> getIds(boolean declarationsOnly) {

    if (myProject.isDisposed()) {
      return Collections.emptyList();
    }
    final GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);

    final FileBasedIndex index = FileBasedIndex.getInstance();
    final Map<VirtualFile, Set<String>> file2idEntries = new HashMap<VirtualFile, Set<String>>();

    index.processValues(AndroidIdIndex.INDEX_ID, AndroidIdIndex.MARKER, null, new FileBasedIndex.ValueProcessor<Set<String>>() {
      @Override
      public boolean process(@NotNull VirtualFile file, Set<String> value) {
        file2idEntries.put(file, value);
        return true;
      }
    }, scope);

    final Set<String> result = new HashSet<String>();

    for (VirtualFile resSubdir : getResourceSubdirsToSearchIds()) {
      for (VirtualFile resFile : resSubdir.getChildren()) {
        final Set<String> idEntries = file2idEntries.get(resFile);

        if (idEntries != null) {
          for (String idEntry : idEntries) {
            if (idEntry.startsWith("+")) {
              idEntry = idEntry.substring(1);
            }
            else if (declarationsOnly) {
              continue;
            }
            if (isResourcePublic(ResourceType.ID.getName(), idEntry)) {
              result.add(idEntry);
            }
          }
        }
      }
    }
    return result;
  }

  @NotNull
  public List<VirtualFile> getResourceSubdirsToSearchIds() {
    final List<VirtualFile> resSubdirs = new ArrayList<VirtualFile>();
    for (ResourceFolderType type : FolderTypeRelationship.getIdGeneratingFolderTypes()) {
      resSubdirs.addAll(getResourceSubdirs(type));
    }
    return resSubdirs;
  }

  public List<ResourceElement> findValueResources(@NotNull String resType, @NotNull String resName) {
    return findValueResources(resType, resName, true);
  }

  // not recommended to use, because it is too slow
  @NotNull
  public List<ResourceElement> findValueResources(@NotNull String resourceType,
                                                  @NotNull String resourceName,
                                                  boolean distinguishDelimitersInName) {
    final List<ValueResourceInfoImpl> resources = findValueResourceInfos(resourceType, resourceName, distinguishDelimitersInName, false);
    final List<ResourceElement> result = new ArrayList<ResourceElement>();

    for (ValueResourceInfoImpl resource : resources) {
      final ResourceElement domElement = resource.computeDomElement();

      if (domElement != null) {
        result.add(domElement);
      }
    }
    return result;
  }

  public void collectLazyResourceElements(@NotNull String resType,
                                          @NotNull String resName,
                                          boolean withAttrs,
                                          @NotNull PsiElement context,
                                          @NotNull Collection<PsiElement> elements) {
    List<ValueResourceInfoImpl> valueResources = findValueResourceInfos(resType, resName, false, withAttrs);

    for (final ValueResourceInfo resource : valueResources) {
      elements.add(new LazyValueResourceElementWrapper(resource, context));
    }
    if (resType.equals("id")) {
      elements.addAll(findIdDeclarations(resName));
    }
    if (elements.size() == 0) {
      for (PsiFile file : findResourceFiles(resType, resName, false)) {
        elements.add(new FileResourceElementWrapper(file));
      }
    }
  }

  @NotNull
  public List<ValueResourceInfoImpl> findValueResourceInfos(@NotNull String resourceType,
                                                            @NotNull final String resourceName,
                                                            final boolean distinguishDelimetersInName,
                                                            boolean searchAttrs) {
    final ResourceType type = resourceType.startsWith("+") ? ResourceType.ID : ResourceType.getEnum(resourceType);
    if (type == null ||
        !AndroidResourceUtil.VALUE_RESOURCE_TYPES.contains(type) &&
        (type != ResourceType.ATTR || !searchAttrs)) {
      return Collections.emptyList();
    }
    final GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);
    final List<ValueResourceInfoImpl> result = new ArrayList<ValueResourceInfoImpl>();
    final Set<VirtualFile> valueResourceFiles = getAllValueResourceFiles();

    FileBasedIndex.getInstance()
      .processValues(AndroidValueResourcesIndex.INDEX_ID, AndroidValueResourcesIndex.createTypeNameMarkerKey(resourceType, resourceName),
                     null, new FileBasedIndex.ValueProcessor<ImmutableSet<AndroidValueResourcesIndex.MyResourceInfo>>() {
      @Override
      public boolean process(@NotNull VirtualFile file, ImmutableSet<AndroidValueResourcesIndex.MyResourceInfo> infos) {
        for (AndroidValueResourcesIndex.MyResourceInfo info : infos) {
          final String name = info.getResourceEntry().getName();

          if (AndroidUtils.equal(resourceName, name, distinguishDelimetersInName)) {
            if (valueResourceFiles.contains(file)) {
              result.add(new ValueResourceInfoImpl(info.getResourceEntry().getName(), type, file, myProject, info.getOffset()));
            }
          }
        }
        return true;
      }
    }, scope);
    return result;
  }
}
