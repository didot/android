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
package com.android.tools.idea.gradle.project.sync;

import com.intellij.openapi.progress.Task;
import com.intellij.testFramework.IdeaTestCase;
import org.mockito.Mock;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link NewGradleSync}.
 */
public class NewGradleSyncTest extends IdeaTestCase {
  @Mock private SyncExecutor mySyncExecutor;
  @Mock private SyncResultHandler myResultHandler;
  @Mock private GradleSyncListener mySyncListener;

  private SyncExecutionCallback myCallback;
  private NewGradleSync myGradleSync;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myCallback = new SyncExecutionCallback();
    myGradleSync = new NewGradleSync(getProject(), mySyncExecutor, myResultHandler);
  }

  public void testSyncWithSuccessfulSync() {
    // Simulate successful sync.
    myCallback.setDone(mock(SyncAction.ProjectModels.class));
    when(mySyncExecutor.syncProject(any())).thenReturn(myCallback);

    myGradleSync.sync(new GradleSyncInvoker.Request(), mySyncListener);

    verify(myResultHandler).onSyncFinished(same(myCallback), any(), same(mySyncListener));
    verify(myResultHandler, never()).onSyncFailed(myCallback, mySyncListener);
  }

  public void testSyncWithFailedSync() {
    // Simulate failed sync.
    myCallback.setRejected(new Throwable("Test error"));
    when(mySyncExecutor.syncProject(any())).thenReturn(myCallback);

    myGradleSync.sync(new GradleSyncInvoker.Request(), mySyncListener);

    verify(myResultHandler, never()).onSyncFinished(same(myCallback), any(), same(mySyncListener));
    verify(myResultHandler).onSyncFailed(myCallback, mySyncListener);
  }

  public void testCreateSyncTaskWithModalExecutionMode() {
    GradleSyncInvoker.Request request = new GradleSyncInvoker.Request();
    request.setRunInBackground(false);

    Task task = myGradleSync.createSyncTask(request, null);
    assertThat(task).isInstanceOf(Task.Modal.class);
  }

  public void testCreateSyncTaskWithBackgroundExecutionMode() {
    GradleSyncInvoker.Request request = new GradleSyncInvoker.Request();
    request.setRunInBackground(true);

    Task task = myGradleSync.createSyncTask(request, null);
    assertThat(task).isInstanceOf(Task.Backgroundable.class);
  }
}