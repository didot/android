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
package com.android.tools.idea.testartifacts.instrumented;

import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.util.SystemInfo;

import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromClass;
import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromDirectory;
import static com.android.tools.idea.testing.TestProjectPaths.TEST_ONLY_MODULE;

/**
 * Test for {@link AndroidTestConfigurationProducer}
 */
public class AndroidTestConfigurationProducerTest extends AndroidGradleTestCase {

  @Override
  protected boolean shouldRunTest() {
    // Do not run tests on Windows (see http://b.android.com/222904)
    return !SystemInfo.isWindows && super.shouldRunTest();
  }

  public void testCanCreateAndroidTestConfigurationFromAndroidTestClass() throws Exception {
    loadSimpleApplication();
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromClass(getProject(), "google.simpleapplication.ApplicationTest");
    assertNotNull(runConfig);
    assertEmpty(runConfig.checkConfiguration(myAndroidFacet));
  }

  public void testCannotCreateAndroidTestConfigurationFromJUnitTestClass() throws Exception {
    loadSimpleApplication();
    assertNull(createAndroidTestConfigurationFromClass(getProject(), "google.simpleapplication.UnitTest"));
  }

  public void testCanCreateAndroidTestConfigurationFromAndroidTestDirectory() throws Exception {
    loadSimpleApplication();
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromDirectory(getProject(), "app/src/androidTest/java");
    assertNotNull(runConfig);
    assertEmpty(runConfig.checkConfiguration(myAndroidFacet));
  }

  public void testCannotCreateAndroidTestConfigurationFromJUnitTestDirectory() throws Exception {
    loadSimpleApplication();
    assertNull(createAndroidTestConfigurationFromDirectory(getProject(), "app/src/test/java"));
  }

  public void testCanCreateAndroidTestConfigurationFromFromTestOnlyModule() throws Exception {
    loadProject(TEST_ONLY_MODULE, "test");
    AndroidTestRunConfiguration runConfig = createAndroidTestConfigurationFromClass(getProject(), "com.example.android.app.ExampleTest");
    assertNotNull(runConfig);
    assertEmpty(runConfig.checkConfiguration(myAndroidFacet));
  }
}
