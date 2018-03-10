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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.graph;

import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AbstractDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.DependencyNodeComparator;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.LibraryDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.ModuleDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings;
import com.android.tools.idea.gradle.structure.configurables.ui.dependencies.PsDependencyComparator;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsResettableNode;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsModel;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.android.*;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class DependenciesTreeRootNode<T extends PsModel> extends AbstractPsResettableNode<T> {
  @NotNull private final DependencyCollectorFunction<T> myDependencyCollectorFunction;
  @NotNull private final DependencyNodeComparator myDependencyNodeComparator;

  public DependenciesTreeRootNode(@NotNull T model, @NotNull DependencyCollectorFunction<T> dependencyCollectorFunction,
                                  @NotNull PsUISettings uiSettings) {
    super(model, uiSettings);
    myDependencyCollectorFunction = dependencyCollectorFunction;
    myDependencyNodeComparator = new DependencyNodeComparator(new PsDependencyComparator(getUiSettings()));
  }

  @Override
  @NotNull
  protected List<? extends AbstractPsModelNode> createChildren() {
    T model = getFirstModel();
    DependencyCollector collector = myDependencyCollectorFunction.apply(model);

    List<AbstractDependencyNode> children = Lists.newArrayList();
    for (Map.Entry<PsArtifactDependencySpec, List<PsLibraryAndroidDependency>> entry : collector.libraryDependenciesBySpec.entrySet()) {
      LibraryDependencyNode child = new LibraryDependencyNode(this, null, entry.getValue());
      children.add(child);
    }

    for (Map.Entry<String, List<PsModuleAndroidDependency>> entry : collector.moduleDependenciesByGradlePath.entrySet()) {
      ModuleDependencyNode child = new ModuleDependencyNode(this, null, entry.getValue());
      children.add(child);
    }

    Collections.sort(children, myDependencyNodeComparator);
    return children;
  }


  public static abstract class DependencyCollectorFunction<T extends PsModel> implements Function<T, DependencyCollector> {
    protected void collectDeclaredDependencies(@NotNull PsModule module, @NotNull DependencyCollector collector) {
      if (module instanceof PsAndroidModule) {
        PsAndroidModule androidModule = (PsAndroidModule)module;
        androidModule.getDependencies().forEach(v -> {
          if (v.isDeclared()) {
            collector.add(v);
          }
        });
      }
    }
  }

  public static class DependencyCollector {
    @NotNull final Map<PsArtifactDependencySpec, List<PsLibraryAndroidDependency>> libraryDependenciesBySpec = Maps.newHashMap();
    @NotNull final Map<String, List<PsModuleAndroidDependency>> moduleDependenciesByGradlePath = Maps.newHashMap();

    void add(@NotNull PsAndroidDependency dependency) {
      if (dependency instanceof PsLibraryAndroidDependency) {
        add((PsLibraryAndroidDependency)dependency);
      }
      else if (dependency instanceof PsModuleAndroidDependency) {
        add((PsModuleAndroidDependency)dependency);
      }
    }

    private void add(@NotNull PsLibraryAndroidDependency dependency) {
      List<PsLibraryAndroidDependency> dependencies = libraryDependenciesBySpec.get(dependency.getSpec());
      if (dependencies == null) {
        dependencies = Lists.newArrayList();
        libraryDependenciesBySpec.put(dependency.getSpec(), dependencies);
      }
      dependencies.add(dependency);
    }

    private void add(@NotNull PsModuleAndroidDependency dependency) {
      String key = dependency.getGradlePath();
      List<PsModuleAndroidDependency> dependencies = moduleDependenciesByGradlePath.get(key);
      if (dependencies == null) {
        dependencies = Lists.newArrayList();
        moduleDependenciesByGradlePath.put(key, dependencies);
      }
      dependencies.add(dependency);
    }
  }
}
