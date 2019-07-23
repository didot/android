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

import com.android.testutils.TestUtils;
import com.android.tools.idea.AndroidTestCaseHelper;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.sdk.IdeSdks;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.testFramework.JavaProjectTestCase;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Tests for {@link SdkSync}.
 */
public class SdkSyncTest extends JavaProjectTestCase {
  private LocalProperties myLocalProperties;
  private File myAndroidSdkPath;
  private IdeSdks myIdeSdks;

  private SdkSync mySdkSync;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    AndroidTestCaseHelper.removeExistingAndroidSdks();
    myLocalProperties = new LocalProperties(myProject);
    myAndroidSdkPath = TestUtils.getSdk();
    myIdeSdks = IdeSdks.getInstance();
    mySdkSync = new SdkSync(myIdeSdks);
    assertNull(myIdeSdks.getAndroidSdkPath());
    IdeSdks.removeJdksOn(getTestRootDisposable());
  }

  public void testSyncIdeAndProjectAndroidHomesWithIdeSdkAndNoProjectSdk() throws Exception {
    ApplicationManager.getApplication().runWriteAction(() -> {
      myIdeSdks.setAndroidSdkPath(myAndroidSdkPath, null);
    });

    mySdkSync.syncIdeAndProjectAndroidSdks(myLocalProperties);

    assertProjectSdkSet();
  }

  public void testSyncIdeAndProjectAndroidHomesWithIdeSdkAndInvalidProjectSdk() throws Exception {
    ApplicationManager.getApplication().runWriteAction(() -> {
      myIdeSdks.setAndroidSdkPath(myAndroidSdkPath, null);
    });

    myLocalProperties.setAndroidSdkPath(new File("randomPath"));
    myLocalProperties.save();

    mySdkSync.syncIdeAndProjectAndroidSdks(myLocalProperties);

    assertProjectSdkSet();
  }

  public void testSyncIdeAndProjectAndroidHomesWithNoIdeSdkAndValidProjectSdk() throws Exception {
    myLocalProperties.setAndroidSdkPath(myAndroidSdkPath);
    myLocalProperties.save();

    mySdkSync.syncIdeAndProjectAndroidSdks(myLocalProperties);

    assertDefaultSdkSet();
  }

  public void testSyncIdeAndProjectAndroidHomesWhenUserSelectsValidSdkPath() throws Exception {
    SdkSync.FindValidSdkPathTask task = new SdkSync.FindValidSdkPathTask(myIdeSdks) {
      @Nullable
      @Override
      File selectValidSdkPath() {
        return myAndroidSdkPath;
      }
    };
    mySdkSync.syncIdeAndProjectAndroidSdk(myLocalProperties, task, myProject);

    assertProjectSdkSet();
    assertDefaultSdkSet();
  }

  public void testSyncIdeAndProjectAndroidHomesWhenUserDoesNotSelectValidSdkPath() throws Exception {
    SdkSync.FindValidSdkPathTask task = new SdkSync.FindValidSdkPathTask(myIdeSdks) {
      @Nullable
      @Override
      File selectValidSdkPath() {
        return null;
      }
    };
    try {
      mySdkSync.syncIdeAndProjectAndroidSdk(myLocalProperties, task, myProject);
      fail("Expecting ExternalSystemException");
    } catch (ExternalSystemException e) {
      // expected
    }

    assertNull(myIdeSdks.getAndroidSdkPath());
    myLocalProperties = new LocalProperties(myProject);
    assertNull(myLocalProperties.getAndroidSdkPath());
  }

  private void assertDefaultSdkSet() {
    File actual = myIdeSdks.getAndroidSdkPath();
    assertNotNull(actual);
    assertEquals(myAndroidSdkPath.getPath(), actual.getPath());
  }

  private void assertProjectSdkSet() throws Exception {
    myLocalProperties = new LocalProperties(myProject);
    File actual = myLocalProperties.getAndroidSdkPath();
    assertNotNull(actual);
    assertEquals(myAndroidSdkPath.getPath(), actual.getPath());
  }
}
