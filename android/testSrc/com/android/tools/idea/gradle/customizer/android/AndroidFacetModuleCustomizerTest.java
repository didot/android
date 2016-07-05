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
package com.android.tools.idea.gradle.customizer.android;

import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.stubs.android.VariantStub;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

import java.io.File;

import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;

/**
 * Tests for {@link com.android.tools.idea.gradle.customizer.android.AndroidFacetModuleCustomizer}.
 */
public class AndroidFacetModuleCustomizerTest extends IdeaTestCase {
  private AndroidProjectStub myAndroidProject;
  private AndroidFacetModuleCustomizer myCustomizer;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    File rootDir = getBaseDirPath(getProject());
    myAndroidProject = TestProjects.createBasicProject(rootDir);
    myAndroidProject.setIsLibrary(true);
    myCustomizer = new AndroidFacetModuleCustomizer();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myAndroidProject != null) {
        myAndroidProject.dispose();
      }
    }
    finally {
      //noinspection ThrowFromFinallyBlock
      super.tearDown();
    }
  }

  public void testCustomizeModule() {
    File rootDir = myAndroidProject.getRootDir();
    VariantStub selectedVariant = myAndroidProject.getFirstVariant();
    assertNotNull(selectedVariant);
    String selectedVariantName = selectedVariant.getName();
    AndroidGradleModel androidModel = new AndroidGradleModel(myAndroidProject.getName(), rootDir, myAndroidProject, selectedVariantName);

    ApplicationManager.getApplication().runWriteAction(() -> {
      final IdeModifiableModelsProviderImpl modelsProvider = new IdeModifiableModelsProviderImpl(myProject);
      try {
        myCustomizer.customizeModule(myProject, myModule, modelsProvider, androidModel);
        modelsProvider.commit();
      }
      catch (Throwable t) {
        modelsProvider.dispose();
        ExceptionUtil.rethrowAllAsUnchecked(t);
      }
    });
    // Verify that AndroidFacet was added and configured.
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assertNotNull(facet);
    assertSame(androidModel, facet.getAndroidModel());

    JpsAndroidModuleProperties facetState = facet.getProperties();
    assertFalse(facetState.ALLOW_USER_CONFIGURATION);
  }
}
