/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome;

import com.android.ide.common.repository.SdkMavenRepository;
import com.android.sdklib.SdkManager;
import com.android.sdklib.devices.Storage;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.remote.RemotePkgInfo;
import com.android.tools.idea.wizard.ScopedStateStore;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Android SDK installable component.
 */
public final class AndroidSdk extends InstallableComponent {
  public static final long SIZE = 265 * Storage.Unit.MiB.getNumberOfBytes();

  public AndroidSdk(@NotNull ScopedStateStore store) {
    super(store, "Android SDK", SIZE, "The collection of Android platform APIs, " +
                               "tools and utilities that enables you to debug, " +
                               "profile, and compile your apps.\n\n" +
                               "The setup wizard will update your current Android SDK " +
                               "installation (if necessary) or install a new version.");
  }

  private static Collection<IPkgDesc> createAll(PkgDesc.Builder... builders) {
    ArrayList<IPkgDesc> list = Lists.newArrayListWithCapacity(builders.length + SdkMavenRepository.values().length);
    for (PkgDesc.Builder builder : builders) {
      list.add(builder.create());
    }
    return list;
  }

  /**
   * Find latest build tools revision with compatible major version number.
   */
  @NotNull
  private static FullRevision getLatestCompatibleBuildToolsRevision(@Nullable Multimap<PkgType, RemotePkgInfo> packages) {
    FullRevision revision = FirstRunWizardDefaults.MIN_BUILD_TOOLS_REVSION;
    if (packages != null) {
      Collection<RemotePkgInfo> tools = packages.get(PkgType.PKG_BUILD_TOOLS);
      for (RemotePkgInfo tool : tools) {
        FullRevision fullRevision = tool.getDesc().getFullRevision();
        if (fullRevision != null && fullRevision.getMajor() == FirstRunWizardDefaults.MIN_BUILD_TOOLS_REVSION.getMajor() &&
            fullRevision.compareTo(revision) > 0) {
          revision = fullRevision;
        }
      }
    }
    return revision;
  }

  @NotNull
  @Override
  public Collection<IPkgDesc> getRequiredSdkPackages(@Nullable Multimap<PkgType, RemotePkgInfo> remotePackages) {
    PkgDesc.Builder androidSdkTools = PkgDesc.Builder.newTool(FullRevision.NOT_SPECIFIED, FullRevision.NOT_SPECIFIED);
    PkgDesc.Builder androidSdkPlatformTools = PkgDesc.Builder.newPlatformTool(FullRevision.NOT_SPECIFIED);
    PkgDesc.Builder androidSdkBuildTools = PkgDesc.Builder.newBuildTool(getLatestCompatibleBuildToolsRevision(remotePackages));
    Collection<IPkgDesc> packages =
      createAll(androidSdkTools, androidSdkPlatformTools, androidSdkBuildTools);

    for (SdkMavenRepository repository : SdkMavenRepository.values()) {
      packages.add(repository.getPackageDescription());
    }
    return packages;
  }

  @Override
  public void configure(@NotNull InstallContext installContext, @NotNull File sdk) {
    // Nothing to do, having components installed is enough
  }

  @Override
  protected boolean isOptionalForSdkLocation(@Nullable SdkManager manager) {
    return false;
  }
}
