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
package com.android.tools.idea.resourceExplorer.importer

import com.android.ide.common.resources.configuration.DensityQualifier
import com.android.ide.common.resources.configuration.NightModeQualifier
import com.android.ide.common.resources.configuration.ScreenOrientationQualifier
import com.android.resources.Density
import com.android.resources.NightMode
import com.android.resources.ResourceType
import com.android.resources.ScreenOrientation
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.intellij.openapi.Disposable
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DesignAssetImporterTest {

  lateinit var disposable: Disposable

  @Rule
  @JvmField
  val rule = AndroidProjectRule.onDisk()

  @Before
  fun setUp() {
    val dir = rule.fixture.tempDirFixture.findOrCreateDir("res")
    dir.children.forEach { it.delete(this) }
  }

  @Test
  fun importSingleDesignAsset() {
    val file = rule.fixture.tempDirFixture.createFile("file1.png", "/n")
    val designAssetSet = DesignAssetSet("set1", listOf(
      DesignAsset(file, listOf(DensityQualifier(Density.XHIGH)), ResourceType.DRAWABLE)
    ))

    val facet = AndroidFacet.getInstance(rule.module)!!

    val designAssetImporter = DesignAssetImporter()
    designAssetImporter.importDesignAssets(listOf(designAssetSet), facet)
    val moduleResources = ResourceRepositoryManager.getOrCreateInstance(facet).getModuleResources(true)!!
    val item = moduleResources.allResourceItems.first()!!
    Truth.assertThat(item.name).isEqualTo("file1")
    Truth.assertThat(item.resourceValue?.value).endsWith("drawable-xhdpi/file1.png")
  }

  @Test
  fun importDesignAssetsInProject() {
    val designAssets = arrayOf("file0.png", "file1.jpg", "file2.xml")
      .map { rule.fixture.tempDirFixture.createFile(it, "/n") }
      .zip(arrayOf(DensityQualifier(Density.XHIGH),
                   ScreenOrientationQualifier(ScreenOrientation.LANDSCAPE),
                   NightModeQualifier(NightMode.NIGHT)))
      .map { (file, qualifier) ->
        DesignAsset(file, listOf(qualifier), ResourceType.DRAWABLE, "resource")
      }

    val designAssetSet = DesignAssetSet("resource", designAssets)
    val facet = AndroidFacet.getInstance(rule.module)!!

    val designAssetImporter = DesignAssetImporter()
    designAssetImporter.importDesignAssets(listOf(designAssetSet), facet)
    val moduleResources = ResourceRepositoryManager.getOrCreateInstance(facet).getModuleResources(true)!!
    val items = moduleResources.allResourceItems.sortedBy { it.resourceValue?.value }

    var i = 0
    Truth.assertThat(items[i].name).isEqualTo("resource")
    Truth.assertThat(items[i].resourceValue?.value).endsWith("drawable-land/resource.jpg")
    i++

    Truth.assertThat(items[i].name).isEqualTo("resource")
    Truth.assertThat(items[i].resourceValue?.value).endsWith("drawable-night/resource.xml")
    i++

    Truth.assertThat(items[i].name).isEqualTo("resource")
    Truth.assertThat(items[i].resourceValue?.value).endsWith("drawable-xhdpi/resource.png")
  }
}