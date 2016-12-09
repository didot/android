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
package com.android.tools.idea.project;

import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.run.AndroidRunConfigurationType;
import com.android.tools.idea.run.TargetSelectionMode;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.InstantAppUrlFinder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_INSTANTAPP;
import static com.android.tools.idea.run.AndroidRunConfiguration.DO_NOTHING;
import static com.android.tools.idea.run.AndroidRunConfiguration.LAUNCH_DEFAULT_ACTIVITY;
import static com.android.tools.idea.run.util.LaunchUtils.isWatchFaceApp;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;

public class AndroidRunConfigurations {
  @NotNull
  public static AndroidRunConfigurations getInstance() {
    return ServiceManager.getService(AndroidRunConfigurations.class);
  }

  public void createRunConfiguration(@NotNull AndroidFacet facet) {
    Module module = facet.getModule();
    ConfigurationFactory configurationFactory = AndroidRunConfigurationType.getInstance().getFactory();
    List<RunConfiguration> configurations = RunManager.getInstance(module.getProject()).getConfigurationsList(configurationFactory.getType());
    for (RunConfiguration configuration : configurations) {
      if (configuration instanceof AndroidRunConfiguration &&
          ((AndroidRunConfiguration)configuration).getConfigurationModule().getModule() == module) {
        // There is already a run configuration for this module.
        return;
      }
    }
    addRunConfiguration(facet, null, TargetSelectionMode.SHOW_DIALOG, null);
  }

  public void addRunConfiguration(@NotNull AndroidFacet facet,
                                  @Nullable String activityClass,
                                  @Nullable TargetSelectionMode targetSelectionMode,
                                  @Nullable String preferredAvdName) {
    Module module = facet.getModule();
    RunManager runManager = RunManager.getInstance(module.getProject());
    AndroidRunConfigurationType runConfigurationType = AndroidRunConfigurationType.getInstance();
    RunnerAndConfigurationSettings settings = runManager.createRunConfiguration(module.getName(), runConfigurationType.getFactory());
    AndroidRunConfiguration configuration = (AndroidRunConfiguration)settings.getConfiguration();
    configuration.setModule(module);

    if (activityClass != null) {
      configuration.setLaunchActivity(activityClass);
    }
    else if (facet.getProjectType() == PROJECT_TYPE_INSTANTAPP) {
      configuration.setLaunchUrl(getDefaultRunConfigurationUrl(facet));
    }
    else if (isWatchFaceApp(facet)) {
      // In case of a watch face app, there is only a service and no default activity that can be launched
      // Eventually, we'd need to support launching a service, but currently you cannot launch a watch face service as well.
      // See https://code.google.com/p/android/issues/detail?id=151353
      configuration.MODE = DO_NOTHING;
    }
    else {
      configuration.MODE = LAUNCH_DEFAULT_ACTIVITY;
    }

    if (targetSelectionMode != null) {
      configuration.getDeployTargetContext().setTargetSelectionMode(targetSelectionMode);
    }
    if (preferredAvdName != null) {
      configuration.PREFERRED_AVD = preferredAvdName;
    }
    runManager.addConfiguration(settings, false);
    runManager.setSelectedConfiguration(settings);
  }

  @NotNull
  private static String getDefaultRunConfigurationUrl(@NotNull AndroidFacet facet) {
    String defaultUrl = "<<ERROR - NO URL SET>>";
    assert facet.getProjectType() == PROJECT_TYPE_INSTANTAPP;
    String foundUrl = new InstantAppUrlFinder(MergedManifest.get(facet)).getDefaultUrl();
    return isEmpty(foundUrl) ? defaultUrl : foundUrl;
  }
}
