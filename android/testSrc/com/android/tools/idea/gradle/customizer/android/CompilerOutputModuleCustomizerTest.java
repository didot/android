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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.testFramework.IdeaTestCase;

import java.io.File;

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.toCanonicalPath;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.openapi.vfs.VfsUtilCore.pathToUrl;
import static com.intellij.util.ExceptionUtil.rethrowAllAsUnchecked;

/**
 * Tests for {@link CompilerOutputModuleCustomizer}.
 */
public class CompilerOutputModuleCustomizerTest extends IdeaTestCase {
  private AndroidProjectStub androidProject;
  private CompilerOutputModuleCustomizer customizer;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    androidProject = TestProjects.createBasicProject();
    customizer = new CompilerOutputModuleCustomizer();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (androidProject != null) {
        androidProject.dispose();
      }
    }
    finally {
      //noinspection ThrowFromFinallyBlock
      super.tearDown();
    }
  }

  public void testCustomizeModule() {
    File rootDir = androidProject.getRootDir();
    AndroidGradleModel androidModel = new AndroidGradleModel(myModule.getName(), rootDir, androidProject, "debug");
    String compilerOutputPath = "";
    final IdeModifiableModelsProviderImpl modelsProvider = new IdeModifiableModelsProviderImpl(myProject);
    try {
      customizer.customizeModule(myProject, myModule, modelsProvider, androidModel);
      ModifiableRootModel rootModel = modelsProvider.getModifiableRootModel(myModule);
      CompilerModuleExtension compilerSettings = rootModel.getModuleExtension(CompilerModuleExtension.class);
      compilerOutputPath = compilerSettings.getCompilerOutputUrl();

      ApplicationManager.getApplication().runWriteAction(modelsProvider::commit);
    }
    catch (Throwable t) {
      modelsProvider.dispose();
      rethrowAllAsUnchecked(t);
    }

    File classesFolder = androidModel.getSelectedVariant().getMainArtifact().getClassesFolder();
    String path = toSystemIndependentName(classesFolder.getPath());
    String expected = pathToUrl(toCanonicalPath(path));
    assertEquals(expected, compilerOutputPath);
  }
}
