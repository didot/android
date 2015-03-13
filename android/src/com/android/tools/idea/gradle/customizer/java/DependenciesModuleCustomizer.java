/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.customizer.java;

import com.android.tools.idea.gradle.IdeaJavaProject;
import com.android.tools.idea.gradle.JavaModel;
import com.android.tools.idea.gradle.customizer.AbstractDependenciesModuleCustomizer;
import com.android.tools.idea.gradle.dependency.DependencySetupErrors;
import com.android.tools.idea.gradle.facet.JavaGradleFacet;
import com.android.tools.idea.gradle.facet.JavaGradleFacetConfiguration;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.google.common.collect.Lists;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import org.gradle.tooling.model.idea.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.util.Projects.isGradleProjectModule;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.intellij.openapi.util.io.FileUtil.*;
import static java.util.Collections.singletonList;

public class DependenciesModuleCustomizer extends AbstractDependenciesModuleCustomizer<IdeaJavaProject> {
  @NotNull @NonNls private static final String UNRESOLVED_DEPENDENCY_PREFIX = "unresolved dependency - ";

  private static final DependencyScope DEFAULT_DEPENDENCY_SCOPE = DependencyScope.COMPILE;

  @Override
  protected void setUpDependencies(@NotNull ModifiableRootModel moduleModel, @NotNull IdeaJavaProject javaProject) {
    List<String> unresolved = Lists.newArrayList();
    List<? extends IdeaDependency> dependencies = javaProject.getDependencies();
    for (IdeaDependency dependency : dependencies) {
      if (dependency instanceof IdeaModuleDependency) {
        updateModuleDependency(moduleModel, (IdeaModuleDependency)dependency);
        continue;
      }
      if (dependency instanceof IdeaSingleEntryLibraryDependency) {
        IdeaSingleEntryLibraryDependency libDependency = (IdeaSingleEntryLibraryDependency)dependency;
        if (isResolved(libDependency)) {
          updateLibraryDependency(moduleModel, (IdeaSingleEntryLibraryDependency)dependency);
          continue;
        }
        String name = getUnresolvedDependencyName(libDependency);
        if (name != null) {
          unresolved.add(name);
        }
      }
    }

    Module module = moduleModel.getModule();

    ProjectSyncMessages messages = ProjectSyncMessages.getInstance(moduleModel.getProject());
    messages.reportUnresolvedDependencies(unresolved, module);

    JavaGradleFacet facet = setAndGetJavaGradleFacet(module);
    File buildFolderPath = javaProject.getBuildFolderPath();
    if (!isGradleProjectModule(module)) {
      JavaModel javaModel = new JavaModel(unresolved, buildFolderPath);
      facet.setJavaModel(javaModel);
    }
    JavaGradleFacetConfiguration facetProperties = facet.getConfiguration();
    facetProperties.BUILD_FOLDER_PATH = buildFolderPath != null ? toSystemIndependentName(buildFolderPath.getPath()) : "";
    facetProperties.BUILDABLE = javaProject.isBuildable();
  }

  private static boolean isResolved(@NotNull IdeaSingleEntryLibraryDependency dependency) {
    String libraryName = getFileName(dependency);
    return libraryName != null && !libraryName.startsWith(UNRESOLVED_DEPENDENCY_PREFIX);
  }

  @Nullable
  private static String getUnresolvedDependencyName(@NotNull IdeaSingleEntryLibraryDependency dependency) {
    String libraryName = getFileName(dependency);
    if (libraryName == null) {
      return null;
    }
    // Gradle uses names like 'unresolved dependency - commons-collections commons-collections 3.2' for unresolved dependencies.
    // We report the unresolved dependency as 'commons-collections:commons-collections:3.2'
    return libraryName.substring(UNRESOLVED_DEPENDENCY_PREFIX.length()).replace(' ', ':');
  }

  @Nullable
  private static String getFileName(@NotNull IdeaSingleEntryLibraryDependency dependency) {
    File binaryPath = dependency.getFile();
    return binaryPath != null ? binaryPath.getName() : null;
  }

  private void updateModuleDependency(@NotNull ModifiableRootModel moduleModel, @NotNull IdeaModuleDependency dependency) {
    DependencySetupErrors setupErrors = getSetupErrors(moduleModel.getProject());

    IdeaModule dependencyModule = dependency.getDependencyModule();
    if (dependencyModule == null || isNullOrEmpty(dependencyModule.getName())) {
      setupErrors.addMissingName(moduleModel.getModule().getName());
      return;
    }
    String moduleName = dependencyModule.getName();
    ModuleManager moduleManager = ModuleManager.getInstance(moduleModel.getProject());
    Module found = null;
    for (Module module : moduleManager.getModules()) {
      if (moduleName.equals(module.getName())) {
        found = module;
      }
    }
    if (found != null) {
      ModuleOrderEntry orderEntry = moduleModel.addModuleOrderEntry(found);
      orderEntry.setExported(true);
      return;
    }
    setupErrors.addMissingModule(moduleName, moduleModel.getModule().getName(), null);
  }

  private void updateLibraryDependency(@NotNull ModifiableRootModel moduleModel, @NotNull IdeaSingleEntryLibraryDependency dependency) {
    DependencyScope scope = parseScope(dependency.getScope());
    File binaryPath = dependency.getFile();
    if (binaryPath == null) {
      DependencySetupErrors setupErrors = getSetupErrors(moduleModel.getProject());
      setupErrors.addMissingBinaryPath(moduleModel.getModule().getName());
      return;
    }
    String path = binaryPath.getPath();

    // Gradle API doesn't provide library name at the moment.
    String name = binaryPath.isFile() ? getNameWithoutExtension(binaryPath) : sanitizeFileName(path);
    setUpLibraryDependency(moduleModel, name, scope, singletonList(path), getPath(dependency.getSource()), getPath(dependency.getJavadoc()));
  }

  @NotNull
  private static List<String> getPath(@Nullable File file) {
    return file == null ? Collections.<String>emptyList() : singletonList(file.getPath());
  }

  @NotNull
  private static DependencyScope parseScope(@Nullable IdeaDependencyScope scope) {
    if (scope == null) {
      return DEFAULT_DEPENDENCY_SCOPE;
    }
    String description = scope.getScope();
    if (description == null) {
      return DEFAULT_DEPENDENCY_SCOPE;
    }
    for (DependencyScope dependencyScope : DependencyScope.values()) {
      if (description.equalsIgnoreCase(dependencyScope.toString())) {
        return dependencyScope;
      }
    }
    return DEFAULT_DEPENDENCY_SCOPE;
  }

  @NotNull
  private static JavaGradleFacet setAndGetJavaGradleFacet(Module module) {
    JavaGradleFacet facet = JavaGradleFacet.getInstance(module);
    if (facet != null) {
      return facet;
    }

    // Module does not have Android-Gradle facet. Create one and add it.
    FacetManager facetManager = FacetManager.getInstance(module);
    ModifiableFacetModel model = facetManager.createModifiableModel();
    try {
      facet = facetManager.createFacet(JavaGradleFacet.getFacetType(), JavaGradleFacet.NAME, null);
      model.addFacet(facet);
    }
    finally {
      model.commit();
    }
    return facet;
  }

}
