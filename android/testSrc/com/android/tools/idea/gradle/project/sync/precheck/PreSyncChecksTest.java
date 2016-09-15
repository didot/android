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
package com.android.tools.idea.gradle.project.sync.precheck;

import com.android.tools.idea.testing.legacy.AndroidGradleTestCase;
import com.intellij.openapi.project.Project;

import java.util.List;

import static com.android.tools.idea.gradle.project.sync.precheck.PreSyncCheckResult.SUCCESS;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link PreSyncChecks}.
 */
public class PreSyncChecksTest extends AndroidGradleTestCase {
  public void testCanSyncWithFailure() {
    SyncCheck strategy1 = mock(SyncCheck.class);
    SyncCheck strategy2 = mock(SyncCheck.class);

    PreSyncChecks checks = new PreSyncChecks(strategy1, strategy2);

    Project project = getProject();
    when(strategy1.canSync(project)).thenReturn(SUCCESS);

    PreSyncCheckResult failure = PreSyncCheckResult.failure("Just kidding");
    when(strategy2.canSync(project)).thenReturn(failure);

    PreSyncCheckResult actualResult = checks.canSync(project);
    assertSame(failure, actualResult);

    verify(strategy1).canSync(project);
    verify(strategy2).canSync(project);
  }

  public void testGetChecks() {
    PreSyncChecks checks = new PreSyncChecks();
    List<SyncCheck> strategies = checks.getStrategies();
    assertThat(strategies).hasSize(2);

    assertThat(strategies.get(0)).isInstanceOf(AndroidSdkPreSyncCheck.class);
    assertThat(strategies.get(1)).isInstanceOf(JdkPreSyncCheck.class);
  }
}