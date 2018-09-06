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

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.property.PropertiesManager;
import com.android.tools.idea.common.property.inspector.InspectorComponent;
import com.android.tools.idea.common.property.inspector.InspectorPanel;
import com.android.tools.idea.common.property.inspector.InspectorProvider;
import com.android.tools.idea.uibuilder.handlers.constraint.WidgetConstraintPanel;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.common.property.editors.NlComponentEditor;
import com.android.tools.idea.uibuilder.property.editors.NlEnumEditor;
import com.android.tools.idea.uibuilder.property.editors.NlReferenceEditor;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.*;
import static com.android.SdkConstants.PreferenceTags.*;
import static com.android.tools.idea.uibuilder.property.editors.NlEditingListener.DEFAULT_LISTENER;

public class IdInspectorProvider implements InspectorProvider<NlPropertiesManager> {
  private IdInspectorComponent myComponent;

  @Override
  public boolean isApplicable(@NotNull List<NlComponent> components,
                              @NotNull Map<String, NlProperty> properties,
                              @NotNull NlPropertiesManager propertiesManager) {
    for (NlComponent component : components) {
      switch (component.getTagName()) {
        case CHECK_BOX_PREFERENCE:
        case EDIT_TEXT_PREFERENCE:
        case LIST_PREFERENCE:
        case MULTI_SELECT_LIST_PREFERENCE:
        case PREFERENCE_CATEGORY:
        case PREFERENCE_SCREEN:
        case RINGTONE_PREFERENCE:
        case SWITCH_PREFERENCE:
        case TAG_GROUP:
        case TAG_ITEM:
        case TAG_MENU:
          return false;
      }
    }
    return true;
  }

  @NotNull
  @Override
  public IdInspectorComponent createCustomInspector(@NotNull List<NlComponent> components,
                                                    @NotNull Map<String, NlProperty> properties,
                                                    @NotNull NlPropertiesManager propertiesManager) {
    if (myComponent == null) {
      myComponent = new IdInspectorComponent(propertiesManager);
    }
    myComponent.updateProperties(components, properties, propertiesManager);
    return myComponent;
  }

  @Override
  public void resetCache() {
    myComponent = null;
  }

  static class IdInspectorComponent implements InspectorComponent<NlPropertiesManager> {
    private final NlReferenceEditor myIdEditor;
    private final NlEnumEditor myWidthEditor;
    private final NlEnumEditor myHeightEditor;
    private final WidgetConstraintPanel myConstraintWidgetPanel;

    private NlProperty myIdAttr;
    private NlProperty myLayoutWidth;
    private NlProperty myLayoutHeight;

    IdInspectorComponent(@NotNull PropertiesManager propertiesManager) {
      myIdEditor = NlReferenceEditor.createForInspector(propertiesManager.getProject(), DEFAULT_LISTENER);
      myWidthEditor = NlEnumEditor.createForInspectorWithBrowseButton(DEFAULT_LISTENER);
      myHeightEditor = NlEnumEditor.createForInspectorWithBrowseButton(DEFAULT_LISTENER);
      myConstraintWidgetPanel = new WidgetConstraintPanel(ImmutableList.of());
    }

    @Override
    public void updateProperties(@NotNull List<NlComponent> components,
                                 @NotNull Map<String, NlProperty> properties,
                                 @NotNull NlPropertiesManager propertiesManager) {
      myIdAttr = properties.get(ATTR_ID);
      myLayoutWidth = properties.get(ATTR_LAYOUT_WIDTH);
      myLayoutHeight = properties.get(ATTR_LAYOUT_HEIGHT);
      myConstraintWidgetPanel.updateComponents(components);
    }

    @Override
    public int getMaxNumberOfRows() {
      return 4;
    }

    @Override
    public void attachToInspector(@NotNull InspectorPanel inspector) {
      myIdEditor.setLabel(inspector.addComponent("ID", null, myIdEditor.getComponent()));
      if (myConstraintWidgetPanel.isApplicable()) {
        inspector.addPanel(myConstraintWidgetPanel);
      }
      myWidthEditor.setLabel(inspector.addComponent(ATTR_LAYOUT_WIDTH, null, myWidthEditor.getComponent()));
      myHeightEditor.setLabel(inspector.addComponent(ATTR_LAYOUT_HEIGHT, null, myHeightEditor.getComponent()));
      refresh();
    }

    @Override
    public void refresh() {
      myIdEditor.setEnabled(myIdAttr != null);
      if (myIdAttr != null) {
        myIdEditor.setProperty(myIdAttr);
        setToolTip(myIdEditor, myIdAttr);
      }
      myWidthEditor.setEnabled(myLayoutWidth != null);
      if (myLayoutWidth != null) {
        myWidthEditor.setProperty(myLayoutWidth);
        setToolTip(myWidthEditor, myLayoutWidth);
      }
      myHeightEditor.setEnabled(myLayoutHeight != null);
      if (myLayoutHeight != null) {
        myHeightEditor.setProperty(myLayoutHeight);
        setToolTip(myHeightEditor, myLayoutHeight);
      }
      if (myIdAttr != null && !myIdAttr.getComponents().isEmpty()) {
        myConstraintWidgetPanel.setProperty(myIdAttr);
      }
    }

    @Override
    @NotNull
    public List<NlComponentEditor> getEditors() {
      return ImmutableList.of(myIdEditor, myWidthEditor, myHeightEditor);
    }

    @TestOnly
    public WidgetConstraintPanel getConstraintPanel() {
      return myConstraintWidgetPanel;
    }

    private static void setToolTip(@NotNull NlComponentEditor editor, @NotNull NlProperty property) {
      JLabel label = editor.getLabel();
      if (label != null) {
        label.setToolTipText(property.getTooltipText());
      }
    }
  }
}
