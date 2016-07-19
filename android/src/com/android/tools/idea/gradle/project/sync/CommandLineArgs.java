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
package com.android.tools.idea.gradle.project.sync;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.AndroidPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.android.builder.model.AndroidProject.*;
import static com.android.builder.model.AndroidProject.MODEL_LEVEL_2_DEP_GRAPH;
import static com.android.builder.model.AndroidProject.PROPERTY_REFRESH_EXTERNAL_NATIVE_MODEL;
import static com.android.tools.idea.gradle.actions.RefreshLinkedCppProjectsAction.REFRESH_EXTERNAL_NATIVE_MODELS_KEY;
import static com.android.tools.idea.gradle.service.notification.hyperlink.SyncProjectWithExtraCommandLineOptionsHyperlink.EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY;
import static com.android.tools.idea.gradle.util.AndroidGradleSettings.createProjectProperty;
import static com.android.tools.idea.gradle.util.GradleUtil.addLocalMavenRepoInitScriptCommandLineOption;
import static com.intellij.util.ArrayUtil.toStringArray;
import static org.jetbrains.android.AndroidPlugin.isGuiTestingMode;

final class CommandLineArgs {
  @NotNull private final Project myProject;

  CommandLineArgs(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  List<String> get() {
    List<String> args = new ArrayList<>();
    args.add("-Didea.resolveSourceSetDependencies=true");

    String[] options = myProject.getUserData(EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY);
    if (options != null) {
      myProject.putUserData(EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY, null);
      Collections.addAll(args, options);
    }

    // These properties tell the Android Gradle plugin that we are performing a sync and not a build.
    args.add(createProjectProperty(PROPERTY_BUILD_MODEL_ONLY, true));
    args.add(createProjectProperty(PROPERTY_BUILD_MODEL_ONLY_ADVANCED, true));
    args.add(createProjectProperty(PROPERTY_INVOKED_FROM_IDE, true));

    // This property tells Gradle to compute the full dependency graph for projects using the Android plugin 2.2.0 or newer.
    args.add(createProjectProperty(PROPERTY_BUILD_MODEL_ONLY_VERSIONED, MODEL_LEVEL_2_DEP_GRAPH));

    Boolean refreshExternalNativeModels = myProject.getUserData(REFRESH_EXTERNAL_NATIVE_MODELS_KEY);
    if (refreshExternalNativeModels != null) {
      myProject.putUserData(REFRESH_EXTERNAL_NATIVE_MODELS_KEY, null);
      args.add(createProjectProperty(PROPERTY_REFRESH_EXTERNAL_NATIVE_MODEL, refreshExternalNativeModels));
    }

    if (isGuiTestingMode() || ApplicationManager.getApplication().isUnitTestMode()) {
      // We store the command line args, the GUI test will later on verify that the correct values were passed to the sync process.
      ApplicationManager.getApplication().putUserData(AndroidPlugin.GRADLE_SYNC_COMMAND_LINE_OPTIONS_KEY, toStringArray(args));
    }

    addLocalMavenRepoInitScriptCommandLineOption(args);
    return args;
  }
}
