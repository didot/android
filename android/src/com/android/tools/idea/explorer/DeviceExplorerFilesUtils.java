/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.explorer;

import com.android.annotations.concurrency.AnyThread;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

public class DeviceExplorerFilesUtils {

  /**
   * Creates a {@link VirtualFile} corresponding to the {@link Path} passed as argument.
   */
  @AnyThread
  @NotNull
  public static ListenableFuture<VirtualFile> findFile(@NotNull Path localPath) {
    // Note: We run this operation inside a transaction because we need to refresh a VirtualFile instance.
    //       See https://github.com/JetBrains/intellij-community/commit/10c0c11281b875e64c31186eac20fc28ba3fc37a
    SettableFuture<VirtualFile> futureFile = SettableFuture.create();
    TransactionGuard.submitTransaction(ApplicationManager.getApplication(), () -> {
      VirtualFile localFile = VfsUtil.findFileByIoFile(localPath.toFile(), true);
      if (localFile == null) {
        futureFile.setException(new RuntimeException(String.format("Unable to locate file \"%s\"", localPath)));
      }
      else {
        futureFile.set(localFile);
      }
    });
    return futureFile;
  }
}
