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
import com.android.tools.idea.gradle.customizer.AbstractContentRootModuleCustomizer;
import com.android.tools.idea.gradle.util.FilePaths;
import com.android.tools.idea.gradle.util.Projects;
import com.google.common.collect.Lists;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.io.FileUtil;
import org.gradle.tooling.model.idea.IdeaContentRoot;
import org.gradle.tooling.model.idea.IdeaSourceDirectory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;
import org.jetbrains.plugins.gradle.model.ExtIdeaContentRoot;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.jetbrains.jps.model.java.JavaResourceRootType.RESOURCE;
import static org.jetbrains.jps.model.java.JavaResourceRootType.TEST_RESOURCE;
import static org.jetbrains.jps.model.java.JavaSourceRootType.SOURCE;
import static org.jetbrains.jps.model.java.JavaSourceRootType.TEST_SOURCE;

public class ContentRootModuleCustomizer extends AbstractContentRootModuleCustomizer<IdeaJavaProject> {
  @Override
  @NotNull
  protected Collection<ContentEntry> findOrCreateContentEntries(@NotNull ModifiableRootModel model, @NotNull IdeaJavaProject javaProject) {
    List<ContentEntry> allEntries = Lists.newArrayList();
    for (IdeaContentRoot contentRoot : javaProject.getContentRoots()) {
      File rootDirPath = contentRoot.getRootDirectory();
      ContentEntry contentEntry = model.addContentEntry(FilePaths.pathToIdeaUrl(rootDirPath));
      allEntries.add(contentEntry);
    }
    return allEntries;
  }

  @Override
  protected void setUpContentEntries(@NotNull ModifiableRootModel ideaModuleModel,
                                     @NotNull Collection<ContentEntry> contentEntries,
                                     @NotNull IdeaJavaProject javaProject,
                                     @NotNull List<RootSourceFolder> orphans) {
    boolean isTopLevelJavaModule = Projects.isGradleProjectModule(ideaModuleModel.getModule());

    File buildFolderPath = javaProject.getBuildFolderPath();
    boolean buildFolderUnexcluded = buildFolderPath == null;

    for (IdeaContentRoot contentRoot : javaProject.getContentRoots()) {
      if (contentRoot == null) {
        continue;
      }
      addSourceFolders(contentEntries, contentRoot.getSourceDirectories(), SOURCE, false, orphans);
      addSourceFolders(contentEntries, contentRoot.getGeneratedSourceDirectories(), SOURCE, true, orphans);

      addSourceFolders(contentEntries, contentRoot.getTestDirectories(), TEST_SOURCE, false, orphans);
      addSourceFolders(contentEntries, contentRoot.getGeneratedTestDirectories(), TEST_SOURCE, true, orphans);

      if (contentRoot instanceof ExtIdeaContentRoot) {
        ExtIdeaContentRoot extContentRoot = (ExtIdeaContentRoot)contentRoot;
        addSourceFolders(contentEntries, extContentRoot.getResourceDirectories(), RESOURCE, false, orphans);
        addSourceFolders(contentEntries, extContentRoot.getTestResourceDirectories(), TEST_RESOURCE, false, orphans);
      }

      for (File excluded : contentRoot.getExcludeDirectories()) {
        if (excluded != null) {
          ContentEntry contentEntry = FilePaths.findParentContentEntry(excluded, contentEntries);
          if (contentEntry != null) {
            if (isTopLevelJavaModule && !buildFolderUnexcluded) {
              // We need to "undo" the implicit exclusion of "build" folder for top-level module.
              if (FileUtil.filesEqual(excluded, buildFolderPath)) {
                buildFolderUnexcluded = true;
                continue;
              }
            }
            addExcludedFolder(contentEntry, excluded);
          }
        }
      }
    }
  }

  private void addSourceFolders(@NotNull Collection<ContentEntry> contentEntries,
                                @Nullable Set<? extends IdeaSourceDirectory> sourceFolders,
                                @NotNull JpsModuleSourceRootType type,
                                boolean generated,
                                @NotNull List<RootSourceFolder> orphans) {
    if (sourceFolders == null) {
      return;
    }
    for (IdeaSourceDirectory dir : sourceFolders) {
      File path = dir.getDirectory();
      addSourceFolder(contentEntries, path, type, generated, orphans);
    }
  }
}
