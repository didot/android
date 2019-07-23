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
package com.android.tools.idea.gradle.project.sync.setup.module;

import com.android.builder.model.SyncIssue;
import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.issues.SyncIssueRegister;
import com.google.common.collect.ImmutableList;
import com.intellij.testFramework.JavaProjectTestCase;
import org.mockito.Mock;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link AndroidModuleSetup}.
 */
public class AndroidModuleSetupTest extends JavaProjectTestCase {
  @Mock private AndroidModuleModel myAndroidModel;
  @Mock private AndroidModuleSetupStep mySetupStep1;
  @Mock private AndroidModuleSetupStep mySetupStep2;
  @Mock private ModuleSetupContext myModuleSetupContext;
  @Mock private IdeAndroidProject myAndroidProject;

  private AndroidModuleSetup myModuleSetup;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    myModuleSetup = new AndroidModuleSetup(mySetupStep1, mySetupStep2);
    when(mySetupStep1.invokeOnSkippedSync()).thenReturn(true); // Only mySetupStep1 can be invoked when sync is skipped.
    when(myModuleSetupContext.getModule()).thenReturn(myModule);
    when(myAndroidModel.getAndroidProject()).thenReturn(myAndroidProject);
    when(myAndroidProject.getSyncIssues()).thenReturn(ImmutableList.of());
  }

  public void testSetUpAndroidModuleWithSyncSkipped() {
    myModuleSetup.setUpModule(myModuleSetupContext, myAndroidModel, true /* sync skipped */);

    // Only 'mySetupStep1' should be invoked when sync is skipped.
    verify(mySetupStep1, times(1)).setUpModule(myModuleSetupContext, myAndroidModel);
    verify(mySetupStep2, times(0)).setUpModule(myModuleSetupContext, myAndroidModel);
  }

  public void testSetUpAndroidModuleWithSyncNotSkipped() {
    myModuleSetup.setUpModule(myModuleSetupContext, myAndroidModel, false /* sync not skipped */);

    // Only 'mySetupStep1' should be invoked when sync is skipped.
    verify(mySetupStep1, times(1)).setUpModule(myModuleSetupContext, myAndroidModel);
    verify(mySetupStep2, times(1)).setUpModule(myModuleSetupContext, myAndroidModel);
  }

  public void testSetUpAndroidModuleRegistersSyncIssues() {
    SyncIssue syncIssue = mock(SyncIssue.class);
    when(myAndroidProject.getSyncIssues()).thenReturn(ImmutableList.of(syncIssue));

    myModuleSetup.setUpModule(myModuleSetupContext, myAndroidModel, false /* sync not skipped */);
    SyncIssueRegister register = SyncIssueRegister.getInstance(myProject);
    assertThat(register.getSyncIssueMap()).containsExactly(myModule, ImmutableList.of(syncIssue));
  }

  public void testSetUpAndroidModuleRegistersSyncIssuesSkipped() {
    SyncIssue syncIssue = mock(SyncIssue.class);
    when(myAndroidProject.getSyncIssues()).thenReturn(ImmutableList.of(syncIssue));

    myModuleSetup.setUpModule(myModuleSetupContext, myAndroidModel, true /* sync skipped */);
    SyncIssueRegister register = SyncIssueRegister.getInstance(myProject);
    assertThat(register.getSyncIssueMap()).containsExactly(myModule, ImmutableList.of(syncIssue));
  }
}