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

import com.android.tools.idea.sdk.wizard.LicenseAgreementStep;
import com.android.tools.idea.wizard.DynamicWizard;
import com.android.tools.idea.wizard.DynamicWizardHost;
import com.android.tools.idea.wizard.SingleStepPath;
import com.google.common.collect.Iterables;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * Wizard to setup Android Studio before the first run
 */
public class FirstRunWizard extends DynamicWizard {
  public static final String WIZARD_TITLE = "Android Studio Setup";

  private boolean myDone = false;

  public FirstRunWizard(DynamicWizardHost host) {
    super(null, null, WIZARD_TITLE, host);
    setTitle(WIZARD_TITLE);
  }

  @Override
  public void init() {
    addPath(new SingleStepPath(new FirstRunWelcomeStep()));
    addPath(new SetupJdkPath());
    addPath(new InstallComponentsPath());
    addPath(new SingleStepPath(new LicenseAgreementStep(getDisposable())));
    super.init();
  }

  // We need to show progress page before proceeding closing the wizard.
  @Override
  public void doFinishAction() {
    final Iterable<LongRunningOperationPath> filter = Iterables.filter(myPaths, LongRunningOperationPath.class);
    if (Iterables.isEmpty(filter) || myDone) {
      super.doFinishAction();
    }
    else if (myCurrentPath != null && !myCurrentPath.readyToLeavePath()) {
      myHost.shakeWindow();
    }
    else {
      new WizardCompletionAction().execute();
      final ProgressStep progressStep = new ProgressStep(getDisposable());
      showStep(progressStep);
      myHost.runSensitiveOperation(progressStep.getProgressIndicator(), true, new Runnable() {
        @Override
        public void run() {
          try {
            doLongRunningOperation(filter, progressStep);
          }
          catch (WizardException e) {
            Logger.getInstance(getClass()).error(e);
            progressStep.showConsole();
            progressStep.print(e.getMessage() + "\n", ConsoleViewContentType.ERROR_OUTPUT);
          }
        }
      });
    }
  }

  private void doLongRunningOperation(Iterable<LongRunningOperationPath> filter, @NotNull ProgressStep progressStep)
    throws WizardException {
    try {
      for (LongRunningOperationPath path : filter) {
        if (progressStep.isCanceled()) {
          break;
        }
        path.runLongOperation(progressStep);
      }
    }
    finally {
      myDone = true;
    }
  }

  @Override
  public void performFinishingActions() {
  }

  @Override
  protected String getWizardActionDescription() {
    return "Android Studio Setup";
  }
}
