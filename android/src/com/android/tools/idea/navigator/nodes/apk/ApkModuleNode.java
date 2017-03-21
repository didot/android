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
package com.android.tools.idea.navigator.nodes.apk;

import com.android.tools.idea.apk.ApkFacet;
import com.android.tools.idea.apk.viewer.ApkFileSystem;
import com.android.tools.idea.navigator.nodes.android.AndroidManifestsGroupNode;
import com.android.tools.idea.navigator.nodes.apk.java.DexGroupNode;
import com.android.tools.idea.navigator.nodes.apk.ndk.LibraryGroupNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ProjectViewModuleNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.FN_APK_CLASSES_DEX;
import static com.android.tools.idea.gradle.util.FilePaths.toSystemDependentPath;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

public class ApkModuleNode extends ProjectViewModuleNode {
  @NotNull private final AndroidFacet myAndroidFacet;
  @NotNull private final ApkFacet myApkFacet;

  @Nullable private final PsiFile myApkPsiFile;
  @Nullable private final VirtualFile myApkFile;
  @Nullable private final VirtualFile myManifestFile;
  @Nullable private final VirtualFile myDexFile;

  private DexGroupNode myDexGroupNode;

  public ApkModuleNode(@NotNull Project project,
                       @NotNull Module module,
                       @NotNull AndroidFacet androidFacet,
                       @NotNull ApkFacet apkFacet,
                       @NotNull ViewSettings settings) {
    super(project, module, settings);
    myAndroidFacet = androidFacet;

    myApkFacet = apkFacet;
    myApkPsiFile = findApkPsiFile();
    myApkFile = myApkPsiFile != null ? myApkPsiFile.getVirtualFile() : null;
    VirtualFile apkRootFile = myApkFile != null ? ApkFileSystem.getInstance().getRootByLocal(myApkFile) : null;

    VirtualFile rootFolder = findModuleRootFolder();
    myManifestFile = rootFolder != null ? rootFolder.findChild(FN_ANDROID_MANIFEST_XML) : null;
    myDexFile = apkRootFile != null ? apkRootFile.findChild(FN_APK_CLASSES_DEX) : null;
  }

  @Nullable
  private VirtualFile findModuleRootFolder() {
    File moduleFilePath = toSystemDependentPath(getModule().getModuleFilePath());
    File modulePath = moduleFilePath.getParentFile();
    return findFileByIoFile(modulePath, false /* do not refresh file system */);
  }

  @Nullable
  private PsiFile findApkPsiFile() {
    String apkPath = myApkFacet.getConfiguration().APK_PATH;
    if (isNotEmpty(apkPath)) {
      File apkFilePath = new File(toSystemDependentName(apkPath));
      if (apkFilePath.isFile()) {
        VirtualFile apkFile = findFileByIoFile(apkFilePath, true);
        if (apkFile != null) {
          assert myProject != null;
          return PsiManager.getInstance(myProject).findFile(apkFile);
        }
      }
    }
    return null;
  }

  @Override
  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    assert myProject != null;

    ViewSettings settings = getSettings();
    List<AbstractTreeNode> children = new ArrayList<>();
    if (myApkPsiFile != null) {
      children.add(new PsiFileNode(myProject, myApkPsiFile, settings));
    }
    // "manifests" folder
    children.add(createManifestGroupNode());

    // "java" folder
    if (myDexGroupNode == null) {
      myDexGroupNode = new DexGroupNode(myProject, settings, myDexFile);
    }
    children.add(myDexGroupNode);

    // "Native libraries" folder
    VirtualFile found = myProject.getBaseDir().findChild("lib");
    if (found != null && found.isDirectory()) {
      children.add(new LibraryGroupNode(myProject, found, settings));
    }

    return children;
  }

  @NotNull
  private AndroidManifestsGroupNode createManifestGroupNode() {
    assert myProject != null;
    Set<VirtualFile> manifestFiles = myManifestFile != null ? Collections.singleton(myManifestFile) : Collections.emptySet();
    return new AndroidManifestsGroupNode(myProject, myAndroidFacet, getSettings(), manifestFiles);
  }

  @Override
  @Nullable
  public Comparable getSortKey() {
    return getModule().getName();
  }

  @Override
  @Nullable
  public Comparable getTypeSortKey() {
    return getSortKey();
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return "APK Module";
  }

  @NotNull
  private Module getModule() {
    Module module = getValue();
    assert module != null;
    return module;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    // This is needed when user selects option "Autoscroll from Source". When user selects manifest in editor, the manifest must be selected
    // automatically in the "Android" view.
    String path = file.getPath();
    if (myApkFile != null && Objects.equals(path, myApkFile.getPath())) {
      return true;
    }
    if (Objects.equals(path, getManifestPath())) {
      return true;
    }
    if (myDexGroupNode != null && myDexGroupNode.contains(file)) {
      return true;
    }
    return false;
  }

  @Nullable
  private String getManifestPath() {
    return myManifestFile != null ? myManifestFile.getPath() : null;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    return super.equals(o);
  }
}
