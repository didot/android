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
package com.android.tools.idea.navigator.nodes.ndk;

import static com.android.tools.idea.gradle.util.GradleUtil.getModuleIcon;
import static com.intellij.openapi.util.text.StringUtil.trimEnd;
import static com.intellij.openapi.util.text.StringUtil.trimStart;

import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeArtifact;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.model.NdkVariant;
import com.android.tools.idea.navigator.AndroidProjectViewPane;
import com.android.tools.idea.navigator.nodes.AndroidViewModuleNode;
import com.android.tools.idea.navigator.nodes.ndk.includes.utils.LexicalIncludePaths;
import com.android.tools.idea.navigator.nodes.ndk.includes.view.NativeIncludes;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NdkModuleNode extends AndroidViewModuleNode {
  public NdkModuleNode(
    @NotNull Project project,
    @NotNull Module value,
    @NotNull AndroidProjectViewPane projectViewPane,
    @NotNull ViewSettings settings) {
    super(project, value, projectViewPane, settings);
  }

  @Override
  @NotNull
  protected Collection<AbstractTreeNode<?>> getModuleChildren() {
    Module module = getValue();
    if (module == null) {
      return Collections.emptyList();
    }

    NdkFacet facet = NdkFacet.getInstance(module);
    if (facet == null || facet.getNdkModuleModel() == null) {
      return Collections.emptyList();
    }

    assert myProject != null;
    return getNativeSourceNodes(myProject, facet.getNdkModuleModel(), getSettings());
  }

  @NotNull
  public static Collection<AbstractTreeNode<?>> getNativeSourceNodes(@NotNull Project project,
                                                                  @NotNull NdkModuleModel ndkModel,
                                                                  @NotNull ViewSettings settings) {
    NativeAndroidProject nativeAndroidProject = ndkModel.getAndroidProject();
    Collection<String> sourceFileExtensions = nativeAndroidProject.getFileExtensions().keySet();

    NdkVariant variant = ndkModel.getSelectedVariant();
    Multimap<NativeLibraryKey, NativeArtifact> nativeLibraries = HashMultimap.create();
    for (NativeArtifact artifact : variant.getArtifacts()) {
      File file = artifact.getOutputFile();
      String nativeLibraryName;
      NativeLibraryType nativeLibraryType;
      if (file == null) {
        nativeLibraryName = artifact.getTargetName();
        nativeLibraryType = NativeLibraryType.OBJECT_LIBRARY;
      }
      else {
        String name = file.getName();
        if (name.endsWith(".so")) {
          nativeLibraryName = trimEnd(name, ".so");
          nativeLibraryType = NativeLibraryType.SHARED_LIBRARY;
        }
        else if (name.endsWith(".a")) {
          nativeLibraryName = trimEnd(name, ".a");
          nativeLibraryType = NativeLibraryType.STATIC_LIBRARY;
        }
        else {
          nativeLibraryName = name;
          nativeLibraryType = NativeLibraryType.OTHER;
        }
        nativeLibraryName = trimStart(nativeLibraryName, "lib");
      }
      nativeLibraries.put(new NativeLibraryKey(nativeLibraryName, nativeLibraryType), artifact);
    }
    List<AbstractTreeNode<?>> children = new ArrayList<>();
    for (NativeLibraryKey key : nativeLibraries.keySet()) {
      String nativeLibraryType = key.getType().getDisplayText();
      String nativeLibraryName = key.getName();
      LocalFileSystem fileSystem = LocalFileSystem.getInstance();
      VirtualFile buildFileFolder = fileSystem.findFileByIoFile(ndkModel.getRootDirPath());
      NdkLibraryEnhancedHeadersNode node =
        new NdkLibraryEnhancedHeadersNode(buildFileFolder, project, nativeLibraryName, nativeLibraryType, nativeLibraries.get(key),
                                          new NativeIncludes(ndkModel::findSettings, nativeLibraries.get(key)), settings,
                                          sourceFileExtensions);
      children.add(node);
    }
    if (children.size() == 1) {
      return (Collection<AbstractTreeNode<?>>)children.get(0).getChildren();
    }
    return children;
  }

  @Override
  @Nullable
  public Comparable getSortKey() {
    Module module = getValue();
    if (module == null) {
      return null;
    }
    return module.getName();
  }

  @Override
  @Nullable
  public Comparable getTypeSortKey() {
    return getSortKey();
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    Module module = getValue();
    if (module == null) {
      return null;
    }
    return String.format("%1$s (Native-Android-Gradle)", super.toTestString(printInfo));
  }

  @Override
  public void update(@NotNull PresentationData presentation) {
    super.update(presentation);
    Module module = getValue();
    if (module != null) {
      presentation.setIcon(getModuleIcon(module));
    }
  }

  public static boolean containedInIncludeFolders(@NotNull NdkModuleModel model, @NotNull VirtualFile file) {
    if (!LexicalIncludePaths.hasHeaderExtension(file.getName())) {
      return false;
    }

    NdkVariant variant = model.getSelectedVariant();
    Multimap<String, NativeArtifact> nativeLibraries = HashMultimap.create();
    for (NativeArtifact artifact : variant.getArtifacts()) {
      File outputFile = artifact.getOutputFile();
      if (outputFile == null) continue;
      String artifactOutputFileName = outputFile.getName();
      nativeLibraries.put(artifactOutputFileName, artifact);
    }

    for (String name : nativeLibraries.keySet()) {
      if (NdkLibraryEnhancedHeadersNode
        .containedInIncludeFolders(new NativeIncludes(model::findSettings, nativeLibraries.get(name)), file)) {
        return true;
      }
    }
    return false;
  }
}
