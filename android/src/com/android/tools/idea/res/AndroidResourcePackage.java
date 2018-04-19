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
package com.android.tools.idea.res;

import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.file.PsiPackageImpl;

/** A generated stub PsiPackage for generated R classes. */
public class AndroidResourcePackage extends PsiPackageImpl {
  public AndroidResourcePackage(PsiManager manager, String qualifiedName) {
    super(manager, qualifiedName);
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public boolean canNavigate() {
    return false;
  }

  @Override
  public String toString() {
    return "AndroidResourcePackage: " + getQualifiedName();
  }
}
