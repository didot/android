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
package com.android.tools.idea.gradle.structure.navigation;

import com.android.tools.idea.gradle.structure.configurables.DependenciesPerspectiveConfigurable;
import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency;
import com.android.tools.idea.gradle.structure.model.PsModulePath;
import com.android.tools.idea.gradle.structure.model.PsPath;
import com.android.tools.idea.structure.dialog.ProjectStructureConfigurable;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.android.tools.idea.gradle.structure.configurables.issues.GoToPathLinkHandler.GO_TO_PATH_TYPE;
import static com.android.tools.idea.gradle.structure.model.PsDependency.TextType.FOR_NAVIGATION;
import static com.android.tools.idea.gradle.structure.navigation.Places.serialize;
import static com.android.tools.idea.structure.dialog.ProjectStructureConfigurable.putPath;

public final class PsLibraryDependencyNavigationPath implements PsPath {
  @NotNull private final PsModulePath myParent;
  @NotNull private final String myModuleName;
  @NotNull private final String myDependency;
  @NotNull private final String myNavigationText;

  public PsLibraryDependencyNavigationPath(@NotNull PsLibraryDependency dependency) {
    myParent = new PsModulePath(dependency.getParent());
    myModuleName = dependency.getParent().getName();
    PsArtifactDependencySpec spec = dependency.getSpec();
    myNavigationText = dependency.toText(FOR_NAVIGATION);
    myDependency = spec.getName() + GRADLE_PATH_SEPARATOR + spec.getVersion();
  }

  @Override
  @NotNull
  public String toText(@NotNull TexType type) {
    switch (type) {
      case PLAIN_TEXT:
        return myDependency;
      case FOR_COMPARE_TO:
        return myDependency + " / " + myModuleName;
    }
    return "";
  }

  @Override
  @NotNull
  public String getHyperlinkDestination(@NotNull PsContext context) {
    Place place = new Place();

    ProjectStructureConfigurable mainConfigurable = context.getMainConfigurable();
    DependenciesPerspectiveConfigurable target = mainConfigurable.findConfigurable(DependenciesPerspectiveConfigurable.class);
    assert target != null;

    putPath(place, target);
    target.putNavigationPath(place, myModuleName, myNavigationText);

    return GO_TO_PATH_TYPE + serialize(place);
  }

  @NotNull
  @Override
  public String getHtml(@NotNull PsContext context) {
    return String.format("<a href='%1$s'>%2$s</a> (%3$s)", getHyperlinkDestination(context), myDependency, myModuleName);
  }

  @NotNull
  @Override
  public List<PsPath> getParents() {
    return ImmutableList.of(myParent);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PsLibraryDependencyNavigationPath path = (PsLibraryDependencyNavigationPath)o;
    return Objects.equal(myParent, path.myParent) &&
           Objects.equal(myModuleName, path.myModuleName) &&
           Objects.equal(myDependency, path.myDependency) &&
           Objects.equal(myNavigationText, path.myNavigationText);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myParent, myModuleName, myDependency, myNavigationText);
  }

  @Override
  public String toString() {
    return toText(TexType.FOR_COMPARE_TO);
  }
}
