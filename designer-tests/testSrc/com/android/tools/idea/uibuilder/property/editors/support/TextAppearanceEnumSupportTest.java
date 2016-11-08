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
package com.android.tools.idea.uibuilder.property.editors.support;

import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;

import java.util.regex.Matcher;

import static com.android.SdkConstants.APPCOMPAT_LIB_ARTIFACT;
import static com.android.tools.idea.uibuilder.property.editors.support.StyleEnumSupportTest.createFrameworkStyle;
import static com.android.tools.idea.uibuilder.property.editors.support.StyleEnumSupportTest.createStyle;
import static com.android.tools.idea.uibuilder.property.editors.support.TextAppearanceEnumSupport.TEXT_APPEARANCE_PATTERN;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(JUnit4.class)
public class TextAppearanceEnumSupportTest {
  private static final StyleResourceValue TEXT_APPEARANCE_STYLE = createFrameworkStyle("TextAppearance");
  private static final StyleResourceValue MATERIAL_STYLE = createFrameworkStyle("TextAppearance.Material");
  private static final StyleResourceValue MATERIAL_SMALL_STYLE = createFrameworkStyle("TextAppearance.Material.Small");
  private static final StyleResourceValue APPCOMPAT_STYLE = createStyle("TextAppearance.AppCompat", APPCOMPAT_LIB_ARTIFACT);
  private static final StyleResourceValue APPCOMPAT_SMALL_STYLE = createStyle("TextAppearance.AppCompat.Small", APPCOMPAT_LIB_ARTIFACT);
  private static final StyleResourceValue APPLICATION_STYLE = createStyle("TextAppearance.MyOwnStyle.Medium", null);

  @Mock
  private NlProperty myProperty;
  @Mock
  private ResourceResolver myResolver;
  @Mock
  private StyleFilter myStyleFilter;

  private TextAppearanceEnumSupport mySupport;

  @Before
  public void setUp() {
    initMocks(this);
    when(myProperty.getResolver()).thenReturn(myResolver);
    when(myProperty.resolveValue(anyString())).thenAnswer(invocation -> invocation.getArguments()[0]);
    when(myProperty.resolveValue("?attr/textAppearanceSmall")).thenReturn("@android:style/TextAppearance.Material.Small");
    when(myResolver.getStyle("TextAppearance.Material.Small", true)).thenReturn(MATERIAL_SMALL_STYLE);
    when(myResolver.getStyle("TextAppearance.AppCompat", false)).thenReturn(APPCOMPAT_STYLE);
    when(myResolver.getStyle("TextAppearance.MyOwnStyle.Medium", false)).thenReturn(APPLICATION_STYLE);

    mySupport = new TextAppearanceEnumSupport(myProperty, myStyleFilter);
  }

  @Test
  public void testTextAppearancePattern() {
    checkTextAppearancePattern("TextAppearance", true, null);
    checkTextAppearancePattern("TextAppearance.Small", true, "Small");
    checkTextAppearancePattern("@android:style/TextAppearance.Material.Small", true, "Material.Small");
    checkTextAppearancePattern("@style/TextAppearance.AppCompat.Small", true, "AppCompat.Small");
    checkTextAppearancePattern("WhatEver", false, null);
  }

  private static void checkTextAppearancePattern(@NotNull String value, boolean expectedMatch, @Nullable String expectedMatchValue) {
    Matcher matcher = TEXT_APPEARANCE_PATTERN.matcher(value);
    assertThat(matcher.matches()).isEqualTo(expectedMatch);
    if (expectedMatch) {
      assertThat(matcher.group(5)).isEqualTo(expectedMatchValue);
    }
  }

  @Test
  public void testFindPossibleValues() {
    when(myStyleFilter.getStylesDerivedFrom("TextAppearance", true)).thenReturn(ImmutableList.of(
      TEXT_APPEARANCE_STYLE, MATERIAL_STYLE, MATERIAL_SMALL_STYLE, APPCOMPAT_STYLE, APPCOMPAT_SMALL_STYLE, APPLICATION_STYLE));
    assertThat(mySupport.getAllValues()).containsExactly(
      new ValueWithDisplayString("TextAppearance", "@android:style/TextAppearance"),
      new ValueWithDisplayString("Material", "@android:style/TextAppearance.Material"),
      new ValueWithDisplayString("Material.Small", "@android:style/TextAppearance.Material.Small"),
      ValueWithDisplayString.SEPARATOR,
      new ValueWithDisplayString("AppCompat", "@style/TextAppearance.AppCompat"),
      new ValueWithDisplayString("AppCompat.Small", "@style/TextAppearance.AppCompat.Small"),
      ValueWithDisplayString.SEPARATOR,
      new ValueWithDisplayString("MyOwnStyle.Medium", "@style/TextAppearance.MyOwnStyle.Medium")).inOrder();
  }

  @Test
  public void testCreateDefaultValue() {
    assertThat(mySupport.createValue(""))
      .isEqualTo(ValueWithDisplayString.UNSET);
  }

  @Test
  public void testCreateFromCompleteFrameworkAttributeValue() {
    assertThat(mySupport.createValue("@android:style/TextAppearance.Material.Small"))
      .isEqualTo(new ValueWithDisplayString("Material.Small", "@android:style/TextAppearance.Material.Small"));
  }

  @Test
  public void testCreateFromCompleteAppcompatAttributeValue() {
    assertThat(mySupport.createValue("@style/TextAppearance.AppCompat"))
      .isEqualTo(new ValueWithDisplayString("AppCompat", "@style/TextAppearance.AppCompat"));
  }

  @Test
  public void testCreateFromCompleteUserDefinedAttributeValue() {
    assertThat(mySupport.createValue("@style/TextAppearance.MyOwnStyle.Medium"))
      .isEqualTo(new ValueWithDisplayString("MyOwnStyle.Medium", "@style/TextAppearance.MyOwnStyle.Medium"));
  }

  @Test
  public void testCreateFromIncompleteFrameworkAttributeValue() {
    assertThat(mySupport.createValue("TextAppearance.Material.Small"))
      .isEqualTo(new ValueWithDisplayString("Material.Small", "@android:style/TextAppearance.Material.Small"));
  }

  @Test
  public void testCreateFromIncompleteAppcompatAttributeValue() {
    assertThat(mySupport.createValue("TextAppearance.AppCompat"))
      .isEqualTo(new ValueWithDisplayString("AppCompat", "@style/TextAppearance.AppCompat"));
  }

  @Test
  public void testCreateFromIncompleteUserDefinedAttributeValue() {
    assertThat(mySupport.createValue("TextAppearance.MyOwnStyle.Medium"))
      .isEqualTo(new ValueWithDisplayString("MyOwnStyle.Medium", "@style/TextAppearance.MyOwnStyle.Medium"));
  }

  @Test
  public void testCreateFromIncompleteUnknownAttributeValue() {
    assertThat(mySupport.createValue("Unknown.Medium"))
      .isEqualTo(new ValueWithDisplayString("Unknown.Medium", "@style/Unknown.Medium"));
  }

  @Test
  public void testCreateFromMinimalFrameworkAttributeValue() {
    assertThat(mySupport.createValue("Material.Small"))
      .isEqualTo(new ValueWithDisplayString("Material.Small", "@android:style/TextAppearance.Material.Small"));
  }

  @Test
  public void testCreateFromMinimalAppcompatAttributeValue() {
    assertThat(mySupport.createValue("AppCompat"))
      .isEqualTo(new ValueWithDisplayString("AppCompat", "@style/TextAppearance.AppCompat"));
  }

  @Test
  public void testCreateFromMinimalUserDefinedAttributeValue() {
    assertThat(mySupport.createValue("MyOwnStyle.Medium"))
      .isEqualTo(new ValueWithDisplayString("MyOwnStyle.Medium", "@style/TextAppearance.MyOwnStyle.Medium"));
  }

  @Test
  public void testCreateFromThemeValue() {
    assertThat(mySupport.createValue("?attr/textAppearanceSmall"))
      .isEqualTo(new ValueWithDisplayString("Material.Small", "?attr/textAppearanceSmall"));
  }
}
