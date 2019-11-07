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
package com.android.tools.idea.gradle.project.sync.errors;

import com.android.tools.idea.gradle.project.sync.issues.TestSyncIssueUsageReporter;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenUrlHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.ToggleOfflineModeHyperlink;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.ImmutableList;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.net.UnknownHostException;
import java.util.List;

import static com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors.registerSyncErrorToSimulate;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.UNKNOWN_HOST;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncQuickFix.OPEN_URL_HYPERLINK;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncQuickFix.TOGGLE_OFFLINE_MODE_HYPERLINK;

/**
 * Tests for {@link UnknownHostErrorHandler}.
 */
public class UnknownHostErrorHandlerTest extends AndroidGradleTestCase {

  private GradleSyncMessagesStub mySyncMessagesStub;
  private TestSyncIssueUsageReporter myUsageReporter;
  private Boolean myOriginalOfflineSetting;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncMessagesStub = GradleSyncMessagesStub.replaceSyncMessagesService(getProject(), getTestRootDisposable());
    myUsageReporter = TestSyncIssueUsageReporter.replaceSyncMessagesService(getProject(), getTestRootDisposable());
    myOriginalOfflineSetting = GradleSettings.getInstance(getProject()).isOfflineWork();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      GradleSettings.getInstance(getProject()).setOfflineWork(myOriginalOfflineSetting); // Set back to default value.
    }
    finally {
      super.tearDown();
    }
  }

  public void testHandleError() throws Exception {
    GradleSettings.getInstance(getProject()).setOfflineWork(false);
    Throwable cause = new UnknownHostException("my host");

    registerSyncErrorToSimulate(cause);

    loadProjectAndExpectSyncError(SIMPLE_APPLICATION);

    GradleSyncMessagesStub.NotificationUpdate notificationUpdate = mySyncMessagesStub.getNotificationUpdate();
    assertNotNull(notificationUpdate);

    assertThat(notificationUpdate.getText()).contains("Unknown host 'my host'. You may need to adjust the proxy settings in Gradle.");

    // Verify hyperlinks are correct.
    List<NotificationHyperlink> quickFixes = notificationUpdate.getFixes();
    assertThat(quickFixes).hasSize(2);
    assertThat(quickFixes.get(0)).isInstanceOf(ToggleOfflineModeHyperlink.class);
    assertThat(quickFixes.get(1)).isInstanceOf(OpenUrlHyperlink.class);

    assertEquals(UNKNOWN_HOST, myUsageReporter.getCollectedFailure());
    assertEquals(ImmutableList.of(TOGGLE_OFFLINE_MODE_HYPERLINK, OPEN_URL_HYPERLINK), myUsageReporter.getCollectedQuickFixes());
  }
}