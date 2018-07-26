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
package com.android.tools.idea.gradle.structure.configurables.android.buildvariants.buildtypes

import com.android.tools.idea.gradle.structure.configurables.ConfigurablesTreeModel
import com.android.tools.idea.gradle.structure.configurables.NamedContainerConfigurableBase
import com.android.tools.idea.gradle.structure.configurables.createConfigurablesTree
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.PsBuildType
import com.intellij.openapi.ui.NamedConfigurable
import javax.swing.tree.DefaultMutableTreeNode

class BuildTypesTreeModel(
    module: PsAndroidModule,
    rootNode: DefaultMutableTreeNode
) : ConfigurablesTreeModel(module, rootNode) {

  fun createBuildType(newName: String): DefaultMutableTreeNode? {
    val buildType = module.addNewBuildType(newName)
    val configurable = BuildTypeConfigurable(buildType)
    return createNode(rootNode, configurable)
  }

  fun removeBuildType(node: DefaultMutableTreeNode) {
    val buildTypeConfigurable = node.userObject as BuildTypeConfigurable
    val buildType = buildTypeConfigurable.model
    module.removeBuildType(buildType)
    removeNodeFromParent(node)
  }
}

fun createBuildTypesModel(module: PsAndroidModule): BuildTypesTreeModel =
    BuildTypesTreeModel(
        module,
        createConfigurablesTree(
            object : NamedContainerConfigurableBase<PsBuildType>("Build Types") {
              override fun getChildren(): List<NamedConfigurable<PsBuildType>> = module.buildTypes.map(::BuildTypeConfigurable)
            }
        ))
