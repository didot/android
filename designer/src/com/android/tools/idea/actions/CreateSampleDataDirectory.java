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
package com.android.tools.idea.actions;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.res.SampleDataResourceRepository;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.PlatformIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Action to create the main Sample Data directory
 */
public class CreateSampleDataDirectory extends AnAction {
  private static final Logger LOG = Logger.getInstance(CreateSampleDataDirectory.class);

  @SuppressWarnings("UnusedDeclaration")
  public CreateSampleDataDirectory() {
    super(AndroidBundle.message("new.sampledata.dir.action.title"), AndroidBundle.message("new.sampledata.dir.action.description"),
          PlatformIcons.FOLDER_ICON);
  }

  @Nullable
  private static AndroidFacet getFacet(@NotNull AnActionEvent e){
    Project project = e.getProject();
    Module[] modules = project != null ? Projects.getModulesToBuildFromSelection(project, e.getDataContext()) : null;
    return modules != null && modules.length > 0 ? AndroidFacet.getInstance(modules[0]) : null;
  }

  @Override
  public void update(AnActionEvent e) {
    if (!StudioFlags.NELE_SAMPLE_DATA.get()) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    AndroidFacet facet = getFacet(e);
    boolean visible;
    try {
      // Only display if the directory doesn't exist already
      visible = facet != null && SampleDataResourceRepository.getSampleDataDir(facet, false) == null;
    }
    catch (IOException ex) {
      visible = false;
    }
    e.getPresentation().setEnabledAndVisible(visible);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    if (!StudioFlags.NELE_SAMPLE_DATA.get()) {
      return;
    }

    AndroidFacet facet = getFacet(e);
    assert facet != null; // Needs to exist or the action wouldn't be visible
    try {
      SampleDataResourceRepository.getSampleDataDir(facet, true);
    }
    catch (IOException ex) {
      LOG.warn("Unable to create Sample Data directory", ex);
    }
  }
}
