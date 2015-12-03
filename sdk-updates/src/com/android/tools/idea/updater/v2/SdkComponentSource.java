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
package com.android.tools.idea.updater.v2;

import com.android.repository.Revision;
import com.android.repository.api.*;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.sdklib.repositoryv2.meta.DetailsTypes;
import com.android.tools.idea.sdkv2.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.ide.externalComponents.ExternalComponentSource;
import com.intellij.ide.externalComponents.UpdatableExternalComponent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.Pair;
import com.intellij.util.concurrency.FutureResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static com.android.repository.api.RepoManager.DEFAULT_EXPIRATION_PERIOD_MS;
import static com.android.repository.api.RepoManager.RepoLoadedCallback;

/**
 * An {@link ExternalComponentSource} that retrieves information from the {@link LocalSdk} and {@link RemoteSdk} provided
 * by the Android SDK.
 */
public class SdkComponentSource implements ExternalComponentSource {

  public static String NAME = "Android SDK";

  public static final String PREVIEW_CHANNEL = "Preview Channel";
  public static final String STABLE_CHANNEL = "Stable Channel";

  private RepoManager myRepoManager;

  private void initIfNecessary(ProgressIndicator indicator) {
    if (myRepoManager == null) {
      RepoManager mgr = AndroidSdkHandler.getInstance().getSdkManager(new StudioLoggerProgressIndicator(getClass()));
      com.android.repository.api.ProgressIndicator progress;
      if (indicator != null) {
        progress = new RepoProgressIndicatorAdapter(indicator);
      }
      else {
        progress = new StudioLoggerProgressIndicator(getClass());
      }
      mgr.loadSynchronously(DEFAULT_EXPIRATION_PERIOD_MS, progress, StudioDownloader.getInstance(), StudioSettingsController.getInstance());
      myRepoManager = mgr;
    }
  }

  /**
   * Install the given new versions of components.
   *
   * @param request The components to install.
   */
  @Override
  public void installUpdates(@NotNull Collection<UpdatableExternalComponent> request) {
    final List<RemotePackage> packages = Lists.newArrayList();
    for (UpdatableExternalComponent p : request) {
      packages.add((RemotePackage)p.getKey());
    }
    new UpdateInfoDialog(true, packages).show();
  }

  /**
   * Retrieves information on updates available from the {@link RemoteSdk}.
   *
   * @param indicator      A {@code ProgressIndicator} that can be updated to show progress, or can be used to cancel the process.
   * @param updateSettings The UpdateSettings to use.
   * @return A collection of {@link UpdatablePackage}s corresponding to the currently installed Packages.
   */
  @NotNull
  @Override
  public Collection<UpdatableExternalComponent> getAvailableVersions(@Nullable ProgressIndicator indicator,
                                                                     @Nullable UpdateSettings updateSettings) {
    return getComponents(indicator, updateSettings, true);
  }

  /**
   * Retrieves information on updates installed using the {@link LocalSdk}.
   *
   * @return A collection of {@link UpdatablePackage}s corresponding to the currently installed Packages.
   */
  @NotNull
  @Override
  public Collection<UpdatableExternalComponent> getCurrentVersions() {
    return getComponents(null, null, false);
  }

  private Collection<UpdatableExternalComponent> getComponents(ProgressIndicator indicator, UpdateSettings settings, boolean remote) {
    List<UpdatableExternalComponent> result = Lists.newArrayList();
    initIfNecessary(indicator);

    Set<String> ignored = settings != null ? Sets.newHashSet(settings.getIgnoredBuildNumbers()) : ImmutableSet.<String>of();

    boolean previewChannel = settings != null && PREVIEW_CHANNEL.equals(settings.getExternalUpdateChannels().get(getName()));
    for (com.android.repository.api.UpdatablePackage p : myRepoManager.getPackages().getConsolidatedPkgs().values()) {
      if (remote) {
        if (p.hasRemote(previewChannel)) {
          RemotePackage remotePackage = p.getRemote(previewChannel);
          if (!ignored.contains(getPackageRevisionId(remotePackage))) {
            result.add(new UpdatablePackage(remotePackage));
          }
        }
      }
      else {
        if (p.hasLocal()) {
          result.add(new UpdatablePackage(p.getLocalInfo()));
        }
      }
    }
    return result;
  }

  static String getPackageRevisionId(RepoPackage p) {
    return String.format("%1$s#%2$s", p.getPath(), p.getVersion().toString());
  }

  @NotNull
  @Override
  public String getName() {
    return NAME;
  }

  @NotNull
  @Override
  public Collection<? extends Pair<String, String>> getStatuses() {
    RepoManager mgr = AndroidSdkHandler.getInstance().getSdkManager(new StudioLoggerProgressIndicator(getClass()));
    ProgressRunner runner = new StudioProgressRunner(false, true, false, "Loading SDK...", true, null);
    // Pass in null as the downloader since we aren't interested in remote packages
    mgr.loadSynchronously(DEFAULT_EXPIRATION_PERIOD_MS, new StudioLoggerProgressIndicator(getClass()), null,
                          StudioSettingsController.getInstance());
    RepositoryPackages packages = mgr.getPackages();
    Revision toolsRevision = null;
    Revision platformRevision = null;
    AndroidVersion platformVersion = null;
    for (LocalPackage info : packages.getLocalPackages().values()) {
      if (info.getTypeDetails() instanceof DetailsTypes.ToolDetailsType &&
          (toolsRevision == null || toolsRevision.compareTo(info.getVersion()) < 0)) {
        toolsRevision = info.getVersion();
      }
      if (info.getTypeDetails() instanceof DetailsTypes.PlatformDetailsType) {
        DetailsTypes.PlatformDetailsType details = (DetailsTypes.PlatformDetailsType)info.getTypeDetails();
        AndroidVersion testVersion = new AndroidVersion(details.getApiLevel(), details.getCodename());
        if (platformVersion == null || platformVersion.compareTo(testVersion) < 0) {
          platformRevision = info.getVersion();
          platformVersion = testVersion;
        }
      }
    }
    List<Pair<String, String>> result = Lists.newArrayList();
    if (toolsRevision != null) {
      result.add(Pair.create("Android SDK Tools:", toolsRevision.toString()));
    }
    if (platformVersion != null) {
      result.add(Pair.create("Android Platform Version:",
                             String.format("%1$s revision %2$s",
                                           platformVersion.getCodename() != null
                                           ? platformVersion.getCodename()
                                           : SdkVersionInfo
                                             .getAndroidName(platformVersion.getApiLevel()),
                                           platformRevision)));
    }
    return result;
  }

  @Nullable
  @Override
  public List<String> getAllChannels() {
    return ImmutableList.of(STABLE_CHANNEL, PREVIEW_CHANNEL);
  }
}
