/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.importing;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.intellij.openapi.module.StdModuleTypes.JAVA;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.util.PathUtil.toSystemIndependentName;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.sdk.IdeSdks;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ExternalProjectSystemRegistry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemIndependent;

class TopLevelModuleFactory {
  private static final Logger LOG = Logger.getInstance(TopLevelModuleFactory.class);
  @NotNull private final IdeInfo myIdeInfo;
  @NotNull private final IdeSdks myIdeSdks;

  TopLevelModuleFactory(@NotNull IdeInfo ideInfo, @NotNull IdeSdks ideSdks) {
    myIdeInfo = ideInfo;
    myIdeSdks = ideSdks;
  }

  void createTopLevelModule(@NotNull Project project) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);

    File projectRootDir = getBaseDirPath(project);
    VirtualFile contentRoot = findFileByIoFile(projectRootDir, true);

    if (contentRoot != null) {
      File moduleFile = new File(
        new File(new File(projectRootDir, Project.DIRECTORY_STORE_FOLDER), "modules"), // "modules" is private in GradleManager.
        projectRootDir.getName() + ".iml");
      ModifiableModuleModel projectModifieableModel = moduleManager.getModifiableModel();
      Module module = projectModifieableModel.newModule(moduleFile.getPath(), JAVA.getId());
      try {
        // A top level module name is usually the same as the name of the project it is contained in. If the caller of this method sets
        // up the project name correctly, we can prevent the root mdule from being disposed by sync if we configure its name correctly.
        // NOTE: We do not expect the project name to always be correct (i.e. match the name configured by Gradle at this point) and
        //       therefore it is still possible that the module created here will be disposed and re-created by sync.
        if (!module.getName().equals(project.getName())) {
          projectModifieableModel.renameModule(module, project.getName());
          projectModifieableModel.setModuleGroupPath(module, new String[]{project.getName()});
        }
      }
      catch (ModuleWithNameAlreadyExists ex) {
        // The top module only plays a temporary role while project is not properly synced. Ignore any errors and let sync corrent
        // the problem.
        LOG.warn(String.format("Failed to rename module '%s' to '%s'", module.getName(), project.getName()), ex);
      }
      projectModifieableModel.commit();
      @SystemIndependent String projectRootDirPath = toSystemIndependentName(projectRootDir.getPath());
      ExternalSystemModulePropertyManager
        .getInstance(module)
        .setExternalOptions(
          GRADLE_SYSTEM_ID,
          new ModuleData(":", GRADLE_SYSTEM_ID, JAVA.getId(), projectRootDir.getName(), projectRootDirPath,
                         projectRootDirPath),
          new ProjectData(GRADLE_SYSTEM_ID, project.getName(), project.getBasePath(), project.getBasePath()));

      ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
      model.addContentEntry(contentRoot);
      if (myIdeInfo.isAndroidStudio()) {
        // If sync fails, make sure that the project has a JDK, otherwise Groovy indices won't work (a common scenario where
        // users will update build.gradle files to fix Gradle sync.)
        // See: https://code.google.com/p/android/issues/detail?id=194621
        model.inheritSdk();
      }
      model.commit();

      FacetManager facetManager = FacetManager.getInstance(module);
      ModifiableFacetModel facetModel = facetManager.createModifiableModel();
      try {
        GradleFacet gradleFacet = GradleFacet.getInstance(module);
        if (gradleFacet == null) {
          // Add "gradle" facet, to avoid balloons about unsupported compilation of modules.
          gradleFacet = facetManager.createFacet(GradleFacet.getFacetType(), GradleFacet.getFacetName(), null);
          facetModel.addFacet(gradleFacet, ExternalProjectSystemRegistry.getInstance().getExternalSource(module));
        }
        gradleFacet.getConfiguration().GRADLE_PROJECT_PATH = GRADLE_PATH_SEPARATOR;
      }
      finally {
        facetModel.commit();
      }
    }
  }
}
