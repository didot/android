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
package com.android.tools.idea.ui.resourcemanager.explorer

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceFile
import com.android.ide.common.resources.ResourceMergerItem
import com.android.ide.common.resources.ResourceResolver
import com.android.resources.ResourceType
import com.android.tools.adtui.imagediff.ImageDiffUtil
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.res.addAarDependency
import com.android.tools.idea.res.addAndroidModule
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.resourcemanager.ImageCache
import com.android.tools.idea.ui.resourcemanager.getPNGFile
import com.android.tools.idea.ui.resourcemanager.getPNGResourceItem
import com.android.tools.idea.ui.resourcemanager.getTestDataDirectory
import com.android.tools.idea.ui.resourcemanager.model.Asset
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.FilterOptions
import com.android.tools.idea.ui.resourcemanager.model.TypeFilterKind
import com.android.tools.idea.ui.resourcemanager.model.ResourceAssetSet
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.ImageIcon
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ResourceExplorerListViewModelImplTest {
  private val projectRule = AndroidProjectRule.onDisk()

  private val chain = RuleChain(projectRule, EdtRule())
  @Rule
  fun getChain() = chain

  private lateinit var largeImageCache: ImageCache
  private lateinit var smallImageCache: ImageCache
  private lateinit var resourceResolver: ResourceResolver
  private val disposable = Disposer.newDisposable("ResourceExplorerListViewModelImplTest")

  @Before
  fun setUp() {
    projectRule.fixture.testDataPath = getTestDataDirectory()
    largeImageCache = ImageCache.createLargeImageCache(disposable)
    smallImageCache = ImageCache.createSmallImageCache(disposable)
    resourceResolver = Mockito.mock(ResourceResolver::class.java)
  }

  @After
  fun tearDown() {
    largeImageCache.clear()
    smallImageCache.clear()
    Disposer.dispose(disposable)
  }

  @Test
  fun getDrawablePreviewAndRefresh() {
    var latch = CountDownLatch(1)
    val pngDrawable = projectRule.getPNGResourceItem()
    val viewModel = createViewModel(projectRule.module, ResourceType.DRAWABLE)
    val asset = Asset.fromResourceItem(pngDrawable) as DesignAsset
    val iconSize = 32 // To compensate the 10% margin around the icon
    Mockito.`when`(resourceResolver.resolveResValue(asset.resourceItem.resourceValue)).thenReturn(asset.resourceItem.resourceValue)
    val emptyIcon = viewModel.drawablePreviewManager.getIcon(asset, iconSize, iconSize, { latch.countDown() })
    assertTrue(latch.await(1, TimeUnit.SECONDS))

    val icon = viewModel.drawablePreviewManager.getIcon(asset, iconSize, iconSize, { /* Do nothing */ }) as ImageIcon
    val image = icon.image as BufferedImage
    ImageDiffUtil.assertImageSimilar(getPNGFile(), image, 0.05)

    // Clear the image cache for the resource
    latch = CountDownLatch(1)
    viewModel.clearImageCache(asset)
    val clearedCacheIcon = viewModel.drawablePreviewManager.getIcon(asset, iconSize, iconSize, { latch.countDown() }) as ImageIcon
    assertSame(emptyIcon, clearedCacheIcon) // When cleared, it should return the same instance of an empty icon
    assertTrue(latch.await(1, TimeUnit.SECONDS))

    val refreshedIcon = viewModel.drawablePreviewManager.getIcon(asset, iconSize, iconSize, { /* Do nothing */ }) as ImageIcon
    val refreshedImage = refreshedIcon.image as BufferedImage
    ImageDiffUtil.assertImageSimilar(getPNGFile().name, image, refreshedImage, 0.05)
  }

  @Test
  @Ignore("b/146464696")
  fun getSampleDataPreview() {
    val latch = CountDownLatch(1)
    val sampleDataResource = ResourceRepositoryManager.getAppResources(projectRule.module)!!.getResources(
      // These are bitmap images, preferred for tests.
      ResourceNamespace.TOOLS, ResourceType.SAMPLE_DATA).values().first { it.name == "backgrounds/scenic" }
    Truth.assertThat(sampleDataResource).isNotNull()
    val asset = Asset.fromResourceItem(sampleDataResource!!) as DesignAsset
    val viewModel = createViewModel(projectRule.module, ResourceType.DRAWABLE)

    val iconSize = 32 // To compensate the 10% margin around the icon
    val emptyIcon = viewModel.assetPreviewManager
      .getPreviewProvider(ResourceType.DRAWABLE)
      .getIcon(asset, iconSize, iconSize, { latch.countDown() }) as ImageIcon
    Truth.assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue()
    val emptyImage = emptyIcon.image as BufferedImage
    Truth.assertThat(emptyImage.getRGB(0, 0)).isEqualTo(0) // No value in empty icon

    val icon = viewModel.assetPreviewManager
      .getPreviewProvider(ResourceType.DRAWABLE)
      .getIcon(asset, iconSize, iconSize, { /* Do nothing */ }) as ImageIcon
    val image = icon.image as BufferedImage
    Truth.assertThat(image.getRGB(0, 0)).isNotEqualTo(0)
  }

  @Test
  fun getPngDrawableSummary() {
    val pngDrawable = projectRule.getPNGResourceItem()
    val viewModel = createViewModel(projectRule.module, ResourceType.DRAWABLE)
    val asset = Asset.fromResourceItem(pngDrawable) as DesignAsset
    val assetSet = ResourceAssetSet(asset.name, listOf(asset))
    Mockito.`when`(resourceResolver.resolveResValue(asset.resourceItem.resourceValue)).thenReturn(asset.resourceItem.resourceValue)

    val summary = viewModel.getResourceSummaryMap(assetSet).get(5L, TimeUnit.MINUTES)
    Truth.assertThat(summary).containsEntry("Name", "png")
    Truth.assertThat(summary).containsEntry("Reference", "@drawable/png")
    Truth.assertThat(summary).containsEntry("Type", "PNG")
    Truth.assertThat(summary).containsEntry("Configuration", "default")
    Truth.assertThat(summary).containsEntry("Value", "png.png")
    Truth.assertThat(viewModel.getResourceConfigurationMap(assetSet).get(5L, TimeUnit.MINUTES)).isEmpty()
  }

  @Test
  fun getDataBindingLayoutSummary() {
    projectRule.fixture.copyFileToProject("res/layout/data_binding_layout.xml", "res/layout/data_binding_layout.xml")
    val layoutResource = ResourceRepositoryManager.getModuleResources(projectRule.module.androidFacet!!).getResources(
      ResourceNamespace.RES_AUTO, ResourceType.LAYOUT).values().first()
    val asset = Asset.fromResourceItem(layoutResource) as DesignAsset
    val assetSet = ResourceAssetSet(asset.name, listOf(asset))
    Mockito.`when`(resourceResolver.resolveResValue(asset.resourceItem.resourceValue)).thenReturn(asset.resourceItem.resourceValue)

    val viewModel = createViewModel(projectRule.module, ResourceType.LAYOUT)
    val summary = viewModel.getResourceSummaryMap(assetSet).get(5L, TimeUnit.MINUTES)
    Truth.assertThat(summary).containsEntry("Name", "data_binding_layout")
    Truth.assertThat(summary).containsEntry("Reference", "@layout/data_binding_layout")
    Truth.assertThat(summary).containsEntry("Type", "Data Binding (TextView)")
    Truth.assertThat(summary).containsEntry("Configuration", "default")
    Truth.assertThat(summary).containsEntry("Value", "data_binding_layout.xml")
    Truth.assertThat(viewModel.getResourceConfigurationMap(assetSet).get(5L, TimeUnit.MINUTES)).isEmpty()
  }

  @Test
  fun getSampleDataSummary() {
    val sampleResource = ResourceRepositoryManager.getAppResources(projectRule.module.androidFacet!!).getResources(
      ResourceNamespace.TOOLS, ResourceType.SAMPLE_DATA).values().first { it.name == "avatars" }
    val asset = Asset.fromResourceItem(sampleResource, ResourceType.DRAWABLE)
    val assetSet = ResourceAssetSet(asset.name, listOf(asset))
    Mockito.`when`(resourceResolver.resolveResValue(asset.resourceItem.resourceValue)).thenReturn(asset.resourceItem.resourceValue)

    val viewModel = createViewModel(projectRule.module, ResourceType.DRAWABLE)
    val summary = viewModel.getResourceSummaryMap(assetSet).get(5L, TimeUnit.MINUTES)
    Truth.assertThat(summary).containsEntry("Name", "avatars")
    Truth.assertThat(summary).containsEntry("Reference", "@tools:sample/avatars")
    Truth.assertThat(summary).containsEntry("Configuration", "default")
    Truth.assertThat(viewModel.getResourceConfigurationMap(assetSet).get(5L, TimeUnit.MINUTES)).isEmpty()
  }

  @Test
  fun getThemeAttributeSummary() {
    projectRule.fixture.copyFileToProject("/res/values/colors.xml", "/res/values/colors.xml")
    val colorResource = ResourceRepositoryManager.getModuleResources(projectRule.module.androidFacet!!).getResources(
      ResourceNamespace.RES_AUTO, ResourceType.COLOR).values().first { it.name == "colorPrimary" }
    val attrResource = ResourceMergerItem("my_attr", ResourceNamespace.RES_AUTO, ResourceType.ATTR, null, null, null)
    ResourceFile.createSingle(File("res/values/attrs.xml"), attrResource, "")
    val asset = Asset.fromResourceItem(attrResource, ResourceType.COLOR)
    val assetSet = ResourceAssetSet(asset.name, listOf(asset))
    Mockito.`when`(resourceResolver.findItemInTheme(asset.resourceItem.referenceToSelf)).thenReturn(colorResource.resourceValue)
    Mockito.`when`(resourceResolver.resolveResValue(colorResource.resourceValue)).thenReturn(colorResource.resourceValue)

    val viewModel = createViewModel(projectRule.module, ResourceType.COLOR)
    val summary = viewModel.getResourceSummaryMap(assetSet).get(5L, TimeUnit.MINUTES)
    Truth.assertThat(summary).containsEntry("Name", "my_attr")
    Truth.assertThat(summary).containsEntry("Reference", "?attr/my_attr")
    Truth.assertThat(summary).containsEntry("Configuration", "default")
    Truth.assertThat(summary).containsEntry("Value", "#3F51B5")
    Truth.assertThat(viewModel.getResourceConfigurationMap(assetSet).get(5L, TimeUnit.MINUTES)).isEmpty()
  }

  @Test
  fun getOtherModulesResources() {
    Truth.assertThat(ResourceRepositoryManager.getModuleResources(projectRule.module)!!.allResources).isEmpty()
    val module2Name = "app2"

    runInEdtAndWait {
      addAndroidModule(module2Name, projectRule.project, "com.example.app2") { resourceDir ->
        FileUtil.copy(File(getTestDataDirectory() + "/res/values/colors.xml"),
                      resourceDir.resolve("values/colors.xml"))
      }
    }

    // Use initial module in ViewModel
    val viewModel = createViewModel(projectRule.module, ResourceType.COLOR)

    val resourceSections = viewModel.getOtherModulesResourceLists().get()
    // Other modules resource lists should return resources from modules other than the current one.
    Truth.assertThat(resourceSections).hasSize(1)
    Truth.assertThat(resourceSections.first().libraryName).isEqualTo(module2Name)
    Truth.assertThat(resourceSections.first().assetSets).isNotEmpty()
  }

  @Test
  fun getLibrariesResources() {
    val libraryName = "myLibrary"
    addAarDependency(projectRule.module,
                     libraryName, "com.resources.test") { resDir ->
      FileUtil.copyDir(File(getTestDataDirectory() + "/res"), resDir)
      // Have only some of these resources to be public.
      resDir.parentFile.resolve(SdkConstants.FN_PUBLIC_TXT).writeText(
        """
          color colorPrimary
          color colorPrimaryDark
          drawable png
          """.trimIndent()
      )
    }

    var viewModel = createViewModel(projectRule.module, ResourceType.COLOR)
    Truth.assertThat(ResourceRepositoryManager.getModuleResources(projectRule.module)!!.allResources).isEmpty()
    viewModel.filterOptions.isShowLibraries = true
    val colorSection = viewModel.getCurrentModuleResourceLists().get()
    Truth.assertThat(colorSection).hasSize(2)
    Truth.assertThat(colorSection[0].assetSets).isEmpty()
    Truth.assertThat(colorSection[1].assetSets).isNotEmpty()
    Truth.assertThat(colorSection[1].assetSets).hasSize(2)
    Truth.assertThat(colorSection[1].assetSets[0].assets[0].type).isEqualTo(ResourceType.COLOR)
    Truth.assertThat(colorSection[1].libraryName).contains(libraryName)

    viewModel = createViewModel(projectRule.module, ResourceType.DRAWABLE)
    viewModel.filterOptions.isShowLibraries = true
    val drawableSection = viewModel.getCurrentModuleResourceLists().get()
    Truth.assertThat(drawableSection).hasSize(2)
    Truth.assertThat(drawableSection[0].assetSets).isEmpty()
    Truth.assertThat(drawableSection[1].assetSets).isNotEmpty()
    Truth.assertThat(drawableSection[1].assetSets).hasSize(1)
    Truth.assertThat(drawableSection[1].assetSets[0].assets[0].type).isEqualTo(ResourceType.DRAWABLE)
    Truth.assertThat(drawableSection[1].libraryName).contains(libraryName)
  }

  @Test
  fun getResourceValues() {
    projectRule.fixture.copyFileToProject("res/values/colors.xml", "res/values/colors.xml")
    val viewModel = createViewModel(projectRule.module, ResourceType.COLOR)

    val values = viewModel.getCurrentModuleResourceLists().get()[0].assetSets
    Truth.assertThat(values).isNotNull()
    Truth.assertThat(values.flatMap { it.assets }
                       .map { it.resourceItem.resourceValue?.value })
      .containsExactly("#3F51B5", "#303F9F", "#9dff00")
  }

  @Test
  fun filterVectorDrawables() {
    projectRule.fixture.copyDirectoryToProject("res/drawable/", "res/drawable/")
    val viewModel = createViewModel(projectRule.module, ResourceType.DRAWABLE)

    val unfilteredDrawables = viewModel.getCurrentModuleResourceLists().get()[0].assetSets
    Truth.assertThat(unfilteredDrawables).hasSize(2)

    val filterTypesModel =  viewModel.filterOptions.typeFiltersModel
    val vectorDrawableFilterOption = filterTypesModel.getSupportedFilters(
      ResourceType.DRAWABLE).first { it.kind == TypeFilterKind.XML_TAG && it.value == "vector" }

    filterTypesModel.setEnabled(ResourceType.DRAWABLE, vectorDrawableFilterOption, true)

    val filteredDrawables = viewModel.getCurrentModuleResourceLists().get()[0].assetSets
    Truth.assertThat(filteredDrawables).hasSize(1)
    Truth.assertThat(filteredDrawables[0].name).isEqualTo("vector_drawable")
  }

  private fun createViewModel(module: Module, resourceType: ResourceType): ResourceExplorerListViewModelImpl {
    val facet = AndroidFacet.getInstance(module)!!
    return ResourceExplorerListViewModelImpl(
      facet,
      null,
      resourceResolver,
      FilterOptions.createDefault(),
      resourceType,
      largeImageCache,
      smallImageCache
    )
  }

  private val ResourceExplorerListViewModel.drawablePreviewManager
    get() = this.assetPreviewManager.getPreviewProvider(ResourceType.DRAWABLE)
}