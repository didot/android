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
package com.android.tools.idea.welcome;

import com.android.tools.idea.wizard.DynamicWizardPath;
import com.android.tools.idea.wizard.ScopedStateStore;
import com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Guides the user through the Android SDK discovery.
 */
public class SetupAndroidSdkPath extends DynamicWizardPath {
  private static final ScopedStateStore.Key<Boolean> KEY_SHOULD_DOWNLOAD =
    ScopedStateStore.createKey("should.download", ScopedStateStore.Scope.PATH, Boolean.class);
  private static final ScopedStateStore.Key<String> KEY_EXISTING_SDK_LOCATION =
    ScopedStateStore.createKey("existing.sdk.location", ScopedStateStore.Scope.PATH, String.class);
  private static final ScopedStateStore.Key<String> KEY_DOWNLOAD_SDK_LOCATION =
    ScopedStateStore.createKey("download.sdk.location", ScopedStateStore.Scope.PATH, String.class);

  private final ScopedStateStore.Key<Boolean> myIsCustomInstall;
  public SetupAndroidSdkPath(ScopedStateStore.Key<Boolean> isCustomInstall) {
    myIsCustomInstall = isCustomInstall;
  }

  @Override
  protected void init() {
    myState.put(KEY_SHOULD_DOWNLOAD, true);
    addStep(new SdkLocationStep(KEY_SHOULD_DOWNLOAD, KEY_EXISTING_SDK_LOCATION));
    addStep(new SdkComponentsStep(KEY_SHOULD_DOWNLOAD, KEY_DOWNLOAD_SDK_LOCATION));
  }

  @Override
  public boolean isPathVisible() {
    return Objects.equal(Boolean.TRUE, getState().get(myIsCustomInstall));
  }

  @NotNull
  @Override
  public String getPathName() {
    return "Setup Android SDK";
  }

  @Override
  public boolean performFinishingActions() {
    return false;
  }
}
