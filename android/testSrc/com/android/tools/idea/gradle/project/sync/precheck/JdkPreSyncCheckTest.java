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

import static com.android.tools.idea.gradle.project.sync.messages.SyncMessageSubject.syncMessage;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.Jdks;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import java.io.File;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for {@link JdkPreSyncCheck}.
 */
public class JdkPreSyncCheckTest extends AndroidGradleTestCase {
  private IdeSdks myMockIdeSdks;
  private Jdks myMockJdks;

  private JdkPreSyncCheck myJdkPreSyncCheck;
  private GradleSyncMessagesStub mySyncMessagesStub;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    loadSimpleApplication();

    myMockIdeSdks = new IdeComponents(getProject()).mockApplicationService(IdeSdks.class);
    myMockJdks = new IdeComponents(getProject()).mockApplicationService(Jdks.class);
    assertSame(myMockIdeSdks, IdeSdks.getInstance());

    mySyncMessagesStub = GradleSyncMessagesStub.replaceSyncMessagesService(getProject());

    myJdkPreSyncCheck = new JdkPreSyncCheck();
    StudioFlags.ALLOW_DIFFERENT_JDK_VERSION.override(false);
  }

  @Override
  public void tearDown() throws Exception {
    StudioFlags.ALLOW_DIFFERENT_JDK_VERSION.clearOverride();
    super.tearDown();
  }

  public void testDoCheckCanSyncWithNullJdk() {
    when(myMockIdeSdks.getJdk()).thenReturn(null);

    PreSyncCheckResult result = myJdkPreSyncCheck.doCheckCanSyncAndTryToFix(getProject());
    verifyCheckFailure(result, "Jdk location is not set");
  }

  public void testDoCheckWithJdkWithoutHomePath() {
    Sdk jdk = mock(Sdk.class);

    when(myMockIdeSdks.getJdk()).thenReturn(jdk);
    when(jdk.getHomePath()).thenReturn(null);

    PreSyncCheckResult result = myJdkPreSyncCheck.doCheckCanSyncAndTryToFix(getProject());
    verifyCheckFailure(result, "Could not find valid Jdk home from the selected Jdk location");
  }

  public void testDoCheckWithJdkWithIncompatibleVersion() {
    Sdk jdk = mock(Sdk.class);
    String pathToJdk10 = "/path/to/jdk10";

    when(myMockIdeSdks.getJdk()).thenReturn(jdk);
    when(jdk.getHomePath()).thenReturn(pathToJdk10);
    when(myMockIdeSdks.getRunningVersionOrDefault()).thenReturn(JavaSdkVersion.JDK_1_8);
    when(myMockJdks.findVersion(new File(pathToJdk10))).thenReturn(JavaSdkVersion.JDK_10);

    PreSyncCheckResult result = myJdkPreSyncCheck.doCheckCanSyncAndTryToFix(getProject());
    verifyCheckFailure(result,
                       "The version of selected Jdk doesn't match the Jdk used by Studio. Please choose a valid Jdk 8 directory.\n" +
                       "Selected Jdk location is /path/to/jdk10.");
  }

  public void testDoCheckWithJdkWithIncompatibleVersionNoCheck() {
    StudioFlags.ALLOW_DIFFERENT_JDK_VERSION.override(true);
    Sdk jdk = mock(Sdk.class);
    String pathToJdk10 = "/path/to/jdk10";

    when(myMockIdeSdks.getJdk()).thenReturn(jdk);
    when(jdk.getHomePath()).thenReturn(pathToJdk10);
    when(myMockIdeSdks.getRunningVersionOrDefault()).thenReturn(JavaSdkVersion.JDK_1_8);
    when(myMockJdks.findVersion(new File(pathToJdk10))).thenReturn(JavaSdkVersion.JDK_10);

    PreSyncCheckResult result = myJdkPreSyncCheck.doCheckCanSyncAndTryToFix(getProject());
    verifyCheckFailure(result,
                       "The Jdk installation is invalid.\n" +
                       "Selected Jdk location is /path/to/jdk10.");
  }

  public void testDoCheckWithJdkWithInvalidJdkInstallation() {
    Sdk jdk = mock(Sdk.class);
    String pathToJdk8 = "/path/to/jdk8";

    when(myMockIdeSdks.getJdk()).thenReturn(jdk);
    when(jdk.getHomePath()).thenReturn(pathToJdk8);
    when(myMockIdeSdks.getRunningVersionOrDefault()).thenReturn(JavaSdkVersion.JDK_1_8);
    when(myMockJdks.findVersion(new File(pathToJdk8))).thenReturn(JavaSdkVersion.JDK_1_8);
    when(myMockIdeSdks.getJdk()).thenReturn(jdk);
    when(jdk.getHomePath()).thenReturn("/path/to/jdk8");

    PreSyncCheckResult result = myJdkPreSyncCheck.doCheckCanSyncAndTryToFix(getProject());
    verifyCheckFailure(result, "The Jdk installation is invalid.\n" +
                               "Selected Jdk location is /path/to/jdk8.");
  }

  private void verifyCheckFailure(@NotNull PreSyncCheckResult result, @NotNull String expectedText) {
    assertFalse(result.isSuccess());

    assertThat(result.getFailureCause()).startsWith("Invalid Jdk");

    SyncMessage message = mySyncMessagesStub.getFirstReportedMessage();
    assertNotNull(message);
    assertThat(message.getText()).hasLength(1);
    assertEquals(SyncMessage.DEFAULT_GROUP, message.getGroup());

    assertAbout(syncMessage()).that(message).hasMessageLineStartingWith(expectedText, 0);
  }
}
