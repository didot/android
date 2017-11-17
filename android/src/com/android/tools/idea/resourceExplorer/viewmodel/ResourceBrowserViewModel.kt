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

import com.android.tools.idea.resourceExplorer.importer.getAssetSets
import com.android.tools.idea.resourceExplorer.model.DesignAssetListModel
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet

val LOGGER = Logger.getInstance(ResourceBrowserViewModel::class.java)

/**
 * ViewModel to manage external resources
 */
class ResourceBrowserViewModel(
    val facet: AndroidFacet,
    private val fileHelper: ResourceFileHelper = ResourceFileHelper.ResourceFileHelperImpl()
) {

  val designAssetListModel = DesignAssetListModel()

  /**
   * Set the directory to browse
   */
  fun setDirectory(directory: VirtualFile) {
    if (directory.isValid && directory.isDirectory) {
      designAssetListModel.setAssets(getAssetSets(directory).sortedBy { (name, _) -> name })
    }
    else {
      LOGGER.error("${directory.path} is not a valid directory")
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
}