/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.editor;

import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.project.FeatureEnableService;
import com.android.tools.idea.uibuilder.model.NlLayoutType;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.AndroidEditorSettings;
import org.jetbrains.annotations.NotNull;

public class NlEditorProvider implements FileEditorProvider, DumbAware {
  /**
   * FileEditorProvider ID for the layout editor
   */
  public static final String DESIGNER_ID = "android-designer2";

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    PsiFile psiFile = AndroidPsiUtils.getPsiFileSafely(project, file);
    if (!(psiFile instanceof XmlFile) || AndroidFacet.getInstance(psiFile) == null) {
      return false;
    }

    FeatureEnableService featureEnableService = FeatureEnableService.getInstance(project);
    if (featureEnableService == null || !featureEnableService.isLayoutEditorEnabled(project)) {
      return false;
    }

    return NlLayoutType.supports((XmlFile)psiFile);
  }

  @NotNull
  @Override
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    PsiFile psiFile = AndroidPsiUtils.getPsiFileSafely(project, file);
    AndroidFacet facet = psiFile instanceof XmlFile ? AndroidFacet.getInstance(psiFile) : null;
    assert facet != null; // checked by accept
    return new NlEditor(facet, file, project);
  }

  @NotNull
  @Override
  public String getEditorTypeId() {
    return DESIGNER_ID;
  }

  @NotNull
  @Override
  public FileEditorPolicy getPolicy() {
    return AndroidEditorSettings.getInstance().getGlobalState().isPreferXmlEditor()
           ? FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR
           : FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR;
  }
}
