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

import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.builder.model.SyncIssue;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.issues.SyncIssueData;
import com.android.tools.idea.gradle.project.sync.issues.SyncIssues;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.mockito.Mock;

/**
 * Tests for {@link JavaModuleSetup}.
 */
public class JavaModuleSetupTest extends PlatformTestCase {
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
    myModuleSetup.setUpModule(myContext, myJavaModel);

    verify(mySetupStep1, times(1)).setUpModule(myContext, myJavaModel);
    verify(mySetupStep2, times(1)).setUpModule(myContext, myJavaModel);
  }
}
