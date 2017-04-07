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
package com.android.tools.idea.instantapp.provision;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.instantapp.InstantApps;
import com.android.tools.idea.run.AndroidRunConfigContext;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.DeviceFutures;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.android.tools.idea.instantapp.InstantApps.isInstantAppSdkEnabled;

/**
 * Provides a {@link ProvisionBeforeRunTask} which is executed before an Instant App is run.
 * This provisions the device.
 */
public class ProvisionBeforeRunTaskProvider extends BeforeRunTaskProvider<ProvisionBeforeRunTaskProvider.ProvisionBeforeRunTask> {
  @NotNull public static final Key<ProvisionBeforeRunTask> ID = Key.create("com.android.instantApps.provision.BeforeRunTask");
  @NotNull private static final String TASK_NAME = "Instant App Provision";

  @NotNull
  @Override
  public Key<ProvisionBeforeRunTask> getId() {
    return ID;
  }

  @NotNull
  @Override
  public String getName() {
    return TASK_NAME;
  }

  @NotNull
  @Override
  public String getDescription(ProvisionBeforeRunTask task) {
    return TASK_NAME;
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return AndroidIcons.Android;
  }

  @Nullable
  @Override
  public Icon getTaskIcon(ProvisionBeforeRunTask task) {
    return AndroidIcons.Android;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }

  @Override
  public boolean isConfigurable() {
    // If clean the data of the Supervisor is necessary, we can implement a configurable task and add this option.
    // For the moment it isn't necessary
    return false;
  }

  @Nullable
  @Override
  public ProvisionBeforeRunTask createTask(RunConfiguration runConfiguration) {
    // This method is called when a new run configuration is created, and in that moment we don't know if it will be an Instant App or not,
    // so we create the task anyway and later, when running it, we check if it's an instant app context to provision the device or not.
    // This method is also called when reading from persistent data (first an empty task is created and after it's configured).
    if (runConfiguration instanceof AndroidRunConfigurationBase && isInstantAppSdkEnabled()) {
      ProvisionBeforeRunTask task = new ProvisionBeforeRunTask();
      task.setEnabled(true);
      return task;
    }
    return null;
  }

  @Override
  public boolean configureTask(RunConfiguration runConfiguration, ProvisionBeforeRunTask task) {
    return false;
  }

  @Override
  public boolean canExecuteTask(RunConfiguration configuration, ProvisionBeforeRunTask task) {
    return isInstantAppContext((AndroidRunConfigurationBase)configuration);
  }

  @Override
  public boolean executeTask(DataContext context, RunConfiguration configuration, ExecutionEnvironment env, ProvisionBeforeRunTask task) {
    if (!isInstantAppContext((AndroidRunConfigurationBase)configuration)) {
      // If the run configuration is not running an Instant App, there's no need to provision the device. Return early.
      return true;
    }

    AndroidRunConfigContext runConfigContext = env.getCopyableUserData(AndroidRunConfigContext.KEY);
    DeviceFutures deviceFutures = runConfigContext == null ? null : runConfigContext.getTargetDevices();

    if (deviceFutures == null) {
      return false;
    }

    ProgressManager progressManager = ProgressManager.getInstance();

    CountDownLatch countDownLatch = new CountDownLatch(1);
    AtomicBoolean result = new AtomicBoolean(true);

    progressManager.run(new Task.Backgroundable(configuration.getProject(), getDescription(task), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        AtomicBoolean tryAgain = new AtomicBoolean(true);
        Set<IDevice> succeededProvisioned = new HashSet<>();
        try {
          while (tryAgain.get()) {
            try {
              indicator.setIndeterminate(true);
              ProvisionRunner provisionRunner = new ProvisionRunner(indicator);

              for (ListenableFuture<IDevice> deviceListenableFuture : deviceFutures.get()) {
                IDevice device = waitForDevice(deviceListenableFuture, indicator);
                if (device == null) {
                  result.set(false);
                  return;
                }

                if (!succeededProvisioned.contains(device)) {
                  provisionRunner.runProvision(device);
                  succeededProvisioned.add(device);
                }
              }
              tryAgain.set(false);
            }
            catch (ProvisionException e) {
              getLogger().warn("Error while provisioning devices", e);

              ApplicationManager.getApplication().invokeAndWait(() -> {
                int choice = Messages
                  .showYesNoDialog("Provision failed with message: " + e.getMessage() + ". Do you want to retry?", "Instant Apps", null);
                if (choice != Messages.OK) {
                  tryAgain.set(false);
                  // If there was an error while provisioning, we stop running the RunConfiguration
                  result.set(false);
                }
              });
            }
          }
        }
        finally {
          // In case an unknown exception (e.g. runtime) is thrown, the countDownLatch unblocks the other thread so the user doesn't expect the timeout
          countDownLatch.countDown();
        }
      }
    });

    try {
      return countDownLatch.await(deviceFutures.get().size() * 300, TimeUnit.SECONDS) && result.get();
    }
    catch (InterruptedException e) {
      getLogger().error("Background thread interrupted", e);
      return false;
    }
  }

  @VisibleForTesting
  boolean isInstantAppContext(@NotNull AndroidRunConfigurationBase runConfiguration) {
    // This method returning false does not guarantee it's not an Instant App context, since recently created run configurations don't have
    // a module selected yet, and then we can't check if the module is instant app.
    Module module = runConfiguration.getConfigurationModule().getModule();
    return isInstantAppSdkEnabled() && module != null && InstantApps.isInstantAppApplicationModule(module);
  }

  @Nullable
  private static IDevice waitForDevice(@NotNull ListenableFuture<IDevice> deviceFuture, ProgressIndicator indicator) {
    while (true) {
      try {
        return deviceFuture.get(1, TimeUnit.SECONDS);
      }
      catch (InterruptedException | ExecutionException e) {
        return null;
      }
      catch (TimeoutException ignored) {
      }

      if (indicator.isCanceled()) {
        return null;
      }
    }
  }

  private static Logger getLogger() {
    return Logger.getInstance(ProvisionBeforeRunTaskProvider.class);
  }

  public static class ProvisionBeforeRunTask extends BeforeRunTask<ProvisionBeforeRunTask> {
    public ProvisionBeforeRunTask() {
      super(ID);
    }
  }
}
