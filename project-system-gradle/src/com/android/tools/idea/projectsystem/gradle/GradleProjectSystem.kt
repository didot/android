/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.projectsystem.gradle

import com.android.AndroidProjectTypes.PROJECT_TYPE_APP
import com.android.builder.model.SourceProvider
import com.android.tools.apk.analyzer.AaptInvoker
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModelHandler
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.SourceProviders
import com.android.tools.idea.projectsystem.SourceProvidersFactory
import com.android.tools.idea.res.AndroidInnerClassFinder
import com.android.tools.idea.res.AndroidManifestClassPsiElementFinder
import com.android.tools.idea.res.AndroidResourceClassPsiElementFinder
import com.android.tools.idea.res.ProjectLightResourceClassService
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.templates.GradleFilePsiMerger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElementFinder
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.SourceProvidersImpl
import org.jetbrains.android.facet.createIdeaSourceProviderFromModelSourceProvider
import org.jetbrains.android.facet.createSourceProvidersForLegacyModule
import java.nio.file.Path

class GradleProjectSystem(val project: Project) : AndroidProjectSystem {
  private val moduleHierarchyProvider: GradleModuleHierarchyProvider = GradleModuleHierarchyProvider(project)
  private val mySyncManager: ProjectSystemSyncManager = GradleProjectSystemSyncManager(project)
  private val myProjectBuildModelHandler: ProjectBuildModelHandler = ProjectBuildModelHandler.getInstance(project)

  private val myPsiElementFinders: List<PsiElementFinder> = run {
    listOf(
      AndroidInnerClassFinder.INSTANCE,
      AndroidManifestClassPsiElementFinder.getInstance(project),
      AndroidResourceClassPsiElementFinder(getLightResourceClassService())
    )
  }

  override fun getSyncManager(): ProjectSystemSyncManager = mySyncManager

  override fun getPathToAapt(): Path {
    return AaptInvoker.getPathToAapt(AndroidSdks.getInstance().tryToChooseSdkHandler(), LogWrapper(GradleProjectSystem::class.java))
  }

  override fun allowsFileCreation() = true

  override fun getDefaultApkFile(): VirtualFile? {
    return ModuleManager.getInstance(project).modules.asSequence()
        .mapNotNull { AndroidModuleModel.get(it) }
        .filter { it.androidProject.projectType == PROJECT_TYPE_APP }
        .flatMap { it.selectedVariant.mainArtifact.outputs.asSequence() }
        .map { it.mainOutputFile.outputFile }
        .find { it.exists() }
        ?.let { VfsUtil.findFileByIoFile(it, true) }
  }

  override fun buildProject() {
    GradleProjectBuilder.getInstance(project).compileJava()
  }

  override fun mergeBuildFiles(dependencies: String, destinationContents: String, supportLibVersionFilter: String?): String {
    return GradleFilePsiMerger.mergeGradleFiles(dependencies, destinationContents, project, supportLibVersionFilter)
  }

  override fun getModuleSystem(module: Module): AndroidModuleSystem {
    return GradleModuleSystem(module, myProjectBuildModelHandler, moduleHierarchyProvider.createForModule(module))
  }

  override fun getPsiElementFinders(): List<PsiElementFinder> = myPsiElementFinders

  override fun getLightResourceClassService() = ProjectLightResourceClassService.getInstance(project)

  override val submodules: Collection<Module>
    get() = moduleHierarchyProvider.forProject.submodules

  override fun getSourceProvidersFactory(): SourceProvidersFactory = object : SourceProvidersFactory {
    override fun createSourceProvidersFor(facet: AndroidFacet): SourceProviders? {
      val model = AndroidModuleModel.get(facet)
      return if (model != null) createSourceProvidersFromModel(model) else createSourceProvidersForLegacyModule(facet)
    }
  }
}

fun createSourceProvidersFromModel(model: AndroidModuleModel): SourceProviders {
  val all =
    @Suppress("DEPRECATION")
    (
      model.allSourceProviders.asSequence() +
      model.activeSourceProviders.asSequence() +
      model.testSourceProviders.asSequence() +
      model.defaultSourceProvider +
      (model as? AndroidModuleModel)?.flavorSourceProviders?.asSequence().orEmpty()
    )
      .toSet()
      .associateWith { createIdeaSourceProviderFromModelSourceProvider(it) }

  fun SourceProvider.toIdeaSourceProvider() = all.getValue(this)

  return SourceProvidersImpl(
    mainIdeaSourceProvider = model.defaultSourceProvider.toIdeaSourceProvider(),
    currentSourceProviders = @Suppress("DEPRECATION") model.activeSourceProviders.map { it.toIdeaSourceProvider() },
    currentTestSourceProviders = @Suppress("DEPRECATION") model.testSourceProviders.map { it.toIdeaSourceProvider() },
    allSourceProviders = @Suppress("DEPRECATION") model.allSourceProviders.map { it.toIdeaSourceProvider() },
    mainAndFlavorSourceProviders =
    (model as? AndroidModuleModel)?.let { androidModuleModel ->
      listOf(model.defaultSourceProvider.toIdeaSourceProvider()) +
      @Suppress("DEPRECATION") androidModuleModel.flavorSourceProviders.map { it.toIdeaSourceProvider() }
    }
    ?: emptyList()
  )
}

