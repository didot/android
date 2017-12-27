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
package com.android.tools.idea.gradle.structure.model.android;

import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static com.android.builder.model.AndroidProject.*;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link PsAndroidArtifact}.
 */
public class PsAndroidArtifactTest {

  private List<PsProductFlavor> myProductFlavors;
  private PsBuildType myBuildType;
  private PsVariant myVariant;
  private PsProductFlavor myFlavor1;
  private PsProductFlavor myFlavor2;

  @Before
  public void setUp() {
    myProductFlavors = Lists.newArrayList();

    myFlavor1 = mock(PsProductFlavor.class);
    when(myFlavor1.getName()).thenReturn("flavor1");

    myFlavor2 = mock(PsProductFlavor.class);
    when(myFlavor2.getName()).thenReturn("flavor2");

    myBuildType = mock(PsBuildType.class);
    when(myBuildType.getName()).thenReturn("debug");

    myVariant = new PsVariant(mock(PsAndroidModule.class), "", "", Collections.emptyList(), null) {
      @Override
      public void forEachProductFlavor(@NotNull Consumer<PsProductFlavor> consumer) {
        myProductFlavors.forEach(consumer);
      }

      @Override
      @NotNull
      public PsBuildType getBuildType() {
        return myBuildType;
      }
    };
  }

  @Test
  public void getPossibleConfigurationNamesWithMainArtifact() {
    PsAndroidArtifact artifact = new PsAndroidArtifact(myVariant, ARTIFACT_MAIN, null);
    List<String> configurationNames = artifact.getPossibleConfigurationNames();
    assertThat(configurationNames).containsExactly("compile", "debugCompile",
                                                   "api", "debugApi",
                                                   "implementation", "debugImplementation");

    myProductFlavors.add(myFlavor1);
    configurationNames = artifact.getPossibleConfigurationNames();
    assertThat(configurationNames).containsExactly("compile", "debugCompile", "flavor1Compile",
                                                   "api", "debugApi", "flavor1Api",
                                                   "implementation", "debugImplementation", "flavor1Implementation");

    myProductFlavors.add(myFlavor2);
    configurationNames = artifact.getPossibleConfigurationNames();
    assertThat(configurationNames).containsExactly("compile", "debugCompile", "flavor1Compile", "flavor2Compile",
                                                   "api", "debugApi", "flavor1Api", "flavor2Api",
                                                   "implementation", "debugImplementation", "flavor1Implementation", "flavor2Implementation");
  }

  @Test
  public void getPossibleConfigurationNamesWitTestArtifact() {
    PsAndroidArtifact artifact = new PsAndroidArtifact(myVariant, ARTIFACT_UNIT_TEST, null);
    List<String> configurationNames = artifact.getPossibleConfigurationNames();
    assertThat(configurationNames).containsExactly("testCompile", "testDebugCompile",
                                                   "testApi", "testDebugApi",
                                                   "testImplementation", "testDebugImplementation");

    myProductFlavors.add(myFlavor1);
    configurationNames = artifact.getPossibleConfigurationNames();
    assertThat(configurationNames).containsExactly("testCompile", "testDebugCompile", "testFlavor1Compile",
                                                   "testApi", "testDebugApi", "testFlavor1Api",
                                                   "testImplementation", "testDebugImplementation", "testFlavor1Implementation");

    myProductFlavors.add(myFlavor2);
    configurationNames = artifact.getPossibleConfigurationNames();
    assertThat(configurationNames).containsExactly("testCompile", "testDebugCompile", "testFlavor1Compile", "testFlavor2Compile",
                                                   "testApi", "testDebugApi", "testFlavor1Api", "testFlavor2Api",
                                                   "testImplementation", "testDebugImplementation", "testFlavor1Implementation", "testFlavor2Implementation");
  }

  @Test
  public void getPossibleConfigurationNamesWitAndroidTestArtifact() {
    PsAndroidArtifact artifact = new PsAndroidArtifact(myVariant, ARTIFACT_ANDROID_TEST, null);
    List<String> configurationNames = artifact.getPossibleConfigurationNames();
    assertThat(configurationNames).containsExactly("androidTestCompile",
                                                   "androidTestApi",
                                                   "androidTestImplementation");

    myProductFlavors.add(myFlavor1);
    configurationNames = artifact.getPossibleConfigurationNames();
    assertThat(configurationNames).containsExactly("androidTestCompile", "androidTestFlavor1Compile",
                                                   "androidTestApi", "androidTestFlavor1Api",
                                                   "androidTestImplementation", "androidTestFlavor1Implementation");

    myProductFlavors.add(myFlavor2);
    configurationNames = artifact.getPossibleConfigurationNames();
    assertThat(configurationNames).containsExactly("androidTestCompile", "androidTestFlavor1Compile", "androidTestFlavor2Compile",
                                                   "androidTestApi", "androidTestFlavor1Api", "androidTestFlavor2Api",
                                                   "androidTestImplementation", "androidTestFlavor1Implementation", "androidTestFlavor2Implementation");
  }
}