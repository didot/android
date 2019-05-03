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
package com.android.tools.idea.project.messages;

import static com.intellij.openapi.externalSystem.service.notification.NotificationSource.PROJECT_SYNC;
import static com.intellij.openapi.util.text.StringUtil.join;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

import com.android.annotations.concurrency.GuardedBy;
import com.android.tools.idea.gradle.project.build.events.AndroidSyncIssueEvent;
import com.android.tools.idea.gradle.project.build.events.AndroidSyncIssueEventResult;
import com.android.tools.idea.gradle.project.build.events.AndroidSyncIssueFileEvent;
import com.android.tools.idea.gradle.project.build.events.AndroidSyncIssueOutputEvent;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.issues.SyncIssueUsageReporter;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.ui.QuickFixNotificationListener;
import com.android.tools.idea.util.PositionInFile;
import com.intellij.build.SyncViewManager;
import com.intellij.build.events.Failure;
import com.intellij.build.events.MessageEvent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.util.SystemProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractSyncMessages implements Disposable {

  private Project myProject;

  @NotNull
  private final Object myLock = new Object();

  @GuardedBy("myLock")
  @NotNull
  private final HashMap<Object, List<NotificationData>> myCurrentNotifications = new HashMap<>();
  @GuardedBy("myLock")
  @NotNull
  private final HashMap<Object, List<Failure>> myShownFailures = new HashMap<>();
  @NotNull private static final String PENDING_TASK_ID = "Pending taskId";

  protected AbstractSyncMessages(@NotNull Project project) {
    myProject = project;
  }

  public int getErrorCount() {
    return countNotifications(notification -> notification.getNotificationCategory() == NotificationCategory.ERROR);
  }

  public int getMessageCount(@NotNull String groupName) {
    return countNotifications(notification -> notification.getTitle().equals(groupName));
  }

  private int countNotifications(@NotNull Predicate<NotificationData> condition) {
    int total = 0;

    synchronized (myLock) {
      for (List<NotificationData> notificationDataList : myCurrentNotifications.values()) {
        for (NotificationData notificationData : notificationDataList) {
          if (condition.test(notificationData)) {
            total++;
          }
        }
      }
    }
    return total;
  }

  public boolean isEmpty() {
    synchronized (myLock) {
      return myCurrentNotifications.isEmpty();
    }
  }

  public void removeAllMessages() {
    synchronized (myLock) {
      myCurrentNotifications.clear();
    }
  }

  public void removeMessages(@NotNull String... groupNames) {
    Set<String> groupSet = new HashSet<>(Arrays.asList(groupNames));
    LinkedList<Object> toRemove = new LinkedList<>();
    synchronized (myLock) {
      for (Object id : myCurrentNotifications.keySet()) {
        List<NotificationData> taskNotifications = myCurrentNotifications.get(id);
        taskNotifications.removeIf(notification -> groupSet.contains(notification.getTitle()));
        if (taskNotifications.isEmpty()) {
          toRemove.add(id);
        }
      }
      for (Object taskId : toRemove) {
        myCurrentNotifications.remove(taskId);
      }
    }
  }

  public void report(@NotNull SyncMessage message) {
    String title = message.getGroup();
    String text = join(message.getText(), "\n");
    NotificationCategory category = message.getType().convertToCategory();
    PositionInFile position = message.getPosition();

    NotificationData notification = createNotification(title, text, category, position);

    Navigatable navigatable = message.getNavigatable();
    notification.setNavigatable(navigatable);

    List<NotificationHyperlink> quickFixes = message.getQuickFixes();
    if (!quickFixes.isEmpty()) {
      updateNotification(notification, text, quickFixes);
    }

    report(notification);
  }

  @NotNull
  public NotificationData createNotification(@NotNull String title,
                                             @NotNull String text,
                                             @NotNull NotificationCategory category,
                                             @Nullable PositionInFile position) {
    NotificationSource source = PROJECT_SYNC;
    if (position != null) {
      String filePath = virtualToIoFile(position.file).getPath();
      return new NotificationData(title, text, category, source, filePath, position.line, position.column, false);
    }
    return new NotificationData(title, text, category, source);
  }

  public void updateNotification(@NotNull NotificationData notification,
                                 @NotNull String text,
                                 @NotNull List<NotificationHyperlink> quickFixes) {
    String message = text;
    int hyperlinkCount = quickFixes.size();
    if (hyperlinkCount > 0) {
      StringBuilder b = new StringBuilder();
      for (int i = 0; i < hyperlinkCount; i++) {
        b.append(quickFixes.get(i).toHtml());
        if (i < hyperlinkCount - 1) {
          b.append("<br>");
        }
      }
      message += ('\n' + b.toString());
    }
    notification.setMessage(message);

    addNotificationListener(notification, quickFixes);
  }

  // Call this method only if notification contains detailed text message with hyperlinks
  // Use updateNotification otherwise
  public void addNotificationListener(@NotNull NotificationData notification, @NotNull List<NotificationHyperlink> quickFixes) {
    for (NotificationHyperlink quickFix : quickFixes) {
      notification.setListener(quickFix.getUrl(), new QuickFixNotificationListener(myProject, quickFix));
    }
  }

  public void report(@NotNull NotificationData notification) {
    // Save on array to be shown by build view later.
    Object taskId = GradleSyncState.getInstance(myProject).getExternalSystemTaskId();
    if (taskId == null) {
      taskId = PENDING_TASK_ID;
    }
    else {
      showNotification(notification, taskId);
    }
    synchronized (myLock) {
      myCurrentNotifications.computeIfAbsent(taskId, key -> new ArrayList<>()).add(notification);
    }
  }

  /**
   * Show all pending events on the Build View, using the given taskId as parent. It clears the pending notifications after showing them.
   * @param taskId id of task associated with this sync.
   * @return The list of failures on the events associated to taskId.
   */
  @NotNull
  public List<Failure> showEvents(@NotNull ExternalSystemTaskId taskId) {
    List<Failure> result;
    // Show notifications created without a taskId
    synchronized (myLock) {
      for (NotificationData notification : myCurrentNotifications.getOrDefault(PENDING_TASK_ID, Collections.emptyList())) {
        showNotification(notification, taskId);
      }
      myCurrentNotifications.remove(taskId);
      myCurrentNotifications.remove(PENDING_TASK_ID);

      result = myShownFailures.remove(taskId);
    }
    if (result == null) {
      result = Collections.emptyList();
    }
    // Report any sync issues reported to the user to the usage tracker.
    SyncIssueUsageReporter.Companion.getInstance(getProject()).reportToUsageTracker();
    return result;
  }

  private void showNotification(@NotNull NotificationData notification, @NotNull Object taskId) {
    String title = notification.getTitle();
    // Since the title of the notification data is the group, it is better to display the first line of the message
    String[] lines = notification.getMessage().split(SystemProperties.getLineSeparator());
    if (lines.length > 0) {
      title = lines[0];
    }

    // Since we have no way of changing the text attributes in the BuildConsole we prefix the message with
    // ERROR or WARNING to indicate to the user the severity of each message.
    notification.setMessage(notification.getNotificationCategory().name() + ": " + notification.getMessage());

    AndroidSyncIssueEvent issueEvent;
    if (notification.getFilePath() != null) {
      issueEvent = new AndroidSyncIssueFileEvent(taskId, notification, title);
    }
    else {
      issueEvent = new AndroidSyncIssueEvent(taskId, notification, title);
    }
    SyncViewManager syncViewManager = ServiceManager.getService(myProject, SyncViewManager.class);
    syncViewManager.onEvent(issueEvent);
    syncViewManager.onEvent(new AndroidSyncIssueOutputEvent(taskId, notification));

    // Only include errors in the summary text output
    // This has the side effect of not opening the right hand bar if there are no failures
    if (issueEvent.getKind() == MessageEvent.Kind.ERROR) {
      synchronized (myLock) {
        myShownFailures.computeIfAbsent(taskId, key -> new ArrayList<>())
          .addAll(((AndroidSyncIssueEventResult)issueEvent.getResult()).getFailures());
      }
    }
  }

  @NotNull
  protected abstract ProjectSystemId getProjectSystemId();

  @NotNull
  protected Project getProject() {
    return myProject;
  }

  @Override
  public void dispose() {
    myProject = null;
  }
}
