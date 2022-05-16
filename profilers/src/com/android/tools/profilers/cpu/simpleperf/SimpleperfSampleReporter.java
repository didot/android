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
package com.android.tools.profilers.cpu.simpleperf;

import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.idea.util.StudioPathManager;
import com.android.tools.profilers.cpu.TracePreProcessor;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.system.CpuArch;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public final class SimpleperfSampleReporter implements TracePreProcessor {

  public SimpleperfSampleReporter() { }

  private static Logger getLogger() {
    return Logger.getInstance(SimpleperfSampleReporter.class);
  }

  /**
   * Receives a raw trace generated by running `simpleperf record` on an Android process, invokes `simpleperf report-sample`, and return
   * the output if conversion is made successfully. If there is a failure while converting the raw trace to the format supported by
   * {@link SimpleperfTraceParser}, return {@link #FAILURE}.
   *
   * @param trace       The raw trace data.
   * @param symbolDirs A list of paths used to find symbols for the given trace.
   *                    Note: They're passed to simpleperf report-sample command using the --symdir flag.
   *                    One --symdir flag should be passed for each directory provided. If null the --symdir flag is not passed.
   * @return
   */
  @Override
  @NotNull
  public ByteString preProcessTrace(@NotNull ByteString trace, @NotNull List<String> symbolDirs) {
    try {
      if (trace.isEmpty()) {
        getLogger().error("Simpleperf preprocessing exited unsuccessfully. Input trace is empty.");
        return FAILURE;
      }

      File processedTraceFile = FileUtil.createTempFile(
        String.format("%s%ctrace-%d", FileUtil.getTempDirectory(), File.separatorChar, System.currentTimeMillis()), ".trace", true);

      List<String> command = getReportSampleCommand(trace, processedTraceFile, symbolDirs);
      getLogger().info("Running simpleperf command: " + command);
      Process reportSample = new ProcessBuilder(command).start();
      reportSample.waitFor();

      boolean reportSampleSuccess = reportSample.exitValue() == 0;
      if (!reportSampleSuccess) {
        String error = new BufferedReader(new InputStreamReader(reportSample.getErrorStream(), StandardCharsets.UTF_8)).readLine();
        getLogger().warn("simpleperf report-sample exited unsuccessfully. " + error);
        return FAILURE;
      }

      ByteString processedTrace = ByteString.copyFrom(Files.readAllBytes(processedTraceFile.toPath()));
      processedTraceFile.delete();
      return processedTrace;
    }
    catch (IOException e) {
      getLogger().warn(String.format("I/O error when trying to execute simpleperf report-sample:\n%s", e.getMessage()));
      return FAILURE;
    }
    catch (InterruptedException e) {
      getLogger().warn(String.format("Failed to wait for simpleperf report-sample command to run:\n%s", e.getMessage()));
      return FAILURE;
    }
  }

  @VisibleForTesting
  List<String> getReportSampleCommand(@NotNull ByteString trace, @NotNull File processedTrace, @NotNull List<String> symbolDirs) throws IOException {
    List<String> command = new ArrayList<>();
    command.add(getSimpleperfBinaryPath());
    command.add("report-sample");
    command.add("--protobuf");
    command.add("--show-callchain");
    command.add("-i");
    command.add(tempFileFromByteString(trace).getAbsolutePath());
    command.add("-o");
    command.add(processedTrace.getAbsolutePath());
    for (String path : symbolDirs) {
      command.add("--symdir");
      command.add(path);
    }
    return command;
  }

  @VisibleForTesting
  String getSimpleperfBinaryPath() {
    String subDir = getSimpleperfBinarySubdirectory();
    String binaryName = getSimpleperfBinaryName();
    if (StudioPathManager.isRunningFromSources())  {
      // Running from sources, so use the prebuilts path. For example:
      // $REPO/prebuilts/tools/windows/simpleperf/simpleperf.exe.
      return Paths.get(StudioPathManager.resolveDevPath("prebuilts/tools/${subDir}/simpleperf/${binaryName}")).toString();
    } else {
      // Release build. For instance:
      // $IDEA_HOME/plugins/android/resources/simpleperf/darwin-x86_64/simpleperf
      return Paths.get(PathManager.getHomePath(), "plugins", "android", "resources", "simpleperf", subDir, binaryName).toString();
    }
  }

  private static File tempFileFromByteString(@NotNull ByteString bytes) throws IOException {
    File file = FileUtil.createTempFile(String.format("cpu_trace_%d", System.currentTimeMillis()), ".trace", true);
    try (FileOutputStream out = new FileOutputStream(file)) {
      out.write(bytes.toByteArray());
    }
    return file;
  }

  private static String getSimpleperfBinarySubdirectory() {
    if (SystemInfo.isLinux && CpuArch.isIntel32()) {
      return "linux-x86";
    }
    else if (SystemInfo.isLinux && CpuArch.isIntel64()) {
      return "linux-x86_64";
    }
    else if (SystemInfo.isMac && CpuArch.isIntel64()) {
      return "darwin-x86_64";
    }
    else if (SystemInfo.isMac && CpuArch.isArm64()) {
      // TODO: Update this when NDK supports mac-arm natively.
      return "darwin-x86_64";
    }
    else if (SystemInfo.isWindows && CpuArch.isIntel32()) {
      return "windows";
    }
    else if (SystemInfo.isWindows && CpuArch.isIntel64()) {
      return "windows-x86_64";
    }
    else {
      throw new IllegalStateException("Unknown operating system/CPU architecture");
    }
  }

  private static String getSimpleperfBinaryName() {
    return SystemInfo.isWindows ? "simpleperf.exe" : "simpleperf";
  }
}
