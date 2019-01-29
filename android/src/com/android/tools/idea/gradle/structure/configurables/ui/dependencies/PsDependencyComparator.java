/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.ui.dependencies;

import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings;
import com.android.tools.idea.gradle.structure.model.PsBaseDependency;
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency;
import com.android.tools.idea.gradle.structure.model.PsModuleDependency;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public class PsDependencyComparator implements Comparator<PsBaseDependency> {
  @NotNull private final PsUISettings myUiSettings;

  public PsDependencyComparator(@NotNull PsUISettings uiSettings) {
    myUiSettings = uiSettings;
  }

  @Override
  public int compare(PsBaseDependency d1, PsBaseDependency d2) {
    if (d1 instanceof PsLibraryDependency) {
      if (d2 instanceof PsLibraryDependency) {
        String s1 = ((PsLibraryDependency)d1).getSpec().getDisplayText(myUiSettings);
        String s2 = ((PsLibraryDependency)d2).getSpec().getDisplayText(myUiSettings);
        return s1.compareTo(s2);
      }
    }
    else if (d1 instanceof PsModuleDependency) {
      if (d2 instanceof PsModuleDependency) {
        return d1.toText().compareTo(d2.toText());
      }
      else if (d2 instanceof PsLibraryDependency) {
        return 1;
      }
    }
    return -1;
  }
}
