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

import com.android.tools.idea.gradle.project.sync.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessagesStub;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.testing.legacy.AndroidGradleTestCase;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link JdkPreSyncCheck}.
 */
public class JdkPreSyncCheckTest extends AndroidGradleTestCase {
  private IdeSdks myRealIdeSdks;
  private IdeSdks myMockIdeSdks;

  private JdkPreSyncCheck myJdkPreSyncCheck;
  private SyncMessagesStub mySyncMessagesStub;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myRealIdeSdks = IdeSdks.getInstance();

    loadSimpleApplication();

    myMockIdeSdks = IdeComponents.replaceServiceWithMock(IdeSdks.class);
    assertSame(myMockIdeSdks, IdeSdks.getInstance());

    mySyncMessagesStub = SyncMessagesStub.replaceSyncMessagesService(getProject());

    myJdkPreSyncCheck = new JdkPreSyncCheck();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      IdeComponents.replaceService(IdeSdks.class, myRealIdeSdks);
    }
    finally {
      super.tearDown();
    }
  }

  public void testDoCheckCanSyncWithNullJdk() throws Exception {
    when(myMockIdeSdks.getJdk()).thenReturn(null);

    PreSyncCheckResult result = myJdkPreSyncCheck.doCheckCanSync(getProject());
    verifyCheckFailure(result);
  }

  public void testDoCheckWithJdkWithoutHomePath() throws Exception {
    Sdk jdk = mock(Sdk.class);

    when(myMockIdeSdks.getJdk()).thenReturn(jdk);
    when(jdk.getHomePath()).thenReturn(null);

    PreSyncCheckResult result = myJdkPreSyncCheck.doCheckCanSync(getProject());
    verifyCheckFailure(result);
  }

  private void verifyCheckFailure(@NotNull PreSyncCheckResult result) {
    assertFalse(result.isSuccess());

    String expectedMsg = "Please use JDK 8 or newer.";
    assertEquals(expectedMsg, result.getFailureCause());

    SyncMessage message = mySyncMessagesStub.getReportedMessage();
    assertNotNull(message);

    String[] actual = message.getText();
    assertThat(actual).hasLength(1);

    assertEquals(expectedMsg, actual[0]);
  }
}