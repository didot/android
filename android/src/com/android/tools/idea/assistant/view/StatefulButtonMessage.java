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
package com.android.tools.idea.assistant.view;

import com.android.tools.idea.assistant.AssistActionStateManager.ActionState;
import com.intellij.icons.AllIcons;
import com.intellij.ui.components.JBLabel;

import javax.swing.*;
import java.awt.*;

/**
 * Displays a message in lieu of a button when an action may not be completed. Note, this is not an extension of JBLabel as it will display
 * other elements such as an edit link and potentially support progress indication.
 *
 * TODO: Add support for an edit action (not yet spec'd out).
 */
public class StatefulButtonMessage extends JPanel {
  public ActionState myState;

  public StatefulButtonMessage(String message, ActionState state) {
    super(new FlowLayout(FlowLayout.LEFT, 0, 0));
    setBorder(BorderFactory.createEmptyBorder());
    setOpaque(false);

    JBLabel messageDisplay = new JBLabel(message);
    messageDisplay.setOpaque(false);
    // TODO(b/29617676): Add a treatment for IN_PROGRESS.
    switch (state) {
      case PARTIALLY_COMPLETE:
      case COMPLETE:
        messageDisplay.setIcon(AllIcons.RunConfigurations.TestPassed);
        messageDisplay.setForeground(UIUtils.getSuccessColor());
        break;
      case ERROR:
        messageDisplay.setIcon(AllIcons.RunConfigurations.TestFailed);
        messageDisplay.setForeground(UIUtils.getFailureColor());
        break;
      default:
        messageDisplay.setIcon(null);
        messageDisplay.setForeground(new JBLabel("").getForeground());
    }

    add(messageDisplay);
  }
}
