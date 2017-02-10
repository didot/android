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
package com.android.tools.idea.profilers.stacktrace;

import com.android.tools.idea.actions.PsiClassNavigation;
import com.android.tools.profilers.common.CodeLocation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.StubVirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class IntelliJStackNavigation implements StackNavigation {
  private static final VirtualFile UNRESOLVED_CLASS_FILE = new StubVirtualFile();

  @NotNull private final Project myProject;
  @NotNull private final CodeLocation myCodeLocation;
  @NotNull private String myPackageName;
  @NotNull private String mySimpleClassName;

  @Nullable private VirtualFile myCachedClassFile = UNRESOLVED_CLASS_FILE;

  IntelliJStackNavigation(@NotNull Project project, @NotNull CodeLocation codeLocation) {
    myProject = project;
    myCodeLocation = codeLocation;

    String className = myCodeLocation.getClassName();
    if (className == null) {
      myPackageName = UNKNOWN_PACKAGE;
      mySimpleClassName = UNKONWN_CLASS;
    }
    else {
      int dot = className.lastIndexOf('.');
      myPackageName = dot <= 0 ? NO_PACKAGE : className.substring(0, dot);
      mySimpleClassName = dot + 1 < className.length() ? className.substring(dot + 1) : UNKONWN_CLASS;
    }
  }

  @Override
  @NotNull
  public CodeLocation getCodeLocation() {
    return myCodeLocation;
  }

  @Override
  public void navigate(@Nullable Runnable preNavigate) {
    Navigatable[] navigatables;
    if (myCodeLocation.getLineNumber() > 0) {
      navigatables =
        PsiClassNavigation.getNavigationForClass(myProject, preNavigate, myCodeLocation.getClassName(), myCodeLocation.getLineNumber());
    }
    else {
      navigatables = PsiClassNavigation.getNavigationForClass(myProject, preNavigate, myCodeLocation.getClassName());
    }

    if (navigatables == null) {
      return;
    }

    for (Navigatable navigatable : navigatables) {
      if (navigatable.canNavigate()) {
        navigatable.navigate(false);
      }
    }
  }

  @Override
  @NotNull
  public String getPackageName() {
    return myPackageName;
  }

  @Override
  @NotNull
  public String getSimpleClassName() {
    return mySimpleClassName;
  }

  @Override
  @NotNull
  public String getMethodName() {
    return myCodeLocation.getMethodName() == null ? UNKNOWN_METHOD : myCodeLocation.getMethodName();
  }

  @Override
  public boolean isInContext() {
    VirtualFile classFile = findClassFile();
    return classFile != null && ProjectFileIndex.SERVICE.getInstance(myProject).isInSource(classFile);
  }

  @Nullable
  VirtualFile findClassFile() {
    //noinspection UseVirtualFileEquals
    if (myCachedClassFile != UNRESOLVED_CLASS_FILE) {
      return myCachedClassFile;
    }

    String className = myCodeLocation.getClassName();
    if (className == null) {
      myCachedClassFile = null;
      return null;
    }

    // JavaPsiFacade can't deal with inner classes, so we'll need to strip the class name down to just the outer class name.
    int innerClassSymboldIndex = className.indexOf('$');
    if (innerClassSymboldIndex > 0) {
      className = className.substring(0, innerClassSymboldIndex);
    }

    PsiClass psiClass = JavaPsiFacade.getInstance(myProject).findClass(className, GlobalSearchScope.allScope(myProject));
    if (psiClass == null) {
      myCachedClassFile = null;
      return null;
    }
    myCachedClassFile = psiClass.getContainingFile().getVirtualFile();
    return myCachedClassFile;
  }
}
