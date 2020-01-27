/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.diagnostics.error;

import com.android.annotations.Nullable;
import com.android.tools.idea.diagnostics.crash.StudioCrashReport;
import com.android.tools.idea.diagnostics.crash.StudioExceptionReport;
import com.android.tools.idea.diagnostics.crash.StudioCrashReporter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.intellij.diagnostic.AbstractMessage;
import com.intellij.diagnostic.IdeErrorsDialog;
import com.intellij.diagnostic.ReportMessages;
import com.intellij.errorreport.bean.ErrorBean;
import com.intellij.ide.DataManager;
import com.intellij.idea.IdeaLogger;
import com.intellij.internal.statistic.analytics.StudioCrashDetails;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.SubmittedReportInfo;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Consumer;
import java.util.Optional;
import org.jetbrains.android.diagnostics.error.IdeaITNProxy;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ErrorReporter extends ErrorReportSubmitter {
  private static final String FEEDBACK_TASK_TITLE = "Submitting error report";

  @NotNull
  @Override
  public String getReportActionText() {
    return AndroidBundle.message("error.report.to.google.action");
  }

  @Override
  public boolean submit(@NotNull IdeaLoggingEvent[] events,
                        @Nullable String description,
                        @Nullable Component parentComponent,
                        @NotNull Consumer<SubmittedReportInfo> callback) {
    IdeaLoggingEvent event = events[0];
    ErrorBean bean = new ErrorBean(event.getThrowable(), IdeaLogger.ourLastActionId);

    bean.setDescription(description);
    bean.setMessage(event.getMessage());

    Pair<String, String> pluginInfo = IdeErrorsDialog.getPluginInfo(event);
    if (pluginInfo != null) {
      bean.setPluginName(pluginInfo.first);
      bean.setPluginVersion(pluginInfo.second);
    }

    Object data = event.getData();

    // Early escape (and no UI impact) if these are analytics events being pushed from the platform
    if (handleAnalyticsReports(event.getThrowable(), data)) {
      return true;
    }

    if (data instanceof AbstractMessage) {
      bean.setAttachments(((AbstractMessage)data).getIncludedAttachments());
    }

    // Android Studio: SystemHealthMonitor is always calling submit with a null parentComponent. In order to determine the data context
    // associated with the currently-focused component, we run that query on the UI thread and delay the rest of the invocation below.
    java.util.function.Consumer<DataContext> submitter = dataContext -> {
    if (dataContext == null) {
      return;
    }

    final Project project = CommonDataKeys.PROJECT.getData(dataContext);

    Consumer<String> successCallback = token -> {
      final SubmittedReportInfo reportInfo = new SubmittedReportInfo(
        null, "Issue " + token, SubmittedReportInfo.SubmissionStatus.NEW_ISSUE);
      callback.consume(reportInfo);

      ReportMessages.GROUP
        .createNotification(ReportMessages.ERROR_REPORT, "Submitted", NotificationType.INFORMATION, null)
        .setImportant(false)
        .notify(project);
    };

    Consumer<Exception> errorCallback = e -> {
      String message = AndroidBundle.message("error.report.at.b.android", e.getMessage());

      ReportMessages.GROUP
        .createNotification(ReportMessages.ERROR_REPORT, message, NotificationType.ERROR, NotificationListener.URL_OPENING_LISTENER)
        .setImportant(false)
        .notify(project);
    };

    Task.Backgroundable feedbackTask;
    if (data instanceof ErrorReportCustomizer) {
      feedbackTask = ((ErrorReportCustomizer) data).makeReportingTask(project, FEEDBACK_TASK_TITLE, true, bean, successCallback, errorCallback);
    } else {
      List<Pair<String, String>> kv = IdeaITNProxy
        .getKeyValuePairs(null, null, bean, ApplicationManager.getApplication(),
                          (ApplicationInfoEx)ApplicationInfo.getInstance(), ApplicationNamesInfo.getInstance(),
                          UpdateSettings.getInstance());
      feedbackTask = new SubmitCrashReportTask(project, FEEDBACK_TASK_TITLE, true, event.getThrowable(), pair2map(kv), successCallback, errorCallback);
    }

    if (project == null) {
      feedbackTask.run(new EmptyProgressIndicator());
    } else {
      ProgressManager.getInstance().run(feedbackTask);
    }
    };

    if (parentComponent != null) {
      submitter.accept(DataManager.getInstance().getDataContext(parentComponent));
    } else {
      DataManager.getInstance()
                 .getDataContextFromFocusAsync()
                 .onSuccess(submitter);
    }

    return true;
  }

  private static boolean handleAnalyticsReports(@Nullable Throwable t, @Nullable Object data) {
    if (!(data instanceof Map)) {
      return false;
    }

    Map map = (Map)data;
    String type = (String)map.get("Type");
    if ("Exception".equals(type)) {
      ImmutableMap<String, String> productData = ImmutableMap.of("md5", (String)map.get("md5"),
                                                                 "summary", (String)map.get("summary"));
      StudioExceptionReport exceptionReport =
        new StudioExceptionReport.Builder().setThrowable(t, false).addProductData(productData).build();
      StudioCrashReporter.getInstance().submit(exceptionReport);
    }
    else if ("Crashes".equals(type)) {
      //noinspection unchecked
      List<StudioCrashDetails> crashDetails = (List<StudioCrashDetails>)map.get("crashDetails");
      List<String> descriptions = crashDetails.stream().map(details -> details.getDescription()).collect(Collectors.toList());
      // If at least one report was JVM crash, submit the batch as a JVM crash
      boolean isJvmCrash = crashDetails.stream().anyMatch(details -> details.isJvmCrash());
      // As there may be multiple crashes reported together, take the shortest uptime (most of the time there is only
      // a single crash anyway).
      long uptimeInMs = crashDetails.stream().mapToLong(details -> details.getUptimeInMs()).min().orElse(-1);

      StudioCrashReport.Builder reportBuilder =
        new StudioCrashReport.Builder().setDescriptions(descriptions).setIsJvmCrash(isJvmCrash).setUptimeInMs(uptimeInMs);

      if (isJvmCrash) {
        Optional<StudioCrashDetails> jvmCrashOptional = crashDetails.stream().filter(details -> details.isJvmCrash()).findAny();
        if (jvmCrashOptional.isPresent()) {
          StudioCrashDetails jvmCrash = jvmCrashOptional.get();
          reportBuilder.setErrorSignal(jvmCrash.getErrorSignal());
          reportBuilder.setErrorFrame(jvmCrash.getErrorFrame());
          reportBuilder.setErrorThread(jvmCrash.getErrorThread());
          reportBuilder.setNativeStack(jvmCrash.getNativeStack());
        }
      }

      StudioCrashReport report = reportBuilder.build();
      // Crash reports are not limited by a rate limiter.
      StudioCrashReporter.getInstance().submit(report, true);
    }
    return true;
  }

  @NotNull
  private static Map<String, String> pair2map(@NotNull List<Pair<String, String>> kv) {
    Map<String, String> m = Maps.newHashMapWithExpectedSize(kv.size());

    for (Pair<String, String> i : kv) {
      m.put(i.getFirst(), i.getSecond());
    }

    return m;
  }
}
