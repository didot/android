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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.theme.EditReferenceFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.StateListPickerFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.adtui.SearchField;
import com.android.tools.idea.ui.resourcechooser.ChooseResourceDialog;
import com.android.tools.idea.ui.resourcechooser.ColorPicker;
import com.android.tools.idea.ui.resourcechooser.StateListPicker;
import com.intellij.icons.AllIcons;
import com.intellij.util.ui.JBUI;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.core.TypeMatcher;
import org.fest.swing.core.matcher.JLabelMatcher;
import org.fest.swing.fixture.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;
import java.awt.*;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;

public class ChooseResourceDialogFixture extends IdeaDialogFixture<ChooseResourceDialog> {
  @NotNull
  public static ChooseResourceDialogFixture find(@NotNull Robot robot) {
    return new ChooseResourceDialogFixture(robot, find(robot, ChooseResourceDialog.class));
  }

  @NotNull
  public static ChooseResourceDialogFixture find(@NotNull Robot robot, @NotNull final GenericTypeMatcher<JDialog> matcher) {
    return new ChooseResourceDialogFixture(robot, find(robot, ChooseResourceDialog.class, matcher));
  }

  private ChooseResourceDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<ChooseResourceDialog> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
  }

  @NotNull
  public JTextComponentFixture getNameTextField() {
    return new JTextComponentFixture(robot(), (JTextComponent)robot().finder().findByLabel(target(), "Name"));
  }

  public JTextComponentFixture getSearchField() {
    Component component = robot().finder().find(target(), new TypeMatcher(SearchField.class));
    return new JTextComponentFixture(robot(), ((SearchField)component).getTextEditor());
  }

  public JTabbedPaneFixture getTabs() {
    return new JTabbedPaneFixture(robot(), (JTabbedPane)robot().finder().findByName(this.target(), "ResourceTypeTabs"));
  }

  public ChooseResourceDialogFixture clickOnTab(@NotNull String name) {
    getTabs().selectTab(name);
    return this;
  }

  @NotNull
  public String getError() {
    return GuiTests.waitUntilShowing(robot(), waitForErrorPanel(), Matchers.byType(JLabel.class)).getText();
  }

  @NotNull
  public JPanel waitForErrorPanel() {
    return GuiTests.waitUntilShowing(robot(), new GenericTypeMatcher<JPanel>(JPanel.class) {
        @Override
        protected boolean isMatching(@NotNull JPanel component) {
          return ("com.intellij.openapi.ui.DialogWrapper$ErrorText").equals(component.getClass().getName());
        }
      });
  }

  public void requireNoError() {
    GuiTests.waitUntilGone(robot(), target(), Matchers.byIcon(JLabel.class, AllIcons.General.Error).andIsShowing());
  }

  @NotNull
  public ColorPickerFixture getColorPicker() {
    return new ColorPickerFixture(robot(), robot().finder().findByType(this.target(), ColorPicker.class));
  }

  @NotNull
  public StateListPickerFixture getStateListPicker() {
    return new StateListPickerFixture(robot(), robot().finder().findByType(this.target(), StateListPicker.class));
  }

  @NotNull
  public EditReferenceFixture getEditReferencePanel() {
    return new EditReferenceFixture(robot(), (Box)robot().finder().findByName(this.target(), "ReferenceEditor"));
  }

  @NotNull
  public JListFixture getList(@NotNull String appNamespaceLabel) {
    return new JListFixture(robot(), (JList)robot().finder().findByName(target(), appNamespaceLabel));
  }

  public void clickOK() {
    findAndClickOkButton(this);
  }

  @NotNull
  public JPopupMenuFixture clickNewResource() {
    new JLabelFixture(robot(), GuiTests.waitUntilShowing(robot(), target(), JLabelMatcher.withText("Add new resource"))).click();
    return new JPopupMenuFixture(robot(), robot().findActivePopupMenu());
  }

  public JTableFixture getResourceNameTable() {
    return new JTableFixture(robot(), (JTable)robot().finder().findByName(target(), "nameTable"));
  }

  public JTableFixture getResourceValueTable() {
    return new JTableFixture(robot(), (JTable)robot().finder().findByName(target(), "valueTable"));
  }

  public JLabelFixture getDrawablePreviewName() {
    return new JLabelFixture(robot(), (JLabel)robot().finder().findByName(target(), "drawablePreviewName"));
  }

  public JLabelFixture getDrawablePreviewLabel() {
    return new JLabelFixture(robot(), (JLabel)robot().finder().findByName(target(), "drawablePreviewLabel"));
  }

  public JLabelFixture getDrawablePreviewType() {
    return new JLabelFixture(robot(), (JLabel)robot().finder().findByName(target(), "drawablePreviewType"));
  }

  public JPanelFixture getDrawablePreviewResolutionPanel() {
    return new JPanelFixture(robot(), (JPanel)robot().finder().findByName(target(), "resolutionChain"));
  }

  public String getDrawableResolutionChain() {
    JPanelFixture panelFixture = getDrawablePreviewResolutionPanel();
    JPanel panel = panelFixture.target();
    StringBuilder sb = new StringBuilder();
    for (int i = 0, n = panel.getComponentCount(); i < n; i++) {
      Component component = panel.getComponent(i);
      if (component instanceof JLabel) {
        JLabel label = (JLabel)component;
        Border border = label.getBorder();
        if (border instanceof EmptyBorder) {
          EmptyBorder emptyBorder = (EmptyBorder)border;
          Insets insets = emptyBorder.getBorderInsets();
          if (insets != null) {
            for (int x = 0; x < insets.left; x += JBUI.scale(12)) {
              sb.append(' ');
            }
          }
        }
        sb.append(label.getText());
        sb.append('\n');
      }
    }

    return sb.toString();
  }

  public String getSelectedTabTitle() {
    JTabbedPane type = robot().finder().findByType(JTabbedPane.class);
    return type.getTitleAt(type.getSelectedIndex());
  }
}
