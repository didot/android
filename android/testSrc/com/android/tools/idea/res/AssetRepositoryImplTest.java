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
package com.android.tools.idea.res;

import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.TestProjectPaths;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.List;

import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;

public class AssetRepositoryImplTest extends AndroidGradleTestCase {

  @NotNull private AssetRepositoryImpl myAppRepo;
  @NotNull private AssetRepositoryImpl myLibRepo;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    loadProject(TestProjectPaths.DEPENDENT_MODULES);
    assertNotNull(myAndroidFacet);

    List<AndroidFacet> depedentFacets = AndroidUtils.getAllAndroidDependencies(myAndroidFacet.getModule(), false);
    // In DEPEDENT_MODUEL project, it only contains 1 dependent module caleld lib.
    assertEquals(1, depedentFacets.size());
    AndroidFacet libFacet = depedentFacets.get(0);
    assertNotNull(libFacet);

    myAppRepo = new AssetRepositoryImpl(myAndroidFacet);
    myLibRepo = new AssetRepositoryImpl(libFacet);
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  @SuppressWarnings("ConstantConditions")
  public void testOpenAsset() throws IOException {
    // app/src/main/assets/app.asset.txt
    final String appContentInAppModule = "I am an asset in app module";
    // lib/src/main/assets/lib.asset.txt
    final String libContentInLibModule = "I am an asset in lib module";
    // app/src/main/assets/raw.asset.txt
    final String rawContentInAppModule = "I locate in app module";
    // lib/src/main/assets/raw.asset.txt
    final String rawContentInLibModule = "I locate in lib module";

    // test opening app.asset.txt, should find the asset
    try (BufferedReader br = new BufferedReader(new InputStreamReader(myAppRepo.openAsset("app.asset.txt", 0)))) {
      String assetContent = br.readLine();
      assertEquals(appContentInAppModule, assetContent);
    }

    // test opening lib.asset.txt in app module, should find the asset.
    try (BufferedReader br = new BufferedReader(new InputStreamReader(myAppRepo.openAsset("lib.asset.txt", 0)))) {
      String assetContent = br.readLine();
      assertEquals(libContentInLibModule, assetContent);
    }

    // test opening raw.asset.txt, the content should be same as the one of app module
    try (BufferedReader br = new BufferedReader(new InputStreamReader(myAppRepo.openAsset("raw.asset.txt", 0)))) {
      String assetContent = br.readLine();
      assertEquals(rawContentInAppModule, assetContent);
    }

    // test opening raw.asset.txt in lib, the content should be same as the one of lib module
    try (BufferedReader br = new BufferedReader(new InputStreamReader(myLibRepo.openAsset("raw.asset.txt", 0)))) {
      String assetContent = br.readLine();
      assertEquals(rawContentInLibModule, assetContent);
    }

    // test opening app.asset.txt in lib, should not find the file.
    try (InputStream is = myLibRepo.openAsset("app.asset.txt", 0)) {
      assertNull(is);
    }

    // test opening non-exist file
    try (InputStream is = myAppRepo.openAsset("missing.txt", 0)) {
      assertNull(is);
    }
  }

  public void testOpenNonAsset() throws IOException {
    File imageFileInApp = new File(getProjectFolderPath(), toSystemDependentName("app/src/main/res/drawable/app.png"));
    File imageFileInLib = new File(getProjectFolderPath(), toSystemDependentName("lib/src/main/res/drawable/lib.png"));
    File nonAssetFileInApp = new File(getProjectFolderPath(), toSystemDependentName("app/src/main/res/assets/app_asset.txt"));
    File nonAssetFileInLib = new File(getProjectFolderPath(), toSystemDependentName("lib/src/main/res/assets/lib_asset.txt"));
    File nonExistingFile = new File(getProjectFolderPath(), toSystemDependentName("app/src/main/res/drawable/non_existing.png"));

    assertTrue(imageFileInApp.isFile());
    assertTrue(imageFileInLib.isFile());
    assertTrue(nonAssetFileInApp.isFile());
    assertTrue(nonAssetFileInLib.isFile());
    assertFalse(nonExistingFile.isFile());

    // check can find app.png in app module
    try (InputStream is = myAppRepo.openNonAsset(0, imageFileInApp.getAbsolutePath(), 0)) {
      assertNotNull(is);
    }

    // check can find lib.png in app module
    try (InputStream is = myAppRepo.openNonAsset(0, imageFileInLib.getAbsolutePath(), 0)) {
      assertNotNull(is);
    }

    // check cannot find app.png in lib module
    try (InputStream is = myLibRepo.openNonAsset(0, imageFileInApp.getAbsolutePath(), 0)) {
      assertNull(is);
    }

    // check can find app_asset.txt in app module
    try (InputStream is = myAppRepo.openNonAsset(0, nonAssetFileInApp.getAbsolutePath(), 0)) {
      assertNotNull(is);
    }

    // check can find lib_asset.png in app module
    try (InputStream is = myAppRepo.openNonAsset(0, nonAssetFileInLib.getAbsolutePath(), 0)) {
      assertNotNull(is);
    }

    // check cannot find app_asset.png in lib module
    try (InputStream is = myLibRepo.openNonAsset(0, nonAssetFileInApp.getAbsolutePath(), 0)) {
      assertNull(is);
    }

    // check cannot find nonExistingFile in both module
    try (InputStream is = myAppRepo.openNonAsset(0, nonExistingFile.getAbsolutePath(), 0)) {
      assertNull(is);
    }
  }
}
