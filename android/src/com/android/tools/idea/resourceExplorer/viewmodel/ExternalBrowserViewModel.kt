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

import com.android.tools.idea.resourceExplorer.importer.ImportersProvider
import com.android.tools.idea.resourceExplorer.importer.QualifierMatcher
import com.android.tools.idea.resourceExplorer.importer.SynchronizationListener
import com.android.tools.idea.resourceExplorer.importer.SynchronizationManager
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.model.DesignAssetListModel
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.android.tools.idea.resourceExplorer.model.getAssetSets
import com.android.tools.idea.resourceExplorer.view.DesignAssetExplorer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import java.awt.Dimension
import java.awt.Image

private val LOG = Logger.getInstance(ExternalBrowserViewModel::class.java)

/**
 * ViewModel for [com.android.tools.idea.resourceExplorer.view.ExternalResourceBrowser]
 * to manage design resources outside the project
 */
class ExternalBrowserViewModel(
  val facet: AndroidFacet,
  private val fileHelper: ResourceFileHelper,
  private val importersProvider: ImportersProvider,
  private val synchronizationManager: SynchronizationManager
) : DesignAssetExplorer {

  override val designAssetListModel = DesignAssetListModel()
  private var directory: VirtualFile? = null

  private var _matcher = QualifierMatcher()

  init {
    synchronizationManager.addListener(object : SynchronizationListener {
      override fun resourceAdded(file: VirtualFile) {
        designAssetListModel.refresh()
      }

      override fun resourceRemoved(file: VirtualFile) {
        designAssetListModel.refresh()
      }
    })
  }

  fun consumeMatcher(matcher: QualifierMatcher) {
    _matcher = matcher
    directory?.let { setDirectory(it) }
  }

  /**
   * Set the directory to browse
   */
  fun setDirectory(directory: VirtualFile) {
    if (directory.isValid && directory.isDirectory) {
      this.directory = directory
      designAssetListModel.setAssets(
        getAssetSets(directory, importersProvider.supportedFileTypes, _matcher)
          .sortedBy { (name, _) -> name })
    } else {
      LOG.error("${directory.path} is not a valid directory")
      return
    }
  }

  /**
   * Import this [DesignAssetSet] into the project
   */
  fun importDesignAssetSet(selectedValue: DesignAssetSet) {
    selectedValue.designAssets.forEach { asset ->
      // TODO use plugin to convert the asset
      fileHelper.copyInProjectResources(asset, selectedValue.name, facet)

    }
  }

  override fun getPreview(asset: DesignAsset, dimension: Dimension): ListenableFuture<out Image?> {
    val extension = asset.file.extension ?: return Futures.immediateFuture(null)
    return importersProvider.getImportersForExtension(extension)
      .firstOrNull()
      ?.getSourcePreview(asset)
      ?.getImage(asset.file, facet.module, dimension)
        ?: Futures.immediateFuture(null)
  }


  fun getSynchronizationStatus(assetSet: DesignAssetSet) =
    synchronizationManager.getSynchronizationStatus(assetSet)

  override fun getStatusLabel(assetSet: DesignAssetSet) = getSynchronizationStatus(assetSet).name

}
