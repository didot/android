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
package com.android.tools.idea.gradle;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.JavaCompileOptions;
import com.android.builder.model.Variant;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Contains Android-Gradle related state necessary for configuring an IDEA project based on a user-selected build variant.
 */
public class IdeaAndroidProject implements Serializable {
  @NotNull private final String myModuleName;
  @NotNull private final String myRootDirPath;
  @NotNull private final AndroidProject myDelegate;
  @NotNull private String mySelectedVariantName;

  /**
   * Creates a new {@link IdeaAndroidProject}.
   *
   * @param moduleName                the name of the IDEA module, created from {@code delegate}.
   * @param rootDirPath               absolute path of the root directory of the imported Android-Gradle project.
   * @param delegate                  imported Android-Gradle project.
   * @param selectedVariantName       name of the selected build variant.
   */
  public IdeaAndroidProject(@NotNull String moduleName,
                            @NotNull String rootDirPath,
                            @NotNull AndroidProject delegate,
                            @NotNull String selectedVariantName) {
    myModuleName = moduleName;
    myRootDirPath = rootDirPath;
    myDelegate = delegate;
    setSelectedVariantName(selectedVariantName);
  }

  @NotNull
  public String getModuleName() {
    return myModuleName;
  }

  /**
   * @return the absolute path of the root directory of the imported Android-Gradle project.
   */
  @NotNull
  public String getRootDirPath() {
    return myRootDirPath;
  }

  /**
   * @return the imported Android-Gradle project.
   */
  @NotNull
  public AndroidProject getDelegate() {
    return myDelegate;
  }

  /**
   * @return the selected build variant.
   */
  @NotNull
  public Variant getSelectedVariant() {
    Variant selected = myDelegate.getVariants().get(mySelectedVariantName);
    return Preconditions.checkNotNull(selected);
  }

  /**
   * Updates the name of the selected build variant. If the given name does not belong to an existing variant, this method will pick up
   * the first variant, in alphabetical order.
   *
   * @param name the new name.
   */
  public void setSelectedVariantName(@NotNull String name) {
    Collection<String> variantNames = getVariantNames();
    String newVariantName;
    if (variantNames.contains(name)) {
      newVariantName = name;
    } else {
      List<String> sorted = Lists.newArrayList(variantNames);
      Collections.sort(sorted);
      newVariantName = sorted.get(0);
    }
    mySelectedVariantName = newVariantName;
  }

  @NotNull
  public Collection<String> getVariantNames() {
    return myDelegate.getVariants().keySet();
  }

  @Nullable
  public JavaSdkVersion getJdkVersion() {
    try {
      JavaCompileOptions compileOptions = myDelegate.getJavaCompileOptions();
      String sourceCompatibility = compileOptions.getSourceCompatibility();
      return JavaSdkVersion.byDescription(sourceCompatibility);
    }
    catch (UnsupportedMethodException e) {
      // This happens when using an old but supported v0.5.+ plug-in. This code will be removed once the minimum supported version is 0.6.0.
      return null;
    }
  }
}
