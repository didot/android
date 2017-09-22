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

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.ng.GradleModuleModels;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link AndroidModuleSetup}.
 */
public class AndroidModuleSetupTest {
  @Mock private Module myModule;
  @Mock private IdeModifiableModelsProvider myModelsProvider;
  @Mock private AndroidModuleModel myAndroidModel;
  @Mock private GradleModuleModels myModuleModels;
  @Mock private ProgressIndicator myProgressIndicator;
  @Mock private AndroidModuleSetupStep mySetupStep1;
  @Mock private AndroidModuleSetupStep mySetupStep2;

  private AndroidModuleSetup myModuleSetup;

  @Before
  public void setUp() throws Exception {
    initMocks(this);
    myModuleSetup = new AndroidModuleSetup(mySetupStep1, mySetupStep2);
  }

  @Test
  public void setUpAndroidModuleWithProgressIndicator() {
    myModuleSetup.setUpModule(myModule, myModelsProvider, myAndroidModel, myModuleModels, myProgressIndicator, false);

    verify(mySetupStep1, times(1)).setUpModule(myModule, myModelsProvider, myAndroidModel, myModuleModels, myProgressIndicator);
    verify(mySetupStep2, times(1)).setUpModule(myModule, myModelsProvider, myAndroidModel, myModuleModels, myProgressIndicator);
  }

  @Test
  public void setUpAndroidModuleWithoutProgressIndicator() {
    myModuleSetup.setUpModule(myModule, myModelsProvider, myAndroidModel, myModuleModels, null, false);

    verify(mySetupStep1, times(1)).setUpModule(myModule, myModelsProvider, myAndroidModel, myModuleModels, null);
    verify(mySetupStep2, times(1)).setUpModule(myModule, myModelsProvider, myAndroidModel, myModuleModels, null);
  }

  @Test
  public void setUpAndroidModuleWithSyncSkipped() {
    when(mySetupStep1.invokeOnSkippedSync()).thenReturn(true);

    myModuleSetup.setUpModule(myModule, myModelsProvider, myAndroidModel, myModuleModels, null, true /* sync skipped */);

    // Only 'mySetupStep1' should be invoked when sync is skipped.
    verify(mySetupStep1, times(1)).setUpModule(myModule, myModelsProvider, myAndroidModel, myModuleModels, null);
    verify(mySetupStep2, never()).setUpModule(myModule, myModelsProvider, myAndroidModel, myModuleModels, null);
  }

  @Test
  public void setUpAndroidModuleWithSyncNotSkipped() {
    when(mySetupStep1.invokeOnSkippedSync()).thenReturn(true);

    myModuleSetup.setUpModule(myModule, myModelsProvider, myAndroidModel, myModuleModels, null, false /* sync not skipped */);

    // Only 'mySetupStep1' should be invoked when sync is skipped.
    verify(mySetupStep1, times(1)).setUpModule(myModule, myModelsProvider, myAndroidModel, myModuleModels, null);
    verify(mySetupStep2, times(1)).setUpModule(myModule, myModelsProvider, myAndroidModel, myModuleModels, null);
  }
}