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
package com.android.tools.idea.uibuilder.property;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.editors.NlPropertyEditors;
import com.android.tools.idea.uibuilder.property.inspector.InspectorPanel;
import com.android.tools.idea.uibuilder.property.ptable.PTable;
import com.android.tools.idea.uibuilder.property.ptable.PTableItem;
import com.android.tools.idea.uibuilder.property.ptable.PTableModel;
import com.android.util.PropertiesMap;
import com.google.common.collect.Table;
import com.intellij.openapi.project.Project;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBCardLayout;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.TOOLS_URI;

public class NlPropertiesPanel extends JPanel implements ViewAllPropertiesAction.Model {
  private static final String CARD_ADVANCED = "table";
  private static final String CARD_DEFAULT = "default";

  private final PTable myTable;
  private final JPanel myTablePanel;
  private final PTableModel myModel;
  private final InspectorPanel myInspectorPanel;

  private final JPanel myCardPanel;

  private List<NlComponent> myComponents;
  private List<NlPropertyItem> myProperties;
  private boolean myAllPropertiesPanelVisible;

  public NlPropertiesPanel(@NotNull Project project) {
    super(new BorderLayout());
    setOpaque(true);
    setFocusable(true);
    setRequestFocusEnabled(true);
    setBackground(UIUtil.TRANSPARENT_COLOR);

    myModel = new PTableModel();

    myTable = new PTable(myModel);
    myTable.setEditorProvider(new NlPropertyEditors(project));
    myTable.getEmptyText().setText("No selected component");
    JComponent fewerPropertiesLink = createViewAllPropertiesLinkPanel(false);
    fewerPropertiesLink.setBorder(BorderFactory.createEmptyBorder(8, 4, 2, 0));
    myTablePanel = new JPanel(new BorderLayout());
    myTablePanel.setVisible(false);
    myTablePanel.setBackground(myTable.getBackground());
    myTablePanel.add(myTable, BorderLayout.NORTH);
    myTablePanel.add(fewerPropertiesLink, BorderLayout.SOUTH);

    myInspectorPanel = new InspectorPanel(project, createViewAllPropertiesLinkPanel(true));

    myCardPanel = new JPanel(new JBCardLayout());

    add(myCardPanel, BorderLayout.CENTER);

    myCardPanel.add(CARD_DEFAULT, ScrollPaneFactory.createScrollPane(myInspectorPanel,
                                                                     ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                                     ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER));
    myCardPanel.add(CARD_ADVANCED, ScrollPaneFactory.createScrollPane(myTablePanel));
    myComponents = Collections.emptyList();
    myProperties = Collections.emptyList();
  }

  public void setItems(@NotNull List<NlComponent> components,
                       @NotNull Table<String, String, NlPropertyItem> properties,
                       @NotNull NlPropertiesManager propertiesManager) {
    myComponents = components;
    myProperties = extractPropertiesForTable(properties);

    List<PTableItem> groupedProperties;
    if (components.isEmpty()) {
      groupedProperties = Collections.emptyList();
    }
    else {
      List<NlPropertyItem> sortedProperties = new NlPropertiesSorter().sort(myProperties, components);
      groupedProperties = new NlPropertiesGrouper().group(sortedProperties, components);
    }
    if (myTable.isEditing()) {
      myTable.removeEditor();
    }
    myModel.setItems(groupedProperties);

    updateDefaultProperties(propertiesManager);
    myInspectorPanel.setComponent(components, properties, propertiesManager);
    myTablePanel.setVisible(!groupedProperties.isEmpty());
  }

  @NotNull
  private static List<NlPropertyItem> extractPropertiesForTable(@NotNull Table<String, String, NlPropertyItem> properties) {
    Map<String, NlPropertyItem> androidProperties = properties.row(SdkConstants.ANDROID_URI);
    Map<String, NlPropertyItem> autoProperties = properties.row(SdkConstants.AUTO_URI);
    Map<String, NlPropertyItem> designProperties = properties.row(TOOLS_URI);
    Map<String, NlPropertyItem> bareProperties = properties.row("");

    // Include all auto (app) properties and all android properties that are not also auto properties.
    List<NlPropertyItem> result = new ArrayList<>(properties.size());
    result.addAll(autoProperties.values());
    for (Map.Entry<String, NlPropertyItem> entry : androidProperties.entrySet()) {
      if (!autoProperties.containsKey(entry.getKey())) {
        result.add(entry.getValue());
      }
    }
    result.addAll(designProperties.values());
    result.addAll(bareProperties.values());
    return result;
  }

  public void modelRendered(@NotNull NlPropertiesManager propertiesManager) {
    updateDefaultProperties(propertiesManager);
    myInspectorPanel.refresh();
  }

  private void updateDefaultProperties(@NotNull NlPropertiesManager propertiesManager) {
    if (myComponents.isEmpty() || myProperties.isEmpty()) {
      return;
    }
    PropertiesMap defaultValues = propertiesManager.getDefaultProperties(myComponents);
    if (defaultValues.isEmpty()) {
      return;
    }
    for (NlPropertyItem property : myProperties) {
      property.setDefaultValue(getDefaultProperty(defaultValues, property));
    }
  }

  @Nullable
  private static PropertiesMap.Property getDefaultProperty(@NotNull PropertiesMap defaultValues, @NotNull NlProperty property) {
    if (SdkConstants.ANDROID_URI.equals(property.getNamespace())) {
      PropertiesMap.Property defaultValue = defaultValues.get(SdkConstants.PREFIX_ANDROID + property.getName());
      if (defaultValue != null) {
        return defaultValue;
      }
      return defaultValues.get(SdkConstants.ANDROID_PREFIX + property.getName());
    }
    return defaultValues.get(property.getName());
  }

  @NotNull
  private JComponent createViewAllPropertiesLinkPanel(boolean viewAllProperties) {
    HyperlinkLabel textLink = new HyperlinkLabel();
    textLink.setHyperlinkText(viewAllProperties ? ViewAllPropertiesAction.VIEW_ALL_PROPERTIES : ViewAllPropertiesAction.VIEW_FEWER_PROPERTIES);
    textLink.addHyperlinkListener(event -> setAllPropertiesPanelVisible(event, viewAllProperties));
    HyperlinkLabel iconLink = new HyperlinkLabel();
    iconLink.setIcon(AndroidIcons.NeleIcons.ToggleProperties);
    iconLink.setUseIconAsLink(true);
    iconLink.addHyperlinkListener(event -> setAllPropertiesPanelVisible(event, viewAllProperties));
    JPanel linkPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    linkPanel.setOpaque(false);
    linkPanel.add(textLink);
    linkPanel.add(iconLink);
    return linkPanel;
  }

  private void setAllPropertiesPanelVisible(@NotNull HyperlinkEvent event, boolean viewAllProperties) {
    if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
      setAllPropertiesPanelVisible(viewAllProperties);
    }
  }

  @Override
  public boolean isAllPropertiesPanelVisible() {
    return myAllPropertiesPanelVisible;
  }

  @Override
  public void setAllPropertiesPanelVisible(boolean viewAllProperties) {
    myAllPropertiesPanelVisible = viewAllProperties;
    JBCardLayout cardLayout = (JBCardLayout)myCardPanel.getLayout();
    String name = viewAllProperties ? CARD_ADVANCED : CARD_DEFAULT;
    cardLayout.swipe(myCardPanel, name, JBCardLayout.SwipeDirection.AUTO);
  }

  public boolean activatePreferredEditor(boolean afterload) {
    if (isAllPropertiesPanelVisible()) {
      setAllPropertiesPanelVisible(false);
    }
    return myInspectorPanel.activatePreferredEditor(afterload);
  }
}
