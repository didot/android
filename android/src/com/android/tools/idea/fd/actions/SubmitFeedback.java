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
package com.android.tools.idea.fd.actions;

import com.android.tools.idea.fd.FlightRecorder;
import com.android.tools.idea.fd.InstantRunSettings;
import com.android.tools.idea.fd.crash.GoogleCrash;
import com.google.common.escape.Escaper;
import com.google.common.net.UrlEscapers;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class SubmitFeedback extends DumbAwareAction {
  private static final NotificationGroup FLR_NOTIFICATION_GROUP = NotificationGroup.balloonGroup("instant.run.flight.recorder");

  public SubmitFeedback() {
    super("Report Instant Run Issue...");
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getProject();
    getTemplatePresentation().setVisible(project != null && !project.isDefault());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      Logger.getInstance(SubmitFeedback.class).info("Unable to identify current project");
      return;
    }

    if (!InstantRunSettings.isRecorderEnabled()) {
      int result = Messages.showYesNoDialog(
        project,
        AndroidBundle.message("instant.run.flr.would.you.like.to.enable"),
        AndroidBundle.message("instant.run.flr.dialog.title"),
        "Yes, I'd like to help",
        "Cancel",
        Messages.getQuestionIcon());
      if (result == Messages.NO) {
        return;
      }

      InstantRunSettings.setRecorderEnabled(true);
      Messages.showInfoMessage(project,
                               AndroidBundle.message("instant.run.flr.howto"),
                               AndroidBundle.message("instant.run.flr.dialog.title"));
      return;
    }

    InstantRunFeedbackDialog dialog = new InstantRunFeedbackDialog(project);
    boolean ok = dialog.showAndGet();
    if (ok) {
      new Task.Backgroundable(project, "Submitting Instant Run Issue") {
        public CompletableFuture<String> myReport;

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          myReport =
            GoogleCrash.getInstance().submit(FlightRecorder.get(project), dialog.getIssueText(), dialog.getLogs());

          while (!myReport.isDone()) {
            try {
              myReport.get(200, TimeUnit.MILLISECONDS);
            }
            catch (Exception ignored) {
            }

            if (indicator.isCanceled()) {
              return;
            }
          }
        }

        @Override
        public void onSuccess() {
          if (myReport.isDone()) {
            String message = String.format("<html>Thank you for submitting the bug report.<br>" +
                                           "If you would like to follow up on this report, please file a bug at <a href=\"bug\">b.android.com</a> and specify the report id '%1$s'<html>",
                                           myReport.getNow("00"));
            FLR_NOTIFICATION_GROUP
              .createNotification("", message, NotificationType.INFORMATION, (notification, event) -> {
                Escaper escaper = UrlEscapers.urlFormParameterEscaper();
                String comment = String.format("Build: %1$s\nInstant Run Report: %2$s",
                                               ApplicationInfo.getInstance().getFullVersion(),
                                               myReport.getNow("00"));
                String url = String.format("https://code.google.com/p/android/issues/entry?template=%1$s&comment=%2$s&status=New",
                                           escaper.escape("Android Studio Instant Run Bug"),
                                           escaper.escape(comment));
                BrowserUtil.browse(url);
              })
              .notify(project);
          }
        }
      }.queue();
    }
  }
}
