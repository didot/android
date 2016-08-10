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
package com.android.tools.idea.gradle.testing;

import com.android.builder.model.*;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.customizer.dependency.DependencySet;
import com.android.tools.idea.gradle.customizer.dependency.LibraryDependency;
import com.android.tools.idea.gradle.customizer.dependency.ModuleDependency;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;

import static com.android.tools.idea.gradle.customizer.dependency.LibraryDependency.PathType.BINARY;
import static com.android.tools.idea.gradle.util.FilePaths.getJarFromJarUrl;
import static com.android.tools.idea.gradle.util.GradleUtil.getDependencies;
import static com.android.tools.idea.gradle.util.GradleUtil.getGeneratedSourceFolders;
import static com.android.utils.FileUtils.toSystemDependentPath;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.vfs.StandardFileSystems.JAR_PROTOCOL_PREFIX;
import static com.intellij.openapi.vfs.VfsUtilCore.urlToPath;
import static org.jetbrains.android.facet.IdeaSourceProvider.getAllSourceFolders;

class ExcludedRoots {
  @NotNull private final Module myModule;
  @NotNull private final ExcludedModules myExcludedModules;
  private final boolean myAndroidTest;

  @NotNull private final Set<File> myExcludedRoots = new HashSet<>();
  @NotNull private final Set<String> myIncludedRootNames = new HashSet<>();

  ExcludedRoots(@NotNull Module module,
                @NotNull ExcludedModules excludedModules,
                @NotNull DependencySet dependenciesToExclude,
                @NotNull DependencySet dependenciesToInclude,
                boolean isAndroidTest) {
    myModule = module;
    myExcludedModules = excludedModules;
    myAndroidTest = isAndroidTest;
    addFolderPathsFromExcludedModules();
    addRemainingModelsIfNecessary();

    for (LibraryDependency libraryDependency : dependenciesToInclude.onLibraries()) {
      Collection<String> binaryPaths = libraryDependency.getPaths(BINARY);
      for (String binaryPath : binaryPaths) {
        File path = new File(binaryPath);
        myIncludedRootNames.add(path.getName());
      }
    }

    addLibraryPaths(dependenciesToExclude);
    removeLibraryPaths(dependenciesToInclude);
  }

  private void addFolderPathsFromExcludedModules() {
    for (Module module : myExcludedModules) {
      ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
      for (ContentEntry entry : rootManager.getContentEntries()) {
        for (SourceFolder sourceFolder : entry.getSourceFolders()) {
          myExcludedRoots.add(urlToFilePath(sourceFolder.getUrl()));
        }

        CompilerModuleExtension compiler = rootManager.getModuleExtension(CompilerModuleExtension.class);
        String url = compiler.getCompilerOutputUrl();
        if (isNotEmpty(url)) {
          myExcludedRoots.add(urlToFilePath(url));
        }
      }

      AndroidGradleModel androidGradleModel = AndroidGradleModel.get(module);
      if (androidGradleModel != null) {
        myExcludedRoots.add(androidGradleModel.getMainArtifact().getJavaResourcesFolder());
      }
    }
  }

  @Nullable
  private static File urlToFilePath(@NotNull String url) {
    if (url.startsWith(JAR_PROTOCOL_PREFIX)) {
      return getJarFromJarUrl(url);
    }
    String path = urlToPath(url);
    return new File(toSystemDependentPath(path));
  }

  private void addRemainingModelsIfNecessary() {
    ModuleManager moduleManager = ModuleManager.getInstance(myExcludedModules.getProject());
    for (Module module : moduleManager.getModules()) {
      if (myExcludedModules.contains(module)) {
        // Excluded modules have already been dealt with.
        continue;
      }
      addModuleIfNecessary(module);
    }
  }

  private void addModuleIfNecessary(@NotNull Module module) {
    AndroidGradleModel androidModel = AndroidGradleModel.get(module);
    if (androidModel != null) {
      BaseArtifact unitTestArtifact = androidModel.getUnitTestArtifactInSelectedVariant();
      BaseArtifact androidTestArtifact = androidModel.getAndroidTestArtifactInSelectedVariant();

      BaseArtifact excludeArtifact = myAndroidTest ? unitTestArtifact : androidTestArtifact;
      BaseArtifact includeArtifact = myAndroidTest ? androidTestArtifact : unitTestArtifact;

      if (excludeArtifact != null) {
        processFolders(excludeArtifact, androidModel, myExcludedRoots::add);
      }

      if (includeArtifact != null) {
        processFolders(includeArtifact, androidModel, myExcludedRoots::remove);
      }
    }
  }

  private static void processFolders(@NotNull BaseArtifact artifact,
                                     @NotNull AndroidGradleModel androidModel,
                                     @NotNull Consumer<File> action) {
    action.accept(artifact.getClassesFolder());
    for (File file : getGeneratedSourceFolders(artifact)) {
      action.accept(file);
    }

    String artifactName = artifact.getName();
    List<SourceProvider> testSourceProviders = androidModel.getTestSourceProviders(artifactName);
    for (SourceProvider sourceProvider : testSourceProviders) {
      for (File file : getAllSourceFolders(sourceProvider)) {
        action.accept(file);
      }
    }
  }

  private void addLibraryPaths(@NotNull DependencySet dependencies) {
    for (LibraryDependency dependency : dependencies.onLibraries()) {
      for (String path : dependency.getPaths(BINARY)) {
        myExcludedRoots.add(new File(path));
      }
    }
  }

  void removeLibraryPaths(@NotNull DependencySet dependencies) {
    for (LibraryDependency dependency : dependencies.onLibraries()) {
      for (String path : dependency.getPaths(BINARY)) {
        myExcludedRoots.remove(new File(path));
      }
    }

    // Reverted this change because there are still issues with tests scopes.
    // Apparently, we are still being too aggressive and excluding too much.
    // See https://code.google.com/p/android/issues/detail?id=219707

    //// Now we need to add to 'excluded' roots the libraries that are in the modules to include, but are in the scope that needs to be
    //// excluded.
    //// https://code.google.com/p/android/issues/detail?id=206481
    //Project project = myModule.getProject();
    //for (ModuleDependency dependency : dependencies.onModules()) {
    //  Module module = dependency.getModule(project);
    //  if (module != null) {
    //    addLibraryPaths(module);
    //  }
    //}
  }

  private void addLibraryPaths(@NotNull Module module) {
    AndroidGradleModel model = AndroidGradleModel.get(module);
    if (model != null) {
      BaseArtifact exclude = myAndroidTest ? model.getUnitTestArtifactInSelectedVariant() : model.getAndroidTestArtifactInSelectedVariant();
      if (exclude != null) {
        addLibraryPaths(exclude, model);
      }
    }
  }

  private void addLibraryPaths(@NotNull BaseArtifact artifact, @NotNull AndroidGradleModel model) {
    Dependencies dependencies = getDependencies(artifact, model.getModelVersion());
    for (AndroidLibrary library : dependencies.getLibraries()) {
      if (isEmpty(library.getProject())) {
        for (File file : library.getLocalJars()) {
          if (!isAlreadyIncluded(file)) {
            myExcludedRoots.add(file);
          }
        }
      }
    }
    for (JavaLibrary library : dependencies.getJavaLibraries()) {
      if (isEmpty(getProject(library))) {
        File jarFile = library.getJarFile();
        if (!isAlreadyIncluded(jarFile)) {
          myExcludedRoots.add(jarFile);
        }
      }
    }
  }

  private boolean isAlreadyIncluded(@NotNull File file) {
    // Do not exclude any library that was already marked as "included"
    // See:
    // https://code.google.com/p/android/issues/detail?id=219089
    return myIncludedRootNames.contains(file.getName());
  }

  @Nullable
  private static String getProject(@NotNull JavaLibrary library) {
    try {
      return library.getProject();
    }
    catch (UnsupportedMethodException e) {
      return null;
    }
  }

  @NotNull
  public Set<File> get() {
    return myExcludedRoots;
  }
}
