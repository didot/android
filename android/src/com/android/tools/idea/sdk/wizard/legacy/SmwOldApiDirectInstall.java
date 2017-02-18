/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.sdk.wizard.legacy;

import com.android.repository.api.Installer;
import com.android.repository.api.InstallerFactory;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.repository.util.InstallerUtil;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.LoggerProgressIndicatorWrapper;
import com.android.sdklib.repository.legacy.descriptors.IPkgDesc;
import com.android.sdklib.repository.legacy.descriptors.PkgType;
import com.android.tools.idea.sdk.*;
import com.android.tools.idea.sdk.install.StudioSdkInstallerUtil;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdk.progress.StudioProgressRunner;
import com.android.tools.idea.sdk.wizard.InstallSelectedPackagesStep;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStepWithDescription;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.wizard.WizardConstants.INSTALL_REQUESTS_KEY;
import static com.android.tools.idea.wizard.WizardConstants.NEWLY_INSTALLED_API_KEY;

/**
 * @deprecated Replaced by {@link InstallSelectedPackagesStep}
 */
public class SmwOldApiDirectInstall extends DynamicWizardStepWithDescription {
  private JBLabel myLabelSdkPath;
  private JTextArea mySdkManagerOutput;
  private JBLabel myLabelProgress1;
  private JProgressBar myProgressBar;
  private JBLabel myLabelProgress2;
  private JLabel myErrorLabel;
  private JPanel myContentPanel;
  private boolean myInstallFinished;

  public SmwOldApiDirectInstall(@NotNull Disposable disposable) {
    super(disposable);
    setBodyComponent(myContentPanel);
  }

  @Override
  public void onEnterStep() {
    super.onEnterStep();
    mySdkManagerOutput.setText("");
    startSdkInstallUsingNonSwtOldApi();
  }

  @Override
  public boolean isStepVisible() {
    return myState.listSize(INSTALL_REQUESTS_KEY) > 0;
  }

  @Override
  public boolean validate() {
    return myInstallFinished;
  }

  @Override
  public boolean canGoPrevious() {
    return myInstallFinished;
  }

  private void startSdkInstallUsingNonSwtOldApi() {
    // Get the SDK instance.
    final AndroidSdkHandler sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    if (sdkHandler.getLocation() == null) {
      myErrorLabel.setText("Error: can't get SDK instance.");
      myErrorLabel.setIcon(AllIcons.General.BalloonError);
      return;
    }

    File androidSdkPath = IdeSdks.getInstance().getAndroidSdkPath();
    if (androidSdkPath != null && androidSdkPath.exists() && !androidSdkPath.canWrite()) {
      myErrorLabel.setText(String.format("SDK folder is read-only: '%1$s'", androidSdkPath.getPath()));
      myErrorLabel.setIcon(AllIcons.General.BalloonError);
      return;
    }

    myLabelSdkPath.setText(sdkHandler.getLocation().getPath());

    final CustomLogger logger = new CustomLogger();
    final com.android.repository.api.ProgressIndicator repoProgress = new StudioLoggerProgressIndicator(getClass());
    RepoManager.RepoLoadedCallback onComplete = packages -> UIUtil.invokeLaterIfNeeded(() -> {
      List<String> requestedChanges = myState.get(INSTALL_REQUESTS_KEY);
      if (requestedChanges == null) {
        // This should never occur
        assert false : "Shouldn't be in installer with no requests";
        myInstallFinished = true;
        invokeUpdate(null);
        return;
      }

      Map<String, RemotePackage> remotes = packages.getRemotePackages();
      List<RemotePackage> requestedPackages = Lists.newArrayList();
      boolean notFound = false;
      for (String path : requestedChanges) {
        RemotePackage remotePackage = remotes.get(path);
        if (remotePackage != null) {
          requestedPackages.add(remotePackage);
        }
        else {
          notFound = true;
        }
      }
      if (requestedPackages.isEmpty()) {
        myInstallFinished = true;
        invokeUpdate(null);
      }
      else {
        requestedPackages = InstallerUtil.computeRequiredPackages(requestedPackages, packages, repoProgress);
        if (requestedPackages == null) {
          notFound = true;
        }
        else {
          InstallTask task = new InstallTask(sdkHandler, requestedPackages, logger);
          BackgroundableProcessIndicator indicator = new BackgroundableProcessIndicator(task);
          logger.setIndicator(indicator);
          ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, indicator);
        }
      }
      if (notFound) {
        myErrorLabel.setText("Problem: Some required packages could not be installed. Check internet connection.");
        myErrorLabel.setIcon(AllIcons.General.BalloonError);
      }
    });

    StudioProgressRunner runner = new StudioProgressRunner(false, true, false, "Updating SDK", false, null);
    sdkHandler.getSdkManager(repoProgress)
      .load(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, null, ImmutableList.of(onComplete), null, runner, new StudioDownloader(),
            StudioSettingsController.getInstance(), false);
  }

  @NotNull
  @Override
  public String getStepName() {
    return "InstallingSDKComponentsStep";
  }

  @NotNull
  @Override
  protected String getStepTitle() {
    return "Installing Requested Components";
  }

  @Nullable
  @Override
  protected String getStepDescription() {
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  private class InstallTask extends Task.Backgroundable {
    @NotNull private final AndroidSdkHandler mySdkHandler;
    @NotNull private final List<RemotePackage> myRequestedPackages;
    @NotNull private final ILogger myLogger;

    private InstallTask(@NotNull AndroidSdkHandler sdkHandler,
                        @NotNull List<RemotePackage> requestedPackages,
                        @NotNull ILogger logger) {
      super(null /*project*/,
            "Installing Android SDK",
            false /*canBeCancelled*/,
            PerformInBackgroundOption.ALWAYS_BACKGROUND);
      mySdkHandler = sdkHandler;
      myRequestedPackages = requestedPackages;
      myLogger = logger;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      com.android.repository.api.ProgressIndicator repoProgress = new LoggerProgressIndicatorWrapper(myLogger);
      final RepoManager sdkManager = mySdkHandler.getSdkManager(repoProgress);
      InstallerFactory factory = StudioSdkInstallerUtil.createInstallerFactory(mySdkHandler);
      for (RemotePackage p : myRequestedPackages) {
        FileOp fop = FileOpUtils.create();
        Installer installer = factory.createInstaller(p, sdkManager, new StudioDownloader(indicator), fop);
        if (installer.prepare(repoProgress)) {
          installer.complete(repoProgress);
        }
      }
      sdkManager.loadSynchronously(0, repoProgress, null, null);
      myState.remove(INSTALL_REQUESTS_KEY);

      GuiUtils.invokeLaterIfNeeded(() -> {
        myProgressBar.setValue(100);
        myLabelProgress1.setText("");
        myLabelProgress2.setText("Done");
        List requestedChanges = myState.get(INSTALL_REQUESTS_KEY);
        checkForUpgrades(requestedChanges);
        myState.remove(INSTALL_REQUESTS_KEY);
        myInstallFinished = true;
        invokeUpdate(null);

        VirtualFile sdkDir = LocalFileSystem.getInstance().findFileByIoFile(mySdkHandler.getLocation());
        if (sdkDir != null) {
          sdkDir.refresh(true, true);
        }
        sdkManager.markInvalid();
      }, ModalityState.defaultModalityState());
    }
  }

  /**
   * Looks through the list of completed changes, and set a key if any new platforms
   * were installed.
   */
  private void checkForUpgrades(@Nullable List completedChanges) {
    if (completedChanges == null) {
      return;
    }
    int highestNewApiLevel = 0;
    for (Object o : completedChanges) {
      if (! (o instanceof IPkgDesc)) {
        continue;
      }
      IPkgDesc pkgDesc = (IPkgDesc)o;
      if (pkgDesc.getType().equals(PkgType.PKG_PLATFORM)) {
        AndroidVersion version = pkgDesc.getAndroidVersion();
        if (version != null && version.getApiLevel() > highestNewApiLevel) {
          highestNewApiLevel = version.getApiLevel();
        }
      }
    }
    if (highestNewApiLevel > 0) {
      myState.put(NEWLY_INSTALLED_API_KEY, highestNewApiLevel);
    }
  }

  private final class CustomLogger extends SdkLoggerIntegration {
    @Override
    protected void setProgress(int progress) {
      myProgressBar.setValue(progress);
    }

    @Override
    protected void setDescription(String description) {
      myLabelProgress2.setText(description);
    }

    @Override
    protected void setTitle(String title) {
      myLabelProgress1.setText(title);
    }

    @Override
    protected void lineAdded(String string) {
      String current = mySdkManagerOutput.getText();
      if (current == null) {
        current = "";
      }
      mySdkManagerOutput.setText(current + "\n" + string);
    }
  }
}
