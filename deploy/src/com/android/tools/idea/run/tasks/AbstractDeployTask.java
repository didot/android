/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.idea.run.tasks;

import com.android.ddmlib.IDevice;
import com.android.tools.deployer.AdbClient;
import com.android.tools.deployer.AdbInstaller;
import com.android.tools.deployer.DeployMetric;
import com.android.tools.deployer.Deployer;
import com.android.tools.deployer.DeployerException;
import com.android.tools.deployer.Installer;
import com.android.tools.deployer.tasks.TaskRunner;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.DeploymentService;
import com.android.tools.idea.run.IdeService;
import com.android.tools.idea.run.ui.ApplyChangesAction;
import com.android.tools.idea.run.util.LaunchStatus;
import com.google.wireless.android.sdk.stats.LaunchTaskDetail;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.commands.ActionCommand;
import com.intellij.openapi.wm.ToolWindowId;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractDeployTask implements LaunchTask {

  public static final int MIN_API_VERSION = 26;
  private static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.toolWindowGroup("UnifiedDeployTask", ToolWindowId.RUN);

  @NotNull private final Project myProject;
  @NotNull private final Map<String, List<File>> myPackages;
  @NotNull protected List<LaunchTaskDetail> mySubTaskDetails;

  private static final String FAILURE_TITLE = "Changes were not applied.\n";

  public static final Logger LOG = Logger.getInstance(AbstractDeployTask.class);

  public AbstractDeployTask(
    @NotNull Project project, @NotNull Map<String, List<File>> packages) {
    myProject = project;
    myPackages = packages;
    mySubTaskDetails = new ArrayList<>();
  }

  @Override
  public int getDuration() {
    return 20;
  }

  @Override
  public boolean perform(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
    return false;
  }

  @Override
  public LaunchResult run(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
    LogWrapper logger = new LogWrapper(LOG);

    AdbClient adb = new AdbClient(device, logger);
    Installer installer = new AdbInstaller(getLocalInstaller(), adb, logger);
    DeploymentService service = DeploymentService.getInstance(myProject);
    IdeService ideService = new IdeService(myProject);
    Deployer deployer = new Deployer(adb, service.getDexDatabase(), service.getTaskRunner(), installer, ideService, logger);
    for (Map.Entry<String, List<File>> entry : myPackages.entrySet()) {
      String applicationId = entry.getKey();
      List<File> apkFiles = entry.getValue();
      try {
        Collection<DeployMetric> metrics = perform(device, deployer, applicationId, apkFiles);
        addSubTaskDetails(metrics);
      }
      catch (DeployerException e) {
        return toLaunchResult(e);
      }
    }

    NOTIFICATION_GROUP.createNotification(getDescription() + " successful", NotificationType.INFORMATION)
      .setImportant(false).notify(myProject);

    return new LaunchResult();
  }

  abstract protected Collection<DeployMetric> perform(
    IDevice device, Deployer deployer, String applicationId, List<File> files) throws DeployerException;

  private String getLocalInstaller() {
    File path = new File(PathManager.getHomePath(), "plugins/android/resources/installer");
    if (!path.exists()) {
      // Development mode
      path = new File(PathManager.getHomePath(), "../../bazel-genfiles/tools/base/deploy/installer/android-installer");
    }
    return path.getAbsolutePath();
  }

  protected static List<String> getPathsToInstall(@NotNull List<File> apkFiles) {
    return apkFiles.stream().map(File::getPath).collect(Collectors.toList());
  }

  @NotNull
  protected Project getProject() {
    return myProject;
  }

  private void addSubTaskDetails(@NotNull Collection<DeployMetric> metrics) {
    for (DeployMetric metric : metrics) {
      if (!metric.getName().isEmpty()) {
        LaunchTaskDetail.Builder detail = LaunchTaskDetail.newBuilder();

        detail.setId(getId() + "." + metric.getName())
          .setStartTimestampMs(metric.getStartTimeMs())
          .setEndTimestampMs(metric.getEndTimeMs())
          .setTid((int)metric.getThreadId());

        if (metric.hasStatus()) {
          detail.setStatus(metric.getStatus());
        }
        mySubTaskDetails.add(detail.build());
      }
    }
  }

  @Override
  @NotNull
  public Collection<LaunchTaskDetail> getSubTaskDetails() {
    return mySubTaskDetails;
  }

  public LaunchResult toLaunchResult(DeployerException e) {

    LaunchResult result = new LaunchResult();
    result.setSuccess(false);

    StringBuilder bubbleError = new StringBuilder(FAILURE_TITLE);
    bubbleError.append(e.getMessage());

    DeployerException.Error error = e.getError();
    if (error.getResolution() != DeployerException.ResolutionAction.NONE) {
      bubbleError.append(String.format("\n<a href='%s'>%s</a>", error.getResolution(), error.getCallToAction()));
    }

    result.setError(bubbleError.toString());
    result.setConsoleError(FAILURE_TITLE + e.getMessage() + "\n" + e.getDetails());

    result.setNotificationListener(new DeploymentErrorNotificationListener());
    result.setErrorId(e.getId());
    return result;
  }

  private class DeploymentErrorNotificationListener implements NotificationListener {
    @Override
    public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
      if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
        return;
      }
      ActionManager manager = ActionManager.getInstance();

      DeployerException.ResolutionAction resolution = DeployerException.ResolutionAction.NONE;
      try {
        resolution = DeployerException.ResolutionAction.valueOf(event.getDescription());
      } catch (IllegalArgumentException e) {
        // Ignore it.
      }

      String actionId = null;
      switch (resolution) {
        case APPLY_CHANGES: // TODO
          actionId = ApplyChangesAction.ID;
          break;
        case RUN_APP:
          actionId = IdeActions.ACTION_DEFAULT_RUNNER;
          break;
        case RETRY:
          actionId = getId();
          break;
      }
      if (actionId == null) {
        return;
      }
      AnAction action = manager.getAction(actionId);
      if (action == null) {
        return;
      }
      manager.tryToExecute(action, ActionCommand.getInputEvent(ApplyChangesAction.ID), null, ActionPlaces.UNKNOWN, true);
    }
  }
}
