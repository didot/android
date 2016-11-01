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
package com.android.tools.idea.gradle.project.sync.messages;

import com.android.tools.idea.testing.IdeComponents;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

public class SyncMessagesStub extends SyncMessages {
  @NotNull private final List<SyncMessage> myMessages = new ArrayList<>();

  @Nullable private NotificationData myNotification;

  @NotNull
  public static SyncMessagesStub replaceSyncMessagesService(@NotNull Project project) {
    SyncMessagesStub syncMessages = new SyncMessagesStub(project);
    IdeComponents.replaceService(project, SyncMessages.class, syncMessages);
    assertSame(syncMessages, SyncMessages.getInstance(project));
    return syncMessages;
  }

  private SyncMessagesStub(@NotNull Project project) {
    super(project, mock(ExternalSystemNotificationManager.class));
  }

  @Override
  public void report(@NotNull SyncMessage message) {
    myMessages.add(message);
  }

  @Nullable
  public SyncMessage getFirstReportedMessage() {
    return myMessages.isEmpty() ? null : myMessages.get(0);
  }

  @NotNull
  public List<SyncMessage> getReportedMessages() {
    return ImmutableList.copyOf(myMessages);
  }

  @Override
  public void report(@NotNull NotificationData notification) {
    myNotification = notification;
  }

  @Nullable
  public NotificationData getNotification() {
    return myNotification;
  }

  public void clearReportedMessages() {
    myMessages.clear();
  }
}
