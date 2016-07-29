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
package com.android.tools.idea.uibuilder.property.inspector;

import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlDesignProperties;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.NlComponentEditor;
import com.android.tools.idea.welcome.install.ComponentInstaller;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.intellij.uiDesigner.core.GridConstraints.*;

public class InspectorPanel extends JPanel implements KeyEventDispatcher {
  private static final int HORIZONTAL_SPACING = 6;

  private final JComponent myAllPropertiesLink;
  private final List<InspectorProvider> myProviders;
  private final NlDesignProperties myDesignProperties;
  private final Font myBoldLabelFont = UIUtil.getLabelFont().deriveFont(Font.BOLD);
  private final JPanel myInspector;
  private List<InspectorComponent> myInspectors = Collections.emptyList();
  private ExpandableGroup myGroup;
  private Map<Component, ExpandableGroup> mySource2GroupMap = new HashMap<>(4);
  private GridConstraints myConstraints = new GridConstraints();
  private int myRow;
  private boolean myActivateEditorAfterLoad;
  private String myPropertyNameForActivation;

  public InspectorPanel(@NotNull Project project, @NotNull JComponent allPropertiesLink) {
    super(new BorderLayout());
    myAllPropertiesLink = allPropertiesLink;
    myAllPropertiesLink.setBorder(BorderFactory.createEmptyBorder(5, 0, 10, 0));
    myProviders = createProviders(project);
    myDesignProperties = new NlDesignProperties();
    myInspector = new GridInspectorPanel();
    myInspector.setBorder(BorderFactory.createEmptyBorder(0, HORIZONTAL_SPACING, 0, HORIZONTAL_SPACING));
    add(myInspector, BorderLayout.CENTER);
  }

  @Override
  public void requestFocus() {
    if (!myInspectors.isEmpty()) {
      List<NlComponentEditor> editors = myInspectors.get(0).getEditors();
      if (!editors.isEmpty()) {
        editors.get(0).requestFocus();
      }
    }
  }

  @Override
  public void addNotify() {
    super.addNotify();
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    // Intercept TAB keys here and expand any group that is currently collapsed if the TAB key is pressed from that group.
    // In that way the user can get to all fields from the keyboard.
    // Note that the arrow keys and '+' and '-' are usually used for something else (so they cannot be used to open/close the group).
    if (event.getKeyCode() == '\t' &&
        event.getModifiers() == 0 &&
        event.getID() == KeyEvent.KEY_PRESSED &&
        event.getSource() instanceof Component) {
      Component source = (Component)event.getSource();
      ExpandableGroup group = mySource2GroupMap.get(source);
      if (group != null && !group.isExpanded()) {
        group.setExpanded(true);
        ApplicationManager.getApplication().invokeLater(source::transferFocus);
        return true;
      }
    }
    return false;
  }

  private static List<InspectorProvider> createProviders(@NotNull Project project) {
    return ImmutableList.of(new IdInspectorProvider(),
                            ViewInspectorProvider.getInstance(project),
                            new ProgressBarInspectorProvider(),
                            new TextInspectorProvider(),
                            new MockupInspectorProvider()
    );
  }

  private static GridLayoutManager createLayoutManager(int rows, int columns) {
    Insets margin = new Insets(0, 0, 0, 0);
    // Hack: Use this constructor to get myMinCellSize = 0 which is not possible in the recommended constructor.
    return new GridLayoutManager(rows, columns, margin, 0, 0);
  }

  public void setComponent(@NotNull List<NlComponent> components,
                           @NotNull Table<String, String, ? extends NlProperty> properties,
                           @NotNull NlPropertiesManager propertiesManager) {
    myInspector.setLayout(null);
    myInspector.removeAll();
    mySource2GroupMap.clear();
    myRow = 0;

    if (!components.isEmpty()) {
      Map<String, NlProperty> propertiesByName = Maps.newHashMapWithExpectedSize(properties.size());
      for (NlProperty property : properties.row(ANDROID_URI).values()) {
        propertiesByName.put(property.getName(), property);
      }
      for (NlProperty property : properties.row(AUTO_URI).values()) {
        propertiesByName.put(property.getName(), property);
      }
      for (NlProperty property : properties.row("").values()) {
        propertiesByName.put(property.getName(), property);
      }
      // Add access to known design properties
      for (NlProperty property : myDesignProperties.getKnownProperties(components)) {
        propertiesByName.putIfAbsent(property.getName(), property);
      }

      List<InspectorComponent> inspectors = createInspectorComponents(components, propertiesManager, propertiesByName, myProviders);
      myInspectors = inspectors;

      int rows = 0;
      for (InspectorComponent inspector : inspectors) {
        rows += inspector.getMaxNumberOfRows();
      }
      rows += inspectors.size(); // 1 row for each divider (including 1 after the last property)
      rows += 2; // 1 Line with a link to all properties + 1 row with a spacer on the bottom

      myInspector.setLayout(createLayoutManager(rows, 2));
      for (InspectorComponent inspector : inspectors) {
        addSeparator();
        inspector.attachToInspector(this);
      }

      endGroup();
      addSeparator();

      // Add a vertical spacer
      myInspector.add(new Spacer(), new GridConstraints(myRow++, 0, 1, 2, ANCHOR_CENTER, FILL_HORIZONTAL, SIZEPOLICY_CAN_GROW,
                                                        SIZEPOLICY_CAN_GROW | SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
      // Add link to all properties table
      addLineComponent(myAllPropertiesLink, myRow++);
    }

    // These are both important to render the controls correctly the first time:
    ApplicationManager.getApplication().invokeLater(() -> {
      revalidate();
      repaint();
      if (myActivateEditorAfterLoad) {
        activatePreferredEditor(myPropertyNameForActivation);
      }
    });
  }

  public void refresh() {
    ApplicationManager.getApplication().invokeLater(() -> myInspectors.stream().forEach(InspectorComponent::refresh));
  }

  @NotNull
  private static List<InspectorComponent> createInspectorComponents(@NotNull List<NlComponent> components,
                                                                    @NotNull NlPropertiesManager propertiesManager,
                                                                    @NotNull Map<String, NlProperty> properties,
                                                                    @NotNull List<InspectorProvider> allProviders) {
    List<InspectorComponent> inspectors = Lists.newArrayListWithExpectedSize(allProviders.size());

    if (components.isEmpty()) {
      // create just the id inspector, which we know can handle a null component
      // this is simply to avoid the screen flickering when switching components
      return ImmutableList.of(new IdInspectorProvider().createCustomInspector(components, properties, propertiesManager));
    }

    for (InspectorProvider provider : allProviders) {
      if (provider.isApplicable(components, properties, propertiesManager)) {
        inspectors.add(provider.createCustomInspector(components, properties, propertiesManager));
      }
    }

    return inspectors;
  }

  public boolean activatePreferredEditor(@NotNull String propertyName, boolean activateAfterLoading) {
    if (activateAfterLoading) {
      myActivateEditorAfterLoad = true;
      myPropertyNameForActivation = propertyName;
    }
    else {
      activatePreferredEditor(propertyName);
    }
    return true;
  }

  private void activatePreferredEditor(@NotNull String propertyName) {
    myActivateEditorAfterLoad = false;
    myPropertyNameForActivation = null;
    boolean designPropertyRequired = propertyName.startsWith(TOOLS_NS_NAME_PREFIX);
    propertyName = StringUtil.trimStart(propertyName, TOOLS_NS_NAME_PREFIX);
    for (InspectorComponent component : myInspectors) {
      for (NlComponentEditor editor : component.getEditors()) {
        NlProperty property = editor.getProperty();
        if (property != null &&
            propertyName.equals(property.getName()) &&
            !(designPropertyRequired && !TOOLS_URI.equals(property.getNamespace()))) {
          editor.requestFocus();
          return;
        }
      }
    }
  }

  public JLabel addTitle(@NotNull String title) {
    JLabel label = createLabel(title, null, null);
    label.setFont(myBoldLabelFont);
    addLineComponent(label, myRow++);
    return label;
  }

  public void addSeparator() {
    endGroup();
    if (myRow > 0) {
      // Never add a separator as the first element.
      addLineComponent(new JSeparator(), myRow++);
    }
  }

  /**
   * Add a component that also serves as a group node in the inspector.
   * @param labelText the label for the component
   * @param tooltip the tooltip for the attribute being edited by the component
   * @param component the editor component
   * @param keySource the component that will have focus for this component
   * @return a JLabel for the label of the component
   */
  public JLabel addExpandableComponent(@NotNull String labelText,
                                       @Nullable String tooltip,
                                       @NotNull Component component,
                                       @NotNull Component keySource) {
    JLabel label = createLabel(labelText, tooltip, component);
    addLabelComponent(label, myRow);
    addValueComponent(component, myRow++);
    startGroup(label, keySource);
    return label;
  }

  public JLabel addComponent(@NotNull String labelText,
                             @Nullable String tooltip,
                             @NotNull Component component) {
    JLabel label = createLabel(labelText, tooltip, component);
    addLabelComponent(label, myRow);
    addValueComponent(component, myRow++);
    return label;
  }

  /**
   * Adds a custom panel that spans the entire width, just set the preferred height on the panel
   */
  public void addPanel(@NotNull JComponent panel) {
    addLineComponent(panel, myRow++);
  }

  private static JLabel createLabel(@NotNull String labelText, @Nullable String tooltip, @Nullable Component component) {
    JBLabel label = new JBLabel(labelText);
    label.setLabelFor(component);
    label.setToolTipText(tooltip);
    return label;
  }

  private void addLineComponent(@NotNull Component component, int row) {
    addComponent(component, row, 0, 2, ANCHOR_WEST, FILL_HORIZONTAL);
  }

  private void addLabelComponent(@NotNull Component component, int row) {
    addComponent(component, row, 0, 1, ANCHOR_WEST, FILL_HORIZONTAL);
  }

  private void addValueComponent(@NotNull Component component, int row) {
    addComponent(component, row, 1, 1, ANCHOR_EAST, FILL_HORIZONTAL);
  }

  private void addComponent(@NotNull Component component, int row, int column, int columnSpan, int anchor, int fill) {
    addToGridPanel(myInspector, component, row, column, columnSpan, anchor, fill);
    if (myGroup != null) {
      myGroup.addComponent(component);
    }
  }

  private void addToGridPanel(@NotNull JPanel panel, @NotNull Component component,
                              int row, int column, int columnSpan, int anchor, int fill) {
    myConstraints.setRow(row);
    myConstraints.setColumn(column);
    myConstraints.setColSpan(columnSpan);
    myConstraints.setAnchor(anchor);
    myConstraints.setFill(fill);
    panel.add(component, myConstraints);
  }

  private void startGroup(@NotNull JLabel label, @NotNull Component keySource) {
    assert myGroup == null;
    myGroup = new ExpandableGroup(label);
    mySource2GroupMap.put(keySource, myGroup);
  }

  private void endGroup() {
    myGroup = null;
  }

  private static class ExpandableGroup {
    private static final String KEY_PREFIX = "inspector.open.";
    private static final Icon EXPANDED_ICON = (Icon)UIManager.get("Tree.expandedIcon");
    private static final Icon COLLAPSED_ICON = (Icon)UIManager.get("Tree.collapsedIcon");
    private final JLabel myLabel;
    private final List<Component> myComponents;
    private final boolean myInitiallyExpanded;

    public ExpandableGroup(@NotNull JLabel label) {
      myLabel = label;
      myComponents = new ArrayList<>(10);
      myInitiallyExpanded = PropertiesComponent.getInstance().getBoolean(KEY_PREFIX + label.getText());
      label.setIcon(myInitiallyExpanded ? EXPANDED_ICON : COLLAPSED_ICON);
      label.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent event) {
          setExpanded(!isExpanded());
        }
      });
    }

    public void addComponent(@NotNull Component component) {
      myComponents.add(component);
      component.setVisible(myInitiallyExpanded);
    }

    public boolean isExpanded() {
      return myLabel.getIcon() == EXPANDED_ICON;
    }

    public void setExpanded(boolean expanded) {
      myLabel.setIcon(expanded ? EXPANDED_ICON : COLLAPSED_ICON);
      myComponents.forEach(component -> component.setVisible(expanded));
      PropertiesComponent.getInstance().setValue(KEY_PREFIX + myLabel.getText(), expanded);
    }
  }

  /**
   * This is a hack to attempt to keep the column size fo the grid to 40% for the label and 60% for the editor.
   * We want to update the constraints before <code>doLayout</code> is called on the panel.
   */
  private static class GridInspectorPanel extends JPanel {
    private int myWidth;

    @Override
    public void setLayout(LayoutManager layoutManager) {
      myWidth = -1;
      super.setLayout(layoutManager);
    }

    @Override
    public void doLayout() {
      updateGridConstraints();
      super.doLayout();
    }

    private void updateGridConstraints() {
      LayoutManager layoutManager = getLayout();
      if (layoutManager instanceof GridLayoutManager) {
        GridLayoutManager gridLayoutManager = (GridLayoutManager)layoutManager;
        if (getWidth() != myWidth) {
          myWidth = getWidth();
          for (Component component : getComponents()) {
            GridConstraints constraints = gridLayoutManager.getConstraintsForComponent(component);
            if (constraints != null) {
              updateMinimumSize(constraints);
            }
          }
        }
      }
    }

    private void updateMinimumSize(@NotNull GridConstraints constraints) {
      if (constraints.getColSpan() == 1) {
        if (constraints.getColumn() == 0) {
          constraints.myMinimumSize.setSize((myWidth - 2 * HORIZONTAL_SPACING) * .4, -1);
        }
        else if (constraints.getColumn() == 1) {
          constraints.myMinimumSize.setSize((myWidth - 2 * HORIZONTAL_SPACING) * .6, -1);
        }
      }
    }
  }
}
