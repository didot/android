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
package com.android.tools.idea.tests.gui.framework.fixture.gradle;

import com.android.tools.idea.gradle.project.build.BuildContext;
import com.android.tools.idea.gradle.project.build.BuildStatus;
import com.android.tools.idea.gradle.project.build.GradleBuildListener;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.util.BuildMode;
import javax.annotation.concurrent.GuardedBy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GradleProjectEventListener implements GradleBuildListener {
  @GuardedBy("myLock")
  private BuildMode myBuildMode;

  @GuardedBy("myLock")
  private long myBuildFinished;

  private final Object myLock = new Object();

  @Override
  public void buildExecutorCreated(@NotNull GradleBuildInvoker.Request request) {
  }

  @Override
  public void buildStarted(@NotNull BuildContext context) {
  }

  @Override
  public void buildFinished(@NotNull BuildStatus status, @Nullable BuildContext context) {
    if (status == BuildStatus.SUCCESS) {
      synchronized (myLock) {
      myBuildFinished = System.currentTimeMillis();
      myBuildMode = context != null ? context.getBuildMode() : null;
      }
    }
  }

  public void reset() {
    synchronized (myLock) {
      myBuildMode = null;
      myBuildFinished = -1;
    }
  }
  public boolean isBuildFinished(@NotNull BuildMode mode) {
    synchronized (myLock) {
      return myBuildFinished > 0 && myBuildMode == mode;
    }
  }

  public long getLastBuildTimestamp() {
    synchronized (myLock) {
      return myBuildFinished;
    }
  }
}
