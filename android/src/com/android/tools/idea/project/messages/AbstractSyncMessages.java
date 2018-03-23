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

import com.android.tools.idea.gradle.project.build.events.AndroidSyncIssueEvent;
import com.android.tools.idea.gradle.project.build.events.AndroidSyncIssueEventResult;
import com.android.tools.idea.gradle.project.build.events.AndroidSyncIssueFileEvent;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.ui.QuickFixNotificationListener;
import com.android.tools.idea.util.PositionInFile;
import com.intellij.build.SyncViewManager;
import com.intellij.build.events.Failure;
import com.intellij.ide.errorTreeView.NewEditableErrorTreeViewPanel;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.MessageView;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

import static com.intellij.openapi.externalSystem.service.notification.NotificationSource.PROJECT_SYNC;
import static com.intellij.openapi.util.text.StringUtil.join;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.openapi.wm.ToolWindowId.MESSAGES_WINDOW;

public abstract class AbstractSyncMessages implements Disposable {
  private static final NotificationSource NOTIFICATION_SOURCE = PROJECT_SYNC;

  private Project myProject;
  @NotNull private final ExternalSystemNotificationManager myNotificationManager;
  @NotNull private final HashMap<ExternalSystemTaskId, List<NotificationData>> myCurrentNotifications = new HashMap<>();
  @NotNull private final HashMap<ExternalSystemTaskId, List<Failure>> myShownFailures = new HashMap<>();

  protected AbstractSyncMessages(@NotNull Project project, @NotNull ExternalSystemNotificationManager manager) {
    myProject = project;
    myNotificationManager = manager;
  }

  public int getErrorCount() {
    return getMessageCount(NotificationCategory.ERROR);
  }

  public int getMessageCount(@NotNull String groupName) {
    return myNotificationManager.getMessageCount(groupName, NOTIFICATION_SOURCE, null, getProjectSystemId());
  }

  public boolean isEmpty() {
    return getMessageCount((NotificationCategory)null) == 0;
  }

  private int getMessageCount(@Nullable NotificationCategory category) {
    return myNotificationManager.getMessageCount(NOTIFICATION_SOURCE, category, getProjectSystemId());
  }

  public void removeAllMessages() {
    myNotificationManager.clearNotifications(NOTIFICATION_SOURCE, getProjectSystemId());
    myCurrentNotifications.clear();
  }

  public void removeMessages(@NotNull String... groupNames) {
    Set<String> groupSet = new HashSet<>(Arrays.asList(groupNames));
    for (ExternalSystemTaskId id : myCurrentNotifications.keySet()) {
      List<NotificationData> taskNotifications = myCurrentNotifications.get(id);
      taskNotifications.removeIf(notification -> groupSet.contains(notification.getTitle()));
      if (taskNotifications.isEmpty()) {
        myCurrentNotifications.remove(id);
      }
    }

    for (String groupName : groupNames) {
      myNotificationManager.clearNotifications(groupName, NOTIFICATION_SOURCE, getProjectSystemId());
    }

    ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(MESSAGES_WINDOW);
    if (toolWindow != null) {
      MessageView messageView = ServiceManager.getService(myProject, MessageView.class);
      ApplicationManager.getApplication().invokeLater(() -> {
        if (myProject.isDisposed()) {
          return;
        }
        // Refresh UI to see updated list of messages.
        NewEditableErrorTreeViewPanel messagesView = findMessagesView(messageView);
        if (messagesView != null) {
          messagesView.updateTree();
        }
      });
    }
  }

  @Nullable
  private NewEditableErrorTreeViewPanel findMessagesView(@NotNull MessageView messageView) {
    NewEditableErrorTreeViewPanel messagesView = null;
    for (Content content : messageView.getContentManager().getContents()) {
      if (!content.isPinned()) {
        String displayName = content.getDisplayName();
        if (displayName != null && displayName.startsWith(getProjectSystemId().getReadableName())) {
          JComponent component = content.getComponent();
          if (component instanceof NewEditableErrorTreeViewPanel) {
            messagesView = (NewEditableErrorTreeViewPanel)component;
            break;
          }
        }
      }
    }
    return messagesView;
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
    myNotificationManager.showNotification(getProjectSystemId(), notification);

    // Save on array to be shown by build view later.
    ExternalSystemTaskId taskId = GradleSyncState.getInstance(myProject).getExternalSystemTaskId();
    myCurrentNotifications.computeIfAbsent(taskId, key -> new ArrayList<>()).add(notification);
    if (taskId != null) {
      showNotification(notification, taskId);
    }
  }

  /**
   * Show all pending events on the Build View, using the given taskId as parent. It clears the pending notifications after showing them.
   * @param taskId id of task associated with this sync.
   * @return The list of failures on the events associated to taskId.
   */
  @NotNull
  public List<Failure> showEvents(@NotNull ExternalSystemTaskId taskId) {
    // Show notifications created without a taskId
    for (NotificationData notification : myCurrentNotifications.getOrDefault(null, Collections.emptyList())) {
      showNotification(notification, taskId);
    }
    myCurrentNotifications.remove(taskId);
    myCurrentNotifications.remove(null);
    List<Failure> result = myShownFailures.remove(taskId);
    if (result == null) {
      result = Collections.emptyList();
    }
    return result;
  }

  private void showNotification(@NotNull NotificationData notification, @NotNull ExternalSystemTaskId taskId) {
    String title = notification.getTitle();
    // Since the title of the notification data is the group, it is better to display the first line of the message
    String[] lines = notification.getMessage().split(SystemProperties.getLineSeparator());
    if (lines.length > 0) {
      title = lines[0];
    }
    AndroidSyncIssueEvent issueEvent;
    if (notification.getFilePath() != null) {
      issueEvent = new AndroidSyncIssueFileEvent(taskId, notification, title);
    }
    else {
      issueEvent = new AndroidSyncIssueEvent(taskId, notification, title);
    }
    ServiceManager.getService(myProject, SyncViewManager.class).onEvent(issueEvent);
    myShownFailures.computeIfAbsent(taskId, key -> new ArrayList<>()).addAll(((AndroidSyncIssueEventResult)issueEvent.getResult()).getFailures());
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
