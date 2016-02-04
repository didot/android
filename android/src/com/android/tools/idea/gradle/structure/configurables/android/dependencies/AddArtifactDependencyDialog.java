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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies;

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.structure.configurables.ArtifactRepositorySearchForm;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidModuleModel;
import com.android.tools.idea.gradle.structure.model.repositories.search.AndroidSdkRepository;
import com.android.tools.idea.gradle.structure.model.repositories.search.ArtifactRepository;
import com.android.tools.idea.gradle.structure.model.repositories.search.MavenCentralRepository;
import com.google.common.collect.Lists;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

class AddArtifactDependencyDialog extends DialogWrapper {
  @NotNull private final PsdAndroidModuleModel myModuleModel;

  private JPanel myPanel;
  private ArtifactRepositorySearchForm mySearchForm;

  AddArtifactDependencyDialog(@NotNull PsdAndroidModuleModel moduleModel) {
    super(moduleModel.getParent().getProject());
    myModuleModel = moduleModel;
    setTitle("Add Artifact Dependency");
    init();
  }

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    if (myPanel == null) {
      myPanel = new JPanel(new BorderLayout());
      setUpUI();
    }
    return myPanel;
  }

  @Override
  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return mySearchForm != null ? mySearchForm.getPreferredFocusedComponent() : super.getPreferredFocusedComponent();
  }

  private void setUpUI() {
    List<ArtifactRepository> repositories = Lists.newArrayList();
    repositories.add(new MavenCentralRepository()); // TODO get the repository from the build.gradle file.

    AndroidGradleModel gradleModel = myModuleModel.getGradleModel();
    AndroidProject androidProject = gradleModel.getAndroidProject();
    repositories.add(new AndroidSdkRepository(androidProject));

    mySearchForm = new ArtifactRepositorySearchForm(repositories);
    myPanel.add(mySearchForm.getPanel(), BorderLayout.CENTER);
  }
}
