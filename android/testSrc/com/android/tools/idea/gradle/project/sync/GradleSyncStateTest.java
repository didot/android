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

import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.ProjectStructure;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.ThreeState;
import com.intellij.util.messages.MessageBus;
import org.mockito.Mock;

import static com.android.tools.idea.gradle.project.sync.GradleSyncState.GRADLE_SYNC_TOPIC;
import static com.google.common.truth.Truth.assertThat;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link GradleSyncState}.
 */
public class GradleSyncStateTest extends PlatformTestCase {
  @Mock private GradleSyncListener myGradleSyncListener;
  @Mock private GradleSyncState.StateChangeNotification myChangeNotification;
  @Mock private GradleSyncSummary mySummary;
  @Mock private GradleFiles myGradleFiles;
  @Mock private ProjectStructure myProjectStructure;

  private GradleSyncState mySyncState;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    MessageBus messageBus = mock(MessageBus.class);

    mySyncState = new GradleSyncState(getProject(), AndroidProjectInfo.getInstance(getProject()), GradleProjectInfo.getInstance(getProject()),
                                      myGradleFiles, messageBus, myProjectStructure, myChangeNotification, mySummary);

    when(messageBus.syncPublisher(GRADLE_SYNC_TOPIC)).thenReturn(myGradleSyncListener);
  }

  public void testSyncStartedWithSyncSkipped() {
    mySyncState.skippedSyncStarted(false /* no user notification */, new GradleSyncInvoker.Request(TRIGGER_PROJECT_MODIFIED));
    verify(myGradleSyncListener, times(1)).syncStarted(getProject(), true, true);
  }

  public void testSyncStartedWithoutUserNotification() {
    assertFalse(mySyncState.isSyncInProgress());

    // TODO Add trigger for testing?
    boolean syncStarted = mySyncState.syncStarted(false /* no user notification */,
                                                  new GradleSyncInvoker.Request(TRIGGER_PROJECT_MODIFIED));
    assertTrue(syncStarted);
    assertTrue(mySyncState.isSyncInProgress());

    // Trying to start a sync again should not work.
    assertFalse(mySyncState.syncStarted(false, new GradleSyncInvoker.Request(TRIGGER_PROJECT_MODIFIED)));

    verify(myChangeNotification, never()).notifyStateChanged();
    verify(mySummary, times(1)).reset(); // 'reset' should have been called only once.
    verify(myGradleSyncListener, times(1)).syncStarted(getProject(), false, true);
  }

  public void testSyncStartedWithUserNotification() {
    assertFalse(mySyncState.isSyncInProgress());

    // TODO Add trigger for testing?
    boolean syncStarted = mySyncState.syncStarted(true /* user notification */,
                                                  new GradleSyncInvoker.Request(TRIGGER_PROJECT_MODIFIED));
    assertTrue(syncStarted);
    assertTrue(mySyncState.isSyncInProgress());

    verify(myChangeNotification, times(1)).notifyStateChanged();
    verify(mySummary, times(1)).reset(); // 'reset' should have been called only once.
    verify(myGradleSyncListener, times(1)).syncStarted(getProject(), false, true);
  }

  public void testSyncSkipped() {
    long timestamp = -1231231231299L; // Some random number

    mySyncState.syncSkipped(timestamp);

    verify(myChangeNotification, never()).notifyStateChanged();
    verify(mySummary, times(1)).setSyncTimestamp(timestamp);
    verify(myGradleSyncListener, times(1)).syncSkipped(getProject());
  }

  public void testSyncSkippedAfterSyncStarted() {
    long timestamp = -1231231231299L; // Some random number

    // TODO Add trigger for testing?
    mySyncState.syncStarted(false, new GradleSyncInvoker.Request(TRIGGER_PROJECT_MODIFIED));
    mySyncState.syncSkipped(timestamp);
    assertFalse(mySyncState.isSyncInProgress());
  }

  public void testSyncFailed() {
    String msg = "Something went wrong";
    mySyncState.setSyncStartedTimeStamp(0, TRIGGER_PROJECT_MODIFIED);
    mySyncState.syncFailed(msg);

    verify(myChangeNotification, times(1)).notifyStateChanged();
    verify(mySummary, times(1)).setSyncTimestamp(anyLong());
    verify(mySummary, times(1)).setSyncErrorsFound(true);
    verify(myGradleSyncListener, times(1)).syncFailed(getProject(), msg);
    verify(myProjectStructure, times(1)).clearData();
  }

  public void testSyncFailedWithoutSyncStarted() {
    String msg = "Something went wrong";
    mySyncState.setSyncStartedTimeStamp(-1, TRIGGER_PROJECT_MODIFIED);
    mySyncState.syncFailed(msg);
    verify(mySummary, never()).setSyncErrorsFound(true);
    verify(myGradleSyncListener, never()).syncFailed(getProject(), msg);
  }

  public void testSyncEnded() {
    mySyncState.setSyncStartedTimeStamp(0, TRIGGER_PROJECT_MODIFIED);
    mySyncState.syncEnded();
    verify(myChangeNotification, times(1)).notifyStateChanged();
    verify(mySummary, times(1)).setSyncTimestamp(anyLong());
    verify(myGradleSyncListener, times(1)).syncSucceeded(getProject());
  }

  public void testSyncEndedWithoutSyncStarted() {
    mySyncState.setSyncStartedTimeStamp(-1, TRIGGER_PROJECT_MODIFIED);
    mySyncState.syncEnded();
    verify(myGradleSyncListener, never()).syncSucceeded(getProject());
  }

  public void testSetupStarted() {
    mySyncState.setupStarted();

    verify(myGradleSyncListener, times(1)).setupStarted(getProject());
  }

  public void testGetSyncTimesSuccess() {
    // Random time when this was written
    long base = 1493320159894L;
    // Time for Gradle
    long gradleTimeMs = 10000;
    // Time for Ide
    long ideTimeMs = 20000;
    long totalTimeMs = gradleTimeMs + ideTimeMs;

    // Sync started but nothing else
    mySyncState.setSyncStartedTimeStamp(base, TRIGGER_PROJECT_MODIFIED);
    assertEquals("Total time should be 0 as nothing has finished", 0L, mySyncState.getSyncTotalTimeMs());
    assertEquals("Gradle time should be -1 (not finished)", -1L, mySyncState.getSyncGradleTimeMs());
    assertEquals("IDE time should be -1 (not started)", -1L, mySyncState.getSyncIdeTimeMs());

    // Gradle part finished
    mySyncState.setSyncSetupStartedTimeStamp(base + gradleTimeMs);
    assertEquals("Total time should be " + gradleTimeMs, gradleTimeMs, mySyncState.getSyncTotalTimeMs());
    assertEquals("Gradle time should be " + gradleTimeMs, gradleTimeMs, mySyncState.getSyncGradleTimeMs());
    assertEquals("IDE time should be -1 (not finished)", -1L, mySyncState.getSyncIdeTimeMs());

    // Ide part finished
    mySyncState.setSyncEndedTimeStamp(base + totalTimeMs);
    assertEquals("Total time should be " + totalTimeMs, totalTimeMs, mySyncState.getSyncTotalTimeMs());
    assertEquals("Gradle time should be " + gradleTimeMs, gradleTimeMs, mySyncState.getSyncGradleTimeMs());
    assertEquals("IDE time should be " + ideTimeMs, ideTimeMs, mySyncState.getSyncIdeTimeMs());
  }

  public void testGetSyncTimesFailedGradle() {
    // Random time when this was written
    long base = 1493321162360L;
    // Time for failure
    long failTimeMs = 30000;

    // Sync started but nothing else
    mySyncState.setSyncStartedTimeStamp(base, TRIGGER_PROJECT_MODIFIED);
    assertEquals("Total time should be 0 as nothing has finished", 0L, mySyncState.getSyncTotalTimeMs());
    assertEquals("Gradle time should be -1 (not finished)", -1L, mySyncState.getSyncGradleTimeMs());
    assertEquals("IDE time should be -1 (not started)", -1L, mySyncState.getSyncIdeTimeMs());

    // Gradle part failed
    mySyncState.setSyncFailedTimeStamp(base + failTimeMs);
    assertEquals("Total time should be " + failTimeMs, failTimeMs, mySyncState.getSyncTotalTimeMs());
    assertEquals("Gradle time should be -1 (failed)", -1L, mySyncState.getSyncGradleTimeMs());
    assertEquals("IDE time should be -1 (not started)", -1L, mySyncState.getSyncIdeTimeMs());
  }

  public void testGetSyncTimesFailedIde() {
    // Random time when this was written
    long base = 1493321769342L;
    // Time for Gradle
    long gradleTimeMs = 40000;
    // Time for Ide
    long failTimeMs = 50000;
    long totalTimeMs = gradleTimeMs + failTimeMs;

    // Sync started but nothing else
    mySyncState.setSyncStartedTimeStamp(base, TRIGGER_PROJECT_MODIFIED);
    assertEquals("Total time should be 0 as nothing has finished", 0L, mySyncState.getSyncTotalTimeMs());
    assertEquals("Gradle time should be -1 (not finished)", -1L, mySyncState.getSyncGradleTimeMs());
    assertEquals("IDE time should be -1 (not started)", -1L, mySyncState.getSyncIdeTimeMs());

    // Gradle part finished
    mySyncState.setSyncSetupStartedTimeStamp(base + gradleTimeMs);
    assertEquals("Total time should be " + gradleTimeMs, gradleTimeMs, mySyncState.getSyncTotalTimeMs());
    assertEquals("Gradle time should be " + gradleTimeMs, gradleTimeMs, mySyncState.getSyncGradleTimeMs());
    assertEquals("IDE time should be -1 (not finished)", -1L, mySyncState.getSyncIdeTimeMs());

    // Ide part failed
    mySyncState.setSyncFailedTimeStamp(base + totalTimeMs);
    assertEquals("Total time should be " + totalTimeMs, totalTimeMs, mySyncState.getSyncTotalTimeMs());
    assertEquals("Gradle time should be " + gradleTimeMs, gradleTimeMs, mySyncState.getSyncGradleTimeMs());
    assertEquals("IDE time should be -1 (failed)", -1L, mySyncState.getSyncIdeTimeMs());
  }

  public void testGetSyncTimesSkipped() {
    // Random time when this was written
    long base = 1493322274878L;
    // Time it took
    long skippedTimeMs = 60000;

    // Sync started but nothing else
    mySyncState.setSyncStartedTimeStamp(base, TRIGGER_PROJECT_MODIFIED);
    assertEquals("Total time should be 0 as nothing has finished", 0L, mySyncState.getSyncTotalTimeMs());
    assertEquals("Gradle time should be -1 (not started)", -1L, mySyncState.getSyncGradleTimeMs());
    assertEquals("IDE time should be -1 (not started)", -1L, mySyncState.getSyncIdeTimeMs());

    // Sync was skipped
    mySyncState.setSyncEndedTimeStamp(base + skippedTimeMs);
    assertEquals("Total time should be " + skippedTimeMs, skippedTimeMs, mySyncState.getSyncTotalTimeMs());
    assertEquals("Gradle time should be -1 (not started)", -1L, mySyncState.getSyncGradleTimeMs());
    assertEquals("IDE time should be -1 (not started)", -1L, mySyncState.getSyncIdeTimeMs());
  }

  public void testGetFormattedSyncDuration() {
    mySyncState.setSyncStartedTimeStamp(0, TRIGGER_PROJECT_MODIFIED);
    assertEquals("10s", mySyncState.getFormattedSyncDuration(10000));
    assertEquals("2m", mySyncState.getFormattedSyncDuration(120000));
    assertEquals("2m 10s", mySyncState.getFormattedSyncDuration(130000));
    assertEquals("2m 10s 100ms", mySyncState.getFormattedSyncDuration(130100));
    assertEquals("1h 2m 10s 100ms", mySyncState.getFormattedSyncDuration(3730100));
  }

  public void testIsSyncNeeded_IfNeverSynced() {
    when(myGradleFiles.areGradleFilesModified()).thenAnswer((invocation) -> true);
    assertThat(mySyncState.isSyncNeeded()).isSameAs(ThreeState.YES);
  }

  public void testSyncNotNeeded_IfNothingModified() {
    when(myGradleFiles.areGradleFilesModified()).thenAnswer((invocation -> false));
    assertThat(mySyncState.isSyncNeeded()).isSameAs(ThreeState.NO);
  }

}
