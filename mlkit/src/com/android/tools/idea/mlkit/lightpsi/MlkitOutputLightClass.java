/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.mlkit.lightpsi;

import com.android.tools.idea.mlkit.MlkitModuleService;
import com.android.tools.idea.psi.NullabilityUtils;
import com.android.tools.idea.psi.light.NullabilityLightMethodBuilder;
import com.android.tools.mlkit.MlkitNames;
import com.android.tools.mlkit.TensorInfo;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import java.util.List;
import org.jetbrains.android.augment.AndroidLightClassBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Output class for output tensors. For each tensor it has a private field to store data and a getter method.
 *
 * @see LightModelClass
 */
public class MlkitOutputLightClass extends AndroidLightClassBase {
  private final PsiClass containingClass;
  private final String qualifiedName;
  private final CachedValue<PsiMethod[]> myMethodCache;

  public MlkitOutputLightClass(@NotNull Module module, @NotNull List<TensorInfo> tensorInfos, @NotNull PsiClass containingClass) {
    super(PsiManager.getInstance(module.getProject()),
          ImmutableSet.of(PsiModifier.PUBLIC, PsiModifier.STATIC, PsiModifier.FINAL));
    this.qualifiedName = String.join(".", containingClass.getQualifiedName(), MlkitNames.OUTPUTS);
    this.containingClass = containingClass;

    setModuleInfo(module, false);

    // Cache getter methods for output class
    ModificationTracker modificationTracker = MlkitModuleService.getInstance(module).getModelFileModificationTracker();
    myMethodCache = CachedValuesManager.getManager(getProject()).createCachedValue(
      () -> {
        PsiMethod[] methods = new PsiMethod[tensorInfos.size()];
        for (int i = 0; i < methods.length; i++) {
          methods[i] = buildGetterMethod(tensorInfos.get(i));
        }

        return CachedValueProvider.Result.create(methods, modificationTracker);
      }
      , false);
  }

  @Nullable
  @Override
  public String getQualifiedName() {
    return qualifiedName;
  }

  @Override
  public String getName() {
    return MlkitNames.OUTPUTS;
  }

  @NotNull
  @Override
  public PsiMethod[] getMethods() {
    return myMethodCache.getValue();
  }

  private PsiMethod buildGetterMethod(TensorInfo tensorInfo) {
    Project project = getProject();
    GlobalSearchScope scope = getResolveScope();
    PsiClassType returnType = PsiType.getTypeByName(CodeUtils.getTypeQualifiedName(tensorInfo), project, scope);
    LightMethodBuilder method =
      new NullabilityLightMethodBuilder(myManager, MlkitNames.formatGetterName(tensorInfo.getIdentifierName(), returnType.getClassName()))
        .setMethodReturnType(returnType, true)
        .addModifiers(PsiModifier.PUBLIC, PsiModifier.FINAL)
        .setContainingClass(this);
    method.setNavigationElement(this);
    return method;
  }

  @Nullable
  @Override
  public PsiClass getContainingClass() {
    return containingClass;
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return containingClass.getNavigationElement();
  }
}
