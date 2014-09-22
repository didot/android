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
package com.android.tools.idea.avdmanager;

import com.android.sdklib.internal.avd.AvdInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.awt.event.ActionEvent;

public class EditAvdAction extends AvdUiAction {
  private static final Logger LOG = Logger.getInstance(RunAvdAction.class);

  public EditAvdAction(AvdInfoProvider avdInfoProvider) {
    super(avdInfoProvider, "Edit", "Edit this AVD", AllIcons.Modules.Edit);
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    AvdEditWizard wizard = new AvdEditWizard(null, null, getAvdInfo(), false);
    wizard.init();
    wizard.show();
    refreshAvds();
  }

  @Override
  public boolean isEnabled() {
    return getAvdInfo() != null;
  }
}
