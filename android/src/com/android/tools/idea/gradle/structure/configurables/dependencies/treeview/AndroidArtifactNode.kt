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
package com.android.tools.idea.gradle.structure.configurables.dependencies.treeview

import com.android.builder.model.AndroidProject
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsNode
import com.android.tools.idea.gradle.structure.model.PsChildModel
import com.android.tools.idea.gradle.structure.model.android.PsAndroidArtifact
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule
import com.intellij.ui.treeStructure.SimpleNode

class AndroidArtifactNode : AbstractPsModelNode<PsChildModel> {
  private val myChildren: List<AbstractPsModelNode<*>>

  constructor(parent: AbstractPsNode, artifact: PsAndroidArtifact) : super(parent, artifact, parent.uiSettings) {
    autoExpandNode = parent !is AndroidArtifactNode

    val additionalChildren =
      artifact
        .takeUnless { it.resolvedName == AndroidProject.ARTIFACT_MAIN }
        ?.parent
        ?.findArtifact(AndroidProject.ARTIFACT_MAIN)
        ?.let { AndroidArtifactNode(parent, it) }
    myChildren = listOfNotNull(additionalChildren) + createNodesForResolvedDependencies(this, artifact.dependencies)
  }

  constructor(parent: AbstractPsNode, javaModule: PsJavaModule) : super(parent, javaModule, parent.uiSettings) {
    autoExpandNode = parent !is AndroidArtifactNode
    myChildren = createNodesForResolvedDependencies(this, javaModule.resolvedDependencies)
  }

  override fun nameOf(artifact: PsChildModel): String {
    val variant = artifact.parent
    return variant!!.name + artifact.name
  }

  override fun getChildren(): Array<SimpleNode> {
    return myChildren.toTypedArray()
  }
}
