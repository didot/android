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
package com.android.tools.idea.editors.gfxtrace.gapi;

import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class ChildProcess {
  @NotNull private static final Logger LOG = Logger.getInstance(GfxTraceEditor.class);
  @NotNull private static final Pattern PORT_PATTERN = Pattern.compile("^Bound on port '(\\d+)'$", 0);
  private final String myName;
  private Thread myServerThread;

  ChildProcess(String name) {
    myName = name;
  }

  protected abstract boolean prepare(ProcessBuilder pb);

  protected abstract void onExit(int code);

  public boolean isRunning() {
    return myServerThread != null && myServerThread.isAlive();
  }

  public SettableFuture<Integer> start() {
    final ProcessBuilder pb = new ProcessBuilder();
    pb.directory(GapiPaths.base());
    pb.redirectErrorStream(true);
    if (!prepare(pb)) {
      return null;
    }
    final SettableFuture<Integer> portF = SettableFuture.create();
    myServerThread = new Thread() {
      @Override
      public void run() {
        runProcess(portF, pb);
      }
    };
    myServerThread.start();
    return portF;
  }

  private void runProcess(final SettableFuture<Integer> portF, final ProcessBuilder pb) {
    // Use the base directory as the working directory for the server.
    Process process = null;
    try {
      // This will throw IOException if the executable is not found.
      LOG.info("Starting " + myName + " as " + pb.toString());
      process = pb.start();
    }
    catch (IOException e) {
      LOG.warn(e);
      portF.setException(e);
      return;
    }
    final BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.forName("UTF-8")));
    try {
      new Thread() {
        @Override
        public void run() {
          processOutput(portF, stdout);
        }
      }.start();
      try {
        onExit(process.waitFor());
      }
      catch (InterruptedException e) {
        LOG.info("Killing " + myName);
        portF.setException(e);
        process.destroy();
      }
    }
    finally {
      try {
        stdout.close();
      }
      catch (IOException ignored) {
      }
    }
  }

  private void processOutput(final SettableFuture<Integer> portF, final BufferedReader stdout) {
    try {
      boolean seenPort = false;
      for (String line; (line = stdout.readLine()) != null; ) {
        if (!seenPort) {
          Matcher matcher = PORT_PATTERN.matcher(line);
          if (matcher.matches()) {
            int port = Integer.parseInt(matcher.group(1));
            seenPort = true;
            portF.set(port);
            LOG.warn("Detected server " + myName + " startup on port " + port);
          }
        }
        LOG.info(myName + ": " + line);
      }
    }
    catch (IOException ignored) {
    }
  }

  public void shutdown() {
    LOG.info("Shutting down " + myName);
    myServerThread.interrupt();
  }
}
