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
package com.android.tools.idea.uibuilder.property;

import com.android.SdkConstants;
import com.android.ide.common.resources.ResourceItem;
import com.android.tools.adtui.workbench.ToolWindowCallback;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.common.property.PropertiesManager;
import com.android.tools.idea.common.property.inspector.InspectorPanel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.property.editors.NlPropertyEditors;
import com.android.tools.idea.uibuilder.property.inspector.NlInspectorProviders;
import com.android.tools.idea.uibuilder.scene.RenderListener;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.KeyListener;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.uibuilder.property.ToggleXmlPropertyEditor.NL_XML_PROPERTY_EDITOR;

public class NlPropertiesManager extends PropertiesManager<NlPropertiesManager> implements RenderListener {
  private NlInspectorProviders myInspectorProviders;

  public NlPropertiesManager(@NotNull AndroidFacet facet, @Nullable DesignSurface designSurface, @NotNull Disposable parentDisposable) {
    super(facet, designSurface, NlPropertyEditors.getInstance(facet.getModule().getProject()), parentDisposable);
  }

  @NotNull
  @Override
  protected NlPropertiesPanel getPropertiesPanel() {
    return (NlPropertiesPanel)super.getPropertiesPanel();
  }

  // TODO:
  public void activatePropertySheet() {
    getPropertiesPanel().activatePropertySheet();
  }

  // TODO:
  public void activateInspector() {
    getPropertiesPanel().activateInspector();
  }

  @Override
  public void registerCallbacks(@NotNull ToolWindowCallback toolWindow) {
    getPropertiesPanel().registerToolWindow(toolWindow);
  }

  @NotNull
  @Override
  public List<AnAction> getGearActions() {
    return ImmutableList.of(new ToggleXmlPropertyEditor(this));
  }

  @NotNull
  @Override
  public List<AnAction> getAdditionalActions() {
    return Collections.singletonList(new ViewAllPropertiesAction(getPropertiesPanel()));
  }

  @Override
  public boolean supportsFiltering() {
    return true;
  }

  @Override
  public void setFilter(@NotNull String filter) {
    getPropertiesPanel().setFilter(filter);
  }

  @Override
  @NotNull
  public KeyListener getFilterKeyListener() {
    return getPropertiesPanel().getFilterKeyListener();
  }

  @Override
  protected void setSceneView(@Nullable SceneView sceneView) {
    if (sceneView == mySceneView) {
      return;
    }
    assert sceneView == null || sceneView instanceof ScreenView : "NlPropertiesManager can only handle ScreenViews";

    if (mySceneView != null) {
      ((ScreenView)mySceneView).getSceneManager().removeRenderListener(this);
    }

    super.setSceneView(sceneView);

    if (mySceneView != null) {
      ((ScreenView)mySceneView).getSceneManager().addRenderListener(this);
    }
  }

  @NotNull
  public InspectorPanel getInspector() {
    return getPropertiesPanel().getInspector();
  }


  @Override
  public void propertyChanged(@NotNull NlProperty property, @Nullable String oldValue, @Nullable String newValue) {
    if (property.getComponents().size() == 1 &&
        SdkConstants.VIEW_MERGE.equals(property.getComponents().get(0).getTagName()) &&
        SdkConstants.TOOLS_URI.equals(property.getNamespace()) &&
        SdkConstants.ATTR_PARENT_TAG.equals(property.getName())) {
      // Special case: When the tools:parentTag is updated on a <merge> tag, the set of attributes for
      // the <merge> tag may change e.g. if the value is set to "LinearLayout" the <merge> tag will
      // then have all attributes from a <LinearLayout>. Force an update of all properties and reset
      // cached components in the inspector providers.
      if (myInspectorProviders != null) {
        myInspectorProviders.resetCache();
      }
      updateSelection();
      return;
    }

    if (PropertiesComponent.getInstance().getBoolean(NL_XML_PROPERTY_EDITOR) &&
        (NlPropertyItem.isReference(oldValue) || NlPropertyItem.isReference(newValue))) {
      updateSelection();
    }
  }

  @Override
  @SuppressWarnings("unused")
  public void resourceChanged(@NotNull ResourceItem item, @Nullable String oldValue, @Nullable String newValue) {
    if (PropertiesComponent.getInstance().getBoolean(NL_XML_PROPERTY_EDITOR) &&
        (NlPropertyItem.isReference(oldValue) || NlPropertyItem.isReference(newValue))) {
      updateSelection();
    }
  }

  @Override
  public void logPropertyChange(@NotNull NlProperty property) {
    //NlUsageTracker.getInstance(getDesignSurface()).logPropertyChange(
    //  property,
    //  getPropertiesPanel().getPropertiesViewMode(),
    //  getPropertiesPanel().getFilterMatchCount());
  }

  /**
   * Find the preferred attribute of the component specified,
   * and bring focus to the editor of this attribute in the inspector.
   * If the inspector is not the current attribute panel: make it the active panel,
   * if the attributes tool window is minimized: restore the attributes tool window.
   *
   * @return true if a preferred attribute was identified and an editor will eventually gain focus in the inspector.
   * false if no preferred attribute was identified.
   */
  @Override
  public boolean activatePreferredEditor(@NotNull DesignSurface surface, @NotNull NlComponent component) {
    ViewHandler handler = NlComponentHelperKt.getViewHandler(component);
    String propertyName = handler != null ? handler.getPreferredProperty() : null;
    if (propertyName == null) {
      return false;
    }
    getPropertiesPanel().activatePreferredEditor(propertyName, myLoading);
    return true;
  }

  @Override
  public void setToolContext(@Nullable DesignSurface designSurface) {
    super.setToolContext(designSurface);
    if (designSurface != null || isPropertiesPanelCreated()) {
      getPropertiesPanel().setToolContext(designSurface);
    }
  }

  @Override
  public void showAccessoryPanel(@NotNull DesignSurface surface, boolean show) {
    getPropertiesPanel().showAccessoryPanel(show);
  }

  @Override
  public void onRenderCompleted() {
    getPropertiesPanel().modelRendered();
  }

  @NotNull
  @Override
  protected NlPropertiesPanel createPropertiesPanel() {
    return new NlPropertiesPanel(this);
  }

  @NotNull
  @Override
  public NlInspectorProviders getInspectorProviders(@NotNull Disposable parentDisposable) {
    if (myInspectorProviders == null) {
      myInspectorProviders = new NlInspectorProviders(this, parentDisposable);
    }
    return myInspectorProviders;
  }
}
