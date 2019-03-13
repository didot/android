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
package com.android.tools.idea.res;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import java.util.Collection;
import org.jetbrains.android.augment.AndroidLightClassBase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for light classes that only contain inner classes, like {@code R} or {@code Manifest}.
 */
public abstract class AndroidClassWithOnlyInnerClassesBase extends AndroidLightClassBase {
  private static final Logger LOG = Logger.getInstance(AndroidClassWithOnlyInnerClassesBase.class);

  @NotNull protected final CachedValue<PsiClass[]> myClassCache;
  @NotNull protected final String myShortName;
  @NotNull protected final PsiJavaFile myFile;

  public AndroidClassWithOnlyInnerClassesBase(@NotNull String shortName,
                                              @Nullable String packageName,
                                              @NotNull PsiManager psiManager,
                                              @NotNull Collection<String> modifiers) {
    super(psiManager, modifiers);
    Project project = getProject();

    myShortName = shortName;

    myClassCache =
      CachedValuesManager.getManager(project).createCachedValue(() -> {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Recomputing inner classes of " + this.getClass());
        }
        PsiClass[] innerClasses = doGetInnerClasses();
        return CachedValueProvider.Result.create(innerClasses, getInnerClassesDependencies());
      });

    PsiFileFactory factory = PsiFileFactory.getInstance(project);
    myFile = (PsiJavaFile)factory.createFileFromText(shortName + ".java", JavaFileType.INSTANCE,
                                                     "// This class is generated on-the-fly by the IDE.");

    // We need to set the package name of the file, otherwise Util#checkReference will highlight all references to the class. This name is
    // sometimes shown in "quick documentation" for the R class itself, but is not considered when resolving references etc., where
    // getQualifiedName is what matters and needs to stay up-to-date with the manifest etc.
    if (packageName == null || !PsiNameHelper.getInstance(project).isQualifiedName(packageName)) {
      packageName = "_";
    }
    myFile.setPackageName(packageName);
  }

  @NotNull
  protected abstract PsiClass[] doGetInnerClasses();

  /**
   * Dependencies (as defined by {@link CachedValueProvider.Result#getDependencyItems()}) for the cached set of inner classes computed by
   * {@link #doGetInnerClasses()}.
   */
  @NotNull
  protected abstract Object[] getInnerClassesDependencies();

  @Nullable
  @Override
  public final PsiClass getContainingClass() {
    return null;
  }

  @NotNull
  @Override
  public final PsiClass[] getInnerClasses() {
    return myClassCache.getValue();
  }

  @Nullable
  @Override
  public final PsiClass findInnerClassByName(@NonNls String name, boolean checkBases) {
    for (PsiClass aClass : getInnerClasses()) {
      if (name.equals(aClass.getName())) {
        return aClass;
      }
    }
    return null;
  }

  @Override
  @NotNull
  public final String getName() {
    return myShortName;
  }

  @NotNull
  @Override
  public final PsiFile getContainingFile() {
    return myFile;
  }
}
