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
package com.android.tools.idea.resourceExplorer.viewmodel

import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.resources.ResourceItem
import com.android.resources.ResourceType
import com.android.tools.idea.res.ResourceNotificationManager
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.resourceExplorer.ImageCache
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.android.tools.idea.resourceExplorer.model.FilterOptions
import com.android.tools.idea.resourceExplorer.rendering.AssetPreviewManager
import com.android.tools.idea.resourceExplorer.rendering.AssetPreviewManagerImpl
import com.android.tools.idea.resources.aar.AarResourceRepository
import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.speedSearch.SpeedSearch
import com.intellij.util.ui.update.MergingUpdateQueue
import org.jetbrains.android.facet.AndroidFacet
import java.util.concurrent.CompletableFuture
import kotlin.properties.Delegates

private val SUPPORTED_RESOURCES = arrayOf(ResourceType.DRAWABLE, ResourceType.COLOR, ResourceType.LAYOUT, ResourceType.MIPMAP)

/**
 * ViewModel for [com.android.tools.idea.resourceExplorer.view.ResourceExplorerView]
 * to manage resources in the provided [facet].
 */
class ProjectResourcesBrowserViewModel(
  facet: AndroidFacet
) : Disposable, ResourceExplorerViewModel {
  /**
   * callback called when the resource model have change. This happen when the facet is changed.
   */
  override var resourceChangedCallback: (() -> Unit)? = null

  override var facet by Delegates.observable(facet) { _, oldFacet, newFacet -> facetUpdated(newFacet, oldFacet) }

  private var resourceVersion: ResourceNotificationManager.ResourceVersion? = null

  private val resourceNotificationManager = ResourceNotificationManager.getInstance(facet.module.project)

  private val dataManager = ResourceDataManager(facet)

  private val imageCache = ImageCache(
    mergingUpdateQueue = MergingUpdateQueue("queue", 1000, true, MergingUpdateQueue.ANY_COMPONENT, this, null, false))

  private val resourceNotificationListener = ResourceNotificationManager.ResourceChangeListener { reason ->
    if (reason.size == 1 && reason.contains(ResourceNotificationManager.Reason.EDIT)) {
      // We don't want to update all resources for every resource file edit.
      // TODO cache the resources, notify the view to only update the rendering of the edited resource.
      return@ResourceChangeListener
    }
    val currentVersion = resourceNotificationManager.getCurrentVersion(facet, null, null)
    if (resourceVersion == currentVersion) {
      return@ResourceChangeListener
    }
    resourceVersion = currentVersion
    resourceChangedCallback?.invoke()
  }

  /**
   * The index in [resourceTypes] of the resource type being used.
   */
  override var resourceTypeIndex = 0
    set(value) {
      if (value in 0 until resourceTypes.size) {
        field = value
        resourceChangedCallback?.invoke()
      }
    }

  override val resourceTypes: Array<ResourceType> get() = SUPPORTED_RESOURCES

  override val selectedTabName: String get() = resourceTypes[resourceTypeIndex].displayName

  override val speedSearch = SpeedSearch(true)

  val filterOptions: FilterOptions = FilterOptions(
    { resourceChangedCallback?.invoke() },
    { speedSearch.updatePattern(it) })

  init {
    subscribeListener(facet)
    Disposer.register(this, imageCache)
  }

  override var assetPreviewManager: AssetPreviewManager = AssetPreviewManagerImpl(facet, imageCache)

  override fun facetUpdated(newFacet: AndroidFacet, oldFacet: AndroidFacet) {
    assetPreviewManager = AssetPreviewManagerImpl(newFacet, imageCache)
    unsubscribeListener(oldFacet)
    subscribeListener(newFacet)
    dataManager.facet = newFacet
    resourceChangedCallback?.invoke()
  }

  private fun subscribeListener(facet: AndroidFacet) {
    resourceNotificationManager
      .addListener(resourceNotificationListener, facet, null, null)
  }

  private fun unsubscribeListener(oldFacet: AndroidFacet) {
    resourceNotificationManager
      .removeListener(resourceNotificationListener, oldFacet, null, null)
  }

  private fun getModuleResources(type: ResourceType): ResourceSection {
    val moduleRepository = ResourceRepositoryManager.getModuleResources(facet)
    val sortedResources = moduleRepository.namespaces
      .flatMap { namespace -> moduleRepository.getResources(namespace, type).values() }
      .sortedBy { it.name }
    return createResourceSection(facet.module.name, sortedResources)
  }

  /**
   * Returns a map from the library name to its resource items
   */
  private fun getLibraryResources(type: ResourceType): List<ResourceSection> {
    val repoManager = ResourceRepositoryManager.getInstance(facet)
    return repoManager.libraryResources.asSequence()
      .flatMap { lib ->
        // Create a section for each library
        lib.namespaces.asSequence()
          .map { namespace -> lib.getResources(namespace, type).values() }
          .filter { it.isNotEmpty() }
          .map { createResourceSection(userReadableLibraryName(lib), it.sortedBy(ResourceItem::getName)) }
      }
      .toList()
  }

  override fun getResourcesLists(): CompletableFuture<List<ResourceSection>> = CompletableFuture.supplyAsync {
    val resourceType = resourceTypes[resourceTypeIndex]
    var resources = listOf(getModuleResources(resourceType))
    if (filterOptions.isShowLibraries) {
      resources += getLibraryResources(resourceType)
    }
    resources
  }

  override fun dispose() {
    unsubscribeListener(facet)
  }

  override fun getData(dataId: String?, selectedAssets: List<DesignAsset>): Any? {
    return dataManager.getData(dataId, selectedAssets)
  }

  override fun openFile(asset: DesignAsset) {
    val psiElement = dataManager.findPsiElement(asset.resourceItem)
    psiElement?.let { NavigationUtil.openFileWithPsiElement(it, true, true) }
  }
}

private fun createResourceSection(libraryName: String, resourceItems: List<ResourceItem>): ResourceSection {
  val designAssets = resourceItems
    .mapNotNull { DesignAsset.fromResourceItem(it) }
    .groupBy(DesignAsset::name)
    .map { (name, assets) -> DesignAssetSet(name, assets) }
  return ResourceSection(libraryName, designAssets)
}

data class ResourceSection(
  val libraryName: String = "",
  val assets: List<DesignAssetSet>)

private fun userReadableLibraryName(lib: AarResourceRepository) =
  lib.libraryName?.let {
    GradleCoordinate.parseCoordinateString(it)?.artifactId
  }
  ?: ""