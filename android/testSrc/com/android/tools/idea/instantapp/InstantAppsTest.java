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
package com.android.tools.idea.instantapp;

import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.IdeComponents;
import org.junit.Before;

import static com.android.tools.idea.instantapp.InstantApps.findBaseFeature;
import static com.android.tools.idea.instantapp.InstantApps.getDefaultInstantAppUrl;
import static com.android.tools.idea.testing.TestProjectPaths.INSTANT_APP;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static org.mockito.Mockito.when;

public class InstantAppsTest extends AndroidGradleTestCase {
  private InstantAppSdks instantAppSdks;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    instantAppSdks = new IdeComponents(null, getTestRootDisposable()).mockApplicationService(InstantAppSdks.class);
    when(instantAppSdks.shouldUseSdkLibraryToRun()).thenReturn(true);
  }

  public void testFindBaseFeatureWithInstantApp() throws Exception {
    loadProject(INSTANT_APP, "instant-app");
    assertEquals(myModules.getModule("feature"), findBaseFeature(myAndroidFacet));
  }

  public void testFindBaseFeatureWithoutInstantApp() throws Exception {
    loadProject(SIMPLE_APPLICATION, "app");
    assertNull(findBaseFeature(myAndroidFacet));
  }

  public void testGetDefaultInstantAppUrlWithInstantApp() throws Exception {
    loadProject(INSTANT_APP, "instant-app");
    assertEquals("http://example.com/example", getDefaultInstantAppUrl(myAndroidFacet));
  }

  public void testGetDefaultInstantAppUrlWithoutInstantAppUsingOldLauncher() throws Exception {
    when(instantAppSdks.shouldUseSdkLibraryToRun()).thenReturn(false);
    loadProject(SIMPLE_APPLICATION, "app");
    assertEquals("<<ERROR - NO URL SET>>", getDefaultInstantAppUrl(myAndroidFacet));
  }

  public void testGetDefaultInstantAppUrlWithoutInstantAppUsingNewSdkLib() throws Exception {
    loadProject(SIMPLE_APPLICATION, "app");
    assertEquals("", getDefaultInstantAppUrl(myAndroidFacet));
  }
}