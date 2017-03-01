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
package com.android.tools.profilers;

import com.android.tools.profilers.common.CodeNavigator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public interface IdeProfilerServices {
  /**
   * Executor to run the tasks that should get back to the main thread.
   */
  @NotNull
  Executor getMainExecutor();

  /**
   * Executor to run the tasks that should run in a thread from the pool.
   */
  @NotNull
  Executor getPoolExecutor();

  /**
   * Saves a file to the file system and have IDE internal state reflect this file addition.
   *
   * @param file                     File to save to.
   * @param fileOutputStreamConsumer {@link Consumer} to write the file contents into the supplied {@link FileOutputStream}.
   * @param postRunnable             A callback for when the system finally finishes writing to and synchronizing the file.
   */
  void saveFile(@NotNull File file, @NotNull Consumer<FileOutputStream> fileOutputStreamConsumer, @Nullable Runnable postRunnable);

  /**
   * Returns a service that can navigate to a target code location.
   *
   * Implementors of this method should be sure to return the same instance each time, not a new
   * instance per call.
   */
  @NotNull
  CodeNavigator getCodeNavigator();
}
