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
package com.android.tools.idea.resourceExplorer.viewmodel

import com.android.tools.idea.resourceExplorer.importer.DesignAssetImporter
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.android.tools.idea.resourceExplorer.plugin.DesignAssetRendererManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ui.JBUI
import org.jetbrains.android.facet.AndroidFacet
import java.awt.Image

/**
 * ViewModel for [ResourceImportDialogViewModel]
 */
class ResourceImportDialogViewModel(val facet: AndroidFacet,
                                    val assetSets: List<DesignAssetSet>,
                                    private val designAssetImporter: DesignAssetImporter = DesignAssetImporter()
) {

  private val rendererManager = DesignAssetRendererManager.getInstance()
  val fileCount: Int = assetSets.sumBy { it.designAssets.size }

  fun doImport() {
    designAssetImporter.importDesignAssets(assetSets, facet)
  }

  fun getAssetPreview(asset: DesignAsset): Image? {
    return rendererManager
      .getViewer(asset.file)
      .getImage(asset.file, facet.module, JBUI.size(50))
      .get()
  }

  fun getItemNumberString(assetSet: DesignAssetSet) =
    "(${assetSet.designAssets.size} ${StringUtil.pluralize("item", assetSet.designAssets.size)})"
}
