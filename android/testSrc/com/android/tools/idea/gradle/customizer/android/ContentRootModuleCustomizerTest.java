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

import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.ContentRootSourcePaths;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static com.android.tools.idea.gradle.TestProjects.createBasicProject;
import static com.intellij.openapi.vfs.VfsUtilCore.urlToPath;
import static com.intellij.util.ExceptionUtil.rethrowAllAsUnchecked;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static java.util.Collections.sort;

/**
 * Tests for {@link ContentRootModuleCustomizer}.
 */
public class ContentRootModuleCustomizerTest extends IdeaTestCase {
  private AndroidProjectStub myAndroidProject;
  private AndroidGradleModel myAndroidModel;

  private ContentRootModuleCustomizer myCustomizer;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    String basePath = myProject.getBasePath();
    assertNotNull(basePath);
    File baseDir = new File(basePath);
    myAndroidProject = createBasicProject(baseDir, myProject.getName());

    Collection<Variant> variants = myAndroidProject.getVariants();
    Variant selectedVariant = getFirstItem(variants);
    assertNotNull(selectedVariant);
    String variantName = selectedVariant.getName();
    myAndroidModel = new AndroidGradleModel(myAndroidProject.getName(), baseDir, myAndroidProject, variantName);

    addContentEntry();
    myCustomizer = new ContentRootModuleCustomizer();
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

  private void addContentEntry() {
    VirtualFile moduleFile = myModule.getModuleFile();
    assertNotNull(moduleFile);
    VirtualFile moduleDir = moduleFile.getParent();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(myModule);
      ModifiableRootModel model = moduleRootManager.getModifiableModel();
      model.addContentEntry(moduleDir);
      model.commit();
    });
  }

  public void testCustomizeModule() throws Exception {
    IdeModifiableModelsProviderImpl modelsProvider = new IdeModifiableModelsProviderImpl(myProject);
    try {
      myCustomizer.customizeModule(myProject, myModule, modelsProvider, myAndroidModel);

      ApplicationManager.getApplication().runWriteAction(modelsProvider::commit);
    }
    catch (Throwable t) {
      modelsProvider.dispose();
      rethrowAllAsUnchecked(t);
    }

    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(myModule);
    ContentEntry contentEntry = moduleRootManager.getContentEntries()[0];

    SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
    List<String> sourcePaths = Lists.newArrayListWithExpectedSize(sourceFolders.length);

    for (SourceFolder folder : sourceFolders) {
      if (!folder.isTestSource()) {
        String path = urlToPath(folder.getUrl());
        sourcePaths.add(path);
      }
    }

    ContentRootSourcePaths expectedPaths = new ContentRootSourcePaths();
    expectedPaths.storeExpectedSourcePaths(myAndroidProject);

    List<String> allExpectedPaths = Lists.newArrayList();
    allExpectedPaths.addAll(expectedPaths.getPaths(ExternalSystemSourceType.SOURCE));
    allExpectedPaths.addAll(expectedPaths.getPaths(ExternalSystemSourceType.SOURCE_GENERATED));
    allExpectedPaths.addAll(expectedPaths.getPaths(ExternalSystemSourceType.RESOURCE));
    sort(allExpectedPaths);

    sort(sourcePaths);

    assertEquals(allExpectedPaths, sourcePaths);
  }
}
