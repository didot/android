/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.npw.assetstudio.wizard;

import com.android.tools.idea.npw.assetstudio.IconGenerator;
import com.android.tools.idea.npw.assetstudio.icon.AndroidIconType;
import com.android.tools.idea.npw.assetstudio.icon.IconGeneratorResult;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Generates icons in a background thread using {@link IconGenerator} instances.
 */
public class IconGenerationProcessor {
  @NotNull private final List<Request> myImageRequests = new ArrayList<>();
  @Nullable private Request myRunningRequest;

  public void enqueue(@NotNull AndroidIconType iconType, @NotNull IconGenerator iconGenerator,
                      @NotNull Consumer<IconGeneratorResult> onDone) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    // If there is at least one request with the same icon type, ignore this one
    if (myImageRequests.stream().anyMatch(x -> Objects.equals(x.getIconType(), iconType))) {
      return;
    }

    if (iconGenerator.sourceAsset().get().isPresent()) {
      IconGenerator.Options options = iconGenerator.createOptions(true);
      Request request = new Request(iconType, iconGenerator, options, onDone);
      myImageRequests.add(request);
    }

    processNextRequest();
  }

  private void processNextRequest() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (myRunningRequest != null) {
      return;
    }

    if (myImageRequests.isEmpty()) {
      return;
    }

    myRunningRequest = myImageRequests.remove(0);
    Worker worker = new Worker(myRunningRequest, () -> {
      ApplicationManager.getApplication().assertIsDispatchThread();
      myRunningRequest = null;
      processNextRequest();
    });
    worker.execute();
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(IconGenerationProcessor.class);
  }

  private static class Request {
    @NotNull private final AndroidIconType myType;
    @NotNull private final IconGenerator myIconGenerator;
    @NotNull private final Consumer<IconGeneratorResult> myOnDone;
    @NotNull private final IconGenerator.Options myOptions;
    @Nullable private IconGeneratorResult myGeneratorResult;

    Request(@NotNull AndroidIconType iconType,
                   @NotNull IconGenerator iconGenerator,
                   @NotNull IconGenerator.Options options,
                   @NotNull Consumer<IconGeneratorResult> onDone) {
      myType = iconType;
      myIconGenerator = iconGenerator;
      myOptions = options;
      myOnDone = onDone;
    }

    public void run() {
      assert !ApplicationManager.getApplication().isDispatchThread();

      myGeneratorResult = myIconGenerator.generateIcons(myOptions);
    }

    public void done() {
      myOnDone.accept(myGeneratorResult);
    }

    @NotNull
    public AndroidIconType getIconType() {
      return myType;
    }
  }

  private static class Worker extends SwingWorker<Void, Void> {
    @NotNull private final Request myRequest;
    @NotNull private final Runnable myOnDone;

    Worker(@NotNull Request request, @NotNull Runnable onDone) {
      myRequest = request;
      myOnDone = onDone;
    }

    @Override
    protected Void doInBackground() throws Exception {
      long nanoStart = System.nanoTime();
      myRequest.run();
      long nanoEnd = System.nanoTime();
      getLog().info(String.format("Icons generated in %,d ms", (nanoEnd - nanoStart) / 1_000_000));
      return null;
    }

    @Override
    protected void done() {
      ApplicationManager.getApplication().assertIsDispatchThread();
      try {
        myRequest.done();
      }
      finally {
        // Don't run immediately to allow things to settle down if necessary.
        ApplicationManager.getApplication().invokeLater(myOnDone, ModalityState.any());
      }
    }
  }
}
