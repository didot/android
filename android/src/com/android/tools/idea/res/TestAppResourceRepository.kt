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
package com.android.tools.idea.res

import com.android.ide.common.util.PathString
import com.android.projectmodel.ExternalLibrary
import com.android.projectmodel.RecursiveResourceFolder
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.res.aar.AarResourceRepositoryCache
import com.android.tools.idea.util.androidFacet
import org.jetbrains.android.facet.AndroidFacet
import java.io.File

class TestAppResourceRepository private constructor(
  facet: AndroidFacet,
  subRepositories: List<LocalResourceRepository>
) : MultiResourceRepository(facet.module.name) {

  init {
    children = subRepositories
  }

  companion object {
    @JvmStatic
    fun create(
      facet: AndroidFacet,
      moduleTestResources: LocalResourceRepository,
      model: AndroidModuleModel
    ): TestAppResourceRepository {
      val project = facet.module.project
      val subRepositories = mutableListOf(moduleTestResources)

      val dependencies = model.selectedAndroidTestCompileDependencies
      if (dependencies != null) {
        subRepositories.addAll(
          dependencies.moduleDependencies.asSequence()
            .mapNotNull { it.projectPath }
            .mapNotNull { GradleUtil.findModuleByGradlePath(project, it) }
            .mapNotNull { it.androidFacet }
            .map(ModuleResourceRepository::forMainResources)
        )

        val aarCache = AarResourceRepositoryCache.getInstance()
        subRepositories.addAll(
          dependencies.androidLibraries.asSequence()
            .map { aarCache.getSourceRepository(
              ExternalLibrary(address = it.artifactAddress,
                              resFolder = RecursiveResourceFolder(PathString(it.resFolder)))) }
        )
      }

      if (facet.configuration.isLibraryProject) {
        // In library projects, there's only one APK when testing and the test R class contains all resources.
        subRepositories += ResourceRepositoryManager.getAppResources(facet)
      }

      return TestAppResourceRepository(facet, subRepositories)
    }
  }
}
