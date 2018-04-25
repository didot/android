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
package com.android.tools.idea.gradle.dsl.model.android;

import com.android.tools.idea.gradle.dsl.api.ExternalNativeBuildModel;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.externalNativeBuild.CMakeModel;
import com.android.tools.idea.gradle.dsl.api.android.externalNativeBuild.NdkBuildModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.android.tools.idea.gradle.dsl.model.android.externalNativeBuild.CMakeModelImpl;
import com.android.tools.idea.gradle.dsl.model.android.externalNativeBuild.NdkBuildModelImpl;
import org.junit.Test;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.STRING;
import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.DERIVED;

/**
 * Tests for {@link ExternalNativeBuildModelImpl}.
 */
public class ExternalNativeBuildModelTest extends GradleFileModelTestCase {
  @Test
  public void testCMake() throws Exception {
    String text = "android {\n" +
                  "  externalNativeBuild {\n" +
                  "    cmake {\n" +
                  "      path file(\"foo/bar\")\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    ExternalNativeBuildModel externalNativeBuild = android.externalNativeBuild();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    CMakeModel cmake = externalNativeBuild.cmake();
    checkForValidPsiElement(cmake, CMakeModelImpl.class);
    assertEquals("path", "foo/bar", cmake.path());
  }

  @Test
  public void testCMakeWithNewFilePath() throws Exception {
    String text = "android {\n" +
                  "  externalNativeBuild {\n" +
                  "    cmake {\n" +
                  "      path new File(\"foo/bar\")\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    ExternalNativeBuildModel externalNativeBuild = android.externalNativeBuild();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    CMakeModel cmake = externalNativeBuild.cmake();
    checkForValidPsiElement(cmake, CMakeModelImpl.class);
    assertEquals("path", "foo/bar", cmake.path());
  }

  @Test
  public void testRemoveCMakeAndReset() throws Exception {
    String text = "android {\n" +
                  "  externalNativeBuild {\n" +
                  "    cmake {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ExternalNativeBuildModel externalNativeBuild = android.externalNativeBuild();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForValidPsiElement(externalNativeBuild.cmake(), CMakeModelImpl.class);

    externalNativeBuild.removeCMake();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForInValidPsiElement(externalNativeBuild.cmake(), CMakeModelImpl.class);

    buildModel.resetState();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForValidPsiElement(externalNativeBuild.cmake(), CMakeModelImpl.class);
  }

  @Test
  public void testRemoveCMakeAndApplyChanges() throws Exception {
    String text = "android {\n" +
                  "  externalNativeBuild {\n" +
                  "    cmake {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ExternalNativeBuildModel externalNativeBuild = android.externalNativeBuild();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForValidPsiElement(externalNativeBuild.cmake(), CMakeModelImpl.class);

    externalNativeBuild.removeCMake();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForInValidPsiElement(externalNativeBuild.cmake(), CMakeModelImpl.class);

    applyChanges(buildModel);
    checkForInValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class); // empty blocks removed
    checkForInValidPsiElement(externalNativeBuild.cmake(), CMakeModelImpl.class);

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    externalNativeBuild = android.externalNativeBuild();
    checkForInValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForInValidPsiElement(externalNativeBuild.cmake(), CMakeModelImpl.class);
  }

  @Test
  public void testAddCMakePathAndReset() throws Exception {
    String text = "android {\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ExternalNativeBuildModel externalNativeBuild = android.externalNativeBuild();
    CMakeModel cmake = externalNativeBuild.cmake();
    assertMissingProperty(cmake.path());

    cmake.path().setValue("foo/bar");
    assertEquals("path", "foo/bar", cmake.path());

    buildModel.resetState();
    assertMissingProperty(cmake.path());
  }

  @Test
  public void testAddCMakePathAndApplyChanges() throws Exception {
    String text = "android {\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    CMakeModel cmake = android.externalNativeBuild().cmake();
    assertMissingProperty(cmake.path());

    cmake.path().setValue("foo/bar");
    assertEquals("path", "foo/bar", cmake.path());

    applyChanges(buildModel);
    assertEquals("path", "foo/bar", cmake.path());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    cmake = android.externalNativeBuild().cmake();
    assertEquals("path", "foo/bar", cmake.path());
  }

  @Test
  public void testNdkBuild() throws Exception {
    String text = "android {\n" +
                  "  externalNativeBuild {\n" +
                  "    ndkBuild {\n" +
                  "      path file(\"foo/Android.mk\")\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    ExternalNativeBuildModel externalNativeBuild = android.externalNativeBuild();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    NdkBuildModel ndkBuild = externalNativeBuild.ndkBuild();
    checkForValidPsiElement(ndkBuild, NdkBuildModelImpl.class);
    assertEquals("path", "foo/Android.mk", ndkBuild.path());
  }

  @Test
  public void testNdkBuildWithNewFilePath() throws Exception {
    String text = "android {\n" +
                  "  externalNativeBuild {\n" +
                  "    ndkBuild {\n" +
                  "      path new File(\"foo\", \"Android.mk\")\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    ExternalNativeBuildModel externalNativeBuild = android.externalNativeBuild();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    NdkBuildModel ndkBuild = externalNativeBuild.ndkBuild();
    checkForValidPsiElement(ndkBuild, NdkBuildModelImpl.class);
    assertEquals("path", "foo/Android.mk", ndkBuild.path());
  }

  @Test
  public void testRemoveNdkBuildAndReset() throws Exception {
    String text = "android {\n" +
                  "  externalNativeBuild {\n" +
                  "    ndkBuild {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ExternalNativeBuildModel externalNativeBuild = android.externalNativeBuild();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForValidPsiElement(externalNativeBuild.ndkBuild(), NdkBuildModelImpl.class);

    externalNativeBuild.removeNdkBuild();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForInValidPsiElement(externalNativeBuild.ndkBuild(), NdkBuildModelImpl.class);

    buildModel.resetState();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForValidPsiElement(externalNativeBuild.ndkBuild(), NdkBuildModelImpl.class);
  }

  @Test
  public void testRemoveNdkBuildAndApplyChanges() throws Exception {
    String text = "android {\n" +
                  "  externalNativeBuild {\n" +
                  "    ndkBuild {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ExternalNativeBuildModel externalNativeBuild = android.externalNativeBuild();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForValidPsiElement(externalNativeBuild.ndkBuild(), NdkBuildModelImpl.class);

    externalNativeBuild.removeNdkBuild();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForInValidPsiElement(externalNativeBuild.ndkBuild(), NdkBuildModelImpl.class);

    applyChanges(buildModel);
    checkForInValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForInValidPsiElement(externalNativeBuild.ndkBuild(), NdkBuildModelImpl.class);

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    externalNativeBuild = android.externalNativeBuild();
    checkForInValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForInValidPsiElement(externalNativeBuild.ndkBuild(), NdkBuildModelImpl.class);
  }

  @Test
  public void testAddNdkBuildPathAndReset() throws Exception {
    String text = "android {\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    NdkBuildModel ndkBuild = android.externalNativeBuild().ndkBuild();
    assertMissingProperty(ndkBuild.path());

    ndkBuild.path().setValue("foo/Android.mk");
    assertEquals("path", "foo/Android.mk", ndkBuild.path());

    buildModel.resetState();
    assertMissingProperty(ndkBuild.path());
  }

  @Test
  public void testAddNdkBuildPathAndApplyChanges() throws Exception {
    String text = "android {\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    NdkBuildModel ndkBuild = android.externalNativeBuild().ndkBuild();
    assertMissingProperty(ndkBuild.path());

    ndkBuild.path().setValue("foo/Android.mk");
    assertEquals("path", "foo/Android.mk", ndkBuild.path());

    applyChanges(buildModel);
    assertEquals("path", "foo/Android.mk", ndkBuild.path());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    ndkBuild = android.externalNativeBuild().ndkBuild();
    assertEquals("path", "foo/Android.mk", ndkBuild.path());
  }

  @Test
  public void testSetConstructorToFunction() throws Exception {
    String text = "android {\n" +
                  "  externalNativeBuild {\n" +
                  "    ndkBuild {\n" +
                  "      path new File(\"foo\", \"Android.mk\")\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    NdkBuildModel ndkBuildModel = android.externalNativeBuild().ndkBuild();
    ndkBuildModel.path().setValue("foo/bar/file.txt");
    applyChangesAndReparse(buildModel);

    android = buildModel.android();
    assertNotNull(android);

    ndkBuildModel = android.externalNativeBuild().ndkBuild();
    verifyPropertyModel(ndkBuildModel.path(), STRING_TYPE, "foo/bar/file.txt", STRING, DERIVED, 0, "0");
  }
}
