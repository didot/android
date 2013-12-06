/*
 * Copyright (C) 2013 The Android Open Source Project
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
package org.jetbrains.android.inspections.lint;

import com.android.annotations.NonNull;
import com.android.builder.model.*;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Project;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.compiler.AndroidDexCompiler;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

import java.io.File;
import java.util.*;

/**
 * An {@linkplain IntellijLintProject} represents a lint project, which typically corresponds to a {@link Module},
 * but can also correspond to a library "project" such as an {@link com.android.builder.model.AndroidLibrary}.
 */
class IntellijLintProject extends Project {
  /**
   * Whether we support running .class file checks. No class file checks are currently registered as inspections.
   * Since IntelliJ doesn't perform background compilation (e.g. only parsing, so there are no bytecode checks)
   * this might need some work before we enable it.
   */
  public static final boolean SUPPORT_CLASS_FILES = false;

  IntellijLintProject(@NonNull LintClient client,
                      @NonNull File dir,
                      @NonNull File referenceDir) {
    super(client, dir, referenceDir);
  }

  /** Creates a set of projects for the given IntelliJ modules */
  @NonNull
  public static List<Project> create(@NonNull IntellijLintClient client, @Nullable List<VirtualFile> files, @NonNull Module... modules) {
    List<Project> projects = Lists.newArrayList();

    Map<Project,Module> projectMap = Maps.newHashMap();
    Map<Module,Project> moduleMap = Maps.newHashMap();
    Map<AndroidLibrary,Project> libraryMap = Maps.newHashMap();
    if (files != null && !files.isEmpty()) {
      // Wrap list with a mutable list since we'll be removing the files as we see them
      files = Lists.newArrayList(files);
    }
    for (Module module : modules) {
      addProjects(client, module, files, moduleMap, libraryMap, projectMap, projects);
    }

    client.setModuleMap(projectMap);

    if (projects.size() > 1) {
      // Partition the projects up such that we only return projects that aren't
      // included by other projects (e.g. because they are library projects)
      Set<Project> roots = new HashSet<Project>(projects);
      for (Project project : projects) {
        roots.removeAll(project.getAllLibraries());
      }
      return Lists.newArrayList(roots);
    } else {
      return projects;
    }
  }

  /**
   * Recursively add lint projects for the given module, and any other module or library it depends on, and also
   * populate the reverse maps so we can quickly map from a lint project to a corresponding module/library (used
   * by the lint client
   */
  private static void addProjects(@NonNull LintClient client,
                                  @NonNull Module module,
                                  @Nullable List<VirtualFile> files,
                                  @NonNull Map<Module,Project> moduleMap,
                                  @NonNull Map<AndroidLibrary, Project> libraryMap,
                                  @NonNull Map<Project,Module> projectMap,
                                  @NonNull List<Project> projects) {
    if (moduleMap.containsKey(module)) {
      return;
    }

    AndroidFacet facet = AndroidFacet.getInstance(module);
    LintModuleProject project = facet != null ? createModuleProject(client, facet) : null;

    if (project == null) {
      // It's possible for the module to *depend* on Android code, e.g. in a Gradle
      // project there will be a top-level non-Android module
      List<AndroidFacet> dependentFacets = AndroidUtils.getAllAndroidDependencies(module, false);
      for (AndroidFacet dependentFacet : dependentFacets) {
        addProjects(client, dependentFacet.getModule(), files, moduleMap, libraryMap, projectMap, projects);
      }
      return;
    }

    projects.add(project);
    moduleMap.put(module, project);
    projectMap.put(project, module);

    if (processFileFilter(module, files, project)) {
      // No need to process dependencies when doing single file analysis
      return;
    }

    List<Project> dependencies = Lists.newArrayList();
    List<AndroidFacet> dependentFacets = AndroidUtils.getAllAndroidDependencies(module, true);
    for (AndroidFacet dependentFacet : dependentFacets) {
      Project p = moduleMap.get(dependentFacet.getModule());
      if (p != null) {
        dependencies.add(p);
      } else {
        addProjects(client, dependentFacet.getModule(), files, moduleMap, libraryMap, projectMap, dependencies);
      }
    }

    if (facet.isGradleProject()) {
      addGradleLibraryProjects(client, files, libraryMap, projects, facet, project, projectMap, dependencies);
    }

    project.setDirectLibraries(dependencies);
  }

  /**
   * Checks whether we have a file filter (e.g. a set of specific files to check in the module rather than all files,
   * and if so, and if all the files have been found, returns true)
   */
  private static boolean processFileFilter(@NonNull Module module, @Nullable List<VirtualFile> files, @NonNull LintModuleProject project) {
    if (files != null && !files.isEmpty()) {
      ListIterator<VirtualFile> iterator = files.listIterator();
      while (iterator.hasNext()) {
        VirtualFile file = iterator.next();
        if (module.getModuleContentScope().accept(file)) {
          project.addFile(VfsUtilCore.virtualToIoFile(file));
          iterator.remove();
        }
      }
      if (files.isEmpty()) {
        // We're only scanning a subset of files (typically the current file in the editor);
        // in that case, don't initialize all the libraries etc
        project.setDirectLibraries(Collections.<Project>emptyList());
        return true;
      }
    }
    return false;
  }

  /** Creates a new module project */
  @Nullable
  private static LintModuleProject createModuleProject(@NonNull LintClient client, @NonNull AndroidFacet facet) {
    final VirtualFile mainContentRoot = AndroidRootUtil.getMainContentRoot(facet);

    if (mainContentRoot == null) {
      return null;
    }
    File dir = new File(mainContentRoot.getPath());

    LintModuleProject project;
    if (facet.isGradleProject()) {
      project = new LintGradleProject(client, dir, dir, facet);
    }
    else {
      project = new LintModuleProject(client, dir, dir, facet);
    }
    client.registerProject(dir, project);
    return project;
  }

  /** Adds any gradle library projects to the dependency list */
  private static void addGradleLibraryProjects(@NonNull LintClient client,
                                               @Nullable List<VirtualFile> files,
                                               @NonNull Map<AndroidLibrary, Project> libraryMap,
                                               @NonNull List<Project> projects,
                                               @NonNull AndroidFacet facet,
                                               @NonNull LintModuleProject project,
                                               @NonNull Map<Project,Module> projectMap,
                                               @NonNull List<Project> dependencies) {
    File dir;IdeaAndroidProject gradleProject = facet.getIdeaAndroidProject();
    if (gradleProject != null) {
      Collection<AndroidLibrary> libraries = gradleProject.getSelectedVariant().getMainArtifact().getDependencies().getLibraries();
      for (AndroidLibrary library : libraries) {
        Project p = libraryMap.get(library);
        if (p == null) {
          dir = library.getFolder();
          p = new LintGradleLibraryProject(client, dir, dir, library);
          libraryMap.put(library, p);
          projectMap.put(p, facet.getModule());
          projects.add(p);

          if (files != null) {
            VirtualFile libraryDir = LocalFileSystem.getInstance().findFileByIoFile(dir);
            if (libraryDir != null) {
              ListIterator<VirtualFile> iterator = files.listIterator();
              while (iterator.hasNext()) {
                VirtualFile file = iterator.next();
                if (VfsUtilCore.isAncestor(libraryDir, file, false)) {
                  project.addFile(VfsUtilCore.virtualToIoFile(file));
                  iterator.remove();
                }
              }
            }
            if (files.isEmpty()) {
              files = null; // No more work in other modules
            }
          }
        }
        dependencies.add(p);
      }
    }
  }

  @Override
  protected void initialize() {
    // NOT calling super: super performs ADT/ant initialization. Here we want to use
    // the gradle data instead
  }

  /** Wraps an Android module */
  private static class LintModuleProject extends IntellijLintProject {
    protected final AndroidFacet myFacet;

    private LintModuleProject(@NonNull LintClient client, @NonNull File dir, @NonNull File referenceDir, @NonNull AndroidFacet facet) {
      super(client, dir, referenceDir);
      myFacet = facet;

      mGradleProject = false;
      mLibrary = myFacet.isLibraryProject();

      AndroidPlatform platform = AndroidPlatform.getInstance(myFacet.getModule());
      if (platform != null) {
        mBuildSdk = platform.getApiLevel();
      }
    }

    @NonNull
    @Override
    public String getName() {
      return myFacet.getModule().getName();
    }

    public void setDirectLibraries(List<Project> libraries) {
      mDirectLibraries = libraries;
    }

    @Override
    @NonNull
    public List<File> getManifestFiles() {
      if (mManifestFiles == null) {
        VirtualFile manifestFile = AndroidRootUtil.getManifestFile(myFacet);
        if (manifestFile != null) {
          mManifestFiles = Collections.singletonList(VfsUtilCore.virtualToIoFile(manifestFile));
        } else {
          mManifestFiles = Collections.emptyList();
        }
      }

      return mManifestFiles;
    }

    @NonNull
    @Override
    public List<File> getProguardFiles() {
      if (mProguardFiles == null) {
        assert !myFacet.isGradleProject(); // Should be overridden to read from gradle state
        final JpsAndroidModuleProperties properties = myFacet.getProperties();

        if (properties.RUN_PROGUARD) {
          final List<String> urls = properties.myProGuardCfgFiles;

          if (!urls.isEmpty()) {
            mProguardFiles = new ArrayList<File>();

            for (String osPath : AndroidUtils.urlsToOsPaths(urls, null)) {
              if (!osPath.contains(AndroidCommonUtils.SDK_HOME_MACRO)) {
                mProguardFiles.add(new File(osPath));
              }
            }
          }
        }

        if (mProguardFiles == null) {
          mProguardFiles = Collections.emptyList();
        }
      }

      return mProguardFiles;
    }

    @NonNull
    @Override
    public List<File> getResourceFolders() {
      if (mResourceFolders == null) {
        List<VirtualFile> folders = myFacet.getResourceFolderManager().getFolders();
        List<File> dirs = Lists.newArrayListWithExpectedSize(folders.size());
        for (VirtualFile folder : folders) {
          dirs.add(VfsUtilCore.virtualToIoFile(folder));
        }
        mResourceFolders = dirs;
      }

      return mResourceFolders;
    }

    @NonNull
    @Override
    public List<File> getJavaSourceFolders() {
      if (mJavaSourceFolders == null) {
        VirtualFile[] sourceRoots = ModuleRootManager.getInstance(myFacet.getModule()).getSourceRoots(false);
        List<File> dirs = new ArrayList<File>(sourceRoots.length);
        for (VirtualFile root : sourceRoots) {
          dirs.add(new File(root.getPath()));
        }
        mJavaSourceFolders = dirs;
      }

      return mJavaSourceFolders;
    }

    @NonNull
    @Override
    public List<File> getJavaClassFolders() {
      if (SUPPORT_CLASS_FILES) {
        if (mJavaClassFolders == null) {
          VirtualFile folder = AndroidDexCompiler.getOutputDirectoryForDex(myFacet.getModule());
          if (folder != null) {
            mJavaClassFolders = Collections.singletonList(VfsUtilCore.virtualToIoFile(folder));
          } else {
            mJavaClassFolders = Collections.emptyList();
          }
        }

        return mJavaClassFolders;
      }

      return Collections.emptyList();
    }

    @NonNull
    @Override
    public List<File> getJavaLibraries() {
      if (SUPPORT_CLASS_FILES) {
        if (mJavaLibraries == null) {
          mJavaLibraries = Lists.newArrayList();

          final OrderEntry[] entries = ModuleRootManager.getInstance(myFacet.getModule()).getOrderEntries();
          // loop in the inverse order to resolve dependencies on the libraries, so that if a library
          // is required by two higher level libraries it can be inserted in the correct place

          for (int i = entries.length - 1; i >= 0; i--) {
            final OrderEntry orderEntry = entries[i];
            if (orderEntry instanceof LibraryOrderEntry) {
              LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)orderEntry;
              VirtualFile[] classes = libraryOrderEntry.getRootFiles(OrderRootType.CLASSES);
              if (classes != null) {
                for (VirtualFile file : classes) {
                  mJavaLibraries.add(VfsUtilCore.virtualToIoFile(file));
                }
              }
            }
          }
        }

        return mJavaLibraries;
      }

      return Collections.emptyList();
    }
  }

  private static class LintGradleProject extends LintModuleProject {
    /**
     * Creates a new Project. Use one of the factory methods to create.
     */
    private LintGradleProject(@NonNull LintClient client, @NonNull File dir, @NonNull File referenceDir, @NonNull AndroidFacet facet) {
      super(client, dir, referenceDir, facet);
      mGradleProject = true;
      mMergeManifests = true;

      // TODO: Read mBuildSdk from compileSdkVersion in the build.gradle file!
    }

    @NonNull
    @Override
    public List<File> getManifestFiles() {
      if (mManifestFiles == null) {
        mManifestFiles = Lists.newArrayList();
        File mainManifest = myFacet.getMainSourceSet().getManifestFile();
        if (mainManifest.exists()) {
          mManifestFiles.add(mainManifest);
        }

        List<SourceProvider> flavorSourceSets = myFacet.getFlavorSourceSets();
        if (flavorSourceSets != null) {
          for (SourceProvider provider : flavorSourceSets) {
            File manifestFile = provider.getManifestFile();
            if (manifestFile.exists()) {
              mManifestFiles.add(manifestFile);
            }
          }
        }

        SourceProvider multiProvider = myFacet.getMultiFlavorSourceProvider();
        if (multiProvider != null) {
          File manifestFile = myFacet.getMainSourceSet().getManifestFile();
          if (manifestFile.exists()) {
            mManifestFiles.add(manifestFile);
          }
        }

        SourceProvider buildTypeSourceSet = myFacet.getBuildTypeSourceSet();
        if (buildTypeSourceSet != null) {
          File manifestFile = buildTypeSourceSet.getManifestFile();
          if (manifestFile.exists()) {
            mManifestFiles.add(manifestFile);
          }
        }

        SourceProvider variantProvider = myFacet.getVariantSourceProvider();
        if (variantProvider != null) {
          File manifestFile = myFacet.getMainSourceSet().getManifestFile();
          if (manifestFile.exists()) {
            mManifestFiles.add(manifestFile);
          }
        }
      }

      return mManifestFiles;
    }

    @NonNull
    @Override
    public List<File> getProguardFiles() {
      if (mProguardFiles == null) {
        if (myFacet.isGradleProject()) {
          IdeaAndroidProject gradleProject = myFacet.getIdeaAndroidProject();
          if (gradleProject != null) {
            ProductFlavor flavor = gradleProject.getDelegate().getDefaultConfig().getProductFlavor();
            mProguardFiles = Lists.newArrayList();
            for (File file : flavor.getProguardFiles()) {
              if (file.exists()) {
                mProguardFiles.add(file);
              }
            }
            try {
              for (File file : flavor.getConsumerProguardFiles()) {
                if (file.exists()) {
                  mProguardFiles.add(file);
                }
              }
            } catch (Throwable t) {
              // On some models, this threw
              //   org.gradle.tooling.model.UnsupportedMethodException: Unsupported method: BaseConfig.getConsumerProguardFiles().
              // Playing it safe for a while.
            }
          }
        }

        if (mProguardFiles == null) {
          mProguardFiles = Collections.emptyList();
        }
      }

      return mProguardFiles;
    }

    @NonNull
    @Override
    public List<File> getJavaClassFolders() {
      if (SUPPORT_CLASS_FILES) {
        if (mJavaClassFolders == null) {
          // Overridden because we don't synchronize the gradle output directory to
          // the AndroidDexCompiler settings the way java source roots are mapped into
          // the module content root settings
          File dir = null;
          if (myFacet.isGradleProject()) {
            IdeaAndroidProject gradleProject = myFacet.getIdeaAndroidProject();
            if (gradleProject != null) {
              Variant variant = gradleProject.getSelectedVariant();
              dir = variant.getMainArtifact().getClassesFolder();
            }
          }
          if (dir != null) {
            mJavaClassFolders = Collections.singletonList(dir);
          } else {
            mJavaClassFolders = Collections.emptyList();
          }
        }

        return mJavaClassFolders;
      }

      return Collections.emptyList();
    }

    @NonNull
    @Override
    public List<File> getJavaLibraries() {
      if (SUPPORT_CLASS_FILES) {
        if (mJavaLibraries == null) {
          if (myFacet.isGradleProject() && myFacet.getIdeaAndroidProject() != null) {
            IdeaAndroidProject gradleProject = myFacet.getIdeaAndroidProject();
            Collection<File> jars = gradleProject.getSelectedVariant().getMainArtifact().getDependencies().getJars();
            mJavaLibraries = Lists.newArrayListWithExpectedSize(jars.size());
            for (File jar : jars) {
              if (jar.exists()) {
                mJavaLibraries.add(jar);
              }
            }
          } else {
            mJavaLibraries = super.getJavaLibraries();
          }
        }
        return mJavaLibraries;
      }

      return Collections.emptyList();
    }

    @Nullable
    @Override
    public String getPackage() {
      String manifestPackage = super.getPackage();
      // For now, lint only needs the manifest package; not the potentially variant specific
      // package. As part of the Gradle work on the Lint API we should make two separate
      // package lookup methods -- one for the manifest package, one for the build package
      if (manifestPackage != null) {
        return manifestPackage;
      }

      IdeaAndroidProject project = myFacet.getIdeaAndroidProject();
      if (project != null) {
        return project.computePackageName("");
      }

      return null;
    }

    @Override
    public int getMinSdk() {
      IdeaAndroidProject ideaAndroidProject = myFacet.getIdeaAndroidProject();
      if (ideaAndroidProject != null) {
        int minSdkVersion = ideaAndroidProject.getSelectedVariant().getMergedFlavor().getMinSdkVersion();
        if (minSdkVersion >= 1) {
          return minSdkVersion;
        }
        // Else: not specified in gradle files; fall back to manifest
      }

      return super.getMinSdk();
    }

    @Override
    public int getTargetSdk() {
      IdeaAndroidProject ideaAndroidProject = myFacet.getIdeaAndroidProject();
      if (ideaAndroidProject != null) {
        int targetSdkVersion = ideaAndroidProject.getSelectedVariant().getMergedFlavor().getTargetSdkVersion();
        if (targetSdkVersion >= 1) {
          return targetSdkVersion;
        }
        // Else: not specified in gradle files; fall back to manifest
      }

      return super.getTargetSdk();
    }

    @Override
    public int getBuildSdk() {
      IdeaAndroidProject ideaAndroidProject = myFacet.getIdeaAndroidProject();
      if (ideaAndroidProject != null) {
        String compileTarget = ideaAndroidProject.getDelegate().getCompileTarget();
        AndroidVersion version = AndroidTargetHash.getPlatformVersion(compileTarget);
        if (version != null) {
          return version.getApiLevel();
        }
      }

      AndroidPlatform platform = AndroidPlatform.getPlatform(myFacet.getModule());
      if (platform != null) {
        return platform.getApiLevel();
      }

      return super.getBuildSdk();
    }

    @Nullable
    @Override
    public AndroidProject getGradleProjectModel() {
      IdeaAndroidProject project = myFacet.getIdeaAndroidProject();
      if (project != null) {
        project.getSelectedVariant();
        return project.getDelegate();
      }

      return null;
    }

    @Nullable
    @Override
    public Variant getCurrentVariant() {
      IdeaAndroidProject project = myFacet.getIdeaAndroidProject();
      if (project != null) {
        return project.getSelectedVariant();
      }

      return null;
    }

    @Nullable
    @Override
    public AndroidLibrary getGradleLibraryModel() {
      return null;
    }
  }

  private static class LintGradleLibraryProject extends IntellijLintProject {
    private final AndroidLibrary myLibrary;

    private LintGradleLibraryProject(@NonNull LintClient client,
                                     @NonNull File dir,
                                     @NonNull File referenceDir,
                                     @NonNull AndroidLibrary library) {
      super(client, dir, referenceDir);
      myLibrary = library;

      mLibrary = true;
      mMergeManifests = true;
      mReportIssues = false;
      mGradleProject = true;
      mDirectLibraries = Collections.emptyList();
    }

    @NonNull
    @Override
    public List<File> getManifestFiles() {
      if (mManifestFiles == null) {
        File manifest = myLibrary.getManifest();
        if (manifest.exists()) {
          mManifestFiles = Collections.singletonList(manifest);
        } else {
          mManifestFiles = Collections.emptyList();
        }
      }

      return mManifestFiles;
    }

    @NonNull
    @Override
    public List<File> getProguardFiles() {
      if (mProguardFiles == null) {
        File proguardRules = myLibrary.getProguardRules();
        if (proguardRules.exists()) {
          mProguardFiles = Collections.singletonList(proguardRules);
        } else {
          mProguardFiles = Collections.emptyList();
        }
      }

      return mProguardFiles;
    }

    @NonNull
    @Override
    public List<File> getResourceFolders() {
      if (mResourceFolders == null) {
        File folder = myLibrary.getResFolder();
        if (folder.exists()) {
          mResourceFolders = Collections.singletonList(folder);
        } else {
          mResourceFolders = Collections.emptyList();
        }
      }

      return mResourceFolders;
    }

    @NonNull
    @Override
    public List<File> getJavaSourceFolders() {
      return Collections.emptyList();
    }

    @NonNull
    @Override
    public List<File> getJavaClassFolders() {
      return Collections.emptyList();
    }

    @NonNull
    @Override
    public List<File> getJavaLibraries() {
      if (SUPPORT_CLASS_FILES) {
        if (mJavaLibraries == null) {
          File jarFile = myLibrary.getJarFile();
          if (jarFile.exists()) {
            mJavaLibraries = Collections.singletonList(jarFile);
          } else {
            mJavaLibraries = Collections.emptyList();
          }
        }

        return mJavaLibraries;
      }

      return Collections.emptyList();
    }

    @Nullable
    @Override
    public AndroidProject getGradleProjectModel() {
      return null;
    }

    @Nullable
    @Override
    public AndroidLibrary getGradleLibraryModel() {
      return myLibrary;
    }
  }
}
