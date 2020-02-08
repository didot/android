/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea

import com.android.SdkConstants.ANDROIDX_ANNOTATIONS_ARTIFACT
import com.android.SdkConstants.ANNOTATIONS_LIB_ARTIFACT
import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.FD_RES
import com.android.SdkConstants.FN_ANNOTATIONS_ZIP
import com.android.SdkConstants.FN_FRAMEWORK_LIBRARY
import com.android.builder.model.level2.Library
import com.android.builder.model.level2.Library.LIBRARY_ANDROID
import com.android.builder.model.level2.Library.LIBRARY_JAVA
import com.android.ide.common.gradle.model.IdeBaseArtifact
import com.android.ide.common.gradle.model.IdeVariant
import com.android.ide.common.repository.GradleCoordinate
import com.android.tools.idea.gradle.LibraryFilePaths
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys
import com.android.tools.idea.io.FilePaths
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.LibraryData
import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData
import com.intellij.openapi.externalSystem.model.project.LibraryLevel
import com.intellij.openapi.externalSystem.model.project.LibraryPathType
import com.intellij.openapi.externalSystem.model.project.LibraryPathType.BINARY
import com.intellij.openapi.externalSystem.model.project.LibraryPathType.DOC
import com.intellij.openapi.externalSystem.model.project.LibraryPathType.SOURCE
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ModuleDependencyData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtil.filesEqual
import com.intellij.openapi.util.io.FileUtil.getNameWithoutExtension
import com.intellij.openapi.util.io.FileUtil.sanitizeFileName
import com.intellij.openapi.util.io.FileUtil.toSystemDependentName
import com.intellij.openapi.util.io.FileUtil.toSystemIndependentName
import org.gradle.tooling.model.UnsupportedMethodException
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.plugins.gradle.model.data.CompositeBuildData
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.linkProjectLibrary
import org.jetbrains.plugins.gradle.settings.GradleExecutionWorkspace
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import java.io.File.separatorChar

internal val RESOLVER_LOG = Logger.getInstance(AndroidGradleProjectResolver::class.java)

typealias SourcesPath = File?
typealias JavadocPath = File?
typealias ArtifactId = String
typealias ArtifactPath = File
data class AdditionalArtifactsPaths(val sources: SourcesPath, val javadoc: JavadocPath)

/**
 * Sets up the [LibraryDependencyData] and [ModuleDependencyData] on the receiving [ModuleData] node.
 *
 * This uses the information provided in the given [variant] if no variant is given then the selected
 * variant from the [AndroidModuleModel] is used. This method assumes that this module has an attached
 * [AndroidModuleModel] data node (given by the key [AndroidProjectKeys.ANDROID_MODEL]).
 *
 * [additionalArtifactsMapper] is used to obtain the respective sources and Javadocs which are attached to the
 * libraries. TODO: Replace with something that makes the call sites nicer and shouldn't rely on the project object.
 *
 * The [idToModuleData] map must be provided and must correctly map module ids created in the same form
 * as [GradleProjectResolverUtil.getModuleId] to the [ModuleData]. This is used to set up
 * [ModuleDependencyData].
 */
@JvmOverloads
fun DataNode<ModuleData>.setupAndroidDependenciesForModule(
  idToModuleData: (String) -> ModuleData?,
  additionalArtifactsMapper: (ArtifactId, ArtifactPath) -> AdditionalArtifactsPaths,
  variant: IdeVariant? = null
) {
  val androidModel = ExternalSystemApiUtil.find(this, AndroidProjectKeys.ANDROID_MODEL)?.data ?: return // TODO: Error here
  // The DataNode tree should have a ProjectData node as a parent of the ModuleData node. We don't throw an
  // exception here as other intellij plugins can manipulate the tree, we do not want to break an import
  // completely due to a badly behaved plugin.
  @Suppress("UNCHECKED_CAST") val projectDataNode = parent as? DataNode<ProjectData>
  if (projectDataNode == null) {
    RESOLVER_LOG.error(
      "Couldn't find project data for module ${data.moduleName}, incorrect tree structure."
    )
    return
  }

  // We need the composite information to compute the module IDs we compute here to only traverse the data
  // node tree once.
  val compositeData = ExternalSystemApiUtil.find(projectDataNode, CompositeBuildData.KEY)?.data

  // These maps keep track of all the dependencies that we have already seen. This allows us to skip over processing
  // dependencies multiple times which more specific scopes.
  val processedLibraries = mutableMapOf<String, LibraryDependencyData>()
  val processedModuleDependencies = mutableMapOf<String, ModuleDependencyData>()

  val selectedVariant = variant ?: androidModel.selectedVariant

  // Setup the dependencies for the main artifact, the main dependencies are done first since there scope is more permissive.
  // This allows us to just skip the dependency if it is already present.
  setupAndroidDependenciesForArtifact(
    selectedVariant.mainArtifact,
    this,
    androidModel.features.shouldExportDependencies(),
    DependencyScope.COMPILE,
    projectDataNode,
    compositeData,
    idToModuleData,
    additionalArtifactsMapper,
    processedLibraries,
    processedModuleDependencies
  )

  // Setup the dependencies of the test artifact.
  selectedVariant.testArtifacts.forEach { testArtifact ->
    setupAndroidDependenciesForArtifact(
      testArtifact,
      this,
      androidModel.features.shouldExportDependencies(),
      DependencyScope.TEST,
      projectDataNode,
      compositeData,
      idToModuleData,
      additionalArtifactsMapper,
      processedLibraries,
      processedModuleDependencies
    )
  }

  // Determine an order for the dependencies, for now we put the modules first and the libraries after.
  // The order of the libraries and modules is the same order as we obtain them from AGP, with the
  // dependencies from the main artifact coming first (java libs then android) and the test artifacts
  // coming after (java libs then android).
  // TODO(rework-12): What is the correct order
  var orderIndex = 0

  // First set up any extra sdk libraries as these should really be in the SDK.
  getExtraSdkLibraries(projectDataNode, this, androidModel.androidProject.bootClasspath).forEach { sdkLibraryDependency ->
    sdkLibraryDependency.order = orderIndex++
    createChild(ProjectKeys.LIBRARY_DEPENDENCY, sdkLibraryDependency)
  }

  processedModuleDependencies.forEach { (_, moduleDependencyData) ->
    moduleDependencyData.order = orderIndex++
    createChild(ProjectKeys.MODULE_DEPENDENCY, moduleDependencyData)
  }
  processedLibraries.forEach { (_, libraryDependencyData) ->
    libraryDependencyData.order = orderIndex++
    createChild(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData)
  }
}

// TODO: Should this be moved and shared with the plugin?
const val LOCAL_LIBRARY_PREFIX = "__local_aars__"

/**
 * Attempts to shorten the library name by making paths relative and makes paths system independent.
 * Name shortening is required because the maximum allowed file name length is 256 characters and .jar files located in deep
 * directories in CI environments may exceed this limit.
 */
private fun adjustLocalLibraryName(artifactFile: File, projectBasePath: String) : @SystemIndependent String {
  val maybeRelative = artifactFile.relativeToOrSelf(File(toSystemDependentName(projectBasePath)))
  if (!filesEqual(maybeRelative, artifactFile)) {
    return toSystemIndependentName(File(".${File.separator}${maybeRelative}").path)
  }

  return toSystemIndependentName(artifactFile.path)
}

/**
 * Converts the artifact address into a name that will be used by the IDE to represent the library.
 */
private fun convertToLibraryName(library: Library, projectBasePath: String): String {
  if (library.artifactAddress.startsWith("$LOCAL_LIBRARY_PREFIX:"))  {
    return adjustLocalLibraryName(library.artifact, projectBasePath)
  }

  return convertMavenCoordinateStringToIdeLibraryName(library.artifactAddress)
}

/**
 * Converts the name of a maven form dependency from the format that is returned from the Android Gradle plugin [Library]
 * to the name that will be used to setup the library in the IDE. The Android Gradle plugin uses maven co-ordinates to
 * represent the library.
 *
 * In order to share the libraries between Android and non-Android modules we want to convert the artifact
 * co-ordinate string that will match the ones that would be set up in the IDE for non-android modules.
 *
 * Current this method removes any @jar from the end of the coordinate since IDEA defaults to this and doesn't display
 * it.
 */
private fun convertMavenCoordinateStringToIdeLibraryName(mavenCoordinate: String) : String {
  return mavenCoordinate.removeSuffix("@jar")
}

/**
 * Removes name extension or qualifier or classifier from the given [libraryName]. If the given [libraryName]
 * can't be parsed as a [GradleCoordinate] this method returns the [libraryName] un-edited.
 */
private fun stripExtension(libraryName: String) : String {
  val coordinate = GradleCoordinate.parseCoordinateString(libraryName) ?: return libraryName
  return "${coordinate.groupId}:${coordinate.artifactId}:${coordinate.version}"
}

private fun Library.isModuleLevel(modulePath: String) = try {
  FileUtil.isAncestor(modulePath, artifactAddress, false)
} catch (e: UnsupportedMethodException) {
  false
}

/**
 * Computes the module ID for the given target of this [library]. We want to be able to reuse the
 * maps of module ID to [ModuleData] in the [GradleExecutionWorkspace], in order to do this we need to be able
 * to reconstruct the module ID key. It is initially computed in [GradleProjectResolverUtil.getModuleId] it's
 * format is currently as follows:
 *   1 - For projects under the main build,  the module ID will just by the Gradle path to the project.
 *       For example ":app", ":lib", ":app:nested:deepNested"
 *   2 - For other project not under the main build,  the module ID will be the name of the Gradle root
 *       project followed by the full Gradle path.
 *       For example "IncludedProject:app", "OtherBuild:lib:"
 *
 *
 */
private fun computeModuleIdForLibraryTarget(
  library: Library,
  projectData: ProjectData?,
  compositeData: CompositeBuildData?
) : String? {
  // If we don't have a ProjectData or CompositeData we assume that the target module is contained within the
  // main Gradle build.
  if (projectData == null) {
    return library.projectPath
  }
  if (library.buildId == projectData.linkedExternalProjectPath ||
      compositeData == null) {
    return GradleProjectResolverUtil.getModuleId(library.projectPath, projectData.externalName)
  }

  // Since the dependency doesn't have the same root path as the module's project it must be pointing to a
  // module in an included build. We now need to find the name of the root Gradle build that the module
  // belongs to in order to construct the module ID.
  val projectName = compositeData.compositeParticipants.firstOrNull {
    it.rootPath == library.buildId
  }?.rootProjectName ?: return GradleProjectResolverUtil.getModuleId(library.projectPath, projectData.externalName)

  return if (library.projectPath == ":") projectName else projectName + library.projectPath
}

private fun setupAndroidDependenciesForArtifact(
  artifact: IdeBaseArtifact,
  moduleDataNode: DataNode<ModuleData>,
  shouldExportDependencies: Boolean,
  scope: DependencyScope,
  projectDataNode: DataNode<ProjectData>,
  compositeData: CompositeBuildData?,
  idToModuleData: (String) -> ModuleData?,
  additionalArtifactsMapper: (ArtifactId, ArtifactPath) -> AdditionalArtifactsPaths?,
  processedLibraries: MutableMap<String, LibraryDependencyData>,
  processedModuleDependencies: MutableMap<String, ModuleDependencyData>
) {
  val dependencies = artifact.level2Dependencies
  val projectData = projectDataNode.data

  // TODO(rework-12): Sort out the order of dependencies.
  (dependencies.javaLibraries + dependencies.androidLibraries).forEach { library ->
    // TODO: Add all pom files from the Sources and javadoc model to the project in a separate DataNode on the project
    //       this information should be used in a data service to set up the project service.

    val libraryName = convertToLibraryName(library, projectData.linkedExternalProjectPath)

    // Skip if already present
    if (processedLibraries.containsKey(libraryName)) return@forEach

    // Add all the required binary paths from the library.
    val libraryData = LibraryData(GradleConstants.SYSTEM_ID, libraryName, false)
    when (library.type) {
      LIBRARY_JAVA -> {
        libraryData.addPath(BINARY, library.artifact.absolutePath)
      }
      LIBRARY_ANDROID -> {
        libraryData.addPath(BINARY, library.compileJarFile)
        libraryData.addPath(BINARY, library.resFolder)
        // TODO: Should this be binary? Do we need the platform to allow custom types here?
        libraryData.addPath(BINARY, library.manifest)
        library.localJars.forEach { localJar ->
          libraryData.addPath(BINARY, localJar)
        }
      }
    }

    // Add the JavaDoc and sources location if we have them.
    additionalArtifactsMapper(stripExtension(libraryName), library.artifact)?.also { (sources, javadocs) ->
      sources?.also { libraryData.addPath(SOURCE, it.absolutePath) }
      javadocs?.also { libraryData.addPath(DOC, it.absolutePath) }
    }

    // It may be possible that we have local sources not obtained by Gradle. We look for those here.
    LibraryFilePaths.findArtifactFilePathInRepository(library.artifact, "-sources.jar", true)?.also {
      libraryData.addPath(SOURCE, it.absolutePath)
    }
    LibraryFilePaths.findArtifactFilePathInRepository(library.artifact, "-javadoc.jar", true)?.also {
      libraryData.addPath(DOC, it.absolutePath)
    }

    // Add external annotations.
    // TODO: Why do we only do this for Android modules?
    // TODO: Add this to the model instead!
    if (library.type == LIBRARY_ANDROID) {
      (library.localJars + library.compileJarFile + library.resFolder).mapNotNull {
        FilePaths.toSystemDependentPath(it)?.path
      }.forEach { binaryPath ->
        if (binaryPath.endsWith(separatorChar + FD_RES)) {
          val annotationsFile = File(binaryPath.removeSuffix(FD_RES) + FN_ANNOTATIONS_ZIP)
          if (annotationsFile.isFile) {
            libraryData.addPath(LibraryPathType.ANNOTATION, annotationsFile.absolutePath)
          }
        }
        else if ((libraryName.startsWith(ANDROIDX_ANNOTATIONS_ARTIFACT) ||
                  libraryName.startsWith(ANNOTATIONS_LIB_ARTIFACT)) &&
                 binaryPath.endsWith(DOT_JAR)) {
          val annotationsFile = File(binaryPath.removeSuffix(DOT_JAR) + "-" + FN_ANNOTATIONS_ZIP)
          if (annotationsFile.isFile) {
            libraryData.addPath(LibraryPathType.ANNOTATION, annotationsFile.absolutePath)
          }
        }
      }
    }

    // Work out the level of the library, if the library path is inside the module directory we treat
    // this as a Module level library. Otherwise we treat it as a Project level one.
    var libraryLevel =
      if (library.isModuleLevel(moduleDataNode.data.moduleFileDirectoryPath)) LibraryLevel.MODULE
      else LibraryLevel.PROJECT

    if (libraryLevel == LibraryLevel.PROJECT && !linkProjectLibrary(null, projectDataNode, libraryData)) {
      libraryLevel = LibraryLevel.MODULE
    }

    // Finally create the LibraryDependencyData
    val libraryDependencyData = LibraryDependencyData(moduleDataNode.data, libraryData, libraryLevel)
    libraryDependencyData.scope = scope
    libraryDependencyData.isExported = shouldExportDependencies
    processedLibraries[libraryName] = libraryDependencyData
  }

  dependencies.moduleDependencies.filter { library ->
    !library.projectPath.isNullOrEmpty()
  }.forEach { library ->
    val targetModuleId =
      computeModuleIdForLibraryTarget(library, projectData, compositeData) ?: return@forEach
    // Skip is already present
    if (processedModuleDependencies.containsKey(targetModuleId)) return@forEach

    val targetData = idToModuleData(targetModuleId) ?: return@forEach
    // Skip if the dependency is a dependency on itself, this can be produced by Gradle when the a module
    // dependency on the module in a different scope ie test code depending on the production code.
    // In IDEA this dependency is implicit.
    // TODO(rework-14): Do we need this special case, is it handled by IDEAs data service.
    // See https://issuetracker.google.com/issues/68016998.
    if (targetData == moduleDataNode.data) return@forEach
    val moduleDependencyData = ModuleDependencyData(moduleDataNode.data, targetData)
    moduleDependencyData.scope = scope
    moduleDependencyData.isExported = shouldExportDependencies
    processedModuleDependencies[targetModuleId] = moduleDependencyData
  }
}

/**
 * Sets the 'useLibrary' libraries or SDK add-ons as library dependencies.
 *
 * These libraries are set at the project level, which makes it impossible to add them to a IDE SDK definition because the IDE SDK is
 * global to the whole IDE. To work around this limitation, we set these libraries as module dependencies instead.
 *
 * TODO: The priority of these is wrong, they should be part of the SDK.
 *
 */
private fun getExtraSdkLibraries(
  projectDataNode: DataNode<ProjectData>,
  moduleDataNode: DataNode<ModuleData>,
  bootClasspath: Collection<String>
) : List<LibraryDependencyData> {
  return bootClasspath.filter { path ->
    File(path).name != FN_FRAMEWORK_LIBRARY
  }.map { path ->
    val filePath = File(path)
    val name = if (filePath.isFile) getNameWithoutExtension(filePath) else sanitizeFileName(path)

    val libraryData = LibraryData(GradleConstants.SYSTEM_ID, name, false)
    libraryData.addPath(BINARY, path)

    // Attempt to find JavaDocs and Sources for the SDK additional lib
    // TODO: Do we actually need this, where are these sources/javadocs located.
    val sources = LibraryFilePaths.findArtifactFilePathInRepository(filePath, "-sources.jar", true)
    if (sources != null) {
      libraryData.addPath(SOURCE, sources.absolutePath)
    }
    val javaDocs = LibraryFilePaths.findArtifactFilePathInRepository(filePath, "-javadoc.jar", true)
    if (javaDocs != null) {
      libraryData.addPath(DOC, javaDocs.absolutePath)
    }

    val libraryLevel = if (linkProjectLibrary(null, projectDataNode, libraryData)) LibraryLevel.PROJECT else LibraryLevel.MODULE

    LibraryDependencyData(moduleDataNode.data, libraryData, libraryLevel).apply {
      scope = DependencyScope.COMPILE
      isExported = false
    }
  }
}