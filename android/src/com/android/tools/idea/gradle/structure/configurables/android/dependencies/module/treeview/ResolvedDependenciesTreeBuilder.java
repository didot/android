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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.module.treeview;

import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AbstractBaseTreeBuilder;
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;

public class ResolvedDependenciesTreeBuilder extends AbstractBaseTreeBuilder {
  @NotNull private final DependencySelection myDependencySelectionSource;
  @NotNull private final DependencySelection myDependencySelectionDestination;

  public ResolvedDependenciesTreeBuilder(@NotNull PsAndroidModule module,
                                         @NotNull JTree tree,
                                         @NotNull DefaultTreeModel treeModel,
                                         @NotNull DependencySelection dependencySelectionSource,
                                         @NotNull DependencySelection dependencySelectionDestination) {
    super(tree, treeModel, new ResolvedDependenciesTreeStructure(module));
    myDependencySelectionSource = dependencySelectionSource;
    myDependencySelectionDestination = dependencySelectionDestination;

    PsUISettings.ChangeListener changeListener = new PsUISettings.ChangeListener() {
      @Override
      public void settingsChanged(@NotNull PsUISettings settings) {
        AbstractTreeStructure treeStructure = getTreeStructure();

        if (treeStructure instanceof ResolvedDependenciesTreeStructure) {
          final PsAndroidDependency selected = myDependencySelectionSource.getSelection();

          boolean needsUpdate = ((ResolvedDependenciesTreeStructure)treeStructure).settingsChanged();

          if (needsUpdate) {
            ActionCallback actionCallback = ResolvedDependenciesTreeBuilder.this.queueUpdate();
            actionCallback.doWhenDone(new Runnable() {
              @Override
              public void run() {
                myDependencySelectionDestination.setSelection(selected);
              }
            });
          }
        }
      }
    };
    PsUISettings.getInstance().addListener(changeListener, this);
  }

  @Override
  protected void onAllNodesExpanded() {
    getReady(this).doWhenDone(new Runnable() {
      @Override
      public void run() {
        PsAndroidDependency selection = myDependencySelectionSource.getSelection();
        myDependencySelectionDestination.setSelection(selection);
      }
    });
  }
}
