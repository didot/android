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
package com.android.tools.idea.sdk.wizard;

import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.wizard.legacy.LicenseAgreementStep;
import com.android.tools.idea.welcome.install.*;
import com.android.tools.idea.welcome.wizard.ProgressStep;
import com.android.tools.idea.wizard.dynamic.*;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wizard that downloads (if necessary), configures, and installs HAXM.
 */
public class HaxmWizard extends DynamicWizard {
  HaxmPath myHaxmPath;

  public HaxmWizard() {
    super(null, null, "HAXM");
    myHaxmPath = new HaxmPath(myHost);
    addPath(myHaxmPath);
  }

  @Override
  public void performFinishingActions() {
    // Nothing. Handled by SetupProgressStep.
  }

  @Override
  public void doCancelAction() {
    if (myHaxmPath.canPerformFinishingActions()) {
      super.doFinishAction();
      return;
    }
    super.doCancelAction();
  }

  @NotNull
  @Override
  protected String getProgressTitle() {
    return "Finishing install...";
  }

  @Override
  protected String getWizardActionDescription() {
    return "HAXM Installation";
  }

  private static class SetupProgressStep extends ProgressStep {
    private Haxm myHaxm;
    private final AtomicBoolean myIsSuccessfullyCompleted = new AtomicBoolean(false);
    private DynamicWizardHost myHost;

    public SetupProgressStep(Disposable parentDisposable, Haxm haxm, DynamicWizardHost host) {
      super(parentDisposable);
      myHaxm = haxm;
      myHost = host;
    }

    @Override
    public boolean canGoNext() {
      return false;
    }

    public boolean isSuccessfullyCompleted() { return myIsSuccessfullyCompleted.get(); }

    @Override
    protected void execute() {
      myHost.runSensitiveOperation(getProgressIndicator(), true, new Runnable() {
        @Override
        public void run() {
          try {
            setupHaxm();
            myIsSuccessfullyCompleted.set(true);
          }
          catch (Exception e) {
            Logger.getInstance(getClass()).error(e);
            showConsole();
            print(e.getMessage() + "\n", ConsoleViewContentType.ERROR_OUTPUT);
          }
        }
      });
    }

    @Override
    public boolean canGoPrevious() {
      return false;
    }

    private void setupHaxm() throws IOException {
      final InstallContext installContext = new InstallContext(FileUtil.createTempDirectory("AndroidStudio", "Haxm", true), this);
      final AndroidSdkHandler sdkHandler = AndroidSdkUtils.tryToChooseSdkHandler();
      myHaxm.updateState(sdkHandler);
      final Collection<? extends InstallableComponent> selectedComponents = Lists.newArrayList(myHaxm);
      installContext.print("Looking for SDK updates...\n", ConsoleViewContentType.NORMAL_OUTPUT);

      // Assume install and configure take approximately the same time; assign 0.5 progressRatio to each
      InstallComponentsOperation install =
        new InstallComponentsOperation(installContext, selectedComponents, new ComponentInstaller(sdkHandler), 0.5);

      try {
        install.then(InstallOperation.wrap(installContext, new Function<File, File>() {
          @Override
          public File apply(@Nullable File input) {
            myHaxm.configure(installContext, sdkHandler);
            return input;
          }
        }, 0.5)).execute(sdkHandler.getLocation());
      }
      catch (InstallationCancelledException e) {
        installContext.print("Android Studio setup was canceled", ConsoleViewContentType.ERROR_OUTPUT);
      }
      catch (WizardException e) {
        throw new RuntimeException(e);
      }
      installContext.print("Done", ConsoleViewContentType.NORMAL_OUTPUT);
    }
  }

  private class HaxmPath extends DynamicWizardPath {
    DynamicWizardHost myHost;
    SetupProgressStep mySetupProgressStep;

    public HaxmPath(DynamicWizardHost host) {
      myHost = host;
    }

    @Override
    protected void init() {
      ScopedStateStore.Key<Boolean> canShow = ScopedStateStore.createKey("ShowHaxmSteps", ScopedStateStore.Scope.PATH, Boolean.class);
      myState.put(canShow, true);
      Haxm haxm = new Haxm(getState(), canShow, true);

      for (DynamicWizardStep step : haxm.createSteps()) {
        addStep(step);
      }
      addStep(new LicenseAgreementStep(getWizard().getDisposable()));
      mySetupProgressStep = new SetupProgressStep(getWizard().getDisposable(), haxm, myHost);
      addStep(mySetupProgressStep);
      haxm.init(mySetupProgressStep);
    }

    @NotNull
    @Override
    public String getPathName() {
      return "Haxm Path";
    }

    @Override
    public boolean canPerformFinishingActions() { return mySetupProgressStep.isSuccessfullyCompleted(); }

    @Override
    public boolean performFinishingActions() {
      return true;
    }
  }
}
