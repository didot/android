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
package com.android.tools.idea.gradle.project.sync.setup.module.idea;

import com.android.builder.model.SyncIssue;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.issues.SyncIssueRegister;
import com.android.tools.idea.gradle.project.sync.setup.module.idea.java.CheckAndroidModuleWithoutVariantsStep;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.mockito.Mock;

import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link JavaModuleSetup}.
 */
public class JavaModuleSetupTest extends IdeaTestCase {
  @Mock private ModuleSetupContext myContext;
  @Mock private JavaModuleModel myJavaModel;
  @Mock private JavaModuleSetupStep mySetupStep1;
  @Mock private JavaModuleSetupStep mySetupStep2;

  private JavaModuleSetup myModuleSetup;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    myModuleSetup = new JavaModuleSetup(mySetupStep1, mySetupStep2);
    when(myContext.getModule()).thenReturn(myModule);
    when(myJavaModel.getSyncIssues()).thenReturn(ImmutableList.of());
  }

  public void testSetUpModule() {
    when(myJavaModel.isAndroidModuleWithoutVariants()).thenReturn(false);
    myModuleSetup.setUpModule(myContext, myJavaModel, false);

    verify(mySetupStep1, times(1)).setUpModule(myContext, myJavaModel);
    verify(mySetupStep2, times(1)).setUpModule(myContext, myJavaModel);
  }

  public void testSetUpAndroidModuleWithSyncSkipped() {
    when(mySetupStep1.invokeOnSkippedSync()).thenReturn(true);
    myModuleSetup.setUpModule(myContext, myJavaModel, true /* sync skipped */);

    // Only 'mySetupStep1' should be invoked when sync is skipped.
    verify(mySetupStep1, times(1)).setUpModule(myContext, myJavaModel);
    verify(mySetupStep2, times(0)).setUpModule(myContext, myJavaModel);
  }

  public void testSetUpAndroidModuleWithSyncNotSkipped() {
    when(mySetupStep1.invokeOnSkippedSync()).thenReturn(true);
    myModuleSetup.setUpModule(myContext, myJavaModel, false /* sync not skipped */);

    // Only 'mySetupStep1' should be invoked when sync is skipped.
    verify(mySetupStep1, times(1)).setUpModule(myContext, myJavaModel);
    verify(mySetupStep2, times(1)).setUpModule(myContext, myJavaModel);
  }

  public void testSetUpAndroidModuleWithoutVariants() {
    when(myJavaModel.isAndroidModuleWithoutVariants()).thenReturn(true);
    myModuleSetup = new JavaModuleSetup(new CheckAndroidModuleWithoutVariantsStep());

    Module module = getModule();
    // Add AndroidFacet to verify that is removed.
    createAndAddAndroidFacet(module);

    ApplicationManager.getApplication().runWriteAction(() -> {
      // Add source folders and excluded folders to verify that they are removed.
      ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();
      ContentEntry contentEntry = modifiableModel.addContentEntry("file://fakePath");
      contentEntry.addSourceFolder("file://fakePath/sourceFolder", false);
      contentEntry.addExcludeFolder("file://fakePath/excludedFolder");
      modifiableModel.commit();
    });

    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(getProject());
    myContext = new ModuleSetupContext.Factory().create(module, modelsProvider);
    myModuleSetup.setUpModule(myContext, myJavaModel, false);
    ApplicationManager.getApplication().runWriteAction(modelsProvider::commit);

    // Verify AndroidFacet was removed.
    assertNull(AndroidFacet.getInstance(module));

    // Verify source folders and excluded folders were removed.
    ContentEntry[] contentEntries = ModuleRootManager.getInstance(module).getContentEntries();
    assertThat(contentEntries).hasLength(1);

    ContentEntry contentEntry = contentEntries[0];
    assertThat(contentEntry.getSourceFolders()).isEmpty();
    assertThat(contentEntry.getExcludeFolderUrls()).isEmpty();
  }

  public void testSetUpAndroidModuleRegistersSyncIssues() {
    SyncIssue syncIssue = mock(SyncIssue.class);
    when(myJavaModel.getSyncIssues()).thenReturn(ImmutableList.of(syncIssue));

    myModuleSetup.setUpModule(myContext, myJavaModel, false /* sync not skipped */);
    SyncIssueRegister register = SyncIssueRegister.getInstance(myProject);
    register.seal();
    assertThat(register.get()).containsExactly(myModule, ImmutableList.of(syncIssue));
  }

  public void testSetUpAndroidModuleRegistersSyncIssuesSkipped() {
    SyncIssue syncIssue = mock(SyncIssue.class);
    when(myJavaModel.getSyncIssues()).thenReturn(ImmutableList.of(syncIssue));

    myModuleSetup.setUpModule(myContext, myJavaModel, true /* sync skipped */);
    SyncIssueRegister register = SyncIssueRegister.getInstance(myProject);
    register.seal();
    assertThat(register.get()).containsExactly(myModule, ImmutableList.of(syncIssue));
  }
}
