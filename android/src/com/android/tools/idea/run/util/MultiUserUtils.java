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
package com.android.tools.idea.run.util;

import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * {@link MultiUserUtils} provides utility methods related to
 * This class should eventually be moved into {@link com.android.ddmlib.IDevice} itself.
 */
public class MultiUserUtils {
  public static final int PRIMARY_USERID = 0;

  public static boolean isCurrentUserThePrimaryUser(@NotNull IDevice device, long timeout, TimeUnit units, boolean defaultValue) {
    if (device.getVersion().getApiLevel() < AndroidVersion.SUPPORTS_MULTI_USER.getApiLevel()) {
      return false;
    }

    CountDownLatch latch = new CountDownLatch(1);
    CollectingOutputReceiver receiver = new CollectingOutputReceiver(latch);
    try {
      device.executeShellCommand("am get-current-user", receiver);
    }
    catch (Exception e) {
      return defaultValue;
    }

    try {
      latch.await(timeout, units);
    }
    catch (InterruptedException e) {
      Logger.getInstance(MultiUserUtils.class).warn("Timed out waiting for output from `am get-current-user`, returning " + defaultValue);
      return defaultValue;
    }

    String output = receiver.getOutput();
    try {
      return Integer.parseInt(output.trim()) == PRIMARY_USERID;
    }
    catch (NumberFormatException e) {
      if (output.length() > 40) {
        output = output.substring(0, 40) + "...";
      }
      Logger.getInstance(MultiUserUtils.class).warn("Error parsing output of `am get-current-user`: " + output);
      return defaultValue;
    }
  }

  public static boolean hasMultipleUsers(@Nullable IDevice device, long timeout, TimeUnit units, boolean defaultValue) {
    if (device == null) {
      return defaultValue;
    }

    if (device.getVersion().getApiLevel() < AndroidVersion.SUPPORTS_MULTI_USER.getApiLevel()) {
      return false;
    }

    CountDownLatch latch = new CountDownLatch(1);
    CollectingOutputReceiver receiver = new CollectingOutputReceiver(latch);
    try {
      device.executeShellCommand("pm list users", receiver);
    }
    catch (Exception e) {
      return defaultValue;
    }

    try {
      latch.await(timeout, units);
    }
    catch (InterruptedException e) {
      Logger.getInstance(MultiUserUtils.class).warn("Timed out waiting for output from `pm list users`, returning " + defaultValue);
      return defaultValue;
    }

    // Output is of the form:
    // <some devices have error messages here, e.g. WARNING: linker: libdvm.so has text relocations
    // Users:
    //    UserInfo{0:Foo:13} running
    //    UserInfo{11:Sample Managed Profile:30} running

    String[] lines = receiver.getOutput().trim().split("\n");
    int numUsers = 0;
    for (String line : lines) {
      if (line.contains("UserInfo{")) {
        numUsers++;
      }
    }

    return numUsers > 1;
  }

  public static int getUserIdFromAmParameters(@NotNull String amFlags) {
    String userFlag = "--user";

    int i = amFlags.indexOf(userFlag);
    if ((i < 0)) {
      return PRIMARY_USERID;
    }

    i += userFlag.length() + 1; // go past --user
    if (i > amFlags.length()) {
      return PRIMARY_USERID;
    }

    amFlags = amFlags.substring(i).trim();
    try {
      return Integer.parseInt(amFlags);
    }
    catch (NumberFormatException e) {
      return PRIMARY_USERID;
    }
  }
}
