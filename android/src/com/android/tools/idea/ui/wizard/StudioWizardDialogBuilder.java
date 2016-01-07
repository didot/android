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
package com.android.tools.idea.ui.wizard;

import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Convenience class for building a {@link ModelWizard} styled for Android Studio.
 */
public final class StudioWizardDialogBuilder {
  private static final Dimension DEFAULT_MIN_SIZE = JBUI.size(800, 650);

  /**
   * The minimum (and initial) size of a dialog should be no bigger than the user's screen (or,
   * a percentage of the user's screen, to leave a bit of space on the sides). This prevents
   * developers from specifying a size that looks good on their monitor but won't fit on a low
   * resolution screen. Worst case, the UI may end up squished for some users, but the
   * prev/next/cancel buttons will always be visible.
   */
  private static final float SCREEN_PERCENT = 0.8f;

  @NotNull ModelWizard myWizard;
  @NotNull String myTitle;
  @Nullable Component myParent;
  @Nullable Project myProject;
  @NotNull DialogWrapper.IdeModalityType myModalityType = DialogWrapper.IdeModalityType.IDE;
  @NotNull Dimension myMinimumSize = DEFAULT_MIN_SIZE;

  public StudioWizardDialogBuilder(@NotNull ModelWizard wizard, @NotNull String title) {
    myWizard = wizard;
    myTitle = title;
  }

  /**
   * Build a wizard with a parent component it should always show in front of. If you use this
   * constructor, any calls to {@link #setProject(Project)} and
   * {@link #setModalityType(DialogWrapper.IdeModalityType)} will be ignored.
   */
  public StudioWizardDialogBuilder(@NotNull ModelWizard wizard, @NotNull String title, @Nullable Component parent) {
    this(wizard, title);
    myParent = parent;
  }

  public StudioWizardDialogBuilder setProject(@Nullable Project project) {
    if (project != null) {
      myProject = project;
    }
    return this;
  }

  public StudioWizardDialogBuilder setModalityType(@Nullable DialogWrapper.IdeModalityType modalityType) {
    if (modalityType != null) {
      myModalityType = modalityType;
    }
    return this;
  }

  public StudioWizardDialogBuilder setMinimumSize(@NotNull Dimension minimumSize) {
    myMinimumSize = minimumSize;
    return this;
  }

  public ModelWizardDialog build() {
    StudioWizardLayout customLayout = new StudioWizardLayout();
    ModelWizardDialog dialog;
    if (myParent != null) {
      dialog = new ModelWizardDialog(myWizard, myTitle, myParent, customLayout);
    }
    else {
      dialog = new ModelWizardDialog(myWizard, myTitle, customLayout, myProject, myModalityType);
    }

    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    Dimension clampedSize = new Dimension(Math.min(myMinimumSize.width, (int)(screenSize.width * SCREEN_PERCENT)),
                                          Math.min(myMinimumSize.height, (int)(screenSize.height * SCREEN_PERCENT)));

    dialog.setSize(clampedSize.width, clampedSize.height);
    return dialog;
  }
}
