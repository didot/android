/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.model;

import com.android.sdklib.AndroidVersion;
import com.android.utils.concurrency.AsyncSupplier;
import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.util.concurrency.SameThreadExecutor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Key;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetScopedService;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_INSTANTAPP;
import static com.android.tools.idea.instantapp.InstantApps.findBaseFeature;

/**
 * Android information about a module, such as its application package, its minSdkVersion, and so on. This
 * is derived by querying the gradle model, or the manifest file if the model doesn't exist (not constructed, or
 * not a Gradle project).
 *
 * Note that in some cases you may need to obtain information from the merged manifest file. In such a case,
 * either obtain it from {@link AndroidModuleInfo} if the information is also available in the gradle model
 * (e.g. minSdk, targetSdk, packageName, etc), or use {@link MergedManifestManager#getSnapshot(Module)}.
 */
public class AndroidModuleInfo extends AndroidFacetScopedService {
  private static final Key<AndroidModuleInfo> KEY = Key.create(AndroidModuleInfo.class.getName());

  @NotNull
  public static AndroidModuleInfo getInstance(@NotNull AndroidFacet facet) {
    AndroidModuleInfo androidModuleInfo = facet.getUserData(KEY);
    if (androidModuleInfo == null) {
      if (facet.getConfiguration().getProjectType() == PROJECT_TYPE_INSTANTAPP) {
        // If this is an AIA app module the info about the app module is actually held in the base split module. Try to set up a
        // redirection to the AndroidModuleInfo of the base split.
        Module baseFeature = findBaseFeature(facet);
        if (baseFeature != null) {
          androidModuleInfo = getInstance(baseFeature);
        }
      }

      if (androidModuleInfo == null) {
        androidModuleInfo = new AndroidModuleInfo(facet);
      }
      facet.putUserData(KEY, androidModuleInfo);
    }
    return androidModuleInfo;
  }

  @Nullable
  public static AndroidModuleInfo getInstance(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    return facet != null ? getInstance(facet) : null;
  }

  @TestOnly
  public static void setInstanceForTest(@NotNull AndroidFacet facet, @Nullable AndroidModuleInfo androidModuleInfo) {
    facet.putUserData(KEY, androidModuleInfo);
  }

  private AndroidModuleInfo(@NotNull AndroidFacet facet) {
    super(facet);
  }

  /**
   * @return the minimum SDK version for current Android module.
   */
  public int getModuleMinApi() {
    return getMinSdkVersion().getApiLevel();
  }

  /**
   * Obtains the applicationId name for the current variant, or if not specified, from the primary manifest.
   * This method will return the applicationId from gradle, even if the manifest merger fails.
   */
  @Nullable
  public String getPackage() {
    AndroidFacet facet = getFacet();
    AndroidModel androidModel = facet.getConfiguration().getModel();
    if (androidModel != null) {
      return androidModel.getApplicationId();
    }

    // Read from the manifest: Not overridden in the configuration
    return MergedManifestManager.getSnapshot(facet).getApplicationId();
  }

  @NotNull
  private static <T> ListenableFuture<T> getFromMergedManifest(@NotNull AndroidFacet facet, @NotNull Function<MergedManifestSnapshot, T> getter) {
    AsyncSupplier<MergedManifestSnapshot> manifestSupplier = MergedManifestManager.getMergedManifestSupplier(facet.getModule());
    MergedManifestSnapshot cachedManifest = manifestSupplier.getNow();
    if (cachedManifest != null) {
      return Futures.immediateFuture(getter.apply(cachedManifest));
    }
    return Futures.transform(manifestSupplier.get(), getter, SameThreadExecutor.INSTANCE);
  }

  /**
   * Returns the minSdkVersion that we pass to the runtime. This is normally the same as
   * {@link #getMinSdkVersion()}, but with preview platforms the minSdkVersion, targetSdkVersion
   * and compileSdkVersion are all coerced to the same preview platform value. This method
   * should be used by launch code for example or packaging code.
   *
   * @deprecated To avoid blocking on merged manifest computations (especially on the EDT since
   * this freezes the UI), add a callback to the future returned by {@link #getRuntimeMinSdkVersion()}
   * instead.
   */
  @Deprecated
  @NotNull
  public AndroidVersion getRuntimeMinSdkVersionSynchronously() {
    AndroidFacet facet = getFacet();
    AndroidModel androidModel = facet.getConfiguration().getModel();
    if (androidModel != null) {
      AndroidVersion minSdkVersion = androidModel.getRuntimeMinSdkVersion();
      if (minSdkVersion != null) {
        return minSdkVersion;
      }
      // Else: not specified in gradle files; fall back to manifest
    }
    return MergedManifestManager.getSnapshot(facet).getMinSdkVersion();
  }

  /**
   * Returns the minSdkVersion that we pass to the runtime. This is normally the same as
   * {@link #getMinSdkVersion()}, but with preview platforms the minSdkVersion, targetSdkVersion
   * and compileSdkVersion are all coerced to the same preview platform value. This method
   * should be used by launch code for example or packaging code.
   */
  @NotNull
  public ListenableFuture<AndroidVersion> getRuntimeMinSdkVersion() {
    AndroidFacet facet = getFacet();
    AndroidModel androidModel = facet.getConfiguration().getModel();
    if (androidModel != null) {
      AndroidVersion minSdkVersion = androidModel.getRuntimeMinSdkVersion();
      if (minSdkVersion != null) {
        return Futures.immediateFuture(minSdkVersion);
      }
      // Else: not specified in gradle files; fall back to manifest
    }
    return getFromMergedManifest(facet, MergedManifestSnapshot::getMinSdkVersion);
  }


  @NotNull
  public AndroidVersion getMinSdkVersion() {
    AndroidFacet facet = getFacet();
    AndroidModel androidModel = facet.getConfiguration().getModel();
    if (androidModel != null) {
      AndroidVersion minSdkVersion = androidModel.getMinSdkVersion();
      if (minSdkVersion != null) {
        return minSdkVersion;
      }
      // Else: not specified in gradle files; fall back to manifest
    }

    return MergedManifestManager.getSnapshot(facet).getMinSdkVersion();
  }

  @NotNull
  public AndroidVersion getTargetSdkVersion() {
    AndroidFacet facet = getFacet();
    AndroidModel androidModel = facet.getConfiguration().getModel();
    if (androidModel != null) {
      AndroidVersion targetSdkVersion = androidModel.getTargetSdkVersion();
      if (targetSdkVersion != null) {
        return targetSdkVersion;
      }
      // Else: not specified in gradle files; fall back to manifest
    }

    return MergedManifestManager.getSnapshot(facet).getTargetSdkVersion();
  }

  @Nullable
  public AndroidVersion getBuildSdkVersion() {
    // TODO: Get this from the model! For now, we take advantage of the fact that
    // the model should have synced the right type of Android SDK to the IntelliJ facet.
    AndroidPlatform platform = AndroidPlatform.getInstance(getModule());
    if (platform != null) {
      return platform.getApiVersion();
    }

    return null;
  }

  /**
   * Returns whether the application is debuggable. For Gradle projects, this is a boolean value.
   * For non Gradle projects, this returns a boolean value if the flag is set, or null if the flag unspecified in the manifest.
   */
  @Nullable
  public Boolean isDebuggable() {
    AndroidFacet facet = getFacet();
    AndroidModel androidModel = facet.getConfiguration().getModel();
    if (androidModel != null) {
      Boolean debuggable = androidModel.isDebuggable();
      if (debuggable != null) {
        return debuggable;
      }
    }

    return MergedManifestManager.getSnapshot(facet).getApplicationDebuggable();
  }

  @NotNull
  public static AndroidVersion getTargetSdkVersion(@Nullable Module module) {
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        AndroidModuleInfo moduleInfo = getInstance(facet.getModule());
        if (moduleInfo != null) {
          return moduleInfo.getTargetSdkVersion();
        }
      }
    }

    return AndroidVersion.DEFAULT;
  }

  @NotNull
  public static AndroidVersion getMinSdkVersion(@Nullable Module module) {
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        AndroidModuleInfo moduleInfo = getInstance(facet.getModule());
        if (moduleInfo != null) {
          return moduleInfo.getMinSdkVersion();
        }
      }
    }

    return AndroidVersion.DEFAULT;
  }

  @Override
  protected void onServiceDisposal(@NotNull AndroidFacet facet) {
    facet.putUserData(KEY, null);
  }
}
