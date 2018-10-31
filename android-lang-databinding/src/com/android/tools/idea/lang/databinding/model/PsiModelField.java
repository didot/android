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
package com.android.tools.idea.lang.databinding.model;

import android.databinding.tool.BindableCompat;
import android.databinding.tool.reflection.ModelClass;
import android.databinding.tool.reflection.ModelField;
import com.android.tools.idea.databinding.DataBindingMode;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;

public class PsiModelField extends ModelField {
  @NotNull PsiField myPsiField;

  private static final BindableCompat BINDABLE_COMPAT = new BindableCompat(new String[0]);

  public PsiModelField(@NotNull PsiField psiField) {
    myPsiField = psiField;
  }

  @NotNull
  public PsiField getPsiField() {
    return myPsiField;
  }

  @Override
  public BindableCompat getBindableAnnotation() {
    PsiModifierList modifierList = myPsiField.getModifierList();
    if (modifierList != null) {
      PsiAnnotation[] annotations = modifierList.getAnnotations();
      for (PsiAnnotation annotation : annotations) {
        if (DataBindingMode.SUPPORT.bindable.equals(annotation.getQualifiedName()) ||
            DataBindingMode.ANDROIDX.bindable.equals(annotation.getQualifiedName())) {
          // we don't care about dependencies in studio so we can return a shared instance.
          return BINDABLE_COMPAT;
        }
      }
    }
    return null;
  }

  @Override
  public String getName() {
    return myPsiField.getName();
  }

  @Override
  public boolean isPublic() {
    return myPsiField.hasModifierProperty(PsiModifier.PUBLIC);
  }

  @Override
  public boolean isStatic() {
    return myPsiField.hasModifierProperty(PsiModifier.STATIC);
  }

  @Override
  public boolean isFinal() {
    return myPsiField.hasModifierProperty(PsiModifier.FINAL);
  }

  @Override
  public ModelClass getFieldType() {
    return new PsiModelClass(myPsiField.getType());
  }
}
