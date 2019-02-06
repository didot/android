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

package com.android.tools.idea.gradle.notification;

import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.projectsystem.FilenameConstants;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.GeneratedSourceFileChangeTracker;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;

public final class GeneratedFileNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
  private static final Key<EditorNotificationPanel> KEY = Key.create("android.generated.file.ro");

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor, @NotNull Project project) {
    return createNotificationPanel(file, project, GradleProjectInfo.getInstance(project));
  }

  @Nullable
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file,
                                                         @NotNull Project project,
                                                         @NotNull GradleProjectInfo projectInfo) {
    AndroidModuleModel androidModel = projectInfo.findAndroidModelInModule(file, false /* include excluded files */);
    if (androidModel == null) {
      return null;
    }

    File buildFolderPath = androidModel.getAndroidProject().getBuildFolder();
    VirtualFile buildFolder = findFileByIoFile(buildFolderPath, false /* do not refresh */);
    if (buildFolder == null || !buildFolder.isDirectory()) {
      return null;
    }

    if (isAncestor(buildFolder, file, false /* not strict */)) {
      if (GeneratedSourceFileChangeTracker.getInstance(project).isEditedGeneratedFile(file)) {
        // A warning is already being displayed by GeneratedFileEditingNotificationProvider
        return null;
      }

      VirtualFile explodedBundled = buildFolder.findChild(FilenameConstants.EXPLODED_AAR);
      boolean inAar = explodedBundled != null && isAncestor(explodedBundled, file, true /* strict */);
      String text;
      if (inAar) {
        text = "Resource files inside Android library archive files (.aar) should not be edited";
      }
      else {
        text = "Files under the \"build\" folder are generated and should not be edited.";
      }

      return new MyEditorNotificationPanel(text);
    }
    return null;
  }

  @VisibleForTesting
  static class MyEditorNotificationPanel extends EditorNotificationPanel {
    MyEditorNotificationPanel(@NotNull String text) {
      super();
      setText(text);
    }

    @VisibleForTesting
    @Nullable
    String getText() {
      return myLabel.getText();
    }
  }
}
