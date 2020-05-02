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
package com.android.tools.idea.gradle.structure

import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.model.GradleModuleModel
import com.android.tools.idea.gradle.project.model.JavaModuleModel
import com.android.tools.idea.gradle.project.sync.GradleModuleModels
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.structure.model.PsResolvedModuleModel
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListenableFutureTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

class GradleResolver {
  /**
   * Requests Gradle models without updating IDE projects and returns the [ListenableFuture] of the requested models.
   */
  fun requestProjectResolved(project: Project, disposable: Disposable): ListenableFuture<List<PsResolvedModuleModel>> {
    val future = ListenableFutureTask.create {
      GradleSyncInvoker
        .getInstance()
        .fetchGradleModels(project)
        .mapNotNull { findModel(it) }
    }
    object : Task.Backgroundable(project, "Fetching build models", true) {
      override fun run(indicator: ProgressIndicator) {
        future.run()
      }
    }.queue()

    Disposer.register(disposable, Disposable { future.cancel(true) })
    return future
  }
}

private fun findModel(module: GradleModuleModels): PsResolvedModuleModel? {
  val gradleModuleModel = module.findModel(GradleModuleModel::class.java) ?: return null
  val gradlePath = gradleModuleModel.gradlePath
  return module.findModel(AndroidModuleModel::class.java)
             ?.let { PsResolvedModuleModel.PsAndroidModuleResolvedModel(gradlePath, gradleModuleModel.buildFilePath?.absolutePath, it) }
         ?: module.findModel(JavaModuleModel::class.java)
             ?.let { PsResolvedModuleModel.PsJavaModuleResolvedModel(gradlePath, gradleModuleModel.buildFilePath?.absolutePath, it) }
}
