/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.wizard.model;

import com.android.tools.idea.ui.properties.core.ObservableBool;
import com.android.tools.idea.ui.properties.expressions.bool.BooleanExpressions;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

/**
 * A step is a single page in a wizard. It is responsible for creating a single pane of UI to
 * present to the user, determining if the information on the page is valid in order to progress,
 * and storing the user's data in the target model.
 */
public abstract class ModelWizardStep<M extends WizardModel> implements Disposable {

  @NotNull private final String myTitle;

  @NotNull private M myModel;

  protected ModelWizardStep(@NotNull M model, @NotNull String title) {
    myModel = model;
    myTitle = title;
  }

  /**
   * Returns the title of this step.
   */
  @NotNull
  public final String getTitle() {
    return myTitle;
  }

  /**
   * Returns the UI component that represents this step.
   */
  @NotNull
  protected abstract JComponent getComponent();

  /**
   * Returns a component which should try and get focus when a step is first entered, or
   * {@code null} if we're OK just letting Swing choose for us. This will likely leave focus where
   * it is, which is probably the wizard's next button.
   */
  @Nullable
  protected JComponent getPreferredFocusComponent() {
    return null;
  }

  @NotNull
  protected final M getModel() {
    return myModel;
  }

  /**
   * When this step is added to a wizard, it is given a chance to add subsequent steps that it is
   * willing to take responsibility for. This is useful for steps which contain toggles that
   * enable/disable following steps.
   */
  @NotNull
  protected Collection<? extends ModelWizardStep> createDependentSteps() {
    return ImmutableList.of();
  }

  /**
   * Called once, when the wizard first starts up but before any step is shown. This is a good
   * place to handle UI initialization that needs to interact with the parent wizard.
   * <p/>
   * Note: You should not store a reference to the wizard parameter. It's only meant to be used in
   * the context of this method.
   */
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
  }

  /**
   * Returns {@code true} to indicate the that this step should be shown, or {@code false} if it
   * should be skipped.
   */
  protected boolean shouldShow() {
    return true;
  }

  /**
   * Returns an observable boolean, which when set to {@code true} means the current step is
   * complete and the user can move onto the next step.
   * <p/>
   * The return type is observable so as soon as you switch the value from {@code false} to
   * {@code true}, UI can automatically be notified through a binding.
   */
  @NotNull
  protected ObservableBool canGoForward() {
    return BooleanExpressions.alwaysTrue();
  }

  /**
   * Returns a boolean, which when set to {@code true} means the current step can
   * go back to previous step.
   */
  @NotNull
  protected boolean canGoBack() {
    return true;
  }

  /**
   * Called when the step is first entered, before it is shown. This method is not called when
   * returning back to a step.
   * <p/>
   * TODO: Should we pass the wizard parameter to this method? This TODO can be safely removed
   * if the dynamic wizard migration is complete and we never needed it.
   */
  protected void onEnter() {
  }

  /**
   * Called just before a step is about to move to the next step (or, if this is the last step, to
   * the finished state). This is a good time to copy any relevant data out of the UI into a model.
   */
  protected void onProceeding() {
  }

  @Override
  public void dispose() {
  }
}
