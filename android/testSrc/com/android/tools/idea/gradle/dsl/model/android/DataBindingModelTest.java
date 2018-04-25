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

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.DataBindingModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import org.junit.Test;

/**
 * Tests for {@link DataBindingModel}.
 */
public class DataBindingModelTest extends GradleFileModelTestCase {
  @Test
  public void testParseElements() throws Exception {
    String text = "android {\n" +
                  "  dataBinding {\n" +
                  "    addDefaultAdapters true\n" +
                  "    enabled false\n" +
                  "    version '1.0'\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    DataBindingModel dataBinding = android.dataBinding();
    assertEquals("addDefaultAdapters", Boolean.TRUE, dataBinding.addDefaultAdapters());
    assertEquals("enabled", Boolean.FALSE, dataBinding.enabled());
    assertEquals("version", "1.0", dataBinding.version());
  }

  @Test
  public void testEditElements() throws Exception {
    String text = "android {\n" +
                  "  dataBinding {\n" +
                  "    addDefaultAdapters true\n" +
                  "    enabled false\n" +
                  "    version '1.0'\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    DataBindingModel dataBinding = android.dataBinding();
    assertEquals("addDefaultAdapters", Boolean.TRUE, dataBinding.addDefaultAdapters());
    assertEquals("enabled", Boolean.FALSE, dataBinding.enabled());
    assertEquals("version", "1.0", dataBinding.version());

    dataBinding.addDefaultAdapters().setValue(false);
    dataBinding.enabled().setValue(true);
    dataBinding.version().setValue("2.0");

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    dataBinding = android.dataBinding();
    assertEquals("addDefaultAdapters", Boolean.FALSE, dataBinding.addDefaultAdapters());
    assertEquals("enabled", Boolean.TRUE, dataBinding.enabled());
    assertEquals("version", "2.0", dataBinding.version());
  }

  @Test
  public void testAddElements() throws Exception {
    String text = "android {\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    DataBindingModel dataBinding = android.dataBinding();
    assertMissingProperty("addDefaultAdapters", dataBinding.addDefaultAdapters());
    assertMissingProperty("enabled", dataBinding.enabled());
    assertMissingProperty("version", dataBinding.version());

    dataBinding.addDefaultAdapters().setValue(true);
    dataBinding.enabled().setValue(false);
    dataBinding.version().setValue("1.0");

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    dataBinding = android.dataBinding();
    assertEquals("addDefaultAdapters", Boolean.TRUE, dataBinding.addDefaultAdapters());
    assertEquals("enabled", Boolean.FALSE, dataBinding.enabled());
    assertEquals("version", "1.0", dataBinding.version());
  }

  @Test
  public void testAddElementsFromExisting() throws Exception {
    String text = "android {\n" +
                  "  dataBinding {\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    DataBindingModel dataBinding = android.dataBinding();
    assertMissingProperty("addDefaultAdapters", dataBinding.addDefaultAdapters());
    assertMissingProperty("enabled", dataBinding.enabled());
    assertMissingProperty("version", dataBinding.version());

    dataBinding.addDefaultAdapters().setValue(true);
    dataBinding.enabled().setValue(false);
    dataBinding.version().setValue("1.0");

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    dataBinding = android.dataBinding();
    assertEquals("addDefaultAdapters", Boolean.TRUE, dataBinding.addDefaultAdapters());
    assertEquals("enabled", Boolean.FALSE, dataBinding.enabled());
    assertEquals("version", "1.0", dataBinding.version());
  }

  @Test
  public void testRemoveElements() throws Exception {
    String text = "android {\n" +
                  "  dataBinding {\n" +
                  "    addDefaultAdapters true\n" +
                  "    enabled false\n" +
                  "    version '1.0'\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    DataBindingModel dataBinding = android.dataBinding();
    checkForValidPsiElement(dataBinding, DataBindingModelImpl.class);
    assertEquals("addDefaultAdapters", Boolean.TRUE, dataBinding.addDefaultAdapters());
    assertEquals("enabled", Boolean.FALSE, dataBinding.enabled());
    assertEquals("version", "1.0", dataBinding.version());

    dataBinding.addDefaultAdapters().delete();
    dataBinding.enabled().delete();
    dataBinding.version().delete();

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    dataBinding = android.dataBinding();
    checkForInValidPsiElement(dataBinding, DataBindingModelImpl.class);
    assertMissingProperty("addDefaultAdapters", dataBinding.addDefaultAdapters());
    assertMissingProperty("enabled", dataBinding.enabled());
    assertMissingProperty("version", dataBinding.version());
  }
}
