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
package org.jetbrains.android.exportSignedPackage;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.help.StudioHelpManagerImpl;
import com.intellij.ui.HyperlinkLabel;

import javax.swing.*;

public class ChooseBundleOrApkStep extends ExportSignedPackageWizardStep {
  public static final String DOC_URL = "https://d.android.com/r/studio-ui/dynamic-delivery/overview.html";
  private final ExportSignedPackageWizard myWizard;
  private JPanel myContentPanel;
  @VisibleForTesting
  JRadioButton myBundleButton;
  @VisibleForTesting
  JRadioButton myApksButton;
  private JPanel myBundlePanel;
  private JPanel myApkPanel;
  private HyperlinkLabel myLearnMoreLink;

  public ChooseBundleOrApkStep(ExportSignedPackageWizard wizard) {
    myWizard = wizard;

    myLearnMoreLink.setHyperlinkText("Learn more");
    myLearnMoreLink.setHyperlinkTarget(DOC_URL);
  }

  @Override
  public String getHelpId() {
    return StudioHelpManagerImpl.STUDIO_HELP_PREFIX + "dynamic-delivery/overview.html";
  }

  @Override
  protected void commitForNext() {
    myWizard.setTargetType(myBundleButton.isSelected() ? "bundle" : "apk");
  }

  @Override
  public JComponent getComponent() {
    return myContentPanel;
  }
}
