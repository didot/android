/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.compiler;

import com.intellij.execution.configurations.CommandLineTokenizer;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "AndroidGradleBuildConfiguration", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class AndroidGradleBuildConfiguration implements PersistentStateComponent<AndroidGradleBuildConfiguration> {
  public boolean USE_EXPERIMENTAL_FASTER_BUILD = true;
  public boolean SYNC_PROJECT_BEFORE_BUILD;
  public String COMMAND_LINE_OPTIONS = "";

  public static AndroidGradleBuildConfiguration getInstance(Project project) {
    return ServiceManager.getService(project, AndroidGradleBuildConfiguration.class);
  }

  @Nullable
  @Override
  public AndroidGradleBuildConfiguration getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull AndroidGradleBuildConfiguration state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @NotNull
  public String[] getCommandLineOptions() {
    List<String> options = new ArrayList<>();
    CommandLineTokenizer tokenizer = new CommandLineTokenizer(COMMAND_LINE_OPTIONS);
    while(tokenizer.hasMoreTokens()) {
      options.add(tokenizer.nextToken());
    }
    return ArrayUtil.toStringArray(options);
  }
}
