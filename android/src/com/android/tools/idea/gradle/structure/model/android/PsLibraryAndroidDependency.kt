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
package com.android.tools.idea.gradle.structure.model.android

import com.android.builder.model.level2.Library
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel
import com.android.tools.idea.gradle.structure.model.*
import com.android.tools.idea.gradle.structure.model.PsDependency.TextType.PLAIN_TEXT
import com.android.tools.idea.gradle.structure.model.helpers.parseString
import com.android.tools.idea.gradle.structure.model.meta.*
import com.android.tools.idea.gradle.structure.model.repositories.search.ArtifactRepositorySearchResults
import com.android.tools.idea.gradle.structure.model.repositories.search.ArtifactRepositorySearchService
import com.android.tools.idea.gradle.structure.model.repositories.search.SearchRequest
import com.google.common.collect.ImmutableSet
import com.google.common.util.concurrent.Futures
import com.intellij.util.PlatformIcons.LIBRARY_ICON
import javax.swing.Icon
import kotlin.reflect.KProperty

open class PsDeclaredLibraryAndroidDependency(
  parent: PsAndroidModule,
  containers: Collection<PsAndroidArtifact>,
  final override val parsedModel: ArtifactDependencyModel
) : PsLibraryAndroidDependency(parent, containers),
    PsDeclaredDependency, PsDeclaredLibraryDependency {
  private val nameResolvedProperty = parsedModel.name()
  private val groupResolvedProperty = parsedModel.group()
  private val versionResolvedProperty = parsedModel.version()
  override val spec: PsArtifactDependencySpec
    get() = PsArtifactDependencySpec.create(
      groupResolvedProperty.toString(),
      nameResolvedProperty.forceString(),
      versionResolvedProperty.toString()
    )
  override val resolvedModel: Any? = null
  override val isDeclared: Boolean = true
  final override val configurationName: String = parsedModel.configurationName()
  override val joinedConfigurationNames: String = configurationName

  var version by Descriptor.version
  override val versionProperty: ModelSimpleProperty<ArtifactRepositorySearchService, Unit, String>
    get() = object : ModelSimpleProperty<ArtifactRepositorySearchService, Unit, String> {
      override val description: String get() = Descriptor.version.description
      override fun bind(model: Unit): ModelPropertyCore<String> = Descriptor.version.bind(this@PsDeclaredLibraryAndroidDependency)
      override fun bindContext(context: ArtifactRepositorySearchService, model: Unit): ModelPropertyContext<String> =
        Descriptor.version.bindContext(context, this@PsDeclaredLibraryAndroidDependency)

      override fun getValue(thisRef: Unit, property: KProperty<*>): ParsedValue<String> = throw UnsupportedOperationException()
      override fun setValue(thisRef: Unit, property: KProperty<*>, value: ParsedValue<String>) = throw UnsupportedOperationException()
    }

  object Descriptor : ModelDescriptor<PsDeclaredLibraryAndroidDependency, Nothing, ArtifactDependencyModel> {
    override fun getResolved(model: PsDeclaredLibraryAndroidDependency): Nothing? = null

    override fun getParsed(model: PsDeclaredLibraryAndroidDependency): ArtifactDependencyModel? = model.parsedModel

    // TODO(solodkyy): Ensure setModified refreshes the resolved dependency collection when required.
    override fun setModified(model: PsDeclaredLibraryAndroidDependency) {
      // NOTE: There is no need to re-index the declared dependency collection. Version is not a part of the key.
      model.isModified = true
      // TODO(solodkyy): Make setModified() customizable at the property level since some properties will need to call resetDependencies().
      model.parent.resetResolvedDependencies()
      model.parent.fireDependencyModifiedEvent(model)
    }

    private const val MAX_ARTIFACTS_TO_REQUEST = 50  // Note: we do not expect more than one result per repository.
    val version: ModelSimpleProperty<ArtifactRepositorySearchService, PsDeclaredLibraryAndroidDependency, String> = property(
      "Version",
      resolvedValueGetter = { null },
      parsedPropertyGetter = { this.version() },
      getter = { asString() },
      setter = { setValue(it) },
      parser = ::parseString,
      knownValuesGetter = { searchService: ArtifactRepositorySearchService, model ->
        Futures.transform(
          searchService.search(SearchRequest(model.spec.name, model.spec.group, MAX_ARTIFACTS_TO_REQUEST, 0)),
          {
            it!!.toVersionValueDescriptors()
          })
      },
      variableMatchingStrategy = VariableMatchingStrategy.WELL_KNOWN_VALUE
    )
  }
}

open class PsResolvedLibraryAndroidDependency(
  parent: PsAndroidModule,
  override val spec: PsArtifactDependencySpec,
  val artifact: PsAndroidArtifact,
  override val resolvedModel: Library,
  private val declaredDependencies: Collection<PsDeclaredLibraryAndroidDependency>
) : PsLibraryAndroidDependency(parent, listOf(artifact)), PsResolvedDependency, PsResolvedLibraryDependency {
  private val parsedModels = declaredDependencies.map { it.parsedModel }
  override val isDeclared: Boolean get() = !declaredDependencies.isEmpty()
  override val joinedConfigurationNames: String get() = parsedModels.joinToString(separator = ", ") { it.configurationName() }

  override fun getParsedModels(): List<DependencyModel> = parsedModels.toList()

  override fun hasPromotedVersion(): Boolean {
    val declaredSpecs = getParsedModels().map {
      PsArtifactDependencySpec.create(it as ArtifactDependencyModel)
    }
    for (declaredSpec in declaredSpecs) {
      if (spec.version != null && declaredSpec.version != null) {
        val declaredVersion = GradleVersion.tryParse(declaredSpec.version!!)
        if (declaredVersion != null && declaredVersion < spec.version!!) {
          return true
        }
      }
    }
    return false
  }

}

abstract class PsLibraryAndroidDependency internal constructor(
  parent: PsAndroidModule,
  containers: Collection<PsAndroidArtifact>
) : PsAndroidDependency(parent, containers), PsLibraryDependency {
  internal val pomDependencies = mutableListOf<PsArtifactDependencySpec>()


  internal fun setDependenciesFromPomFile(value: List<PsArtifactDependencySpec>) {
    pomDependencies.clear()
    pomDependencies.addAll(value)
  }

  fun getTransitiveDependencies(artifactDependencies: PsAndroidArtifactDependencyCollection): Set<PsLibraryAndroidDependency> {
    val transitive = ImmutableSet.builder<PsLibraryAndroidDependency>()
    for (dependency in pomDependencies) {
      // TODO(b/74948244): Include the requested version as a parsed model so that we see any promotions.
      val found = artifactDependencies.findLibraryDependencies(dependency.group, dependency.name)
      transitive.addAll(found)
    }

    return transitive.build()
  }

  override val name: String get() = spec.name

  override val icon: Icon get() = LIBRARY_ICON

  override fun toText(type: PsDependency.TextType): String = spec.toString()

  override fun toString(): String = toText(PLAIN_TEXT)
}

fun ArtifactRepositorySearchResults.toVersionValueDescriptors(): List<ValueDescriptor<String>> =
  results
    .flatMap { it.artifacts }
    .flatMap { it.versions }
    .distinct()
    .sortedDescending()
    .map { version -> ValueDescriptor(version.toString()) }

