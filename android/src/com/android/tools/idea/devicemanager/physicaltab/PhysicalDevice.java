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

import com.android.resources.Density;
import com.android.tools.idea.devicemanager.Device;
import icons.StudioIcons;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Objects;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PhysicalDevice extends Device implements Comparable<@NotNull PhysicalDevice> {
  static final @NotNull Comparator<@Nullable Instant> LAST_ONLINE_TIME_COMPARATOR = Comparator.nullsLast(Comparator.reverseOrder());

  private static final @NotNull Comparator<@NotNull PhysicalDevice> PHYSICAL_DEVICE_COMPARATOR =
    Comparator.<PhysicalDevice, Boolean>comparing(Device::isOnline, Comparator.reverseOrder())
      .thenComparing(PhysicalDevice::getLastOnlineTime, LAST_ONLINE_TIME_COMPARATOR);

  private final @NotNull Key myKey;
  private final @Nullable Instant myLastOnlineTime;
  private final @NotNull String myNameOverride;
  private final @NotNull String myApi;
  private final @NotNull Collection<@NotNull ConnectionType> myConnectionTypes;
  private final @Nullable Resolution myResolution;
  private final int myDensity;
  private final @NotNull Collection<@NotNull String> myAbis;

  public static final class Builder extends Device.Builder {
    private @Nullable Key myKey;
    private @Nullable Instant myLastOnlineTime;
    private @NotNull String myNameOverride = "";
    private @Nullable String myApi;
    private final @NotNull Collection<@NotNull ConnectionType> myConnectionTypes = EnumSet.noneOf(ConnectionType.class);
    private @Nullable Resolution myResolution;
    private int myDensity = -1;
    private final @NotNull Collection<@NotNull String> myAbis = new ArrayList<>();

    public @NotNull Builder setKey(@NotNull Key key) {
      myKey = key;
      return this;
    }

    @NotNull Builder setLastOnlineTime(@Nullable Instant lastOnlineTime) {
      myLastOnlineTime = lastOnlineTime;
      return this;
    }

    public @NotNull Builder setName(@NotNull String name) {
      myName = name;
      return this;
    }

    @NotNull Builder setNameOverride(@NotNull String nameOverride) {
      myNameOverride = nameOverride;
      return this;
    }

    public @NotNull Builder setTarget(@NotNull String target) {
      myTarget = target;
      return this;
    }

    public @NotNull Builder setApi(@NotNull String api) {
      myApi = api;
      return this;
    }

    public @NotNull Builder addConnectionType(@NotNull ConnectionType connectionType) {
      myConnectionTypes.add(connectionType);
      return this;
    }

    @NotNull Builder addAllConnectionTypes(@NotNull Collection<@NotNull ConnectionType> connectionTypes) {
      myConnectionTypes.addAll(connectionTypes);
      return this;
    }

    @NotNull Builder setResolution(@Nullable Resolution resolution) {
      myResolution = resolution;
      return this;
    }

    @NotNull Builder setDensity(int density) {
      myDensity = density;
      return this;
    }

    @NotNull Builder addAllAbis(@NotNull Collection<@NotNull String> abis) {
      myAbis.addAll(abis);
      return this;
    }

    @Override
    public @NotNull PhysicalDevice build() {
      return new PhysicalDevice(this);
    }
  }

  private PhysicalDevice(@NotNull Builder builder) {
    super(builder);

    assert builder.myKey != null;
    myKey = builder.myKey;

    myLastOnlineTime = builder.myLastOnlineTime;
    myNameOverride = builder.myNameOverride;

    assert builder.myApi != null;
    myApi = builder.myApi;

    myConnectionTypes = builder.myConnectionTypes;
    myResolution = builder.myResolution;
    myDensity = builder.myDensity;
    myAbis = builder.myAbis;
  }

  @NotNull Key getKey() {
    return myKey;
  }

  @Nullable Instant getLastOnlineTime() {
    return myLastOnlineTime;
  }

  @Override
  public @NotNull Icon getIcon() {
    return StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE;
  }

  @NotNull String getNameOverride() {
    return myNameOverride;
  }

  @Override
  public boolean isOnline() {
    return !myConnectionTypes.isEmpty();
  }

  @NotNull String getApi() {
    return myApi;
  }

  @NotNull Collection<@NotNull ConnectionType> getConnectionTypes() {
    return myConnectionTypes;
  }

  @Nullable Resolution getResolution() {
    return myResolution;
  }

  @Nullable Resolution getDp() {
    if (myDensity == -1) {
      return null;
    }

    if (myResolution == null) {
      return null;
    }

    double density = myDensity;

    int width = (int)Math.ceil(Density.DEFAULT_DENSITY * myResolution.getWidth() / density);
    int height = (int)Math.ceil(Density.DEFAULT_DENSITY * myResolution.getHeight() / density);

    return new Resolution(width, height);
  }

  int getDensity() {
    return myDensity;
  }

  @NotNull Collection<@NotNull String> getAbis() {
    return myAbis;
  }

  @Override
  public int hashCode() {
    int hashCode = myKey.hashCode();

    hashCode = 31 * hashCode + Objects.hashCode(myLastOnlineTime);
    hashCode = 31 * hashCode + myNameOverride.hashCode();
    hashCode = 31 * hashCode + myName.hashCode();
    hashCode = 31 * hashCode + myTarget.hashCode();
    hashCode = 31 * hashCode + myApi.hashCode();
    hashCode = 31 * hashCode + myConnectionTypes.hashCode();
    hashCode = 31 * hashCode + Objects.hashCode(myResolution);
    hashCode = 31 * hashCode + myDensity;
    hashCode = 31 * hashCode + myAbis.hashCode();

    return hashCode;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof PhysicalDevice)) {
      return false;
    }

    PhysicalDevice device = (PhysicalDevice)object;

    return myKey.equals(device.myKey) &&
           Objects.equals(myLastOnlineTime, device.myLastOnlineTime) &&
           myNameOverride.equals(device.myNameOverride) &&
           myName.equals(device.myName) &&
           myTarget.equals(device.myTarget) &&
           myApi.equals(device.myApi) &&
           myConnectionTypes.equals(device.myConnectionTypes) &&
           Objects.equals(myResolution, device.myResolution) &&
           myDensity == device.myDensity &&
           myAbis.equals(device.myAbis);
  }

  @Override
  public int compareTo(@NotNull PhysicalDevice device) {
    return PHYSICAL_DEVICE_COMPARATOR.compare(this, device);
  }
}
