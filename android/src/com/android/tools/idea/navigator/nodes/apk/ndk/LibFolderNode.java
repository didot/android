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
package com.android.tools.idea.navigator.nodes.apk.ndk;

import com.android.tools.idea.apk.ApkFacet;
import com.android.tools.idea.apk.debugging.NativeLibrary;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidSourceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.icons.AllIcons.Modules.SourceRoot;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;
import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;

public class LibFolderNode extends ProjectViewNode<VirtualFile> {
  @NotNull private final VirtualFile myFolder;

  public LibFolderNode(@NotNull Project project, @NotNull VirtualFile folder, @NotNull ViewSettings settings) {
    super(project, folder, settings);
    myFolder = folder;
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode> getChildren() {
    assert myProject != null;
    List<NativeLibrary> libraries = new ArrayList<>();

    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      ApkFacet facet = ApkFacet.getInstance(module);
      if (facet != null) {
        libraries.addAll(facet.getConfiguration().NATIVE_LIBRARIES);
      }
    }

    ViewSettings settings = getSettings();
    List<AbstractTreeNode> children = new ArrayList<>();
    for (NativeLibrary library : libraries) {
      children.add(new LibraryNode(myProject, library, settings));
    }

    return children;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return isAncestor(myFolder, file, false /* not strict */);
  }

  @Override
  protected void update(PresentationData presentation) {
    presentation.setIcon(SourceRoot);
    presentation.addText(getSourceType().getName(), REGULAR_ATTRIBUTES);
    presentation.addText(" (lib)", GRAY_ATTRIBUTES);
  }

  @Override
  @Nullable
  public Comparable getSortKey() {
    return getTypeSortKey();
  }

  @Override
  @Nullable
  public Comparable getTypeSortKey() {
    return getSourceType();
  }

  @NotNull
  private static AndroidSourceType getSourceType() {
    return AndroidSourceType.CPP;
  }

  @Override
  @Nullable
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return getSourceType().getName();
  }
}
