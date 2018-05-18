/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.annotations.concurrency.GuardedBy;
import com.android.builder.model.AaptOptions;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.Library;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.repository.GradleVersion;
import com.android.ide.common.repository.ResourceVisibilityLookup;
import com.android.ide.common.resources.AbstractResourceRepository;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.res.aar.AarResourceRepositoryCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.android.dom.manifest.AndroidManifestUtils;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static org.jetbrains.android.facet.ResourceFolderManager.addAarsFromModuleLibraries;

public class ResourceRepositoryManager implements Disposable {
  private static final Key<ResourceRepositoryManager> KEY = Key.create(ResourceRepositoryManager.class.getName());
  private static final Logger LOG = Logger.getInstance(ResourceRepositoryManager.class);

  private static final Object APP_RESOURCES_LOCK = new Object();
  private static final Object PROJECT_RESOURCES_LOCK = new Object();
  private static final Object MODULE_RESOURCES_LOCK = new Object();

  @NotNull private final AndroidFacet myFacet;
  @Nullable private ResourceVisibilityLookup.Provider myResourceVisibilityProvider;

  /**
   * If the module is namespaced, this is the shared {@link ResourceNamespace} instance corresponding to the package name from the manifest.
   */
  @Nullable private ResourceNamespace myCachedNamespace;

  @GuardedBy("APP_RESOURCES_LOCK")
  private AppResourceRepository myAppResources;

  @GuardedBy("PROJECT_RESOURCES_LOCK")
  private ProjectResourceRepository myProjectResources;

  @GuardedBy("MODULE_RESOURCES_LOCK")
  private LocalResourceRepository myModuleResources;

  @NotNull
  public static ResourceRepositoryManager getOrCreateInstance(@NotNull AndroidFacet facet) {
    ResourceRepositoryManager instance = facet.getUserData(KEY);
    if (instance == null) {
      instance = facet.putUserDataIfAbsent(KEY, new ResourceRepositoryManager(facet));
    }
    return instance;
  }

  @Nullable
  public static ResourceRepositoryManager getOrCreateInstance(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    return facet == null ? null : getOrCreateInstance(facet);
  }

  @Nullable
  public static ResourceRepositoryManager getInstance(@NotNull PsiElement element) {
    Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (module == null) {
      return null;
    }

    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return null;
    }

    return getOrCreateInstance(facet);
  }

  /**
   * Computes and returns the app resources.
   *
   * @see #getAppResources(boolean)
   * @return the resource repository or null if the module is not an Android module
   */
  @Nullable
  public static LocalResourceRepository getAppResources(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    return facet != null ? getAppResources(facet) : null;
  }

  /**
   * Computes and returns the app resources.
   *
   * @see #getAppResources(boolean)
   */
  @NotNull
  public static LocalResourceRepository getAppResources(@NotNull AndroidFacet facet) {
    return getOrCreateInstance(facet).getAppResources(true);
  }

  /**
   * Computes and returns the project resources.
   *
   * @see #getProjectResources(boolean)
   * @return the resource repository or null if the module is not an Android module
   */
  @Nullable
  public static LocalResourceRepository getProjectResources(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    return facet != null ? getProjectResources(facet) : null;
  }

  /**
   * Computes and returns the project resources.
   *
   * @see #getProjectResources(boolean)
   */
  @NotNull
  public static LocalResourceRepository getProjectResources(@NotNull AndroidFacet facet) {
    return getOrCreateInstance(facet).getProjectResources(true);
  }

  /**
   * Computes and returns the module resources.
   *
   * @see #getModuleResources(boolean)
   * @return the resource repository or null if the module is not an Android module
   */
  @Nullable
  public static LocalResourceRepository getModuleResources(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    return facet != null ? getModuleResources(facet) : null;
  }

  /**
   * Computes and returns the module resources.
   *
   * @see #getModuleResources(boolean)
   */
  @NotNull
  public static LocalResourceRepository getModuleResources(@NotNull AndroidFacet facet) {
    return getOrCreateInstance(facet).getModuleResources(true);
  }

  private ResourceRepositoryManager(@NotNull AndroidFacet facet) {
    myFacet = facet;
    Disposer.register(facet, this);
  }

  /**
   * Returns true if this project is build with Gradle but the AndroidModuleModel did not exist when the resources were cached.
   * And reset the state.
   */
  public static boolean testAndClearTempResourceCached(@NotNull Project project) {
    if (project.getUserData(AppResourceRepository.TEMPORARY_RESOURCE_CACHE) != Boolean.TRUE) {
      return false;
    }
    project.putUserData(AppResourceRepository.TEMPORARY_RESOURCE_CACHE, null);
    return true;
  }

  @NotNull
  public static Collection<Library> findAarLibraries(@NotNull AndroidFacet facet) {
    List<Library> libraries = new ArrayList<>();
    if (facet.requiresAndroidModel()) {
      AndroidModuleModel androidModel = AndroidModuleModel.get(facet);
      if (androidModel != null) {
        List<AndroidFacet> dependentFacets = AndroidUtils.getAllAndroidDependencies(facet.getModule(), true);
        addGradleLibraries(libraries, androidModel);
        for (AndroidFacet dependentFacet : dependentFacets) {
          AndroidModuleModel dependentGradleModel = AndroidModuleModel.get(dependentFacet);
          if (dependentGradleModel != null) {
            addGradleLibraries(libraries, dependentGradleModel);
          }
        }
      }
    }
    return libraries;
  }

  private static void addGradleLibraries(@NotNull List<Library> list, @NotNull AndroidModuleModel androidModuleModel) {
    list.addAll(androidModuleModel.getSelectedMainCompileLevel2Dependencies().getAndroidLibraries());
  }

  @NotNull
  static Map<File, String> findAarLibraries(@NotNull AndroidFacet facet, @NotNull List<AndroidFacet> dependentFacets) {
    // Use the gradle model if available, but if not, fall back to using plain IntelliJ library dependencies
    // which have been persisted since the most recent sync
    AndroidModuleModel androidModuleModel = AndroidModuleModel.get(facet);
    if (androidModuleModel != null) {
      List<Library> libraries = new ArrayList<>();
      addGradleLibraries(libraries, androidModuleModel);
      for (AndroidFacet dependentFacet : dependentFacets) {
        AndroidModuleModel dependentGradleModel = AndroidModuleModel.get(dependentFacet);
        if (dependentGradleModel != null) {
          addGradleLibraries(libraries, dependentGradleModel);
        }
      }
      GradleVersion modelVersion = androidModuleModel.getModelVersion();
      assert modelVersion != null;
      return findAarLibrariesFromGradle(dependentFacets, libraries);
    }
    Project project = facet.getModule().getProject();
    if (GradleProjectInfo.getInstance(project).isBuildWithGradle()) {
      project.putUserData(AppResourceRepository.TEMPORARY_RESOURCE_CACHE, true);
    }
    return findAarLibrariesFromIntelliJ(facet, dependentFacets);
  }

  /**
   * Reads IntelliJ library definitions ({@link com.intellij.openapi.roots.LibraryOrSdkOrderEntry}) and if possible, finds a corresponding
   * {@code .aar} resource library to include. This works before the Gradle project has been initialized.
   */
  @NotNull
  private static Map<File, String> findAarLibrariesFromIntelliJ(@NotNull AndroidFacet facet, @NotNull List<AndroidFacet> dependentFacets) {
    // Find .aar libraries from old IntelliJ library definitions
    Map<File, String> dirs = new HashMap<>();
    addAarsFromModuleLibraries(facet, dirs);
    for (AndroidFacet f : dependentFacets) {
      addAarsFromModuleLibraries(f, dirs);
    }
    return dirs;
  }

  /**
   * Looks up the library dependencies from the Gradle tools model and returns the corresponding {@code .aar}
   * resource directories.
   */
  @NotNull
  private static Map<File, String> findAarLibrariesFromGradle(@NotNull List<AndroidFacet> dependentFacets,
                                                              @NotNull List<Library> libraries) {
    // Pull out the unique directories, in case multiple modules point to the same .aar folder.
    Map<File, String> files = new HashMap<>(libraries.size());

    Set<String> moduleNames = new HashSet<>();
    for (AndroidFacet f : dependentFacets) {
      moduleNames.add(f.getModule().getName());
    }
    try {
      for (Library library : libraries) {
        // We should only add .aar dependencies if they aren't already provided as modules.
        // For now, the way we associate them with each other is via the library name.
        // In the future the model will provide this for us.
        String libraryName = library.getArtifactAddress();
        // Strip the build system prefix and the "@aar" suffix, if present (b/79942260).
        // For example, both, "Gradle: com.android.support:appcompat-v7-27.1.0" and
        // "com.android.support:appcompat-v7:27.1.0@aar", become "com.android.support:appcompat-v7:27.1.0".
        int prefixEnd = libraryName.lastIndexOf(' ') + 1;
        int suffixStart = libraryName.endsWith("@aar") ? libraryName.length() - "@aar".length() : libraryName.length();
        libraryName = libraryName.substring(prefixEnd, suffixStart);
        if (!moduleNames.contains(libraryName)) {
          File resFolder = new File(library.getResFolder());
          if (resFolder.exists()) {
            files.put(resFolder, libraryName);
            // Don't add it again!
            moduleNames.add(libraryName);
          }
        }
      }
    }
    catch (UnsupportedOperationException e) {
      // This happens when there is an incompatibility between the builder-model interfaces embedded in Android Studio and the cached model.
      // If we got here, it is because this code got invoked before project sync happened (e.g. when reopening a project with open editors).
      // Project sync now is smart enough to handle this case and will trigger a full sync.
      LOG.warn("Incompatibility found between the IDE's builder-model and the cached Gradle model", e);
    }
    return files;
  }

  /**
   * Returns the repository with all non-framework resources available to a given module (in the current variant). This includes not just
   * the resources defined in this module, but in any other modules that this module depends on, as well as any libraries those modules may
   * depend on (such as appcompat). This repository also contains sample data resources associated with the {@link ResourceNamespace#TOOLS}
   * namespace.
   *
   * <p>When a layout is rendered in the layout, it is fetching resources from the app resource repository: it should see all the resources
   * just like the app does.
   *
   * @return the computed repository or null of {@code createIfNecessary} is false and no other action caused the creation of the repository.
   */
  @Contract("true -> !null")
  @Nullable
  public LocalResourceRepository getAppResources(boolean createIfNecessary) {
    return ApplicationManager.getApplication().runReadAction((Computable<AppResourceRepository>)() -> {
      synchronized (APP_RESOURCES_LOCK) {
        if (myAppResources == null && createIfNecessary) {
          myAppResources = AppResourceRepository.create(myFacet);
          Disposer.register(this, myAppResources);
        }
        return myAppResources;
      }
    });
  }

  /**
   * Returns the resource repository for a module along with all its (local) module dependencies.
   *
   * <p>It doesn't contain resources from AAR dependencies.
   *
   * <p>An example of where this is useful is the layout editor; in its “Language” menu it lists all the relevant languages in the project
   * and lets you choose between them. Here we don’t want to include resources from libraries; If you depend on Google Play Services, and it
   * provides 40 translations for its UI, we don’t want to show all 40 languages in the language menu, only the languages actually locally
   * in the user’s source code.
   *
   * @return the computed repository or null of {@code createIfNecessary} is false and no other action caused the creation of the repository
   */
  @Contract("true -> !null")
  @Nullable
  public LocalResourceRepository getProjectResources(boolean createIfNecessary) {
    return ApplicationManager.getApplication().runReadAction((Computable<ProjectResourceRepository>)() -> {
      synchronized (PROJECT_RESOURCES_LOCK) {
        if (myProjectResources == null && createIfNecessary) {
          myProjectResources = ProjectResourceRepository.create(myFacet);
          Disposer.register(this, myProjectResources);
        }
        return myProjectResources;
      }
    });
  }

  /**
   * Returns the resource repository for a single module (which can possibly have multiple resource folders). Does not include resources
   * from any dependencies.
   *
   * @return the computed repository or null of {@code createIfNecessary} is false and no other action caused the creation of the repository
   */
  @Contract("true -> !null")
  @Nullable
  public LocalResourceRepository getModuleResources(boolean createIfNecessary) {
    return ApplicationManager.getApplication().runReadAction((Computable<LocalResourceRepository>)() -> {
      synchronized (MODULE_RESOURCES_LOCK) {
        if (myModuleResources == null && createIfNecessary) {
          myModuleResources = ModuleResourceRepository.create(myFacet);
          Disposer.register(this, myModuleResources);
        }
        return myModuleResources;
      }
    });
  }

  /**
   * Returns the resource repository with Android framework resources, for the module's compile SDK.
   *
   * @param needLocales if the return repository should contain resources defined using a locale qualifier (e.g. all translation strings).
   *                    This makes creating the repository noticeably slower.
   * @return the framework repository or null if the SDK resources directory cannot be determined for the module.
   */
  @Nullable
  public AbstractResourceRepository getFrameworkResources(boolean needLocales) {
    AndroidPlatform androidPlatform = AndroidPlatform.getInstance(myFacet.getModule());
    if (androidPlatform == null) {
      return null;
    }

    return androidPlatform.getSdkData().getTargetData(androidPlatform.getTarget()).getFrameworkResources(needLocales);
  }

  /**
   * If namespacing is disabled, the namespace parameter is ignored and the method returns a list containing the single resource repository
   * returned by {@link #getAppResources(boolean)}. Otherwise the method returns a list of module, library, or sample data resource
   * repositories for the given namespace. In a well formed Android project the returned list will contain at most one resource repository.
   * Multiple repositories may be returned only when there is a package name collision between modules or libraries.
   *
   * @param namespace the namespace to return resource repositories for
   * @return the repositories for the given namespace
   */
  @NotNull
  public List<LocalResourceRepository> getAppResourcesForNamespace(@NotNull ResourceNamespace namespace) {
    AppResourceRepository appRepository = (AppResourceRepository)getAppResources(true);
    if (getNamespacing() == AaptOptions.Namespacing.DISABLED) {
      return ImmutableList.of(appRepository);
    }
    return appRepository.getRepositoriesForNamespace(namespace);
  }

  public void resetResources() {
    resetVisibility();

    synchronized (MODULE_RESOURCES_LOCK) {
      if (myModuleResources != null) {
        Disposer.dispose(myModuleResources);
        myModuleResources = null;
      }
    }

    synchronized (PROJECT_RESOURCES_LOCK) {
      if (myProjectResources != null) {
        Disposer.dispose(myProjectResources);
        myProjectResources = null;
      }
    }

    synchronized (APP_RESOURCES_LOCK) {
      if (myAppResources != null) {
        Disposer.dispose(myAppResources);
        myAppResources = null;
      }
    }
  }

  @Override
  public void dispose() {
    // There's nothing to dispose in this object, but the actual resource repositories may need to do clean-up and they are children
    // of this object in the Disposer hierarchy.
  }

  public void resetAllCaches() {
    resetResources();
    ConfigurationManager.getOrCreateInstance(myFacet.getModule()).getResolverCache().reset();
    ResourceFolderRegistry.reset();
    AarResourceRepositoryCache.getInstance().clear();
  }

  public void resetVisibility() {
    myResourceVisibilityProvider = null;
  }

  @NotNull
  public AaptOptions.Namespacing getNamespacing() {
    AndroidModel model = myFacet.getConfiguration().getModel();
    if (model != null) {
      return model.getNamespacing();
    } else {
      return AaptOptions.Namespacing.DISABLED;
    }
  }

  /**
   * Returns the {@link ResourceNamespace} used by the current module.
   *
   * <p>This is read from the manifest, so needs to be run inside a read action.
   */
  @NotNull
  public ResourceNamespace getNamespace() {
    if (getNamespacing() == AaptOptions.Namespacing.DISABLED) {
      return ResourceNamespace.RES_AUTO;
    }

    String packageName = AndroidManifestUtils.getPackageName(myFacet);
    if (packageName == null) {
      return ResourceNamespace.RES_AUTO;
    }

    if (myCachedNamespace == null || !packageName.equals(myCachedNamespace.getPackageName())) {
      myCachedNamespace = ResourceNamespace.fromPackageName(packageName);
    }

    return myCachedNamespace;
  }

  @Nullable
  public ResourceVisibilityLookup.Provider getResourceVisibilityProvider() {
    if (myResourceVisibilityProvider == null) {
      if (!myFacet.requiresAndroidModel() || myFacet.getConfiguration().getModel() == null) {
        return null;
      }
      myResourceVisibilityProvider = new ResourceVisibilityLookup.Provider();
    }

    return myResourceVisibilityProvider;
  }

  @NotNull
  public ResourceVisibilityLookup getResourceVisibility() {
    AndroidModuleModel androidModel = AndroidModuleModel.get(myFacet);
    if (androidModel != null) {
      ResourceVisibilityLookup.Provider provider = getResourceVisibilityProvider();
      if (provider != null) {
        AndroidProject androidProject = androidModel.getAndroidProject();
        Variant variant = androidModel.getSelectedVariant();
        return provider.get(androidProject, variant);
      }
    }

    return ResourceVisibilityLookup.NONE;
  }

  @NotNull
  public Multimap<String, VirtualFile> getAllResourceDirs() {
    // TODO(b/76128326): manage the set of directories here.
    synchronized (APP_RESOURCES_LOCK) {
      getAppResources(true);
      return myAppResources.getAllResourceDirs();
    }
  }


  @Nullable
  public LocalResourceRepository findRepositoryFor(@NotNull File aarDirectory) {
    // TODO(b/76128326): manage the libraries here.
    synchronized (APP_RESOURCES_LOCK) {
      getAppResources(true);
      return myAppResources.findRepositoryFor(aarDirectory);
    }
  }

  /**
   * Returns the libraries among the app resources, if any.
   */
  @NotNull
  public List<? extends LocalResourceRepository> getLibraries() {
    // TODO(b/76128326): manage the libraries here.
    synchronized (APP_RESOURCES_LOCK) {
      getAppResources(true);
      return myAppResources.getLibraries();
    }
  }
}
