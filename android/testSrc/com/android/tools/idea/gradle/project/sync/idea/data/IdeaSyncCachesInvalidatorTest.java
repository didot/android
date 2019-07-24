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
package com.android.tools.idea.gradle.project.sync.idea.data;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.gradle.project.sync.ng.caching.CachedProjectModels;
import com.android.tools.idea.testing.AndroidGradleTestCase;

import java.io.File;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.intellij.openapi.project.Project.DIRECTORY_STORE_FOLDER;

public class IdeaSyncCachesInvalidatorTest extends AndroidGradleTestCase {
  private IdeaSyncCachesInvalidator myInvalidator;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myInvalidator = new IdeaSyncCachesInvalidator();
  }

  public void testCacheIsInvalidated() throws Exception {
    boolean originalUseSingleVariantSync = GradleExperimentalSettings.getInstance().USE_SINGLE_VARIANT_SYNC;
    try {
      GradleExperimentalSettings.getInstance().USE_SINGLE_VARIANT_SYNC = false;
      loadSimpleApplication();
      DataNodeCaches dataNodeCaches = DataNodeCaches.getInstance(getProject());

      // All models are in cache
      assertFalse(dataNodeCaches.isCacheMissingModels(dataNodeCaches.getCachedProjectData()));

      // After invalidating cache, models are not in the cache anymore
      myInvalidator.invalidateCaches();
      assertTrue(dataNodeCaches.isCacheMissingModels(dataNodeCaches.getCachedProjectData()));

      // Sync and check if models are replaced
      requestSyncAndWait();
      assertFalse(dataNodeCaches.isCacheMissingModels(dataNodeCaches.getCachedProjectData()));
    }
    finally {
      GradleExperimentalSettings.getInstance().USE_SINGLE_VARIANT_SYNC = originalUseSingleVariantSync;
    }
  }

  public void testLibrariesFolderIsDeleted() throws Exception {
    loadSimpleApplication();

    // Create .idea/libraries folder under project folder.
    File ideaFolderPath = new File(getBaseDirPath(getProject()), DIRECTORY_STORE_FOLDER);
    File librariesFolderPath = new File(ideaFolderPath, "libraries");
    assertTrue(librariesFolderPath.mkdirs());

    // Verify that libraries folder exists.
    assertExists(librariesFolderPath);

    // Verify that after invalidating cache, libraries folder is deleted.
    myInvalidator.invalidateCaches();
    assertDoesntExist(librariesFolderPath);
  }

  public void testNewSyncCacheIsInvalidated() throws Exception {
    try {
      StudioFlags.NEW_SYNC_INFRA_ENABLED.override(true);

      loadSimpleApplication();

      // Models were cached
      assertTrue(CachedProjectModels.getCacheFilePath(getProject()).exists());

      // After invalidating cache, models are not in the cache anymore
      myInvalidator.invalidateCaches();
      assertFalse(CachedProjectModels.getCacheFilePath(getProject()).exists());

      // Sync and check if models are replaced
      requestSyncAndWait();
      assertTrue(CachedProjectModels.getCacheFilePath(getProject()).exists());
    }
    finally {
      StudioFlags.NEW_SYNC_INFRA_ENABLED.clearOverride();
    }
  }
}
