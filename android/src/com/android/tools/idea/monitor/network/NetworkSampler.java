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
package com.android.tools.idea.monitor.network;

import com.android.ddmlib.*;
import com.android.tools.adtui.TimelineData;
import com.android.tools.idea.monitor.DeviceSampler;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class NetworkSampler extends DeviceSampler {
  public static final String NETWORK_STATS_FILE = "/proc/net/xt_qtaguid/stats";
  private static final int TIMELINE_DATA_STREAM_SIZE = 2;
  private static final int TIMELINE_DATA_SIZE = 2048;
  private static final int MAX_TIMEOUT_SECOND = 1;
  private static final String LINE_SPLIT_REGEX = "[ \t\r\n\f]";

  private int myUid;
  private long myStartingRxBytes;
  private long myStartingTxBytes;

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(DeviceSampler.class);
  }

  public NetworkSampler(int frequencyMs) {
    super(new TimelineData(TIMELINE_DATA_STREAM_SIZE, TIMELINE_DATA_SIZE, new TimelineData.AreaTransform()), frequencyMs);
  }

  @NotNull
  @Override
  public String getName() {
    return "Network Sampler";
  }

  @Override
  public void start() {
    myUid = -1;
    myStartingRxBytes = -1L;
    myStartingTxBytes = -1L;
    // Start the sampling after data is reset.
    super.start();
  }

  /**
   * Returns whether the network monitoring stats file is present in the system image, the returned value is positive if the file
   * is present, or negative if the stats file is absent which is a bug being fixed, or zero if the checking is interrupted by
   * unexpected things.
   */
  public int checkStatsFile(@NotNull Client client) {
    // Stops checking if the selected client is changed.
    if (getClient() != client) {
      return 0;
    }
    IDevice device = client.getDevice();
    if (device == null || device.isOffline()) {
      return -1;
    }

    CollectingOutputReceiver receiver = new CollectingOutputReceiver();
    try {
      // Timeout should not be set since this is run on a pooled thread. Some machines may be slow and timeout can cause exit too early.
      device.executeShellCommand("ls " + NETWORK_STATS_FILE, receiver);
      return receiver.getOutput().contains("No such file") ? -1 : 1;
    }
    catch (TimeoutException timeoutException) {
      getLog().warn(String.format("TimeoutException %1$s in ls %2$s", timeoutException.getMessage(), NETWORK_STATS_FILE));
    }
    catch (AdbCommandRejectedException rejectedException) {
      getLog().warn(
        String.format("AdbCommandRejectedException %1$s in ls %2$s", rejectedException.getMessage(), NETWORK_STATS_FILE));
    }
    catch (ShellCommandUnresponsiveException unresponsiveException) {
      getLog().warn(
        String.format("ShellCommandUnresponsiveException %1$s in ls %2$s", unresponsiveException.getMessage(), NETWORK_STATS_FILE));
    }
    catch (IOException ioException) {
      getLog().warn(String.format("IOException %1$s in ls %2$s", ioException.getMessage(), NETWORK_STATS_FILE));
    }
    return 0;
  }

  /**
   * Samples the network usage belonging to the connected device and application.
   */
  @Override
  protected void sample(boolean forced) throws InterruptedException {
    Client client = getClient();
    IDevice device = client != null ? client.getDevice() : null;
    if (device == null) {
      return;
    }

    if (myUid < 0) {
      int pid = client.getClientData().getPid();
      myUid = getUidFromPid(pid, device);
      if (myUid < 0) {
        return;
      }
    }

    NetworkStatsReceiver receiver = new NetworkStatsReceiver(myUid);
    // Command that gets network metrics, the output includes the connected app and other apps' stats.
    String command = "cat " + NETWORK_STATS_FILE + " | grep " + receiver.getUid();
    int myDataType = TYPE_DATA;
    try {
      device.executeShellCommand(command, receiver, MAX_TIMEOUT_SECOND, TimeUnit.SECONDS);
    }
    catch (TimeoutException timeoutException) {
      myDataType = TYPE_TIMEOUT;
    }
    catch (AdbCommandRejectedException | ShellCommandUnresponsiveException | IOException ignored) {
      myDataType = TYPE_UNREACHABLE;
    }
    if (receiver.isFileMissing() || myDataType != TYPE_DATA) {
      return;
    }
    if (myStartingRxBytes < 0) {
      myStartingRxBytes = receiver.getRxBytes();
      myStartingTxBytes = receiver.getTxBytes();
    }
    else {
      getTimelineData().add(System.currentTimeMillis(), myDataType, (receiver.getRxBytes() - myStartingRxBytes) / 1024.f,
                            (receiver.getTxBytes() - myStartingTxBytes) / 1024.f);
    }
  }

  private static int getUidFromPid(int pid, IDevice device) {
    UidReceiver uidReceiver = new UidReceiver();
    try {
      device.executeShellCommand("cat /proc/" + pid + "/status", uidReceiver, MAX_TIMEOUT_SECOND, TimeUnit.SECONDS);
    }
    catch (TimeoutException timeoutException) {
      getLog().warn(String.format("TimeoutException to get uid from pid %d", pid));
    }
    catch (AdbCommandRejectedException commandRejectedException) {
      getLog().warn(String.format("AdbCommandRejectedException to get uid from pid %d", pid));
    }
    catch (ShellCommandUnresponsiveException commandUnresponsiveException) {
      getLog().warn(String.format("ShellCommandUnresponsiveException to get uid from pid %d", pid));
    }
    catch (IOException ioException) {
      getLog().warn(String.format("IOException to get uid from pid %d", pid));
    }
    return uidReceiver.getUid();
  }

  /**
   * Object to receive the shell get pid status command output and get the uid.
   */
  private static class UidReceiver extends MultiLineReceiver {

    private static final int INDEX_OF_EFFECTIVE_USER_ID = 1;

    private int myUid;

    UidReceiver() {
      myUid = -1;
    }

    public int getUid() {
      return myUid;
    }

    @Override
    public void processNewLines(String[] lines) {
      for (String line : lines) {
        if (line.startsWith("Uid:")) {
          String[] values = line.split(LINE_SPLIT_REGEX);
          if (values.length <= INDEX_OF_EFFECTIVE_USER_ID) {
            getLog().warn(String.format("NumberFormatException %1$s \n length %2$d", line, values.length));
            return;
          }
          try {
            myUid = Integer.parseInt(values[INDEX_OF_EFFECTIVE_USER_ID]);
          }
          catch (NumberFormatException e) {
            getLog().warn(String.format("NumberFormatException %1$s in %2$s", e.getMessage(), line));
          }
          break;
        }
      }
    }

    @Override
    public boolean isCancelled() {
      return false;
    }
  }

  /**
   * Object to receive the network stats adb command output and parse the table-formatted output.
   */
  private static class NetworkStatsReceiver extends MultiLineReceiver {

    private static int INDEX_OF_UID = 3;
    private static int INDEX_OF_RX_BYTES = 5;
    private static int INDEX_OF_TX_BYTES = 7;

    private final int myUid;
    private long myRxBytes;
    private long myTxBytes;
    private boolean myIsFileMissing;

    NetworkStatsReceiver(int uid) {
      this.myUid = uid;
      this.myRxBytes = 0L;
      this.myTxBytes = 0L;
      this.myIsFileMissing = false;
    }

    public int getUid() {
      return myUid;
    }

    public long getRxBytes() {
      return myRxBytes;
    }

    public long getTxBytes() {
      return myTxBytes;
    }

    public boolean isFileMissing() {
      return myIsFileMissing;
    }

    /**
     * Processes the stats line to sum up all network stats belonging to the uid.
     */
    @Override
    public void processNewLines(String[] lines) {
      for (String line : lines) {
        // Line starting with idx is the header.
        if (line.startsWith("idx")) {
          continue;
        }
        if (line.contains("No such file")) {
          myIsFileMissing = true;
          return;
        }
        String[] values = line.split(LINE_SPLIT_REGEX);
        if (values.length < INDEX_OF_TX_BYTES) {
          continue;
        }

        // Gets the network usages belonging to the uid.
        try {
          int lineUid = Integer.parseInt(values[INDEX_OF_UID]);
          if (myUid == lineUid) {
            int tempRxBytes = Integer.parseInt(values[INDEX_OF_RX_BYTES]);
            int tempTxBytes = Integer.parseInt(values[INDEX_OF_TX_BYTES]);
            if (tempRxBytes < 0 || tempTxBytes < 0) {
              getLog().warn(String.format("Negative rxBytes %1$d and/or txBytes %2$d in %3$s", tempRxBytes, tempTxBytes, line));
              continue;
            }
            myRxBytes += tempRxBytes;
            myTxBytes += tempTxBytes;
          }
        }
        catch (NumberFormatException e) {
          getLog().warn(String.format("Expected int value, instead got uid %1$s, rxBytes %2$s, txBytes %3$s in %4$s", values[INDEX_OF_UID],
                                      values[INDEX_OF_RX_BYTES], values[INDEX_OF_TX_BYTES], line));
        }
      }
    }

    @Override
    public boolean isCancelled() {
      return false;
    }
  }
}
