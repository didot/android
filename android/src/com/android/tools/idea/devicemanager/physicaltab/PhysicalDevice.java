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

import com.android.tools.idea.devicemanager.Device;
import com.android.tools.idea.devicemanager.DeviceType;
import com.android.tools.idea.devicemanager.Resolution;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PhysicalDevice extends Device {
  private final @NotNull String myNameOverride;
  private final @NotNull ImmutableCollection<@NotNull ConnectionType> myConnectionTypes;
  private final @Nullable Battery myPower;
  private final @Nullable Resolution myResolution;
  private final int myDensity;
  private final @NotNull ImmutableCollection<@NotNull String> myAbis;
  private final @Nullable StorageDevice myStorageDevice;

  public static final class Builder extends Device.Builder {
    private @NotNull String myNameOverride = "";
    private final @NotNull Collection<@NotNull ConnectionType> myConnectionTypes = EnumSet.noneOf(ConnectionType.class);
    private @Nullable Battery myPower;
    private @Nullable Resolution myResolution;
    private int myDensity = -1;
    private final @NotNull Collection<@NotNull String> myAbis = new ArrayList<>();
    private @Nullable StorageDevice myStorageDevice;

    public @NotNull Builder setKey(@NotNull Key key) {
      myKey = key;
      return this;
    }

    @NotNull Builder setType(@NotNull DeviceType type) {
      myType = type;
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

    @NotNull Builder setPower(@Nullable Battery power) {
      myPower = power;
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

    @NotNull Builder setStorageDevice(@Nullable StorageDevice storageDevice) {
      myStorageDevice = storageDevice;
      return this;
    }

    @Override
    public @NotNull PhysicalDevice build() {
      return new PhysicalDevice(this);
    }
  }

  private PhysicalDevice(@NotNull Builder builder) {
    super(builder);

    myNameOverride = builder.myNameOverride;
    myConnectionTypes = ImmutableSet.copyOf(builder.myConnectionTypes);
    myPower = builder.myPower;
    myResolution = builder.myResolution;
    myDensity = builder.myDensity;
    myAbis = ImmutableList.copyOf(builder.myAbis);
    myStorageDevice = builder.myStorageDevice;
  }

  @Override
  public @NotNull Icon getIcon() {
    return myType.getPhysicalIcon();
  }

  @NotNull String getNameOverride() {
    return myNameOverride;
  }

  @Override
  public boolean isOnline() {
    return !myConnectionTypes.isEmpty();
  }

  @NotNull Collection<@NotNull ConnectionType> getConnectionTypes() {
    return myConnectionTypes;
  }

  @Nullable Battery getPower() {
    return myPower;
  }

  @Nullable Resolution getResolution() {
    return myResolution;
  }

  @Nullable Resolution getDp() {
    return getDp(myDensity, myResolution);
  }

  @NotNull Collection<@NotNull String> getAbis() {
    return myAbis;
  }

  @Nullable StorageDevice getStorageDevice() {
    return myStorageDevice;
  }

  @Override
  public int hashCode() {
    int hashCode = myKey.hashCode();

    hashCode = 31 * hashCode + myType.hashCode();
    hashCode = 31 * hashCode + myName.hashCode();
    hashCode = 31 * hashCode + myNameOverride.hashCode();
    hashCode = 31 * hashCode + myTarget.hashCode();
    hashCode = 31 * hashCode + myApi.hashCode();
    hashCode = 31 * hashCode + myConnectionTypes.hashCode();
    hashCode = 31 * hashCode + Objects.hashCode(myPower);
    hashCode = 31 * hashCode + Objects.hashCode(myResolution);
    hashCode = 31 * hashCode + myDensity;
    hashCode = 31 * hashCode + myAbis.hashCode();
    hashCode = 31 * hashCode + Objects.hashCode(myStorageDevice);

    return hashCode;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof PhysicalDevice)) {
      return false;
    }

    PhysicalDevice device = (PhysicalDevice)object;

    return myKey.equals(device.myKey) &&
           myType.equals(device.myType) &&
           myName.equals(device.myName) &&
           myNameOverride.equals(device.myNameOverride) &&
           myTarget.equals(device.myTarget) &&
           myApi.equals(device.myApi) &&
           myConnectionTypes.equals(device.myConnectionTypes) &&
           Objects.equals(myPower, device.myPower) &&
           Objects.equals(myResolution, device.myResolution) &&
           myDensity == device.myDensity &&
           myAbis.equals(device.myAbis) &&
           Objects.equals(myStorageDevice, device.myStorageDevice);
  }
}
