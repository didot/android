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

import com.android.annotations.VisibleForTesting
import com.android.annotations.concurrency.GuardedBy
import com.android.builder.model.AaptOptions
import com.android.ide.common.rendering.api.AttrResourceValue
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleServiceManager
import gnu.trove.TIntObjectHashMap
import gnu.trove.TObjectIntHashMap
import org.jetbrains.android.facet.AndroidFacet
import java.util.*

private const val FIRST_PACKAGE_ID: Byte = 0x02
private val LOG = Logger.getInstance(ResourceIdManager::class.java)

/**
 * Module service responsible for tracking the numeric resource ids we assign to resources, in an attempt to emulate aapt.
 */
class ResourceIdManager private constructor(val module: Module) : ResourceClassGenerator.NumericIdProvider {

  private val facet = AndroidFacet.getInstance(module) ?: error("${ResourceIdManager::class.qualifiedName} used on a non-Android module.")

  companion object {
    @JvmStatic
    fun get(module: Module) = ModuleServiceManager.getService(module, ResourceIdManager::class.java)!!
  }

  /**
   * Class for generating dynamic ids with the given byte as the "package id" part of the 32-bit resource id.
   *
   * The generated ids follow the aapt PPTTEEEE format: 1 byte for package, 1 byte for type, 2 bytes for entry id. The entry IDs are
   * assigned sequentially, starting with the highest possible value and going down. This should mean they won't conflict with "compiled ids"
   * assigned by real aapt in a normal-size project (although there is no mechanism to check that). See [compiledToIdMap].
   */
  class IdProvider(private val packageByte: Byte) {
    private val counters: ShortArray = ShortArray(ResourceType.values().size, { 0xffff.toShort() })

    fun getNext(type: ResourceType): Int {
      return buildResourceId(packageByte, (type.ordinal + 1).toByte(), --counters[type.ordinal])
    }

    override fun toString(): String {
      return Arrays.toString(counters)
    }
  }

  @GuardedBy("this")
  private var nextPackageId: Byte = FIRST_PACKAGE_ID

  @GuardedBy("this")
  private val perNamespaceProviders = hashMapOf<ResourceNamespace, IdProvider>()

  @Synchronized
  private fun resetProviders() {
    nextPackageId = FIRST_PACKAGE_ID
    perNamespaceProviders.clear()
    perNamespaceProviders[ResourceNamespace.RES_AUTO] = IdProvider(0x7f)
    perNamespaceProviders[ResourceNamespace.ANDROID] = IdProvider(0x01)
  }

  init {
    resetProviders()
  }

  /**
   * Ids assigned by this class, on-the-fly. May not be the same as ids chosen by aapt.
   *
   * "Compiled ids" take precedence over these, if known. See [compiledToIdMap].
   */
  @GuardedBy("this")
  private val dynamicToIdMap = TObjectIntHashMap<ResourceReference>()

  /** Inverse of [dynamicToIdMap]. */
  @GuardedBy("this")
  private val dynamicFromIdMap = TIntObjectHashMap<ResourceReference>()

  /**
   * Ids read from the real `R.class` file saved to disk by aapt. They are used instead of dynamic ids, to make sure numeric values compiled
   * into custom views bytecode are consistent with the resource-to-id mapping that this class maintains.
   *
   * These are only read when we know the custom views are compiled against an R class with fields marked as final. See [finalIdsUsed].
   */
  @GuardedBy("this")
  private var compiledToIdMap = TObjectIntHashMap<ResourceReference>()

  /** Inverse of [compiledToIdMap]. */
  @GuardedBy("this")
  private var compiledFromIdMap = TIntObjectHashMap<ResourceReference>()

  /**
   * Whether R classes with final ids are used for compiling custom views.
   */
  val finalIdsUsed: Boolean
    get() {
      return facet.configuration.isAppProject
          && ResourceRepositoryManager.getOrCreateInstance(facet).namespacing == AaptOptions.Namespacing.DISABLED
    }

  /**
   * Looks for resources of type [ResourceType.ID] in R.txt files of all libraries.
   *
   * Because currently we don't parse layouts inside AARs, this is used by [com.android.ide.common.resources.ResourceResolver] as a fallback
   * when it sees reference to an unknown [ResourceType.ID] resource. If this provider knows about the resource in question, the resolver
   * creates a new [com.android.ide.common.rendering.api.ResourceValue] on the fly.
   *
   * TODO(namespaces): parse layouts in AARs and remove this.
   */
  fun isIdDefined(resource: ResourceReference): Boolean {
    assert(resource.resourceType == ResourceType.ID)

    return ResourceRepositoryManager.getOrCreateInstance(facet)
      .libraries
      .asSequence()
      .mapNotNull { (it as FileResourceRepository).allDeclaredIds?.get(resource.name) }
      .any()
  }

  /**
   * Checks R.txt files of all AARs in the project, looking for the numeric ids of attributes in a styleable of the given name.
   *
   * TODO(namespaces): stop reading R.txt, keep track of the IDs of framework resources and use them.
   */
  override fun getDeclaredArrayValues(attrs: List<AttrResourceValue>, styleableName: String): Array<Int?>? {
    @Suppress("UNCHECKED_CAST") // Until we get rid of R.txt parsing, we rely on the right type of library repositories.
    return getDeclaredArrayValues(
      ResourceRepositoryManager.getOrCreateInstance(facet).libraries as MutableList<FileResourceRepository>,
      attrs,
      styleableName
    )
  }

  @VisibleForTesting
  fun getDeclaredArrayValues(
    aarLibraries: List<FileResourceRepository>,
    attrs: List<AttrResourceValue>,
    styleableName: String
  ): Array<Int?>? {
    for (repo in aarLibraries) {
      try {
        return RDotTxtParser.getDeclareStyleableArray(repo.resourceTextFile ?: continue, attrs, styleableName) ?: continue
        // TODO(b/76207181): This code used to reorder the aarLibraries list (which is stored in the repository), in an effort to find
        // related styleables faster next time. This caused concurrent modification exceptions, but we need to evaluate the performance
        // impact of removing this.
      } catch (e: Exception) {
        assert(false) { e.message ?: "failed to parse R.txt" }
        LOG.warn("Error while parsing R.txt", e)
      }
    }
    return null
  }

  @Synchronized
  fun findById(id: Int): ResourceReference? = compiledFromIdMap[id] ?: dynamicFromIdMap[id]

  /**
   * Returns the compiled id of the given resource, if known.
   *
   * See [compiledToIdMap] for an explanation of what this means.
   */
  @Synchronized
  fun getCompiledId(resource: ResourceReference): Int? = compiledToIdMap[resource].let { if (it == 0) null else it }

  /**
   *  Returns the compiled id if known, otherwise returns the dynamic id of the resource (which may need to be generated).
   *
   *  See [compiledToIdMap] and [dynamicToIdMap] for an explanation of what this means.
   */
  @Synchronized
  override fun getOrGenerateId(resource: ResourceReference): Int {
    val compiledId = getCompiledId(resource)
    if (compiledId != null) {
      return compiledId
    }

    val dynamicId = dynamicToIdMap[resource]
    if (dynamicId != 0) {
      return dynamicId
    }

    val provider = perNamespaceProviders.getOrPut(resource.namespace, { IdProvider(nextPackageId++) })
    val newId = provider.getNext(resource.resourceType)

    dynamicToIdMap.put(resource, newId)
    dynamicFromIdMap.put(newId, resource)

    return newId
  }

  @Synchronized
  fun setCompiledIds(toIdMap: TObjectIntHashMap<ResourceReference>, fromIdMap: TIntObjectHashMap<ResourceReference>) {
    compiledToIdMap = toIdMap
    compiledFromIdMap = fromIdMap
  }

  @Synchronized
  fun resetDynamicIds() {
    ResourceClassRegistry.get(module.project).clearCache()

    resetProviders()
    dynamicToIdMap.clear()
    dynamicFromIdMap.clear()
  }
}
