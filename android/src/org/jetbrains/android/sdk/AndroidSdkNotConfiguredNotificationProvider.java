// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.sdk;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public final class AndroidSdkNotConfiguredNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
  private static final Key<EditorNotificationPanel> KEY = Key.create("android.sdk.not.configured.notification");

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor, @NotNull Project project) {
    if (file.getFileType() != XmlFileType.INSTANCE) {
      return null;
    }
    final Module module = ModuleUtilCore.findModuleForFile(file, project);
    final AndroidFacet facet = module != null ? AndroidFacet.getInstance(module) : null;

    if (facet == null) {
      return null;
    }
    if (!facet.requiresAndroidModel()
        && (AndroidResourceUtil.isResourceFile(file, facet) || file.equals(AndroidRootUtil.getPrimaryManifestFile(facet)))) {
      final AndroidPlatform platform = AndroidPlatform.getInstance(module);

      if (platform == null) {
        return new MySdkNotConfiguredNotificationPanel(module);
      }
    }
    return null;
  }

  private static final class MySdkNotConfiguredNotificationPanel extends EditorNotificationPanel {
    MySdkNotConfiguredNotificationPanel(@NotNull final Module module) {
      setText("Android SDK is not configured for module '" + module.getName() + "' or corrupted");

      createActionLabel("Open Project Structure", new Runnable() {
        @Override
        public void run() {
          ModulesConfigurator.showDialog(module.getProject(), module.getName(), ClasspathEditor.NAME);
          EditorNotifications.getInstance(module.getProject()).updateAllNotifications();
        }
      });
    }
  }
}

