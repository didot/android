/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.invoker;

import static com.android.tools.idea.gradle.project.build.invoker.TestCompileTypeUtilKt.getArtifacts;
import static com.android.tools.idea.gradle.util.BuildMode.REBUILD;
import static com.android.tools.idea.gradle.util.GradleBuilds.BUILD_SRC_FOLDER_NAME;
import static com.android.tools.idea.gradle.util.GradleBuilds.CLEAN_TASK_NAME;
import static com.android.tools.idea.gradle.util.GradleProjectSystemUtil.createFullTaskName;
import static com.android.tools.idea.gradle.util.GradleUtil.findModuleByGradlePath;
import static com.android.tools.idea.projectsystem.gradle.GradleProjectPathKt.getBuildRootDir;
import static com.android.tools.idea.projectsystem.gradle.GradleProjectPathKt.getGradleProjectPath;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.util.PathUtil.toSystemIndependentName;
import static java.util.Arrays.stream;
import static java.util.stream.Stream.concat;

import com.android.tools.idea.gradle.model.IdeAndroidProjectType;
import com.android.tools.idea.gradle.model.IdeBaseArtifact;
import com.android.tools.idea.gradle.model.IdeTestedTargetVariant;
import com.android.tools.idea.gradle.model.IdeVariant;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.java.JavaFacet;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.DynamicAppUtils;
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil;
import com.android.tools.idea.gradle.util.GradleProjects;
import com.android.tools.idea.projectsystem.gradle.GradleProjectPath;
import com.android.utils.Pair;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.ListMultimap;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GradleTaskFinder {
  @NotNull
  public static GradleTaskFinder getInstance() {
    return ApplicationManager.getApplication().getService(GradleTaskFinder.class);
  }

  @NotNull
  public ListMultimap<Path, String> findTasksToExecuteForTest(@NotNull Module[] modules,
                                                              @NotNull Module[] testModules,
                                                              @NotNull BuildMode buildMode,
                                                              @NotNull TestCompileType testCompileType) {
    ListMultimap<Path, String> allTasks = findTasksToExecuteCore(modules, buildMode, TestCompileType.NONE);
    ListMultimap<Path, String> testedModulesTasks = findTasksToExecuteCore(testModules, buildMode, testCompileType);

    // Add testedModulesTasks to allTasks without duplicate
    for (Map.Entry<Path, String> task : testedModulesTasks.entries()) {
      if (!allTasks.values().contains(task.getValue())) {
        allTasks.put(task.getKey(), task.getValue());
      }
    }
    if (allTasks.isEmpty()) {
      notifyNoTaskFound(
        concat(stream(modules), stream(testModules)).distinct().toArray(Module[]::new), buildMode, testCompileType);
    }
    return allTasks;
  }

  @NotNull
  public ListMultimap<Path, String> findTasksToExecute(@NotNull Module[] modules,
                                                       @NotNull BuildMode buildMode,
                                                       @NotNull TestCompileType testCompileType) {
    ArrayListMultimap<Path, String> result = findTasksToExecuteCore(modules, buildMode, testCompileType);
    if (result.isEmpty()) {
      notifyNoTaskFound(modules, buildMode, testCompileType);
    }
    return result;
  }

  @NotNull
  private ArrayListMultimap<Path, String> findTasksToExecuteCore(@NotNull Module[] modules,
                                                                 @NotNull BuildMode buildMode,
                                                                 @NotNull TestCompileType testCompileType) {
    LinkedHashMultimap<Path, String> tasks = LinkedHashMultimap.create();
    LinkedHashMultimap<Path, String> cleanTasks = LinkedHashMultimap.create();

    Set<Module> allModules = new LinkedHashSet<>();
    for (Module module : modules) {
      allModules.addAll(GradleProjectSystemUtil.getModulesToBuild(module));
    }

    // Instrumented test support for Dynamic Features: base-app module should be added explicitly for gradle tasks
    if (testCompileType == TestCompileType.ANDROID_TESTS) {
      for (Module module : modules) {
        Module baseAppModule = DynamicAppUtils.getBaseFeature(module);
        if (baseAppModule != null) {
          allModules.add(baseAppModule);
        }
      }
    }

    for (Module module : allModules) {
      String moduleFilePath = ExternalSystemModulePropertyManager.getInstance(module).getLinkedProjectPath();
      if (moduleFilePath != null && (toSystemIndependentName(moduleFilePath).endsWith("/" + BUILD_SRC_FOLDER_NAME))) {
        // "buildSrc" is a special case handled automatically by Gradle.
        continue;
      }

      Set<String> moduleTasks = new LinkedHashSet<>();
      Pair<Module,String> moduleAndGradleProjectPath = findModuleAndGradleProjectPath(module);
      if (moduleAndGradleProjectPath != null) {
        module = moduleAndGradleProjectPath.getFirst();
        String gradlePath = moduleAndGradleProjectPath.getSecond();
        findAndAddGradleBuildTasks(module, gradlePath, buildMode, moduleTasks, testCompileType);
        GradleProjectPath gradleProjectPathCore = getGradleProjectPath(module);
        if (gradleProjectPathCore == null) continue;
        Path keyPath = getBuildRootDir(gradleProjectPathCore).toPath();
        if (buildMode == REBUILD && !moduleTasks.isEmpty()) {
          // Clean only if other tasks are needed
          cleanTasks.put(keyPath, createFullTaskName(gradlePath, CLEAN_TASK_NAME));
        }

        // Remove duplicates and prepend moduleTasks to tasks.
        // TODO(xof): investigate whether this effective reversal is necessary for or neutral regarding correctness.
        moduleTasks.addAll(tasks.get(keyPath));
        tasks.removeAll(keyPath);
        tasks.putAll(keyPath, moduleTasks);
      }
    }
    ArrayListMultimap<Path, String> result = ArrayListMultimap.create();

    for (Path key : cleanTasks.keySet()) {
      List<String> keyTasks = new ArrayList<>(cleanTasks.get(key));
      // We effectively reversed the per-module tasks, other than clean, above; reverse the clean tasks here.
      Collections.reverse(keyTasks);
      result.putAll(key, keyTasks);
    }
    for (Map.Entry<Path, String> entry : tasks.entries()) {
      result.put(entry.getKey(), entry.getValue());
    }

    return result;
  }

  private static @Nullable Pair<Module,String> findModuleAndGradleProjectPath(@NotNull Module module) {
    GradleFacet gradleFacet = GradleFacet.getInstance(module);
    // TODO(b/203237539)
    if (gradleFacet == null) {
      int lastIndexOfDot = module.getName().lastIndexOf(".");
      if (lastIndexOfDot > 0) {
        String parentModuleName = module.getName().substring(0, lastIndexOfDot);
        Module parentModule = ModuleManager.getInstance(module.getProject()).findModuleByName(parentModuleName);
        if (parentModule != null) {
          gradleFacet = GradleFacet.getInstance(parentModule);
          module = parentModule;
        }
      }
      if (gradleFacet == null) {
        return null;
      }
    }

    String gradlePath = gradleFacet.getConfiguration().GRADLE_PROJECT_PATH;
    if (isEmpty(gradlePath)) {
      // Gradle project path is never, ever null. If the path is empty, it shows as ":". We had reports of this happening. It is likely that
      // users manually added the Android-Gradle facet to a project. After all it is likely not to be a Gradle module. Better quit and not
      // build the module.
      String msg = String.format("Module '%1$s' does not have a Gradle path. It is likely that this module was manually added by the user.",
                                 module.getName());
      getLogger().info(msg);
      return null;
    }

    return Pair.of(module, gradlePath);
  }

  private static void findAndAddGradleBuildTasks(@NotNull Module module,
                                                 @NotNull String gradlePath,
                                                 @NotNull BuildMode buildMode,
                                                 @NotNull Set<String> tasks,
                                                 @NotNull TestCompileType testCompileType) {
    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    if (androidFacet != null) {
      AndroidFacetProperties properties = androidFacet.getProperties();

      GradleAndroidModel androidModel = GradleAndroidModel.get(module);

      switch (buildMode) {
        case CLEAN: // Intentional fall-through.
        case SOURCE_GEN:
          addAfterSyncTasks(tasks, gradlePath, properties);
          if (androidModel != null) {
            addAfterSyncTasksForTestArtifacts(tasks, gradlePath, testCompileType, androidModel);
          }
          break;
        case ASSEMBLE:
        case REBUILD:
          addTaskIfSpecified(tasks, gradlePath, properties.ASSEMBLE_TASK_NAME);

          // Add assemble tasks for tests.
          if (testCompileType != TestCompileType.ALL) {
            if (androidModel != null) {
              for (IdeBaseArtifact artifact : getArtifacts(testCompileType, androidModel.getSelectedVariant())) {
                addTaskIfSpecified(tasks, gradlePath, artifact.getAssembleTaskName());
              }
            }
          }

          // Add assemble tasks for tested variants in test-only modules
          addAssembleTasksForTargetVariants(tasks, module);

          break;
        case BUNDLE:
          // The "Bundle" task is only valid for base (app) module, not for features, libraries, etc.
          if (androidModel != null && androidModel.getAndroidProject().getProjectType() == IdeAndroidProjectType.PROJECT_TYPE_APP) {
            String taskName = androidModel.getSelectedVariant().getMainArtifact().getBuildInformation().getBundleTaskName();
            addTaskIfSpecified(tasks, gradlePath, taskName);
          }
          break;
        case APK_FROM_BUNDLE:
          // The "ApkFromBundle" task is only valid for base (app) module, and for features if it's for instrumented tests
          if (androidModel != null && androidModel.getAndroidProject().getProjectType() == IdeAndroidProjectType.PROJECT_TYPE_APP) {
            String taskName = androidModel.getSelectedVariant().getMainArtifact().getBuildInformation().getApkFromBundleTaskName();
            addTaskIfSpecified(tasks, gradlePath, taskName);
          }
          else if (androidModel != null &&
                   androidModel.getAndroidProject().getProjectType() == IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE) {
            // Instrumented test support for Dynamic Features: Add assembleDebugAndroidTest tasks
            if (testCompileType == TestCompileType.ANDROID_TESTS) {
              for (IdeBaseArtifact artifact : getArtifacts(testCompileType, androidModel.getSelectedVariant())) {
                addTaskIfSpecified(tasks, gradlePath, artifact.getAssembleTaskName());
              }
            }
          }
          break;
        default:
          addAfterSyncTasks(tasks, gradlePath, properties);
          if (androidModel != null) {
            addAfterSyncTasksForTestArtifacts(tasks, gradlePath, testCompileType, androidModel);
            for (IdeBaseArtifact artifact : getArtifacts(testCompileType, androidModel.getSelectedVariant())) {
              addTaskIfSpecified(tasks, gradlePath, artifact.getCompileTaskName());
            }
          }
          // When compiling for unit tests, run only COMPILE_JAVA_TEST_TASK_NAME, which will run javac over main and test code. If the
          // Jack compiler is enabled in Gradle, COMPILE_JAVA_TASK_NAME will end up running e.g. compileDebugJavaWithJack, which produces
          // no *.class files and would be just a waste of time.
          if (testCompileType != TestCompileType.UNIT_TESTS) {
            addTaskIfSpecified(tasks, gradlePath, properties.COMPILE_JAVA_TASK_NAME);
          }

          // Add compile tasks for tests.
          break;
      }
    }
    else {
      JavaFacet javaFacet = JavaFacet.getInstance(module);
      if (javaFacet != null && javaFacet.getConfiguration().BUILDABLE) {
        String gradleTaskName = javaFacet.getGradleTaskName(buildMode);
        if (gradleTaskName != null) {
          tasks.add(createFullTaskName(gradlePath, gradleTaskName));
        }
        if (TestCompileType.UNIT_TESTS.equals(testCompileType) || TestCompileType.ALL.equals(testCompileType)) {
          tasks.add(createFullTaskName(gradlePath, JavaFacet.TEST_CLASSES_TASK_NAME));
        }
      }
    }
  }

  private static void addAssembleTasksForTargetVariants(@NotNull Set<String> tasks, @NotNull Module testOnlyModule) {
    GradleAndroidModel testAndroidModel = GradleAndroidModel.get(testOnlyModule);

    if (testAndroidModel == null ||
        testAndroidModel.getAndroidProject().getProjectType() != IdeAndroidProjectType.PROJECT_TYPE_TEST) {
      // If we don't have the target module and variant to be tested, no task should be added.
      return;
    }

    for (IdeTestedTargetVariant testedTargetVariant : testAndroidModel.getSelectedVariant().getTestedTargetVariants()) {
      String targetProjectGradlePath = testedTargetVariant.getTargetProjectPath();
      Module targetModule = findModuleByGradlePath(testOnlyModule.getProject(), targetProjectGradlePath);

      // Adds the assemble task for the tested variants
      if (targetModule != null) {
        GradleAndroidModel targetAndroidModel = GradleAndroidModel.get(targetModule);

        if (targetAndroidModel != null) {
          String targetVariantName = testedTargetVariant.getTargetVariant();
          IdeVariant targetVariant = targetAndroidModel.findVariantByName(targetVariantName);

          if (targetVariant != null) {
            addTaskIfSpecified(tasks, targetProjectGradlePath, targetVariant.getMainArtifact().getAssembleTaskName());
          }
        }
      }
    }
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(GradleTaskFinder.class);
  }

  private static void addAfterSyncTasksForTestArtifacts(@NotNull Set<String> tasks,
                                                        @NotNull String gradlePath,
                                                        @NotNull TestCompileType testCompileType,
                                                        @NotNull GradleAndroidModel androidModel) {
    IdeVariant variant = androidModel.getSelectedVariant();
    Collection<IdeBaseArtifact> testArtifacts = getArtifacts(testCompileType, variant);
    for (IdeBaseArtifact artifact : testArtifacts) {
      for (String taskName : artifact.getIdeSetupTaskNames()) {
        addTaskIfSpecified(tasks, gradlePath, taskName);
      }
    }
  }

  private static void addAfterSyncTasks(@NotNull Set<String> tasks,
                                        @NotNull String gradlePath,
                                        @NotNull AndroidFacetProperties properties) {
    // Make sure all the generated sources, unpacked aars and mockable jars are in place. They are usually up to date, since we
    // generate them at sync time, so Gradle will just skip those tasks. The generated files can be missing if this is a "Rebuild
    // Project" run or if the user cleaned the project from the command line. The mockable jar is necessary to run unit tests, but the
    // compilation tasks don't depend on it, so we have to call it explicitly.
    for (String taskName : properties.AFTER_SYNC_TASK_NAMES) {
      addTaskIfSpecified(tasks, gradlePath, taskName);
    }
  }

  private static void addTaskIfSpecified(@NotNull Set<String> tasks, @NotNull String gradlePath, @Nullable String gradleTaskName) {
    if (isNotEmpty(gradleTaskName)) {
      String buildTask = createFullTaskName(gradlePath, gradleTaskName);
      tasks.add(buildTask);
    }
  }

  private static final int MAX_MODULES_TO_INCLUDE_IN_LOG_MESSAGE = 50;
  private static final int MAX_MODULES_TO_SHOW_IN_NOTIFICATION = 5;

  private void notifyNoTaskFound(@NotNull Module[] modules, @NotNull BuildMode mode, @NotNull TestCompileType type) {
    if (modules.length == 0) return;
    Project project = modules[0].getProject();

    String logModuleNames =
      Arrays.stream(modules)
        .limit(MAX_MODULES_TO_INCLUDE_IN_LOG_MESSAGE)
        .map(GradleProjects::getGradleModulePath)
        .filter(Objects::nonNull)
        .collect(Collectors.joining(", "))
      + (modules.length > MAX_MODULES_TO_INCLUDE_IN_LOG_MESSAGE ? "..." : "");
    String logMessage =
      String.format("Unable to find Gradle tasks to build: [%s]. Build mode: %s. Tests: %s.", logModuleNames, mode, type.getDisplayName());
    getLogger().warn(logMessage);

    String moduleNames =
      Arrays.stream(modules)
        .limit(MAX_MODULES_TO_SHOW_IN_NOTIFICATION)
        .map(GradleProjects::getGradleModulePath)
        .filter(Objects::nonNull)
        .collect(Collectors.joining(", "))
      + (modules.length > 5 ? "..." : "");
    String message =
      String.format("Unable to find Gradle tasks to build: [%s]. <br>Build mode: %s. <br>Tests: %s.", moduleNames, mode, type.getDisplayName());
    NotificationGroupManager.getInstance()
      .getNotificationGroup("Android Gradle Tasks")
      .createNotification(message, NotificationType.WARNING)
      .notify(project);
  }
}
