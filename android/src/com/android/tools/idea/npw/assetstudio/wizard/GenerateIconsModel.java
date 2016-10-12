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
package com.android.tools.idea.npw.assetstudio.wizard;

import com.android.tools.idea.npw.assetstudio.icon.AndroidIconGenerator;
import com.android.tools.idea.npw.project.AndroidProjectPaths;
import com.android.tools.idea.wizard.model.WizardModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A base-class {@link WizardModel} which generates Android icons into the user's project.
 * This parent class is responsible for collecting all the source information needed for previewing
 * icons, and child classes must implement the final logic which generates the final Android icon
 * assets to disk.
 *
 * A wizard that owns this model is expected to call
 * {@link #setIconGenerator(AndroidIconGenerator)} at some point before finishing.
 */
public abstract class GenerateIconsModel extends WizardModel {
  @Nullable private AndroidIconGenerator myIconGenerator;

  @NotNull private AndroidProjectPaths myPaths;

  public GenerateIconsModel(@NotNull AndroidFacet facet) {
    this(new AndroidProjectPaths(facet));
  }

  public GenerateIconsModel(@NotNull AndroidProjectPaths paths) {
    myPaths = paths;
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(GenerateIconsModel.class);
  }

  public final void setPaths(@NotNull AndroidProjectPaths paths) {
    myPaths = paths;
  }

  @NotNull
  public AndroidProjectPaths getPaths() {
    return myPaths;
  }

  @Nullable
  public final AndroidIconGenerator getIconGenerator() {
    return myIconGenerator;
  }

  public final void setIconGenerator(@NotNull AndroidIconGenerator iconGenerator) {
    myIconGenerator = iconGenerator;
  }

  @Override
  protected final void handleFinished() {
    if (myIconGenerator == null) {
      getLog().error("GenerateIconsModel did not collect expected information and will not complete. Please report this error.");
      return;
    }

    ApplicationManager.getApplication().runWriteAction(() -> generateIntoPath(myPaths, myIconGenerator));
  }

  /**
   * Serialize the icons into files on disk. This method will be called within a WriteAction.
   */
  protected abstract void generateIntoPath(@NotNull AndroidProjectPaths paths, @NotNull AndroidIconGenerator iconGenerator);
}
