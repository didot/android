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
package com.android.tools.idea.gradle.project.sync.setup.module.common;

import static com.android.tools.idea.gradle.project.sync.messages.SyncMessageSubject.syncMessage;
import static com.android.tools.idea.project.messages.MessageType.ERROR;
import static com.android.tools.idea.project.messages.MessageType.WARNING;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.project.messages.SyncMessage;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestCase;
import java.util.List;

/**
 * Tests for {@link DependencySetupIssues}.
 */
public class DependencySetupIssuesTest extends PlatformTestCase {
  private GradleSyncMessagesStub mySyncMessages;
  private DependencySetupIssues myIssues;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();
    mySyncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(project, getTestRootDisposable());
    myIssues = new DependencySetupIssues(mySyncMessages);
  }

  public void testAddMissingModule() {
    myIssues.addMissingModule(":lib2", "app2");
    myIssues.addMissingModule(":lib2", "app1");
    myIssues.addMissingModule(":lib3", "app1");
    myIssues.addMissingModule(":lib1", "app1");

    List<DependencySetupIssues.MissingModule> missingModules = myIssues.getMissingModules();
    assertThat(missingModules).hasSize(3);

    DependencySetupIssues.MissingModule missingModule = missingModules.get(0);
    assertThat(missingModule.dependencyPath).isEqualTo(":lib1");
    assertThat(missingModule.dependentNames).containsExactly("app1");
  }

  public void testAddDependentOnLibraryWithoutBinaryPath() {
    myIssues.addMissingBinaryPath("app2");
    myIssues.addMissingBinaryPath("app2");
    myIssues.addMissingBinaryPath("app1");
    assertThat(myIssues.getDependentsOnLibrariesWithoutBinaryPath()).containsExactly("app1", "app2").inOrder();
  }

  public void testReportIssues() {
    myIssues.addMissingModule(":lib1", "app1");
    myIssues.addMissingModule(":lib2", "app2");
    myIssues.addMissingModule(":lib2", "app1");
    myIssues.addMissingModule(":lib3", "app1");
    myIssues.addMissingBinaryPath("app2");
    myIssues.addMissingBinaryPath("app1");

    myIssues.reportIssues();

    List<SyncMessage> messages = mySyncMessages.getReportedMessages();
    assertThat(messages).hasSize(5);

    SyncMessage message = messages.get(0);
    // @formatter:off
    assertAbout(syncMessage()).that(message).hasGroup("Missing Dependencies")
                                            .hasType(ERROR)
                                            .hasMessageLine("Unable to find module with Gradle path ':lib1' (needed by module 'app1'.)", 0);
    // @formatter:on

    message = messages.get(1);
    assertAbout(syncMessage()).that(message).hasGroup("Missing Dependencies")
                                            .hasType(ERROR)
                                            .hasMessageLine("Unable to find module with Gradle path ':lib2' (needed by modules: 'app1', 'app2'.)", 0);
    // @formatter:on

    message = messages.get(2);
    // @formatter:off
    assertAbout(syncMessage()).that(message).hasGroup("Missing Dependencies")
                                            .hasType(ERROR)
                                            .hasMessageLine("Unable to find module with Gradle path ':lib3' (needed by module 'app1'.)", 0);
    // @formatter:on

    message = messages.get(3);
    // @formatter:off
    assertAbout(syncMessage()).that(message).hasGroup("Missing Dependencies")
                                            .hasType(ERROR)
                                            .hasMessageLine("Module 'app1' depends on libraries that do not have a 'binary' path.", 0);
    // @formatter:on

    message = messages.get(4);
    // @formatter:off
    assertAbout(syncMessage()).that(message).hasGroup("Missing Dependencies")
                                            .hasType(ERROR)
                                            .hasMessageLine("Module 'app2' depends on libraries that do not have a 'binary' path.", 0);
    // @formatter:on
  }
}
