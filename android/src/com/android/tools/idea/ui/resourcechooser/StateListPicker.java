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
package com.android.tools.idea.ui.resourcechooser;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.AbstractResourceRepository;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.editors.theme.attributes.editors.DrawableRendererEditor;
import com.android.tools.idea.editors.theme.attributes.editors.GraphicalResourceRendererEditor;
import com.android.tools.idea.editors.theme.ui.ResourceComponent;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.res.ResourceHelper;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.List;

public class StateListPicker extends JPanel {
  private static final EnumSet<ResourceType> DIMENSIONS_ONLY = EnumSet.of(ResourceType.DIMEN);

  private final Module myModule;
  private final Configuration myConfiguration;
  private @Nullable ResourceHelper.StateList myStateList;
  private List<StateComponent> myStateComponents;
  private final @NotNull RenderTask myRenderTask;

  private boolean myIsBackgroundStateList;
  /** If not empty, it contains colors to compare with the state list items colors to find out any possible contrast problems,
   *  and descriptions to use in case there is a problem. */
  private @NotNull ImmutableMap<String, Color> myContrastColorsWithDescription = ImmutableMap.of();

  public StateListPicker(@Nullable ResourceHelper.StateList stateList,
                         @NotNull Module module,
                         @NotNull Configuration configuration) {

    myModule = module;
    myConfiguration = configuration;
    myRenderTask = DrawableRendererEditor.configureRenderTask(module, configuration);
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    if (stateList != null) {
      setStateList(stateList);
    }
  }

  public void setStateList(@NotNull ResourceHelper.StateList stateList) {
    myStateList = stateList;
    myStateComponents = Lists.newArrayListWithCapacity(myStateList.getStates().size());
    removeAll();
    if (myStateList.getStates().isEmpty()) {
      add(new JLabel("Empty " + myStateList.getType() + " StateList"));
    }
    for (final ResourceHelper.StateListState state : myStateList.getStates()) {
      final StateComponent stateComponent = createStateComponent(state);
      add(stateComponent);
    }
    revalidate();
    repaint();
  }

  @NotNull
  private StateComponent createStateComponent(@NotNull ResourceHelper.StateListState state) {
    final StateComponent stateComponent = new StateComponent(myModule.getProject());
    myStateComponents.add(stateComponent);

    String stateValue = state.getValue();
    String alphaValue = state.getAlpha();

    stateComponent.addValueActionListener(new ValueActionListener(state, stateComponent));
    stateComponent.addAlphaActionListener(new AlphaActionListener(state, stateComponent));

    stateComponent.setValueText(stateValue);
    stateComponent.setAlphaValue(alphaValue);

    stateComponent.setAlphaVisible(!StringUtil.isEmpty(alphaValue));

    stateComponent.setNameText(state.getDescription());
    stateComponent.setComponentPopupMenu(createAlphaPopupMenu(state, stateComponent));

    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assert facet != null;
    assert myStateList != null;
    List<String> completionStrings = ResourceHelper.getCompletionFromTypes(facet, myStateList.getFolderType() == ResourceFolderType.COLOR
                                                                                  ? GraphicalResourceRendererEditor.COLORS_ONLY
                                                                                  : GraphicalResourceRendererEditor.DRAWABLES_ONLY);
    stateComponent.getResourceComponent().setCompletionStrings(completionStrings);
    stateComponent.getAlphaComponent().setCompletionStrings(ResourceHelper.getCompletionFromTypes(facet, DIMENSIONS_ONLY));

    return stateComponent;
  }

  @NotNull
  private JBPopupMenu createAlphaPopupMenu(@NotNull final ResourceHelper.StateListState state,
                                           @NotNull final StateComponent stateComponent) {
    JBPopupMenu popupMenu = new JBPopupMenu();
    final JMenuItem deleteAlpha = new JMenuItem("Delete alpha");
    popupMenu.add(deleteAlpha);
    deleteAlpha.setVisible(!StringUtil.isEmpty(state.getAlpha()));

    final JMenuItem createAlpha = new JMenuItem("Create alpha");
    popupMenu.add(createAlpha);
    createAlpha.setVisible(StringUtil.isEmpty(state.getAlpha()));

    deleteAlpha.addActionListener(e -> {
      stateComponent.getAlphaComponent().setVisible(false);
      stateComponent.setAlphaValue(null);
      state.setAlpha(null);
      updateIcon(stateComponent);
      deleteAlpha.setVisible(false);
      createAlpha.setVisible(true);
    });

    createAlpha.addActionListener(e -> {
      AlphaActionListener listener = stateComponent.getAlphaActionListener();
      if (listener == null) {
        return;
      }
      listener.actionPerformed(new ActionEvent(stateComponent.getAlphaComponent(), ActionEvent.ACTION_PERFORMED, null));
      if (!StringUtil.isEmpty(state.getAlpha())) {
        stateComponent.getAlphaComponent().setVisible(true);
        createAlpha.setVisible(false);
        deleteAlpha.setVisible(true);
      }
    });

    return popupMenu;
  }

  /**
   * Returns a {@link ValidationInfo} in the case one of the state list state has a value that does not resolve to a valid resource,
   * or a value that is a private framework value. or if one of the state list component requires an API level higher than minApi.
   */
  @Nullable/*if there is no error*/
  public ValidationInfo doValidate(int minApi) {
    IAndroidTarget target = myConfiguration.getRealTarget();
    assert target != null;
    final AndroidTargetData androidTargetData = AndroidTargetData.getTargetData(target, myModule);
    assert androidTargetData != null;
    AbstractResourceRepository frameworkResources = myConfiguration.getFrameworkResources();
    assert frameworkResources != null;

    for (StateComponent component : myStateComponents) {
      ValidationInfo error = component.doValidate(minApi, androidTargetData);
      if (error != null) {
        return error;
      }
    }
    return null;
  }

  public void setContrastParameters(@NotNull ImmutableMap<String, Color> contrastColorsWithDescription, boolean isBackgroundStateList) {
    myContrastColorsWithDescription = contrastColorsWithDescription;
    myIsBackgroundStateList = isBackgroundStateList;
  }

  @Nullable
  public ResourceHelper.StateList getStateList() {
    return myStateList;
  }

  class ValueActionListener implements ActionListener, DocumentListener {
    private final ResourceHelper.StateListState myState;
    private final StateComponent myComponent;

    public ValueActionListener(ResourceHelper.StateListState state, StateComponent stateComponent) {
      myState = state;
      myComponent = stateComponent;
    }

    /**
     * @see AlphaActionListener#documentChanged(DocumentEvent)
     */
    @Override
    public void documentChanged(DocumentEvent e) {
      myState.setValue(myComponent.getResourceValue());
      // This is run inside a WriteAction and updateIcon may need an APP_RESOURCES_LOCK from AndroidFacet.
      // To prevent a potential deadlock, we call updateIcon in another thread.
      ApplicationManager.getApplication().invokeLater(() -> {
        updateIcon(myComponent);
        myComponent.repaint();
      }, ModalityState.any());
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      ResourceComponent resourceComponent = myComponent.getResourceComponent();

      final String attributeValue = resourceComponent.getValueText();
      ResourceUrl attributeValueUrl = ResourceUrl.parse(attributeValue);
      boolean isFrameworkValue = attributeValueUrl != null && attributeValueUrl.isFramework();
      String nameSuggestion = attributeValueUrl != null ? attributeValueUrl.name : attributeValue;

      EnumSet<ResourceType> allowedTypes;
      assert myStateList != null;
      if (myStateList.getFolderType() == ResourceFolderType.COLOR) {
        allowedTypes = GraphicalResourceRendererEditor.COLORS_ONLY;
      }
      else {
        allowedTypes = GraphicalResourceRendererEditor.DRAWABLES_ONLY;
      }

      ChooseResourceDialog.ResourceNameVisibility resourceNameVisibility = ChooseResourceDialog.ResourceNameVisibility.FORCE;
      if (nameSuggestion.startsWith("#")) {
        nameSuggestion = null;
        resourceNameVisibility = ChooseResourceDialog.ResourceNameVisibility.SHOW;
      }

      ChooseResourceDialog dialog = ChooseResourceDialog.builder()
        .setModule(myModule)
        .setTypes(allowedTypes)
        .setCurrentValue(attributeValue)
        .setIsFrameworkValue(isFrameworkValue)
        .setResourceNameVisibility(resourceNameVisibility)
        .setResourceNameSuggestion(nameSuggestion)
        .setConfiguration(myConfiguration)
        .build();


      if (!myContrastColorsWithDescription.isEmpty()) {
        dialog
          .setContrastParameters(myContrastColorsWithDescription, myIsBackgroundStateList, !myStateList.getDisabledStates().contains(myState));
      }

      dialog.show();

      if (dialog.isOK()) {
        String resourceName = dialog.getResourceName();
        myComponent.setValueText(resourceName);

        // If a resource was overridden, it may affect several states of the state list.
        // Thus we need to repaint all components.
        repaintAllComponents();
      }
    }
  }

  private class AlphaActionListener implements ActionListener, DocumentListener {
    private final ResourceHelper.StateListState myState;
    private final StateComponent myComponent;

    public AlphaActionListener(ResourceHelper.StateListState state, StateComponent stateComponent) {
      myState = state;
      myComponent = stateComponent;
    }

    /**
     * @see ValueActionListener#documentChanged(DocumentEvent)
     */
    @Override
    public void documentChanged(DocumentEvent e) {
      myState.setAlpha(myComponent.getAlphaValue());
      // This is run inside a WriteAction and updateIcon may need an APP_RESOURCES_LOCK from AndroidFacet.
      // To prevent a potential deadlock, we call updateIcon in another thread.
      ApplicationManager.getApplication().invokeLater(() -> {
        updateIcon(myComponent);
        myComponent.repaint();
      }, ModalityState.any());
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      ResourceSwatchComponent source = myComponent.getAlphaComponent();
      String itemValue = source.getText();

      ResourceResolver resourceResolver = myConfiguration.getResourceResolver();
      assert resourceResolver != null;

      ResourceValue resValue = resourceResolver.findResValue(itemValue, false);
      String resolvedResource = resValue != null ? resourceResolver.resolveResValue(resValue).getName() : itemValue;

      ChooseResourceDialog dialog = ChooseResourceDialog.builder()
        .setModule(myModule)
        .setTypes(DIMENSIONS_ONLY)
        .setCurrentValue(resolvedResource)
        .setConfiguration(myConfiguration)
        .build();

      dialog.show();

      if (dialog.isOK()) {
        String resourceName = dialog.getResourceName();
        myState.setAlpha(resourceName);
        myComponent.setAlphaValue(resourceName);

        // If a resource was overridden, it may affect several states of the state list.
        // Thus we need to repaint all components.
        repaintAllComponents();
      }
    }
  }

  private void updateIcon(@NotNull StateComponent component) {
    component.showAlphaError(false);

    ResourceResolver resourceResolver = myConfiguration.getResourceResolver();
    assert resourceResolver != null;
    component.setValueIcon(getSwatchIcon(component.getResourceValue(), resourceResolver, myRenderTask));

    String alphaValue = component.getAlphaValue();
    if (!StringUtil.isEmpty(alphaValue)) {
      try {
        float alpha = Float.parseFloat(ResourceHelper.resolveStringValue(resourceResolver, alphaValue));
        Font iconFont = JBUI.Fonts.smallFont().asBold();
        component.getAlphaComponent().setSwatchIcon(new ResourceSwatchComponent.TextIcon(String.format("%.2f", alpha), iconFont));
      }
      catch (NumberFormatException e) {
        component.showAlphaError(true);
        component.getAlphaComponent().setSwatchIcon(ResourceSwatchComponent.WARNING_ICON);
      }
    }
    else {
      Font iconFont = JBUI.Fonts.smallFont().asBold();
      component.getAlphaComponent().setSwatchIcon(new ResourceSwatchComponent.TextIcon("1.00", iconFont));
    }
  }

  @NotNull
  public static ResourceSwatchComponent.SwatchIcon getSwatchIcon(@Nullable String resourceName, @NotNull ResourceResolver resourceResolver, @NotNull RenderTask renderTask) {
    ResourceValue resValue = resourceResolver.findResValue(resourceName, false);
    resValue = resourceResolver.resolveResValue(resValue);

    if (resValue == null || resValue.getResourceType() == ResourceType.COLOR) {
      final List<Color> colors = ResourceHelper.resolveMultipleColors(resourceResolver, resValue, renderTask.getModule().getProject());
      ResourceSwatchComponent.SwatchIcon icon;
      if (colors.isEmpty()) {
        Color colorValue = ResourceHelper.parseColor(resourceName);
        if (colorValue != null) {
          icon = new ResourceSwatchComponent.ColorIcon(colorValue);
        }
        else {
          icon = ResourceSwatchComponent.WARNING_ICON;
        }
      }
      else {
        icon = new ResourceSwatchComponent.ColorIcon(Iterables.getLast(colors));
        icon.setIsStack(colors.size() > 1);
      }
      return icon;
    }

    List<BufferedImage> images = renderTask.renderDrawableAllStates(resValue);
    ResourceSwatchComponent.SwatchIcon icon;
    if (images.isEmpty()) {
      icon = ResourceSwatchComponent.WARNING_ICON;
    }
    else {
      icon = new ResourceSwatchComponent.SquareImageIcon(Iterables.getLast(images));
      icon.setIsStack(images.size() > 1);
    }
    return icon;
  }

  private void repaintAllComponents() {
    for (StateComponent component : myStateComponents) {
      updateIcon(component);
      component.repaint();
    }
  }

  private class StateComponent extends Box {
    private final ResourceComponent myResourceComponent;
    private final ResourceSwatchComponent myAlphaComponent;
    private final JBLabel myAlphaErrorLabel;
    private AlphaActionListener myAlphaActionListener;

    public StateComponent(@NotNull Project project) {
      super(BoxLayout.PAGE_AXIS);

      myResourceComponent = new ResourceComponent(project, true);
      add(myResourceComponent);

      myAlphaComponent = new ResourceSwatchComponent(project, true);
      add(myAlphaComponent);

      Font font = StateListPicker.this.getFont();
      setFont(ThemeEditorUtils.scaleFontForAttribute(font));

      Box alphaErrorComponent = new Box(BoxLayout.LINE_AXIS);
      myAlphaErrorLabel =
        new JBLabel("This value does not resolve to a floating-point number.", AllIcons.General.BalloonWarning, SwingConstants.LEADING);
      myAlphaErrorLabel.setVisible(false);
      alphaErrorComponent.add(myAlphaErrorLabel);
      alphaErrorComponent.add(Box.createHorizontalGlue());
      add(alphaErrorComponent);
    }

    @NotNull
    public ResourceComponent getResourceComponent() {
      return myResourceComponent;
    }

    @NotNull
    public ResourceSwatchComponent getAlphaComponent() {
      return myAlphaComponent;
    }

    public void showAlphaError(boolean hasError) {
      myAlphaErrorLabel.setVisible(hasError);
      myAlphaComponent.setWarningBorder(hasError);
    }

    public void setNameText(@NotNull String name) {
      myResourceComponent.setNameText(name);
    }

    public void setValueText(@NotNull String value) {
      myResourceComponent.setValueText(value);
      updateIcon(this);
    }

    public void setAlphaValue(@Nullable String alphaValue) {
      myAlphaComponent.setText(Strings.nullToEmpty(alphaValue));
    }

    public void setAlphaVisible(boolean isVisible) {
      myAlphaComponent.setVisible(isVisible);
    }

    public void setValueIcon(@NotNull ResourceSwatchComponent.SwatchIcon icon) {
      myResourceComponent.setSwatchIcon(icon);
    }

    @NotNull
    public String getResourceValue() {
      return myResourceComponent.getValueText();
    }

    @Nullable
    public String getAlphaValue() {
      return myAlphaComponent.getText();
    }

    public void addValueActionListener(@NotNull ValueActionListener listener) {
      myResourceComponent.addSwatchListener(listener);
      myResourceComponent.addTextDocumentListener(listener);
    }

    public void addAlphaActionListener(@NotNull AlphaActionListener listener) {
      myAlphaComponent.addSwatchListener(listener);
      myAlphaComponent.addTextDocumentListener(listener);
      myAlphaActionListener = listener;
    }

    @Nullable
    public AlphaActionListener getAlphaActionListener() {
      return myAlphaActionListener;
    }

    @Override
    public void setComponentPopupMenu(JPopupMenu popup) {
      super.setComponentPopupMenu(popup);
      myResourceComponent.setComponentPopupMenu(popup);
      myAlphaComponent.setComponentPopupMenu(popup);
    }

    @Override
    public void setFont(Font font) {
      super.setFont(font);
      if (myResourceComponent != null) {
        myResourceComponent.setFont(font);
      }
      if (myAlphaComponent != null) {
        myAlphaComponent.setFont(font);
      }
    }

    @Nullable/*if there is no error*/
    public ValidationInfo doValidate(int minApi, @NotNull AndroidTargetData androidTargetData) {
      ValidationInfo error = getResourceComponent().doValidate(minApi, androidTargetData);
      if (error == null && getAlphaValue() != null) {
        error = getAlphaComponent().doValidate(minApi, androidTargetData);
      }
      return error;
    }
  }
}
