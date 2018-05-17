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
package org.jetbrains.android.uipreview;

import com.android.builder.model.AaptOptions;
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.AbstractResourceRepository;
import com.android.tools.idea.Projects;
import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.project.build.PostProjectBuildTasksExecutor;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.layoutlib.LayoutLibrary;
import com.android.tools.idea.model.TestAndroidModel;
import com.android.tools.idea.res.*;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.DummyCompileContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.TimeoutUtil;
import org.gradle.internal.impldep.aQute.bnd.service.repository.ResourceRepository;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.android.tools.idea.io.FilePaths.pathToIdeaUrl;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

public class ModuleClassLoaderTest extends AndroidTestCase {
  /**
   * Generates an empty R class file with one static field ID = "FileID"
   */
  @SuppressWarnings("SameParameterValue")
  private static void generateRClass(@NotNull String pkg, @NotNull File outputFile) throws IOException {
    File tmpDir = FileUtil.createTempDirectory("source", null);
    File tmpClass = new File(tmpDir, "R.java");
    FileUtil.writeToFile(tmpClass,
                         "package " + pkg + ";" +
                         "public class R {" +
                         "      public static final String ID = \"FileID\";" +
                         "}");

    JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    javac.run(null, System.out, System.err, tmpClass.getAbsolutePath());

    FileUtil.copy(new File(tmpDir, "R.class"), outputFile);
  }

  // Disabled. Failing in post-submit
  public void disabledTestModuleClassLoading() throws ClassNotFoundException, IOException {
    LayoutLibrary layoutLibrary = mock(LayoutLibrary.class);

    Module module = myFixture.getModule();
    File tmpDir = Files.createTempDir();
    File outputDir = new File(tmpDir, CompilerModuleExtension.PRODUCTION + "/" + module.getName() + "/test");
    assertTrue(FileUtil.createDirectory(outputDir));
    CompilerProjectExtension.getInstance(getProject()).setCompilerOutputUrl(pathToIdeaUrl(tmpDir));

    generateRClass("test", new File(outputDir, "R.class"));

    ApplicationManager.getApplication().runReadAction(() -> {
      ModuleClassLoader loader = ModuleClassLoader.get(layoutLibrary, module);
      try {
        Class<?> rClass = loader.loadClass("test.R");
        String value = (String)rClass.getDeclaredField("ID").get(null);
        assertEquals("FileID", value);
      }
      catch (ClassNotFoundException | IllegalAccessException | NoSuchFieldException e) {
        fail("Unexpected exception " + e.getLocalizedMessage());
      }
    });
  }

  /**
   * Verifies that the AAR generated R classes are given priority vs the build generated files. This is important in cases like support
   * library upgrades/downgrades. In those cases, the build generated file, will be outdated so it shouldn't be used by the ModuleClassLoader.
   * By preferring the AAR geneated versions, we make sure we are always up-to-date.
   * See <a href="http://b.android.com/229382">229382</a>
   */
  public void testAARPriority() throws Exception {
    doTestAARPriority();
  }

  public void testAARPriorityNamespaced() throws Exception {
    enableNamespacing("test");
    doTestAARPriority();
  }

  private void doTestAARPriority() throws IOException {
    LayoutLibrary layoutLibrary = mock(LayoutLibrary.class);

    Module module = myFixture.getModule();
    File tmpDir = Files.createTempDir();
    File outputDir = new File(tmpDir, CompilerModuleExtension.PRODUCTION + "/" + module.getName() + "/test");
    assertTrue(FileUtil.createDirectory(outputDir));
    CompilerProjectExtension.getInstance(getProject()).setCompilerOutputUrl(pathToIdeaUrl(tmpDir));

    generateRClass("test", new File(outputDir, "R.class"));

    ResourceRepositoryManager repositoryManager = ResourceRepositoryManager.getOrCreateInstance(module);
    ResourceNamespace namespace = repositoryManager.getNamespace();
    List<LocalResourceRepository> repositories = repositoryManager.getAppResourcesForNamespace(namespace);
    assertEquals(1, repositories.size());
    ResourceClassRegistry rClassRegistry = ResourceClassRegistry.get(module.getProject());
    rClassRegistry.addLibrary(repositories.get(0), ResourceIdManager.get(module), "test", namespace);

    ApplicationManager.getApplication().runReadAction(() -> {
      ModuleClassLoader loader = ModuleClassLoader.get(layoutLibrary, module);
      try {
        Class<?> rClass = loader.loadClass("test.R");
        rClass.getDeclaredField("ID");
        fail("Field \"ID\" is not expected");
      }
      catch (NoSuchFieldException expected) {
      }
      catch (ClassNotFoundException e) {
        fail("Unexpected exception " + e.getLocalizedMessage());
      }
    });

  }

  public void testIsSourceModified() throws IOException {
    File rootDirPath = Projects.getBaseDirPath(getProject());
    AndroidProjectStub androidProject = TestProjects.createBasicProject();
    myFacet.getConfiguration().setModel(
      new AndroidModuleModel(androidProject.getName(), rootDirPath, androidProject, "debug", new IdeDependenciesFactory()));
    myFacet.getProperties().ALLOW_USER_CONFIGURATION = false;
    assertThat(myFacet.requiresAndroidModel()).isTrue();

    File srcDir = new File(Files.createTempDir(), "src");
    File rSrc = new File(srcDir, "com/google/example/R.java");
    FileUtil.writeToFile(rSrc, "package com.google.example; public class R { public class string {} }");
    File modifiedSrc = new File(srcDir, "com/google/example/Modified.java");
    FileUtil.writeToFile(modifiedSrc, "package com.google.example; public class Modified {}");
    File notModifiedSrc = new File(srcDir, "/com/google/example/NotModified.java");
    FileUtil.writeToFile(notModifiedSrc, "package com.google.example; public class NotModified {}");

    ApplicationManager.getApplication().runWriteAction(
      () -> PsiTestUtil.addSourceRoot(myModule, VfsUtil.findFileByIoFile(srcDir, true)));

    JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    for (File src : ImmutableList.of(rSrc, modifiedSrc, notModifiedSrc)) {
      javac.run(null, null, null, src.getPath());
    }

    VirtualFile rClass = VfsUtil.findFileByIoFile(new File(rSrc.getParent(), "R.class"), true);
    assertThat(rClass).isNotNull();
    VirtualFile rStringClass = VfsUtil.findFileByIoFile(new File(rSrc.getParent(), "R$string.class"), true);
    assertThat(rStringClass).isNotNull();
    VirtualFile modifiedClass = VfsUtil.findFileByIoFile(new File(modifiedSrc.getParent(), "Modified.class"), true);
    assertThat(modifiedClass).isNotNull();
    VirtualFile notModifiedClass = VfsUtil.findFileByIoFile(new File(notModifiedSrc.getParent(), "NotModified.class"), true);
    assertThat(notModifiedClass).isNotNull();

    ModuleClassLoader loader = ModuleClassLoader.get(
      new LayoutLibrary() {
      }, myModule);
    loader.loadClassFile("com.google.example.R", rClass);
    loader.loadClassFile("com.google.example.R$string", rStringClass);
    loader.loadClassFile("com.google.example.Modified", modifiedClass);
    loader.loadClassFile("com.google.example.NotModified", notModifiedClass);

    // Wait a bit to make sure timestamp is different.
    // At least one whole second because Apple's HFS only has whole second resolution.
    TimeoutUtil.sleep(1200);

    // Always false for R classes.
    assertThat(loader.isSourceModified("com.google.example.R", null)).isFalse();
    assertThat(loader.isSourceModified("com.google.example.R$string", null)).isFalse();

    // Even if we modify them.
    FileUtil.appendToFile(rSrc, "// some comments.");
    LocalFileSystem.getInstance().refreshIoFiles(Collections.singleton(rSrc));
    assertThat(loader.isSourceModified("com.google.example.R", null)).isFalse();
    assertThat(loader.isSourceModified("com.google.example.R$string", null)).isFalse();

    // No build yet.
    FileUtil.appendToFile(modifiedSrc, "// some comments.");
    LocalFileSystem.getInstance().refreshIoFiles(Collections.singleton(modifiedSrc));
    assertThat(loader.isSourceModified("com.google.example.Modified", null)).isTrue();
    assertThat(loader.isSourceModified("com.google.example.NotModified", null)).isFalse();

    // Trigger build.
    PostProjectBuildTasksExecutor.getInstance(getProject()).onBuildCompletion(DummyCompileContext.getInstance());
    assertThat(loader.isSourceModified("com.google.example.Modified", null)).isFalse();
    assertThat(loader.isSourceModified("com.google.example.NotModified", null)).isFalse();
  }
}
