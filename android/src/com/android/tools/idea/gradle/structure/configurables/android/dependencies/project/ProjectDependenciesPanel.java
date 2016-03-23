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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.project;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AbstractDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.ui.AbstractMainPanel;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

import static com.android.tools.idea.gradle.structure.configurables.android.dependencies.UiUtil.createMainVerticalSplitter;

class ProjectDependenciesPanel extends AbstractMainPanel {
  @NotNull private final JBSplitter myVerticalSplitter;
  @NotNull private final DeclaredDependenciesPanel myDeclaredDependenciesPanel;
  @NotNull private final TargetModulesPanel myTargetModulesPanel;

  ProjectDependenciesPanel(@NotNull PsProject project, @NotNull PsContext context, @NotNull List<PsModule> extraTopModules) {
    super(project, context, extraTopModules);

    myDeclaredDependenciesPanel = new DeclaredDependenciesPanel(project, context);
    myTargetModulesPanel = new TargetModulesPanel(project, context);

    myDeclaredDependenciesPanel.add(new DeclaredDependenciesPanel.SelectionListener() {
      @Override
      public void dependencySelected(@NotNull List<AbstractDependencyNode<? extends PsAndroidDependency>> selectedNodes) {
        List<PsAndroidDependency> dependencies = Lists.newArrayList();
        for (AbstractDependencyNode<?> node : selectedNodes) {
          // Only the dependencies node under the root contain all the modules that contain such dependencies. The given nodes may be
          // transitive dependencies, which do not have that information.
          // To ensure we get all the target modules, we get the "top parent" of each of the selected nodes.
          AbstractDependencyNode<? extends PsAndroidDependency> topParent = getTopParent(node);
          dependencies.addAll(topParent.getModels());
        }
        myTargetModulesPanel.displayTargetModules(dependencies);
      }
    });

    myVerticalSplitter = createMainVerticalSplitter();
    myVerticalSplitter.setFirstComponent(myDeclaredDependenciesPanel);
    myVerticalSplitter.setSecondComponent(myTargetModulesPanel);

    add(myVerticalSplitter, BorderLayout.CENTER);
  }

  @NotNull
  private static AbstractDependencyNode<?> getTopParent(AbstractDependencyNode<?> node) {
    SimpleNode current = node;
    while (true) {
      SimpleNode parent = current.getParent();
      if (parent instanceof AbstractDependencyNode) {
        current = parent;
        continue;
      }
      return (AbstractDependencyNode<?>)current;
    }
  }

  @Override
  public void dispose() {
    Disposer.dispose(myDeclaredDependenciesPanel);
    Disposer.dispose(myTargetModulesPanel);
  }
}
