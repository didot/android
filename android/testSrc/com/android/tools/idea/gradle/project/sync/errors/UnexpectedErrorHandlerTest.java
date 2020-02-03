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
import com.android.tools.idea.gradle.project.sync.hyperlink.FileBugHyperlink;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.ShowLogHyperlink;
import com.android.tools.idea.testing.AndroidGradleTestCase;

import com.google.common.collect.ImmutableList;
import java.util.List;

import static com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors.registerSyncErrorToSimulate;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.UNEXPECTED_ERROR;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncQuickFix.FILE_BUG_HYPERLINK;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncQuickFix.SHOW_LOG_HYPERLINK;

/**
 * Tests for {@link UnexpectedErrorHandler}.
 */
public class UnexpectedErrorHandlerTest extends AndroidGradleTestCase {

  private GradleSyncMessagesStub mySyncMessagesStub;
  private TestSyncIssueUsageReporter myUsageReporter;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncMessagesStub = GradleSyncMessagesStub.replaceSyncMessagesService(getProject(), getTestRootDisposable());
    myUsageReporter = TestSyncIssueUsageReporter.replaceSyncMessagesService(getProject(), getTestRootDisposable());
  }

  public void testHandleError() throws Exception {
    String errMsg = "This is an unexpected error. Please file a bug containing the idea.log file.";
    registerSyncErrorToSimulate(errMsg);

    loadProjectAndExpectSyncError(SIMPLE_APPLICATION);

    GradleSyncMessagesStub.NotificationUpdate notificationUpdate = mySyncMessagesStub.getNotificationUpdate();
    assertNotNull(notificationUpdate);

    assertThat(notificationUpdate.getText()).contains(errMsg);

    // Verify hyperlinks are correct.
    List<NotificationHyperlink> quickFixes = notificationUpdate.getFixes();
    assertThat(quickFixes).hasSize(2);
    assertThat(quickFixes.get(0)).isInstanceOf(FileBugHyperlink.class);
    assertThat(quickFixes.get(1)).isInstanceOf(ShowLogHyperlink.class);

    assertEquals(UNEXPECTED_ERROR, myUsageReporter.getCollectedFailure());
    assertEquals(ImmutableList.of(FILE_BUG_HYPERLINK, SHOW_LOG_HYPERLINK), myUsageReporter.getCollectedQuickFixes());
  }
}