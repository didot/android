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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.tools.lint.detector.api.Lint.stripIdPrefix;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.annotations.concurrency.GuardedBy;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.AbstractResourceRepository;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceTable;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.aar.AarResourceRepository;
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Repository for Android application resources, e.g. those that show up in {@code R}, not {@code android.R}
 * (which are referred to as framework resources.). Note that this includes resources from Gradle libraries
 * too, even though you may not think of these as "local" (they do however (a) end up in the application
 * namespace, and (b) get extracted by Gradle into the project's build folder where they are merged with
 * the other resources.)
 * <p>
 * For a given Android module, you can obtain either the resources for the module itself, or for a module and all
 * its libraries. Most clients should use the module with all its dependencies included; when a user is
 * using code completion for example, they expect to be offered not just the drawables in this module, but
 * all the drawables available in this module which includes the libraries.
 * </p>
 * <p>
 * The module repository is implemented using several layers. Consider a Gradle project where the main module has
 * two flavors, and depends on a library module. In this case, the {@linkplain LocalResourceRepository} for
 * the module with dependencies will contain these components:
 * <ul>
 *   <li> A {@link AppResourceRepository} which contains a
 *          {@link AarResourceRepository} wrapping each AAR library dependency,
 *          and merges this with the project resource repository </li>
 *   <li> A {@link ProjectResourceRepository} representing the collection of module repositories</li>
 *   <li> For each module (e.g. the main module and library module}, a {@link ModuleResourceRepository}</li>
 *   <li> For each resource directory in each module, a {@link ResourceFolderRepository}</li>
 * </ul>
 * These different repositories are merged together by the {@link MultiResourceRepository} class,
 * which represents a repository that just combines the resources from each of its children.
 * All of {@linkplain AppResourceRepository}, {@linkplain ModuleResourceRepository} and
 * {@linkplain ProjectResourceRepository} are instances of a {@linkplain MultiResourceRepository}.
 * </p>
 * <p>
 * The {@link ResourceFolderRepository} is the lowest level of repository. It is associated with just
 * a single resource folder. Therefore, it does not have to worry about trying to mask resources between
 * different flavors; that task is done by the {@link ModuleResourceRepository} which combines
 * {@linkplain ResourceFolderRepository} instances. Instead, the {@linkplain ResourceFolderRepository} just
 * needs to compute the resource items for the resource folders, including qualifier variations.
 * </p>
 * <p>
 * The resource repository automatically stays up to date. You can call {@linkplain #getModificationCount()}
 * to see whether anything has changed since your last data fetch. This is for example how the resource
 * string folding in the source editors work; they fetch the current values of the resource strings, and
 * store those along with the current project resource modification count into the folding data structures.
 * When the editor wants to see if the folding sections are up to date, those are compared with the current
 * {@linkplain #getModificationCount()} version, and only if they differ is the folding structure updated.
 * </p>
 * <p>
 * Only the {@linkplain ResourceFolderRepository} needs to listen for user edits and file changes. It
 * uses {@linkplain PsiProjectListener}, a single listener which is shared by all repositories in the
 * same project, to get notified when something in one of its resource files changes, and it uses the
 * PSI change event to selectively update the repository data structures, if possible.
 * </p>
 * <p>
 * The {@linkplain ResourceFolderRepository} can also have a pointer to its parent. This is possible
 * since a resource folder can only be in a single module. The parent reference is used to quickly
 * invalidate the cache of the parent {@link MultiResourceRepository}. For example, let's say the
 * project has two flavors. When the PSI change event is used to update the name of a string resource,
 * the repository will also notify the parent that its {@link ResourceType#ID} map is out of date.
 * The {@linkplain MultiResourceRepository} will use this to null out its map cache of strings, and
 * on the next read, it will merge in the string maps from all its {@linkplain ResourceFolderRepository}
 * children.
 * </p>
 * <p>
 * One common type of "update" is changing the current variant in the IDE. With the above scheme,
 * this just means reordering the {@linkplain ResourceFolderRepository} instances in the
 * {@linkplain ModuleResourceRepository}; it does not have to rescan the resources as it did in the
 * previous implementation.
 * </p>
 * <p>
 * The {@linkplain ProjectResourceRepository} is similar, but it combines {@link ModuleResourceRepository}
 * instances rather than {@link ResourceFolderRepository} instances. Note also that the way these
 * resource repositories work is slightly different from the way the resource items are used by
 * the builder: The builder will bail if it encounters duplicate declarations unless they are in alternative
 * folders of the same flavor. For the resource repository we never want to bail on merging; the repository
 * is kept up to date and live as the user is editing, so it is normal for the repository to sometimes
 * reflect invalid user edits (in the same way a Java editor in an IDE sometimes is showing uncompilable
 * source code) and it needs to be able to handle this case and offer a state that is as close to possible
 * as the intended meaning. Error handling is done by another part of the IDE.
 * </p>
 * <p>
 * Finally, note that the resource repository is showing the current state of the resources for the
 * currently selected variant. Note however that the above approach also lets us query resources for
 * example for <b>all</b> flavors, not just the currently selected flavor. We can offer APIs to iterate
 * through all available {@link ResourceFolderRepository} instances, not just the set of instances for
 * the current module's current flavor. This will allow us to for example preview the string translations
 * for a given resource name not just for the current flavor but for all other flavors as well.
 * </p>
 * <p>
 * See also the {@code README.md} file in this package.
 * </p>
 */
@SuppressWarnings("InstanceGuardedByStatic") // TODO: The whole locking scheme for resource repositories needs to be reworked.
public abstract class LocalResourceRepository extends AbstractResourceRepository implements Disposable, ModificationTracker {
  protected static final Logger LOG = Logger.getInstance(LocalResourceRepository.class);

  protected static final AtomicLong ourModificationCounter = new AtomicLong();

  private final String myDisplayName;

  @GuardedBy("ITEM_MAP_LOCK")
  @Nullable private List<MultiResourceRepository> myParents;

  private volatile long myGeneration;

  private final Object RESOURCE_DIRS_LOCK = new Object();
  @Nullable private Set<VirtualFile> myResourceDirs;

  protected LocalResourceRepository(@NotNull String displayName) {
    super();
    myDisplayName = displayName;
    setModificationCount(ourModificationCounter.incrementAndGet());
  }

  @NotNull
  public String getDisplayName() {
    return myDisplayName;
  }

  @Nullable
  public String getLibraryName() {
    return null;
  }

  @Override
  public void dispose() {}

  public void addParent(@NotNull MultiResourceRepository parent) {
    synchronized (ITEM_MAP_LOCK) {
      if (myParents == null) {
        myParents = new ArrayList<>(2); // Don't expect many parents
      }
      myParents.add(parent);
    }
  }

  public void removeParent(@NotNull MultiResourceRepository parent) {
    synchronized (ITEM_MAP_LOCK) {
      if (myParents != null) {
        myParents.remove(parent);
      }
    }
  }

  public boolean hasParents() {
    synchronized (ITEM_MAP_LOCK) {
      return myParents != null && !myParents.isEmpty();
    }
  }

  protected void invalidateParentCaches() {
    synchronized (ITEM_MAP_LOCK) {
      if (myParents != null) {
        for (MultiResourceRepository parent : myParents) {
          parent.invalidateCache(this);
        }
      }
    }
  }

  protected void invalidateParentCaches(@NotNull ResourceNamespace namespace, @NotNull ResourceType... types) {
    synchronized (ITEM_MAP_LOCK) {
      if (myParents != null) {
        for (MultiResourceRepository parent : myParents) {
          parent.invalidateCache(this, namespace, types);
        }
      }
    }
  }

  // ---- Implements ModificationCount ----

  /**
   * Returns the current generation of the app resources. Any time the app resources are updated,
   * the generation increases. This can be used to force refreshing of layouts etc (which will cache
   * configured app resources) when the project resources have changed since last render.
   * <p>
   * Note that the generation is not a simple change count. If you change the contents of a layout drawable XML file,
   * that will not affect the {@link ResourceItem} and {@link ResourceValue} results returned from
   * this repository; we only store the presence of file based resources like layouts, menus, and drawables.
   * Therefore, only additions or removals of these files will cause a generation change.
   * <p>
   * Value resource files, such as string files, will cause generation changes when they are edited (unless
   * the change is determined to not be relevant to resource values, such as a change in an XML comment, etc.
   *
   * @return the generation id
   */
  @Override
  public long getModificationCount() {
    return myGeneration;
  }

  protected void setModificationCount(long count) {
    myGeneration = count;
  }

  @Nullable
  public DataBindingInfo getDataBindingInfoForLayout(String layoutName) {
    return null;
  }

  @Nullable
  public Map<String, DataBindingInfo> getDataBindingResourceFiles() {
    return null;
  }

  @VisibleForTesting
  public boolean isScanPending(@NotNull PsiFile psiFile) {
    return false;
  }

  /** Returns the {@link PsiFile} corresponding to the source of the given resource item, if possible */
  @Nullable
  public static PsiFile getItemPsiFile(@NotNull Project project, @NotNull ResourceItem item) {
    if (project.isDisposed()) {
      return null;
    }

    if (item instanceof PsiResourceItem) {
      PsiResourceItem psiResourceItem = (PsiResourceItem)item;
      return psiResourceItem.getPsiFile();
    }

    VirtualFile virtualFile = ResourceHelper.getSourceAsVirtualFile(item);
    if (virtualFile != null) {
      PsiManager psiManager = PsiManager.getInstance(project);
      return psiManager.findFile(virtualFile);
    }

    return null;
  }

  /**
   * Returns the {@link XmlTag} corresponding to the given resource item. This is only
   * defined for resource items in value files.
   */
  @Nullable
  public static XmlTag getItemTag(@NotNull Project project, @NotNull ResourceItem item) {
    if (item instanceof PsiResourceItem) {
      PsiResourceItem psiResourceItem = (PsiResourceItem)item;
      return psiResourceItem.getTag();
    }

    PsiFile psiFile = getItemPsiFile(project, item);
    if (psiFile instanceof XmlFile) {
      String resourceName = item.getName();
      XmlFile xmlFile = (XmlFile)psiFile;
      ApplicationManager.getApplication().assertReadAccessAllowed();
      XmlTag rootTag = xmlFile.getRootTag();
      if (rootTag != null && rootTag.isValid()) {
        XmlTag[] subTags = rootTag.getSubTags();
        for (XmlTag tag : subTags) {
          if (tag.isValid()
              && resourceName.equals(tag.getAttributeValue(ATTR_NAME))
              && item.getType() == AndroidResourceUtil.getResourceTypeForResourceTag(tag)) {
            return tag;
          }
        }
        // TODO: Support nested tags inside declare-styleable. See http://b/74194028.
      }

      // This method should only be called on value resource types.
      assert FolderTypeRelationship.getRelatedFolders(item.getType()).contains(ResourceFolderType.VALUES) : item.getType();
    }

    return null;
  }

  @Nullable
  public String getViewTag(@NotNull ResourceItem item) {
    if (item instanceof PsiResourceItem) {
      PsiResourceItem psiItem = (PsiResourceItem)item;
      XmlTag tag = psiItem.getTag();

      final String id = item.getName();

      if (tag != null && tag.isValid()
          // Make sure that the id attribute we're searching for is actually
          // defined for this tag, not just referenced from this tag.
          // For example, we could have
          //    <Button a:alignLeft="@+id/target" a:id="@+id/something ...>
          // and this should *not* return "Button" as the view tag for
          // @+id/target!
          && id.equals(stripIdPrefix(tag.getAttributeValue(ATTR_ID, ANDROID_URI)))) {
        return tag.getName();
      }


      PsiFile file = psiItem.getPsiFile();
      if (file instanceof XmlFile && file.isValid()) {
        XmlFile xmlFile = (XmlFile)file;
        XmlTag rootTag = xmlFile.getRootTag();
        if (rootTag != null && rootTag.isValid()) {
          return findViewTag(rootTag, id);
        }
      }
    }

    return null;
  }

  @Nullable
  private static String findViewTag(XmlTag tag, String target) {
    String id = tag.getAttributeValue(ATTR_ID, ANDROID_URI);
    if (id != null && id.endsWith(target) && target.equals(stripIdPrefix(id))) {
      return tag.getName();
    }

    for (XmlTag sub : tag.getSubTags()) {
      if (sub.isValid()) {
        String found = findViewTag(sub, target);
        if (found != null) {
          return found;
        }
      }
    }

    return null;
  }

  /**
   * Forces the repository to update itself synchronously, if necessary (in case there
   * are pending updates). This method must be called on the event dispatch thread!
   */
  public void sync() {
    ApplicationManager.getApplication().assertIsDispatchThread();
  }

  @NotNull
  public final Set<VirtualFile> getResourceDirs() {
    synchronized (RESOURCE_DIRS_LOCK) {
      if (myResourceDirs != null) {
        return myResourceDirs;
      }
      myResourceDirs = computeResourceDirs();
      return myResourceDirs;
    }
  }

  @NotNull protected abstract Set<VirtualFile> computeResourceDirs();

  public final void invalidateResourceDirs() {
    synchronized (RESOURCE_DIRS_LOCK) {
      myResourceDirs = null;
    }
    synchronized (ITEM_MAP_LOCK) {
      if (myParents != null) {
        for (LocalResourceRepository parent : myParents) {
          parent.invalidateResourceDirs();
        }
      }
    }
  }

  /** Package accessible version of {@link #getFullTable()}. */
  @NonNull
  ResourceTable getFullTablePackageAccessible() {
    return getFullTable();
  }

  public static final class EmptyRepository extends LocalResourceRepository implements SingleNamespaceResourceRepository {
    @NotNull private final ResourceNamespace myNamespace;

    public EmptyRepository(@NotNull ResourceNamespace namespace) {
      super("");
      myNamespace = namespace;
    }

    @Override
    @NotNull
    protected Set<VirtualFile> computeResourceDirs() {
      return Collections.emptySet();
    }

    @Override
    @NotNull
    protected ResourceTable getFullTable() {
      return new ResourceTable();
    }

    @Override
    @Nullable
    protected ListMultimap<String, ResourceItem> getMap(@NotNull ResourceNamespace namespace, @NotNull ResourceType type, boolean create) {
      if (create) {
        throw new UnsupportedOperationException();
      } else {
        return null;
      }
    }

    @Override
    @NotNull
    public ResourceNamespace getNamespace() {
      return myNamespace;
    }

    @Override
    @Nullable
    public String getPackageName() {
      return myNamespace.getPackageName();
    }
  }
}
