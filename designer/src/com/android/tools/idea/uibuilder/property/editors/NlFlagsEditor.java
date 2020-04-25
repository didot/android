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
package com.android.tools.idea.uibuilder.property.editors;

import com.android.tools.adtui.common.AdtSecondaryPanel;
import com.android.tools.idea.common.property.editors.BaseComponentEditor;
import com.android.tools.idea.common.property.editors.NlComponentEditor;
import com.android.tools.idea.uibuilder.property.EmptyProperty;
import com.android.tools.idea.uibuilder.property.NlFlagPropertyItem;
import com.android.tools.idea.common.property.NlProperty;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextFieldUI;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.JBUI;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.TextUI;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;

import static com.android.tools.idea.uibuilder.property.editors.NlEditingListener.DEFAULT_LISTENER;

/**
 * The {@link NlFlagsEditor} is used to edit a {@link NlFlagPropertyItem} by displaying
 * a popup with a list of choices.
 */
public class NlFlagsEditor extends BaseComponentEditor implements NlComponentEditor {
  private final JPanel myPanel;
  private final JTextField myValue;
  private NlFlagPropertyItem myProperty;

  public static NlFlagsEditor create() {
    return new NlFlagsEditor();
  }

  private NlFlagsEditor() {
    super(DEFAULT_LISTENER);
    AnAction action = createDisplayFlagEditorAction();
    ActionButton button = new ActionButton(action,
                                           action.getTemplatePresentation().clone(),
                                           ActionPlaces.UNKNOWN,
                                           ActionToolbar.NAVBAR_MINIMUM_BUTTON_SIZE);
    myValue = new CustomTextField();
    myValue.setEditable(false);
    myValue.setFocusable(true);
    myPanel = new AdtSecondaryPanel(new BorderLayout(JBUI.scale(HORIZONTAL_COMPONENT_GAP), 0));
    myPanel.setBorder(JBUI.Borders.empty(VERTICAL_SPACING, HORIZONTAL_SPACING, VERTICAL_SPACING, 0));
    myPanel.add(myValue, BorderLayout.CENTER);
    myPanel.add(button, BorderLayout.LINE_END);
    myValue.addActionListener(event -> displayFlagEditor());
    myValue.addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent e) {
        displayFlagEditor();
      }
    });
    myValue.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        displayFlagEditor();
      }
    });
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @NotNull
  @Override
  public NlProperty getProperty() {
    return myProperty != null ? myProperty : EmptyProperty.INSTANCE;
  }

  @Override
  public void setProperty(@NotNull NlProperty property) {
    assert property instanceof NlFlagPropertyItem;
    myProperty = (NlFlagPropertyItem)property;
    myValue.setText(property.getValue());
  }

  private AnAction createDisplayFlagEditorAction() {
    return new AnAction() {
      @Override
      public void update(@NotNull AnActionEvent event) {
        Presentation presentation = event.getPresentation();
        if (myProperty != null) {
          presentation.setIcon(AllIcons.General.Ellipsis);
          presentation.setText("Click to edit");
          presentation.setEnabledAndVisible(true);
        }
        else {
          presentation.setIcon(null);
          presentation.setText((String)null);
          presentation.setEnabledAndVisible(false);
        }
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent event) {
        displayFlagEditor();
      }
    };
  }

  private void displayFlagEditor() {
    FlagsDialog dialog = new FlagsDialog(myProperty);
    dialog.setResizable(false);
    dialog.setInitialLocationCallback(() -> {
      Point location = new Point(0, 0);
      SwingUtilities.convertPointToScreen(location, myPanel);
      return location;
    });
    dialog.show();
  }

  private static class FlagsDialog extends DialogWrapper {
    private final NlFlagPropertyItem myProperty;

    protected FlagsDialog(@NotNull NlFlagPropertyItem property) {
      super(property.getModel().getProject(), false, IdeModalityType.MODELESS);
      myProperty = property;
      setTitle(property.getName());
      init();
      getWindow().addWindowFocusListener(new WindowFocusListener() {
        @Override
        public void windowGainedFocus(WindowEvent e) {
        }

        @Override
        public void windowLostFocus(WindowEvent e) {
          close(0);
        }
      });
    }

    @Override
    @NotNull
    protected Action[] createActions() {
      return new Action[]{getOKAction()};
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      JPanel panel = new AdtSecondaryPanel();
      panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
      AttributeDefinition definition = myProperty.getDefinition();
      assert definition != null;
      for (String item : definition.getValues()) {
        NlFlagEditor editor = NlFlagEditor.createForInspector(DEFAULT_LISTENER);
        editor.setProperty(myProperty.getChildProperty(item));
        panel.add(editor.getComponent());
      }
      JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(panel,
                                                                  ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                                  ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      scrollPane.setBorder(null);
      scrollPane.getVerticalScrollBar().setUnitIncrement(25);
      scrollPane.getVerticalScrollBar().setBlockIncrement(25);
      return scrollPane;
    }
  }

  /**
   * {@link CustomTextField} gives us a consistent UI between all the editors.
   * We achieve this by always using a Darcula UI.
   * Note: forcing the Darcula UI does not imply dark colors.
   *
   * Instead we get consistent spacing with ComboBox and the text editor.
   */
  private static class CustomTextField extends JTextField {

    public CustomTextField() {
      setBorder(new DarculaTextBorder());
    }

    @Override
    public void setUI(TextUI ui) {
      super.setUI(new CustomTextFieldUI());
    }
  }

  private static class CustomTextFieldUI extends DarculaTextFieldUI {
    @Override
    protected void paintDarculaBackground(Graphics graphics2D, JTextComponent component) {
      // Override the background color of this non editable JTextField
      graphics2D.setColor(component.getBackground());
      super.paintDarculaBackground(graphics2D, component);
    }
  }
}
