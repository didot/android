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
package com.android.tools.idea.gradle.quickfix;

import com.android.tools.idea.gradle.dsl.parser.NewExternalDependency;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.search.GlobalSearchScope.moduleWithLibrariesScope;

public class AddGradleJetbrainsAnnotationFix extends AbstractGradleDependencyFix {
  @NotNull private final String myClassName;

  public AddGradleJetbrainsAnnotationFix(@NotNull Module module, @NotNull PsiReference reference, @NotNull String className) {
    super(module, reference);
    myClassName = className;
  }

  @Override
  @NotNull
  public String getText() {
    return "Add library 'org.jetbrains:annotations:13.0' to classpath";
  }

  @Override
  public void invoke(@NotNull final Project project, @Nullable final Editor editor, @Nullable PsiFile file) {
    boolean testScope = isTestScope(myModule, myReference);
    String configurationName = getConfigurationName(myModule, testScope);

    NewExternalDependency newDependency = new NewExternalDependency(configurationName, "org.jetbrains", "annotations", "13.0");

    addDependencyAndSync(newDependency, new Computable<PsiClass[]>() {
      @Override
      public PsiClass[] compute() {
        PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(myClassName, moduleWithLibrariesScope(myModule));
        return aClass != null ? new PsiClass[]{aClass} : null;
      }
    }, editor);
  }
}
