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

import com.android.tools.idea.gradle.project.sync.messages.SyncMessagesStub;
import com.android.tools.idea.gradle.service.notification.hyperlink.DownloadJdk8Hyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.UseEmbeddedJdkHyperlink;
import com.android.tools.idea.testing.AndroidGradleTestCase;

import java.util.List;

import static com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors.registerSyncErrorToSimulate;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;
import static org.jetbrains.android.util.AndroidUtils.isAndroidStudio;

/**
 * Tests for {@link Jdk8RequiredErrorHandler}.
 */
public class Jdk8RequiredErrorHandlerTest extends AndroidGradleTestCase {
  private SyncMessagesStub mySyncMessagesStub;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncMessagesStub = SyncMessagesStub.replaceSyncMessagesService(getProject());
  }

  public void testHandleError() throws Exception {
    registerSyncErrorToSimulate("com/android/jack/api/ConfigNotSupportedException : Unsupported major.minor version 52.0");

    loadProjectAndExpectSyncError(SIMPLE_APPLICATION);

    SyncMessagesStub.NotificationUpdate notificationUpdate = mySyncMessagesStub.getNotificationUpdate();
    assertNotNull(notificationUpdate);

    assertThat(notificationUpdate.getText()).contains("Please use JDK 8 or newer.");

    boolean androidStudio = isAndroidStudio();

    // Verify hyperlinks are correct.
    List<NotificationHyperlink> quickFixes = notificationUpdate.getFixes();
    int expectedQuickFixCount = 1;
    if (androidStudio) {
      // Android Studio has an extra quick-fix: "Use Embedded JDK".
      expectedQuickFixCount++;
    }
    assertThat(quickFixes).hasSize(expectedQuickFixCount);

    NotificationHyperlink quickFix;
    if (androidStudio) {
      quickFix = quickFixes.get(0);
      assertThat(quickFix).isInstanceOf(UseEmbeddedJdkHyperlink.class);
    }

    quickFix = quickFixes.get(expectedQuickFixCount - 1);
    assertThat(quickFix).isInstanceOf(DownloadJdk8Hyperlink.class);
  }
}
