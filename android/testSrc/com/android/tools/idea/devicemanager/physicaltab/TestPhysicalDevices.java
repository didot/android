/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.physicaltab;

public final class TestPhysicalDevices {
  private static final Key GOOGLE_PIXEL_3_KEY = new SerialNumber("86UX00F4R");

  public static final PhysicalDevice GOOGLE_PIXEL_3 = new PhysicalDevice.Builder()
    .setKey(GOOGLE_PIXEL_3_KEY)
    .setName("Google Pixel 3")
    .setTarget("Android 12.0")
    .setApi("S")
    .build();

  static final PhysicalDevice ONLINE_GOOGLE_PIXEL_3 = new PhysicalDevice.Builder()
    .setKey(GOOGLE_PIXEL_3_KEY)
    .setName("Google Pixel 3")
    .setTarget("Android 12.0")
    .setApi("31")
    .addConnectionType(ConnectionType.USB)
    .build();

  public static final PhysicalDevice GOOGLE_PIXEL_5 = new PhysicalDevice.Builder()
    .setKey(new SerialNumber("0A071FDD4003ZG"))
    .setName("Google Pixel 5")
    .setTarget("Android 11.0")
    .setApi("30")
    .build();

  private TestPhysicalDevices() {
  }
}
