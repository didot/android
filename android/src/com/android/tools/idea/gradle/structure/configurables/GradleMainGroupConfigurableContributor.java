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
package com.android.tools.idea.gradle.structure.configurables;

import com.android.tools.idea.gradle.structure.configurables.android.buildtypes.BuildTypesPerspectiveConfigurable;
import com.android.tools.idea.gradle.structure.model.PsdProjectModel;
import com.android.tools.idea.structure.dialog.MainGroupConfigurableContributor;
import com.google.common.collect.Lists;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GradleMainGroupConfigurableContributor extends MainGroupConfigurableContributor {
  @Override
  @NotNull
  public List<Configurable> getConfigurables(@NotNull Project project) {
    PsdProjectModel projectModel = new PsdProjectModel(project);

    List<Configurable> configurables = Lists.newArrayList();
    configurables.add(new DependenciesPerspectiveConfigurable(projectModel));
    configurables.add(new BuildTypesPerspectiveConfigurable(projectModel));

    return configurables;
  }
}
