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
package com.android.tools.idea.gradle.project;

import com.android.tools.idea.gradle.project.sync.hyperlink.NotificationHyperlink;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertSame;

public class AndroidGradleNotificationStub extends AndroidGradleNotification {
  @NotNull private final List<NotificationMessage> myMessages = new ArrayList<>();

  public AndroidGradleNotificationStub(@NotNull Project project) {
    super(project);
  }

  @NotNull
  public static AndroidGradleNotificationStub replaceSyncMessagesService(@NotNull Project project) {
    AndroidGradleNotificationStub notification = new AndroidGradleNotificationStub(project);
    IdeComponents.replaceService(project, AndroidGradleNotification.class, notification);
    assertSame(notification, AndroidGradleNotification.getInstance(project));
    return notification;
  }

  @Override
  public void showBalloon(@NotNull String title,
                          @NotNull String text,
                          @NotNull NotificationType type,
                          @NotNull NotificationGroup group,
                          @NotNull NotificationHyperlink... hyperlinks) {
    NotificationMessage message = new NotificationMessage(title, text, type, group, hyperlinks);
    myMessages.add(message);
  }

  @NotNull
  public List<NotificationMessage> getMessages() {
    return myMessages;
  }

  @Nullable
  @Override
  public Notification getNotification() {
    return super.getNotification();
  }

  public static class NotificationMessage {
    @NotNull private final String myTitle;
    @NotNull private final String myText;
    @NotNull private final NotificationType myType;
    @NotNull private final NotificationGroup myGroup;
    @NotNull private final NotificationHyperlink[] myHyperlinks;

    NotificationMessage(@NotNull String title,
                        @NotNull String text,
                        @NotNull NotificationType type,
                        @NotNull NotificationGroup group,
                        @NotNull NotificationHyperlink... hyperlinks) {
      myTitle = title;
      myText = text;
      myType = type;
      myGroup = group;
      myHyperlinks = hyperlinks;
    }

    @NotNull
    public String getTitle() {
      return myTitle;
    }

    @NotNull
    public String getText() {
      return myText;
    }

    @NotNull
    public NotificationType getType() {
      return myType;
    }

    @NotNull
    public NotificationGroup getGroup() {
      return myGroup;
    }

    @NotNull
    public NotificationHyperlink[] getHyperlinks() {
      return myHyperlinks;
    }
  }
}
