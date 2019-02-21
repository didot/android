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
package com.android.tools.idea.editors.manifest;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.variant.view.BuildVariantUpdater;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.android.tools.idea.model.MergedManifestManager;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;

import static com.android.tools.idea.projectsystem.ProjectSystemSyncUtil.PROJECT_SYSTEM_SYNC_TOPIC;

public class ManifestEditor extends UserDataHolderBase implements FileEditor {

  private final AndroidFacet myFacet;
  private JPanel myLazyContainer;
  private ManifestPanel myManifestPanel;
  private final VirtualFile mySelectedFile;
  private boolean mySelected;
  private final PsiTreeChangeListener myPsiChangeListener = new PsiTreeChangeAdapter() {
    @Override
    public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
      psiChange(event);
    }

    @Override
    public void childAdded(@NotNull PsiTreeChangeEvent event) {
      psiChange(event);
    }

    @Override
    public void childRemoved(@NotNull PsiTreeChangeEvent event) {
      psiChange(event);
    }

    @Override
    public void childReplaced(@NotNull PsiTreeChangeEvent event) {
      psiChange(event);
    }

    @Override
    public void childMoved(@NotNull PsiTreeChangeEvent event) {
      psiChange(event);
    }

    @Override
    public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
      psiChange(event);
    }
  };
  private final BuildVariantView.BuildVariantSelectionChangeListener buildVariantListener = this::reload;

  ManifestEditor(@NotNull AndroidFacet facet, @NotNull VirtualFile manifestFile) {
    myFacet = facet;
    mySelectedFile = manifestFile;
    myLazyContainer = new JPanel(new BorderLayout());
  }

  private void psiChange(PsiTreeChangeEvent event) {
    PsiFile file = event.getFile();
    // Assume that all android manifest files have the same filename
    if (file != null && SdkConstants.FN_ANDROID_MANIFEST_XML.equals(file.getName())) {
      // This method may still hold a writelock, and we are doing a UI update.
      // Avoid deadlocks, break out of writelock psi/thread for UI update.
      ApplicationManager.getApplication().invokeLater(this::reload);
    }
  }

  private void reload() {
    if (mySelected) {
      myManifestPanel.setManifestSnapshot(MergedManifestManager.getSnapshot(myFacet), mySelectedFile);
    }
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myLazyContainer;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  @NotNull
  @Override
  public String getName() {
    return "Merged Manifest";
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void selectNotify() {
    mySelected = true;

    final Project project = myFacet.getModule().getProject();
    if (myManifestPanel == null) {
      myManifestPanel = new ManifestPanel(myFacet);
      myLazyContainer.add(myManifestPanel);
      // Parts of the merged manifest come from the project's build model, so we want to know
      // if that changes so we can get the latest values.
      project.getMessageBus().connect(this).subscribe(PROJECT_SYSTEM_SYNC_TOPIC, result -> {
        if (result == ProjectSystemSyncManager.SyncResult.FAILURE || result == ProjectSystemSyncManager.SyncResult.SUCCESS) {
          reload();
        }
      });
    }

    PsiManager.getInstance(project).addPsiTreeChangeListener(myPsiChangeListener);
    BuildVariantUpdater.getInstance(project).addSelectionChangeListener(buildVariantListener);
    reload();
  }

  @Override
  public void deselectNotify() {
    mySelected = false;
    final Project thisProject = myFacet.getModule().getProject();
    PsiManager.getInstance(thisProject).removePsiTreeChangeListener(myPsiChangeListener);
    BuildVariantUpdater.getInstance(thisProject).removeSelectionChangeListener(buildVariantListener);
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Nullable
  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  @Nullable
  @Override
  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Nullable
  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }

  @Override
  public void dispose() {
  }
}
