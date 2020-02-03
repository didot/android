// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.navigator.nodes;

import com.android.tools.idea.navigator.AndroidProjectViewPane;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ProjectViewModuleNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import java.util.Collection;
import java.util.Objects;
import kotlin.collections.CollectionsKt;
import org.jetbrains.annotations.NotNull;

/**
 * Specialization of {@link ProjectViewModuleNode} for Android view.
 */
public abstract class AndroidViewModuleNode extends ProjectViewModuleNode {
  @NotNull protected final AndroidProjectViewPane myProjectViewPane;

  public AndroidViewModuleNode(@NotNull Project project,
                               @NotNull Module value,
                               @NotNull AndroidProjectViewPane projectViewPane,
                               ViewSettings viewSettings) {
    super(project, value, viewSettings);
    myProjectViewPane = projectViewPane;
  }

  /**
   * @return module children except of its sub-modules.
   */
  @NotNull
  protected abstract Collection<AbstractTreeNode<?>> getModuleChildren();

  /**
   * Provides access to the platform's {@link ProjectViewModuleNode#getChildren}.
   */
  @NotNull
  protected final Collection<AbstractTreeNode<?>> platformGetChildren() {
    return super.getChildren();
  }

  /**
   * {@inheritDoc}
   * Final. Please override {@link #getModuleChildren()} }.
   */
  @NotNull
  @Override
  public final Collection<AbstractTreeNode<?>> getChildren() {
    return CollectionsKt.plus(
      ModuleNodeUtils.createChildModuleNodes(Objects.requireNonNull(getProject()), getValue(), myProjectViewPane, getSettings()),
      getModuleChildren());
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    // All flavors of AndroidViewModuleNode representing the same module are considered equal (http://b/70635980).
    if (!(o instanceof AndroidViewModuleNode)) {
      return false;
    }
    return Comparing.equal(getEqualityObject(), ((AndroidViewModuleNode)o).getEqualityObject());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getEqualityObject());
  }
}
