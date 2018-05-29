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
package com.android.tools.idea.gradle.structure.dependencies;

import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.android.PsLibraryAndroidDependency;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Objects;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class AddLibraryDependencyDialog extends AbstractAddDependenciesDialog {
  @NotNull public static final String TITLE = "Add Library Dependency";

  private LibraryDependenciesForm myLibraryDependenciesForm;

  public AddLibraryDependencyDialog(@NotNull PsModule module) {
    super(module);
    setTitle(TITLE);
  }

  @Override
  public void addNewDependencies() {
    String library = myLibraryDependenciesForm.getSelectedLibrary();
    assert library != null;

    DependencyScopesSelector scopesPanel = getScopesPanel();
    List<String> scopesNames = scopesPanel.getSelectedScopeNames();

    getModule().addLibraryDependency(library, scopesNames);
  }

  @Override
  @NotNull
  protected String getSplitterProportionKey() {
    return "psd.add.library.dependency.main.horizontal.splitter.proportion";
  }

  @Override
  @NotNull
  protected JComponent getDependencySelectionView() {
    if (myLibraryDependenciesForm == null) {
      myLibraryDependenciesForm = new LibraryDependenciesForm(getModule());
    }
    return myLibraryDependenciesForm.getPanel();
  }

  @Override
  @NotNull
  protected String getInstructions() {
    return "Use the form below to find the library to add. This form uses the repositories specified in  the project's build files (e.g. " +
           "JCenter, Maven Central, etc.)";
  }

  @Override
  @NotNull
  protected String getDimensionServiceKey() {
    return "psd.add.library.dependency.panel.dimension";
  }

  @Override
  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return myLibraryDependenciesForm != null ? myLibraryDependenciesForm.getPreferredFocusedComponent() : null;
  }

  @Override
  protected void dispose() {
    super.dispose();
    if (myLibraryDependenciesForm != null) {
      Disposer.dispose(myLibraryDependenciesForm);
    }
  }

  @Override
  @Nullable
  protected ValidationInfo doValidate() {
    List<Exception> searchErrors = myLibraryDependenciesForm.getSearchErrors();
    if (!searchErrors.isEmpty()) {
      StringBuilder buffer = new StringBuilder();
      searchErrors.forEach(e -> buffer.append(getErrorMessage(e)).append("\n"));
      return new ValidationInfo(buffer.toString(), myLibraryDependenciesForm.getPreferredFocusedComponent());
    }

    String selectedLibrary = myLibraryDependenciesForm.getSelectedLibrary();
    if (isEmpty(selectedLibrary)) {
      return new ValidationInfo("Please specify the library to add as dependency", myLibraryDependenciesForm.getPreferredFocusedComponent());
    }
    PsArtifactDependencySpec spec = PsArtifactDependencySpec.Companion.create(selectedLibrary);
    PsModule module = getModule();
    if (spec != null && module instanceof PsAndroidModule) {
      PsAndroidModule androidModule = (PsAndroidModule)module;
      Ref<Boolean> found = new Ref<>(false);
      androidModule.getDependencies().forEach(dependency -> {
        if (dependency instanceof PsLibraryAndroidDependency) {
          PsLibraryAndroidDependency libraryDependency = (PsLibraryAndroidDependency)dependency;
          PsArtifactDependencySpec resolvedSpec = libraryDependency.getSpec();
          if (Objects.equals(spec.getGroup(), resolvedSpec.getGroup()) && Objects.equals(spec.getName(), resolvedSpec.getName())) {
            found.set(true);
          }
        }
      });

      if (found.get()) {
        String msg = String.format("Library '%1$s' is already a dependency", spec.getName());
        return new ValidationInfo(msg, myLibraryDependenciesForm.getPreferredFocusedComponent());
      }
    }

    return getScopesPanel().validateInput();
  }

  @NotNull
  private static String getErrorMessage(@NotNull Exception error) {
    if (error instanceof UnknownHostException) {
      return "Failed to connect to host '" + error.getMessage() + "'. Please check your Internet connection.";
    }

    String msg = error.getMessage();
    if (isNotEmpty(msg)) {
      return msg;
    }
    return error.getClass().getName();
  }
}
