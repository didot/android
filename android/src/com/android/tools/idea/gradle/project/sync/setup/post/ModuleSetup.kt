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
package com.android.tools.idea.gradle.project.sync.setup.post

import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.configuration.compilerArgumentsBySourceSet
import org.jetbrains.kotlin.idea.configuration.configureFacetByCompilerArguments
import org.jetbrains.kotlin.idea.configuration.sourceSetName
import org.jetbrains.kotlin.idea.facet.KotlinFacet

class ModuleSetup @VisibleForTesting
internal constructor(private val myProject: Project, vararg setupSteps: ModuleSetupStep) {
  private val mySetupSteps: Array<out ModuleSetupStep> = setupSteps

  constructor(project: Project) : this(project, *ModuleSetupStep.getExtensions()) {}

  fun setUpModules(progressIndicator: ProgressIndicator?) {
    for (module in ModuleManager.getInstance(myProject).modules) {
      for (setupStep in mySetupSteps) {
        setupStep.setUpModule(module, progressIndicator)
      }
      setupKotlinOptionsOnFacet(module)
    }
  }

  // Added due to KT-19958
  private fun setupKotlinOptionsOnFacet(module: Module) {
    val facet = AndroidFacet.getInstance(module) ?: return
    val androidModel = AndroidModuleModel.get(facet) ?: return
    val sourceSetName = androidModel.selectedVariant.name
    if (module.sourceSetName == sourceSetName) return
    val argsInfo = module.compilerArgumentsBySourceSet?.get(sourceSetName) ?: return
    val kotlinFacet = KotlinFacet.get(module) ?: return
    module.sourceSetName = sourceSetName
    configureFacetByCompilerArguments(kotlinFacet, argsInfo, null)
  }
}
