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
package com.android.tools.idea.gradle.project.sync.setup.module;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.GradleSyncSummary;
import com.android.tools.idea.gradle.project.sync.SyncAction;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.stubs.gradle.IdeaModuleStub;
import com.android.tools.idea.gradle.stubs.gradle.IdeaProjectStub;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.plugins.gradle.model.BuildScriptClasspathModel;
import org.mockito.Mock;

import java.io.File;

import static com.android.tools.idea.gradle.util.Facets.findFacet;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.google.common.truth.Truth.assertAbout;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link GradleModuleSetup}.
 */
public class GradleModuleSetupTest extends IdeaTestCase {
  @Mock private SyncAction.ModuleModels myModuleModels;
  @Mock private BuildScriptClasspathModel myClasspathModel;
  @Mock private GradleSyncState mySyncState;

  private Module myModule;
  private IdeaModuleStub myModuleModel;
  private IdeModifiableModelsProvider myModelsProvider;
  private GradleSyncSummary mySyncSummary;
  private GradleModuleSetup myModuleSetup;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();
    IdeComponents.replaceService(project, GradleSyncState.class, mySyncState);

    String moduleName = "app";
    myModule = createModule(moduleName);

    IdeaProjectStub projectModel = new IdeaProjectStub(project.getName());
    myModuleModel = projectModel.addModule(moduleName, "task1", "task2");

    mySyncSummary = new GradleSyncSummary(project);
    when(mySyncState.getSummary()).thenReturn(mySyncSummary);

    myModelsProvider = new IdeModifiableModelsProviderImpl(project);
    myModuleSetup = new GradleModuleSetup();
  }

  public void testSetUpModule() {
    when(myModuleModels.getModule()).thenReturn(myModuleModel);
    when(myModuleModels.findModel(BuildScriptClasspathModel.class)).thenReturn(myClasspathModel);

    String gradleVersion = "2.14.1";
    when(myClasspathModel.getGradleVersion()).thenReturn(gradleVersion);

    myModuleSetup.setUpModule(myModule, myModelsProvider, myModuleModels);

    // Apply changes to verify state.
    ApplicationManager.getApplication().runWriteAction(() -> myModelsProvider.commit());

    GradleFacet facet = findFacet(myModule, myModelsProvider, GradleFacet.getFacetTypeId());
    assertNotNull(facet);

    GradleModuleModel gradleModuleModel = facet.getGradleModuleModel();
    assertNotNull(gradleModuleModel);

    assertEquals(":app", gradleModuleModel.getGradlePath());
    assertEquals(gradleVersion, gradleModuleModel.getGradleVersion());

    File buildFilePath = myModuleModel.getGradleProject().getBuildScript().getSourceFile();
    assertAbout(file()).that(gradleModuleModel.getBuildFilePath()).isEquivalentAccordingToCompareTo(buildFilePath);

    GradleVersion actualGradleVersion = mySyncSummary.getGradleVersion();
    assertNotNull(actualGradleVersion);
    assertEquals(gradleVersion, actualGradleVersion.toString());
  }
}