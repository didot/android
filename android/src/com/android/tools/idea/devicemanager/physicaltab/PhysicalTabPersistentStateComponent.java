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

import com.android.tools.idea.devicemanager.physicaltab.PhysicalTabPersistentStateComponent.PhysicalTabState;
import com.android.tools.idea.util.xmlb.InstantConverter;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import com.intellij.util.xmlb.annotations.XCollection.Style;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "deviceManagerPhysicalTab", storages = @Storage("deviceManagerPhysicalTab.xml"))
@Service
final class PhysicalTabPersistentStateComponent implements PersistentStateComponent<@NotNull PhysicalTabState> {
  private @NotNull PhysicalTabState myState;

  @VisibleForTesting
  PhysicalTabPersistentStateComponent() {
    myState = new PhysicalTabState();
  }

  static @NotNull PhysicalTabPersistentStateComponent getInstance() {
    return ServiceManager.getService(PhysicalTabPersistentStateComponent.class);
  }

  @NotNull Collection<@NotNull PhysicalDevice> get() {
    return myState.physicalDevices.stream()
      .map(PhysicalDeviceState::asPhysicalDevice)
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  void set(@NotNull Collection<@NotNull PhysicalDevice> devices) {
    myState.physicalDevices = devices.stream()
      .map(PhysicalDeviceState::new)
      .collect(Collectors.toList());
  }

  @Override
  public @NotNull PhysicalTabState getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull PhysicalTabState state) {
    myState = state;
  }

  static final class PhysicalTabState {
    @XCollection(style = Style.v2)
    private @NotNull Collection<@NotNull PhysicalDeviceState> physicalDevices = Collections.emptyList();

    @Override
    public int hashCode() {
      return physicalDevices.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object object) {
      return object instanceof PhysicalTabState && physicalDevices.equals(((PhysicalTabState)object).physicalDevices);
    }
  }

  @Tag("PhysicalDevice")
  private static final class PhysicalDeviceState {
    @OptionTag(tag = "key", nameAttribute = "")
    private @Nullable KeyState key;

    @OptionTag(tag = "lastOnlineTime", nameAttribute = "", converter = InstantConverter.class)
    private @Nullable Instant lastOnlineTime;

    @OptionTag(tag = "name", nameAttribute = "")
    private @Nullable String name;

    @OptionTag(tag = "nameOverride", nameAttribute = "")
    @SuppressWarnings({"CanBeFinal", "FieldMayBeFinal"})
    private @NotNull String nameOverride;

    @OptionTag(tag = "target", nameAttribute = "")
    private @Nullable String target;

    @OptionTag(tag = "api", nameAttribute = "")
    private @Nullable String api;

    @SuppressWarnings("unused")
    private PhysicalDeviceState() {
      nameOverride = "";
    }

    private PhysicalDeviceState(@NotNull PhysicalDevice device) {
      key = new KeyState(device.getKey());
      lastOnlineTime = device.getLastOnlineTime();
      name = device.getName();
      nameOverride = "";
      target = device.getTarget();
      api = device.getApi();
    }

    private @Nullable PhysicalDevice asPhysicalDevice() {
      if (key == null || name == null || target == null || api == null) {
        Logger.getInstance(PhysicalTabPersistentStateComponent.class).warn("key, name, target, or api are null");
        return null;
      }

      Key key = this.key.asKey();

      if (key == null) {
        Logger.getInstance(PhysicalTabPersistentStateComponent.class).warn("key is null");
        return null;
      }

      return new PhysicalDevice.Builder()
        .setKey(key)
        .setLastOnlineTime(lastOnlineTime)
        .setName(name)
        .setNameOverride(nameOverride)
        .setTarget(target)
        .setApi(api)
        .build();
    }

    @Override
    public int hashCode() {
      int hashCode = Objects.hashCode(key);

      hashCode = 31 * hashCode + Objects.hashCode(lastOnlineTime);
      hashCode = 31 * hashCode + Objects.hashCode(name);
      hashCode = 31 * hashCode + nameOverride.hashCode();
      hashCode = 31 * hashCode + Objects.hashCode(target);
      hashCode = 31 * hashCode + Objects.hashCode(api);

      return hashCode;
    }

    @Override
    public boolean equals(@Nullable Object object) {
      if (!(object instanceof PhysicalDeviceState)) {
        return false;
      }

      PhysicalDeviceState device = (PhysicalDeviceState)object;

      return Objects.equals(key, device.key) &&
             Objects.equals(lastOnlineTime, device.lastOnlineTime) &&
             Objects.equals(name, device.name) &&
             nameOverride.equals(device.nameOverride) &&
             Objects.equals(target, device.target) &&
             Objects.equals(api, device.api);
    }
  }

  @Tag("Key")
  private static final class KeyState {
    @OptionTag(tag = "type", nameAttribute = "")
    private @Nullable KeyType type;

    @OptionTag(tag = "value", nameAttribute = "")
    private @Nullable String value;

    @SuppressWarnings("unused")
    private KeyState() {
    }

    private KeyState(@NotNull Key key) {
      type = KeyType.get(key);
      value = key.toString();
    }

    private @Nullable Key asKey() {
      if (type == null || value == null) {
        Logger.getInstance(PhysicalTabPersistentStateComponent.class).warn("type or value are null");
        return null;
      }

      return type.newKey(value);
    }

    @Override
    public int hashCode() {
      return 31 * Objects.hashCode(type) + Objects.hashCode(value);
    }

    @Override
    public boolean equals(@Nullable Object object) {
      if (!(object instanceof KeyState)) {
        return false;
      }

      KeyState key = (KeyState)object;
      return Objects.equals(type, key.type) && Objects.equals(value, key.value);
    }
  }

  private enum KeyType {
    SERIAL_NUMBER {
      @Override
      @NotNull Key newKey(@NotNull String value) {
        return new SerialNumber(value);
      }
    },

    DOMAIN_NAME {
      @Override
      @NotNull Key newKey(@NotNull String value) {
        return new DomainName(value);
      }
    };

    private static @NotNull KeyType get(@NotNull Key key) {
      if (key instanceof SerialNumber) {
        return SERIAL_NUMBER;
      }

      if (key instanceof DomainName) {
        return DOMAIN_NAME;
      }

      throw new AssertionError(key);
    }

    abstract @NotNull Key newKey(@NotNull String value);
  }
}
