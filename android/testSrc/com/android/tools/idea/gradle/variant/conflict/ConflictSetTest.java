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
package com.android.tools.idea.gradle.variant.conflict;

import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.stubs.android.*;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

import java.io.File;
import java.util.List;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_APP;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_LIBRARY;
import static com.android.tools.idea.Projects.getBaseDirPath;

/**
 * Tests for {@link ConflictSet}.
 */
public class ConflictSetTest extends IdeaTestCase {
  private AndroidProjectStub myAppModel;
  private VariantStub myAppDebugVariant;

  private Module myLibModule;
  private String myLibGradlePath;
  private AndroidProjectStub myLibModel;
  private VariantStub myLibDebugVariant;
  private IdeDependenciesFactory myDependenciesFactory;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myLibModule = createModule("lib");
    myLibGradlePath = ":lib";
    myDependenciesFactory = new IdeDependenciesFactory();
  }

  @Override
  protected void tearDown() throws Exception {
    myLibModule = null;
    super.tearDown();
  }

  public void testFindSelectionConflictsWithoutConflict() {
    setUpModels();
    setUpDependencyOnLibrary("debug");
    setUpModules();

    List<Conflict> conflicts = ConflictSet.findConflicts(myProject).getSelectionConflicts();
    assertTrue(conflicts.isEmpty());
  }

  public void testFindSelectionConflictsWithoutEmptyVariantDependency() {
    setUpModels();
    setUpDependencyOnLibrary("");
    setUpModules();

    List<Conflict> conflicts = ConflictSet.findConflicts(myProject).getSelectionConflicts();
    assertTrue(conflicts.isEmpty());
  }

  public void testFindSelectionConflictsWithoutNullVariantDependency() {
    setUpModels();
    setUpDependencyOnLibrary(null);
    setUpModules();

    List<Conflict> conflicts = ConflictSet.findConflicts(myProject).getSelectionConflicts();
    assertTrue(conflicts.isEmpty());
  }

  public void testFindSelectionConflictsWithConflict() {
    setUpModels();
    setUpDependencyOnLibrary("release");
    setUpModules();

    List<Conflict> conflicts = ConflictSet.findConflicts(myProject).getSelectionConflicts();
    assertEquals(1, conflicts.size());

    Conflict conflict = conflicts.get(0);
    assertSame(myLibModule, conflict.getSource());
    assertSame("debug", conflict.getSelectedVariant());

    List<Conflict.AffectedModule> affectedModules = conflict.getAffectedModules();
    assertEquals(1, affectedModules.size());

    Conflict.AffectedModule affectedModule = affectedModules.get(0);
    assertSame(myModule, affectedModule.getTarget());
    assertSame("release", affectedModule.getExpectedVariant());
  }

  private void setUpModels() {
    myAppModel = new AndroidProjectStub("app");
    myAppDebugVariant = myAppModel.addVariant("debug");
    myLibModel = new AndroidProjectStub("lib");
    myLibModel.setProjectType(PROJECT_TYPE_LIBRARY);
    myLibDebugVariant = myLibModel.addVariant("debug");
  }

  private void setUpModules() {
    ApplicationManager.getApplication().runWriteAction(() -> {
      setUpMainModuleAsApp();
      setUpLibModule();
      setUpModuleDependencies();
    });
  }

  private void setUpMainModuleAsApp() {
    FacetManager facetManager = FacetManager.getInstance(myModule);
    ModifiableFacetModel facetModel = facetManager.createModifiableModel();
    try {
      AndroidFacet facet = createFacet(facetManager, PROJECT_TYPE_APP);

      File rootDirPath = getBaseDirPath(myProject);
      AndroidModuleModel model =
        new AndroidModuleModel(myModule.getName(), rootDirPath, myAppModel, myAppDebugVariant.getName(), myDependenciesFactory);
      facet.getConfiguration().setModel(model);
      facetModel.addFacet(facet);
    }
    finally {
      facetModel.commit();
    }
  }

  private void setUpLibModule() {
    FacetManager facetManager = FacetManager.getInstance(myLibModule);
    ModifiableFacetModel facetModel = facetManager.createModifiableModel();
    try {
      AndroidFacet androidFacet = createFacet(facetManager, PROJECT_TYPE_LIBRARY);

      File moduleFilePath = new File(myLibModule.getModuleFilePath());
      AndroidModuleModel model =
        new AndroidModuleModel(myModule.getName(), moduleFilePath.getParentFile(), myLibModel, myLibDebugVariant.getName(),
                               myDependenciesFactory);
      androidFacet.getConfiguration().setModel(model);

      facetModel.addFacet(androidFacet);

      GradleFacet gradleFacet = facetManager.createFacet(GradleFacet.getFacetType(), GradleFacet.getFacetName(), null);
      gradleFacet.getConfiguration().GRADLE_PROJECT_PATH = myLibGradlePath;
      facetModel.addFacet(gradleFacet);
    }
    finally {
      facetModel.commit();
    }
  }

  @NotNull
  private static AndroidFacet createFacet(@NotNull FacetManager facetManager, int projectType) {
    AndroidFacet facet = facetManager.createFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME, null);
    JpsAndroidModuleProperties facetState = facet.getProperties();
    facetState.ALLOW_USER_CONFIGURATION = false;
    facetState.PROJECT_TYPE = projectType;
    return facet;
  }

  private void setUpModuleDependencies() {
    // Make module depend on myLibModule.
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(myModule);
    ModifiableRootModel rootModel = moduleRootManager.getModifiableModel();
    try {
      rootModel.addModuleOrderEntry(myLibModule);
    }
    finally {
      rootModel.commit();
    }
  }

  private void setUpDependencyOnLibrary(@Nullable String projectVariant) {
    AndroidArtifactStub mainArtifact = myAppDebugVariant.getMainArtifact();
    DependenciesStub dependencies = mainArtifact.getDependencies();
    File jarFile = new File(myProject.getBasePath(), "file.jar");
    AndroidLibraryStub lib = new AndroidLibraryStub(jarFile, jarFile, myLibGradlePath, projectVariant);
    dependencies.addLibrary(lib);
  }
}
