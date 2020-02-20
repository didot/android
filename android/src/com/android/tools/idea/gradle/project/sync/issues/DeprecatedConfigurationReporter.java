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
package com.android.tools.idea.gradle.project.sync.issues;

import com.android.builder.model.SyncIssue;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileHyperlink;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.builder.model.SyncIssue.TYPE_DEPRECATED_CONFIGURATION;

public class DeprecatedConfigurationReporter extends SimpleDeduplicatingSyncIssueReporter {
  @Override
  int getSupportedIssueType() {
    return TYPE_DEPRECATED_CONFIGURATION;
  }

  @Override
  @NotNull
  protected String getDeduplicationKey(@NotNull SyncIssue issue) {
    String config = extractConfigurationName(issue);
    return (config != null) ? config : issue.toString();
  }

  @Nullable
  private static String extractConfigurationName(@NotNull SyncIssue issue) {
    String data = issue.getData();
    if (data == null) {
      return null;
    }
    String[] parts = data.split("::");
    if (parts.length < 1) {
      return null;
    }

    return parts[0];
  }
}
