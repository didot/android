/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.post.upgrade;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.ui.Messages.OK;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.testing.IdeComponents;
import com.android.tools.idea.testing.TestMessagesDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.testFramework.PlatformTestCase;
import java.util.List;
import org.mockito.Mock;

/**
 * Tests for {@link GradlePluginUpgrade#performForcedPluginUpgrade(Project, GradleVersion, GradleVersion)}}.
 */
public class ForcedGradlePluginUpgradeTest extends PlatformTestCase {
  @Mock private AndroidPluginInfo myPluginInfo;
  @Mock private AndroidPluginVersionUpdater myVersionUpdater;
  @Mock private GradleSyncState mySyncState;

  private GradleSyncMessagesStub mySyncMessages;
  private TestDialog myOriginalTestDialog;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();
    when(myPluginInfo.getModule()).thenReturn(getModule());

    new IdeComponents(project).replaceProjectService(GradleSyncState.class, mySyncState);
    new IdeComponents(project).replaceProjectService(AndroidPluginVersionUpdater.class, myVersionUpdater);
    mySyncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(project, getTestRootDisposable());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myOriginalTestDialog != null) {
        ForcedPluginPreviewVersionUpgradeDialog.setTestDialog(myOriginalTestDialog);
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testPerformUpgradeWhenUpgradeNotNeeded() {
    GradleVersion latestPluginVersion = GradleVersion.parse("2.0.0");

    boolean upgraded = GradlePluginUpgrade.shouldForcePluginUpgrade(GradleVersion.parse("3.0.0"), latestPluginVersion);
    assertFalse(upgraded);

    verify(mySyncState, never()).syncSucceeded();
    verify(myVersionUpdater, never()).updatePluginVersion(latestPluginVersion, GradleVersion.parse(GRADLE_LATEST_VERSION));
    assertThat(mySyncMessages.getReportedMessages()).isEmpty();
  }

  public void testPerformUpgradeWhenUserAcceptsUpgrade() {
    GradleVersion alphaPluginVersion = GradleVersion.parse("2.0.0-alpha9");
    GradleVersion latestPluginVersion = GradleVersion.parse("2.0.0");

    // Simulate user accepting the upgrade.
    myOriginalTestDialog = ForcedPluginPreviewVersionUpgradeDialog.setTestDialog(new TestMessagesDialog(OK));

    boolean upgraded = GradlePluginUpgrade.performForcedPluginUpgrade(getProject(), alphaPluginVersion, latestPluginVersion);
    assertTrue(upgraded);

    verify(myVersionUpdater).updatePluginVersion(latestPluginVersion, GradleVersion.parse(GRADLE_LATEST_VERSION), alphaPluginVersion);
    assertThat(mySyncMessages.getReportedMessages()).isEmpty();
  }

  // See https://code.google.com/p/android/issues/detail?id=227927
  public void testPerformUpgradeWhenUserDeclinesUpgrade() {
    GradleVersion latestPluginVersion = GradleVersion.parse("2.0.0");
    GradleVersion currentPluginVersion = GradleVersion.parse("2.0.0-alpha9");

    // Simulate user canceling upgrade.
    myOriginalTestDialog = ForcedPluginPreviewVersionUpgradeDialog.setTestDialog(new TestMessagesDialog(Messages.CANCEL));

    boolean upgraded = GradlePluginUpgrade.performForcedPluginUpgrade(getProject(), currentPluginVersion, latestPluginVersion);
    assertFalse(upgraded);

    List<SyncMessage> messages = mySyncMessages.getReportedMessages();
    assertThat(messages).hasSize(1);
    String message = messages.get(0).getText()[1];
    assertThat(message).contains("Please update your project to use version 2.0.0.");

    verify(myVersionUpdater, never()).updatePluginVersion(latestPluginVersion, GradleVersion.parse(GRADLE_LATEST_VERSION));
  }
}
