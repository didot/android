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
package com.android.tools.idea.gradle.npw.project;

import com.android.builder.model.SourceProvider;
import com.android.tools.idea.npw.model.NewModuleModel;
import com.android.tools.idea.projectsystem.AndroidModuleTemplate;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.*;

/**
 * Project paths for a Gradle Android project.
 *
 * <p>Generally looks like this:
 *
 * <pre>
 * app/ (module root)
 * `-src/
 *  |-main/ (manifest directory)
 *  | |-AndroidManifest.xml
 *  | |-java/... (src root)
 *  | | `-com/google/foo/bar/... (src directory of package com.google.foo.bar)
 *  | |-res/... (res directory)
 *  | `-aidl/... (aidl directory)
 *  `-test/
 *    `-java/... (test root)
 *      `-com/google/foo/bar/... (test directory of package com.google.foo.bar)
 * </pre>
 */
public class GradleAndroidModuleTemplate implements AndroidModuleTemplate {
  @Nullable private File myModuleRoot;
  @Nullable private File mySrcRoot;
  @Nullable private File myTestRoot;
  @NotNull private List<File> myResDirectories = Collections.emptyList();
  @Nullable private File myAidlRoot;
  @Nullable private File myManifestDirectory;

  @Override
  @Nullable
  public File getModuleRoot() {
    return myModuleRoot;
  }

  @Nullable
  private static File appendPackageToRoot(@Nullable File root, @Nullable String packageName) {
    if (root == null || packageName == null) {
      return root;
    }
    String packagePath = packageName.replace('.', File.separatorChar);
    return new File(root, packagePath);
  }

  @Override
  @Nullable
  public File getSrcDirectory(@Nullable String packageName) {
    return appendPackageToRoot(mySrcRoot, packageName);
  }

  @Override
  @Nullable
  public File getTestDirectory(@Nullable String packageName) {
    return appendPackageToRoot(myTestRoot, packageName);
  }

  @Override
  @NotNull
  public List<File> getResDirectories() {
    return myResDirectories;
  }

  @Override
  @Nullable
  public File getAidlDirectory(@Nullable String packageName) {
    return appendPackageToRoot(myAidlRoot, packageName);
  }

  @Override
  @Nullable
  public File getManifestDirectory() {
    return myManifestDirectory;
  }

  public static NamedModuleTemplate createDummyTemplate() {
    return createDefaultTemplateAt("", "");
  }

  /**
   * Create an {@link NamedModuleTemplate} with default values.
   * Assumes the 'main' flavor and default android locations for the source, test, res,
   * aidl and manifest.
   */
  public static NamedModuleTemplate createDefaultTemplateAt(@NotNull String projectPath, @NotNull String moduleName) {
    // Note: Module name may have ":", needs to be converted to a path
    File moduleRoot = NewModuleModel.getModuleRoot(projectPath, moduleName);
    File baseSrcDir = new File(moduleRoot, FD_SOURCES);
    File baseFlavorDir = new File(baseSrcDir, FD_MAIN);
    GradleAndroidModuleTemplate paths = new GradleAndroidModuleTemplate();
    paths.myModuleRoot = moduleRoot;
    paths.mySrcRoot = new File(baseFlavorDir, FD_JAVA);
    paths.myTestRoot = new File(baseSrcDir.getPath(), FD_TEST + File.separatorChar + FD_JAVA);
    paths.myResDirectories = ImmutableList.of(new File(baseFlavorDir, FD_RESOURCES));
    paths.myAidlRoot = new File(baseFlavorDir, FD_AIDL);
    paths.myManifestDirectory = baseFlavorDir;
    return new NamedModuleTemplate("main", paths);
  }

  /**
   * Convenience method to get {@link SourceProvider}s from the current project which can be used
   * to instantiate an instance of this class.
   */
  @NotNull
  private static List<SourceProvider> getSourceProviders(@NotNull AndroidFacet androidFacet, @Nullable VirtualFile targetDirectory) {
    if (targetDirectory != null) {
      return IdeaSourceProvider.getSourceProvidersForFile(androidFacet, targetDirectory, androidFacet.getMainSourceProvider());
    }
    else {
      return IdeaSourceProvider.getAllSourceProviders(androidFacet);
    }
  }

  /**
   * @param androidFacet    from which we receive {@link SourceProvider}s.
   * @param targetDirectory to filter the relevant {@link SourceProvider}s from the {@code androidFacet}.
   * @return a list of {@link NamedModuleTemplate}s created from each of {@code androidFacet}'s {@link SourceProvider}s.
   * In cases where the source provider returns multiple paths, we always take the first match.
   */
  @NotNull
  public static List<NamedModuleTemplate> getModuleTemplates(@NotNull Module module, @Nullable VirtualFile targetDirectory) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return Collections.emptyList();
    }
    List<NamedModuleTemplate> templates = Lists.newArrayList();
    for (SourceProvider sourceProvider : getSourceProviders(facet, targetDirectory)) {
      GradleAndroidModuleTemplate paths = new GradleAndroidModuleTemplate();
      VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
      if (roots.length > 0) {
        paths.myModuleRoot = VfsUtilCore.virtualToIoFile(roots[0]);
      }
      paths.mySrcRoot = Iterables.getFirst(sourceProvider.getJavaDirectories(), null);
      List<VirtualFile> testsRoot = ModuleRootManager.getInstance(module).getSourceRoots(JavaModuleSourceRootTypes.TESTS);
      if (!testsRoot.isEmpty()) {
        paths.myTestRoot = VfsUtilCore.virtualToIoFile(testsRoot.get(0));
      }
      paths.myResDirectories = ImmutableList.copyOf(sourceProvider.getResDirectories());
      paths.myAidlRoot = Iterables.getFirst(sourceProvider.getAidlDirectories(), null);
      paths.myManifestDirectory = sourceProvider.getManifestFile().getParentFile();
      templates.add(new NamedModuleTemplate(sourceProvider.getName(), paths));
    }
    return templates;
  }
}
