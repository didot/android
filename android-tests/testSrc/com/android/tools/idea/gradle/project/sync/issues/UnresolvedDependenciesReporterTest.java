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
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.sync.hyperlink.InstallArtifactHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.InstallRepositoryHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.ShowDependencyInProjectStructureHyperlink;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessagesStub;
import com.android.tools.idea.gradle.util.PositionInFile;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

import static com.android.builder.model.SyncIssue.TYPE_UNRESOLVED_DEPENDENCY;
import static com.android.ide.common.repository.SdkMavenRepository.ANDROID;
import static com.android.ide.common.repository.SdkMavenRepository.GOOGLE;
import static com.android.tools.idea.gradle.project.sync.messages.SyncMessageSubject.syncMessage;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link UnresolvedDependenciesReporter}.
 */
public class UnresolvedDependenciesReporterTest extends AndroidGradleTestCase {
  private SyncIssue mySyncIssue;
  private SyncMessagesStub mySyncMessagesStub;
  private UnresolvedDependenciesReporter myReporter;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncIssue = mock(SyncIssue.class);
    mySyncMessagesStub = SyncMessagesStub.replaceSyncMessagesService(getProject());
    myReporter = new UnresolvedDependenciesReporter();
  }

  public void testGetSupportedIssueType() {
    assertEquals(TYPE_UNRESOLVED_DEPENDENCY, myReporter.getSupportedIssueType());
  }

  public void testReportWithRegularJavaLibrary() throws Exception {
    loadSimpleApplication();
    mySyncMessagesStub.clearReportedMessages();

    when(mySyncIssue.getData()).thenReturn("com.google.guava:guava:19.0");

    Module appModule = myModules.getAppModule();
    VirtualFile buildFile = getGradleBuildFile(appModule);
    myReporter.report(mySyncIssue, appModule, buildFile);

    SyncMessage message = mySyncMessagesStub.getFirstReportedMessage();
    assertNotNull(message);
    assertThat(message.getText()).hasLength(1);

    // @formatter:off
    assertAbout(syncMessage()).that(message).hasGroup("Unresolved dependencies")
                                            .hasMessageLine("Failed to resolve: com.google.guava:guava:19.0", 0);
    // @formatter:on

    assertThat(message.getNavigatable()).isInstanceOf(OpenFileDescriptor.class);
    OpenFileDescriptor navigatable = (OpenFileDescriptor)message.getNavigatable();
    assertEquals(buildFile, navigatable.getFile());

    PositionInFile position = message.getPosition();
    assertNotNull(position);
    assertSame(buildFile, position.file);
  }

  // fails in bazel sandbox
  public void ignore_testReportWithConstraintLayout() throws Exception {
    loadSimpleApplication();
    mySyncMessagesStub.clearReportedMessages();

    Module appModule = myModules.getAppModule();

    when(mySyncIssue.getData()).thenReturn("com.android.support.constraint:constraint-layout:+");

    myReporter.report(mySyncIssue, appModule, null);

    SyncMessage message = mySyncMessagesStub.getFirstReportedMessage();
    assertNotNull(message);

    // @formatter:off
    assertAbout(syncMessage()).that(message).hasGroup("Unresolved Android dependencies")
                                            .hasMessageLine("Failed to resolve: com.android.support.constraint:constraint-layout:+", 0);
    // @formatter:on

    List<NotificationHyperlink> quickFixes = message.getQuickFixes();
    int expectedSize = IdeInfo.getInstance().isAndroidStudio() ? 2 : 1;
    assertThat(quickFixes).hasSize(expectedSize);

    NotificationHyperlink quickFix = quickFixes.get(0);
    assertThat(quickFix).isInstanceOf(InstallArtifactHyperlink.class);

    if (IdeInfo.getInstance().isAndroidStudio()) {
      quickFix = quickFixes.get(1);
      assertThat(quickFix).isInstanceOf(ShowDependencyInProjectStructureHyperlink.class);
    }
  }

  public void testReportWithAppCompat() throws Exception {
    loadSimpleApplication();
    mySyncMessagesStub.clearReportedMessages();

    Module appModule = myModules.getAppModule();

    when(mySyncIssue.getData()).thenReturn("com.android.support:appcompat-v7:24.1.1");

    myReporter.report(mySyncIssue, appModule, null);

    SyncMessage message = mySyncMessagesStub.getFirstReportedMessage();
    assertNotNull(message);

    // @formatter:off
    assertAbout(syncMessage()).that(message).hasGroup("Unresolved Android dependencies")
                                            .hasMessageLine("Failed to resolve: com.android.support:appcompat-v7:24.1.1", 0);
    // @formatter:on

    List<NotificationHyperlink> quickFixes = message.getQuickFixes();
    int expectedSize = IdeInfo.getInstance().isAndroidStudio() ? 2 : 1;
    assertThat(quickFixes).hasSize(expectedSize);

    NotificationHyperlink quickFix = quickFixes.get(0);
    assertThat(quickFix).isInstanceOf(InstallRepositoryHyperlink.class);

    InstallRepositoryHyperlink hyperlink = (InstallRepositoryHyperlink)quickFix;
    assertSame(ANDROID, hyperlink.getRepository());

    if (IdeInfo.getInstance().isAndroidStudio()) {
      quickFix = quickFixes.get(1);
      assertThat(quickFix).isInstanceOf(ShowDependencyInProjectStructureHyperlink.class);
    }
  }

  public void testReportWithPlayServices() throws Exception {
    loadSimpleApplication();
    mySyncMessagesStub.clearReportedMessages();

    Module appModule = myModules.getAppModule();

    when(mySyncIssue.getData()).thenReturn("com.google.android.gms:play-services:9.4.0");

    myReporter.report(mySyncIssue, appModule, null);

    SyncMessage message = mySyncMessagesStub.getFirstReportedMessage();
    assertNotNull(message);

    // @formatter:off
    assertAbout(syncMessage()).that(message).hasGroup("Unresolved Android dependencies")
                                            .hasMessageLine("Failed to resolve: com.google.android.gms:play-services:9.4.0", 0);
    // @formatter:on

    List<NotificationHyperlink> quickFixes = message.getQuickFixes();
    int expectedSize = IdeInfo.getInstance().isAndroidStudio() ? 2 : 1;
    assertThat(quickFixes).hasSize(expectedSize);

    NotificationHyperlink quickFix = quickFixes.get(0);
    assertThat(quickFix).isInstanceOf(InstallRepositoryHyperlink.class);

    InstallRepositoryHyperlink hyperlink = (InstallRepositoryHyperlink)quickFix;
    assertSame(GOOGLE, hyperlink.getRepository());

    if (IdeInfo.getInstance().isAndroidStudio()) {
      quickFix = quickFixes.get(1);
      assertThat(quickFix).isInstanceOf(ShowDependencyInProjectStructureHyperlink.class);
    }
  }
}