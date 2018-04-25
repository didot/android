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
package com.android.tools.idea.gradle.dsl.model;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel;
import com.android.tools.idea.gradle.dsl.parser.BuildModelContext;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import com.android.tools.idea.gradle.dsl.parser.files.GradleBuildFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleSettingsFile;
import com.android.tools.idea.gradle.dsl.parser.settings.ProjectPropertiesDslElement;
import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.dsl.parser.settings.ProjectPropertiesDslElement.BUILD_FILE_NAME;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleSettingsFile;
import static com.intellij.openapi.util.io.FileUtil.filesEqual;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

public class GradleSettingsModelImpl extends GradleFileModelImpl implements GradleSettingsModel {
  public static final String INCLUDE = "include";

  @Nullable
  public static GradleSettingsModel get(@NotNull Project project) {
    VirtualFile file = getGradleSettingsFile(getBaseDirPath(project));
    return file != null ? parseBuildFile(file, project, "settings") : null;
  }

  @NotNull
  public static GradleSettingsModel parseBuildFile(@NotNull VirtualFile file, @NotNull Project project, @NotNull String moduleName) {
    GradleSettingsFile settingsFile = new GradleSettingsFile(file, project, moduleName, BuildModelContext.create(project));
    settingsFile.parse();
    return new GradleSettingsModelImpl(settingsFile);
  }

  public GradleSettingsModelImpl(@NotNull GradleSettingsFile parsedModel) {
    super(parsedModel);
  }

  /**
   * Returns the module paths specified by the include statements. Note that these path are not file paths, but instead specify the
   * location of the modules in the project hierarchy. As such, the paths use the ':' character as separator.
   *
   * <p>For example, the path a:b or :a:b represents the module in the directory $projectDir/a/b.
   */
  @NotNull
  @Override
  public List<String> modulePaths() {
    List<String> result = Lists.newArrayList();
    result.add(":"); // Indicates the root module.

    GradleDslExpressionList includePaths = myGradleDslFile.getPropertyElement(INCLUDE, GradleDslExpressionList.class);
    if (includePaths == null) {
      return result;
    }

    for (GradleDslSimpleExpression includePath : includePaths.getSimpleExpressions()) {
      String value = includePath.getValue(String.class);
      if (value != null) {
        result.add(standardiseModulePath(value));
      }
    }
    return result;
  }

  @Override
  public void addModulePath(@NotNull String modulePath) {
    modulePath = standardiseModulePath(modulePath);
    myGradleDslFile.addToNewLiteralList(INCLUDE, modulePath);
  }

  @Override
  public void removeModulePath(@NotNull String modulePath) {
    // Try to remove the module path whether it has ":" prefix or not.
    if (!modulePath.startsWith(":")) {
      myGradleDslFile.removeFromExpressionList(INCLUDE, ":" + modulePath);
    }
    myGradleDslFile.removeFromExpressionList(INCLUDE, modulePath);
  }

  @Override
  public void replaceModulePath(@NotNull String oldModulePath, @NotNull String newModulePath) {
    // Try to replace the module path whether it has ":" prefix or not.
    if (!newModulePath.startsWith(":")) {
      newModulePath = ":" + newModulePath;
    }
    if (!oldModulePath.startsWith(":")) {
      myGradleDslFile.replaceInExpressionList(INCLUDE, ":" + oldModulePath, newModulePath);
    }
    myGradleDslFile.replaceInExpressionList(INCLUDE, oldModulePath, newModulePath);
  }

  @Nullable
  @Override
  public File moduleDirectory(String modulePath) {
    modulePath = standardiseModulePath(modulePath);

    if (!modulePaths().contains(modulePath)) {
      return null;
    }

    File rootDirPath = getBaseDirPath(myGradleDslFile.getProject());
    if (modulePath.equals(":")) {
      return rootDirPath;
    }

    String projectKey = "project('" + modulePath + "')";
    ProjectPropertiesDslElement projectProperties = myGradleDslFile.getPropertyElement(projectKey, ProjectPropertiesDslElement.class);
    if (projectProperties != null) {
      File projectDir = projectProperties.projectDir();
      if (projectDir != null) {
        return projectDir;
      }
    }

    File parentDir;
    if (modulePath.lastIndexOf(':') == 0) {
      parentDir = rootDirPath;
    }
    else {
      String parentModule = parentModule(modulePath);
      if (parentModule == null) {
        return null;
      }
      parentDir = moduleDirectory(parentModule);
    }
    String moduleName = modulePath.substring(modulePath.lastIndexOf(':') + 1);
    return new File(parentDir, moduleName);
  }

  @Nullable
  @Override
  public String moduleWithDirectory(@NotNull File moduleDir) {
    for (String modulePath : modulePaths()) {
      if (filesEqual(moduleDir, moduleDirectory(modulePath))) {
        return modulePath;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public GradleBuildModel moduleModel(@NotNull String modulePath) {
    File buildFilePath = buildFile(modulePath);
    if (buildFilePath == null) {
      return null;
    }
    VirtualFile buildFile = findFileByIoFile(buildFilePath, true);
    if (buildFile == null) {
      return null;
    }
    GradleBuildFile dslFile =
      myGradleDslFile.getContext().getOrCreateBuildFile(buildFile, modulePath.substring(modulePath.lastIndexOf(':') + 1));
    return new GradleBuildModelImpl(dslFile);
  }

  @Nullable
  @Override
  public String parentModule(@NotNull String modulePath) {
    modulePath = standardiseModulePath(modulePath);

    List<String> allModulePaths = modulePaths();
    if (!allModulePaths.contains(modulePath)) {
      return null;
    }

    if (modulePath.equals(":")) {
      return null;
    }

    int lastPathElementIndex = modulePath.lastIndexOf(':');
    String parentModulePath = lastPathElementIndex == 0 ? ":" : modulePath.substring(0, lastPathElementIndex);

    if (allModulePaths.contains(parentModulePath)) {
      return parentModulePath;
    }
    return null;
  }

  @Nullable
  @Override
  public GradleBuildModel getParentModuleModel(@NotNull String modulePath) {
    String parentModule = parentModule(modulePath);
    if (parentModule == null) {
      return null;
    }
    return moduleModel(parentModule);
  }

  @Nullable
  @Override
  public File buildFile(@NotNull String modulePath) {
    File moduleDirectory = moduleDirectory(modulePath);
    if (moduleDirectory == null) {
      return null;
    }

    String buildFileName = null;
    String projectKey = "project('" + modulePath + "')";
    ProjectPropertiesDslElement projectProperties = myGradleDslFile.getPropertyElement(projectKey, ProjectPropertiesDslElement.class);
    if (projectProperties != null) {
      buildFileName =  projectProperties.getLiteral(BUILD_FILE_NAME, String.class);
    }

    if (buildFileName == null) {
      buildFileName = FN_BUILD_GRADLE;
    }

    return new File(moduleDirectory, buildFileName);
  }

  private static String standardiseModulePath(@NotNull String modulePath) {
    return modulePath.startsWith(":") ? modulePath : ":" + modulePath;
  }
}
