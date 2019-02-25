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
package com.android.tools.idea.databinding;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BrClassFinder extends PsiElementFinder {
  private final DataBindingProjectComponent myComponent;
  private CachedValue<Map<String, PsiClass>> myClassByPackageCache;
  public BrClassFinder(DataBindingProjectComponent component) {
    myComponent = component;
    myClassByPackageCache = CachedValuesManager.getManager(component.getProject()).createCachedValue(
      () -> {
        Map<String, PsiClass> classes = new HashMap<>();
        for (AndroidFacet facet : myComponent.getDataBindingEnabledFacets()) {
          if (ModuleDataBinding.getInstance(facet).isEnabled()) {
            classes
              .put(DataBindingUtil.getBrQualifiedName(facet), DataBindingClassFactory.getOrCreateBrClassFor(facet));
          }
        }
        return CachedValueProvider.Result.create(classes, myComponent);
      }, false);
  }

  @Nullable
  @Override
  public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    if (!isEnabled() || !qualifiedName.endsWith(DataBindingUtil.BR)) {
      return null;
    }
    PsiClass psiClass = myClassByPackageCache.getValue().get(qualifiedName);
    if (psiClass == null || psiClass.getProject() != scope.getProject()) {
      return null;
    }
    return psiClass;
  }

  @NotNull
  @Override
  public PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    if (!isEnabled()) {
      return PsiClass.EMPTY_ARRAY;
    }
    PsiClass aClass = findClass(qualifiedName, scope);
    if (aClass == null) {
      return PsiClass.EMPTY_ARRAY;
    }
    return new PsiClass[]{aClass};
  }

  @Nullable
  @Override
  public PsiPackage findPackage(@NotNull String qualifiedName) {
    return null;
  }

  private boolean isEnabled() {
    return DataBindingCodeGenService.getInstance().isCodeGenSetToInMemoryFor(myComponent);
  }
}
