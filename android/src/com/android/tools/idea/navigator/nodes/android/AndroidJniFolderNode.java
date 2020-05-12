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
package com.android.tools.idea.navigator.nodes.android;

import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.navigator.nodes.FolderGroupNode;
import com.android.tools.idea.navigator.nodes.GroupNodes;
import com.android.tools.idea.navigator.nodes.ndk.NdkSourceFolderNode;
import com.google.common.collect.Iterables;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.android.tools.idea.navigator.nodes.ndk.NdkModuleNodeKt.containedInIncludeFolders;
import static com.android.tools.idea.navigator.nodes.ndk.NdkModuleNodeKt.getNativeSourceNodes;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;
import static org.jetbrains.android.facet.AndroidSourceType.CPP;

public class AndroidJniFolderNode extends ProjectViewNode<NdkModuleModel> implements FolderGroupNode {

  @NotNull
  final private VirtualFile myBuildFileFolder;

  final private int myCachedHashCode;

  AndroidJniFolderNode(@NotNull Project project, @NotNull NdkModuleModel ndkModuleModel, @NotNull ViewSettings settings) {
    super(project, ndkModuleModel, settings);
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    myBuildFileFolder = Objects.requireNonNull(fileSystem.findFileByIoFile(ndkModuleModel.getRootDirPath()));
    myCachedHashCode = myBuildFileFolder.hashCode();
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode<?>> getChildren() {
    assert myProject != null;
    Collection<AbstractTreeNode<?>> nativeSourceNodes = getNativeSourceNodes(myProject, getNdkModel(), getSettings());
    if (nativeSourceNodes.size() == 1) {
      AbstractTreeNode sourceNode = Iterables.getOnlyElement(nativeSourceNodes);
      if (sourceNode instanceof NdkSourceFolderNode) {
        return ((NdkSourceFolderNode)sourceNode).getChildren();
      }
    }
    return nativeSourceNodes;
  }

  @Override
  @NotNull
  public List<PsiDirectory> getFolders() {
    Collection<File> sourceFolderPaths = getNdkModel().getSelectedVariant().getSourceFolders();
    List<PsiDirectory> folders = new ArrayList<>(sourceFolderPaths.size());

    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    assert myProject != null;
    PsiManager psiManager = PsiManager.getInstance(myProject);

    for (File sourceFolderPath : sourceFolderPaths) {
      VirtualFile sourceFolder = fileSystem.findFileByIoFile(sourceFolderPath);
      if (sourceFolder != null) {
        PsiDirectory psiSourceFolder = psiManager.findDirectory(sourceFolder);
        if (psiSourceFolder != null) {
          folders.add(psiSourceFolder);
        }
      }
    }

    return folders;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    Collection<File> sourceFolders = getNdkModel().getSelectedVariant().getSourceFolders();
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();

    for (File folder : sourceFolders) {
      VirtualFile virtualFile = fileSystem.findFileByIoFile(folder);
      if (virtualFile != null && isAncestor(virtualFile, file, false)) {
        return true;
      }
    }

    NdkModuleModel moduleModel = getValue();
    if (moduleModel == null) {
      return false;
    }

    return containedInIncludeFolders(moduleModel, file);
  }

  @Override
  public boolean canRepresent(Object element) {
    return GroupNodes.canRepresent(this, element);
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    presentation.addText(CPP.getName(), REGULAR_ATTRIBUTES);

    Icon icon = CPP.getIcon();
    if (icon != null) {
      presentation.setIcon(icon);
    }
    presentation.setPresentableText(CPP.getName());
  }

  @Override
  @Nullable
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return CPP.getName();
  }

  @Override
  @Nullable
  public Comparable getSortKey() {
    return CPP;
  }

  @Override
  @Nullable
  public Comparable getTypeSortKey() {
    return CPP;
  }

  /**
   * equals and hashCode in IntelliJ 'Node' classes is used to re-indentify effectively the same node in the tree across actions like
   * sync. The identity of the AndroidJniFolderNode is the single Android.mk or CMakeLists.txt. This is why only myBuildFileFolder is
   * considered to be part of the identity.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AndroidJniFolderNode that = (AndroidJniFolderNode)o;
    return Objects.equals(myBuildFileFolder, that.myBuildFileFolder);
  }

  @Override
  public int hashCode() {
    return myCachedHashCode;
  }

  @Override
  public String toString() {
    return "cpp";
  }

  @NotNull
  private NdkModuleModel getNdkModel() {
    NdkModuleModel value = getValue();
    assert value != null;
    return value;
  }
}
