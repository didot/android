/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.actions;

import com.android.SdkConstants;
import com.google.common.collect.Lists;
import com.intellij.ide.actions.OpenProjectFileChooserDescriptor;
import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.projectImport.ProjectImportProvider;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Imports a new project into Android Studio.
 * <p>
 * This action replaces the default "Import Project..." changing the behavior of project imports. If the user selects a project's root
 * directory of a Gradle project, this action will detect that the project is a Gradle project and it will direct the user to the Gradle
 * "Import Project" wizard, instead of the intermediate wizard where users can choose to import a project from existing sources. This has
 * been a source of confusion for our users.
 * <p>
 * The code in the original action cannot be extended or reused. It is implemented using static methods and the method where we change the
 * behavior is at the bottom of the call chain.
 */
public class AndroidImportProjectAction extends AnAction {
  @NonNls private static final String LAST_IMPORTED_LOCATION = "last.imported.location";

  public AndroidImportProjectAction() {
    super("Import Project...");
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    AddModuleWizard wizard = selectFileAndCreateWizard();
    if (wizard != null) {
      if (wizard.getStepCount() > 0) {
        if (!wizard.showAndGet()) {
          return;
        }
        //noinspection ConstantConditions
        NewProjectUtil.createFromWizard(wizard, null);
      }
    }
  }

  @Nullable
  private static AddModuleWizard selectFileAndCreateWizard() {
    FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, true, true, false, false) {
      FileChooserDescriptor myDelegate = new OpenProjectFileChooserDescriptor(true);
      @Override
      public Icon getIcon(VirtualFile file) {
        Icon icon = myDelegate.getIcon(file);
        return icon == null ? super.getIcon(file) : icon;
      }
    };
    descriptor.setHideIgnored(false);
    descriptor.setTitle("Select Gradle Project Import");
    String description = "Select build.gradle or settings.gradle";
    descriptor.setDescription(description);
    return selectFileAndCreateWizard(descriptor);
  }

  @Nullable
  private static AddModuleWizard selectFileAndCreateWizard(@NotNull FileChooserDescriptor descriptor) {
    FileChooserDialog chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, null, null);
    VirtualFile toSelect = null;
    String lastLocation = PropertiesComponent.getInstance().getValue(LAST_IMPORTED_LOCATION);
    if (lastLocation != null) {
      toSelect = LocalFileSystem.getInstance().refreshAndFindFileByPath(lastLocation);
    }
    VirtualFile[] files = chooser.choose(toSelect, null);
    if (files.length == 0) {
      return null;
    }
    VirtualFile file = files[0];
    PropertiesComponent.getInstance().setValue(LAST_IMPORTED_LOCATION, file.getPath());
    return createImportWizard(file);
  }

  @Nullable
  private static AddModuleWizard createImportWizard(@NotNull VirtualFile file) {
    VirtualFile target = findMatchingChild(file, SdkConstants.FN_BUILD_GRADLE, SdkConstants.FN_SETTINGS_GRADLE);
    if (target == null) {
      target = file;
    }
    List<ProjectImportProvider> available = Lists.newArrayList();
    for (ProjectImportProvider provider : ProjectImportProvider.PROJECT_IMPORT_PROVIDER.getExtensions()) {
      if (provider.canImport(target, null)) {
        available.add(provider);
      }
    }
    if (available.isEmpty()) {
      Messages.showInfoMessage("Cannot import anything from " + file.getPath(), "Cannot Import");
      return null;
    }

    String path;
    if (available.size() == 1) {
      path = available.get(0).getPathToBeImported(file);
    }
    else {
      path = ProjectImportProvider.getDefaultPath(file);
    }

    ProjectImportProvider[] availableProviders = available.toArray(new ProjectImportProvider[available.size()]);
    return new AddModuleWizard(null, path, availableProviders);
  }

  @Nullable
  private static VirtualFile findMatchingChild(@NotNull VirtualFile parent, @NotNull String...validNames) {
    if (parent.isDirectory()) {
      for (VirtualFile child : getChildrenOf(parent)) {
        for (String name : validNames) {
          if (name.equals(child.getName())) {
            return child;
          }
        }
      }
    }
    return null;
  }

  @NotNull
  private static Collection<VirtualFile> getChildrenOf(@NotNull VirtualFile file) {
    if (file instanceof NewVirtualFile) {
      return ((NewVirtualFile)file).getCachedChildren();
    }
    return Lists.newArrayList(file.getChildren());
  }

}
