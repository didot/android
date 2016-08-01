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
import com.android.tools.idea.uibuilder.model.PreferenceUtils;
import com.android.tools.idea.uibuilder.property.NlFlagPropertyItem;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.*;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.ui.LafManager;
import com.intellij.openapi.project.Project;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.property.editors.NlEditingListener.DEFAULT_LISTENER;

public class TextInspectorProvider implements InspectorProvider {
  private static final List<String> TEXT_PROPERTIES = ImmutableList.of(
    ATTR_TEXT,
    ATTR_CONTENT_DESCRIPTION,
    ATTR_TEXT_APPEARANCE,
    ATTR_FONT_FAMILY,
    ATTR_TYPEFACE,
    ATTR_TEXT_SIZE,
    ATTR_LINE_SPACING_EXTRA,
    ATTR_TEXT_STYLE,
    ATTR_TEXT_ALL_CAPS,
    ATTR_TEXT_ALIGNMENT,
    ATTR_TEXT_COLOR);

  private TextInspectorComponent myComponent;

  public TextInspectorProvider() {
    LafManager.getInstance().addLafManagerListener(source -> myComponent = null);
  }

  @Override
  public boolean isApplicable(@NotNull List<NlComponent> components,
                              @NotNull Map<String, NlProperty> properties,
                              @NotNull NlPropertiesManager propertiesManager) {
    if (!properties.keySet().containsAll(TEXT_PROPERTIES)) {
      return false;
    }
    for (NlComponent component : components) {
      // Do not show Text properties for preferences even though the component may have all the properties
      if (PreferenceUtils.VALUES.contains(component.getTagName())) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  @Override
  public InspectorComponent createCustomInspector(@NotNull List<NlComponent> components,
                                                  @NotNull Map<String, NlProperty> properties,
                                                  @NotNull NlPropertiesManager propertiesManager) {
    if (myComponent == null) {
      myComponent = new TextInspectorComponent(propertiesManager);
    }
    myComponent.updateProperties(components, properties, propertiesManager);
    return myComponent;
  }

  /**
   * Text font inspector component for setting font family, size, decorations, color.
   */
  private static class TextInspectorComponent implements InspectorComponent {
    private final NlReferenceEditor myTextEditor;
    private final NlReferenceEditor myDesignTextEditor;
    private final NlReferenceEditor myDescriptionEditor;
    private final NlEnumEditor myStyleEditor;
    private final NlEnumEditor myFontFamilyEditor;
    private final NlEnumEditor myTypefaceEditor;
    private final NlEnumEditor myFontSizeEditor;
    private final NlEnumEditor mySpacingEditor;
    private final NlBooleanIconEditor myBoldEditor;
    private final NlBooleanIconEditor myItalicsEditor;
    private final NlBooleanIconEditor myAllCapsEditor;
    private final NlBooleanIconEditor myStartEditor;
    private final NlBooleanIconEditor myLeftEditor;
    private final NlBooleanIconEditor myCenterEditor;
    private final NlBooleanIconEditor myRightEditor;
    private final NlBooleanIconEditor myEndEditor;
    private final NlReferenceEditor myColorEditor;
    private final JPanel myTextStylePanel;
    private final JPanel myAlignmentPanel;

    private NlProperty myText;
    private NlProperty myDesignText;
    private NlProperty myDescription;
    private NlProperty myStyle;
    private NlProperty myFontFamily;
    private NlProperty myTypeface;
    private NlProperty myFontSize;
    private NlProperty mySpacing;
    private NlFlagPropertyItem myTextStyle;
    private NlProperty myTextAllCaps;
    private NlProperty myAlignment;
    private NlProperty myColor;

    public TextInspectorComponent(@NotNull NlPropertiesManager propertiesManager) {
      Project project = propertiesManager.getProject();
      myTextEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
      myDesignTextEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);
      myDescriptionEditor = NlReferenceEditor.createForInspectorWithBrowseButton(project, DEFAULT_LISTENER);

      myStyleEditor = NlEnumEditor.createForInspector(createEnumStyleListener());
      myFontFamilyEditor = NlEnumEditor.createForInspector(DEFAULT_LISTENER);
      myTypefaceEditor = NlEnumEditor.createForInspector(DEFAULT_LISTENER);
      myFontSizeEditor = NlEnumEditor.createForInspector(DEFAULT_LISTENER);
      mySpacingEditor = NlEnumEditor.createForInspector(DEFAULT_LISTENER);
      myBoldEditor = new NlBooleanIconEditor(AndroidIcons.NeleIcons.TextStyleBold, "Bold");
      myItalicsEditor = new NlBooleanIconEditor(AndroidIcons.NeleIcons.TextStyleItalics, "Italics");
      myAllCapsEditor = new NlBooleanIconEditor(AndroidIcons.NeleIcons.TextAllCaps, "All Caps");
      myStartEditor = new NlBooleanIconEditor(AndroidIcons.NeleIcons.TextAlignViewStart, "Align Start of View", TextAlignment.VIEW_START);
      myLeftEditor = new NlBooleanIconEditor(AndroidIcons.NeleIcons.TextAlignTextStart, "Align Start of Text", TextAlignment.TEXT_START);
      myCenterEditor = new NlBooleanIconEditor(AndroidIcons.NeleIcons.TextAlignCentered, "Align Center", TextAlignment.CENTER);
      myRightEditor = new NlBooleanIconEditor(AndroidIcons.NeleIcons.TextAlignTextEnd, "Align End of Text", TextAlignment.TEXT_END);
      myEndEditor = new NlBooleanIconEditor(AndroidIcons.NeleIcons.TextAlignViewEnd, "Align End of View", TextAlignment.VIEW_END);
      myColorEditor = NlReferenceEditor.createForInspectorWithBrowseButton(propertiesManager.getProject(), DEFAULT_LISTENER);

      myTextStylePanel = new JPanel(new FlowLayout(FlowLayout.LEADING));
      myTextStylePanel.add(myBoldEditor.getComponent());
      myTextStylePanel.add(myItalicsEditor.getComponent());
      myTextStylePanel.add(myAllCapsEditor.getComponent());

      myAlignmentPanel = new JPanel(new FlowLayout(FlowLayout.LEADING));
      myAlignmentPanel.add(myStartEditor.getComponent());
      myAlignmentPanel.add(myLeftEditor.getComponent());
      myAlignmentPanel.add(myCenterEditor.getComponent());
      myAlignmentPanel.add(myRightEditor.getComponent());
      myAlignmentPanel.add(myEndEditor.getComponent());
    }

    @Override
    public void updateProperties(@NotNull List<NlComponent> components,
                                 @NotNull Map<String, NlProperty> properties,
                                 @NotNull NlPropertiesManager propertiesManager) {
      myText = properties.get(ATTR_TEXT);
      myDesignText = myText.getDesignTimeProperty();
      myDescription = properties.get(ATTR_CONTENT_DESCRIPTION);
      myStyle = properties.get(ATTR_TEXT_APPEARANCE);
      myFontFamily = properties.get(ATTR_FONT_FAMILY);
      myTypeface = properties.get(ATTR_TYPEFACE);
      myFontSize = properties.get(ATTR_TEXT_SIZE);
      mySpacing = properties.get(ATTR_LINE_SPACING_EXTRA);
      myTextStyle = (NlFlagPropertyItem)properties.get(ATTR_TEXT_STYLE);
      myTextAllCaps = properties.get(ATTR_TEXT_ALL_CAPS);
      myAlignment = properties.get(ATTR_TEXT_ALIGNMENT);
      myColor = properties.get(ATTR_TEXT_COLOR);
    }

    @Override
    public int getMaxNumberOfRows() {
      return 12;
    }

    @Override
    public void attachToInspector(@NotNull InspectorPanel inspector) {
      refresh();
      inspector.addTitle("TextView");
      inspector.addComponent(ATTR_TEXT, myText.getTooltipText(), myTextEditor.getComponent());
      JLabel designText = inspector.addComponent(ATTR_TEXT, myDesignText.getTooltipText(), myDesignTextEditor.getComponent());
      designText.setIcon(AndroidIcons.NeleIcons.DesignProperty);
      inspector.addComponent(ATTR_CONTENT_DESCRIPTION, myDescription.getTooltipText(), myDescriptionEditor.getComponent());

      inspector.addExpandableComponent(ATTR_TEXT_APPEARANCE, myStyle.getTooltipText(), myStyleEditor.getComponent(), myStyleEditor.getKeySource());
      inspector.addComponent(ATTR_FONT_FAMILY, myFontFamily.getTooltipText(), myFontFamilyEditor.getComponent());
      inspector.addComponent(ATTR_TYPEFACE, myTypeface.getTooltipText(), myTypefaceEditor.getComponent());
      inspector.addComponent(ATTR_TEXT_SIZE, myFontSize.getTooltipText(), myFontSizeEditor.getComponent());
      inspector.addComponent(ATTR_LINE_SPACING_EXTRA, mySpacing.getTooltipText(), mySpacingEditor.getComponent());
      inspector.addComponent(ATTR_TEXT_COLOR, myColor.getTooltipText(), myColorEditor.getComponent());
      inspector.addComponent(ATTR_TEXT_STYLE, myTextStyle.getTooltipText(), myTextStylePanel);
      inspector.addComponent(ATTR_TEXT_ALIGNMENT, myAlignment.getTooltipText(), myAlignmentPanel);
    }

    @Override
    public void refresh() {
      myTextEditor.setProperty(myText);
      myDesignTextEditor.setProperty(myDesignText);
      myDescriptionEditor.setProperty(myDescription);
      myStyleEditor.setProperty(myStyle);
      myFontFamilyEditor.setProperty(myFontFamily);
      myTypefaceEditor.setProperty(myTypeface);
      myFontSizeEditor.setProperty(myFontSize);
      mySpacingEditor.setProperty(mySpacing);
      myBoldEditor.setProperty(myTextStyle.getChildProperty(TextStyle.VALUE_BOLD));
      myItalicsEditor.setProperty(myTextStyle.getChildProperty(TextStyle.VALUE_ITALIC));
      myAllCapsEditor.setProperty(myTextAllCaps);
      myStartEditor.setProperty(myAlignment);
      myLeftEditor.setProperty(myAlignment);
      myCenterEditor.setProperty(myAlignment);
      myRightEditor.setProperty(myAlignment);
      myEndEditor.setProperty(myAlignment);
      myColorEditor.setProperty(myColor);
    }

    @Nullable
    @Override
    public NlComponentEditor getEditorForProperty(@NotNull String propertyName) {
      switch (propertyName) {
        case ATTR_TEXT:
          return myTextEditor;
        default:
          return null;
      }
    }

    private NlEditingListener createEnumStyleListener() {
      return new NlEditingListener() {
        @Override
        public void stopEditing(@NotNull NlComponentEditor editor, @Nullable Object value) {
          // TODO: Create a write transaction here to include all these changes in one undo event
          myStyle.setValue(value);
          myFontFamily.setValue(null);
          myFontSize.setValue(null);
          mySpacing.setValue(null);
          myTextStyle.setValue(null);
          myTextAllCaps.setValue(null);
          myAlignment.setValue(null);
          myColor.setValue(null);
          refresh();
        }

        @Override
        public void cancelEditing(@NotNull NlComponentEditor editor) {

        }
      };
    }
  }
}
