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

import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkVersionInfo;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.sun.java.swing.plaf.windows.WindowsComboBoxUI;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.android.dom.converters.OnClickConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.ComboBoxUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

import static com.android.SdkConstants.*;

public class NlEnumEditor extends NlBaseComponentEditor implements NlComponentEditor {
  private static final int SMALL_WIDTH = 65;
  private static final List<String> AVAILABLE_TEXT_SIZES = ImmutableList.of("8sp", "10sp", "12sp", "14sp", "18sp", "24sp", "30sp", "36sp");
  private static final List<String> AVAILABLE_LINE_SPACINGS = AVAILABLE_TEXT_SIZES;
  private static final List<String> AVAILABLE_TYPEFACES = ImmutableList.of("normal", "sans", "serif", "monospace");
  private static final List<String> AVAILABLE_SIZES = ImmutableList.of("match_parent", "wrap_content");

  private final JPanel myPanel;
  private final JComboBox<ValueWithDisplayString> myCombo;

  private NlProperty myProperty;
  private String myApiVersion;
  private boolean myUpdatingProperty;
  private int myAddedValueIndex;

  public static NlTableCellEditor createForTable() {
    NlTableCellEditor cellEditor = new NlTableCellEditor();
    cellEditor.init(new NlEnumEditor(cellEditor, cellEditor, false, true));
    return cellEditor;
  }

  public static NlEnumEditor createForInspector(@NotNull NlEditingListener listener) {
    return new NlEnumEditor(listener, null, true, false);
  }

  public static NlEnumEditor createForInspectorWithBrowseButton(@NotNull NlEditingListener listener) {
    return new NlEnumEditor(listener, null, true, true);
  }

  /**
   * Return <code>true</code> if the property can be edited with an {@link NlEnumEditor}.
   */
  public static boolean supportsProperty(@NotNull NlProperty property) {
    // The attributes supported should list the properties that do not specify Enum in the formats.
    // This is generally the same list we have special code for in {@link #setModel}.
    // When updating list please make the corresponding change in {@link #setModel}.
    switch (property.getName()) {
      case ATTR_FONT_FAMILY:
      case ATTR_TYPEFACE:
      case ATTR_TEXT_SIZE:
      case ATTR_LINE_SPACING_EXTRA:
      case ATTR_TEXT_APPEARANCE:
      case ATTR_LAYOUT_HEIGHT:
      case ATTR_LAYOUT_WIDTH:
      case ATTR_DROPDOWN_HEIGHT:
      case ATTR_DROPDOWN_WIDTH:
      case ATTR_ON_CLICK:
        return true;
      case ATTR_STYLE:
        String tagName = property.getTagName();
        return tagName != null && StyleFilter.hasWidgetStyles(property.getModel().getProject(), property.getResolver(), tagName);
      default:
        if (property.getName().endsWith(ValueWithDisplayString.TEXT_APPEARANCE_SUFFIX)) {
          return true;
        }
        if (AndroidDomUtil.SPECIAL_RESOURCE_TYPES.get(property.getName()) == ResourceType.ID) {
          return true;
        }
        AttributeDefinition definition = property.getDefinition();
        Set<AttributeFormat> formats = definition != null ? definition.getFormats() : Collections.emptySet();
        return formats.contains(AttributeFormat.Enum);
    }
  }

  private NlEnumEditor(@NotNull NlEditingListener listener,
                       @Nullable BrowsePanel.Context context,
                       boolean includeBorder,
                       boolean includeBrowseButton) {
    super(listener);
    myAddedValueIndex = -1; // nothing added
    myPanel = new JPanel(new BorderLayout(HORIZONTAL_COMPONENT_GAP, 0));

    //noinspection unchecked
    myCombo = new CustomComboBox(includeBorder ? myPanel : null);
    myCombo.setEditable(true);
    myPanel.add(myCombo, BorderLayout.CENTER);

    if (includeBrowseButton || context != null) {
      myPanel.add(createBrowsePanel(context), BorderLayout.LINE_END);
    }

    myCombo.addActionListener(this::comboValuePicked);
    JTextField editor = (JTextField)myCombo.getEditor().getEditorComponent();
    editor.registerKeyboardAction(event -> enter(),
                                  KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                                  JComponent.WHEN_FOCUSED);
    editor.registerKeyboardAction(event -> cancel(),
                                  KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                                  JComponent.WHEN_FOCUSED);
    editor.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent event) {
        editor.selectAll();
      }

      @Override
      public void focusLost(FocusEvent event) {
        stopEditing(getText());
        // Remove the selection after we lose focus for feedback on which editor is the active editor
        editor.select(0, 0);
      }
    });
    //noinspection unchecked
    myCombo.setRenderer(new EnumRenderer());
  }

  @Override
  public void setEnabled(boolean en) {
    myCombo.setEnabled(en);
    createBrowsePanel(null);
  }

  @Override
  public void setProperty(@NotNull NlProperty property) {
    if (property != myProperty || !getApiVersion(property).equals(myApiVersion)) {
      setModel(property);
    }
    try {
      myUpdatingProperty = true;
      selectItem(ValueWithDisplayString.create(property.getValue(), property));
    }
    finally {
      myUpdatingProperty = false;
    }
  }

  @Override
  public void requestFocus() {
    myCombo.requestFocus();
  }

  private void setModel(@NotNull NlProperty property) {
    assert supportsProperty(property) : this.getClass().getName() + property;
    myProperty = property;
    myApiVersion = getApiVersion(property);

    AttributeDefinition definition = property.getDefinition();
    ValueWithDisplayString[] values;
    // The attributes supported should list the properties that do not specify Enum in the formats.
    // This is generally the same list we have special code for in {@link #propertySupported}.
    // When updating list please make the corresponding change in {@link #propertySupported}.
    switch (property.getName()) {
      case ATTR_FONT_FAMILY:
        values = ValueWithDisplayString.create(AndroidDomUtil.AVAILABLE_FAMILIES);
        break;
      case ATTR_TYPEFACE:
        values = ValueWithDisplayString.create(AVAILABLE_TYPEFACES);
        break;
      case ATTR_TEXT_SIZE:
        values = ValueWithDisplayString.create(AVAILABLE_TEXT_SIZES);
        break;
      case ATTR_LINE_SPACING_EXTRA:
        values = ValueWithDisplayString.create(AVAILABLE_LINE_SPACINGS);
        break;
      case ATTR_TEXT_APPEARANCE:
        values = createTextAttributeArray(property);
        break;
      case ATTR_LAYOUT_HEIGHT:
      case ATTR_LAYOUT_WIDTH:
      case ATTR_DROPDOWN_HEIGHT:
      case ATTR_DROPDOWN_WIDTH:
        values = ValueWithDisplayString.create(AVAILABLE_SIZES);
        break;
      case ATTR_ON_CLICK:
        values = createOnClickValues(property);
        break;
      case ATTR_STYLE:
        values = createStyleArrayFromTag(property);
        break;
      default:
        if (property.getName().endsWith(ValueWithDisplayString.TEXT_APPEARANCE_SUFFIX)) {
          values = createTextAttributeArray(property);
        }
        else if (AndroidDomUtil.SPECIAL_RESOURCE_TYPES.get(property.getName()) == ResourceType.ID) {
          values = createChoicesForId(property);
        }
        else {
          values = definition == null ? ValueWithDisplayString.EMPTY_ARRAY : ValueWithDisplayString.create(definition.getValues());
        }
    }

    DefaultComboBoxModel<ValueWithDisplayString> newModel = new DefaultComboBoxModel<ValueWithDisplayString>(values) {
      @Override
      public void setSelectedItem(Object object) {
        if (object instanceof String) {
          String newValue = (String)object;
          object = new ValueWithDisplayString(newValue, newValue);
        }
        super.setSelectedItem(object);
      }
    };
    newModel.insertElementAt(ValueWithDisplayString.UNSET, 0);
    myCombo.setModel(newModel);
    myAddedValueIndex = -1; // nothing added
  }

  @Override
  @Nullable
  public NlProperty getProperty() {
    return myProperty;
  }

  @NotNull
  private static String getApiVersion(@NotNull NlProperty property) {
    IAndroidTarget target = property.getModel().getConfiguration().getTarget();
    return target == null ? SdkVersionInfo.HIGHEST_KNOWN_STABLE_API + "U" : target.getVersion().getApiString();
  }

  private void selectItem(@NotNull ValueWithDisplayString value) {
    DefaultComboBoxModel<ValueWithDisplayString> model = (DefaultComboBoxModel<ValueWithDisplayString>)myCombo.getModel();
    int index = model.getIndexOf(value);
    if (index == -1) {
      if (myAddedValueIndex >= 0) {
        model.removeElementAt(myAddedValueIndex);
      }
      myAddedValueIndex = findBestInsertionPoint(value);
      model.insertElementAt(value, myAddedValueIndex);
    }
    if (!value.equals(model.getSelectedItem())) {
      model.setSelectedItem(value);
    }
    if (!myProperty.isDefaultValue(value.getValue())) {
      myCombo.getEditor().getEditorComponent().setForeground(CHANGED_VALUE_TEXT_COLOR);
    }
    else {
      myCombo.getEditor().getEditorComponent().setForeground(DEFAULT_VALUE_TEXT_COLOR);
    }
  }

  private int findBestInsertionPoint(@NotNull ValueWithDisplayString newValue) {
    AttributeDefinition definition = myProperty.getDefinition();
    boolean isDimension = definition != null && definition.getFormats().contains(AttributeFormat.Dimension);
    int startIndex = 1;
    if (!isDimension) {
      return startIndex;
    }
    String newTextValue = newValue.toString();
    Quantity newQuantity = Quantity.parse(newTextValue);
    if (newQuantity == null) {
      return startIndex;
    }

    ComboBoxModel<ValueWithDisplayString> model = myCombo.getModel();
    for (int index = startIndex, size = model.getSize(); index < size; index++) {
      String textValue = model.getElementAt(index).getValue();
      if (textValue != null) {
        Quantity quantity = Quantity.parse(textValue);
        if (newQuantity.compareTo(quantity) <= 0) {
          return index;
        }
      }
    }
    return model.getSize();
  }

  @Override
  @Nullable
  public Object getValue() {
    ValueWithDisplayString value = (ValueWithDisplayString)myCombo.getSelectedItem();
    if (value == null) {
      return null;
    }
    return value.getValue();
  }

  @Override
  @NotNull
  public JComponent getComponent() {
    return myPanel;
  }

  private void enter() {
    if (!myCombo.isPopupVisible()) {
      String newValue = getText();
      selectItem(ValueWithDisplayString.create(newValue, myProperty));
      stopEditing(newValue);
      if (hasFocus()) {
        myCombo.getEditor().selectAll();
      }
    }
    myCombo.hidePopup();
  }

  private void cancel() {
    String text = myProperty.getValue();
    if (text == null) {
      text = ValueWithDisplayString.UNSET.toString();
    }
    myCombo.getEditor().setItem(text);
    selectItem(ValueWithDisplayString.create(myProperty.getValue(), myProperty));
    stopEditing(myProperty.getValue());
    if (hasFocus()) {
      myCombo.getEditor().selectAll();
    }
    myCombo.hidePopup();
  }

  private boolean hasFocus() {
    if (myCombo.hasFocus()) {
      return true;
    }
    return myCombo.getEditor().getEditorComponent().hasFocus();
  }

  @Nullable
  private String getText() {
    String text = myCombo.getEditor().getItem().toString();
    if (StringUtil.isEmpty(text) || text.equals(ValueWithDisplayString.UNSET.toString())) {
      return null;
    }
    return Quantity.addUnit(myProperty, text);
  }

  private void comboValuePicked(ActionEvent event) {
    if (myUpdatingProperty || myProperty == null) {
      return;
    }
    ValueWithDisplayString value = (ValueWithDisplayString)myCombo.getModel().getSelectedItem();
    String actionCommand = event.getActionCommand();

    // only notify listener if a value has been picked from the combo box, not for every event from the combo
    // Note: these action names seem to be platform dependent?
    if (value != null && ("comboBoxEdited".equals(actionCommand) || "comboBoxChanged".equals(actionCommand))) {
      stopEditing(value.getValue());
    }
  }

  private static ValueWithDisplayString[] createTextAttributeArray(@NotNull NlProperty property) {
    StyleFilter checker = new StyleFilter(property.getModel().getProject(), property.getResolver());
    StyleAccumulator accumulator = new StyleAccumulator();
    checker.getStylesDerivedFrom("TextAppearance", true).forEach(accumulator::append);
    return accumulator.getValues().toArray(new ValueWithDisplayString[0]);
  }

  private static ValueWithDisplayString[] createStyleArrayFromTag(@NotNull NlProperty property) {
    String tagName = property.getTagName();
    assert tagName != null;
    StyleFilter checker = new StyleFilter(property.getModel().getProject(), property.getResolver());
    StyleAccumulator accumulator = new StyleAccumulator();
    checker.getWidgetStyles(tagName).forEach(accumulator::append);
    return accumulator.getValues().toArray(new ValueWithDisplayString[0]);
  }

  private static ValueWithDisplayString[] createOnClickValues(@NotNull NlProperty property) {
    Module module = property.getModel().getModule();
    Configuration configuration = property.getModel().getConfiguration();
    String activityClassName = configuration.getActivity();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(module.getProject());
    Collection<PsiClass> classes;
    if (activityClassName != null) {
      if (activityClassName.startsWith(".")) {
        MergedManifest manifest = MergedManifest.get(module);
        String pkg = StringUtil.notNullize(manifest.getPackage());
        activityClassName = pkg + activityClassName;
      }
      PsiClass aClass = facade.findClass(activityClassName, module.getModuleScope());
      if (aClass != null) {
        classes = Collections.singleton(aClass);
      } else {
        classes = Collections.emptyList();
      }
    }
    else {
      GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false);
      PsiClass activity = facade.findClass(CLASS_ACTIVITY, scope);
      if (activity != null) {
        classes = ClassInheritorsSearch.search(activity, scope, true).findAll();
      }
      else {
        classes = Collections.emptyList();
      }
    }
    List<ValueWithDisplayString> values = new ArrayList<>();
    Set<String> found = new HashSet<>();
    for (PsiClass psiClass : classes) {
      for (PsiMethod method : psiClass.getAllMethods()) {
        if (OnClickConverter.CONVERTER_FOR_LAYOUT.checkSignature(method) &&
            found.add(method.getName()) &&
            method instanceof PsiMethodImpl) {
          values.add(new ValueWithDisplayString(method.getName() + " (" + psiClass.getName() + ")", method.getName()));
        }
      }
    }
    return values.toArray(new ValueWithDisplayString[0]);
  }

  private static ValueWithDisplayString[] createChoicesForId(@NotNull NlProperty property) {
    return IdAnalyzer.findIdsForProperty(property).stream()
      .map(id -> new ValueWithDisplayString(id, ID_PREFIX + id))
      .toArray(ValueWithDisplayString[]::new);
  }

  private static class CustomComboBox extends ComboBox {
    private final JPanel myBorderPanel;
    private boolean myUseDarculaUI;

    public CustomComboBox(@Nullable JPanel borderPanel) {
      super(SMALL_WIDTH);
      myBorderPanel = borderPanel;
      setBorders();
    }

    private void setBorders() {
      int horizontalSpacing = myUseDarculaUI ? 0 : 1;
      if (myBorderPanel != null) {
        myBorderPanel.setBorder(BorderFactory.createEmptyBorder(VERTICAL_SPACING, horizontalSpacing, VERTICAL_SPACING, 0));
      }
      setBorder(myUseDarculaUI && myBorderPanel != null ? null : BorderFactory.createEmptyBorder(1, 4, 1, 4));
    }

    @Override
    public void setUI(ComboBoxUI ui) {
      myUseDarculaUI = !(ui instanceof WindowsComboBoxUI);
      if (myUseDarculaUI) {
        // There are multiple reasons for hardcoding the ComboBoxUI here:
        // 1) Some LAF will draw a beveled border which does not look good in the table grid.
        // 2) In the inspector we would like the reference editor and the combo boxes to have a similar width.
        //    This is very hard unless you can control the UI.
        // Note: forcing the Darcula UI does not imply dark colors.
        ui = new CustomDarculaComboBoxUI(this);
      }
      super.setUI(ui);
      setBorders();
    }
  }

  private static class CustomDarculaComboBoxUI extends DarculaComboBoxUI {
    
    public CustomDarculaComboBoxUI(@NotNull JComboBox comboBox) {
      super(comboBox);
    }

    @Override
    protected Insets getInsets() {
      // Minimize the vertical padding used in the UI
      return JBUI.insets(VERTICAL_PADDING, HORIZONTAL_PADDING, VERTICAL_PADDING, 4).asUIResource();
    }

    @Override
    @NotNull
    protected Color getArrowButtonFillColor(@NotNull Color defaultColor) {
      // Use a lighter gray for the IntelliJ LAF. Darcula remains what is was.
      return JBColor.LIGHT_GRAY;
    }
  }

  private static class StyleAccumulator {
    private final List<ValueWithDisplayString> myValues = new ArrayList<>();
    private StyleResourceValue myPreviousStyle;

    public void append(@NotNull StyleResourceValue style) {
      if (myPreviousStyle != null && (myPreviousStyle.isFramework() != style.isFramework() ||
                                      myPreviousStyle.isUserDefined() != style.isUserDefined())) {
        myValues.add(ValueWithDisplayString.SEPARATOR);
      }
      myPreviousStyle = style;
      myValues.add(ValueWithDisplayString.createStyleValue(style.getName(), getStylePrefix(style), null));
    }

    @NotNull
    public List<ValueWithDisplayString> getValues() {
      return myValues;
    }

    @NotNull
    private static String getStylePrefix(@NotNull StyleResourceValue style) {
      if (style.isFramework()) {
        return ANDROID_STYLE_RESOURCE_PREFIX;
      }
      return STYLE_RESOURCE_PREFIX;
    }
  }

  private class EnumRenderer extends ColoredListCellRenderer<ValueWithDisplayString> {

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      if (value == ValueWithDisplayString.SEPARATOR) {
        return new JSeparator();
      }
      return super.getListCellRendererComponent(list, value, index, selected, hasFocus);
    }

    @Override
    protected void customizeCellRenderer(JList list, ValueWithDisplayString value, int index, boolean selected, boolean hasFocus) {
      if (value != null) {
        boolean isDefaultValue = myProperty.isDefaultValue(value.getValue());
        if (!selected && !isDefaultValue && Objects.equals(value.getValue(), getValue())) {
          myForeground = CHANGED_VALUE_TEXT_COLOR;
        }
        else if (index == 0 || isDefaultValue) {
          myForeground = DEFAULT_VALUE_TEXT_COLOR;
        }
        append(value.toString());
      }
    }
  }
}
