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
package com.android.tools.idea.res;

import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.AbstractResourceRepository;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

/**
 * Tests for {@link FrameworkResourceRepository}.
 */
public class FrameworkResourceRepositoryTest extends AndroidTestCase {
  /** Enables printing of repository statistics. */
  private static final boolean PRINT_STATS = false;

  private File myResourceFolder;

  private void deleteRepositoryCache() {
    for (boolean withLocaleResources : new boolean[] {false, true}) {
      //noinspection ResultOfMethodCallIgnored
      FrameworkResourceRepository.getCacheFile(myResourceFolder, withLocaleResources).delete();
    }
  }

  /**
   * Returns the resource folder of the Android framework resources used by LayoutLib.
   */
  @NotNull
  private File getSdkResFolder() {
    IAndroidTarget androidTarget = getLayoutLibTarget(myModule);
    assertNotNull(androidTarget);
    String sdkPlatformPath = Files.simplifyPath(androidTarget.getLocation());
    return new File(sdkPlatformPath + "/data/res");
  }

  @NotNull
  private static IAndroidTarget getLayoutLibTarget(@NotNull Module module) {
    ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(module);

    for (IAndroidTarget target : manager.getTargets()) {
      if (ConfigurationManager.isLayoutLibTarget(target)) {
        manager.setTarget(target);
        break;
      }
    }
    FolderConfiguration folderConfig = new FolderConfiguration();
    Configuration configuration = Configuration.create(manager, null, folderConfig);
    return configuration.getTarget();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myResourceFolder = getSdkResFolder();
    deleteRepositoryCache();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      //noinspection ResultOfMethodCallIgnored
      deleteRepositoryCache();
    } finally {
      super.tearDown();
    }
  }

  public void testLoading() {
    for (boolean withLocaleResources : new boolean[] {true, false}) {
      // Test loading without cache.
      long start = System.currentTimeMillis();
      if (PRINT_STATS) {
        FrameworkResourceRepository.create(myResourceFolder, withLocaleResources, false);
      }
      long loadTimeWithoutCache = System.currentTimeMillis() - start;
      FrameworkResourceRepository fromSourceFiles = FrameworkResourceRepository.create(myResourceFolder, withLocaleResources, true);
      assertFalse(fromSourceFiles.isLoadedFromCache());
      checkContents(fromSourceFiles);

      // Test loading from cache.
      start = System.currentTimeMillis();
      FrameworkResourceRepository fromCache = FrameworkResourceRepository.create(myResourceFolder, withLocaleResources, true);
      long loadTimeWithCache = System.currentTimeMillis() - start;
      assertTrue(fromCache.isLoadedFromCache());
      checkContents(fromCache);
      compareContents(fromSourceFiles, fromCache);

      if (PRINT_STATS) {
        String type = withLocaleResources ? "Load time" : "Load time without locale resources";
        System.out.println(type + " without cache: " + loadTimeWithoutCache / 1000. + " sec, with cache " + loadTimeWithCache / 1000.
                           + " sec");
      }
    }
  }

  private static void compareContents(@NotNull AbstractResourceRepository expected, @NotNull AbstractResourceRepository actual) {
    List<ResourceItem> expectedItems = new ArrayList<>(expected.getAllResourceItems());
    List<ResourceItem> actualItems = new ArrayList<>(actual.getAllResourceItems());

    Comparator<ResourceItem> comparator = (item1, item2) -> {
      int comp = item1.getType().compareTo(item2.getType());
      if (comp != 0) {
        return comp;
      }
      comp = item1.getNamespace().compareTo(item2.getNamespace());
      if (comp != 0) {
        return comp;
      }
      comp = item1.getName().compareTo(item2.getName());
      if (comp != 0) {
        return comp;
      }
      return item1.getSource().compareTo(item2.getSource());
    };
    expectedItems.sort(comparator);
    actualItems.sort(comparator);
    assertEquals(expectedItems.size(), actualItems.size());
    for (int i = 0; i < expectedItems.size(); i++) {
      ResourceItem expectedItem = expectedItems.get(i);
      ResourceItem actualItem = actualItems.get(i);
      assertTrue("Different ResourceItem at position " + i, areEquivalent(expectedItem, actualItem));
      assertEquals("Different FolderConfiguration at position " + i, expectedItem.getConfiguration(), actualItem.getConfiguration());
      assertEquals("Different ResourceValue at position " + i, expectedItem.getResourceValue(), actualItem.getResourceValue());
    }

    for (ResourceType type : ResourceType.values()) {
      List<ResourceItem> expectedPublic = new ArrayList<>(expected.getPublicResourcesOfType(type));
      List<ResourceItem> actualPublic = new ArrayList<>(actual.getPublicResourcesOfType(type));
      assertEquals("Number of public resources doesn't match for type " + type.getName(), expectedPublic.size(), actualPublic.size());
      expectedPublic.sort(comparator);
      actualPublic.sort(comparator);
      for (int i = 0; i < expectedPublic.size(); i++) {
        ResourceItem expectedItem = expectedPublic.get(i);
        ResourceItem actualItem = actualPublic.get(i);
        assertTrue("Public resource difference at position " + i + " for type " + type.getName(), areEquivalent(expectedItem, actualItem));
      }
    }
  }

  private static boolean areEquivalent(@NotNull ResourceItem item1, @NotNull ResourceItem item2) {
    if (!item1.getType().equals(item2.getType())) {
      return false;
    }
    if (!item1.getNamespace().equals(item2.getNamespace())) {
      return false;
    }
    if (!item1.getName().equals(item2.getName())) {
      return false;
    }
    if (!Objects.equals(item1.getLibraryName(), item2.getLibraryName())) {
      return false;
    }
    return Objects.equals(item1.getSource(), item2.getSource());
  }

  private static void checkContents(@NotNull FrameworkResourceRepository repository) {
    checkPublicResourcesCount(repository);
    checkAttributes(repository);
  }

  private static void checkAttributes(@NotNull FrameworkResourceRepository repository) {
    // `typeface` is declared first at top-level and later referenced from within `<declare-styleable>`. Make sure the later reference
    // doesn't shadow the original definition.
    checkAttrDefinition(repository, "typeface");

    // `appCategory` is defined only in attr_manifest.xml.
    checkAttrDefinition(repository, "appCategory");
  }

  private static void checkAttrDefinition(@NotNull FrameworkResourceRepository repository, @NotNull String attrName) {
    ResourceItem attrItem = repository.getResourceItems(ResourceNamespace.ANDROID, ResourceType.ATTR, attrName).get(0);
    AttrResourceValue attrValue = (AttrResourceValue)attrItem.getResourceValue();
    assertFalse(attrValue.getAttributeValues().isEmpty());
  }

  private static void checkPublicResourcesCount(@NotNull FrameworkResourceRepository repository) {
    List<ResourceItem> resourceItems = repository.getAllResourceItems();
    assertTrue("Too few resources: " + resourceItems.size(), resourceItems.size() >= 10000);
    for (ResourceItem item : resourceItems) {
      assertEquals(ResourceNamespace.ANDROID, item.getNamespace());
    }
    ImmutableMap<ResourceType, Integer> expectations = ImmutableMap.of(
        ResourceType.STYLE, 700,
        ResourceType.ATTR, 1200,
        ResourceType.DRAWABLE, 600,
        ResourceType.ID, 60,
        ResourceType.LAYOUT, 20
    );
    for (ResourceType type : ResourceType.values()) {
      Collection<ResourceItem> publicExpected = repository.getPublicResourcesOfType(type);
      Integer minExpected = expectations.get(type);
      if (minExpected != null) {
        assertTrue("Too few public resources of type " + type.getName(), publicExpected.size() >= minExpected);
      }
    }
  }
}
