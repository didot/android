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

import static com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors.registerSyncErrorToSimulate;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.UNSUPPORTED_GRADLE_VERSION;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncQuickFix.FIX_GRADLE_VERSION_IN_WRAPPER_HYPERLINK;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncQuickFix.OPEN_FILE_HYPERLINK;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncQuickFix.OPEN_GRADLE_SETTINGS_HYPERLINK;

import com.android.tools.idea.gradle.project.sync.hyperlink.FixGradleVersionInWrapperHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenGradleSettingsHyperlink;
import com.android.tools.idea.gradle.project.sync.issues.TestSyncIssueUsageReporter;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for {@link UnsupportedGradleVersionErrorHandler}.
 */
public class UnsupportedGradleVersionErrorHandlerTest extends AndroidGradleTestCase {

  private GradleSyncMessagesStub mySyncMessagesStub;
  private TestSyncIssueUsageReporter myUsageReporter;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncMessagesStub = GradleSyncMessagesStub.replaceSyncMessagesService(getProject(), getTestRootDisposable());
    myUsageReporter = TestSyncIssueUsageReporter.replaceSyncMessagesService(getProject(), getTestRootDisposable());
  }

  public void testHandleError() throws Exception {
    Throwable cause = new RuntimeException("Gradle version 2.2 is required.");

    registerSyncErrorToSimulate(cause);

    loadProjectAndExpectSyncError(SIMPLE_APPLICATION);

    GradleSyncMessagesStub.NotificationUpdate notificationUpdate = mySyncMessagesStub.getNotificationUpdate();
    assertNotNull(notificationUpdate);

    assertThat(notificationUpdate.getText()).contains("Gradle version 2.2 is required." + "\n\n" +
                                                      "Please fix the project's Gradle settings.");

    // Verify hyperlinks are correct.
    List<NotificationHyperlink> quickFixes = notificationUpdate.getFixes();
    assertThat(quickFixes).hasSize(3);
    assertThat(quickFixes.get(0)).isInstanceOf(FixGradleVersionInWrapperHyperlink.class);
    verifyOpenGradleWrapperPropertiesFile(getProject(), quickFixes.get(1));
    assertThat(quickFixes.get(2)).isInstanceOf(OpenGradleSettingsHyperlink.class);

    assertEquals(UNSUPPORTED_GRADLE_VERSION, myUsageReporter.getCollectedFailure());
    assertEquals(ImmutableList.of(FIX_GRADLE_VERSION_IN_WRAPPER_HYPERLINK, OPEN_FILE_HYPERLINK, OPEN_GRADLE_SETTINGS_HYPERLINK),
                 myUsageReporter.getCollectedQuickFixes());
  }

  // See https://code.google.com/p/android/issues/detail?id=231658
  public void testHandleErrorWithPlugin2_3AndGradleOlderThan3_3() throws Exception {
    String causeText = "Minimum supported Gradle version is 3.3. Current version is 2.14.1. " +
                       "If using the gradle wrapper, try editing the distributionUrl in " +
                       "/MyApplication/gradle/wrapper/gradle-wrapper.properties to gradle-3.3-all.zip";
    RuntimeException cause = new RuntimeException(causeText);
    registerSyncErrorToSimulate(cause);

    loadProjectAndExpectSyncError(SIMPLE_APPLICATION);

    GradleSyncMessagesStub.NotificationUpdate notificationUpdate = mySyncMessagesStub.getNotificationUpdate();
    assertNotNull(notificationUpdate);

    assertThat(notificationUpdate.getText()).contains("Minimum supported Gradle version is 3.3. Current version is 2.14.1." + "\n\n" +
                                                      "Please fix the project's Gradle settings.");

    // Verify hyperlinks are correct.
    List<NotificationHyperlink> quickFixes = notificationUpdate.getFixes();
    assertThat(quickFixes).hasSize(3);
    assertThat(quickFixes.get(0)).isInstanceOf(FixGradleVersionInWrapperHyperlink.class);
    verifyOpenGradleWrapperPropertiesFile(getProject(), quickFixes.get(1));
    assertThat(quickFixes.get(2)).isInstanceOf(OpenGradleSettingsHyperlink.class);

    assertEquals(UNSUPPORTED_GRADLE_VERSION, myUsageReporter.getCollectedFailure());
    assertEquals(ImmutableList.of(FIX_GRADLE_VERSION_IN_WRAPPER_HYPERLINK, OPEN_FILE_HYPERLINK, OPEN_GRADLE_SETTINGS_HYPERLINK),
                 myUsageReporter.getCollectedQuickFixes());
  }

  public static void verifyOpenGradleWrapperPropertiesFile(@NotNull Project project, @NotNull NotificationHyperlink link) {
    assertThat(link).isInstanceOf(OpenFileHyperlink.class);
    OpenFileHyperlink openFileHyperlink = (OpenFileHyperlink)link;
    assertTrue(openFileHyperlink.toHtml().contains("Open Gradle wrapper properties"));
    assertThat(openFileHyperlink.getFilePath()).isEqualTo(GradleWrapper.find(project).getPropertiesFilePath().getAbsolutePath());
  }
}