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
package com.android.tools.idea.gradle.project;

import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@State(
  name="GradleExperimentalSettings",
  storages = {
  @Storage(
    file = StoragePathMacros.APP_CONFIG + "/gradle.experimental.xml"
  )}
)
public class GradleExperimentalSettings implements PersistentStateComponent<GradleExperimentalSettings> {
  public boolean SELECT_MODULES_ON_PROJECT_IMPORT;
  public boolean SKIP_SOURCE_GEN_ON_PROJECT_SYNC;
  public int MAX_MODULE_COUNT_FOR_SOURCE_GEN = 5;
  public boolean LOAD_ALL_TEST_ARTIFACTS = true;
  public boolean USE_NEW_PROJECT_STRUCTURE_DIALOG;
  public boolean GROUP_NATIVE_SOURCES_BY_ARTIFACT;

  @NotNull
  public static GradleExperimentalSettings getInstance() {
    return ServiceManager.getService(GradleExperimentalSettings.class);
  }

  @Override
  @NotNull
  public GradleExperimentalSettings getState() {
    return this;
  }

  @Override
  public void loadState(GradleExperimentalSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
