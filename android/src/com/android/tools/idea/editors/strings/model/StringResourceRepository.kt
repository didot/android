/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.editors.strings.model

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.Locale
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.configuration.Configurable
import com.android.ide.common.util.PathString
import com.android.resources.ResourceType
import com.android.tools.idea.res.LocalResourceRepository
import com.android.tools.idea.res.LocalResourceRepository.EmptyRepository
import com.android.tools.idea.res.MultiResourceRepository
import com.android.tools.idea.res.PsiResourceItem
import com.android.tools.idea.res.ResourceFolderRepository
import com.android.tools.idea.util.toVirtualFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.EdtExecutorService
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** A repository of [ResourceItem]s, keyed by [StringResourceKey] and optionally [Locale]. */
interface StringResourceRepository {
  /**
   * Returns a [List] of all the keys in the [StringResourceRespository].
   *
   * The order of the list will correspond to the order the resources are encountered in files, and
   * dynamic resources will be last.
   */
  fun getKeys(): List<StringResourceKey>

  /** Returns all [ResourceItem]s that match the given [key]. */
  fun getItems(key: StringResourceKey): List<ResourceItem>

  /**
   * Returns the default [ResourceItem], if present, for the given [key]. The default is defined as
   * the [ResourceItem] without a specified [Locale].
   */
  fun getDefaultValue(key: StringResourceKey): ResourceItem?

  /**
   * Returns the [ResourceItem] for the given [key], for the specified [locale], if one exists, and
   * `null` otherwise.
   */
  fun getTranslation(key: StringResourceKey, locale: Locale): ResourceItem?

  /**
   * Schedules the given [Runnable] to run once pending updates to the underlying repository finish.
   */
  fun invokeAfterPendingUpdatesFinish(key: StringResourceKey, callback: Runnable)

  /** Suspends execution until updates to the repository for the given [key] are complete. */
  suspend fun waitForUpdates(key: StringResourceKey)

  companion object {
    /** Returns a new instance of an empty [StringResourceRepository]. */
    @JvmStatic
    fun empty(): StringResourceRepository =
        StringResourceRepositoryImpl(EmptyRepository(ResourceNamespace.RES_AUTO))

    /**
     * Creates a new [StringResourceRepository] from the given [repository]. Note that the
     * repository may not be ready to use as modifications to [repository] initiated by this
     * function may still be in flight.
     *
     * Use [LocalResourceRepository.invokeAfterPendingUpdatesFinish] to wait for these updates to
     * finish.
     */
    @JvmStatic
    fun create(repository: LocalResourceRepository): StringResourceRepository =
        StringResourceRepositoryImpl(repository)
  }
}

/**
 * Implementation class of [StringResourceRepository] interface based on [VirtualFile],
 * [ResourceFolderRepository], and [LocalResourceRepository].
 */
private class StringResourceRepositoryImpl(repository: LocalResourceRepository) :
    StringResourceRepository {
  private val resourceDirectoryRepositoryMap: Map<VirtualFile, ResourceFolderRepository>
  private val dynamicResourceRepository: LocalResourceRepository

  init {
    val repositories: List<LocalResourceRepository> =
        when (repository) {
          is MultiResourceRepository -> repository.localResources
          else -> listOf(repository)
        }
    val repositoryMap: MutableMap<VirtualFile, ResourceFolderRepository> =
        LinkedHashMap(repositories.size)

    var dynamicRepository: LocalResourceRepository? = null

    // Convert resource items to PsiResourceItem to know their locations in files.
    for (localRepository in repositories) {
      if (localRepository is ResourceFolderRepository) {
        repositoryMap[localRepository.resourceDir] = localRepository
        // Use ordering similar to a recursive directory scan.
        localRepository
            .getResources(localRepository.namespace, ResourceType.STRING)
            .values()
            .filter { item -> item !is PsiResourceItem }
            .mapNotNull { item -> item.source }
            .toSortedSet(pathStringComparator)
            .mapNotNull { s -> s.toVirtualFile() }
            .forEach(localRepository::convertToPsiIfNeeded)
      } else {
        assert(dynamicRepository == null) // Should only be one of these in the list.
        dynamicRepository = localRepository
      }
    }

    if (dynamicRepository == null) {
      dynamicRepository = EmptyRepository(ResourceNamespace.RES_AUTO)
    }
    resourceDirectoryRepositoryMap = repositoryMap
    dynamicResourceRepository = dynamicRepository
  }

  override fun getKeys(): List<StringResourceKey> {
    val resourceDirectoryKeys =
        resourceDirectoryRepositoryMap.flatMap { (dir, repo) ->
          repo.getResourceNames(ResourceNamespace.TODO(), ResourceType.STRING).map { name ->
            StringResourceKey(name, dir)
          }
        }
    val dynamicResourceKeys =
        dynamicResourceRepository
            .getResourceNames(ResourceNamespace.TODO(), ResourceType.STRING)
            .map(::StringResourceKey)
    return resourceDirectoryKeys + dynamicResourceKeys
  }

  override fun getItems(key: StringResourceKey): List<ResourceItem> =
      key.getRepository().getResources(ResourceNamespace.TODO(), ResourceType.STRING, key.name)

  override fun getDefaultValue(key: StringResourceKey): ResourceItem? =
      getItems(key).find { it.configuration.localeQualifier == null }

  override fun getTranslation(key: StringResourceKey, locale: Locale): ResourceItem? =
      getItems(key).find { it.hasLocale(locale) }

  override fun invokeAfterPendingUpdatesFinish(key: StringResourceKey, callback: Runnable) =
      key.getRepository()
          .invokeAfterPendingUpdatesFinish(EdtExecutorService.getInstance(), callback)

  override suspend fun waitForUpdates(key: StringResourceKey) {
    suspendCoroutine<Unit> { cont ->
      key.getRepository().invokeAfterPendingUpdatesFinish(EdtExecutorService.getInstance()) {
        cont.resume(Unit)
      }
    }
  }

  /** Returns the [LocalResourceRepository] for `this` [StringResourceKey]. */
  private fun StringResourceKey.getRepository(): LocalResourceRepository =
      if (directory == null) dynamicResourceRepository
      else requireNotNull(resourceDirectoryRepositoryMap[directory])

  /**
   * Returns `true` iff the configuration's localeQualifier is non-`null` and corresponds to the
   * given [locale].
   */
  private fun Configurable.hasLocale(locale: Locale): Boolean =
      configuration.localeQualifier?.let { Locale.create(it) == locale } ?: false

  companion object {
    /**
     * Comparator to ensure ordering of resources is consistent with the order they show up in
     * files. This compares the two [PathString]s segment by segment.
     *
     * TODO(b/232444069): Remove this once PathString's comparator is updated to be equivalent.
     */
    val pathStringComparator: Comparator<PathString> = Comparator { p1, p2 ->
      val segments1 = p1.segments
      val segments2 = p2.segments
      (segments1 zip segments2)
          .asSequence()
          .map { (s1, s2) -> compareValues(s1, s2) }
          .find { it != 0 }
          ?: (segments1.size - segments2.size)
    }
  }
}
