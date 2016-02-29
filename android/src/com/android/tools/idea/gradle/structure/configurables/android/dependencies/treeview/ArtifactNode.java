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

import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsdNode;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidArtifactModel;
import com.google.common.collect.Lists;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;

import java.util.List;

class ArtifactNode extends AbstractPsdNode<PsdAndroidArtifactModel> {
  @NotNull private List<AbstractPsdNode<?>> myChildren = Lists.newArrayList();

  ArtifactNode(@NotNull AbstractPsdNode<?> parent, @NotNull PsdAndroidArtifactModel model) {
    super(parent, model);
    setAutoExpandNode(true);
  }

  ArtifactNode(@NotNull AbstractPsdNode<?> parent, @NotNull List<PsdAndroidArtifactModel> models) {
    super(parent, models);
    setAutoExpandNode(true);
  }

  @Override
  @NotNull
  protected String nameOf(PsdAndroidArtifactModel model) {
    return model.getParent().getName() + model.getName();
  }

  @Override
  public SimpleNode[] getChildren() {
    return myChildren.toArray(new SimpleNode[myChildren.size()]);
  }

  public void setChildren(@NotNull List<AbstractPsdNode<?>> children) {
    myChildren = children;
  }
}
