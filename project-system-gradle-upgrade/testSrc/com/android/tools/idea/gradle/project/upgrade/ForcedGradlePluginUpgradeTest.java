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
package com.android.tools.idea.gradle.project.upgrade;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.ui.Messages.OK;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.testing.TestMessagesDialog;
import com.intellij.mock.MockDumbService;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.ServiceContainerUtil;
import java.util.List;
import org.mockito.Mock;

/**
 * Tests for {@link GradlePluginUpgrade#performForcedPluginUpgrade(Project, GradleVersion, GradleVersion)}}.
 */
public class ForcedGradlePluginUpgradeTest extends PlatformTestCase {
  @Mock private RefactoringProcessorInstantiator myRefactoringProcessorInstantiator;
  @Mock private AgpUpgradeRefactoringProcessor myProcessor;

  private GradleSyncMessagesStub mySyncMessages;
  private TestDialog myOriginalTestDialog;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();

    ServiceContainerUtil.replaceService(project, DumbService.class, new MockDumbService(project), project);
    ServiceContainerUtil.replaceService(project, RefactoringProcessorInstantiator.class, myRefactoringProcessorInstantiator, project);
    when(myRefactoringProcessorInstantiator.createProcessor(same(project), any(), any())).thenReturn(myProcessor);
    mySyncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(project);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myOriginalTestDialog != null) {
        ForcedPluginPreviewVersionUpgradeDialog.setTestDialog(myOriginalTestDialog);
      }
    }
    finally {
      super.tearDown();
    }
  }

  public void testNewerThanLatestKnown() {
    GradleVersion latestPluginVersion = GradleVersion.parse("2.0.0");

    boolean incompatible = GradlePluginUpgrade.versionsAreIncompatible(GradleVersion.parse("3.0.0"), latestPluginVersion);
    assertTrue(incompatible);
    // Can't "upgrade" down from a newer version.
    verifyNoInteractions(myRefactoringProcessorInstantiator);
    verifyNoInteractions(myProcessor);
    assertThat(mySyncMessages.getReportedMessages()).isEmpty();
  }

  public void testUpgradeAccepted() {
    GradleVersion alphaPluginVersion = GradleVersion.parse("2.0.0-alpha9");
    GradleVersion latestPluginVersion = GradleVersion.parse("2.0.0");

    // Simulate user accepting the upgrade.
    myOriginalTestDialog = ForcedPluginPreviewVersionUpgradeDialog.setTestDialog(new TestMessagesDialog(OK));
    when(myRefactoringProcessorInstantiator.showAndGetAgpUpgradeDialog(any(), same(false))).thenReturn(true);
    GradlePluginUpgrade.performForcedPluginUpgrade(getProject(), alphaPluginVersion, latestPluginVersion);
    verify(myRefactoringProcessorInstantiator).showAndGetAgpUpgradeDialog(any(), same(false));
    verify(myProcessor).run();
    assertThat(mySyncMessages.getReportedMessages()).isEmpty();
  }

  public void testUpgradeAcceptedThenCancelled() {
    GradleVersion alphaPluginVersion = GradleVersion.parse("2.0.0-alpha9");
    GradleVersion latestPluginVersion = GradleVersion.parse("2.0.0");
    // Simulate user accepting then cancelling the upgrade.
    myOriginalTestDialog = ForcedPluginPreviewVersionUpgradeDialog.setTestDialog(new TestMessagesDialog(OK));
    when(myRefactoringProcessorInstantiator.showAndGetAgpUpgradeDialog(any(), same(false))).thenReturn(false);
    GradlePluginUpgrade.performForcedPluginUpgrade(getProject(), alphaPluginVersion, latestPluginVersion);
    verify(myRefactoringProcessorInstantiator).showAndGetAgpUpgradeDialog(any(), same(false));
    verifyNoInteractions(myProcessor);
    // TODO(xof): this is suboptimal and should probably show the same message as if we cancelled from the first dialog.
    assertThat(mySyncMessages.getReportedMessages()).isEmpty();
  }

  // See https://code.google.com/p/android/issues/detail?id=227927
  public void testUpgradeDeclined() {
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

    verifyNoInteractions(myRefactoringProcessorInstantiator);
    verifyNoInteractions(myProcessor);
  }
}
