/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome;

import com.android.tools.idea.wizard.DynamicWizardPath;
import com.android.tools.idea.wizard.DynamicWizardStep;
import com.android.tools.idea.wizard.ScopedStateStore;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.download.DownloadableFileDescription;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Wizard path that manages component installation flow. It will prompt the user
 * for the components to install and for install parameters. On wizard
 * completion it will download and unzip component archives and will
 * perform component setup.
 */
public class InstallComponentsPath extends DynamicWizardPath implements LongRunningOperationPath {
  public static final ScopedStateStore.Key<Boolean> KEY_CUSTOM_INSTALL =
    ScopedStateStore.createKey("custom.install", ScopedStateStore.Scope.PATH, Boolean.class);
  public static final InstallableComponent[] COMPONENTS = createComponents();
  private static final Logger LOG = Logger.getInstance(InstallComponentsPath.class);
  private InstallationTypeWizardStep myInstallationTypeWizardStep;

  private static InstallableComponent[] createComponents() {
    AndroidSdk androidSdk = new AndroidSdk(KEY_CUSTOM_INSTALL);
    if (SystemInfo.isWindows || SystemInfo.isMac) {
      return new InstallableComponent[]{androidSdk, new Haxm(KEY_CUSTOM_INSTALL)};
    }
    else {
      return new InstallableComponent[]{androidSdk};
    }
  }

  private static File createTempDir() throws WizardException {
    File tempDirectory;
    try {
      tempDirectory = FileUtil.createTempDirectory("AndroidStudio", "FirstRun", true);
    }
    catch (IOException e) {
      throw new WizardException("Unable to create temporary folder: " + e.getMessage(), e);
    }
    return tempDirectory;
  }

  @Override
  protected void init() {
    boolean handoff = InstallerData.get(myState).exists();
    if (!handoff) {
      myInstallationTypeWizardStep = new InstallationTypeWizardStep(KEY_CUSTOM_INSTALL);
      addStep(myInstallationTypeWizardStep);
    }
    for (InstallableComponent component : COMPONENTS) {
      component.init(myState);
      for (DynamicWizardStep step : component.createSteps()) {
        addStep(step);
      }
    }
    if (SystemInfo.isLinux && !handoff) {
      addStep(new LinuxHaxmInfoStep());
    }
  }

  @NotNull
  @Override
  public String getPathName() {
    return "Setup Android Studio Components";
  }

  private List<InstallableComponent> getSelectedComponents() throws WizardException {
    boolean customInstall = myState.getNotNull(KEY_CUSTOM_INSTALL, true);
    List<InstallableComponent> selectedOperations = Lists.newArrayListWithCapacity(COMPONENTS.length);

    for (InstallableComponent component : COMPONENTS) {
      if (!customInstall || myState.getNotNull(component.getKey(), true)) {
        selectedOperations.add(component);
      }
    }
    return selectedOperations;
  }

  @Override
  public void runLongOperation(@NotNull ProgressStep progressStep) throws WizardException {
    List<InstallableComponent> selectedComponents = getSelectedComponents();
    File tempDirectory = createTempDir();

    Set<DownloadableFileDescription> descriptions = Sets.newHashSet();
    for (InstallableComponent component : selectedComponents) {
      descriptions.addAll(component.getFilesToDownloadAndExpand());
    }
    InstallContext installContext = new InstallContext(tempDirectory, descriptions, progressStep);
    List<PreinstallOperation> preinstallOperations =
      ImmutableList.of(new DownloadOperation(installContext), new UnzipOperation(installContext));
    try {
      for (PreinstallOperation operation : preinstallOperations) {
        if (!operation.execute()) {
          return;
        }
      }
      progressStep.getProgressIndicator().setIndeterminate(true);
      for (InstallableComponent component : selectedComponents) {
        component.perform(installContext);
      }
    }
    finally {
      installContext.cleanup(progressStep);
    }
  }

  @Override
  public boolean performFinishingActions() {
    // Everything happens after wizard completion
    return true;
  }

  @Override
  public boolean isPathVisible() {
    return true;
  }

  public boolean showsStep() {
    if (isPathVisible()) {
      if (myInstallationTypeWizardStep != null && myInstallationTypeWizardStep.isStepVisible()) {
        return true;
      }
      try {
        for (InstallableComponent component : getSelectedComponents()) {
          if (component.hasVisibleStep()) {
            return true;
          }
        }
      }
      catch (WizardException e) {
        LOG.error(e);
      }
    }
    return false;
  }
}
