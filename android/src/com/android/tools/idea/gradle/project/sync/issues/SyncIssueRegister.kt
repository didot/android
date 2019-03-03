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
package com.android.tools.idea.gradle.project.sync.issues

import com.android.builder.model.SyncIssue
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

/**
 * A project based component that stores a map from modules to sync issues. These are registered during sync (module setup) and are reported
 * shortly afterward. The map is cleared at the start of each sync.
 */
class SyncIssueRegister {
  private val syncIssueMap: MutableMap<Module, MutableList<SyncIssue>> = HashMap()

  fun register(module: Module, syncIssues: Collection<SyncIssue>) {
    syncIssueMap.computeIfAbsent(module) { ArrayList() }.addAll(syncIssues)
    Disposer.register(module, Disposable { syncIssueMap.remove(module) })
  }

  fun getAndClear(): Map<Module, List<SyncIssue>> {
    return syncIssueMap.toMap().also { syncIssueMap.clear() }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): SyncIssueRegister {
      return ServiceManager.getService(project, SyncIssueRegister::class.java)
    }
  }
}
