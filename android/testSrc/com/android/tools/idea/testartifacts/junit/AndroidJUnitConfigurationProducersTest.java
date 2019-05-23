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
package com.android.tools.idea.testartifacts.junit;

import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createContext;
import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createJUnitConfigurationFromClass;
import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createJUnitConfigurationFromDirectory;
import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createJUnitConfigurationFromFile;
import static com.android.tools.idea.testartifacts.TestConfigurationTesting.getPsiElement;
import static com.android.tools.idea.testing.TestProjectPaths.TEST_ARTIFACTS_KOTLIN;

import com.android.tools.idea.gradle.run.MakeBeforeRunTaskProvider;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.openapi.util.SystemInfo;
import java.util.List;
import org.gradle.internal.impldep.com.google.common.collect.Lists;


/**
 * Tests for all the {@link AndroidJUnitConfigurationProducer}s
 */
public class AndroidJUnitConfigurationProducersTest extends AndroidGradleTestCase {

  @Override
  protected boolean shouldRunTest() {
    // Do not run tests on Windows (see http://b.android.com/222904)
    return !SystemInfo.isWindows && super.shouldRunTest();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  public void testCanCreateJUnitConfigurationFromJUnitTestClass() throws Exception {
    loadSimpleApplication();
    assertNotNull(createJUnitConfigurationFromClass(getProject(), "google.simpleapplication.UnitTest"));
  }

  public void testCannotCreateJUnitConfigurationFromAndroidTestClass() throws Exception {
    loadSimpleApplication();
    assertNull(createJUnitConfigurationFromClass(getProject(), "google.simpleapplication.ApplicationTest"));
  }

  public void testCanCreateJUnitConfigurationFromJUnitTestDirectory() throws Exception {
    loadSimpleApplication();
    assertNotNull(createJUnitConfigurationFromDirectory(getProject(), "app/src/test/java"));
  }

  public void testCannotCreateJUnitConfigurationFromAndroidTestDirectory() throws Exception {
    loadSimpleApplication();
    assertNull(createJUnitConfigurationFromDirectory(getProject(), "app/src/androidTest/java"));
  }

  public void testCannotCreateJUnitConfigurationFromAndroidTestClassKotlin() throws Exception {
    loadProject(TEST_ARTIFACTS_KOTLIN);
    assertNull(createJUnitConfigurationFromFile(
      getProject(), "app/src/androidTest/java/com/example/android/kotlin/ExampleInstrumentedTest.kt"));
  }

  public void testCanCreateJUnitConfigurationFromJUnitTestDirectoryKotlin() throws Exception {
    loadProject(TEST_ARTIFACTS_KOTLIN);
    assertNotNull(createJUnitConfigurationFromDirectory(getProject(), "app/src/test/java"));
  }

  public void testCannotCreateJUnitConfigurationFromAndroidTestDirectoryKotlin() throws Exception {
    loadProject(TEST_ARTIFACTS_KOTLIN);
    assertNull(createJUnitConfigurationFromDirectory(getProject(), "app/src/androidTest/java"));
  }

  public void testCreatedJUnitConfigurationHasGradleBeforeRunTask() throws Exception {
    loadSimpleApplication();

    // Create and add RunConfiguration to the RunManager
    RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(getProject());
    RunnerAndConfigurationSettings settings = runManager.createConfiguration(
      createJUnitConfigurationFromClass(getProject(), "google.simpleapplication.UnitTest"),
      AndroidJUnitConfigurationType.getInstance().getConfigurationFactories()[0]);
    runManager.addConfiguration(settings);

    // Get AndroidJUnitRunConfiguration from RunManager
    List<RunConfiguration> runConfigurations = runManager.getConfigurationsList(AndroidJUnitConfigurationType.getInstance());
    assertSize(1, runConfigurations);
    RunConfiguration runConfiguration = runConfigurations.iterator().next();
    assertInstanceOf(runConfiguration, AndroidJUnitConfiguration.class);

    // Check if BeforeRunTask is correct
    List<BeforeRunTask<?>> beforeRunTasks = runManager.getBeforeRunTasks(runConfiguration);
    assertSize(1, beforeRunTasks);
    assertEquals(MakeBeforeRunTaskProvider.ID, beforeRunTasks.get(0).getProviderId());

    // Re-sync and check again
    requestSyncAndWait();
    runConfigurations = runManager.getConfigurationsList(AndroidJUnitConfigurationType.getInstance());
    assertSize(1, runConfigurations);
    runConfiguration = runConfigurations.iterator().next();
    assertInstanceOf(runConfiguration, AndroidJUnitConfiguration.class);

    beforeRunTasks = RunManagerImpl.getInstanceImpl(getProject()).getBeforeRunTasks(runConfiguration);
    assertSize(1, beforeRunTasks);
    assertEquals(MakeBeforeRunTaskProvider.ID, beforeRunTasks.get(0).getProviderId());
  }

  public void testExistingJUnitConfigurationNotModifiedAfterSync() throws Exception {
    loadSimpleApplication();

    // Create and add RunConfiguration to the RunManager
    RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(getProject());
    RunnerAndConfigurationSettings settings = runManager.createConfiguration(
      createJUnitConfigurationFromClass(getProject(), "google.simpleapplication.UnitTest"),
      AndroidJUnitConfigurationType.getInstance().getConfigurationFactories()[0]);
    runManager.addConfiguration(settings);

    // Get AndroidJUnitRunConfiguration from RunManager
    List<RunConfiguration> runConfigurations = runManager.getConfigurationsList(AndroidJUnitConfigurationType.getInstance());
    assertSize(1, runConfigurations);
    RunConfiguration runConfiguration = runConfigurations.iterator().next();
    assertInstanceOf(runConfiguration, AndroidJUnitConfiguration.class);

    // Check if BeforeRunTask is correct
    List<BeforeRunTask<?>> beforeRunTasks = runManager.getBeforeRunTasks(runConfiguration);
    assertSize(1, beforeRunTasks);
    assertEquals(MakeBeforeRunTaskProvider.ID, beforeRunTasks.get(0).getProviderId());

    // Modify tasks
    CompileStepBeforeRun.MakeBeforeRunTask ideaMake = BeforeRunTaskProvider.getProvider(getProject(), CompileStepBeforeRun.ID).
      createTask(runConfiguration);
    runManager.setBeforeRunTasks(runConfiguration, Lists.<BeforeRunTask>newArrayList(ideaMake));

    // Re-sync and check again
    requestSyncAndWait();
    runConfigurations = runManager.getConfigurationsList(AndroidJUnitConfigurationType.getInstance());
    assertSize(1, runConfigurations);
    runConfiguration = runConfigurations.iterator().next();
    assertInstanceOf(runConfiguration, AndroidJUnitConfiguration.class);

    beforeRunTasks = RunManagerImpl.getInstanceImpl(getProject()).getBeforeRunTasks(runConfiguration);
    assertSize(1, beforeRunTasks);
    assertEquals(CompileStepBeforeRun.ID, beforeRunTasks.get(0).getProviderId());
  }

  public void testIsFromContextForDirectoryJUnitConfiguration() throws Exception {
    loadSimpleApplication();
    AndroidJUnitConfiguration configuration = createJUnitConfigurationFromDirectory(getProject(), "app/src/test/java");
    ConfigurationContext context = createContext(getProject(), getPsiElement(getProject(), "app/src/test/java", true));
    assertTrue(new TestDirectoryAndroidConfigurationProducer().isConfigurationFromContext(configuration, context));
  }
}
