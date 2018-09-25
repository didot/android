/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.common.property.editors.NlComponentEditor;
import com.android.tools.idea.common.property.inspector.InspectorComponent;
import com.android.tools.idea.common.property.inspector.InspectorPanel;
import com.android.tools.idea.common.property.inspector.InspectorProvider;
import com.android.tools.idea.uibuilder.api.CustomPanel;
import com.android.tools.idea.uibuilder.api.PropertyComponentHandler;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LayoutCustomPanelInspectorProvider implements InspectorProvider<NlPropertiesManager> {

  Map<String, CustomPanel> myCachedCustomComponents = new HashMap<>();

  @Override
  public boolean isApplicable(@NotNull List<NlComponent> components,
                              @NotNull Map<String, NlProperty> properties,
                              @NotNull NlPropertiesManager propertiesManager) {
    if (components.size() != 1) {
      return false;
    }

    NlComponent parent = components.get(0).getParent();
    if (parent == null) {
      return false;
    }

    PropertyComponentHandler handler = NlComponentHelperKt.getViewHandler(parent);
    if (handler == null) {
      return false;
    }

    String parentTagName = parent.getTagName();
    if (myCachedCustomComponents.containsKey(parentTagName)) {
      return true;
    }
    CustomPanel customComponent = handler.getLayoutCustomPanel();
    if (customComponent == null) {
      return false;
    }
    myCachedCustomComponents.put(parentTagName, customComponent);
    return true;
  }

  @NotNull
  @Override
  public InspectorComponent<NlPropertiesManager> createCustomInspector(@NotNull List<NlComponent> components,
                                                                       @NotNull Map<String, NlProperty> properties,
                                                                       @NotNull NlPropertiesManager propertiesManager) {
    assert components.size() == 1;

    NlComponent parent = components.get(0).getParent();

    assert parent != null;

    String parentTagName = parent.getTagName();
    CustomPanel customPanel = myCachedCustomComponents.get(parentTagName);

    assert customPanel != null;

    InspectorComponent<NlPropertiesManager> inspector = new LayoutCustomPanelInspectorComponent(customPanel);
    inspector.updateProperties(components, properties, propertiesManager);
    return inspector;
  }

  @Override
  public void resetCache() {
    myCachedCustomComponents.clear();
  }

  private static class LayoutCustomPanelInspectorComponent implements InspectorComponent<NlPropertiesManager> {
    private final CustomPanel myPanel;
    private NlComponent myComponent;

    public LayoutCustomPanelInspectorComponent(CustomPanel panel) {
      myPanel = panel;
    }

    @Override
    public void updateProperties(@NotNull List<NlComponent> components,
                                 @NotNull Map<String, NlProperty> properties,
                                 @NotNull NlPropertiesManager propertiesManager) {
      assert components.size() == 1;
      myComponent = components.get(0);
    }

    @Override
    @NotNull
    public List<NlComponentEditor> getEditors() {
      return Collections.emptyList();
    }

    @Override
    public int getMaxNumberOfRows() {
      return 1;
    }

    @Override
    public void attachToInspector(@NotNull InspectorPanel inspector) {
      refresh();
      inspector.addPanel(myPanel.getPanel());
    }

    @Override
    public void refresh() {
      myPanel.useComponent(myComponent);
    }
  }
}
