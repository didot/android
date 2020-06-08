/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.importing;

import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.projectImport.ProjectImportProvider;
import icons.AndroidIcons;
import java.util.Collections;
import java.util.List;
import javax.swing.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link ProjectImportProvider} with ability to import Android Gradle projects.
 * This is used by VCS when checking out a project and delegates to our {@link  GradleProjectImporter}.
 */
public final class AndroidGradleProjectImportProvider extends ProjectImportProvider {
  @Override
  protected ProjectImportBuilder doGetBuilder() {
    return ProjectImportBuilder.EXTENSIONS_POINT_NAME.findExtensionOrFail(AndroidGradleImportBuilder.class);
  }

  public static final class AndroidGradleImportBuilder extends ProjectImportBuilder<String> {
    @NotNull
    @Override
    public String getName() {
      return "Android Gradle";
    }

    @Override
    public Icon getIcon() {
      return AndroidIcons.Android;
    }

    @Override
    public boolean isMarked(String element) {
      return false;
    }

    @Override
    public void setOpenProjectSettingsAfter(boolean on) {
    }

    @Nullable
    @Override
    public List<Module> commit(Project project,
                               ModifiableModuleModel model,
                               ModulesProvider modulesProvider,
                               ModifiableArtifactModel artifactModel) {
      GradleProjectImporter.getInstance().importProjectCore(project.getBaseDir(), project);
      return Collections.emptyList();
    }
  }
}
