/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.instantapp;

import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Dependencies;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.android.builder.model.AndroidProject.*;
import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Helper methods for checking modules in an AIA-enabled project are created correctly by a project sync.
 *
 * Note: Splits were previously called atoms, and not all the Apis have bee updated to the new terminology, hence there is some mixed
 * naming.
 */
public class AIAProjectStructureAssertions {
  /**
   * Asserts that the given module is an app module with dependencies on the given library projects and no invalid split dependencies
   */
  public static void assertModuleIsValidAIAApp(@NotNull Module module, @NotNull Collection<String> expectedDependencies)
    throws InterruptedException {
    assertModuleIsValidForAIA(module, expectedDependencies, PROJECT_TYPE_APP, false);
  }

  /**
   * Asserts that the given module is an instant app module with the given base split and dependencies on the given split projects
   */
  public static void assertModuleIsValidAIAInstantApp(@NotNull Module module,
                                                      @NotNull Collection<String> expectedDependencies)
    throws InterruptedException {
    // At the moment we have no way to actually get InstantApp module dependencies (they aren't reported in the model) so we can't check
    // them. Hence expectedDependencies is replaced with ImmutableList.of() below
    assertModuleIsValidForAIA(module, ImmutableList.of(), PROJECT_TYPE_INSTANTAPP, false);
  }

  /**
   * Asserts that the given module is a split module with the given base split and dependencies on the given library projects and split
   * projects
   */
  public static void assertModuleIsValidAIAFeature(@NotNull Module module,
                                                   @NotNull Collection<String> expectedDependencies) throws InterruptedException {
    assertModuleIsValidForAIA(module, expectedDependencies, PROJECT_TYPE_FEATURE, false);
  }

  /**
   * Asserts that the given module is a base split module with dependencies on the given library projects
   */
  public static void assertModuleIsValidAIABaseFeature(@NotNull Module module,
                                                       @NotNull Collection<String> expectedDependencies) throws InterruptedException {
    assertModuleIsValidForAIA(module, expectedDependencies, PROJECT_TYPE_FEATURE, true);
  }

  private static void assertModuleIsValidForAIA(@NotNull Module module,
                                                @NotNull Collection<String> expectedDependencies,
                                                int moduleType,
                                                boolean isBaseSplit) throws InterruptedException {
    AndroidModuleModel model = AndroidModuleModel.get(module);
    assertNotNull(model);
    int projectType = model.getProjectType();
    assertEquals(moduleType, projectType);

    Dependencies dependencies = model.getMainArtifact().getDependencies();
    List<String> libraries =
      dependencies.getLibraries().stream().map(AndroidLibrary::getProject).filter(Objects::nonNull).collect(Collectors.toList());

    assertEquals(expectedDependencies.size(), libraries.size());
    assertTrue(libraries.stream().allMatch(expectedDependencies::contains));

    assertEquals(isBaseSplit, model.getAndroidProject().isBaseSplit());
  }
}
