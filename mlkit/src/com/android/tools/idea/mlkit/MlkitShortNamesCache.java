/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.mlkit;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.mlkit.lightpsi.LightModelClass;
import com.google.common.base.Strings;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FileBasedIndex;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Used by code completion for unqualified class names and for suggesting imports.
 */
public class MlkitShortNamesCache extends PsiShortNamesCache {
  private final Project myProject;
  private final Key<String> ML_CLASS_NAME_KEY = Key.create("ML_CLASS_NAME_KEY");

  public MlkitShortNamesCache(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public PsiClass[] getClassesByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    if (StudioFlags.ML_MODEL_BINDING.get()) {
      List<PsiClass> lightClassList = new ArrayList<>();
      Map<VirtualFile, MlModelMetadata> modelFileMap = MlkitUtils.getAllModelFileMapFromIndex(myProject, scope);

      PsiClass[] lightClasses = MlkitUtils.getLightModelClasses(myProject, modelFileMap);
      for (PsiClass lightClass : lightClasses) {
        if (name.equals(lightClass.getName())) {
          lightClassList.add(lightClass);
        }

        for (PsiClass innerClass : lightClass.getInnerClasses()) {
          if (name.equals(innerClass.getName())) {
            lightClassList.add(innerClass);
          }
        }
      }

      return lightClassList.toArray(PsiClass.EMPTY_ARRAY);
    }

    return PsiClass.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public String[] getAllClassNames() {
    if (StudioFlags.ML_MODEL_BINDING.get()) {
      List<String> classNameList = new ArrayList<>();
      FileBasedIndex index = FileBasedIndex.getInstance();
      index.processAllKeys(MlModelFileIndex.INDEX_ID, key -> {
        index.processValues(MlModelFileIndex.INDEX_ID, key, null, (file, value) -> {
          Module module = ModuleUtilCore.findModuleForFile(file, myProject);
          if (module != null && MlkitUtils.isMlModelBindingBuildFeatureEnabled(module)) {
            String className = file.getUserData(ML_CLASS_NAME_KEY);
            if (Strings.isNullOrEmpty(className)) {
              className = MlkitUtils.computeModelClassName(module, file);
              file.putUserData(ML_CLASS_NAME_KEY, className);
            }
            classNameList.add(className);
            return false;
          }
          return true;
        }, GlobalSearchScope.projectScope(myProject));

        return true;
      }, GlobalSearchScope.projectScope(myProject), null);

      if (!classNameList.isEmpty()) {
        classNameList.addAll(LightModelClass.getInnerClassNames());
      }

      return ArrayUtil.toStringArray(classNameList);
    }

    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @NotNull
  @Override
  public PsiMethod[] getMethodsByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    //TODO(jackqdyulei): implement it to return correct methods.
    return PsiMethod.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public PsiMethod[] getMethodsByNameIfNotMoreThan(@NotNull String name, @NotNull GlobalSearchScope scope, int maxCount) {
    return PsiMethod.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public PsiField[] getFieldsByNameIfNotMoreThan(@NotNull String name, @NotNull GlobalSearchScope scope, int maxCount) {
    return PsiField.EMPTY_ARRAY;
  }

  @Override
  public boolean processMethodsWithName(@NotNull String name, @NotNull GlobalSearchScope scope, @NotNull Processor<PsiMethod> processor) {
    return false;
  }

  @NotNull
  @Override
  public String[] getAllMethodNames() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @NotNull
  @Override
  public PsiField[] getFieldsByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    return PsiField.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public String[] getAllFieldNames() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }
}
