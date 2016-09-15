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
package com.android.tools.idea.gradle.project.sync.issues;

import com.android.builder.model.SyncIssue;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessagesStub;
import com.android.tools.idea.gradle.service.notification.hyperlink.FixGradleVersionInWrapperHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.OpenGradleSettingsHyperlink;
import com.android.tools.idea.testing.legacy.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;

import java.util.List;

import static com.android.builder.model.SyncIssue.TYPE_GRADLE_TOO_OLD;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link UnsupportedGradleReporter}.
 */
public class UnsupportedGradleReporterTest extends AndroidGradleTestCase {
  private SyncIssue mySyncIssue;
  private SyncMessagesStub mySyncMessagesStub;
  private UnsupportedGradleReporter myReporter;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncIssue = mock(SyncIssue.class);
    mySyncMessagesStub = SyncMessagesStub.replaceSyncMessagesService(getProject());
    myReporter = new UnsupportedGradleReporter();
  }

  public void testGetSupportedIssueType() {
    assertEquals(TYPE_GRADLE_TOO_OLD, myReporter.getSupportedIssueType());
  }

  public void testReport() throws Exception {
    loadSimpleApplication();
    Module appModule = myModules.getAppModule();

    String expectedText = "Hello World!";
    when(mySyncIssue.getMessage()).thenReturn(expectedText);
    when(mySyncIssue.getData()).thenReturn("2.14.1");

    myReporter.report(mySyncIssue, appModule, null);

    SyncMessage message = mySyncMessagesStub.getReportedMessage();
    assertNotNull(message);

    assertEquals(SyncMessage.DEFAULT_GROUP, message.getGroup());

    String[] text = message.getText();
    assertThat(text).hasLength(1);
    assertEquals(expectedText, text[0]);

    List<NotificationHyperlink> quickFixes = message.getQuickFixes();
    assertThat(quickFixes).hasSize(2);

    NotificationHyperlink quickFix = quickFixes.get(0);
    assertThat(quickFix).isInstanceOf(FixGradleVersionInWrapperHyperlink.class);
    FixGradleVersionInWrapperHyperlink hyperlink = (FixGradleVersionInWrapperHyperlink)quickFix;
    assertEquals("2.14.1", hyperlink.getGradleVersion());

    quickFix = quickFixes.get(1);
    assertThat(quickFix).isInstanceOf(OpenGradleSettingsHyperlink.class);
  }
}