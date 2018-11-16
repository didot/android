/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.ng;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.ProjectBuildFileChecksums;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.project.sync.ng.caching.CachedProjectModels;
import com.android.tools.idea.gradle.project.sync.ng.caching.ModelNotFoundInCacheException;
import com.android.tools.idea.gradle.project.sync.ng.variantonly.VariantOnlyProjectModels;
import com.android.tools.idea.gradle.project.sync.ng.variantonly.VariantOnlySyncOptions;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link NewGradleSync}.
 */
public class NewGradleSyncTest extends IdeaTestCase {
  @Mock private GradleSyncMessages mySyncMessages;
  @Mock private SyncExecutor mySyncExecutor;
  @Mock private SyncResultHandler myResultHandler;
  @Mock private GradleSyncListener mySyncListener;
  @Mock private ProjectBuildFileChecksums.Loader myBuildFileChecksumsLoader;
  @Mock private CachedProjectModels.Loader myProjectModelsLoader;
  @Mock private SyncExecutionCallback.Factory myCallbackFactory;

  private SyncExecutionCallback myCallback;
  private NewGradleSync myGradleSync;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    StudioFlags.COMPOUND_SYNC_ENABLED.override(false);
    myCallback = new SyncExecutionCallback();
    myGradleSync =
      new NewGradleSync(getProject(), mySyncMessages, mySyncExecutor, myResultHandler, myBuildFileChecksumsLoader, myProjectModelsLoader,
                        myCallbackFactory);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      StudioFlags.COMPOUND_SYNC_ENABLED.clearOverride();
    }
    finally {
      super.tearDown();
    }
  }

  public void testSyncFromCachedModels() throws Exception {
    GradleSyncInvoker.Request request = GradleSyncInvoker.Request.projectModified();
    request.useCachedGradleModels = true;

    Project project = getProject();
    ProjectBuildFileChecksums buildFileChecksums = mock(ProjectBuildFileChecksums.class);
    when(myBuildFileChecksumsLoader.loadFromDisk(project)).thenReturn(buildFileChecksums);
    when(buildFileChecksums.canUseCachedData()).thenReturn(true);

    CachedProjectModels projectModelsCache = mock(CachedProjectModels.class);
    when(myProjectModelsLoader.loadFromDisk(project)).thenReturn(projectModelsCache);

    myGradleSync.sync(request, mySyncListener);

    verify(mySyncMessages).removeAllMessages();
    verify(myResultHandler).onSyncSkipped(same(projectModelsCache), any(), any(), same(mySyncListener), any());
  }

  public void testFailedSyncFromCachedModels() throws Exception {
    GradleSyncInvoker.Request request = GradleSyncInvoker.Request.projectModified();
    request.useCachedGradleModels = true;

    Project project = getProject();
    ProjectBuildFileChecksums buildFileChecksums = mock(ProjectBuildFileChecksums.class);
    when(myBuildFileChecksumsLoader.loadFromDisk(project)).thenReturn(buildFileChecksums);
    when(buildFileChecksums.canUseCachedData()).thenReturn(true);

    CachedProjectModels projectModelsCache = mock(CachedProjectModels.class);
    when(myProjectModelsLoader.loadFromDisk(project)).thenReturn(projectModelsCache);

    // Simulate loading models from cache fails.
    Answer getTaskIdAndTrowError = new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        ExternalSystemTaskId taskId = invocation.getArgument(4);
        myCallback.setDone(mock(SyncProjectModels.class), taskId);
        throw new ModelNotFoundInCacheException(GradleModuleModel.class);
      }
    };
    doAnswer(getTaskIdAndTrowError).when(myResultHandler)
                                   .onSyncSkipped(same(projectModelsCache), any(), any(), same(mySyncListener), any());

    when(myCallbackFactory.create()).thenReturn(myCallback);
    doNothing().when(mySyncExecutor).syncProject(any(), eq(myCallback));

    myGradleSync.sync(request, mySyncListener);

    // Full sync should have been executed.
    verify(mySyncMessages).removeAllMessages();
    verify(myResultHandler).onSyncFinished(same(myCallback), any(), any(), same(mySyncListener));
  }

  public void testSyncFromCachedModelsWithoutBuildFileChecksums() {
    GradleSyncInvoker.Request request = GradleSyncInvoker.Request.projectModified();
    request.useCachedGradleModels = true;

    Project project = getProject();
    ProjectBuildFileChecksums buildFileChecksums = mock(ProjectBuildFileChecksums.class);
    when(myBuildFileChecksumsLoader.loadFromDisk(project)).thenReturn(buildFileChecksums);
    when(buildFileChecksums.canUseCachedData()).thenReturn(false);

    myCallback.setDone(mock(SyncProjectModels.class), mock(ExternalSystemTaskId.class));
    when(myCallbackFactory.create()).thenReturn(myCallback);
    doNothing().when(mySyncExecutor).syncProject(any(), eq(myCallback));

    myGradleSync.sync(request, mySyncListener);

    verify(mySyncMessages).removeAllMessages();
    // Full sync should have been executed.
    verify(myResultHandler).onSyncFinished(same(myCallback), any(), any(), same(mySyncListener));
  }

  public void testSyncFromCachedModelsWithoutModelsCache() {
    GradleSyncInvoker.Request request = GradleSyncInvoker.Request.projectModified();
    request.useCachedGradleModels = true;

    Project project = getProject();
    ProjectBuildFileChecksums buildFileChecksums = mock(ProjectBuildFileChecksums.class);
    when(myBuildFileChecksumsLoader.loadFromDisk(project)).thenReturn(buildFileChecksums);
    when(buildFileChecksums.canUseCachedData()).thenReturn(true);

    myCallback.setDone(mock(SyncProjectModels.class), mock(ExternalSystemTaskId.class));
    when(myCallbackFactory.create()).thenReturn(myCallback);
    doNothing().when(mySyncExecutor).syncProject(any(), eq(myCallback));

    myGradleSync.sync(request, mySyncListener);

    verify(mySyncMessages).removeAllMessages();
    // Full sync should have been executed.
    verify(myResultHandler).onSyncFinished(same(myCallback), any(), any(), same(mySyncListener));
  }

  public void testSyncFromCachedModelsWithoutBuildFileOutdatedChecksums() {
    GradleSyncInvoker.Request request = GradleSyncInvoker.Request.projectModified();
    request.useCachedGradleModels = true;

    Project project = getProject();
    ProjectBuildFileChecksums buildFileChecksums = mock(ProjectBuildFileChecksums.class);
    when(myBuildFileChecksumsLoader.loadFromDisk(project)).thenReturn(buildFileChecksums);
    when(buildFileChecksums.canUseCachedData()).thenReturn(true);

    when(myProjectModelsLoader.loadFromDisk(project)).thenReturn(null);

    myCallback.setDone(mock(SyncProjectModels.class), mock(ExternalSystemTaskId.class));
    when(myCallbackFactory.create()).thenReturn(myCallback);
    doNothing().when(mySyncExecutor).syncProject(any(), eq(myCallback));

    myGradleSync.sync(request, mySyncListener);

    verify(mySyncMessages).removeAllMessages();
    // Full sync should have been executed.
    verify(myResultHandler).onSyncFinished(same(myCallback), any(), any(), same(mySyncListener));
  }

  public void testSyncWithSuccessfulSync() {
    // Simulate successful sync.
    GradleSyncInvoker.Request request = GradleSyncInvoker.Request.projectModified();

    myCallback.setDone(mock(SyncProjectModels.class), mock(ExternalSystemTaskId.class));
    when(myCallbackFactory.create()).thenReturn(myCallback);
    doNothing().when(mySyncExecutor).syncProject(any(), eq(myCallback));

    myGradleSync.sync(request, mySyncListener);

    verify(mySyncMessages).removeAllMessages();
    verify(myResultHandler).onSyncFinished(same(myCallback), any(), any(), same(mySyncListener));
    verify(myResultHandler, never()).onSyncFailed(myCallback, mySyncListener);
  }

  public void testSyncWithFailedSync() {
    // Simulate failed sync.
    GradleSyncInvoker.Request request = GradleSyncInvoker.Request.projectModified();

    myCallback.setRejected(new Throwable("Test error"));
    when(myCallbackFactory.create()).thenReturn(myCallback);
    doNothing().when(mySyncExecutor).syncProject(any(), eq(myCallback));

    myGradleSync.sync(request, mySyncListener);

    verify(mySyncMessages).removeAllMessages();
    verify(myResultHandler, never()).onSyncFinished(same(myCallback), any(), any(), same(mySyncListener));
    verify(myResultHandler).onSyncFailed(myCallback, mySyncListener);
  }

  public void testCreateSyncTaskWithModalExecutionMode() {
    GradleSyncInvoker.Request request = GradleSyncInvoker.Request.projectModified();
    request.runInBackground = false;

    Task task = myGradleSync.createSyncTask(request, null);
    assertThat(task).isInstanceOf(Task.Modal.class);
  }

  public void testCreateSyncTaskWithBackgroundExecutionMode() {
    GradleSyncInvoker.Request request = GradleSyncInvoker.Request.projectModified();
    request.runInBackground = true;

    Task task = myGradleSync.createSyncTask(request, null);
    assertThat(task).isInstanceOf(Task.Backgroundable.class);
  }

  public void testSyncWithVariantOnlySuccessfulSync() {
    // Simulate successful sync.
    GradleSyncInvoker.Request request = GradleSyncInvoker.Request.projectModified();
    request.variantOnlySyncOptions = mock(VariantOnlySyncOptions.class);

    myCallback.setDone(mock(VariantOnlyProjectModels.class), mock(ExternalSystemTaskId.class));
    when(myCallbackFactory.create()).thenReturn(myCallback);
    doNothing().when(mySyncExecutor).syncProject(any(), eq(myCallback));

    myGradleSync.sync(request, mySyncListener);

    verify(mySyncMessages).removeAllMessages();
    verify(myResultHandler).onVariantOnlySyncFinished(same(myCallback), any(), any(), same(mySyncListener));
    verify(myResultHandler, never()).onSyncFailed(myCallback, mySyncListener);
  }

  public void testSyncWithVariantOnlyFailedSync() {
    // Simulate failed sync.
    GradleSyncInvoker.Request request = GradleSyncInvoker.Request.projectModified();
    request.variantOnlySyncOptions = mock(VariantOnlySyncOptions.class);

    myCallback.setRejected(new Throwable("Test error"));
    when(myCallbackFactory.create()).thenReturn(myCallback);
    doNothing().when(mySyncExecutor).syncProject(any(), eq(myCallback));

    myGradleSync.sync(request, mySyncListener);

    verify(mySyncMessages).removeAllMessages();
    verify(myResultHandler, never()).onSyncFinished(same(myCallback), any(), any(), same(mySyncListener));
    verify(myResultHandler).onSyncFailed(myCallback, mySyncListener);
  }

  public void testCompoundSync() {
    try {
      StudioFlags.NEW_SYNC_INFRA_ENABLED.override(true);
      StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(true);
      StudioFlags.COMPOUND_SYNC_ENABLED.override(true);

      GradleSyncInvoker.Request request = GradleSyncInvoker.Request.projectModified();
      when(myCallbackFactory.create()).thenReturn(myCallback);

      doAnswer(invocation -> {
        myCallback.setDone(mock(SyncProjectModels.class), mock(ExternalSystemTaskId.class));
        return null;
      }).when(mySyncExecutor).syncProject(any(), same(myCallback), any(), any(), any(), eq(true));

      myGradleSync.sync(request, mySyncListener);

      verify(mySyncExecutor).syncProject(any(), same(myCallback), eq(null), any(), any(), eq(true));

      verify(myResultHandler).onCompoundSyncModels(same(myCallback), any(), any(), same(mySyncListener), eq(false));
      verify(myResultHandler).onCompoundSyncFinished(same(mySyncListener));
      verify(myResultHandler, never()).onSyncFailed(same(myCallback), same(mySyncListener));
    }
    finally {
      StudioFlags.NEW_SYNC_INFRA_ENABLED.clearOverride();
      StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.clearOverride();
      StudioFlags.COMPOUND_SYNC_ENABLED.clearOverride();
    }
  }

  public void testCompoundSyncForVariantOnlySync() {
    try {
      StudioFlags.NEW_SYNC_INFRA_ENABLED.override(true);
      StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(true);
      StudioFlags.COMPOUND_SYNC_ENABLED.override(true);

      GradleSyncInvoker.Request request = GradleSyncInvoker.Request.projectModified();
      request.variantOnlySyncOptions = new VariantOnlySyncOptions(new File(""), "", "", null, true);
      when(myCallbackFactory.create()).thenReturn(myCallback);

      doAnswer(invocation -> {
        myCallback.setDone(mock(VariantOnlyProjectModels.class), mock(ExternalSystemTaskId.class));
        return null;
      }).when(mySyncExecutor).syncProject(any(), same(myCallback), eq(request.variantOnlySyncOptions), any(), any(), anyBoolean());

      myGradleSync.sync(request, mySyncListener);

      verify(mySyncExecutor).syncProject(any(), same(myCallback), eq(request.variantOnlySyncOptions), any(), any(), anyBoolean());

      verify(myResultHandler).onCompoundSyncModels(same(myCallback), any(), any(), same(mySyncListener), eq(true));
      verify(myResultHandler).onCompoundSyncFinished(same(mySyncListener));
      verify(myResultHandler, never()).onSyncFailed(same(myCallback), same(mySyncListener));
    }
    finally {
      StudioFlags.NEW_SYNC_INFRA_ENABLED.clearOverride();
      StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.clearOverride();
      StudioFlags.COMPOUND_SYNC_ENABLED.clearOverride();
    }
  }
}