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

import static com.android.SdkConstants.ANDROID_NS_NAME_PREFIX;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTRS_DATA_BINDING;
import static com.android.SdkConstants.ATTR_ALIAS;
import static com.android.SdkConstants.ATTR_CLASS;
import static com.android.SdkConstants.ATTR_FORMAT;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.EXT_PNG;
import static com.android.SdkConstants.ID_PREFIX;
import static com.android.SdkConstants.NEW_ID_PREFIX;
import static com.android.SdkConstants.TAGS_DATA_BINDING;
import static com.android.SdkConstants.TAG_DATA;
import static com.android.SdkConstants.TAG_IMPORT;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_LAYOUT;
import static com.android.SdkConstants.TAG_RESOURCES;
import static com.android.SdkConstants.TAG_VARIABLE;
import static com.android.resources.ResourceFolderType.COLOR;
import static com.android.resources.ResourceFolderType.DRAWABLE;
import static com.android.resources.ResourceFolderType.FONT;
import static com.android.resources.ResourceFolderType.LAYOUT;
import static com.android.resources.ResourceFolderType.VALUES;
import static com.android.tools.idea.databinding.DataBindingUtil.isDataBindingEnabled;
import static com.android.tools.idea.databinding.ViewBindingUtil.isViewBindingEnabled;
import static com.android.tools.idea.res.PsiProjectListener.isRelevantFile;
import static com.android.tools.idea.resources.base.ResourceSerializationUtil.createPersistentCache;
import static com.android.tools.idea.resources.base.ResourceSerializationUtil.writeResourcesToStream;
import static com.android.tools.lint.detector.api.Lint.stripIdPrefix;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jetbrains.android.util.AndroidResourceUtil.getResourceTypeForResourceTag;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.DataBindingResourceType;
import com.android.ide.common.resources.FileResourceNameValidator;
import com.android.ide.common.resources.ResourceFile;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceMergerItem;
import com.android.ide.common.resources.ResourceTable;
import com.android.ide.common.resources.ValueResourceNameValidator;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.util.PathString;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.resources.ResourceVisibility;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.model.MergedManifestManager;
import com.android.tools.idea.res.binding.BindingLayoutGroup;
import com.android.tools.idea.res.binding.BindingLayoutInfo;
import com.android.tools.idea.res.binding.BindingLayoutPsi;
import com.android.tools.idea.res.binding.PsiDataBindingResourceItem;
import com.android.tools.idea.resources.base.Base128InputStream;
import com.android.tools.idea.resources.base.BasicFileResourceItem;
import com.android.tools.idea.resources.base.BasicResourceItem;
import com.android.tools.idea.resources.base.BasicValueResourceItemBase;
import com.android.tools.idea.resources.base.LoadableResourceRepository;
import com.android.tools.idea.resources.base.RepositoryConfiguration;
import com.android.tools.idea.resources.base.RepositoryLoader;
import com.android.tools.idea.resources.base.ResourceSerializationUtil;
import com.android.tools.idea.resources.base.ResourceSourceFile;
import com.android.tools.idea.util.FileExtensions;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeAnyChangeAbstractAdapter;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiTreeChangeListener;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlProcessingInstruction;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.concurrent.GuardedBy;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.ide.PooledThreadExecutor;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * The {@link ResourceFolderRepository} is leaf in the repository tree, and is used for user editable resources (e.g. the resources in the
 * project, typically the res/main source set.) Each ResourceFolderRepository contains the resources provided by a single res folder. This
 * repository is built on top of IntelliJ’s PSI infrastructure. This allows it (along with PSI listeners) to be updated incrementally; for
 * example, when it notices that the user is editing the value inside a <string> element in a value folder XML file, it will directly update
 * the resource value for the given resource item, and so on.
 *
 * <p>For efficiency, the ResourceFolderRepository is initialized using non-PSI parsers and then
 * lazily switches to PSI parsers after edits. See also {@code README.md} in this package.
 *
 * <p>Remaining work:
 * <ul>
 * <li>Find some way to have event updates in this resource folder directly update parent repositories
 * (typically {@link ModuleResourceRepository})</li>
 * <li>Add defensive checks for non-read permission reads of resource values</li>
 * <li>Idea: For {@link #scheduleScan}; compare the removed items from the added items, and if they're the same, avoid
 * creating a new generation.</li>
 * <li>Register the PSI project listener as a project service instead.</li>
 * </ul>
 */
public final class ResourceFolderRepository extends LocalResourceRepository implements LoadableResourceRepository {
  /**
   * Increment when making changes that may affect content of repository cache files.
   * Used together with CachingData.codeVersion. Important for developer builds.
   */
  static final String CACHE_FILE_FORMAT_VERSION = "1";
  private static final byte[] CACHE_FILE_HEADER = "Resource cache".getBytes(UTF_8);
  /**
   * Maximum fraction of resources out of date in the cache for the cache to be considered fresh.
   * <p>
   * Loading without cache takes approximately twice as long as with the cache. This means that
   * if x% of all resources are loaded from sources because the cache is not completely up to date,
   * it introduces approximately x% loading time overhead. 5% overhead seems acceptable since it
   * is well within natural variation. Since cache file creation is asynchronous, the cost of
   * keeping cache fresh is pretty low.
   */
  private static final double CACHE_STALENESS_THRESHOLD = 0.05;
  private static final Comparator<ResourceItemSource<? extends ResourceItem>> SOURCE_COMPARATOR =
      Comparator.comparing(ResourceItemSource::getFolderConfiguration);
  private static final Logger LOG = Logger.getInstance(ResourceFolderRepository.class);

  @NotNull private final AndroidFacet myFacet;
  @NotNull private final PsiTreeChangeListener myPsiListener;
  @NotNull private final VirtualFile myResourceDir;
  @NotNull private final ResourceNamespace myNamespace;
  private final boolean myDataBindingEnabled;
  private final boolean myViewBindingEnabled;
  /**
   * Common prefix of paths of all file resources.  Used to compose resource paths returned by
   * the {@link BasicFileResourceItem#getSource()} method.
   */
  @NotNull private final String myResourcePathPrefix;

  // Statistics of the initial repository loading.
  private int myNumXmlFilesLoadedInitially; // Doesn't count files that were explicitly skipped.
  private int myNumXmlFilesLoadedInitiallyFromSources;

  @GuardedBy("AbstractResourceRepositoryWithLocking.ITEM_MAP_LOCK")
  @NotNull private final ResourceTable myFullTable = new ResourceTable();

  @NotNull private final Map<VirtualFile, ResourceItemSource<? extends ResourceItem>> mySources = new HashMap<>();
  @NotNull private final PsiManager myPsiManager;
  @NotNull private Set<BindingLayoutGroup> myDataBindingResourceFiles = new HashSet<>();
  private long myDataBindingResourceFilesModificationCount = Long.MIN_VALUE;
  @NotNull private final Object SCAN_LOCK = new Object();
  @Nullable private Set<PsiFile> myPendingScans;

  @VisibleForTesting
  static int ourFullRescans;

  /**
   * Creates a ResourceFolderRepository and loads its contents.
   * <p>
   * If {@code cachingData} is not null, an attempt is made
   * to load resources from the cache file specified in {@code cachingData}. While loading from the cache resources
   * defined in the XML files that changed recently are skipped. Whether an XML has changed or not is determined by
   * comparing the file modification time obtained by calling {@link VirtualFile#getModificationStamp()} with
   * the modification time stored in the cache.
   * <p>
   * The remaining resources are then loaded by parsing XML files that were not present in the cache or were newer
   * than their cached versions.
   * <p>
   * If a significant (determined by {@link #CACHE_STALENESS_THRESHOLD}} percentage of resources was loaded by parsing
   * XML files and {@code cachingData.cacheCreationExecutor} is not null, the new cache file is created using that
   * executor, possibly after this method has already returned.
   * <p>
   * After creation the contents of the repository are maintained to be up to date by listening to VFS and PSI events.
   * <p>
   * NOTE: You should normally use {@link ResourceFolderRegistry#get} rather than this method.
   */
  @NotNull
  static ResourceFolderRepository create(@NotNull AndroidFacet facet, @NotNull VirtualFile dir, @NotNull ResourceNamespace namespace,
                                         @Nullable ResourceFolderRepositoryCachingData cachingData) {
    return new ResourceFolderRepository(facet, dir, namespace, cachingData);
  }

  private ResourceFolderRepository(@NotNull AndroidFacet facet,
                                   @NotNull VirtualFile resourceDir,
                                   @NotNull ResourceNamespace namespace,
                                   @Nullable ResourceFolderRepositoryCachingData cachingData) {
    super(resourceDir.getName());
    myFacet = facet;
    myResourceDir = resourceDir;
    myNamespace = namespace;
    myResourcePathPrefix = myResourceDir.getPath() + '/';
    myDataBindingEnabled = isDataBindingEnabled(facet);
    myViewBindingEnabled = isViewBindingEnabled(facet);
    myPsiListener = StudioFlags.INCREMENTAL_RESOURCE_REPOSITORIES.get() ? new IncrementalUpdatePsiListener() : new SimplePsiListener();
    myPsiManager = PsiManager.getInstance(getProject());

    Loader loader = new Loader(this, cachingData);
    loader.load();
  }

  @NotNull
  public VirtualFile getResourceDir() {
    return myResourceDir;
  }

  @Override
  @Nullable
  public String getLibraryName() {
    return null; // Resource folder is not a library.
  }

  @Override
  @NotNull
  public Path getOrigin() {
    return Paths.get(myResourceDir.getPath());
  }

  @Override
  @NotNull
  public String getResourceUrl(@NotNull String relativeResourcePath) {
    return myResourcePathPrefix + relativeResourcePath;
  }

  @Override
  @NotNull
  public PathString getSourceFile(@NotNull String relativeResourcePath, boolean forFileResource) {
    return new PathString(myResourcePathPrefix + relativeResourcePath);
  }

  @Override
  @Nullable
  public String getPackageName() {
    return ResourceRepositoryImplUtil.getPackageName(myNamespace, myFacet);
  }

  @Override
  public boolean containsUserDefinedResources() {
    return true;
  }

  private static void addToResult(@NotNull Map<ResourceType, ListMultimap<String, ResourceItem>> result, @NotNull ResourceItem item) {
    // The insertion order matters, see AppResourceRepositoryTest#testStringOrder.
    result.computeIfAbsent(item.getType(), t -> LinkedListMultimap.create()).put(item.getName(), item);
  }

  /**
   * Inserts the given resources into this repository, while holding the global repository lock.
   */
  private void commitToRepository(@NotNull Map<ResourceType, ListMultimap<String, ResourceItem>> itemsByType) {
    synchronized (ITEM_MAP_LOCK) {
      commitToRepositoryWithoutLock(itemsByType);
    }
  }

  /**
   * Inserts the given resources into this repository without acquiring any locks. Safe to call only while
   * holding {@link #ITEM_MAP_LOCK} or during construction of ResourceFolderRepository.
   */
  @SuppressWarnings("GuardedBy")
  private void commitToRepositoryWithoutLock(@NotNull Map<ResourceType, ListMultimap<String, ResourceItem>> itemsByType) {
    for (Map.Entry<ResourceType, ListMultimap<String, ResourceItem>> entry : itemsByType.entrySet()) {
      getOrCreateMap(myNamespace, entry.getKey()).putAll(entry.getValue());
    }
  }

  /**
   * Determines if it's unnecessary to write or update the file-backed cache.
   * If only a few items were reparsed, then the cache is fresh enough.
   *
   * @return true if this repo is backed by a fresh file cache
   */
  @VisibleForTesting
  boolean hasFreshFileCache() {
    return myNumXmlFilesLoadedInitiallyFromSources <= myNumXmlFilesLoadedInitially * CACHE_STALENESS_THRESHOLD;
  }

  @TestOnly
  int getNumXmlFilesLoadedInitially() {
    return myNumXmlFilesLoadedInitially;
  }

  @TestOnly
  int getNumXmlFilesLoadedInitiallyFromSources() {
    return myNumXmlFilesLoadedInitiallyFromSources;
  }

  @Nullable
  private PsiFile ensureValid(@NotNull PsiFile psiFile) {
    if (psiFile.isValid()) {
      return psiFile;
    }

    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile != null && virtualFile.exists() && !getProject().isDisposed()) {
      return myPsiManager.findFile(virtualFile);
    }

    return null;
  }

  private void scanFileResourceFileAsPsi(@NotNull Map<ResourceType, ListMultimap<String, ResourceItem>> result,
                                         @NotNull ResourceFolderType folderType,
                                         @NotNull FolderConfiguration folderConfiguration,
                                         @NotNull ResourceType type,
                                         boolean idGenerating,
                                         @NotNull PsiFile file) {
    // XML or image.
    String resourceName = ResourceHelper.getResourceName(file);
    if (FileResourceNameValidator.getErrorTextForNameWithoutExtension(resourceName, folderType) != null) {
      return; // Not a valid file resource name.
    }

    PsiResourceItem item = PsiResourceItem.forFile(resourceName, type, myNamespace, file, false);

    if (idGenerating) {
      List<PsiResourceItem> items = new ArrayList<>();
      items.add(item);
      addToResult(result, item);
      addIds(result, items, file);

      PsiResourceFile resourceFile = new PsiResourceFile(file, items, folderType, folderConfiguration);
      scanBindingLayout(resourceFile, getModificationCount());
      mySources.put(file.getVirtualFile(), resourceFile);
    } else {
      PsiResourceFile resourceFile = new PsiResourceFile(file, Collections.singletonList(item), folderType, folderConfiguration);
      mySources.put(file.getVirtualFile(), resourceFile);
      addToResult(result, item);
    }
  }

  @Override
  @Nullable
  public BindingLayoutInfo getBindingLayoutInfo(@NotNull String layoutName) {
    List<ResourceItem> resourceItems = getResources(myNamespace, ResourceType.LAYOUT, layoutName);
    for (ResourceItem item : resourceItems) {
      if (item instanceof PsiResourceItem) {
        PsiResourceFile source = ((PsiResourceItem)item).getSourceFile();
        if (source != null) {
          return source.getBindingLayoutInfo();
        }
      }
    }
    return null;
  }

  /**
   * Given a list of existing groups and a list of (one or more) layouts (representing the same
   * layout but possibly multiple configurations), returns a {@link BindingLayoutGroup} that owns
   * those layouts. If an existing group is found, it will be updated; or otherwise, a new group
   * will be created on demand.
   */
  @NotNull
  private static BindingLayoutGroup createOrUpdateGroup(@NotNull Set<BindingLayoutGroup> existingGroups,
                                                        @NotNull List<BindingLayoutInfo> layouts,
                                                        Long modificationCount) {
    BindingLayoutGroup group = null;
    BindingLayoutInfo mainLayout = layouts.get(0);
    for (BindingLayoutGroup existingGroup : existingGroups) {
      if (existingGroup.getMainLayout() == mainLayout) {
        group = existingGroup;
        break;
      }
    }

    if (group == null) {
      group = new BindingLayoutGroup(layouts);
    }
    else {
      group.updateLayouts(layouts, modificationCount);
    }
    return group;
  }

  @Override
  @NotNull
  public Set<BindingLayoutGroup> getDataBindingResourceFiles() {
    long modificationCount = getModificationCount();
    if (myDataBindingResourceFilesModificationCount == modificationCount) {
      return myDataBindingResourceFiles;
    }
    myDataBindingResourceFilesModificationCount = modificationCount;

    // Group all related layouts together, e.g. "layout/fragment_demo.xml" and "layout-land/fragment_demo.xml"
    Set<BindingLayoutGroup> groups = mySources.values().stream()
      .map(resourceFile -> resourceFile instanceof PsiResourceFile ? (((PsiResourceFile)resourceFile).getBindingLayoutInfo()) : null)
      .filter(Objects::nonNull)
      .collect(Collectors.groupingBy(info -> info.getXml().getFileName()))
      .values().stream()
      .map(layouts -> createOrUpdateGroup(myDataBindingResourceFiles, layouts, modificationCount))
      .collect(Collectors.toSet());

    myDataBindingResourceFiles = Collections.unmodifiableSet(groups);
    return myDataBindingResourceFiles;
  }

  private static void scanDataBindingDataTag(@NotNull PsiResourceFile resourceFile, @Nullable XmlTag dataTag, long modificationCount) {
    BindingLayoutInfo info = resourceFile.getBindingLayoutInfo();
    assert info != null;
    List<PsiDataBindingResourceItem> items = new ArrayList<>();
    if (dataTag == null) {
      info.replaceDataItems(items, modificationCount);
      return;
    }
    Set<String> usedNames = new HashSet<>();
    for (XmlTag tag : dataTag.findSubTags(TAG_VARIABLE)) {
      String nameValue = tag.getAttributeValue(ATTR_NAME);
      if (nameValue == null) {
        continue;
      }
      String name = StringUtil.unescapeXmlEntities(nameValue);
      if (StringUtil.isNotEmpty(name)) {
        if (usedNames.add(name)) {
          PsiDataBindingResourceItem item = new PsiDataBindingResourceItem(name, DataBindingResourceType.VARIABLE, tag, resourceFile);
          items.add(item);
        }
      }
    }
    Set<String> usedAliases = new HashSet<>();
    for (XmlTag tag : dataTag.findSubTags(TAG_IMPORT)) {
      String typeValue = tag.getAttributeValue(ATTR_TYPE);
      if (typeValue == null) {
        continue;
      }
      String type = StringUtil.unescapeXmlEntities(typeValue);
      String aliasValue = tag.getAttributeValue(ATTR_ALIAS);
      String alias = aliasValue == null ? null : StringUtil.unescapeXmlEntities(aliasValue);
      if (alias == null) {
        int lastIndexOfDot = type.lastIndexOf('.');
        if (lastIndexOfDot >= 0) {
          alias = type.substring(lastIndexOfDot + 1);
        }
      }
      if (StringUtil.isNotEmpty(alias)) {
        if (usedAliases.add(type)) {
          PsiDataBindingResourceItem item = new PsiDataBindingResourceItem(alias, DataBindingResourceType.IMPORT, tag, resourceFile);
          items.add(item);
        }
      }
    }

    info.replaceDataItems(items, modificationCount);
  }

  private boolean isBindingLayoutFile(@NotNull PsiResourceFile resourceFile) {
    if (resourceFile.getFolderType() != LAYOUT || !(resourceFile.getPsiFile() instanceof XmlFile)) {
      return false;
    }
    XmlTag layoutTag = ((XmlFile)resourceFile.getPsiFile()).getRootTag();
    if (layoutTag == null) {
      return false;
    }
    if (TAG_LAYOUT.equals(layoutTag.getName()) || isViewBindingEnabled(myFacet)) {
      return true;
    }
    return false;
  }

  private void scanBindingLayout(@NotNull PsiResourceFile resourceFile, long modificationCount) {
    if (!isBindingLayoutFile(resourceFile)) {
      return;
    }
    XmlFile layoutFile = (XmlFile) resourceFile.getPsiFile();
    XmlTag layoutTag = layoutFile.getRootTag();
    XmlTag dataTag = layoutTag == null ? null : layoutTag.findFirstSubTag(TAG_DATA);
    String classAttrValue = null;
    if (dataTag != null) {
      classAttrValue = dataTag.getAttributeValue(ATTR_CLASS);
      if (classAttrValue != null) {
        classAttrValue = StringUtil.unescapeXmlEntities(classAttrValue);
      }
    }

    BindingLayoutInfo info = resourceFile.getBindingLayoutInfo();
    if (info == null) {
      PsiDirectory folder = resourceFile.getPsiFile().getParent();
      // If we're here, we have a LAYOUT folder type, so folder is always non-null. See also: isBindingLayoutFile.
      assert folder != null;
      String folderName = folder.getName();
      String fileName = resourceFile.getPsiFile().getName();
      String modulePackage = MergedManifestManager.getSnapshot(myFacet).getPackage();

      info = new BindingLayoutInfo(modulePackage, folderName, fileName, classAttrValue);
      info.setPsi(new BindingLayoutPsi(myFacet, resourceFile));
      resourceFile.setBindingLayoutInfo(info);
    } else {
      info.updateClassData(classAttrValue, modificationCount);
    }
    scanDataBindingDataTag(resourceFile, dataTag, modificationCount);
  }

  @Override
  @GuardedBy("AbstractResourceRepositoryWithLocking.ITEM_MAP_LOCK")
  @NotNull
  protected ResourceTable getFullTable() {
    return myFullTable;
  }

  @Override
  @GuardedBy("AbstractResourceRepositoryWithLocking.ITEM_MAP_LOCK")
  @Contract("_, _, true -> !null")
  @Nullable
  protected ListMultimap<String, ResourceItem> getMap(@NotNull ResourceNamespace namespace, @NotNull ResourceType type, boolean create) {
    ListMultimap<String, ResourceItem> multimap = myFullTable.get(namespace, type);
    if (multimap == null && create) {
      multimap = LinkedListMultimap.create(); // use LinkedListMultimap to preserve ordering for editors that show original order.
      myFullTable.put(namespace, type, multimap);
    }
    return multimap;
  }

  @Override
  @NotNull
  public ResourceNamespace getNamespace() {
    return myNamespace;
  }

  private void addIds(@NotNull Map<ResourceType, ListMultimap<String, ResourceItem>>result,
                      @NotNull List<PsiResourceItem> items,
                      @NotNull PsiFile file) {
    addIds(result, items, file, false);
  }

  private void addIds(@NotNull Map<ResourceType, ListMultimap<String, ResourceItem>> result,
                      @NotNull List<PsiResourceItem> items,
                      @NotNull PsiElement element,
                      boolean calledFromPsiListener) {
    // "@+id/" names found before processing the view tag corresponding to the id.
    Map<String, XmlTag> pendingResourceIds = new HashMap<>();
    Collection<XmlTag> xmlTags = PsiTreeUtil.findChildrenOfType(element, XmlTag.class);
    if (element instanceof XmlTag) {
      addId(result, items, (XmlTag)element, pendingResourceIds, calledFromPsiListener);
    }
    if (!xmlTags.isEmpty()) {
      for (XmlTag tag : xmlTags) {
        addId(result, items, tag, pendingResourceIds, calledFromPsiListener);
      }
    }
    // Add any remaining ids.
    if (!pendingResourceIds.isEmpty()) {
      for (Map.Entry<String, XmlTag> entry : pendingResourceIds.entrySet()) {
        String id = entry.getKey();
        PsiResourceItem item = PsiResourceItem.forXmlTag(id, ResourceType.ID, myNamespace, entry.getValue(), calledFromPsiListener);
        items.add(item);
        addToResult(result, item);
      }
    }
  }

  private void addId(@NotNull Map<ResourceType, ListMultimap<String, ResourceItem>> result,
                     @NotNull List<PsiResourceItem> items,
                     @NotNull XmlTag tag,
                     @NotNull Map<String, XmlTag> pendingResourceIds,
                     boolean calledFromPsiListener) {
    assert tag.isValid();
    ListMultimap<String, ResourceItem> idMultimap = result.get(ResourceType.ID);
    for (XmlAttribute attribute : tag.getAttributes()) {
      if (ANDROID_URI.equals(attribute.getNamespace())) {
        // For all attributes in the android namespace, check if something has a value of the form "@+id/"
        // If the attribute is not android:id, and an item for it hasn't been created yet, add it to
        // the list of pending ids.
        String value = attribute.getValue();
        if (value != null && value.startsWith(NEW_ID_PREFIX) && !ATTR_ID.equals(attribute.getLocalName())) {
          String id = value.substring(NEW_ID_PREFIX.length());
          if (isValidResourceName(id) && (idMultimap == null || !idMultimap.containsKey(id)) && !pendingResourceIds.containsKey(id)) {
            pendingResourceIds.put(id, tag);
          }
        }
      }
    }
    // Now process the android:id attribute.
    String id = tag.getAttributeValue(ATTR_ID, ANDROID_URI);
    if (id != null) {
      if (id.startsWith(ID_PREFIX)) {
        // If the id is not "@+id/", it may still have been declared as "@+id/" in a preceding view (eg. layout_above).
        // So, we test if this is such a pending id.
        id = id.substring(ID_PREFIX.length());
        if (!pendingResourceIds.containsKey(id)) {
          return;
        }
      } else if (id.startsWith(NEW_ID_PREFIX)) {
        id = id.substring(NEW_ID_PREFIX.length());
      } else {
        return;
      }

      if (isValidResourceName(id)) {
        pendingResourceIds.remove(id);
        PsiResourceItem item = PsiResourceItem.forXmlTag(id, ResourceType.ID, myNamespace, tag, calledFromPsiListener);
        items.add(item);
        addToResult(result, item);
      }
    }
  }

  private boolean scanValueFileAsPsi(@NotNull Map<ResourceType, ListMultimap<String, ResourceItem>> result,
                                     @NotNull PsiFile file,
                                     @NotNull FolderConfiguration folderConfiguration) {
    boolean added = false;
    FileType fileType = file.getFileType();
    if (fileType == StdFileTypes.XML) {
      XmlFile xmlFile = (XmlFile)file;
      assert xmlFile.isValid();
      XmlDocument document = xmlFile.getDocument();
      if (document != null) {
        XmlTag root = document.getRootTag();
        if (root == null) {
          return false;
        }
        if (!root.getName().equals(TAG_RESOURCES)) {
          return false;
        }
        XmlTag[] subTags = root.getSubTags(); // Not recursive, right?
        List<PsiResourceItem> items = new ArrayList<>(subTags.length);
        for (XmlTag tag : subTags) {
          String name = tag.getAttributeValue(ATTR_NAME);
          ResourceType type = getResourceTypeForResourceTag(tag);
          if (type != null && isValidResourceName(name)) {
            PsiResourceItem item = PsiResourceItem.forXmlTag(name, type, myNamespace, tag, false);
            addToResult(result, item);
            items.add(item);
            added = true;

            if (type == ResourceType.STYLEABLE) {
              // For styleables we also need to create attr items for its children.
              XmlTag[] attrs = tag.getSubTags();
              if (attrs.length > 0) {
                for (XmlTag child : attrs) {
                  String attrName = child.getAttributeValue(ATTR_NAME);
                  if (isValidResourceName(attrName) && !attrName.startsWith(ANDROID_NS_NAME_PREFIX)
                      // Only add attr nodes for elements that specify a format or have flag/enum children; otherwise
                      // it's just a reference to an existing attr.
                      && (child.getAttribute(ATTR_FORMAT) != null || child.getSubTags().length > 0)) {
                    PsiResourceItem attrItem = PsiResourceItem.forXmlTag(attrName, ResourceType.ATTR, myNamespace, child, false);
                    items.add(attrItem);
                    addToResult(result, attrItem);
                  }
                }
              }
            }
          }
        }

        PsiResourceFile resourceFile = new PsiResourceFile(file, items, VALUES, folderConfiguration);
        mySources.put(file.getVirtualFile(), resourceFile);
      }
    }

    return added;
  }

  @Contract(value = "null -> false")
  private static boolean isValidResourceName(@Nullable String name) {
    return !StringUtil.isEmpty(name) && ValueResourceNameValidator.getErrorText(name, null) == null;
  }

  // Schedule a rescan to convert any map ResourceItems to PSI if needed, and return true if conversion
  // is needed (incremental updates which rely on PSI are not possible).
  private boolean convertToPsiIfNeeded(@NotNull PsiFile psiFile, @NotNull ResourceFolderType folderType) {
    ResourceItemSource resFile = mySources.get(psiFile.getVirtualFile());
    if (resFile instanceof PsiResourceFile) {
      return false;
    }
    // This schedules a rescan, and when the actual rescanImmediately happens it will purge non-PSI
    // items as needed, populate psi items, and add to myFileTypes once done.
    scheduleScan(psiFile, folderType);
    return true;
  }

  /**
   * Returns true if the given element represents a resource folder
   * (e.g. res/values-en-rUS or layout-land, *not* the root res/ folder)
   */
  private boolean isResourceFolder(@NotNull VirtualFile virtualFile) {
    if (virtualFile.isDirectory()) {
      VirtualFile parentDirectory = virtualFile.getParent();
      if (parentDirectory != null) {
        return parentDirectory.equals(myResourceDir);
      }
    }
    return false;
  }

  private boolean isResourceFile(@NotNull VirtualFile virtualFile) {
    VirtualFile parent = virtualFile.getParent();
    return parent != null && isResourceFolder(parent);
  }

  private boolean isResourceFile(@NotNull PsiFile psiFile) {
    return isResourceFile(psiFile.getVirtualFile());
  }

  @Override
  boolean isScanPending(@NotNull PsiFile psiFile) {
    synchronized (SCAN_LOCK) {
      return myPendingScans != null && myPendingScans.contains(psiFile);
    }
  }

  @VisibleForTesting
  void scheduleScan(@NotNull PsiFile psiFile, @NotNull ResourceFolderType folderType) {
    synchronized (SCAN_LOCK) {
      if (isScanPending(psiFile)) {
        return;
      }

      if (myPendingScans == null) {
        myPendingScans = new HashSet<>();
      }
      myPendingScans.add(psiFile);
    }

    ApplicationManager.getApplication().invokeLater(() -> {
      if (!psiFile.isValid()) return;

      ApplicationManager.getApplication().runWriteAction(() -> {
        if (isScanPending(psiFile)) {
          scan(psiFile, folderType);
          synchronized (SCAN_LOCK) {
            // myPendingScans cannot be null since it contains psiFile.
            myPendingScans.remove(psiFile);
            if (myPendingScans.isEmpty()) {
              myPendingScans = null;
            }
          }
        }
      });
    });
  }

  @Override
  public void sync() {
    super.sync();

    List<PsiFile> files;
    synchronized (SCAN_LOCK) {
      if (myPendingScans == null || myPendingScans.isEmpty()) {
        return;
      }
      files = new ArrayList<>(myPendingScans);
    }

    ApplicationManager.getApplication().runWriteAction(() -> {
      for (PsiFile file : files) {
        if (file.isValid()) {
          ResourceFolderType folderType = ResourceHelper.getFolderType(file);
          if (folderType != null) {
            scan(file, folderType);
          }
        }
      }
    });

    synchronized (SCAN_LOCK) {
      myPendingScans = null;
    }
  }

  private void scan(@NotNull PsiFile psiFile, @NotNull ResourceFolderType folderType) {
    if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
      ApplicationManager.getApplication().runReadAction(() -> scan(psiFile, folderType));
      return;
    }

    if (psiFile.getProject().isDisposed()) return;

    Map<ResourceType, ListMultimap<String, ResourceItem>> result = new HashMap<>();

    PsiFile file = psiFile;
    if (folderType == VALUES) {
      // For unit test tracking purposes only.
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ourFullRescans++;

      // First delete out the previous items.
      ResourceItemSource<? extends ResourceItem> source = this.mySources.remove(file.getVirtualFile());
      boolean removed = false;
      if (source != null) {
        removed = removeItemsFromSource(source);
      }

      file = ensureValid(file);
      boolean added = false;
      if (file != null) {
        // Add items for this file.
        PsiDirectory parent = file.getParent();
        assert parent != null; // Since we have a folder type.
        PsiDirectory fileParent = psiFile.getParent();
        if (fileParent != null) {
          FolderConfiguration folderConfiguration = FolderConfiguration.getConfigForFolder(fileParent.getName());
          if (folderConfiguration != null) {
            added = scanValueFileAsPsi(result, file, folderConfiguration);
          }
        }
      }

      if (added || removed) {
        // TODO: Consider doing a deeper diff of the changes to the resource items
        //       to determine if the removed and added items actually differ.
        setModificationCount(ourModificationCounter.incrementAndGet());
        invalidateParentCaches();
      }
    } else {
      ResourceItemSource<? extends ResourceItem> source = mySources.get(file.getVirtualFile());
      // If the old file was a PsiResourceFile, we could try to update ID ResourceItems in place.
      if (source instanceof PsiResourceFile) {
        PsiResourceFile psiResourceFile = (PsiResourceFile)source;
        // Already seen this file; no need to do anything unless it's an XML file with generated ids;
        // in that case we may need to update the id's.
        if (FolderTypeRelationship.isIdGeneratingFolderType(folderType) && file.getFileType() == StdFileTypes.XML) {
          // For unit test tracking purposes only.
          //noinspection AssignmentToStaticFieldFromInstanceMethod
          ourFullRescans++;

          // We've already seen this resource, so no change in the ResourceItem for the
          // file itself (e.g. @layout/foo from layout-land/foo.xml). However, we may have
          // to update the id's:
          Set<String> idsBefore = new HashSet<>();
          synchronized (ITEM_MAP_LOCK) {
            ListMultimap<String, ResourceItem> map = myFullTable.get(myNamespace, ResourceType.ID);
            if (map != null) {
              List<PsiResourceItem> idItems = new ArrayList<>();
              for (PsiResourceItem item : psiResourceFile) {
                if (item.getType() == ResourceType.ID) {
                  idsBefore.add(item.getName());
                  idItems.add(item);
                }
              }
              for (String id : idsBefore) {
                // Note that ResourceFile has a flat map (not a multimap) so it doesn't
                // record all items (unlike the myItems map) so we need to remove the map
                // items manually, can't just do map.remove(item.getName(), item)
                List<ResourceItem> mapItems = map.get(id);
                if (mapItems != null && !mapItems.isEmpty()) {
                  List<ResourceItem> toDelete = new ArrayList<>(mapItems.size());
                  for (ResourceItem mapItem : mapItems) {
                    if (mapItem instanceof PsiResourceItem && ((PsiResourceItem)mapItem).getSourceFile() == psiResourceFile) {
                      toDelete.add(mapItem);
                    }
                  }
                  for (ResourceItem delete : toDelete) {
                    map.remove(delete.getName(), delete);
                  }
                }
              }
              for (PsiResourceItem item : idItems) {
                psiResourceFile.removeItem(item);
              }
            }
          }

          // Add items for this file.
          List<PsiResourceItem> idItems = new ArrayList<>();
          file = ensureValid(file);
          if (file != null) {
            addIds(result, idItems, file);
          }
          if (!idItems.isEmpty()) {
            for (PsiResourceItem item : idItems) {
              psiResourceFile.addItem(item);
            }
          }

          rescanJustDataBinding(psiFile);
          // Identities may have changed even if the ids are the same, so update maps
          invalidateParentCaches(myNamespace, ResourceType.ID);
        }
      } else {
        // Remove old items first, if switching to PSI. Rescan below to add back, but with a possibly different multimap list order.
        boolean switchingToPsi = source != null;
        if (switchingToPsi) {
          removeItemsFromSource(source);
        }
        // For unit test tracking purposes only
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        ourFullRescans++;

        PsiDirectory parent = file.getParent();
        assert parent != null; // since we have a folder type

        List<ResourceType> resourceTypes = FolderTypeRelationship.getRelatedResourceTypes(folderType);
        assert resourceTypes.size() >= 1 : folderType;
        ResourceType type = resourceTypes.get(0);

        boolean idGeneratingFolder = FolderTypeRelationship.isIdGeneratingFolderType(folderType);

        file = ensureValid(file);
        if (file != null) {
          PsiDirectory fileParent = psiFile.getParent();
          if (fileParent != null) {
            FolderConfiguration folderConfiguration = FolderConfiguration.getConfigForFolder(fileParent.getName());
            if (folderConfiguration != null) {
              boolean idGeneratingFile = idGeneratingFolder && file.getFileType() == StdFileTypes.XML;
              scanFileResourceFileAsPsi(result, folderType, folderConfiguration, type, idGeneratingFile, file);
            }
          }
          setModificationCount(ourModificationCounter.incrementAndGet());
          invalidateParentCaches();
        }
      }
    }

    commitToRepository(result);
  }

  private void scan(@NotNull VirtualFile file) {
    ResourceFolderType folderType = ResourceHelper.getFolderType(file);
    if (folderType == null) {
      return;
    }

    if (isResourceFile(file) && isRelevantFile(file)) {
      PsiFile psiFile = myPsiManager.findFile(file);
      if (psiFile != null) {
        scan(psiFile, folderType);
      }
    }
  }

  /**
   * Removes resource items matching the given source file and tag.
   *
   * @return true if any resource items were removed from the repository
   */
  private boolean removeItemForTag(@NotNull ResourceItemSource<PsiResourceItem> source, @NotNull XmlTag xmlTag) {
    boolean changed = false;

    synchronized (ITEM_MAP_LOCK) {
      for (Iterator<PsiResourceItem> sourceIter = source.iterator(); sourceIter.hasNext();) {
        PsiResourceItem item = sourceIter.next();
        if (item.wasTag(xmlTag)) {
          ListMultimap<String, ResourceItem> map = myFullTable.get(myNamespace, item.getType());
          List<ResourceItem> items = map.get(item.getName());
          for (Iterator<ResourceItem> iter = items.iterator(); iter.hasNext(); ) {
            ResourceItem candidate = iter.next();
            if (candidate == item) {
              iter.remove();
              changed = true;
              break;
            }
          }
          sourceIter.remove();
        }
      }

      return changed;
    }
  }

  /**
   * Removes all resource items associated the given source file.
   *
   * @return true if any resource items were removed from the repository
   */
  private boolean removeItemsFromSource(@NotNull ResourceItemSource<? extends ResourceItem> source) {
    boolean changed = false;

    synchronized (ITEM_MAP_LOCK) {
      for (ResourceItem item : source) {
        ListMultimap<String, ResourceItem> map = myFullTable.get(myNamespace, item.getType());
        List<ResourceItem> items = map.get(item.getName());
        for (Iterator<ResourceItem> iter = items.iterator(); iter.hasNext(); ) {
          ResourceItem candidate = iter.next();
          if (candidate == item) {
            iter.remove();
            changed = true;
            break;
          }
        }
        if (items.isEmpty()) {
          map.removeAll(item.getName());
        }
      }
    }
    return changed;
  }

  /**
   * Find the {@link com.android.tools.idea.configurations.Configuration} for the provided file and
   * it's associated {@link AndroidTargetData} asynchronously and then run the provided consumer on the EDT
   */
  private void getAndroidTargetDataThenRun(@NotNull VirtualFile file, @NotNull Consumer<AndroidTargetData> consumer) {
    Module module = myFacet.getModule();
    ConfigurationManager configurationManager = ConfigurationManager.findExistingInstance(module);
    if (configurationManager == null) {
      return;
    }
    CompletableFuture.supplyAsync(() -> {
      IAndroidTarget target = configurationManager.getConfiguration(file).getTarget();
      if (target != null) {
        return AndroidTargetData.getTargetData(target, module);
      }
      return null;
    }, PooledThreadExecutor.INSTANCE)
      .thenAcceptAsync((target) -> {
        if (target != null) {
          consumer.accept(target);
        }
      }, EdtExecutorService.getInstance());
  }

  /**
   * Called when a bitmap has been changed/deleted. In that case we need to clear out any caches for that
   * image held by layout lib.
   */
  private void bitmapUpdated(@NotNull VirtualFile bitmap) {
    Module module = myFacet.getModule();
    getAndroidTargetDataThenRun(bitmap, (targetData) -> targetData.clearLayoutBitmapCache(module));
  }

  /**
   * Called when a font file has been changed/deleted. This removes the corresponding file from the
   * Typeface cache inside layoutlib.
   */
  private void clearFontCache(@NotNull VirtualFile virtualFile) {
    getAndroidTargetDataThenRun(virtualFile, (targetData) -> targetData.clearFontCache(virtualFile.getPath()));
  }

  @NotNull
  public PsiTreeChangeListener getPsiListener() {
    return myPsiListener;
  }

  /**
   * PSI listener which schedules a full file rescan after every change.
   *
   * @see IncrementalUpdatePsiListener
   */
  private final class SimplePsiListener extends PsiTreeAnyChangeAbstractAdapter {
    @Override
    protected void onChange(@Nullable PsiFile psiFile) {
      ResourceFolderType folderType = ResourceHelper.getFolderType(psiFile);
      if (folderType != null && psiFile != null && isResourceFile(psiFile)) {
        scheduleScan(psiFile, folderType);
      }
    }
  }

  /**
   * PSI listener which keeps the repository up to date. It handles simple edits synchronously and schedules rescans for other events.
   *
   * @see IncrementalUpdatePsiListener
   */
  private final class IncrementalUpdatePsiListener extends PsiTreeChangeAdapter {
    private boolean myIgnoreChildrenChanged;

    @Override
    public void childAdded(@NotNull PsiTreeChangeEvent event) {
      PsiFile psiFile = event.getFile();
      if (psiFile != null && isRelevantFile(psiFile)) {
        if (isScanPending(psiFile)) {
          return;
        }
        // Some child was added within a file.
        ResourceFolderType folderType = ResourceHelper.getFolderType(psiFile);
        if (folderType != null && isResourceFile(psiFile)) {
          PsiElement child = event.getChild();
          PsiElement parent = event.getParent();
          if (folderType == VALUES) {
            if (child instanceof XmlTag) {
              XmlTag tag = (XmlTag)child;

              if (isItemElement(tag)) {
                if (convertToPsiIfNeeded(psiFile, folderType)) {
                  return;
                }
                ResourceItemSource<? extends ResourceItem> source = mySources.get(psiFile.getVirtualFile());
                if (source != null) {
                  assert source instanceof PsiResourceFile;
                  PsiResourceFile psiResourceFile = (PsiResourceFile)source;
                  String name = tag.getAttributeValue(ATTR_NAME);
                  if (isValidResourceName(name)) {
                    ResourceType type = getResourceTypeForResourceTag(tag);
                    if (type == ResourceType.STYLEABLE) {
                      // Can't handle declare styleable additions incrementally yet; need to update paired attr items.
                      scheduleScan(psiFile, folderType);
                      return;
                    }
                    if (type != null) {
                      PsiResourceItem item = PsiResourceItem.forXmlTag(name, type, myNamespace, tag, true);
                      synchronized (ITEM_MAP_LOCK) {
                        getMap(myNamespace, type, true).put(name, item);
                        psiResourceFile.addItem(item);
                        setModificationCount(ourModificationCounter.incrementAndGet());
                        invalidateParentCaches(myNamespace, type);
                        return;
                      }
                    }
                  }
                }
              }

              // See if you just added a new item inside a <style> or <array> or <declare-styleable> etc.
              XmlTag parentTag = tag.getParentTag();
              if (parentTag != null && getResourceTypeForResourceTag(parentTag) != null) {
                if (convertToPsiIfNeeded(psiFile, folderType)) {
                  return;
                }
                // Yes just invalidate the corresponding cached value.
                ResourceItem parentItem = findValueResourceItem(parentTag, psiFile);
                if (parentItem instanceof PsiResourceItem) {
                  if (((PsiResourceItem)parentItem).recomputeValue()) {
                    setModificationCount(ourModificationCounter.incrementAndGet());
                  }
                  return;
                }
              }

              scheduleScan(psiFile, folderType);
              // Else: fall through and do full file rescan.
            } else if (parent instanceof XmlText) {
              // If the edit is within an item tag.
              XmlText text = (XmlText)parent;
              handleValueXmlTextEdit(text.getParentTag(), psiFile);
              return;
            } else if (child instanceof XmlText) {
              // If the edit is within an item tag.
              handleValueXmlTextEdit(parent, psiFile);
              return;
            } else if (parent instanceof XmlComment || child instanceof XmlComment) {
              // Can ignore comment edits or new comments.
              return;
            }
            scheduleScan(psiFile, folderType);
          } else if (FolderTypeRelationship.isIdGeneratingFolderType(folderType) && psiFile.getFileType() == StdFileTypes.XML) {
            if (parent instanceof XmlComment || child instanceof XmlComment) {
              return;
            }
            if (parent instanceof XmlText || (child instanceof XmlText && child.getText().trim().isEmpty())) {
              return;
            }

            if (parent instanceof XmlElement && child instanceof XmlElement) {
              if (child instanceof XmlTag) {
                if (convertToPsiIfNeeded(psiFile, folderType)) {
                  return;
                }
                if (affectsDataBinding((XmlTag)child)) {
                  rescanJustDataBinding(psiFile);
                }
                List<PsiResourceItem> ids = new ArrayList<>();
                Map<ResourceType, ListMultimap<String, ResourceItem>> result = new HashMap<>();
                addIds(result, ids, child, true);
                commitToRepository(result);
                if (!ids.isEmpty()) {
                  ResourceItemSource<? extends ResourceItem> resFile = mySources.get(psiFile.getVirtualFile());
                  if (resFile != null) {
                    assert resFile instanceof PsiResourceFile;
                    PsiResourceFile psiResourceFile = (PsiResourceFile)resFile;
                    for (PsiResourceItem id : ids) {
                      psiResourceFile.addItem(id);
                    }
                    setModificationCount(ourModificationCounter.incrementAndGet());
                    invalidateParentCaches(myNamespace, ResourceType.ID);
                  }
                }
                return;
              } else if (child instanceof XmlAttribute || parent instanceof XmlAttribute) {
                // We check both because invalidation might come from XmlAttribute if it is inserted at once.
                XmlAttribute attribute = parent instanceof XmlAttribute ? (XmlAttribute)parent : (XmlAttribute)child;
                // warning for separate if branches suppressed because to do.
                if (ATTR_ID.equals(attribute.getLocalName()) && ANDROID_URI.equals(attribute.getNamespace())) {
                  // TODO: Update it incrementally.
                  scheduleScan(psiFile, folderType);
                } else if (affectsDataBinding(attribute)){
                  rescanJustDataBinding(psiFile);
                }
              }
            }
          } else if (folderType == FONT) {
            clearFontCache(psiFile.getVirtualFile());
          }
        }
      }

      myIgnoreChildrenChanged = true;
    }

    @Override
    public void childRemoved(@NotNull PsiTreeChangeEvent event) {
      PsiFile psiFile = event.getFile();
      if (psiFile != null && isRelevantFile(psiFile)) {
        if (isScanPending(psiFile)) {
          return;
        }
        // Some child was removed within a file.
        ResourceFolderType folderType = ResourceHelper.getFolderType(psiFile);
        if (folderType != null && isResourceFile(psiFile)) {
          PsiElement child = event.getChild();
          PsiElement parent = event.getParent();

          if (folderType == VALUES) {
            if (child instanceof XmlTag) {
              XmlTag tag = (XmlTag)child;

              // See if you just removed an item inside a <style> or <array> or <declare-styleable> etc.
              if (parent instanceof XmlTag) {
                XmlTag parentTag = (XmlTag)parent;
                if (getResourceTypeForResourceTag(parentTag) != null) {
                  if (convertToPsiIfNeeded(psiFile, folderType)) {
                    return;
                  }
                  // Yes just invalidate the corresponding cached value.
                  ResourceItem resourceItem = findValueResourceItem(parentTag, psiFile);
                  if (resourceItem instanceof PsiResourceItem) {
                    if (((PsiResourceItem)resourceItem).recomputeValue()) {
                      setModificationCount(ourModificationCounter.incrementAndGet());
                    }

                    if (resourceItem.getType() == ResourceType.ATTR) {
                      parentTag = parentTag.getParentTag();
                      if (parentTag != null && getResourceTypeForResourceTag(parentTag) == ResourceType.STYLEABLE) {
                        ResourceItem declareStyleable = findValueResourceItem(parentTag, psiFile);
                        if (declareStyleable instanceof PsiResourceItem) {
                          if (((PsiResourceItem)declareStyleable).recomputeValue()) {
                            setModificationCount(ourModificationCounter.incrementAndGet());
                          }
                        }
                      }
                    }
                    return;
                  }
                }
              }

              if (isItemElement(tag)) {
                if (convertToPsiIfNeeded(psiFile, folderType)) {
                  return;
                }
                ResourceItemSource<? extends ResourceItem> source = mySources.get(psiFile.getVirtualFile());
                if (source != null) {
                  PsiResourceFile resourceFile = (PsiResourceFile)source;
                  String name;
                  if (!tag.isValid()) {
                    ResourceItem item = findValueResourceItem(tag, psiFile);
                    if (item != null) {
                      name = item.getName();
                    } else {
                      // Can't find the name of the deleted tag; just do a full rescan
                      scheduleScan(psiFile, folderType);
                      return;
                    }
                  } else {
                    name = tag.getAttributeValue(ATTR_NAME);
                  }
                  if (name != null) {
                    ResourceType type = getResourceTypeForResourceTag(tag);
                    if (type != null) {
                      synchronized (ITEM_MAP_LOCK) {
                        boolean removed = removeItemForTag(resourceFile, tag);
                        if (removed) {
                          setModificationCount(ourModificationCounter.incrementAndGet());
                          invalidateParentCaches(myNamespace, type);
                        }
                      }
                    }
                  }

                  return;
                }
              }

              scheduleScan(psiFile, folderType);
            } else if (parent instanceof XmlText) {
              // If the edit is within an item tag.
              XmlText text = (XmlText)parent;
              handleValueXmlTextEdit(text.getParentTag(), psiFile);
            } else if (child instanceof XmlText) {
              handleValueXmlTextEdit(parent, psiFile);
            } else if (parent instanceof XmlComment || child instanceof XmlComment) {
              // Can ignore comment edits or removed comments.
              return;
            } else {
              // Some other change: do full file rescan.
              scheduleScan(psiFile, folderType);
            }
          } else if (FolderTypeRelationship.isIdGeneratingFolderType(folderType) && psiFile.getFileType() == StdFileTypes.XML) {
            // TODO: Handle removals of id's (values an attributes) incrementally.
            scheduleScan(psiFile, folderType);
          } else if (folderType == FONT) {
            clearFontCache(psiFile.getVirtualFile());
          }
        }
      }

      myIgnoreChildrenChanged = true;
    }

    @Override
    public void childReplaced(@NotNull PsiTreeChangeEvent event) {
      PsiFile psiFile = event.getFile();
      if (psiFile != null) {
        if (isScanPending(psiFile)) {
          return;
        }
        // This method is called when you edit within a file.
        if (isRelevantFile(psiFile)) {
          // First determine if the edit is non-consequential.
          // That's the case if the XML edited is not a resource file (e.g. the manifest file),
          // or if it's within a file that is not a value file or an id-generating file (layouts and menus),
          // such as editing the content of a drawable XML file.
          ResourceFolderType folderType = ResourceHelper.getFolderType(psiFile);
          if (folderType != null && FolderTypeRelationship.isIdGeneratingFolderType(folderType) &&
              psiFile.getFileType() == StdFileTypes.XML) {
            // The only way the edit affected the set of resources was if the user added or removed an
            // id attribute. Since these can be added redundantly we can't automatically remove the old
            // value if you renamed one, so we'll need a full file scan.
            // However, we only need to do this scan if the change appears to be related to ids; this can
            // only happen if the attribute value is changed.
            PsiElement parent = event.getParent();
            PsiElement child = event.getChild();
            if (parent instanceof XmlText || child instanceof XmlText || parent instanceof XmlComment || child instanceof XmlComment) {
              return;
            }
            if (parent instanceof XmlElement && child instanceof XmlElement) {
              if (event.getOldChild() == event.getNewChild()) {
                // We're not getting accurate PSI information: we have to do a full file scan.
                scheduleScan(psiFile, folderType);
                return;
              }
              if (child instanceof XmlAttributeValue) {
                assert parent instanceof XmlAttribute : parent;
                XmlAttribute attribute = (XmlAttribute)parent;
                if (ATTR_ID.equals(attribute.getLocalName()) && ANDROID_URI.equals(attribute.getNamespace())) {
                  // For each id attribute!
                  ResourceItemSource<? extends ResourceItem> source = mySources.get(psiFile.getVirtualFile());
                  if (source != null) {
                    XmlTag xmlTag = attribute.getParent();
                    PsiElement oldChild = event.getOldChild();
                    PsiElement newChild = event.getNewChild();
                    if (oldChild instanceof XmlAttributeValue && newChild instanceof XmlAttributeValue) {
                      XmlAttributeValue oldValue = (XmlAttributeValue)oldChild;
                      XmlAttributeValue newValue = (XmlAttributeValue)newChild;
                      ResourceUrl oldResourceUrl = ResourceUrl.parse(oldValue.getValue());
                      ResourceUrl newResourceUrl = ResourceUrl.parse(newValue.getValue());

                      // Make sure to compare name as well as urlType, e.g. if both have @+id or not.
                      if (Objects.equals(oldResourceUrl, newResourceUrl)) {
                        // Can happen when there are error nodes (e.g. attribute value not yet closed during typing etc).
                        return;
                      }

                      if (handleIdChange(psiFile, source, xmlTag, newResourceUrl, stripIdPrefix(oldValue.getValue()))) {
                        return;
                      }
                    }
                  }

                  scheduleScan(psiFile, folderType);
                }
              } else if (parent instanceof XmlAttributeValue) {
                PsiElement grandParent = parent.getParent();
                if (grandParent instanceof XmlProcessingInstruction) {
                  // Don't care about edits in the processing instructions, e.g. editing the encoding attribute in
                  // <?xml version="1.0" encoding="utf-8"?>
                  return;
                }
                assert grandParent instanceof XmlAttribute : parent;
                XmlAttribute attribute = (XmlAttribute)grandParent;
                if (ATTR_ID.equals(attribute.getLocalName()) &&
                    ANDROID_URI.equals(attribute.getNamespace())) {
                  // For each id attribute!
                  ResourceItemSource<? extends ResourceItem> resFile = mySources.get(psiFile.getVirtualFile());
                  if (resFile != null) {
                    XmlTag xmlTag = attribute.getParent();
                    PsiElement oldChild = event.getOldChild();
                    PsiElement newChild = event.getNewChild();
                    ResourceUrl oldResourceUrl = ResourceUrl.parse(oldChild.getText());
                    ResourceUrl newResourceUrl = ResourceUrl.parse(newChild.getText());

                    // Make sure to compare name as well as urlType, e.g. if both have @+id or not.
                    if (Objects.equals(oldResourceUrl, newResourceUrl)) {
                      // Can happen when there are error nodes (e.g. attribute value not yet closed during typing etc).
                      return;
                    }

                    if (handleIdChange(psiFile, resFile, xmlTag, newResourceUrl, stripIdPrefix(oldChild.getText()))) {
                      return;
                    }
                  }

                  scheduleScan(psiFile, folderType);
                } else if (affectsDataBinding(attribute)) {
                  rescanJustDataBinding(psiFile);
                } else if (folderType != VALUES) {
                  // This is an XML change within an ID generating folder to something that it's not an ID. While we do not need
                  // to generate the ID, we need to notify that something relevant has changed.
                  // One example of this change would be an edit to a drawable.
                  setModificationCount(ourModificationCounter.incrementAndGet());
                }
              }

              return;
            }

            // TODO: Handle adding/removing elements in layouts incrementally.

            scheduleScan(psiFile, folderType);
          } else if (folderType == VALUES) {
            // This is a folder that *may* contain XML files. Check if this is a relevant XML edit.
            PsiElement parent = event.getParent();
            if (parent instanceof XmlElement) {
              // Editing within an XML file
              // An edit in a comment can be ignored
              // An edit in a text inside an element can be used to invalidate the ResourceValue of an element
              //    (need to search upwards since strings can have HTML content)
              // An edit between elements can be ignored
              // An edit to an attribute name (not the attribute value for the attribute named "name"...) can
              //     sometimes be ignored (if you edit type or name, consider what to do)
              // An edit of an attribute value can affect the name of type so update item
              // An edit of other parts; for example typing in a new <string> item character by character.
              // etc.

              if (parent instanceof XmlComment) {
                // Nothing to do
                return;
              }

              // See if you just removed an item inside a <style> or <array> or <declare-styleable> etc.
              if (parent instanceof XmlTag) {
                XmlTag parentTag = (XmlTag)parent;
                if (getResourceTypeForResourceTag(parentTag) != null) {
                  if (convertToPsiIfNeeded(psiFile, folderType)) {
                    return;
                  }
                  // Yes just invalidate the corresponding cached value
                  ResourceItem resourceItem = findValueResourceItem(parentTag, psiFile);
                  if (resourceItem instanceof PsiResourceItem) {
                    if (((PsiResourceItem)resourceItem).recomputeValue()) {
                      setModificationCount(ourModificationCounter.incrementAndGet());
                    }
                    return;
                  }
                }

                if (parentTag.getName().equals(TAG_RESOURCES)
                    && event.getOldChild() instanceof XmlText
                    && event.getNewChild() instanceof XmlText) {
                  return;
                }
              }

              if (parent instanceof XmlText) {
                XmlText text = (XmlText)parent;
                handleValueXmlTextEdit(text.getParentTag(), psiFile);
                return;
              }

              if (parent instanceof XmlAttributeValue) {
                PsiElement attribute = parent.getParent();
                if (attribute instanceof XmlProcessingInstruction) {
                  // Don't care about edits in the processing instructions, e.g. editing the encoding attribute in
                  // <?xml version="1.0" encoding="utf-8"?>
                  return;
                }
                PsiElement tag = attribute.getParent();
                assert attribute instanceof XmlAttribute : attribute;
                XmlAttribute xmlAttribute = (XmlAttribute)attribute;
                assert tag instanceof XmlTag : tag;
                XmlTag xmlTag = (XmlTag)tag;
                String attributeName = xmlAttribute.getName();
                // We could also special case handling of editing the type attribute, and the parent attribute,
                // but editing these is rare enough that we can just stick with the fallback full file scan for those
                // scenarios.
                if (isItemElement(xmlTag) && attributeName.equals(ATTR_NAME)) {
                  // Edited the name of the item: replace it.
                  ResourceType type = getResourceTypeForResourceTag(xmlTag);
                  if (type != null) {
                    String oldName = event.getOldChild().getText();
                    String newName = event.getNewChild().getText();
                    if (oldName.equals(newName)) {
                      // Can happen when there are error nodes (e.g. attribute value not yet closed during typing etc).
                      return;
                    }
                    // findResourceItem depends on PSI in some cases, so we need to bail and rescan if not PSI.
                    if (convertToPsiIfNeeded(psiFile, folderType)) {
                      return;
                    }
                    ResourceItem item = findResourceItem(type, psiFile, oldName, xmlTag);
                    if (item != null) {
                      synchronized (ITEM_MAP_LOCK) {
                        ListMultimap<String, ResourceItem> map = myFullTable.get(myNamespace, item.getType());
                        if (map != null) {
                          // Found the relevant item: delete it and create a new one in a new location.
                          map.remove(oldName, item);
                          if (isValidResourceName(newName)) {
                            PsiResourceItem newItem = PsiResourceItem.forXmlTag(newName, type, myNamespace, xmlTag, true);
                            map.put(newName, newItem);
                            ResourceItemSource<? extends ResourceItem> resFile = mySources.get(psiFile.getVirtualFile());
                            if (resFile != null) {
                              PsiResourceFile resourceFile = (PsiResourceFile)resFile;
                              resourceFile.removeItem((PsiResourceItem)item);
                              resourceFile.addItem(newItem);
                            }
                            else {
                              assert false : item;
                            }
                          }
                          setModificationCount(ourModificationCounter.incrementAndGet());
                          invalidateParentCaches(myNamespace, type);
                        }
                      }

                      // Invalidate surrounding declare styleable if any
                      if (type == ResourceType.ATTR) {
                        XmlTag parentTag = xmlTag.getParentTag();
                        if (parentTag != null && getResourceTypeForResourceTag(parentTag) == ResourceType.STYLEABLE) {
                          ResourceItem style = findValueResourceItem(parentTag, psiFile);
                          if (style instanceof PsiResourceItem) {
                            ((PsiResourceItem)style).recomputeValue();
                          }
                        }
                      }

                      return;
                    }
                  } else {
                    XmlTag parentTag = xmlTag.getParentTag();
                    if (parentTag != null && getResourceTypeForResourceTag(parentTag) != null) {
                      // <style>, or <plurals>, or <array>, or <string-array>, ...
                      // Edited the attribute value of an item that is wrapped in a <style> tag: invalidate parent cached value.
                      if (convertToPsiIfNeeded(psiFile, folderType)) {
                        return;
                      }
                      ResourceItem resourceItem = findValueResourceItem(parentTag, psiFile);
                      if (resourceItem instanceof PsiResourceItem) {
                        if (((PsiResourceItem)resourceItem).recomputeValue()) {
                          setModificationCount(ourModificationCounter.incrementAndGet());
                        }
                        return;
                      }
                    }
                  }
                }
              }
            }

            // Fall through: We were not able to directly manipulate the repository to accommodate
            // the edit, so re-scan the whole value file instead.
            scheduleScan(psiFile, folderType);
          } else if (folderType == COLOR) {
            PsiElement parent = event.getParent();
            if (parent instanceof XmlElement) {
              if (parent instanceof XmlComment) {
                return; // Nothing to do.
              }

              if (parent instanceof XmlAttributeValue) {
                PsiElement attribute = parent.getParent();
                if (attribute instanceof XmlProcessingInstruction) {
                  // Don't care about edits in the processing instructions, e.g. editing the encoding attribute in
                  // <?xml version="1.0" encoding="utf-8"?>
                  return;
                }
              }

              setModificationCount(ourModificationCounter.incrementAndGet());
              return;
            }
          } else if (folderType == FONT) {
            clearFontCache(psiFile.getVirtualFile());
          } else if (folderType != null) {
            PsiElement parent = event.getParent();

            if (parent instanceof XmlElement) {
              if (parent instanceof XmlComment) {
                return; // Nothing to do.
              }

              // A change to an XML file that does not require adding/removing resources. This could be a change to the contents of an XML
              // file in the raw folder.
              setModificationCount(ourModificationCounter.incrementAndGet());
            }
          } // else: can ignore this edit.
        }
      }

      myIgnoreChildrenChanged = true;
    }

    /**
     * Tries to handle changes to an {@code android:id} tag incrementally.
     *
     * @return true if incremental change succeeded, false otherwise (i.e. a rescan is necessary).
     */
    private boolean handleIdChange(@NotNull PsiFile psiFile,
                                   @NotNull ResourceItemSource<? extends ResourceItem> resFile,
                                   @NotNull XmlTag xmlTag,
                                   @Nullable ResourceUrl newResourceUrl,
                                   @NotNull String oldName) {
      if (resFile instanceof PsiResourceFile) {
        PsiResourceFile psiResourceFile = (PsiResourceFile)resFile;
        ResourceItem item = findResourceItem(ResourceType.ID, psiFile, oldName, xmlTag);
        synchronized (ITEM_MAP_LOCK) {
          ListMultimap<String, ResourceItem> map = myFullTable.get(myNamespace, ResourceType.ID);
          if (map != null) {
            boolean madeChanges = false;

            if (item != null) {
              // Found the relevant item: delete it and create a new one in a new location
              map.remove(oldName, item);
              if (psiResourceFile.isSourceOf(item)) {
                psiResourceFile.removeItem((PsiResourceItem)item);
              }
              madeChanges = true;
            }

            if (newResourceUrl != null) {
              String newName = newResourceUrl.name;
              if (newResourceUrl.urlType == ResourceUrl.UrlType.CREATE && isValidResourceName(newName)) {
                PsiResourceItem newItem = PsiResourceItem.forXmlTag(newName, ResourceType.ID, myNamespace, xmlTag, true);
                map.put(newName, newItem);
                psiResourceFile.addItem(newItem);
                madeChanges = true;
              }
            }

            if (madeChanges) {
              setModificationCount(ourModificationCounter.incrementAndGet());
              invalidateParentCaches(myNamespace, ResourceType.ID);
            }
            return true;
          }
        }
      }
      return false;
    }

    private void handleValueXmlTextEdit(@Nullable PsiElement parent, @NotNull PsiFile psiFile) {
      if (!(parent instanceof XmlTag)) {
        // Edited text outside the root element.
        return;
      }
      XmlTag parentTag = (XmlTag)parent;
      String parentTagName = parentTag.getName();
      if (parentTagName.equals(TAG_RESOURCES)) {
        // Editing whitespace between top level elements; ignore.
        return;
      }

      if (parentTagName.equals(TAG_ITEM)) {
        XmlTag style = parentTag.getParentTag();
        if (style != null && ResourceType.fromXmlTagName(style.getName()) != null) {
          ResourceFolderType folderType = ResourceHelper.getFolderType(psiFile);
          assert folderType != null;
          if (convertToPsiIfNeeded(psiFile, folderType)) {
            return;
          }
          // <style>, or <plurals>, or <array>, or <string-array>, ...
          // Edited the text value of an item that is wrapped in a <style> tag: invalidate.
          ResourceItem item = findValueResourceItem(style, psiFile);
          if (item instanceof PsiResourceItem) {
            boolean cleared = ((PsiResourceItem)item).recomputeValue();
            if (cleared) { // Only bump revision if this is a value which has already been observed!
              setModificationCount(ourModificationCounter.incrementAndGet());
            }
          }
          return;
        }
      }

      // Find surrounding item.
      while (parentTag != null) {
        if (isItemElement(parentTag)) {
          ResourceFolderType folderType = ResourceHelper.getFolderType(psiFile);
          assert folderType != null;
          if (convertToPsiIfNeeded(psiFile, folderType)) {
            return;
          }
          ResourceItem item = findValueResourceItem(parentTag, psiFile);
          if (item instanceof PsiResourceItem) {
            // Edited XML value.
            boolean cleared = ((PsiResourceItem)item).recomputeValue();
            if (cleared) { // Only bump revision if this is a value which has already been observed!
              setModificationCount(ourModificationCounter.incrementAndGet());
            }
          }
          break;
        }
        parentTag = parentTag.getParentTag();
      }

      // Fully handled; other whitespace changes do not affect resources.
    }

    @Override
    public final void beforeChildrenChange(@NotNull PsiTreeChangeEvent event) {
      myIgnoreChildrenChanged = false;
    }

    @Override
    public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
      PsiElement parent = event.getParent();
      // Called after children have changed. There are typically individual childMoved, childAdded etc
      // calls that we hook into for more specific details. However, there are some events we don't
      // catch using those methods, and for that we have the below handling.
      if (myIgnoreChildrenChanged) {
        // We've already processed this change as one or more individual childMoved, childAdded, childRemoved etc calls.
        // However, we sometimes get some surprising (=bogus) events where the parent and the child
        // are the same, and in those cases there may be other child events we need to process
        // so fall through and process the whole file.
        if (parent != event.getChild()) {
          return;
        }
      }
      else if (event instanceof PsiTreeChangeEventImpl && (((PsiTreeChangeEventImpl)event).isGenericChange())) {
          return;
      }

      if (parent != null && parent.getChildren().length == 1 && parent.getChildren()[0] instanceof PsiWhiteSpace) {
        // This event is just adding white spaces.
        return;
      }

      PsiFile psiFile = event.getFile();
      if (psiFile != null && isRelevantFile(psiFile)) {
        VirtualFile file = psiFile.getVirtualFile();
        if (file != null) {
          ResourceFolderType folderType = ResourceHelper.getFolderType(psiFile);
          if (folderType != null && isResourceFile(psiFile)) {
            // TODO: If I get an XmlText change and the parent is the resources tag or it's a layout, nothing to do.
            scheduleScan(psiFile, folderType);
          }
        }
      } else {
        Throwable throwable = new Throwable();
        throwable.fillInStackTrace();
        LOG.debug("Received unexpected childrenChanged event for inter-file operations", throwable);
      }
    }

    /**
     * Checks if changes in the given attribute affects data binding.
     *
     * @param attribute The XML attribute
     * @return true if changes in this element would affect data binding
     */
    private boolean affectsDataBinding(@NotNull XmlAttribute attribute) {
      return ArrayUtil.contains(attribute.getLocalName(), ATTRS_DATA_BINDING)
             && ArrayUtil.contains(attribute.getParent().getLocalName(), TAGS_DATA_BINDING);
    }

    /**
     * Checks if changes in the given XmlTag affects data binding.
     *
     * @param xmlTag the tag to check
     * @return true if changes in the xml tag would affect data binding info, false otherwise
     */
    private boolean affectsDataBinding(@NotNull XmlTag xmlTag) {
      return ArrayUtil.contains(xmlTag.getLocalName(), TAGS_DATA_BINDING);
    }
  }

  void onBitmapFileUpdated(@NotNull VirtualFile file) {
    if (ResourceHelper.getFolderType(file) != null) {
      bitmapUpdated(file);
    }
  }

  void onFileCreated(@NotNull VirtualFile file) {
    scan(file);
  }

  private Project getProject() {
    return myFacet.getModule().getProject();
  }

  void onFileOrDirectoryRemoved(@NotNull VirtualFile file) {
    if (file.isDirectory()) {
      for (Iterator<Map.Entry<VirtualFile, ResourceItemSource<? extends ResourceItem>>> iterator = mySources.entrySet().iterator();
           iterator.hasNext(); ) {
        Map.Entry<VirtualFile, ResourceItemSource<? extends ResourceItem>> entry = iterator.next();
        iterator.remove();
        VirtualFile sourceFile = entry.getKey();
        if (VfsUtilCore.isAncestor(file, sourceFile, true)) {
          ResourceItemSource<? extends ResourceItem> source = entry.getValue();
          onSourceRemoved(sourceFile, source);
        }
      }
    }
    else {
      ResourceItemSource<? extends ResourceItem> source = mySources.remove(file);
      if (source != null) {
        onSourceRemoved(file, source);
      }
    }
  }

  private void onSourceRemoved(@NotNull VirtualFile file, @NotNull ResourceItemSource<? extends ResourceItem> source) {
    boolean removed = removeItemsFromSource(source);
    if (removed) {
      setModificationCount(ourModificationCounter.incrementAndGet());
      invalidateParentCaches();
    }

    ResourceFolderType folderType = ResourceHelper.getFolderType(file);
    if (folderType == DRAWABLE) {
      FileType fileType = file.getFileType();
      if (fileType.isBinary() && fileType == FileTypeManager.getInstance().getFileTypeByExtension(EXT_PNG)) {
        bitmapUpdated(file);
      }
    }
    else if (folderType == FONT) {
      clearFontCache(file);
    }
  }

  private void rescanJustDataBinding(@NotNull PsiFile psiFile) {
    ResourceItemSource<? extends ResourceItem> resFile = mySources.get(psiFile.getVirtualFile());
    if (resFile != null) {
      // Data-binding files are always scanned as PsiResourceFiles.
      PsiResourceFile resourceFile = (PsiResourceFile)resFile;

      // TODO: this is a targeted workaround for b/77658263, but we need to fix the invalid PSI eventually.
      // At this point, it's possible resFile._psiFile is invalid and has a different FileViewProvider than psiFile, even though in theory
      // they should be identical.
      if (!resourceFile.getPsiFile().isValid()) {
        resourceFile.setPsiFile(psiFile, resourceFile.getFolderConfiguration());
      }

      setModificationCount(ourModificationCounter.incrementAndGet());
      scanBindingLayout(resourceFile, getModificationCount());
    }
  }

  private static boolean isItemElement(@NotNull XmlTag xmlTag) {
    String tag = xmlTag.getName();
    if (tag.equals(TAG_RESOURCES)) {
      return false;
    }
    return tag.equals(TAG_ITEM) || ResourceType.fromXmlTagName(tag) != null;
  }

  @Nullable
  private ResourceItem findValueResourceItem(@NotNull XmlTag tag, @NotNull PsiFile file) {
    if (!tag.isValid()) {
      // This function should only be used if we know file's items are PsiResourceItems.
      ResourceItemSource<? extends ResourceItem> resFile = mySources.get(file.getVirtualFile());
      if (resFile != null) {
        assert resFile instanceof PsiResourceFile;
        PsiResourceFile resourceFile = (PsiResourceFile)resFile;
        for (ResourceItem item : resourceFile) {
          PsiResourceItem pri = (PsiResourceItem)item;
          if (pri.wasTag(tag)) {
            return item;
          }
        }
      }
      return null;
    }
    String name = tag.getAttributeValue(ATTR_NAME);
    synchronized (ITEM_MAP_LOCK) {
      return name != null ? findValueResourceItem(tag, file, name) : null;
    }
  }

  @Nullable
  private ResourceItem findValueResourceItem(@NotNull XmlTag tag, @NotNull PsiFile file, @NotNull String name) {
    ResourceType type = getResourceTypeForResourceTag(tag);
    return findResourceItem(type, file, name, tag);
  }

  @Nullable
  private ResourceItem findResourceItem(@Nullable ResourceType type, @NotNull PsiFile file, @Nullable String name, @Nullable XmlTag tag) {
    if (type == null || name == null) {
      return null;
    }

    // Do IO work before obtaining the lock:
    File ioFile = VfsUtilCore.virtualToIoFile(file.getVirtualFile());

    synchronized (ITEM_MAP_LOCK) {
      ListMultimap<String, ResourceItem> map = myFullTable.get(myNamespace, type);
      if (map == null) {
        return null;
      }
      List<ResourceItem> items = map.get(name);
      assert items != null;
      if (tag != null) {
        // Only PsiResourceItems can match.
        for (ResourceItem resourceItem : items) {
          if (resourceItem instanceof PsiResourceItem) {
            PsiResourceItem psiResourceItem = (PsiResourceItem)resourceItem;
            if (psiResourceItem.wasTag(tag)) {
              return resourceItem;
            }
          }
        }
      }
      else {
        // Check all items for the right source file.
        for (ResourceItem item : items) {
          if (item instanceof PsiResourceItem) {
            if (Objects.equals(((PsiResourceItem)item).getPsiFile(), file)) {
              return item;
            }
          }
          else {
            ResourceFile resourceFile = ((ResourceMergerItem)item).getSourceFile();
            if (resourceFile != null && FileUtil.filesEqual(resourceFile.getFile(), ioFile)) {
              return item;
            }
          }
        }
      }
    }

    return null;
  }

  // For debugging only
  @Override
  public String toString() {
    return getClass().getSimpleName() + " for " + myResourceDir + ": @" + Integer.toHexString(System.identityHashCode(this));
  }

  @Override
  @NotNull
  protected Set<VirtualFile> computeResourceDirs() {
    return Collections.singleton(myResourceDir);
  }

  @Override
  @NotNull
  public ResourceSourceFile deserializeResourceSourceFile(
      @NotNull Base128InputStream stream, @NotNull List<RepositoryConfiguration> configurations) throws IOException {
    return VfsResourceFile.deserialize(stream, configurations);
  }

  /**
   * Tracks state used by the initial scan, which may be used to save the state to a cache.
   * The file cache omits non-XML single-file items, since those are easily derived from the file path.
   */
  private static class Loader extends RepositoryLoader<ResourceFolderRepository> {
    @NotNull private final ResourceFolderRepository myRepository;
    @NotNull private final VirtualFile myResourceDir;
    @NotNull private final PsiManager myPsiManager;
    @Nullable private final ResourceFolderRepositoryCachingData myCachingData;
    @NotNull private final Map<ResourceType, ListMultimap<String, ResourceItem>> myResources = new EnumMap<>(ResourceType.class);
    @NotNull private final Map<VirtualFile, ResourceItemSource<BasicResourceItem>> mySources = new HashMap<>();
    @NotNull private final Map<VirtualFile, BasicFileResourceItem> myFileResources = new HashMap<>();
    // The following two fields are used as a cache of size one for quick conversion from a PathString to a VirtualFile.
    @Nullable private VirtualFile myLastVirtualFile;
    @Nullable private PathString myLastPathString;

    @NotNull Set<VirtualFile> myFilesToReparseAsPsi = new HashSet<>();

    Loader(@NotNull ResourceFolderRepository repository, @Nullable ResourceFolderRepositoryCachingData cachingData) {
      super(VfsUtilCore.virtualToIoFile(repository.myResourceDir).toPath(), null, repository.getNamespace());
      myRepository = repository;
      myResourceDir = repository.myResourceDir;
      myPsiManager = repository.myPsiManager;
      myCachingData = cachingData;
      // TODO: Support visibility without relying on ResourceVisibilityLookup.
      myDefaultVisibility = ResourceVisibility.UNDEFINED;
    }

    public void load() {
      if (!myResourceDir.isValid()) {
        return;
      }

      loadFromPersistentCache();

      ApplicationManager.getApplication().runReadAction(this::getPsiDirsForListener);

      scanResFolder();

      populateRepository();

      ApplicationManager.getApplication().runReadAction(() -> scanQueuedPsiResources());

      if (myCachingData != null && !myRepository.hasFreshFileCache()) {
        Executor executor = myCachingData.getCacheCreationExecutor();
        if (executor != null) {
          executor.execute(this::createCacheFile);
        }
      }
    }

    private void loadFromPersistentCache() {
      if (myCachingData == null) {
        return;
      }

      byte[] fileHeader = getCacheFileHeader(myCachingData);
      try (Base128InputStream stream = new Base128InputStream(myCachingData.getCacheFile())) {
        if (!ResourceSerializationUtil.validateContents(fileHeader, stream)) {
          return; // Cache file header doesn't match.
        }
        ResourceSerializationUtil.readResourcesFromStream(stream, Maps.newHashMapWithExpectedSize(1000), null, myRepository,
                                                          item -> addResourceItem(item, myRepository));
      }
      catch (NoSuchFileException ignored) {
        // Cache file does not exist.
      }
      catch (Throwable e) {
        // Remove incomplete data.
        mySources.clear();
        myFileResources.clear();

        LOG.warn("Failed to load resources from cache file " + myCachingData.getCacheFile().toString(), e);
      }
    }

    @NotNull
    protected byte[] getCacheFileHeader(@NotNull ResourceFolderRepositoryCachingData cachingData) {
      return ResourceSerializationUtil.getCacheFileHeader(stream -> {
        stream.write(CACHE_FILE_HEADER);
        stream.writeString(CACHE_FILE_FORMAT_VERSION);
        stream.writeString(myResourceDir.getPath());
        stream.writeString(cachingData.getCodeVersion());
      });
    }

    private void createCacheFile() {
      assert myCachingData != null;
      byte[] header = getCacheFileHeader(myCachingData);
      try {
        createPersistentCache(myCachingData.getCacheFile(), header, stream -> writeResourcesToStream(myResources, stream, config -> true));
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    private void scanResFolder() {
      try {
        for (VirtualFile subDir : myResourceDir.getChildren()) {
          if (subDir.isValid() && subDir.isDirectory()) {
            String folderName = subDir.getName();
            FolderInfo folderInfo = FolderInfo.create(folderName, myFolderConfigCache);
            if (folderInfo != null) {
              RepositoryConfiguration configuration = getConfiguration(myRepository, folderInfo.configuration);
              for (VirtualFile file : subDir.getChildren()) {
                if (folderInfo.folderType == VALUES ? mySources.containsKey(file) : myFileResources.containsKey(file)) {
                  if (isParsableFile(file, folderInfo)) {
                    countCacheHit();
                  }
                  continue;
                }

                if (myRepository.myViewBindingEnabled && folderInfo.resourceType == ResourceType.LAYOUT) {
                  // Layout XML files are queued to be scanned separately in scanQueuedPsiResources.
                  myFilesToReparseAsPsi.add(file);
                  continue;
                }

                PathString pathString = FileExtensions.toPathString(file);
                myLastVirtualFile = file;
                myLastPathString = pathString;
                try {
                  loadResourceFile(pathString, folderInfo, configuration);
                  if (isParsableFile(file, folderInfo)) {
                    countCacheMiss();
                  }
                }
                catch (ParsingException e) {
                  // Reparse the file as PSI. The PSI parser is more forgiving than KXmlParser because
                  // it is designed to work with potentially malformed files in the middle of editing.
                  myFilesToReparseAsPsi.add(file);
                }
              }
            }
          }
        }
      }
      catch (Exception e) {
        LOG.error("Failed to load resources from " + myResourceDirectoryOrFile.toString(), e);
      }

      super.finishLoading(myRepository);

      // Associate file resources with sources.
      for (Map.Entry<VirtualFile, BasicFileResourceItem> entry : myFileResources.entrySet()) {
        VirtualFile virtualFile = entry.getKey();
        BasicFileResourceItem item = entry.getValue();
        ResourceItemSource<BasicResourceItem> source =
            mySources.computeIfAbsent(virtualFile, file -> new VfsResourceFile(file, item.getRepositoryConfiguration()));
        source.addItem(item);
      }

      // Populate the myResources map.
      List<ResourceItemSource<BasicResourceItem>> sortedSources = new ArrayList<>(mySources.values());
      // Sort sources according to folder configurations to have deterministic ordering of resource items in myResources.
      sortedSources.sort(SOURCE_COMPARATOR);
      for (ResourceItemSource<BasicResourceItem> source : sortedSources) {
        for (ResourceItem item : source) {
          getOrCreateMap(item.getType()).put(item.getName(), item);
        }
      }
    }

    private void loadResourceFile(
        @NotNull PathString file, @NotNull FolderInfo folderInfo, @NotNull RepositoryConfiguration configuration) {
      if (folderInfo.resourceType == null) {
        if (isXmlFile(file)) {
          parseValueResourceFile(file, configuration);
        }
      }
      else {
        if (isXmlFile(file)) {
          if (folderInfo.folderType == LAYOUT) {
            parseLayoutFile(file, configuration);
          }
          else if (folderInfo.isIdGenerating) {
            parseIdGeneratingResourceFile(file, configuration);
          }
        }

        BasicFileResourceItem item = createFileResourceItem(file, folderInfo.resourceType, configuration);
        addResourceItem(item, (ResourceFolderRepository)item.getRepository());
      }
    }

    private void parseLayoutFile(@NotNull PathString file, @NotNull RepositoryConfiguration configuration) {
      try (InputStream stream = getInputStream(file)) {
        ResourceSourceFile sourceFile = createResourceSourceFile(file, configuration);
        XmlPullParser parser = new KXmlParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        parser.setInput(stream, null);

        int event;
        do {
          event = parser.nextToken();
          if (event == XmlPullParser.START_TAG) {
            if (parser.getDepth() == 1 && parser.getPrefix() == null && parser.getName().equals(TAG_LAYOUT)) {
              // TODO(b/136500593): Handle databinding information without resorting to PSI parsing.
              throw new DataBindingInfoEncounteredException();
            }

            int numAttributes = parser.getAttributeCount();
            for (int i = 0; i < numAttributes; i++) {
              String idValue = parser.getAttributeValue(i);
              if (idValue.startsWith(NEW_ID_PREFIX) && idValue.length() > NEW_ID_PREFIX.length()) {
                String resourceName = idValue.substring(NEW_ID_PREFIX.length());
                addIdResourceItem(resourceName, sourceFile);
              }
            }
          }
        } while (event != XmlPullParser.END_DOCUMENT);
      }
      // KXmlParser throws RuntimeException for an undefined prefix and an illegal attribute name.
      catch (IOException | XmlPullParserException | RuntimeException e) {
        handleParsingError(file, e);
      }

      addValueFileResources();
    }

    private static boolean isParsableFile(@NotNull VirtualFile file, @NotNull FolderInfo folderInfo) {
      return (folderInfo.folderType == VALUES || folderInfo.isIdGenerating) && isXmlFile(file.getName());
    }

    private void populateRepository() {
      myRepository.mySources.putAll(mySources);
      myRepository.commitToRepositoryWithoutLock(myResources);
    }

    @NotNull
    private ListMultimap<String, ResourceItem> getOrCreateMap(@NotNull ResourceType resourceType) {
      return myResources.computeIfAbsent(resourceType, type -> LinkedListMultimap.create());
    }

    @Override
    @NotNull
    protected InputStream getInputStream(@NotNull PathString file) throws IOException {
      VirtualFile virtualFile = getVirtualFile(file);
      if (virtualFile == null) {
        throw new NoSuchFileException(file.getNativePath());
      }
      return virtualFile.getInputStream();
    }

    @Nullable
    private VirtualFile getVirtualFile(@NotNull PathString file) {
      return file.equals(myLastPathString) ? myLastVirtualFile : FileExtensions.toVirtualFile(file);
    }

    /**
     * Currently, {@link com.intellij.psi.impl.file.impl.PsiVFSListener} requires that at least the parent directory of each file has been
     * accessed as PSI before bothering to notify any listener of events. So, make a quick pass to grab the necessary PsiDirectories.
     */
    private void getPsiDirsForListener() {
      PsiDirectory resourceDirPsi = myPsiManager.findDirectory(myResourceDir);
      if (resourceDirPsi != null) {
        resourceDirPsi.getSubdirectories();
      }
    }

    @Override
    protected void addResourceItem(@NotNull BasicResourceItem item, @NotNull ResourceFolderRepository repository) {
      if (item instanceof BasicValueResourceItemBase) {
        VfsResourceFile sourceFile = (VfsResourceFile)((BasicValueResourceItemBase)item).getSourceFile();
        if (sourceFile.isValid()) {
          sourceFile.addItem(item);
          mySources.put(sourceFile.getVirtualFile(), sourceFile);
        }
      }
      else if (item instanceof BasicFileResourceItem) {
        BasicFileResourceItem fileResourceItem = (BasicFileResourceItem)item;
        VirtualFile virtualFile = getVirtualFile(fileResourceItem.getSource());
        if (virtualFile != null) {
          myFileResources.put(virtualFile, fileResourceItem);
        }
      }
      else {
        throw new IllegalArgumentException("Unexpected type: " + item.getClass().getName());
      }
    }

    @Override
    @NotNull
    protected ResourceSourceFile createResourceSourceFile(@NotNull PathString file, @NotNull RepositoryConfiguration configuration) {
      VirtualFile virtualFile = getVirtualFile(file);
      return new VfsResourceFile(virtualFile, configuration);
    }

    @Override
    protected void handleParsingError(@NotNull PathString file, @NotNull Exception e) {
      throw new ParsingException(e);
    }

    /**
     * For resource files that failed when scanning with a VirtualFile, retry with PsiFile.
     */
    private void scanQueuedPsiResources() {
      for (VirtualFile file : myFilesToReparseAsPsi) {
        myRepository.scan(file);
      }
    }

    private void countCacheHit() {
      ++myRepository.myNumXmlFilesLoadedInitially;
    }

    private void countCacheMiss() {
      ++myRepository.myNumXmlFilesLoadedInitially;
      ++myRepository.myNumXmlFilesLoadedInitiallyFromSources;
    }
  }

  private static class ParsingException extends RuntimeException {
    ParsingException(Throwable cause) {
      super(cause);
    }

    protected ParsingException() {
    }
  }

  private static class DataBindingInfoEncounteredException extends ParsingException {
  }
}
