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
package com.android.tools.idea.uibuilder.handlers.constraint;

import com.android.tools.idea.uibuilder.scout.Scout;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

import javax.swing.*;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import org.jetbrains.annotations.Nullable;

public class MarginPopup extends JPanel {

  private final JBTextField DEFAULT_MARGIN = new JBTextField("Default Margin : ");
  private final JBTextField myTextField = new JBTextField();
  private final JButton[] myHistoryButtons = new JButton[4];
  private final int[] myDefaultValues = {0, 8, 16, 24};
  private final int[] myHistoryValues = {-1, -1, -1, -1};

  ActionListener myListener;
  private int myValue = Scout.DEFAULT_MARGIN;
  private JBPopup myPopup;

  public int getValue() {
    return myValue;
  }

  public void setPopup(@Nullable JBPopup popup) {
    myPopup = popup;
  }

  private ActionListener myDefaultListener = new ActionListener() {
    @Override
    public void actionPerformed(ActionEvent e) {
      JButton b = (JButton)e.getSource();
      String s = b.getText();
      try {
        myValue = Integer.parseInt(s);
      }
      catch (NumberFormatException e1) {
        myValue = 0;
        s = "0";
      }
      if (!s.isEmpty()) {
        myTextField.setText(s);
      }
      if (myListener != null) {
        myListener.actionPerformed(e);
      }
      if (myPopup != null) {
        myPopup.cancel();
      }
    }
  };

  private boolean isADefault(int value) {
    for (int i = 0; i < myDefaultValues.length; i++) {
      if (myDefaultValues[i] == value) {
        return true;
      }
    }
    return false;
  }

  private ActionListener mTextListener = new ActionListener() {
    @Override
    public void actionPerformed(ActionEvent e) {
      saveNewValue(e);
      SwingUtilities.getWindowAncestor(MarginPopup.this).setVisible(false);
    }
  };

  public void setActionListener(ActionListener actionListener) {
    myListener = actionListener;
  }

  void saveNewValue(ActionEvent e) {
    try {
      int value = myValue = Integer.parseInt(myTextField.getText());
      if (!isADefault(value)) {

        for (int i = 0; i < myHistoryValues.length; i++) {
          int old = myHistoryValues[i];
          myHistoryValues[i] = value;
          if (value > 0) {
            myHistoryButtons[i].setText(Integer.toString(value));
          }
          value = old;
        }
      }
    }
    catch (NumberFormatException e1) {
      e1.printStackTrace();
    }
    if (myListener != null) {
      myListener.actionPerformed(e);
    }
  }

  public MarginPopup() {
    super(new GridBagLayout());
    setBackground(JBColor.background());
    GridBagConstraints gc = new GridBagConstraints();
    DEFAULT_MARGIN.setEditable(false);
    DEFAULT_MARGIN.setFocusable(false);
    DEFAULT_MARGIN.setBorder(BorderFactory.createEmptyBorder());

    gc.gridx = 0;
    gc.gridy = 0;
    gc.gridwidth = 2;
    gc.insets = JBUI.insets(10, 5, 5, 0);
    gc.fill = GridBagConstraints.BOTH;
    DEFAULT_MARGIN.setHorizontalAlignment(SwingConstants.RIGHT);
    add(DEFAULT_MARGIN, gc);

    gc.gridx = 2;
    gc.gridy = 0;
    gc.gridwidth = 1;
    gc.insets = JBUI.insets(10, 5, 5, 0);
    gc.fill = GridBagConstraints.BOTH;
    myTextField.setHorizontalAlignment(SwingConstants.RIGHT);
    myTextField.setText(Integer.toString(myValue));
    add(myTextField, gc);

    gc.gridx = 3;
    gc.gridwidth = 1;
    gc.insets.bottom = JBUI.scale(0);
    gc.insets.left = JBUI.scale(2);
    gc.insets.right = JBUI.scale(5);
    gc.insets.top = JBUI.scale(2);
    add(new JBLabel("dp"), gc);

    gc.gridwidth = 1;

    gc.gridy = 1;
    gc.insets.bottom = JBUI.scale(0);
    gc.insets.left = JBUI.scale(0);
    gc.insets.right = JBUI.scale(0);
    gc.insets.top = JBUI.scale(0);
    ((AbstractDocument)myTextField.getDocument()).setDocumentFilter(new DocumentFilter() {
      @Override
      public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
        for (int i = 0; i < text.length(); i++) {
          if (!Character.isDigit(text.charAt(i))) {
            return;
          }
        }
        super.replace(fb, offset, length, text, attrs);
      }
    });
    gc.fill = GridBagConstraints.HORIZONTAL;
    myTextField.addActionListener(mTextListener);
    Insets margin = new Insets(0, 0, 0, 0);
    for (int i = 0; i < 4; i++) {
      JButton b = new JButton("" + myDefaultValues[i]);
      b.setMargin(margin);
      b.addActionListener(myDefaultListener);
      gc.gridx = i;
      gc.insets.left = JBUI.scale((i == 0) ? 5 : 0);
      gc.insets.right = JBUI.scale((i == 3) ? 5 : 0);
      add(b, gc);
    }
    gc.gridy = 2;
    gc.insets.bottom = JBUI.scale(7);
    for (int i = 0; i < myHistoryButtons.length; i++) {
      myHistoryButtons[i] = new JButton("XXX");
      myHistoryButtons[i].setPreferredSize(myHistoryButtons[i].getPreferredSize());
      myHistoryButtons[i].setMargin(margin);
      if (myHistoryValues[i] > 0) {
        myHistoryButtons[i].setText(Integer.toString(myHistoryValues[i]));
      }
      else {
        myHistoryButtons[i].setText("");
      }
      myHistoryButtons[i].addActionListener(myDefaultListener);

      gc.gridx = i;
      gc.insets.left = JBUI.scale((i == 0) ? 5 : 0);
      gc.insets.right = JBUI.scale((i == 3) ? 5 : 0);
      //Insets in = myHistoryButtons[i].getMargin();
      add(myHistoryButtons[i], gc);
    }
    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentShown(ComponentEvent e) {
      }

      @Override
      public void componentHidden(ComponentEvent e) {
        saveNewValue(null);
      }
    });
  }

  public JComponent getTextField() {
    return myTextField;
  }
}
