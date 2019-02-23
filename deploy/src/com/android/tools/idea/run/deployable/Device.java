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
package com.android.tools.idea.run.deployable;

import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/**
 * Device is a multi-threaded handler for resolving application IDs.
 * <p>
 * At the most basic level, there are three threads accessing this class:
 * 1) The DDMLib thread, which adds/removes/modifies {@link IDevice}s, thereby modifying this class via {@link #refresh()}.
 * 2) The UI thread, querying for {@link Client}s via {@link #findClientWithApplicationId(String)}.
 * 3) An internal {@link ExecutorService} that runs the bash scripts while eventually resolves the application ID.
 * <p>
 * This class is thread-safe, as all external modifications are performed through the through the synchronized refresh method.
 * Furthermore, all reads are done through thread-safe implementations of {@link HashMap}.
 */
public class Device {
  private static final Pattern PACKAGE_NAME_PATTERN = Pattern.compile("^package:(\\S+)\\s+.*");

  @NotNull private final ExecutorService myResolverExecutor;

  @NotNull private final IDevice myIDevice;

  // Map from PIDs to Process wrappers of processes on device (in some sense, the Clients).
  // Important note: this object is only meant to be used in a single-writer, multi-reader setting.
  // In this implementation, only refresh() updates this concurrent hash map.
  @NotNull private final Map<Integer, Process> myPidToProcess = new ConcurrentHashMap<>();

  // Pre-O devices don't have the API necessary to resolve the package name directly from the PID.
  // We need a package name to resolve properly for pre-O.
  // Map from given application IDs to a pending PID resolution on this device.
  @NotNull private final Map<String, Future<Void>> myResolutions = new ConcurrentHashMap<>();

  Device(@NotNull ExecutorService resolverExecutor, @NotNull IDevice device) {
    myResolverExecutor = resolverExecutor;
    myIDevice = device;
  }

  @NotNull
  public List<Client> findClientWithApplicationId(@NotNull String applicationId) {
    if (isLegacyDevice()) {
      myResolutions.computeIfAbsent(applicationId, ignored -> resolveLegacyPid(applicationId));
    }

    List<Client> clients = new ArrayList<>();
    for (Process process : myPidToProcess.values()) {
      if (applicationId.equals(process.getApplicationId())) {
        clients.add(process.getClient());
      }
    }

    // Fall back when we fail or haven't succeeded yet.
    if (clients.isEmpty()) {
      for (Client client : myIDevice.getClients()) {
        if (applicationId.equals(client.getClientData().getClientDescription()) ||
            applicationId.equals(client.getClientData().getPackageName())) {
          clients.add(client);
        }
      }
    }

    return clients;
  }

  synchronized void refresh() {
    Map<Integer, Client> clients = new HashMap<>(myIDevice.getClients().length);
    for (Client client : myIDevice.getClients()) {
      clients.put(client.getClientData().getPid(), client);
    }

    // Remove all existing ApplicationServices not in the new Client list.
    Set<Integer> removedPids = new HashSet<>(myPidToProcess.keySet());
    removedPids.removeAll(clients.keySet());
    myPidToProcess.keySet().removeAll(removedPids);

    // If any Client was reopened, we should update the Process to point to the new Client.
    myPidToProcess.keySet().forEach(pid -> {
      Client client = clients.get(pid);
      if (client != null) {
        myPidToProcess.get(pid).setClient(client);
      }
    });

    // Add all new Clients that do not already exist in the ApplicationServices set.
    Set<Integer> addedPids = new HashSet<>(clients.keySet());
    addedPids.removeAll(myPidToProcess.keySet());
    addedPids.forEach(pid -> {
      Client client = clients.get(pid);
      Process process = new Process(client);
      if (!isLegacyDevice()) {
        resolveApplicationId(process);
      }
      myPidToProcess.put(pid, process);
    });

    if (isLegacyDevice() && !addedPids.isEmpty()) {
      myResolutions.values().removeIf(resolution -> {
          resolution.cancel(true);
          return true;
        }
      );
    }
  }

  private boolean isLegacyDevice() {
    return myIDevice.getVersion().getFeatureLevel() < AndroidVersion.VersionCodes.O;
  }

  /**
   * Asynchronously resolves to the application ID. The application ID is the package name that is
   * ultimately given to the application on the device, which usually comes from the manifest.
   * Note that this may be different from {@link ClientData#getClientDescription()} or
   * {@link ClientData#getPackageName()} due to the manifest containing a process rename XML
   * option via "android:process".
   *
   * <p>When the manifest option is specified, the two aforementioned {@link ClientData }methods
   * will return the process name, not the application ID. Therefore, this method guarantees the
   * application ID if it's possible to resolve. Only if it's not possible will this fall back to
   * using the process name instead.
   *
   * @return a {@link ListenableFuture} that will eventually resolve to the application ID, or
   * {@code null} if this device does not support this feature.
   */
  private void resolveApplicationId(@NotNull Process process) {
    myResolverExecutor.submit(() -> {
      String command = String.format("stat -c %%u /proc/%d | xargs -n 1 pm list packages --uid", process.getPid());

      CollectingOutputReceiver receiver = new CollectingOutputReceiver();
      myIDevice.executeShellCommand(command, receiver);

      String output = receiver.getOutput();
      if (output.isEmpty()) {
        // We return null to coerce the lambda into a Callable instead of a Runnable.
        return null;
      }

      Matcher m = PACKAGE_NAME_PATTERN.matcher(output);
      if (m.find()) {
        process.setApplicationId(m.group(1));
      }
      return null;
    });
  }

  @NotNull
  private Future<Void> resolveLegacyPid(@NotNull String applicationId) {
    return myResolverExecutor.submit(
      () -> {
        // This shell command tries to retrieve the PID associated with a given application ID.
        // To achieve this goal, it does the following:
        //
        // 1) Gets the UID using run-as with the application ID and the whoami command.
        // 2) Runs the ps command under the application ID to get a list of all running
        //    processes running under the user associated with the given application ID.
        //    The output of ps looks something like: "<uid> <pid> ..."
        // 3) The output of ps is piped into grep/tr/cut to parse out the second parameter,
        //    of each line, which is the PID of each process.
        // 4) The pid is then used in the readlink command to follow the symbolic links of
        //    the symlinked exe file under the PID's /proc directory.
        // 5) If the symlink resolves to any of the 32 or 64 bit zygote process, the PID is
        //    printed to the console, serving as the output of the script.
        String command =
          String.format(
            "uid=`run-as %s whoami` && " +
            "for pid in `run-as %s ps | grep -o -p \"$uid[[:space:]]\\{1,\\}[[:digit:]]\\{1,\\}\" | tr -s ' ' ' ' | cut -d ' ' -f2`; do " +
            "  if [[ `run-as %s readlink /proc/$pid/exe` == /system/bin/app_process* ]]; then " +
            "    echo $pid; " +
            "  fi; " +
            "done",
            applicationId, applicationId, applicationId);
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        myIDevice.executeShellCommand(command, receiver);

        String output = receiver.getOutput();
        if (output.isEmpty()) {
          return null;
        }

        String[] lines = output.split("\n");
        // We only handle the first return value for now.
        try {
          for (String line : lines) {
            int pid = Integer.parseInt(line);
            myPidToProcess.computeIfPresent(pid, (ignored, process) -> {
              process.setApplicationId(applicationId);
              return process;
            });
          }
        }
        catch (NumberFormatException ignored) {
        }
        return null;
      });
  }
}
