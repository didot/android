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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview;

import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsNode;
import com.android.tools.idea.gradle.structure.model.PsChildModel;
import com.android.tools.idea.gradle.structure.model.PsModel;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidArtifact;
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.DependencyNodesKt.createNodesForResolvedDependencies;

public class AndroidArtifactNode extends AbstractPsModelNode<PsChildModel> {
  @NotNull private final List<AbstractPsModelNode<?>> myChildren;

  public AndroidArtifactNode(@NotNull AbstractPsNode parent, @NotNull PsAndroidArtifact artifact) {
    super(parent, artifact, parent.getUiSettings());
    setAutoExpandNode(!(parent instanceof AndroidArtifactNode));
    myChildren = createNodesForResolvedDependencies(this, artifact.getDependencies(), artifact);
  }

  public AndroidArtifactNode(@NotNull AbstractPsNode parent, @NotNull PsJavaModule javaModule) {
    super(parent, javaModule, parent.getUiSettings());
    setAutoExpandNode(!(parent instanceof AndroidArtifactNode));
    myChildren = createNodesForResolvedDependencies(this, javaModule.getResolvedDependencies(), null);
  }

  @Override
  @NotNull
  protected String nameOf(PsChildModel artifact) {
    PsModel variant = artifact.getParent();
    return variant.getName() + artifact.getName();
  }

  @Override
  public SimpleNode[] getChildren() {
    return myChildren.toArray(new SimpleNode[0]);
  }
}
