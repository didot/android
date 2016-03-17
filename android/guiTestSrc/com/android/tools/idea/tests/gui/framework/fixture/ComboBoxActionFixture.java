/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.tests.gui.framework.Wait;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.ui.JBListWithHintProvider;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.popup.list.ListPopupModel;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.fixture.JButtonFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.junit.Assert.*;

public class ComboBoxActionFixture {
  @NotNull private Robot myRobot;
  @NotNull private JButton myTarget;
  private static final Class<?> ourComboBoxButtonClass;
  static {
    Class<?> temp = null;
    try {
      temp = ComboBoxActionFixture.class.getClassLoader().loadClass(ComboBoxAction.class.getCanonicalName() + "$ComboBoxButton");
    }
    catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
    ourComboBoxButtonClass = temp;
  }

  public static ComboBoxActionFixture findComboBox(@NotNull Robot robot, @NotNull Container root) {
    JButton comboBoxButton = robot.finder().find(root, new GenericTypeMatcher<JButton>(JButton.class) {
      @Override
      protected boolean isMatching(@NotNull JButton component) {
        return ourComboBoxButtonClass.isInstance(component);
      }
    });
    return new ComboBoxActionFixture(robot, comboBoxButton);
  }

  public static ComboBoxActionFixture findComboBoxByText(@NotNull Robot robot, @NotNull Container root, @NotNull final String text) {
    JButton comboBoxButton = robot.finder().find(root, new GenericTypeMatcher<JButton>(JButton.class) {
      @Override
      protected boolean isMatching(@NotNull JButton component) {
        return ourComboBoxButtonClass.isInstance(component) && component.getText().equals(text);
      }
    });
    return new ComboBoxActionFixture(robot, comboBoxButton);
  }

  public ComboBoxActionFixture(@NotNull Robot robot, @NotNull JButton target) {
    myRobot = robot;
    myTarget = target;
  }

  public void selectItem(@NotNull String itemName) {
    click();
    selectItemByText(getPopupList(), itemName);
  }

  public String getSelectedItemText() {
    return execute(new GuiQuery<String>() {
      @Nullable
      @Override
      protected String executeInEDT() throws Throwable {
        return myTarget.getText();
      }
    });
  }

  private void click() {
    final JButtonFixture comboBoxButtonFixture = new JButtonFixture(myRobot, myTarget);
    Wait.minutes(2).expecting("comboBoxButton to be enabled").until(new Wait.Objective() {
      @Override
      public boolean isMet() {
        //noinspection ConstantConditions
        return execute(new GuiQuery<Boolean>() {
          @Override
          protected Boolean executeInEDT() throws Throwable {
            return comboBoxButtonFixture.target().isEnabled();
          }
        });
      }
    });
    comboBoxButtonFixture.click();
  }

  @NotNull
  private JList getPopupList() {
    return myRobot.finder().findByType(JBListWithHintProvider.class);
  }

  private static void selectItemByText(@NotNull final JList list, @NotNull final String text) {
    Wait.minutes(2).expecting("the list to be populated").until(new Wait.Objective() {
      @Override
      public boolean isMet() {
        ListPopupModel popupModel = (ListPopupModel)list.getModel();
        for (int i = 0; i < popupModel.getSize(); ++i) {
          PopupFactoryImpl.ActionItem actionItem = (PopupFactoryImpl.ActionItem)popupModel.get(i);
          assertNotNull(actionItem);
          if (text.equals(actionItem.getText())) {
            return true;
          }
        }
        return false;
      }
    });

    final Integer appIndex = execute(new GuiQuery<Integer>() {
      @Override
      protected Integer executeInEDT() throws Throwable {
        ListPopupModel popupModel = (ListPopupModel)list.getModel();
        for (int i = 0; i < popupModel.getSize(); ++i) {
          PopupFactoryImpl.ActionItem actionItem = (PopupFactoryImpl.ActionItem)popupModel.get(i);
          assertNotNull(actionItem);
          if (text.equals(actionItem.getText())) {
            return i;
          }
        }
        return -1;
      }
    });
    //noinspection ConstantConditions
    assertTrue(appIndex >= 0);

    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        list.setSelectedIndex(appIndex);
      }
    });
    assertEquals(text, ((PopupFactoryImpl.ActionItem)list.getSelectedValue()).getText());
  }
}
