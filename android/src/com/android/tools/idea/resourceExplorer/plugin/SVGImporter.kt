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
package com.android.tools.idea.resourceExplorer.plugin

import com.android.ide.common.vectordrawable.Svg2Vector
import com.android.resources.ResourceType
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.intellij.openapi.diagnostic.Logger
import com.intellij.testFramework.LightVirtualFile
import java.io.ByteArrayOutputStream
import java.io.File

private val supportedFileTypes = setOf("svg")

/**
 * Importer for SVGs
 */
class SVGImporter : ResourceImporter {
  override val presentableName = "SVG Importer"

  override val userCanEditQualifiers get() = true

  override fun getSupportedFileTypes() = supportedFileTypes

  override fun getSourcePreview(asset: DesignAsset): DesignAssetRenderer? =
    DesignAssetRendererManager.getInstance().getViewer(SVGAssetRenderer::class.java)

  override fun processFiles(files: List<File>): List<DesignAsset> {
    val errorBuilder = StringBuilder()
    val designAssets = files
      .map { convertSVGToVectorDrawable(it, errorBuilder) }
      .map { DesignAsset(it, emptyList(), ResourceType.DRAWABLE) }
    if (errorBuilder.isNotBlank()) {
      Logger.getInstance(SVGImporter::class.java).warn("Error converting SVGs to Vector Drawable\n $errorBuilder")
    }
    return designAssets
  }

  private fun convertSVGToVectorDrawable(it: File, errorBuilder: StringBuilder): LightVirtualFile {
    val byteArrayOutputStream = ByteArrayOutputStream()
    val errorLog = Svg2Vector.parseSvgToXml(it, byteArrayOutputStream)
    if (errorLog.isNotBlank()) {
      errorBuilder
        .append(errorLog)
        .append('\n')
    }
    return LightVirtualFile("${it.nameWithoutExtension}.xml", String(byteArrayOutputStream.toByteArray()))
  }
}
