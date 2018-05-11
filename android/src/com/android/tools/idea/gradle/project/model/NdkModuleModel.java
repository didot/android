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
package com.android.tools.idea.gradle.project.model;

import com.android.builder.model.*;
import com.android.ide.common.gradle.model.IdeNativeAndroidProject;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.google.common.collect.Iterables;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.*;

import static java.util.Collections.sort;

public class NdkModuleModel implements ModuleModel {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 3L;

  @NotNull private String myModuleName;
  @NotNull private File myRootDirPath;
  @NotNull private IdeNativeAndroidProject myAndroidProject;

  @NotNull private transient NdkModelFeatures myFeatures;
  @Nullable private transient GradleVersion myModelVersion;

  @NotNull private Map<String, NdkVariant> myVariantsByName = new HashMap<>();
  @NotNull private Map<String, NativeToolchain> myToolchainsByName = new HashMap<>();
  @NotNull private Map<String, NativeSettings> mySettingsByName = new HashMap<>();

  @SuppressWarnings("NullableProblems") // Set in the constructor.
  @NotNull private String mySelectedVariantName;

  @Nullable
  public static NdkModuleModel get(@NotNull Module module) {
    NdkFacet facet = NdkFacet.getInstance(module);
    return facet != null ? get(facet) : null;
  }

  @Nullable
  public static NdkModuleModel get(@NotNull NdkFacet ndkFacet) {
    NdkModuleModel androidModel = ndkFacet.getNdkModuleModel();
    if (androidModel == null) {
      return null;
    }
    return androidModel;
  }

  public NdkModuleModel(@NotNull String moduleName,
                        @NotNull File rootDirPath,
                        @NotNull IdeNativeAndroidProject androidProject) {
    myModuleName = moduleName;
    myRootDirPath = rootDirPath;
    myAndroidProject = androidProject;

    parseAndSetModelVersion();
    myFeatures = new NdkModelFeatures(myModelVersion);

    populateVariantsByName();
    populateToolchainsByName();
    populateSettingsByName();

    initializeSelectedVariant();
  }

  private void populateVariantsByName() {
    for (NativeArtifact artifact : myAndroidProject.getArtifacts()) {
      String variantName = myFeatures.isGroupNameSupported() ? artifact.getGroupName() : artifact.getName();
      NdkVariant variant = myVariantsByName.get(variantName);
      if (variant == null) {
        variant = new NdkVariant(variantName, myFeatures.isExportedHeadersSupported());
        myVariantsByName.put(variant.getName(), variant);
      }
      variant.addArtifact(artifact);
    }
    if (myVariantsByName.isEmpty()) {
      // There will mostly be at least one variant, but create a dummy variant when there are none.
      myVariantsByName.put("-----", new NdkVariant("-----", myFeatures.isExportedHeadersSupported()));
    }
  }

  private void populateToolchainsByName() {
    for (NativeToolchain toolchain : myAndroidProject.getToolChains()) {
      myToolchainsByName.put(toolchain.getName(), toolchain);
    }
  }

  private void populateSettingsByName() {
    for (NativeSettings settings : myAndroidProject.getSettings()) {
      mySettingsByName.put(settings.getName(), settings);
    }
  }

  private void initializeSelectedVariant() {
    Set<String> variantNames = myVariantsByName.keySet();
    assert !variantNames.isEmpty();

    if (variantNames.size() == 1) {
      mySelectedVariantName = Iterables.getOnlyElement(variantNames);
      return;
    }

    for (String variantName : variantNames) {
      if (variantName.equals("debug")) {
        mySelectedVariantName = variantName;
        return;
      }
    }

    List<String> sortedVariantNames = new ArrayList<>(variantNames);
    sort(sortedVariantNames);
    assert !sortedVariantNames.isEmpty();
    mySelectedVariantName = sortedVariantNames.get(0);
  }

  private void parseAndSetModelVersion() {
    myModelVersion = GradleVersion.tryParse(myAndroidProject.getModelVersion());
  }

  @Override
  @NotNull
  public String getModuleName() {
    return myModuleName;
  }

  @NotNull
  public File getRootDirPath() {
    return myRootDirPath;
  }

  @NotNull
  public IdeNativeAndroidProject getAndroidProject() {
    return myAndroidProject;
  }

  @NotNull
  public Collection<String> getVariantNames() {
    return myVariantsByName.keySet();
  }

  @NotNull
  public Collection<NdkVariant> getVariants() {
    return myVariantsByName.values();
  }

  @NotNull
  public NdkVariant getSelectedVariant() {
    NdkVariant selected = myVariantsByName.get(mySelectedVariantName);
    assert selected != null;
    return selected;
  }

  public void setSelectedVariantName(@NotNull String name) {
    Collection<String> variantNames = getVariantNames();
    if (variantNames.contains(name)) {
      mySelectedVariantName = name;
    }
    else {
      initializeSelectedVariant();
    }
  }

  @Nullable
  public NativeToolchain findToolchain(@NotNull String toolchainName) {
    return myToolchainsByName.get(toolchainName);
  }

  @Nullable
  public NativeSettings findSettings(@NotNull String settingsName) {
    return mySettingsByName.get(settingsName);
  }

  @NotNull
  public NdkModelFeatures getFeatures() {
    return myFeatures;
  }

  private void writeObject(ObjectOutputStream out) throws IOException {
    out.writeObject(myModuleName);
    out.writeObject(myRootDirPath);
    out.writeObject(myAndroidProject);
    out.writeObject(mySelectedVariantName);
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    myModuleName = (String)in.readObject();
    myRootDirPath = (File)in.readObject();
    myAndroidProject = (IdeNativeAndroidProject)in.readObject();
    mySelectedVariantName = (String)in.readObject();

    parseAndSetModelVersion();
    myFeatures = new NdkModelFeatures(myModelVersion);

    myVariantsByName = new HashMap<>();
    myToolchainsByName = new HashMap<>();
    mySettingsByName = new HashMap<>();

    populateVariantsByName();
    populateToolchainsByName();
    populateSettingsByName();
  }
}
