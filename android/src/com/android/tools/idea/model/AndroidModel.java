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
package com.android.tools.idea.model;

import com.android.builder.model.AaptOptions;
import com.android.builder.model.SourceProvider;
import com.android.projectmodel.DynamicResourceValue;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.databinding.DataBindingMode;
import com.android.tools.lint.detector.api.Desugaring;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A common interface for Android module models.
 */
public interface AndroidModel {
  @Nullable
  static AndroidModel get(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    return facet != null ? facet.getConfiguration().getModel() : null;
  }
  /**
   * @return the default source provider.
   * TODO: To be build-system-agnostic, simplify source provider usage.
   * {@link AndroidFacet#getMainSourceProvider()}
   */
  @Deprecated
  @NotNull
  SourceProvider getDefaultSourceProvider();

  /**
   * @return the currently active (non-test) source providers for this Android module in overlay order (meaning that later providers
   * override earlier providers when they redefine resources).
   * {@link org.jetbrains.android.facet.IdeaSourceProvider#getCurrentSourceProviders}
   */
  @Deprecated
  @NotNull
  List<SourceProvider> getActiveSourceProviders();

  /**
   * @return the currently active test source providers for this Android module in overlay order.
   * {@link org.jetbrains.android.facet.IdeaSourceProvider#getCurrentTestSourceProviders}
   */
  @Deprecated
  @NotNull
  List<SourceProvider> getTestSourceProviders();

  /**
   * @return all of the non-test source providers, including those that are not currently active.
   * {@link org.jetbrains.android.facet.IdeaSourceProvider#getAllSourceProviders(AndroidFacet)}
   */
  @Deprecated
  @NotNull
  List<SourceProvider> getAllSourceProviders();

  /**
   * @return the current application ID.
   */
  @NotNull
  String getApplicationId();

  /**
   * @return all the application IDs of artifacts this Android module could produce.
   */
  @NotNull
  Set<String> getAllApplicationIds();

  /**
   * @return whether the manifest package is overridden.
   * TODO: Potentially dedupe with computePackageName.
   */
  boolean overridesManifestPackage();

  /**
   * @return whether the application is debuggable, or {@code null} if not specified.
   */
  Boolean isDebuggable();

  /**
   * @return the minimum supported SDK version.
   * {@link AndroidModuleInfo#getMinSdkVersion()}
   */
  @Nullable
  AndroidVersion getMinSdkVersion();

  /**
   * @return the {@code minSdkVersion} that we pass to the runtime. This is normally the same as {@link #getMinSdkVersion()}, but with
   * "preview" platforms the {@code minSdkVersion}, {@code targetSdkVersion} and {@code compileSdkVersion} are all coerced to the same
   * "preview" platform value. This method should be used by launch code for example or packaging code.
   */
  @Nullable
  AndroidVersion getRuntimeMinSdkVersion();

  /**
   * @return the target SDK version.
   * {@link AndroidModuleInfo#getTargetSdkVersion()}
   */
  @Nullable
  AndroidVersion getTargetSdkVersion();

  /**
   * @return the internal version number of the Android application.
   */
  @Nullable
  Integer getVersionCode();

  /**
   * Indicates whether the given file or directory is generated.
   *
   * @param file the file or directory.
   * @return {@code true} if the given file or directory is generated; {@code false} otherwise.
   */
  boolean isGenerated(@NotNull VirtualFile file);

  /**
   * @return Whether data binding is enabled for this model and whether it is support or X versions.
   */
  @NotNull
  DataBindingMode getDataBindingMode();

  /**
   * @return A provider for finding .class output files and external .jars.
   */
  @NotNull
  ClassJarProvider getClassJarProvider();

  /**
   * @return Whether the class specified by fqcn is out of date and needs to be rebuilt.
   */
  boolean isClassFileOutOfDate(@NotNull Module module, @NotNull String fqcn, @NotNull VirtualFile classFile);

  @NotNull
  AaptOptions.Namespacing getNamespacing();

  /** @return the set of desugaring capabilities of the build system in use. */
  @NotNull
  Set<Desugaring> getDesugaring();

  /**
   * Returns the set of build-system-provided resource values and overrides.
   */
  @NotNull
  default Map<String, DynamicResourceValue> getResValues() {
    return Collections.emptyMap();
  }
}
