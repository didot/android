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
package com.android.tools.idea.gradle.parser;

import com.android.SdkConstants;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class GradleBuildFileTest extends IdeaTestCase {
  private Document myDocument;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myDocument = null;
  }

  public void testGetTopLevelValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    assertEquals("17.0.0", file.getValue(BuildFileKey.BUILD_TOOLS_VERSION));
  }

  public void testNestedValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    GrStatementOwner closure = file.getClosure("android/defaultConfig");
    assertEquals(1, file.getValue(closure, BuildFileKey.TARGET_SDK_VERSION));
  }

  public void testCanParseSimpleValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    assertTrue(file.canParseValue(BuildFileKey.BUILD_TOOLS_VERSION));
  }

  public void testCantParseComplexValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    GrStatementOwner closure = file.getClosure("android/defaultConfig");
    assertFalse(file.canParseValue(closure, BuildFileKey.MIN_SDK_VERSION));
  }

  public void testSetTopLevelValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.BUILD_TOOLS_VERSION, "18.0.0");
      }
    });
    String expected = getSimpleTestFile().replaceAll("17.0.0", "18.0.0");
    assertContents(file, expected);
  }

  public void testSetNestedValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        GrStatementOwner closure = file.getClosure("android/defaultConfig");
        file.setValue(closure, BuildFileKey.TARGET_SDK_VERSION, 2);
      }
    });
    String expected = getSimpleTestFile().replaceAll("targetSdkVersion 1", "targetSdkVersion 2");
    assertContents(file, expected);
  }

  public void testSetStringValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.BUILD_TOOLS_VERSION, "99.0.0");
      }
    });
    String expected = getSimpleTestFile().replaceAll("buildToolsVersion '17.0.0'", "buildToolsVersion '99.0.0'");
    assertContents(file, expected);
    assertEquals("99.0.0", file.getValue(BuildFileKey.BUILD_TOOLS_VERSION));
  }

  public void testSetIntegerValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.COMPILE_SDK_VERSION, 99);
      }
    });
    String expected = getSimpleTestFile().replaceAll("compileSdkVersion 17", "compileSdkVersion 99");
    assertContents(file, expected);
    assertEquals(99, file.getValue(BuildFileKey.COMPILE_SDK_VERSION));
  }

  public void testSetBooleanValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    final GrStatementOwner closure = file.getClosure("android/buildTypes/debug");
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        file.setValue(closure, BuildFileKey.DEBUGGABLE, false);
      }
    });
    String expected = getSimpleTestFile().replaceAll("debuggable true", "debuggable false");
    assertContents(file, expected);
    assertEquals(false, file.getValue(closure, BuildFileKey.DEBUGGABLE));
  }

  public void testSetFileValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    final GrStatementOwner closure = file.getClosure("android/signingConfigs/debug");
    final File replacementFile = new File("foo.keystore");
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        file.setValue(closure, BuildFileKey.STORE_FILE, replacementFile);
      }
    });
    String expected = getSimpleTestFile().replaceAll("debug.keystore", "foo.keystore");
    assertContents(file, expected);
    assertEquals(replacementFile, file.getValue(closure, BuildFileKey.STORE_FILE));
  }

  public void testSetFileStringValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    final GrStatementOwner closure = file.getClosure("android/productFlavors/flavor1");
    final File replacementFile = new File("foo.txt");
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        file.setValue(closure, BuildFileKey.PROGUARD_FILE, replacementFile);
      }
    });
    String expected = getSimpleTestFile().replaceAll("proguard-flavor1.txt", "foo.txt");
    assertContents(file, expected);
    assertEquals(replacementFile, file.getValue(closure, BuildFileKey.PROGUARD_FILE));
  }

  // TODO: Make this test work.
  public void testSetNamedObjectValue() throws Exception {
    if (true) {
      System.err.println("GradleBuildFileTest#testSetNamedObjectValue currently disabled");
      return;
    }

    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    Object value = file.getValue(BuildFileKey.FLAVORS);
    assert value != null;
    assert value instanceof List;
    final List<NamedObject> flavors = (List<NamedObject>)value;
    assertEquals(2, flavors.size());
    NamedObject flavor3 = new NamedObject("flavor3");
    flavor3.setValue(BuildFileKey.PACKAGE_NAME, "flavor3.packagename");
    flavors.add(flavor3);
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.FLAVORS, flavors);
      }
    });
    Object newValue = file.getValue(BuildFileKey.FLAVORS);
    assert newValue != null;
    assert newValue instanceof List;
    final List<NamedObject> newFlavors = (List<NamedObject>)newValue;

    StringBuilder expected = new StringBuilder(getSimpleTestFile());
    int position = expected.indexOf("}\n", expected.indexOf("flavor2 {\n")) + 2;
    expected.insert(position,
        "        flavor3 {\n" +
        "            packageName 'flavor3.packagename'\n" +
        "        }\n"
                               );
    assertContents(file, expected.toString());
    assertEquals(flavors, newFlavors);
  }

  public void testCreateStringValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.IGNORE_ASSETS_PATTERN, "foo");
      }
    });
    StringBuilder expected = new StringBuilder(getSimpleTestFile());
    int position = expected.length() - 1;
    expected.insert(position,
                    "    aaptOptions {\n" +
                    "        ignoreAssetsPattern 'foo'\n" +
                    "    }\n");
    assertContents(file, expected.toString());
    assertEquals("foo", file.getValue(BuildFileKey.IGNORE_ASSETS_PATTERN));
  }

  public void testCreateIntegerValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    final GrStatementOwner closure = file.getClosure("android/productFlavors/flavor1");
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        file.setValue(closure, BuildFileKey.VERSION_CODE, 199);
      }
    });
    StringBuilder expected = new StringBuilder(getSimpleTestFile());
    int position = expected.indexOf("\n", expected.indexOf("proguard-flavor1.txt")) + 1;
    expected.insert(position, "            versionCode 199\n");
    assertContents(file, expected.toString());
    Object value = file.getValue(BuildFileKey.FLAVORS);
    final List<NamedObject> flavors = (List<NamedObject>)value;
    assertEquals(2, flavors.size());
    assertEquals(199, flavors.get(0).getValue(BuildFileKey.VERSION_CODE));
  }

  public void testCreateBooleanValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.INCREMENTAL, true);
      }
    });
    StringBuilder expected = new StringBuilder(getSimpleTestFile());
    int position = expected.length() - 1;
    expected.insert(position,
                    "    dexOptions {\n" +
                    "        incremental true\n" +
                    "    }\n");
    assertContents(file, expected.toString());
    assertEquals(true, file.getValue(BuildFileKey.INCREMENTAL));
  }

  public void testCreateFileValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    final GrStatementOwner closure = file.getClosure("android/signingConfigs/config2");
    final File newFile = new File("foo.keystore");
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        file.setValue(closure, BuildFileKey.STORE_FILE, newFile);
      }
    });
    StringBuilder expected = new StringBuilder(getSimpleTestFile());
    int position = expected.indexOf("\n", expected.indexOf("config2 {")) + 1;
    expected.insert(position, "            storeFile file('foo.keystore')\n");
    assertContents(file, expected.toString());
    Object value = file.getValue(BuildFileKey.SIGNING_CONFIGS);
    final List<NamedObject> configs = (List<NamedObject>)value;
    assertEquals(2, configs.size());
    assertEquals(newFile, configs.get(1).getValue(BuildFileKey.STORE_FILE));
  }

  public void testCreateFileStringValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    final GrStatementOwner closure = file.getClosure("android/productFlavors/flavor2");
    final File newFile = new File("foo.txt");
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        file.setValue(closure, BuildFileKey.PROGUARD_FILE, newFile);
      }
    });
    StringBuilder expected = new StringBuilder(getSimpleTestFile());
    int position = expected.indexOf("\n", expected.indexOf("flavor2 {")) + 1;
    expected.insert(position, "            proguardFile 'foo.txt'\n");
    assertContents(file, expected.toString());
    Object value = file.getValue(BuildFileKey.FLAVORS);
    final List<NamedObject> configs = (List<NamedObject>)value;
    assertEquals(2, configs.size());
    assertEquals(newFile, configs.get(1).getValue(BuildFileKey.PROGUARD_FILE));
  }

  public void testRemoveValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        file.removeValue(null, BuildFileKey.COMPILE_SDK_VERSION);
      }
    });

    String expected = getSimpleTestFile().replace("    compileSdkVersion 17\n", "");
    assertContents(file, expected.toString());
    assertNull(file.getValue(BuildFileKey.COMPILE_SDK_VERSION));
  }

  public void testCanParseValueChecksInitialization() {
    GradleBuildFile file = getBadGradleBuildFile();
    try {
      file.canParseValue(BuildFileKey.TARGET_SDK_VERSION);
    } catch (IllegalStateException e) {
      // expected
      return;
    }
    fail("Failed to get expected IllegalStateException");
  }

  public void testCanParseNestedValueChecksInitialization() {
    GradleBuildFile file = getBadGradleBuildFile();
    try {
      file.canParseValue(getDummyClosure(), BuildFileKey.TARGET_SDK_VERSION);
    } catch (IllegalStateException e) {
      // expected
      return;
    }
    fail("Failed to get expected IllegalStateException");
  }

  public void testGetClosureChecksInitialization() {
    GradleBuildFile file = getBadGradleBuildFile();
    try {
      file.getClosure("/");
    } catch (IllegalStateException e) {
      // expected
      return;
    }
    fail("Failed to get expected IllegalStateException");
  }

  public void testGetValueChecksInitialization() {
    GradleBuildFile file = getBadGradleBuildFile();
    try {
      file.getValue(BuildFileKey.TARGET_SDK_VERSION);
    } catch (IllegalStateException e) {
      // expected
      return;
    }
    fail("Failed to get expected IllegalStateException");
  }

  public void testGetNestedValueChecksInitialization() {
    GradleBuildFile file = getBadGradleBuildFile();
    try {
      file.getValue(getDummyClosure(), BuildFileKey.TARGET_SDK_VERSION);
    } catch (IllegalStateException e) {
      // expected
      return;
    }
    fail("Failed to get expected IllegalStateException");
  }

  public void testSetValueChecksInitialization() {
    GradleBuildFile file = getBadGradleBuildFile();
    try {
      file.setValue(BuildFileKey.TARGET_SDK_VERSION, 2);
    } catch (IllegalStateException e) {
      // expected
      return;
    }
    fail("Failed to get expected IllegalStateException");
  }

  public void testSetNestedValueChecksInitialization() {
    GradleBuildFile file = getBadGradleBuildFile();
    try {
      file.setValue(getDummyClosure(), BuildFileKey.TARGET_SDK_VERSION, 2);
    } catch (IllegalStateException e) {
      // expected
      return;
    }
    fail("Failed to get expected IllegalStateException");
  }

  public void testGetsPropertyFromRedundantBlock() throws Exception {
    GradleBuildFile file = getTestFile(
      "android {\n" +
      "    buildToolsVersion '17.0.0'\n" +
      "}\n" +
      "android {\n" +
      "    compileSdkVersion 17\n" +
      "}\n"
    );
    assertEquals(17, file.getValue(BuildFileKey.COMPILE_SDK_VERSION));
    assertEquals("17.0.0", file.getValue(BuildFileKey.BUILD_TOOLS_VERSION));
  }

  public void testGetsMavenRepositories() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    List<Repository> expectedRepositories = Lists.newArrayList(
        new Repository(Repository.Type.MAVEN_CENTRAL, null),
        new Repository(Repository.Type.URL, "www.foo1.com"),
        new Repository(Repository.Type.URL, "www.foo2.com"),
        new Repository(Repository.Type.URL, "www.foo3.com"),
        new Repository(Repository.Type.URL, "www.foo4.com"));
    assertEquals(expectedRepositories, file.getValue(BuildFileKey.LIBRARY_REPOSITORY));
  }

  public void testSetsMavenRepositories() throws Exception {
    final GradleBuildFile file = getTestFile("");
    final List<Repository> newRepositories = Lists.newArrayList(
      new Repository(Repository.Type.MAVEN_CENTRAL, null),
      new Repository(Repository.Type.MAVEN_LOCAL, null),
      new Repository(Repository.Type.URL, "www.foo.com"));
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.LIBRARY_REPOSITORY, newRepositories);
      }
    });
    String expected =
      "repositories {\n" +
      "    mavenCentral()\n" +
      "    mavenLocal()\n" +
      "    maven { url 'www.foo.com' }\n" +
      "}";
    assertContents(file, expected);
  }

  public void testGetsFiletreeDependencies() throws Exception {
    GradleBuildFile file = getTestFile(
      "dependencies {\n" +
      "    compile fileTree(dir: 'libs', includes: ['*.jar', '*.aar'])\n" +
      "}"
    );
    ImmutableList<String> fileList = ImmutableList.of("*.jar", "*.aar");
    Map<String, Object> nvMap = ImmutableMap.of(
      "dir", "libs",
      "includes", (Object)fileList
    );
    Dependency dep = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.FILETREE, nvMap);
    List<Dependency> expected = ImmutableList.of(dep);
    assertEquals(expected, file.getValue(BuildFileKey.DEPENDENCIES));
  }

  public void testSetsFiletreeDependencies() throws Exception {
    final GradleBuildFile file = getTestFile("");
    ImmutableList<String> fileList = ImmutableList.of("*.jar", "*.aar");
    Map<String, Object> nvMap = ImmutableMap.of(
      "dir", "libs",
      "includes", (Object)fileList
    );
    final Dependency dep = new Dependency(Dependency.Scope.COMPILE, Dependency.Type.FILETREE, nvMap);
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        file.setValue(BuildFileKey.DEPENDENCIES, ImmutableList.of(dep));
      }
    });
    String expected =
      "dependencies {\n" +
      "    compile fileTree(dir: 'libs', includes: ['*.jar', '*.aar'])\n" +
      "}";
    assertContents(file, expected);
  }

  private static String getSimpleTestFile() throws IOException {
    return
      "buildscript {\n" +
      "    repositories {\n" +
      "        mavenCentral()\n" +
      "    }\n" +
      "    dependencies {\n" +
      "        classpath 'com.android.tools.build:gradle:0.5.+'\n" +
      "    }\n" +
      "}\n" +
      "apply plugin: 'android'\n" +
      "repositories {\n" +
      "    mavenCentral()\n" +
      "    maven('www.foo1.com', 'www.foo2.com')\n" +
      "    maven {\n" +
      "        url 'www.foo3.com'\n" +
      "        url 'www.foo4.com'\n" +
      "    }\n" +
      "}\n" +
      "dependencies {\n" +
      "    compile 'com.android.support:support-v4:13.0.+'\n" +
      "}\n" +
      "android {\n" +
      "    compileSdkVersion 17\n" +
      "    buildToolsVersion '17.0.0'\n" +
      "    defaultConfig {\n" +
      "        minSdkVersion someCrazyMethodCall()\n" +
      "        targetSdkVersion 1\n" +
      "    }\n" +
      "    buildTypes {\n" +
      "        debug {\n" +
      "            debuggable true\n" +
      "        }\n" +
      "        release {\n" +
      "            debuggable false\n" +
      "        }\n" +
      "    }\n" +
      "    signingConfigs {\n" +
      "        debug {\n" +
      "            storeFile file('debug.keystore')\n" +
      "        }\n" +
      "        config2 {\n" +
      "        }\n" +
      "    }\n" +
      "    productFlavors {\n" +
      "        flavor1 {\n" +
      "            proguardFile 'proguard-flavor1.txt'\n" +
      "        }\n" +
      "        flavor2 {\n" +
      "        }\n" +
      "    }\n" +
      "}";
  }

  private GradleBuildFile getTestFile(String contents) throws IOException {
    VirtualFile vf = getVirtualFile(createTempFile(SdkConstants.FN_BUILD_GRADLE, contents));
    myDocument = FileDocumentManager.getInstance().getDocument(vf);
    return new GradleBuildFile(vf, getProject());
  }

  private GradleBuildFile getBadGradleBuildFile() {
    // Use an intentionally invalid file path so that GradleBuildFile will remain uninitialized. This simulates the condition of
    // the PSI file not being parsed yet. GradleBuildFile will warn about the PSI file; this is expected.
    VirtualFile vf = LocalFileSystem.getInstance().getRoot();
    return new GradleBuildFile(vf, getProject());
  }

  private GrStatementOwner getDummyClosure() {
    return GroovyPsiElementFactory.getInstance(myProject).createClosureFromText("{}");
  }

  private void assertContents(GradleBuildFile file, String expected) throws IOException {
    PsiDocumentManager.getInstance(getProject()).commitDocument(myDocument);
    String actual = myDocument.getText();
    assertEquals(expected, actual);
  }
}
