/*
 * Copyright (C) 2018 The Android Open Source Project
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
@file:JvmName("SyncIssues")
package com.android.tools.idea.gradle.project.sync.issues

import com.android.builder.model.SyncIssue
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.SYNC_ISSUE
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.find
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findAll
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findProjectData
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.util.GradleConstants

@JvmName("forModule")
fun Module.syncIssues() : List<SyncIssueData> {
  val linkedProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(this) ?: return emptyList()
  val projectDataNode = findProjectData(project, GradleConstants.SYSTEM_ID, linkedProjectPath) ?: return emptyList()
  val moduleDataNode = find(projectDataNode, ProjectKeys.MODULE) { node ->
    node.data.internalName == name
  } ?: return emptyList()
  return findAll(moduleDataNode, SYNC_ISSUE).map { dataNode -> dataNode.data }
}

data class SyncIssueData(
  val message: String,
  val data: String?,
  val multiLineMessage: List<String>?,
  val severity: Int,
  val type: Int
)

class SyncIssueDataService : AbstractProjectDataService<SyncIssueData, Void>() {
  override fun importData(toImport: Collection<DataNode<SyncIssueData>>,
                          projectData: ProjectData?,
                          project: Project,
                          modelsProvider: IdeModifiableModelsProvider) {
    val moduleToSyncIssueMap : MutableMap<Module, List<SyncIssue>> = mutableMapOf()
    ExternalSystemApiUtil.groupBy(toImport, ModuleData::class.java).entrySet().forEach { (moduleNode, syncIssues) ->
      val module = modelsProvider.findIdeModule(moduleNode.data) ?: return@forEach
      // TODO: Make the reporter handle SyncIssueData instead, but for now to just use an adapter.
      val mappedSyncIssues : List<SyncIssue> = syncIssues.map { node -> object : SyncIssue {
        override val severity: Int = node.data.severity
        override val type: Int = node.data.type
        override val data: String? = node.data.data
        override val message: String = node.data.message
        override val multiLineMessage: List<String>? = node.data.multiLineMessage
      }}
      moduleToSyncIssueMap[module] = mappedSyncIssues
    }
    SyncIssuesReporter.getInstance().report(moduleToSyncIssueMap)
  }

  override fun getTargetDataKey(): Key<SyncIssueData> {
    return SYNC_ISSUE
  }
}