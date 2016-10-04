/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework;

import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;
import org.junit.runners.model.RunnerScheduler;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

import static com.android.SdkConstants.DOT_CLASS;
import static com.android.tools.idea.tests.gui.framework.GuiTests.GUI_TESTS_RUNNING_IN_SUITE_PROPERTY;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.notNullize;

/** {@link Runner} that finds and runs classes {@link RunWith} {@link GuiTestRunner}. */
public class GuiTestSuiteRunner extends Suite {

  /** The name of a property specifying a {@link TestGroup} to run. If unspecified, all tests are run regardless of group. */
  private static final String TEST_GROUP_PROPERTY_NAME = "ui.test.group";

  public GuiTestSuiteRunner(Class<?> suiteClass, RunnerBuilder builder) throws InitializationError {
    super(builder, suiteClass, getGuiTestClasses(suiteClass));
    setScheduler(IDE_DISPOSER);
    System.setProperty(GUI_TESTS_RUNNING_IN_SUITE_PROPERTY, "true");
    try {
      String testGroupProperty = System.getProperty(TEST_GROUP_PROPERTY_NAME);
      if (testGroupProperty != null) {
        filter(new TestGroupFilter(TestGroup.valueOf(testGroupProperty)));
      }
    } catch (NoTestsRemainException e) {
      throw new InitializationError(e);
    }
  }

  @NotNull
  private static Class<?>[] getGuiTestClasses(@NotNull Class<?> suiteClass) throws InitializationError {
    List<File> guiTestClassFiles = Lists.newArrayList();
    File parentDir = getParentDir(suiteClass);

    String packagePath = suiteClass.getPackage().getName().replace('.', File.separatorChar);
    int packagePathIndex = parentDir.getPath().indexOf(packagePath);
    assertThat(packagePathIndex).isGreaterThan(-1);
    String testDirPath = parentDir.getPath().substring(0, packagePathIndex);

    findPotentialGuiTestClassFiles(parentDir, guiTestClassFiles);
    List<Class<?>> guiTestClasses = Lists.newArrayList();
    ClassLoader classLoader = suiteClass.getClassLoader();
    for (File classFile : guiTestClassFiles) {
      String path = classFile.getPath();
      String className = path.substring(testDirPath.length(), path.indexOf(DOT_CLASS)).replace(File.separatorChar, '.');
      try {
        Class<?> testClass = classLoader.loadClass(className);
        if (isGuiTest(testClass)) {
          guiTestClasses.add(testClass);
        }
      }
      catch (ClassNotFoundException e) {
        throw new InitializationError(e);
      }
    }
    return guiTestClasses.toArray(new Class<?>[guiTestClasses.size()]);
  }

  private static boolean isGuiTest(Class<?> testClass) {
    RunWith runWith = testClass.getAnnotation(RunWith.class);
    return runWith != null && runWith.value().getSimpleName().equals(GuiTestRunner.class.getSimpleName());
  }

  private static void findPotentialGuiTestClassFiles(@NotNull File directory, @NotNull List<File> guiTestClassFiles) {
    File[] children = notNullize(directory.listFiles());
    for (File child : children) {
      if (child.isDirectory()) {
        findPotentialGuiTestClassFiles(child, guiTestClassFiles);
        continue;
      }
      if (child.isFile() && !child.isHidden() && child.getName().endsWith("Test.class")) {
        guiTestClassFiles.add(child);
      }
    }
  }

  @NotNull
  private static File getParentDir(@NotNull Class<?> clazz) throws InitializationError {
    URL classUrl = clazz.getResource(clazz.getSimpleName() + DOT_CLASS);
    try {
      return new File(classUrl.toURI()).getParentFile();
    }
    catch (URISyntaxException e) {
      throw new InitializationError(e);
    }
  }

  private static class TestGroupFilter extends Filter {
    @NotNull final TestGroup testGroup;

    TestGroupFilter(@NotNull TestGroup testGroup) {
      this.testGroup = testGroup;
    }

    @Override
    public boolean shouldRun(Description description) {
      return (description.isTest() && testGroupOf(description) == testGroup)
        || description.getChildren().stream().anyMatch(this::shouldRun);
    }

    @NotNull
    static TestGroup testGroupOf(Description description) {
      RunIn methodAnnotation = description.getAnnotation(RunIn.class);
      return (methodAnnotation != null) ? methodAnnotation.value() : testGroupOf(description.getTestClass());
    }

    @NotNull
    static TestGroup testGroupOf(@NotNull Class<?> testClass) {
      RunIn classAnnotation = testClass.getAnnotation(RunIn.class);
      return (classAnnotation != null) ? classAnnotation.value() : TestGroup.DEFAULT;
    }

    @Override
    public String describe() {
      return TestGroupFilter.class.getSimpleName() + " for " + testGroup;
    }
  }

  private static final RunnerScheduler IDE_DISPOSER = new RunnerScheduler() {
    @Override
    public void schedule(Runnable childStatement) {
      childStatement.run();
    }

    @Override
    public void finished() {
      IdeTestApplication.disposeInstance();
    }
  };
}
