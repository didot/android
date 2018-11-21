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

import com.intellij.diagnostic.logging.LogConfigurationPanel;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.JavaRunConfigurationExtensionManager;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.TestObject;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.project.Project;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Android implementation of {@link JUnitConfiguration} so some behaviors can be overridden.
 */
public class AndroidJUnitConfiguration extends JUnitConfiguration {
  public AndroidJUnitConfiguration(@NotNull Project project, @NotNull ConfigurationFactory configurationFactory) {
    super(project, new JUnitConfiguration.Data() {
      @Override
      public TestObject getTestObject(@NotNull JUnitConfiguration configuration) {
        AndroidTestObject testObject = fromString(TEST_OBJECT, configuration, ExecutionEnvironmentBuilder.create(
          DefaultRunExecutor.getRunExecutorInstance(), configuration).build());
        return testObject != null ? testObject : super.getTestObject(configuration);
      }
    }, configurationFactory);
  }

  @Override
  public TestObject getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    AndroidTestObject testObject = fromString(getPersistentData().TEST_OBJECT, this, env);
    return testObject != null ? testObject : super.getState(executor, env);
  }

  @Override
  public SMTRunnerConsoleProperties createTestConsoleProperties(Executor executor) {
    return new AndroidJUnitConsoleProperties(this, executor);
  }

  @Override
  @NotNull
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    SettingsEditorGroup<AndroidJUnitConfiguration> group = new SettingsEditorGroup<>();
    group.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title"), new AndroidJUnitConfigurable(getProject()));
    JavaRunConfigurationExtensionManager.getInstance().appendEditors(this, group);
    group.addEditor(ExecutionBundle.message("logs.tab.title"), new LogConfigurationPanel<>());
    return group;
  }

  @NotNull
  public Module[] getModulesToCompile() {
    if (TEST_PACKAGE.equals(getPersistentData().TEST_OBJECT)) {
      TestSearchScope scope = getPersistentData().getScope();
      if (scope == TestSearchScope.WHOLE_PROJECT) {
        return getAllModules().toArray(Module.EMPTY_ARRAY);
      }
      if (scope == TestSearchScope.MODULE_WITH_DEPENDENCIES) {
        Module classpathModule = getConfigurationModule().getModule();
        if (classpathModule != null) {
          Set<Module> modules = new HashSet<>();
          ModuleUtilCore.getDependencies(classpathModule, modules);
          return modules.toArray(Module.EMPTY_ARRAY);
        }
      }
    }

    return getModules();
  }

  private static AndroidTestObject fromString(String id,
                                              @NotNull JUnitConfiguration configuration,
                                              @NotNull ExecutionEnvironment environment) {
    if (JUnitConfiguration.TEST_PACKAGE.equals(id)) {
      return new AndroidTestObject(new AndroidTestPackage(configuration, environment));
    }
    if (JUnitConfiguration.TEST_PATTERN.equals(id)) {
      return new AndroidTestObject(new AndroidTestsPattern(configuration, environment));
    }
    return null;
  }
}
