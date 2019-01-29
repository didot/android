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
package com.android.tools.idea.testartifacts.instrumented;

import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.SimpleConfigurationType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.ui.LayeredIcon;
import icons.StudioIcons;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class AndroidTestRunConfigurationType extends SimpleConfigurationType {
  public AndroidTestRunConfigurationType() {
    super(AndroidCommonUtils.ANDROID_TEST_RUN_CONFIGURATION_TYPE,
          AndroidBundle.message("android.test.run.configuration.type.name"),
          AndroidBundle.message("android.test.run.configuration.type.description"), new NotNullLazyValue<Icon>() {
        @NotNull
        @Override
        protected Icon compute() {
          LayeredIcon icon = new LayeredIcon(2);
          icon.setIcon(StudioIcons.Shell.Filetree.ANDROID_PROJECT, 0);
          icon.setIcon(AllIcons.Nodes.JunitTestMark, 1);
          return icon;
        }
      });
  }

  @NotNull
  public static AndroidTestRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(AndroidTestRunConfigurationType.class);
  }

  @NotNull
  @Override
  public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
    return new AndroidTestRunConfiguration(project, this);
  }

  @Override
  public String getHelpTopic() {
    return "reference.dialogs.rundebug.AndroidTestRunConfigurationType";
  }
}
