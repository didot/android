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
package com.android.tools.idea.ui.wizard;

import com.android.tools.idea.ui.properties.InvalidationListener;
import com.android.tools.idea.ui.properties.ListenerManager;
import com.android.tools.idea.ui.properties.ObservableValue;
import com.android.tools.idea.ui.properties.core.BoolProperty;
import com.android.tools.idea.ui.properties.core.BoolValueProperty;
import com.android.tools.idea.ui.properties.core.ObservableBool;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Table;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Iterator;

/**
 * A panel that wraps some inner content and allows registering {@link Validator}s, which, if any
 * of them are invalid, causes a warning or error message to display across the bottom of the
 * panel.
 */
public final class ValidatorPanel extends JPanel implements Disposable {

  /**
   * Used to set empty text on a label. If completely empty, the height calculations are off.
   */
  private static final String BLANK = " ";

  // @formatter:off
  private static ImmutableMap<Validator.Severity, Icon> ICONS = ImmutableMap.of(
    Validator.Severity.INFO, AllIcons.General.BalloonInformation,
    Validator.Severity.WARNING, AllIcons.General.BalloonWarning,
    Validator.Severity.ERROR, AllIcons.General.BalloonError
  );
  // @formatter:on

  private final ListenerManager myListeners = new ListenerManager();
  private final Table<Validator.Severity, ObservableValue<?>, String> myMessages = HashBasedTable.create();
  private final BoolProperty myHasErrors = new BoolValueProperty();

  private JPanel myRootPanel;
  private JPanel mySouthPanel;
  private JBLabel myValidationLabel;

  public ValidatorPanel(@NotNull Disposable parentDisposable, @NotNull JPanel innerPanel) {
    super(new BorderLayout());

    add(myRootPanel);
    myRootPanel.add(innerPanel);

    myValidationLabel.setText(BLANK);

    Disposer.register(parentDisposable, this);
  }

  /**
   * Register a {@link Validator} linked to a target property. Whenever the target property
   * changes, the validator will be tested with its value.
   *
   * Registration order of validators doesn't matter - if multiple errors happen at the same time
   * (or warnings, etc.), the one which shows up is random. However, a message of higher severity
   * will always trump a message of lower severity.
   *
   * See also {@link #hasErrors()}, which will be true if any validator has returned an
   * {@link Validator.Severity#ERROR} result.
   */
  public <T> void registerValidator(@NotNull final ObservableValue<T> value,
                                    @NotNull final Validator<T> validator) {
    myListeners.listenAndFire(value, new InvalidationListener() {
      @Override
      public void onInvalidated(@NotNull ObservableValue<?> sender) {
        Validator.Result result = validator.validate(value.get());
        myMessages.column(value).clear();
        if (result.getSeverity() != Validator.Severity.OK) {
          myMessages.put(result.getSeverity(), value, result.getMessage());
        }

        updateValidationLabel();
      }
    });
  }

  /**
   * Returns a property which indicates if any of the components in this panel are invalid.
   *
   * This is a useful property to listen to when overriding {@link ModelWizardStep#canGoForward()},
   * and you may even be able to just return it directly.
   */
  @NotNull
  public ObservableBool hasErrors() {
    return myHasErrors;
  }

  private void updateValidationLabel() {
    myHasErrors.set(false);
    myValidationLabel.setIcon(null);
    myValidationLabel.setText(BLANK);

    for (Validator.Severity severity : Validator.Severity.values()) {
      Iterator<String> messages = myMessages.row(severity).values().iterator();
      if (messages.hasNext()) {
        myValidationLabel.setText(messages.next());
        myValidationLabel.setIcon(ICONS.get(severity));

        if (severity == Validator.Severity.ERROR) {
          myHasErrors.set(true);
        }
      }
    }
  }

  @Override
  public void dispose() {
    myListeners.releaseAll();
  }
}
