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
import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;
import org.junit.runners.model.RunnerScheduler;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.DOT_CLASS;
import static com.android.tools.idea.tests.gui.framework.GuiTests.GUI_TESTS_RUNNING_IN_SUITE_PROPERTY;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.notNullize;

/** {@link Runner} that finds and runs classes {@link RunWith} {@link GuiTestRunner}, limited by {@link IncludeTestGroups} if present. */
public class GuiTestSuiteRunner extends Suite {
  @Retention(RetentionPolicy.RUNTIME)
  public @interface IncludeTestGroups {
    TestGroup[] value();
  }

  public GuiTestSuiteRunner(Class<?> suiteClass, RunnerBuilder builder) throws InitializationError {
    super(builder, suiteClass, getGuiTestClasses(suiteClass));
    setScheduler(IDE_DISPOSER);
    System.setProperty(GUI_TESTS_RUNNING_IN_SUITE_PROPERTY, "true");
  }

  @NotNull
  public static Class<?>[] getGuiTestClasses(@NotNull Class<?> suiteClass) throws InitializationError {
    List<File> guiTestClassFiles = Lists.newArrayList();
    List<TestGroup> suiteGroups = getSuiteGroups(suiteClass);
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
        if (isGuiTest(testClass) && isInGroup(testClass, suiteGroups)) {
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

  @NotNull
  private static List<TestGroup> getSuiteGroups(@NotNull Class<?> suiteClass) {
    for (Annotation annotation : suiteClass.getAnnotations()) {
      if (annotation instanceof IncludeTestGroups) {
        TestGroup[] values = ((IncludeTestGroups)annotation).value();
        if (values != null) {
          return Lists.newArrayList(values);
        }
        break;
      }
    }
    return Collections.emptyList();
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

  private static boolean isInGroup(@NotNull Class<?> testClass, List<TestGroup> suiteGroups) {
    if (suiteGroups.isEmpty()) {
      return true;
    }
    if (suiteGroups.contains(getTestGroup(testClass))) {
      return true;
    }
    return false;
  }

  @NotNull
  public static TestGroup getTestGroup(@NotNull Class<?> suiteClass) {
    RunIn runIn = suiteClass.getAnnotation(RunIn.class);
    return (runIn != null) ? runIn.value() : TestGroup.DEFAULT;
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
