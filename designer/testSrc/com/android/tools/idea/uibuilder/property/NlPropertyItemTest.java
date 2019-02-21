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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_AUTO_TEXT;
import static com.android.SdkConstants.ATTR_CONTEXT;
import static com.android.SdkConstants.ATTR_ELEVATION;
import static com.android.SdkConstants.ATTR_FONT_FAMILY;
import static com.android.SdkConstants.ATTR_GRAVITY;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.ATTR_PARENT_TAG;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.ATTR_TEXT;
import static com.android.SdkConstants.ATTR_TEXT_APPEARANCE;
import static com.android.SdkConstants.ATTR_TEXT_SIZE;
import static com.android.SdkConstants.ATTR_VISIBILITY;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.VALUE_WRAP_CONTENT;
import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;
import static com.android.tools.idea.uibuilder.property.NlProperties.STARRED_PROP;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceValueImpl;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.ResourceValueMap;
import com.android.resources.ResourceType;
import com.android.tools.property.ptable.PTableGroupItem;
import com.android.tools.property.ptable.StarState;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.SelectionListener;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xml.XmlName;
import com.intellij.xml.XmlAttributeDescriptor;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.annotations.NotNull;

public class NlPropertyItemTest extends PropertyTestCase {

  public void testCreateFlagProperty() {
    NlPropertyItem item = createFrom(myTextView, ATTR_GRAVITY);
    assertThat(item).isInstanceOf(NlFlagPropertyItem.class);
    assertThat(item.getName()).isEqualTo(ATTR_GRAVITY);
  }

  public void testCreateIDProperty() {
    NlPropertyItem item = createFrom(myTextView, ATTR_ID);
    assertThat(item).isInstanceOf(NlIdPropertyItem.class);
    assertThat(item.getName()).isEqualTo(ATTR_ID);
  }

  public void testCreateTextProperty() {
    NlPropertyItem item = createFrom(myTextView, ATTR_TEXT);
    assertThat(item).isNotInstanceOf(NlFlagPropertyItem.class);
    assertThat(item).isNotInstanceOf(NlIdPropertyItem.class);
    assertThat(item.getName()).isEqualTo(ATTR_TEXT);
  }

  public void testCreateDesignProperty() {
    NlPropertyItem item = createFrom(myTextView, ATTR_TEXT);
    NlPropertyItem design = item.getDesignTimeProperty();
    assertThat(design).isNotInstanceOf(NlFlagPropertyItem.class);
    assertThat(design).isNotInstanceOf(NlIdPropertyItem.class);
    assertThat(design.getName()).isEqualTo(ATTR_TEXT);
    assertThat(design.getNamespace()).isEqualTo(TOOLS_URI);

    // The design property of a design property is itself
    assertThat(design.getDesignTimeProperty()).isSameAs(design);
  }

  public void testCreateDesignPropertyInPropertyTable() {
    NlPropertyItem item = createFrom(myTextView, ATTR_TEXT);
    PTableGroupItem group = new SimpleGroupItem();
    group.addChild(item);
    NlPropertyItem design = item.getDesignTimeProperty();
    assertThat(design).isNotInstanceOf(NlFlagPropertyItem.class);
    assertThat(design).isNotInstanceOf(NlIdPropertyItem.class);
    assertThat(design.getName()).isEqualTo(ATTR_TEXT);
    assertThat(design.getNamespace()).isEqualTo(TOOLS_URI);
    assertThat(design.getParent()).isEqualTo(group);
    assertThat(group.getChildren()).containsAllOf(item, design);

    // Deleting it removes it from the group
    design.delete();
    assertThat(group.getChildren()).doesNotContain(design);
  }

  public void testCreateWithoutAttributeDefinition() {
    // It is an error not to specify an AttributeDefinition for normal attributes
    XmlAttributeDescriptor descriptor = getDescriptor(myTextView, ATTR_TEXT);
    assertThat(descriptor).isNotNull();
    try {
      NlPropertyItem.create(new XmlName(ATTR_TEXT, ANDROID_URI), null, ImmutableList.of(myTextView), myPropertiesManager);
      fail("An AttributeDefinition should exist for ATTR_TEXT");
    }
    catch (IllegalArgumentException ex) {
      assertThat(ex.getMessage()).isEqualTo("Missing attribute definition for text");
    }

    // Style does not have an AttributeDefinition
    NlPropertyItem item = createFrom(myTextView, ATTR_STYLE);
    assertThat(item).isNotInstanceOf(NlFlagPropertyItem.class);
    assertThat(item).isNotInstanceOf(NlIdPropertyItem.class);
    assertThat(item.getName()).isEqualTo(ATTR_STYLE);
    assertThat(item.getDefinition()).isNull();
  }

  public void testCreateForToolAttributes() {
    AttributeDefinition definition = new AttributeDefinition(ResourceNamespace.TOOLS, ATTR_CONTEXT);
    NlPropertyItem item =
        NlPropertyItem.create(new XmlName(ATTR_CONTEXT, TOOLS_URI), definition, ImmutableList.of(myMerge), myPropertiesManager);
    assertThat(item).isNotInstanceOf(NlFlagPropertyItem.class);
    assertThat(item).isNotInstanceOf(NlIdPropertyItem.class);
    assertThat(item.getName()).isEqualTo(ATTR_CONTEXT);
    assertThat(item.getNamespace()).isEqualTo(TOOLS_URI);
    assertThat(item.getDefinition()).isEqualTo(definition);
  }

  public void testSameDefinition() {
    NlPropertyItem text = createFrom(myTextView, ATTR_TEXT);
    NlPropertyItem gravity = createFrom(myTextView, ATTR_GRAVITY);
    AttributeDefinition textDefinition = text.getDefinition();
    assertThat(textDefinition).isNotNull();
    AttributeDefinition gravityDefinition = gravity.getDefinition();
    assertThat(gravityDefinition).isNotNull();
    XmlAttributeDescriptor gravityDescriptor = getDescriptor(myTextView, ATTR_GRAVITY);
    assertThat(gravityDescriptor).isNotNull();

    assertThat(text.sameDefinition(text)).isTrue();
    assertThat(text.sameDefinition(createFrom(myTextView, ATTR_TEXT))).isTrue();
    assertThat(text.sameDefinition(createFrom(myTextView, ATTR_TEXT).getDesignTimeProperty())).isFalse();
    assertThat(text.sameDefinition(gravity)).isFalse();
    assertThat(text.sameDefinition(gravity.getDesignTimeProperty())).isFalse();
  }

  public void testGetValue() {
    assertThat(createFrom(myTextView, ATTR_TEXT).getValue()).isEqualTo("SomeText");
    assertThat(createFrom(myCheckBox1, ATTR_TEXT).getValue()).isEqualTo("Enable Wifi");
    assertThat(createFrom(myCheckBox2, ATTR_TEXT).getValue()).isEqualTo("SomeText");
    assertThat(createFrom(myCheckBox3, ATTR_TEXT).getValue()).isNull();
    assertThat(createFrom(ATTR_TEXT, myCheckBox1, myCheckBox2).getValue()).isNull();
    assertThat(createFrom(ATTR_TEXT, myTextView, myCheckBox2).getValue()).isEqualTo("SomeText");
  }

  public void testIsDefaultValue() {
    NlPropertyItem text = createFrom(myTextView, ATTR_TEXT);
    assertThat(text.isDefaultValue(null)).isTrue();
    assertThat(text.isDefaultValue("Text")).isFalse();

    text.setDefaultValue("Text");
    assertThat(text.isDefaultValue(null)).isTrue();
    assertThat(text.isDefaultValue("Text")).isTrue();
  }

  public void testGetResolvedValue() {
    NlPropertyItem text = createFrom(myTextView, ATTR_TEXT);
    assertThat(text.getResolvedValue()).isEqualTo("SomeText");

    NlPropertyItem textAppearance = createFrom(myTextView, ATTR_TEXT_APPEARANCE);
    assertThat(textAppearance.getResolvedValue()).isNull();

    textAppearance.setValue("?android:attr/textAppearanceMedium");
    UIUtil.dispatchAllInvocationEvents();
    assertThat(textAppearance.getResolvedValue()).isEqualTo("@android:style/TextAppearance.Material.Medium");

    textAppearance.setValue("@android:style/TextAppearance.Material.Medium");
    UIUtil.dispatchAllInvocationEvents();
    assertThat(textAppearance.getResolvedValue()).isEqualTo("@android:style/TextAppearance.Material.Medium");

    textAppearance.setValue("?android:attr/textAppearanceMedium");
    textAppearance.setDefaultValue("?android:attr/textAppearanceMedium");
    UIUtil.dispatchAllInvocationEvents();
    assertThat(textAppearance.getResolvedValue()).isEqualTo("@android:style/TextAppearance.Material.Medium");

    textAppearance.setValue(null);
    textAppearance
      .setDefaultValue("?android:attr/textAppearanceMedium");
    UIUtil.dispatchAllInvocationEvents();
    assertThat(textAppearance.getResolvedValue()).isEqualTo("@android:style/TextAppearance.Material.Medium");

    NlPropertyItem size = createFrom(myTextView, ATTR_TEXT_SIZE);
    assertThat(size.getResolvedValue()).isEqualTo(null);

    size.setValue("@dimen/text_size_small_material");
    UIUtil.dispatchAllInvocationEvents();
    assertThat(size.getResolvedValue()).isEqualTo("14sp");

    size.setDefaultValue("@dimen/text_size_small_material");
    UIUtil.dispatchAllInvocationEvents();
    assertThat(size.getResolvedValue()).isEqualTo("14sp");
  }

  public void testGetResolvedFontValue() {
    NlPropertyItem font = createFrom(myTextView, ATTR_FONT_FAMILY);
    font.setValue("@font/Lobster");
    UIUtil.dispatchAllInvocationEvents();

    ResourceValueMap fonts = ResourceValueMap.create();
    fonts.put("Lobster", new ResourceValueImpl(RES_AUTO, ResourceType.FONT, "Lobster", "/very/long/filename/do/not/use"));
    fonts.put("DancingScript",
              new ResourceValueImpl(RES_AUTO, ResourceType.FONT, "DancingScript", "/very/long/filename/do/not/use"));
    ResourceResolver resolver = myModel.getConfiguration().getResourceResolver();
    assertThat(resolver).isNotNull();
    resolver.getProjectResources().put(ResourceType.FONT, fonts);

    assertThat(font.getResolvedValue()).isEqualTo("Lobster");
  }

  public void testGetChildProperty() {
    NlPropertyItem text = createFrom(myTextView, ATTR_TEXT);
    try {
      text.getChildProperty("SomeThing");
      fail("This should have caused an UnsupportedOperationException");
    }
    catch (UnsupportedOperationException ex) {
      assertThat(ex.getMessage()).isEqualTo("SomeThing");
    }
  }

  public void testGetTag() {
    NlPropertyItem text = createFrom(myTextView, ATTR_TEXT);
    assertThat(text.getTag()).isNotNull();
    assertThat(text.getTag().getName()).isEqualTo(TEXT_VIEW);
    assertThat(text.getTagName()).isEqualTo(TEXT_VIEW);

    // Multiple component does not give access to tag and tagName
    NlPropertyItem text2 = NlPropertyItem.create(
      new XmlName(ATTR_CONTEXT, ANDROID_URI), text.getDefinition(), ImmutableList.of(myTextView, myCheckBox1), myPropertiesManager);
    assertThat(text2.getTag()).isNull();
    assertThat(text2.getTagName()).isNull();
  }

  public void testSetValue() {
    NlPropertyItem text = createFrom(myTextView, ATTR_TEXT);
    text.setValue("Hello World");
    UIUtil.dispatchAllInvocationEvents();
    assertThat(myTextView.getAttribute(ANDROID_URI, ATTR_TEXT)).isEqualTo("Hello World");
    verify(myUsageTracker).logPropertyChange(text, NlPropertiesPanel.PropertiesViewMode.INSPECTOR, -1);
  }

  public void testSetValueWithDefaultValue() {
    NlPropertyItem auto = createFrom(myTextView, ATTR_AUTO_TEXT);
    auto.setDefaultValue("true");
    auto.setValue("true");
    UIUtil.dispatchAllInvocationEvents();
    assertThat(myTextView.getAttribute(ANDROID_URI, ATTR_AUTO_TEXT)).isEqualTo("true");
  }

  public void testSetValueParentTagOnMergeTagAddsOrientationProperty() {
    int originalUpdateCount = myPropertiesManager.getUpdateCount();

    // Make sure the orientation property does not exist initially
    assertThat(getDescriptor(myMerge, ATTR_ORIENTATION)).isNull();

    // Now set the parentTag property to a linear layout
    NlPropertyItem parentTag = createFrom(myMerge, ATTR_PARENT_TAG);
    SelectionListener selectionListener = mock(SelectionListener.class);
    myDesignSurface.getSelectionModel().addListener(selectionListener);
    parentTag.setValue(LINEAR_LAYOUT);
    UIUtil.dispatchAllInvocationEvents();

    assertThat(myMerge.getAttribute(TOOLS_URI, ATTR_PARENT_TAG)).isEqualTo(LINEAR_LAYOUT);
    assertThat(myPropertiesManager.getUpdateCount()).isEqualTo(originalUpdateCount + 1);
    assertThat(getDescriptor(myMerge, ATTR_ORIENTATION)).isNotNull();
    verify(myUsageTracker).logPropertyChange(parentTag, NlPropertiesPanel.PropertiesViewMode.INSPECTOR, -1);
  }

  public void testSetValueOnDisposedProject() {
    NlPropertyItem text = createFrom(myTextView, ATTR_TEXT);

    // Make a fake project instance that reports true to isDisposed()
    Project fakeProject = mock(Project.class);
    when(fakeProject.isDisposed()).thenReturn(true);
    NlModel fakeModel = mock(NlModel.class);
    when(fakeModel.getProject()).thenReturn(fakeProject);
    NlComponent fakeComponent = mock(NlComponent.class);
    when(fakeComponent.getModel()).thenReturn(fakeModel);
    when(fakeComponent.getTagDeprecated()).thenThrow(new RuntimeException("setValue should bail out"));
    NlPropertyItem fake = NlPropertyItem.create(
      new XmlName(ATTR_TEXT, ANDROID_URI), text.getDefinition(), ImmutableList.of(fakeComponent), myPropertiesManager);
    fake.setValue("stuff");
  }

  public void testMisc() {
    NlPropertyItem text = createFrom(myTextView, ATTR_TEXT);

    assertThat(text.toString()).isEqualTo("@android:text");
    assertThat(text.getTooltipText()).isEqualTo("@android:text: Text to display.");
    assertThat(text.isEditable(1)).isTrue();
  }

  public void testSetInitialStarred() {
    NlPropertyItem elevation = createFrom(myTextView, ATTR_ELEVATION);
    assertThat(elevation.getStarState()).isEqualTo(StarState.STAR_ABLE);

    elevation.setInitialStarred();
    assertThat(elevation.getStarState()).isEqualTo(StarState.STARRED);
  }

  public void testSetStarred() {
    int originalUpdateCount = myPropertiesManager.getUpdateCount();
    NlPropertyItem text = createFrom(myTextView, ATTR_ELEVATION);
    assertThat(text.getStarState()).isEqualTo(StarState.STAR_ABLE);

    text.setStarState(StarState.STARRED);
    UIUtil.dispatchAllInvocationEvents();
    assertThat(text.getStarState()).isEqualTo(StarState.STARRED);
    assertThat(myPropertiesComponent.getValue(STARRED_PROP)).isEqualTo(ATTR_VISIBILITY + ";" + ATTR_ELEVATION);
    assertThat(myPropertiesManager.getUpdateCount()).isEqualTo(originalUpdateCount + 1);
  }

  public void testNamespaceToPrefix() {
    assertThat(NlPropertyItem.namespaceToPrefix(null)).isEqualTo("");
    assertThat(NlPropertyItem.namespaceToPrefix("http://schemas.android.com/apk/res/android")).isEqualTo("@android:");
    assertThat(NlPropertyItem.namespaceToPrefix("http://schemas.android.com/apk/res-auto")).isEqualTo("@app:");
  }

  public void testSetWidthAndHeightNoUnit() {
    NlPropertyItem width = createFrom(myTextView, ATTR_LAYOUT_WIDTH);
    assertThat(width.getResolvedValue()).isEqualTo(VALUE_WRAP_CONTENT);

    width.setValue("100");
    UIUtil.dispatchAllInvocationEvents();
    assertThat(width.getResolvedValue()).isEqualTo("100dp");

    NlPropertyItem height = createFrom(myTextView, ATTR_LAYOUT_HEIGHT);
    assertThat(height.getResolvedValue()).isEqualTo(VALUE_WRAP_CONTENT);

    height.setValue("90");
    UIUtil.dispatchAllInvocationEvents();
    assertThat(height.getResolvedValue()).isEqualTo("90dp");
  }

  public void testSetWidthAndHeightWithUnit() {
    NlPropertyItem width = createFrom(myTextView, ATTR_LAYOUT_WIDTH);
    assertThat(width.getResolvedValue()).isEqualTo(VALUE_WRAP_CONTENT);

    width.setValue("100dp");
    UIUtil.dispatchAllInvocationEvents();
    assertThat(width.getResolvedValue()).isEqualTo("100dp");

    NlPropertyItem height = createFrom(myTextView, ATTR_LAYOUT_HEIGHT);
    assertThat(height.getResolvedValue()).isEqualTo(VALUE_WRAP_CONTENT);

    height.setValue("90dp");
    UIUtil.dispatchAllInvocationEvents();
    assertThat(height.getResolvedValue()).isEqualTo("90dp");
  }

  public void testSetWidthAndHeightMatchParent() {
    NlPropertyItem width = createFrom(myTextView, ATTR_LAYOUT_WIDTH);
    assertThat(width.getResolvedValue()).isEqualTo(VALUE_WRAP_CONTENT);

    width.setValue("match_parent");
    UIUtil.dispatchAllInvocationEvents();
    assertThat(width.getResolvedValue()).isEqualTo("match_parent");

    NlPropertyItem height = createFrom(myTextView, ATTR_LAYOUT_HEIGHT);
    assertThat(height.getResolvedValue()).isEqualTo(VALUE_WRAP_CONTENT);

    height.setValue("match_parent");
    UIUtil.dispatchAllInvocationEvents();
    assertThat(height.getResolvedValue()).isEqualTo("match_parent");
  }

  public void testSetWidthAndHeightGibberish() {
    NlPropertyItem width = createFrom(myTextView, ATTR_LAYOUT_WIDTH);
    assertThat(width.getResolvedValue()).isEqualTo(VALUE_WRAP_CONTENT);

    width.setValue("gibberish");
    UIUtil.dispatchAllInvocationEvents();
    assertThat(width.getResolvedValue()).isEqualTo("gibberish");

    NlPropertyItem height = createFrom(myTextView, ATTR_LAYOUT_HEIGHT);
    assertThat(height.getResolvedValue()).isEqualTo(VALUE_WRAP_CONTENT);

    height.setValue("gibberish");
    UIUtil.dispatchAllInvocationEvents();
    assertThat(height.getResolvedValue()).isEqualTo("gibberish");
  }

  public void testSetTextSizeNoUnit() {
    NlPropertyItem textSize = createFrom(myTextView, ATTR_TEXT_SIZE);
    assertThat(textSize.getResolvedValue()).isEqualTo(null);

    textSize.setValue("10");
    UIUtil.dispatchAllInvocationEvents();
    assertThat(textSize.getResolvedValue()).isEqualTo("10sp");
  }

  private static class SimpleGroupItem extends PTableGroupItem {
    @NotNull
    @Override
    public String getName() {
      return "Group";
    }
  }
}
