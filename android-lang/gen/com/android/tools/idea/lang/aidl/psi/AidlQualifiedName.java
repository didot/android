/*
 * Copyright (C) 2022 The Android Open Source Project
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

// ATTENTION: This file has been automatically generated from Aidl.bnf. Do not edit it manually.

package com.android.tools.idea.lang.aidl.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;

public interface AidlQualifiedName extends AidlNamedElement {

  @NotNull
  List<AidlNameComponent> getNameComponentList();

  @NotNull
  PsiElement getNameIdentifier();

  @NotNull
  String getQualifiedName();

  @Nullable
  PsiElement resolve();

  @NotNull
  PsiReference getReference();

}
