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
package com.android.tools.idea.testing

import com.android.AndroidProjectTypes
import com.android.builder.model.AndroidArtifact
import com.android.builder.model.AndroidArtifactOutput
import com.android.builder.model.AndroidLibrary
import com.android.builder.model.AndroidProject
import com.android.builder.model.BuildTypeContainer
import com.android.builder.model.Dependencies
import com.android.builder.model.JavaArtifact
import com.android.builder.model.JavaLibrary
import com.android.builder.model.ProductFlavorContainer
import com.android.builder.model.SourceProvider
import com.android.builder.model.SourceProviderContainer
import com.android.builder.model.ViewBindingOptions
import com.android.ide.common.gradle.model.IdeAndroidProjectImpl
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory
import com.android.ide.common.gradle.model.stubs.AaptOptionsStub
import com.android.ide.common.gradle.model.stubs.AndroidArtifactStub
import com.android.ide.common.gradle.model.stubs.AndroidGradlePluginProjectFlagsStub
import com.android.ide.common.gradle.model.stubs.AndroidLibraryStub
import com.android.ide.common.gradle.model.stubs.AndroidProjectStub
import com.android.ide.common.gradle.model.stubs.ApiVersionStub
import com.android.ide.common.gradle.model.stubs.BuildTypeContainerStub
import com.android.ide.common.gradle.model.stubs.BuildTypeStub
import com.android.ide.common.gradle.model.stubs.DependenciesStub
import com.android.ide.common.gradle.model.stubs.DependencyGraphsStub
import com.android.ide.common.gradle.model.stubs.InstantRunStub
import com.android.ide.common.gradle.model.stubs.JavaArtifactStub
import com.android.ide.common.gradle.model.stubs.JavaCompileOptionsStub
import com.android.ide.common.gradle.model.stubs.LintOptionsStub
import com.android.ide.common.gradle.model.stubs.MavenCoordinatesStub
import com.android.ide.common.gradle.model.stubs.ProductFlavorContainerStub
import com.android.ide.common.gradle.model.stubs.ProductFlavorStub
import com.android.ide.common.gradle.model.stubs.SourceProviderContainerStub
import com.android.ide.common.gradle.model.stubs.SourceProviderStub
import com.android.ide.common.gradle.model.stubs.VariantStub
import com.android.ide.common.gradle.model.stubs.VectorDrawablesOptionsStub
import com.android.ide.common.gradle.model.stubs.ViewBindingOptionsStub
import com.android.projectmodel.ARTIFACT_NAME_ANDROID_TEST
import com.android.projectmodel.ARTIFACT_NAME_MAIN
import com.android.projectmodel.ARTIFACT_NAME_UNIT_TEST
import com.android.sdklib.AndroidVersion
import com.android.testutils.TestUtils
import com.android.testutils.TestUtils.getLatestAndroidPlatform
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.model.GradleModuleModel
import com.android.tools.idea.gradle.project.model.JavaModuleModel
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.project.sync.idea.IdeaSyncPopulateProjectTask
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys
import com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup
import com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.android.tools.idea.sdk.IdeSdks
import com.android.utils.FileUtils
import com.android.utils.appendCapitalized
import com.google.common.truth.TruthJUnit.assume
import com.intellij.externalSystem.JavaProjectData
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.StdModuleTypes.JAVA
import com.intellij.openapi.project.Project
import com.intellij.util.text.nullize
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.model.ExternalSourceSet
import org.jetbrains.plugins.gradle.model.ExternalTask
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataCache
import java.io.File

typealias AndroidProjectBuilder = (projectName: String, basePath: File, agpVersion: String) -> AndroidProject

sealed class ModuleModelBuilder {
  abstract val gradlePath: String
  abstract val gradleVersion: String?
  abstract val agpVersion: String?
}

data class AndroidModuleModelBuilder(
  override val gradlePath: String,
  override val gradleVersion: String? = null,
  override val agpVersion: String? = null,
  val selectedBuildVariant: String,
  val projectBuilder: AndroidProjectBuilder
) : ModuleModelBuilder() {
  constructor (gradlePath: String, selectedBuildVariant: String, projectBuilder: AndroidProjectBuilder)
    : this(gradlePath, null, null, selectedBuildVariant, projectBuilder)
}

data class JavaModuleModelBuilder(
  override val gradlePath: String,
  override val gradleVersion: String? = null,
  val buildable: Boolean = true
) : ModuleModelBuilder() {

  constructor (gradlePath: String, buildable: Boolean = true) : this(gradlePath, null,  buildable)

  override val agpVersion: String? = null

  companion object {
    val rootModuleBuilder = JavaModuleModelBuilder(":", buildable = false)
  }
}

data class AndroidModuleDependency(val moduleGradlePath: String, val variant: String?)
/**
 * An interface providing access to [AndroidProject] sub-model builders are used to build [AndroidProject] and its other sub-models.
 */
interface AndroidProjectStubBuilder {
  val agpVersion: String
  val buildId: String
  val projectName: String
  val basePath: File
  val buildPath: File
  val projectType: Int
  val minSdk: Int
  val targetSdk: Int
  val mainSourceProvider: SourceProvider
  val androidTestSourceProviderContainer: SourceProviderContainer?
  val unitTestSourceProviderContainer: SourceProviderContainer?
  val debugSourceProvider: SourceProvider?
  val releaseSourceProvider: SourceProvider?
  val defaultConfig: ProductFlavorContainer
  val debugBuildType: BuildTypeContainer?
  val releaseBuildType: BuildTypeContainer?
  val dynamicFeatures: List<String>
  val viewBindingOptions: ViewBindingOptions
  fun androidModuleDependencies(variant: String): List<AndroidModuleDependency>?
  fun mainArtifact(variant: String): AndroidArtifact
  fun androidTestArtifact(variant: String): AndroidArtifact
  fun unitTestArtifact(variant: String): JavaArtifact
  val androidProject: AndroidProject
}

/**
 * A helper method for building [AndroidProject] stubs.
 *
 * This method creates a model of a simple project which can be slightly customized by providing alternative implementations of
 * sub-model builders.
 */
fun createAndroidProjectBuilder(
  buildId: AndroidProjectStubBuilder.() -> String = { "/tmp/buildId" }, //  buildId should not be assumed to be a path.
  projectType: AndroidProjectStubBuilder.() -> Int = { AndroidProjectTypes.PROJECT_TYPE_APP },
  minSdk: AndroidProjectStubBuilder.() -> Int = { 16 },
  targetSdk: AndroidProjectStubBuilder.() -> Int = { 22 },
  defaultConfig: AndroidProjectStubBuilder.() -> ProductFlavorContainerStub = { buildDefaultConfigStub() },
  mainSourceProvider: AndroidProjectStubBuilder.() -> SourceProviderStub = { buildMainSourceProviderStub() },
  androidTestSourceProvider: AndroidProjectStubBuilder.() -> SourceProviderContainerStub? = { buildAndroidTestSourceProviderContainerStub() },
  unitTestSourceProvider: AndroidProjectStubBuilder.() -> SourceProviderContainerStub? = { buildUnitTestSourceProviderContainerStub() },
  debugSourceProvider: AndroidProjectStubBuilder.() -> SourceProviderStub? = { buildDebugSourceProviderStub() },
  releaseSourceProvider: AndroidProjectStubBuilder.() -> SourceProviderStub? = { buildReleaseSourceProviderStub() },
  debugBuildType: AndroidProjectStubBuilder.() -> BuildTypeContainerStub? = { buildDebugBuildTypeStub() },
  releaseBuildType: AndroidProjectStubBuilder.() -> BuildTypeContainerStub? = { buildReleaseBuildTypeStub() },
  dynamicFeatures: AndroidProjectStubBuilder.() -> List<String> = { emptyList() },
  viewBindingOptions: AndroidProjectStubBuilder.() -> ViewBindingOptionsStub = { buildViewBindingOptions() },
  mainArtifactStub: AndroidProjectStubBuilder.(variant: String) -> AndroidArtifactStub = { variant -> buildMainArtifactStub(variant) },
  androidTestArtifactStub: AndroidProjectStubBuilder.(variant: String) -> AndroidArtifactStub = { variant -> buildAndroidTestArtifactStub(variant) },
  unitTestArtifactStub: AndroidProjectStubBuilder.(variant: String) -> JavaArtifactStub = { variant -> buildUnitTestArtifactStub(variant) },
  androidModuleDependencyList: AndroidProjectStubBuilder.(variant: String) -> List<AndroidModuleDependency> = { emptyList() },
  androidProject: AndroidProjectStubBuilder.() -> AndroidProject = { buildAndroidProjectStub() }
): AndroidProjectBuilder {
  return { projectName, basePath, agpVersion ->
    val builder = object : AndroidProjectStubBuilder {
      override val agpVersion: String = agpVersion
      override val buildId: String = buildId()
      override val projectName: String = projectName
      override val basePath: File = basePath
      override val buildPath: File get() = basePath.resolve("build")
      override val projectType: Int get() = projectType()
      override val minSdk: Int get() = minSdk()
      override val targetSdk: Int get() = targetSdk()
      override val mainSourceProvider: SourceProvider get() = mainSourceProvider()
      override val androidTestSourceProviderContainer: SourceProviderContainer? get() = androidTestSourceProvider()
      override val unitTestSourceProviderContainer: SourceProviderContainer? get() = unitTestSourceProvider()
      override val debugSourceProvider: SourceProvider? get() = debugSourceProvider()
      override val releaseSourceProvider: SourceProvider? get() = releaseSourceProvider()
      override val defaultConfig: ProductFlavorContainer = defaultConfig()
      override val debugBuildType: BuildTypeContainer? = debugBuildType()
      override val releaseBuildType: BuildTypeContainer? = releaseBuildType()
      override val dynamicFeatures: List<String> = dynamicFeatures()
      override val viewBindingOptions: ViewBindingOptions = viewBindingOptions()
      override fun androidModuleDependencies(variant: String): List<AndroidModuleDependency> = androidModuleDependencyList(variant)
      override fun mainArtifact(variant: String): AndroidArtifact = mainArtifactStub(variant)
      override fun androidTestArtifact(variant: String): AndroidArtifact = androidTestArtifactStub(variant)
      override fun unitTestArtifact(variant: String): JavaArtifact = unitTestArtifactStub(variant)
      override val androidProject: AndroidProject = androidProject()
    }
    builder.androidProject
  }
}

fun createAndroidProjectBuilderForDefaultTestProjectStructure(): AndroidProjectBuilder =
    createAndroidProjectBuilder(
      minSdk = { AndroidVersion.MIN_RECOMMENDED_API },
      targetSdk = { AndroidVersion.VersionCodes.O_MR1 },
      mainSourceProvider = {
        SourceProviderStub(
          ARTIFACT_NAME_MAIN,
          File(basePath, "AndroidManifest.xml"),
          listOf(File(basePath, "src")),
          emptyList(),
          emptyList(),
          emptyList(),
          emptyList(),
          emptyList(),
          listOf(File(basePath, "res")),
          emptyList(),
          emptyList(),
          emptyList())
      },
      androidTestSourceProvider = { null },
      unitTestSourceProvider = { null },
      releaseSourceProvider = { null }
    )

fun AndroidProjectStubBuilder.buildMainSourceProviderStub() =
  SourceProviderStub(ARTIFACT_NAME_MAIN, basePath.resolve("src/main"), "AndroidManifest.xml")

fun AndroidProjectStubBuilder.buildAndroidTestSourceProviderContainerStub() =
  SourceProviderContainerStub(
    ARTIFACT_NAME_ANDROID_TEST,
    SourceProviderStub(ARTIFACT_NAME_ANDROID_TEST, basePath.resolve("src/androidTest"), "AndroidManifest.xml"))

fun AndroidProjectStubBuilder.buildUnitTestSourceProviderContainerStub() =
  SourceProviderContainerStub(
    ARTIFACT_NAME_UNIT_TEST,
    SourceProviderStub(ARTIFACT_NAME_UNIT_TEST, basePath.resolve("src/test"), "AndroidManifest.xml"))

fun AndroidProjectStubBuilder.buildDebugSourceProviderStub() =
  SourceProviderStub("debug", basePath.resolve("src/debug"), "AndroidManifest.xml")

fun AndroidProjectStubBuilder.buildReleaseSourceProviderStub() =
  SourceProviderStub("release", basePath.resolve("src/release"), "AndroidManifest.xml")

fun AndroidProjectStubBuilder.buildDefaultConfigStub() = ProductFlavorContainerStub(
  ProductFlavorStub(
    mapOf(),
    listOf(),
    VectorDrawablesOptionsStub(),
    null,
    null,
    12,
    "2.0",
    ApiVersionStub("$minSdk", null, minSdk),
    ApiVersionStub("$targetSdk", null, targetSdk),
    null,
    null,
    null,
    null,
    null,
    null,
    "android.test.InstrumentationTestRunner",
    null,
    null,
    null,
    null
  ),
  mainSourceProvider,
  listOfNotNull(androidTestSourceProviderContainer, unitTestSourceProviderContainer)
)

fun AndroidProjectStubBuilder.buildDebugBuildTypeStub() = debugSourceProvider?.let { debugSourceProvider ->
  BuildTypeContainerStub(
    BuildTypeStub(
      debugSourceProvider.name, mapOf(), mapOf(), mapOf(), listOf(), listOf(), listOf(), mapOf(), null, null, null, null, null,
      true, true, true, 1, false, true),
    debugSourceProvider,
    listOf())
}

fun AndroidProjectStubBuilder.buildReleaseBuildTypeStub() = releaseSourceProvider?.let { releaseSourceProvider ->
  BuildTypeContainerStub(
    BuildTypeStub(
      releaseSourceProvider.name, mapOf(), mapOf(), mapOf(), listOf(), listOf(), listOf(), mapOf(), null, null, null, null,
      null, false, false, false, 1, true, true),
    releaseSourceProvider,
    listOf())
}

fun AndroidProjectStubBuilder.buildViewBindingOptions() = ViewBindingOptionsStub()

fun AndroidProjectStubBuilder.buildMainArtifactStub(
  variant: String,
  classFolders: Set<File> = setOf()
): AndroidArtifactStub {
  val androidModuleDependencies = this.androidModuleDependencies(variant).orEmpty()
  val dependenciesStub = buildDependenciesStub(
    libraries = androidModuleDependencies.map {
      AndroidLibraryStub(
        MavenCoordinatesStub("artifacts", it.moduleGradlePath, "unspecificed", "jar"),
        this.buildId,
        it.moduleGradlePath,
        "artifacts:${it.moduleGradlePath}:unspecified@jar",
        false,
        false,
        File("stub_bundle.jar"),
        File("stub_folder"),
        emptyList(),
        emptyList(),
        File("stub_AndroidManifest.xml"),
        File("stub_jarFile.jar"),
        File("stub_compileJarFile.jar"),
        File("stub_resFolder"),
        File("stub_resStaticLibrary"),
        File("stub_assetsFolder"),
        it.variant,
        emptyList(),
        File("srub_proguard.txt"),
        File("stub_lintJar.jar"),
        File("stub_publicResources")
      )
    }
  )
  return AndroidArtifactStub(
    ARTIFACT_NAME_MAIN,
    "compile".appendCapitalized(variant).appendCapitalized("sources"),
    "assemble".appendCapitalized(variant),
    buildPath.resolve("output/apk/$variant/output.json"),
    buildPath.resolve("intermediates/javac/$variant/classes"),
    classFolders,
    buildPath.resolve("intermediates/java_res/$variant/out"),
    dependenciesStub,
    dependenciesStub,
    DependencyGraphsStub(listOf(), listOf(), listOf(), listOf()),
    setOf("ideSetupTask1", "ideSetupTask2"),
    setOf(),
    null,
    null,
    listOf<AndroidArtifactOutput>(),
    "applicationId",
    "generate".appendCapitalized(variant).appendCapitalized("sources"),
    mapOf(),
    mapOf(),
    InstantRunStub(),
    "defaultConfig",
    null,
    null,
    listOf(),
    null,
    null,
    "bundle".takeIf { projectType == AndroidProjectTypes.PROJECT_TYPE_APP }?.appendCapitalized(variant),
    buildPath.resolve("intermediates/bundle_ide_model/$variant/output.json"),
    "extractApksFor".takeIf { projectType == AndroidProjectTypes.PROJECT_TYPE_APP }?.appendCapitalized(variant),
    buildPath.resolve("intermediates/apk_from_bundle_ide_model/$variant/output.json"),
    null,
    false
  )
}

fun AndroidProjectStubBuilder.buildAndroidTestArtifactStub(
  variant: String,
  classFolders: Set<File> = setOf()
): AndroidArtifactStub {
  val dependenciesStub = buildDependenciesStub()
  return AndroidArtifactStub(
    ARTIFACT_NAME_ANDROID_TEST,
    "compile".appendCapitalized(variant).appendCapitalized("androidTestSources"),
    "assemble".appendCapitalized(variant).appendCapitalized("androidTest"),
    buildPath.resolve("output/apk/$variant/output.json"),
    buildPath.resolve("intermediates/javac/${variant}AndroidTest/classes"),
    classFolders,
    buildPath.resolve("intermediates/java_res/${variant}AndroidTest/out"),
    dependenciesStub,
    dependenciesStub,
    DependencyGraphsStub(listOf(), listOf(), listOf(), listOf()),
    setOf("ideAndroidTestSetupTask1", "ideAndroidTestSetupTask2"),
    setOf(),
    null,
    null,
    listOf(),
    "applicationId",
    "generate".appendCapitalized(variant).appendCapitalized("androidTestSources"),
    mapOf(),
    mapOf(),
    InstantRunStub(),
    "defaultConfig",
    null,
    null,
    listOf(),
    null,
    null,
    "bundle"
      .takeIf { projectType == AndroidProjectTypes.PROJECT_TYPE_APP }?.appendCapitalized(variant)?.appendCapitalized("androidTest"),
    buildPath.resolve("intermediates/bundle_ide_model/$variant/output.json"),
    "extractApksFor"
      .takeIf { projectType == AndroidProjectTypes.PROJECT_TYPE_APP }?.appendCapitalized(variant)?.appendCapitalized("androidTest"),
    buildPath.resolve("intermediates/apk_from_bundle_ide_model/$variant/output.json"),
    null,
    false
  )
}

fun AndroidProjectStubBuilder.buildUnitTestArtifactStub(
  variant: String,
  classFolders: Set<File> = setOf(),
  dependencies: Dependencies = buildDependenciesStub(),
  mockablePlatformJar: File? = null
): JavaArtifactStub {
  return JavaArtifactStub(
    ARTIFACT_NAME_UNIT_TEST,
    "compile".appendCapitalized(variant).appendCapitalized("unitTestSources"),
    "assemble".appendCapitalized(variant).appendCapitalized("unitTest"),
    buildPath.resolve("intermediates/javac/${variant}UnitTest/classes"),
    classFolders,
    buildPath.resolve("intermediates/java_res/${variant}UnitTest/out"),
    dependencies,
    dependencies,
    DependencyGraphsStub(listOf(), listOf(), listOf(), listOf()),
    setOf("ideUnitTestSetupTask1", "ideUnitTestSetupTask2"),
    setOf(),
    null,
    null,
    mockablePlatformJar
  )
}

fun AndroidProjectStubBuilder.buildAndroidProjectStub(): AndroidProjectStub {
  val debugBuildType = this.debugBuildType
  val releaseBuildType = this.releaseBuildType
  val defaultVariant = debugBuildType ?: releaseBuildType
  val defaultVariantName = defaultVariant?.sourceProvider?.name ?: "main"
  return AndroidProjectStub(
    agpVersion,
    projectName,
    null,
    defaultConfig,
    listOfNotNull(debugBuildType, releaseBuildType),
    listOf(),
    "buildToolsVersion",
    listOf(),
    listOf("debug", "release")
      .map { variant ->
        VariantStub(
          variant,
          variant,
          mainArtifact(variant),
          listOf(androidTestArtifact(variant)),
          listOf(unitTestArtifact(variant)),
          variant,
          listOf(),
          defaultConfig.productFlavor,
          listOf(),
          false
        )
      },
    listOfNotNull(debugBuildType?.sourceProvider?.name, releaseBuildType?.sourceProvider?.name),
    defaultVariantName,
    listOf(),
    getLatestAndroidPlatform(),
    listOf(),
    listOf(),
    listOf(),
    LintOptionsStub(),
    setOf(),
    JavaCompileOptionsStub(),
    AaptOptionsStub(),
    dynamicFeatures,
    viewBindingOptions,
    buildPath,
    null,
    1,
    true,
    projectType,
    true,
    AndroidGradlePluginProjectFlagsStub()
  )
}

fun AndroidProjectStubBuilder.buildDependenciesStub(
  libraries: List<AndroidLibrary> = listOf(),
  javaLibraries: List<JavaLibrary> = listOf(),
  projects: List<String> = listOf(),
  javaModules: List<Dependencies.ProjectIdentifier> = listOf(),
  runtimeOnlyClasses: List<File> = listOf()
): DependenciesStub = DependenciesStub(libraries, javaLibraries, projects, javaModules, runtimeOnlyClasses)

/**
 * Sets up [project] as a one module project configured in the same way sync would conigure it from the same model.
 */
fun setupTestProjectFromAndroidModel(
  project: Project,
  basePath: File,
  vararg moduleBuilders: ModuleModelBuilder
) {
  if (IdeSdks.getInstance().androidSdkPath === null) {
    AndroidGradleTests.setUpSdks(project, project, TestUtils.getSdk())
  }

  val moduleManager = ModuleManager.getInstance(project)
  if (moduleManager.modules.size <= 1) {
    runWriteAction {
      val modifiableModel = moduleManager.modifiableModel
      val module = if (modifiableModel.modules.isEmpty()) {
        modifiableModel.newModule(basePath.resolve("${project.name}.iml").path, JAVA.id)
      }
      else {
        moduleManager.modules[0]
      }
      if (module.name != project.name) {
        modifiableModel.renameModule(module, project.name)
        modifiableModel.setModuleGroupPath(module, arrayOf(project.name))
      }
      modifiableModel.commit()
      ExternalSystemModulePropertyManager
        .getInstance(module)
        .setExternalOptions(
          GRADLE_SYSTEM_ID,
          ModuleData(":", GRADLE_SYSTEM_ID, JAVA.id, project.name, basePath.path, basePath.path),
          ProjectData(GRADLE_SYSTEM_ID, project.name, project.basePath!!, basePath.path))
    }
  }
  else {
    assume().that(moduleManager.modules.size).isEqualTo(0)
  }
  ProjectSystemService.getInstance(project).replaceProjectSystemForTests(GradleProjectSystem (project))
  val gradlePlugins = listOf(
    "com.android.java.model.builder.JavaLibraryPlugin", "org.gradle.buildinit.plugins.BuildInitPlugin",
    "org.gradle.buildinit.plugins.WrapperPlugin", "org.gradle.api.plugins.HelpTasksPlugin",
    "com.android.build.gradle.api.AndroidBasePlugin", "org.gradle.language.base.plugins.LifecycleBasePlugin",
    "org.gradle.api.plugins.BasePlugin", "org.gradle.api.plugins.ReportingBasePlugin",
    "org.gradle.api.plugins.JavaBasePlugin", "com.android.build.gradle.AppPlugin",
    "org.gradle.plugins.ide.idea.IdeaPlugin"
  )
  val task = IdeaSyncPopulateProjectTask(project)
  val buildPath = basePath.resolve("build")
  val projectName = project.name
  val projectDataNode = DataNode<ProjectData>(
    ProjectKeys.PROJECT,
    ProjectData(
      GRADLE_SYSTEM_ID,
      projectName,
      basePath.path,
      basePath.path),
    null)

  projectDataNode.addChild(
    DataNode<JavaProjectData>(
      JavaProjectData.KEY,
      JavaProjectData(GRADLE_SYSTEM_ID, buildPath.path),
      null
    )
  )

  projectDataNode.addChild(
    DataNode<ExternalProject>(
      ExternalProjectDataCache.KEY,
      object : ExternalProject {
        override fun getExternalSystemId(): String = GRADLE_SYSTEM_ID.id
        override fun getId(): String = projectName
        override fun getName(): String = projectName
        override fun getQName(): String = projectName
        override fun getDescription(): String? = null
        override fun getGroup(): String = ""
        override fun getVersion(): String = "unspecified"
        override fun getChildProjects(): Map<String, ExternalProject> = mapOf()
        override fun getProjectDir(): File = basePath
        override fun getBuildDir(): File = buildPath
        override fun getBuildFile(): File? = null
        override fun getTasks(): Map<String, ExternalTask> = mapOf()
        override fun getSourceSets(): Map<String, ExternalSourceSet> = mapOf()
        override fun getArtifacts(): List<File> = listOf()
        override fun getArtifactsByConfiguration(): Map<String, MutableSet<File>> = mapOf()
      },
      null
    )
  )

  moduleBuilders.forEach { moduleBuilder ->
    val gradlePath = moduleBuilder.gradlePath
    val moduleName = gradlePath.substringAfterLast(':').nullize() ?: projectName;
    val moduleBasePath = basePath.resolve(gradlePath.substring(1).replace(':', File.separatorChar))
    FileUtils.mkdirs(moduleBasePath)
    val moduleDataNode = when (moduleBuilder) {
      is AndroidModuleModelBuilder -> {
        createAndroidModuleDataNode(
          moduleName,
          gradlePath,
          moduleBasePath,
          moduleBuilder.gradleVersion,
          moduleBuilder.agpVersion,
          gradlePlugins,
          moduleBuilder.projectBuilder(
            moduleName,
            moduleBasePath,
            moduleBuilder.agpVersion ?: LatestKnownPluginVersionProvider.INSTANCE.get()
          ),
          moduleBuilder.selectedBuildVariant
        )
      }
      is JavaModuleModelBuilder ->
        createJavaModuleDataNode(
          moduleName,
          gradlePath,
          moduleBasePath,
          moduleBuilder.buildable
        )
    }
    projectDataNode.addChild(moduleDataNode)
  }

  IdeSdks.removeJdksOn(project)
  runWriteAction {
    task.populateProject(
      projectDataNode,
      ExternalSystemTaskId.create(GRADLE_SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, project),
      PostSyncProjectSetup.Request(),
      null
    )
    assume().that(GradleSyncState.getInstance(project).lastSyncFailed()).isFalse()
  }
}

private fun createAndroidModuleDataNode(
  moduleName: String,
  gradlePath: String,
  moduleBasePath: File,
  gradleVersion: String?,
  agpVersion: String?,
  gradlePlugins: List<String>,
  androidProjectStub: AndroidProject,
  selectedVariantName: String
): DataNode<ModuleData> {

  val moduleDataNode = createGradleModuleDataNode(gradlePath, moduleName, moduleBasePath)

  moduleDataNode.addChild(
    DataNode<GradleModuleModel>(
      AndroidProjectKeys.GRADLE_MODULE_MODEL,
      GradleModuleModel(
        moduleName,
        listOf(),
        gradlePath,
        moduleBasePath,
        gradlePlugins,
        null,
        gradleVersion,
        agpVersion,
        false
      ),
      null
    )
  )

  moduleDataNode.addChild(
    DataNode<AndroidModuleModel>(
      AndroidProjectKeys.ANDROID_MODEL,
      AndroidModuleModel.create(
        moduleName,
        moduleBasePath,
        IdeAndroidProjectImpl.create(
          androidProjectStub,
          IdeDependenciesFactory(),
          null,
          null),
        selectedVariantName
      ),
      null
    )
  )

  return moduleDataNode
}

private fun createJavaModuleDataNode(
  moduleName: String,
  gradlePath: String,
  moduleBasePath: File,
  buildable: Boolean
): DataNode<ModuleData> {

  val moduleDataNode = createGradleModuleDataNode(gradlePath, moduleName, moduleBasePath)

  if (buildable || gradlePath != ":") {
    moduleDataNode.addChild(
      DataNode<GradleModuleModel>(
        AndroidProjectKeys.GRADLE_MODULE_MODEL,
        GradleModuleModel(
          moduleName,
          listOf(),
          gradlePath,
          moduleBasePath,
          emptyList(),
          null,
          null,
          null,
          false
        ),
        null
      )
    )
  }

  moduleDataNode.addChild(
    DataNode<JavaModuleModel>(
      AndroidProjectKeys.JAVA_MODULE_MODEL,
      JavaModuleModel.create(
        moduleName,
        emptyList(),
        emptyList(),
        emptyList(),
        emptyMap(),
        emptyList(),
        null,
        null,
        null,
        buildable,
        false
      ),
      null
    )
  )

  return moduleDataNode
}

private fun createGradleModuleDataNode(
  gradlePath: String,
  moduleName: String,
  moduleBasePath: File
): DataNode<ModuleData> {
  val moduleDataNode = DataNode<ModuleData>(
    ProjectKeys.MODULE,
    ModuleData(
      if (gradlePath == ":") moduleName else gradlePath,
      GRADLE_SYSTEM_ID,
      JavaModuleType.getModuleType().id,
      moduleName,
      moduleBasePath.path,
      moduleBasePath.path
    ),
    null
  )
  return moduleDataNode
}
