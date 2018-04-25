/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.build;

import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.ext.ExtModel;
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import org.junit.Test;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.STRING;

/**
 * Tests resolving references to project, parent, rootProject etc.
 */
public class ReferenceResolutionTest extends GradleFileModelTestCase {
  @Test
  public void testResolveRootDir() throws Exception {
    String settingsText = "include ':" + SUB_MODULE_NAME + "'";

    String mainModuleText = "";

    String subModuleText = "ext {\n" +
                           "  rpd = rootDir\n" +
                           "  rpd1 = project.rootDir\n" +
                           "  rpd2 = parent.rootDir\n" +
                           "  rpd3 = rootProject.rootDir\n" +
                           "  rpd4 = project(':" + SUB_MODULE_NAME + "').rootDir\n" +
                           "  rpd5 = project(':').rootDir\n" +
                           "}";

    writeToSettingsFile(settingsText);
    writeToBuildFile(mainModuleText);
    writeToSubModuleBuildFile(subModuleText);

    String expectedRootDir = getBaseDirPath(myProject).getPath();
    ExtModel ext = getSubModuleGradleBuildModel().ext();
    verifyPropertyModel(ext.findProperty("rpd").resolve(), STRING_TYPE, expectedRootDir, STRING, PropertyType.REGULAR, 1);
    verifyPropertyModel(ext.findProperty("rpd1").resolve(), STRING_TYPE, expectedRootDir, STRING, PropertyType.REGULAR, 1);
    verifyPropertyModel(ext.findProperty("rpd2").resolve(), STRING_TYPE, expectedRootDir, STRING, PropertyType.REGULAR, 1);
    verifyPropertyModel(ext.findProperty("rpd3").resolve(), STRING_TYPE, expectedRootDir, STRING, PropertyType.REGULAR, 1);
    verifyPropertyModel(ext.findProperty("rpd4").resolve(), STRING_TYPE, expectedRootDir, STRING, PropertyType.REGULAR, 1);
    verifyPropertyModel(ext.findProperty("rpd5").resolve(), STRING_TYPE, expectedRootDir, STRING, PropertyType.REGULAR, 1);
  }

  @Test
  public void testResolveProjectDir() throws Exception {
    String settingsText = "include ':" + SUB_MODULE_NAME + "'";

    String mainModuleText = "";

    String subModuleText = "ext {\n" +
                           "  pd = projectDir\n" +
                           "  pd1 = project.projectDir\n" +
                           "  pd2 = parent.projectDir\n" +
                           "  pd3 = rootProject.projectDir\n" +
                           "  pd4 = project(':" + SUB_MODULE_NAME + "').projectDir\n" +
                           "  pd5 = project(':').projectDir\n" +
                           "}";

    writeToSettingsFile(settingsText);
    writeToBuildFile(mainModuleText);
    writeToSubModuleBuildFile(subModuleText);

    String expectedRootDir = getBaseDirPath(myProject).getPath();
    String expectedSubModuleDir = mySubModuleBuildFile.getParent();
    ExtModel ext = getSubModuleGradleBuildModel().ext();
    verifyPropertyModel(ext.findProperty("pd").resolve(), STRING_TYPE, expectedSubModuleDir, STRING, PropertyType.REGULAR, 1);
    verifyPropertyModel(ext.findProperty("pd1").resolve(), STRING_TYPE, expectedSubModuleDir, STRING, PropertyType.REGULAR, 1);
    verifyPropertyModel(ext.findProperty("pd2").resolve(), STRING_TYPE, expectedRootDir, STRING, PropertyType.REGULAR, 1);
    verifyPropertyModel(ext.findProperty("pd3").resolve(), STRING_TYPE, expectedRootDir, STRING, PropertyType.REGULAR, 1);
    verifyPropertyModel(ext.findProperty("pd4").resolve(), STRING_TYPE, expectedSubModuleDir, STRING, PropertyType.REGULAR, 1);
    verifyPropertyModel(ext.findProperty("pd5").resolve(), STRING_TYPE, expectedRootDir, STRING, PropertyType.REGULAR, 1);
  }

  @Test
  public void testResolveProject() throws Exception {
    String settingsText = "include ':" + SUB_MODULE_NAME + "'";

    String mainModuleText = "";

    String subModuleText = "android {\n" +
                           "  compileSdkVersion = \"android-23\"\n" +
                           "  defaultConfig {\n" +
                           "    minSdkVersion = project.android.compileSdkVersion\n" +
                           "  }\n" +
                           "}";

    writeToSettingsFile(settingsText);
    writeToBuildFile(mainModuleText);
    writeToSubModuleBuildFile(subModuleText);

    AndroidModel android = getSubModuleGradleBuildModel().android();
    assertNotNull(android);

    assertEquals("compileSdkVersion", "android-23", android.compileSdkVersion());
    assertEquals("minSdkVersion", "android-23", android.defaultConfig().minSdkVersion());
  }

  @Test
  public void testResolveParent() throws Exception {
    String settingsText = "include ':" + SUB_MODULE_NAME + "'";

    String mainModuleText = "android {\n" +
                            "  compileSdkVersion = \"android-23\"\n" +
                            "}";

    String subModuleText = "android {\n" +
                           "  compileSdkVersion = parent.android.compileSdkVersion\n" +
                           "}";

    writeToSettingsFile(settingsText);
    writeToBuildFile(mainModuleText);
    writeToSubModuleBuildFile(subModuleText);

    AndroidModel androidModel = getGradleBuildModel().android();
    assertNotNull(androidModel);
    assertEquals("compileSdkVersion", "android-23", androidModel.compileSdkVersion());

    AndroidModel subModuleAndroidModel = getSubModuleGradleBuildModel().android();
    assertNotNull(subModuleAndroidModel);
    assertEquals("compileSdkVersion", "android-23", subModuleAndroidModel.compileSdkVersion());
  }

  @Test
  public void testResolveRootProject() throws Exception {
    String settingsText = "include ':" + SUB_MODULE_NAME + "'";

    String mainModuleText = "android {\n" +
                            "  compileSdkVersion = \"android-23\"\n" +
                            "}";

    String subModuleText = "android {\n" +
                           "  compileSdkVersion = rootProject.android.compileSdkVersion\n" +
                           "}";

    writeToSettingsFile(settingsText);
    writeToBuildFile(mainModuleText);
    writeToSubModuleBuildFile(subModuleText);

    AndroidModel androidModel = getGradleBuildModel().android();
    assertNotNull(androidModel);
    assertEquals("compileSdkVersion", "android-23", androidModel.compileSdkVersion());

    AndroidModel subModuleAndroidModel = getSubModuleGradleBuildModel().android();
    assertNotNull(subModuleAndroidModel);
    assertEquals("compileSdkVersion", "android-23", subModuleAndroidModel.compileSdkVersion());
  }

  @Test
  public void testResolveProjectPath() throws Exception {
    String settingsText = "include ':" + SUB_MODULE_NAME + "'";

    String mainModuleText = "";

    String subModuleText = "android {\n" +
                           "  compileSdkVersion = \"android-23\"\n" +
                           "  defaultConfig {\n" +
                           "    minSdkVersion = project(':" + SUB_MODULE_NAME + "').android.compileSdkVersion\n" +
                           "  }\n" +
                           "}";

    writeToSettingsFile(settingsText);
    writeToBuildFile(mainModuleText);
    writeToSubModuleBuildFile(subModuleText);

    AndroidModel android = getSubModuleGradleBuildModel().android();
    assertNotNull(android);

    assertEquals("compileSdkVersion", "android-23", android.compileSdkVersion());
    assertEquals("minSdkVersion", "android-23", android.defaultConfig().minSdkVersion());
  }

  @Test
  public void testResolveOtherProjectPath() throws Exception {
    String settingsText = "include ':" + SUB_MODULE_NAME + "'";

    String mainModuleText = "android {\n" +
                            "  compileSdkVersion = \"android-23\"\n" +
                            "}";

    String subModuleText = "android {\n" +
                           "  compileSdkVersion = project(':').android.compileSdkVersion\n" +
                           "}";

    writeToSettingsFile(settingsText);
    writeToBuildFile(mainModuleText);
    writeToSubModuleBuildFile(subModuleText);

    AndroidModel androidModel = getGradleBuildModel().android();
    assertNotNull(androidModel);
    assertEquals("compileSdkVersion", "android-23", androidModel.compileSdkVersion());

    AndroidModel subModuleAndroidModel = getSubModuleGradleBuildModel().android();
    assertNotNull(subModuleAndroidModel);
    assertEquals("compileSdkVersion", "android-23", subModuleAndroidModel.compileSdkVersion());
  }
}
