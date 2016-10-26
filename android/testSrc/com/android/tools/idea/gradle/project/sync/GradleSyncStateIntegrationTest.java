/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync;

import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.Modules;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.android.facet.AndroidFacet;

import static com.android.tools.idea.gradle.project.sync.GradleSyncState.GRADLE_SYNC_TOPIC;
import static com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH_APPAND_LIB;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link GradleSyncState}.
 */
public class GradleSyncStateIntegrationTest extends AndroidGradleTestCase {
  private Modules myModules;
  private GradleSyncListener mySyncListener;
  private GradleSyncState mySyncState;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Project project = getProject();
    myModules = new Modules(project);
    mySyncListener = mock(GradleSyncListener.class);

    MessageBus messageBus = mock(MessageBus.class);
    when(messageBus.syncPublisher(GRADLE_SYNC_TOPIC)).thenReturn(mySyncListener);

    mySyncState = new GradleSyncState(project, GradleProjectInfo.getInstance(project), messageBus);
  }

  public void testInvalidateLastSync() throws Exception {
    loadProject(PROJECT_WITH_APPAND_LIB);

    Module appModule = myModules.getAppModule();
    AndroidFacet appAndroidFacet = AndroidFacet.getInstance(appModule);
    assertNotNull(appAndroidFacet);
    assertNotNull(appAndroidFacet.getAndroidModel());

    Module libModule = myModules.getModule("lib");
    AndroidFacet libAndroidFacet = AndroidFacet.getInstance(libModule);
    assertNotNull(libAndroidFacet);
    assertNotNull(libAndroidFacet.getAndroidModel());

    mySyncState.invalidateLastSync("Error");
    assertTrue(mySyncState.lastSyncFailed());

    assertNull(appAndroidFacet.getAndroidModel());
    assertNull(libAndroidFacet.getAndroidModel());

    verify(mySyncListener).syncFailed(getProject(), "Error");
  }

  public void testSyncErrorsFailSync() throws Exception {
    loadSimpleApplication();
    mySyncState.getSummary().setSyncErrorsFound(true);

    assertTrue(mySyncState.lastSyncFailed());
  }
}