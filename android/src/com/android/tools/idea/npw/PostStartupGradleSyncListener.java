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
package com.android.tools.idea.npw;

import com.android.annotations.NonNull;
import com.android.tools.idea.gradle.project.importing.NewProjectImportGradleSyncListener;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.project.Project;

final class PostStartupGradleSyncListener extends NewProjectImportGradleSyncListener {
  private final Runnable myRunnable;

  PostStartupGradleSyncListener(@NonNull Runnable runnable) {
    myRunnable = runnable;
  }

  @Override
  public void syncSucceeded(@NonNull Project project) {
    StartupManagerEx manager = StartupManagerEx.getInstanceEx(project);

    if (manager.postStartupActivityPassed()) {
      myRunnable.run();
    }
    else {
      manager.registerPostStartupActivity(myRunnable);
    }
  }
}
