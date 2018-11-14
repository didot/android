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
package com.android.tools.idea.uibuilder.palette;

import com.android.SdkConstants;
import com.android.tools.idea.common.model.NlLayoutType;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.handlers.linear.LinearLayoutHandler;
import com.google.common.collect.ImmutableList;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.util.CollectionQuery;
import icons.AndroidIcons;
import icons.StudioIcons;
import java.util.concurrent.CountDownLatch;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.google.common.truth.Truth.assertThat;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class NlPaletteModelTest {
  @Rule
  public final AndroidProjectRule projectRule = AndroidProjectRule.onDisk().initAndroid(true);
  private AndroidFacet facet;
  private JavaCodeInsightTestFixture fixture;
  private NlPaletteModel model;

  @Before
  public void setUp() throws Exception {
    fixture = projectRule.getFixture(JavaCodeInsightTestFixture.class);
    facet = AndroidFacet.getInstance(projectRule.getModule());
    model = NlPaletteModel.get(facet);

    try (Reader reader = new InputStreamReader(NlPaletteModel.class.getResourceAsStream(NlLayoutType.LAYOUT.getPaletteFileName()))) {
      model.loadPalette(reader, NlLayoutType.LAYOUT);
    }
  }

  @After
  public void tearDown() throws Exception {
      model = null;
  }

  @Test
  public void addIllegalThirdPartyComponent() {
    Palette palette = model.getPalette(NlLayoutType.LAYOUT);
    boolean added = model.addAdditionalComponent(NlLayoutType.LAYOUT, NlPaletteModel.THIRD_PARTY_GROUP, palette, null, LINEAR_LAYOUT, LINEAR_LAYOUT, null, null,
                                                 SdkConstants.CONSTRAINT_LAYOUT_LIB_ARTIFACT, null, Collections.emptyList(), Collections.emptyList());
    assertThat(added).isFalse();
    assertThat(getGroupByName(NlPaletteModel.THIRD_PARTY_GROUP)).isNull();

    ViewHandler handler = ViewHandlerManager.get(facet).getHandler(LINEAR_LAYOUT);
    assertThat(handler).isInstanceOf(LinearLayoutHandler.class);
  }

  @Test
  public void addThirdPartyComponent() {
    registerJavaClasses();
    registerFakeBaseViewHandler();
    Palette palette = model.getPalette(NlLayoutType.LAYOUT);
    String tag = "com.example.FakeCustomView";
    boolean added = model
      .addAdditionalComponent(NlLayoutType.LAYOUT, NlPaletteModel.THIRD_PARTY_GROUP, palette, AndroidIcons.Android, tag, tag,
                              getXml(tag), getPreviewXml(tag), SdkConstants.CONSTRAINT_LAYOUT_LIB_ARTIFACT,
                              "family", ImmutableList.of("family", "size"), Collections.emptyList());
    Palette.Group thirdParty = getGroupByName(NlPaletteModel.THIRD_PARTY_GROUP);
    assertThat(added).isTrue();
    assertThat(thirdParty).isNotNull();
    assertThat(thirdParty.getItems().size()).isEqualTo(1);

    Palette.Item item = (Palette.Item)thirdParty.getItem(0);
    assertThat(item.getTagName()).isEqualTo(tag);
    assertThat(item.getIcon()).isEqualTo(AndroidIcons.Android);
    assertThat(item.getTitle()).isEqualTo("FakeCustomView");
    assertThat(item.getGradleCoordinateId()).isEqualTo(SdkConstants.CONSTRAINT_LAYOUT_LIB_ARTIFACT);
    assertThat(item.getXml()).isEqualTo(getXml(tag));

    ViewHandler handler = ViewHandlerManager.get(facet).getHandler(tag);
    assertThat(handler).isNotNull();
    assertThat(handler.getTitle(tag)).isEqualTo("FakeCustomView");
    assertThat(handler.getIcon(tag)).isEqualTo(AndroidIcons.Android);
    assertThat(handler.getGradleCoordinateId(tag)).isEqualTo(SdkConstants.CONSTRAINT_LAYOUT_LIB_ARTIFACT);
    assertThat(handler.getPreviewScale(tag)).isWithin(0.0).of(1.0);
    assertThat(handler.getInspectorProperties()).containsExactly("family", "size");
    assertThat(handler.getLayoutInspectorProperties()).isEmpty();
    assertThat(handler.getPreferredProperty()).isEqualTo("family");
  }

  @Test
  public void projectComponents() throws InterruptedException {
    registerJavaClasses();
    Palette.Group projectComponents = getGroupByName(NlPaletteModel.PROJECT_GROUP);
    assertThat(projectComponents).isNull();

    CountDownLatch latch = new CountDownLatch(1);
    model.setUpdateListener((paletteModel, layoutType) -> latch.countDown());

    model.loadAdditionalComponents(NlLayoutType.LAYOUT, (project) -> {
      PsiClass customView = mock(PsiClass.class);
      when(customView.getName()).thenReturn("FakeCustomView");
      when(customView.getQualifiedName()).thenReturn("com.example.FakeCustomView");
      return new CollectionQuery<>(ImmutableList.of(customView));
    });
    latch.await();

    projectComponents = getGroupByName(NlPaletteModel.PROJECT_GROUP);
    assertThat(projectComponents.getItems().size()).isEqualTo(1);

    String tag = "com.example.FakeCustomView";
    Palette.Item item = (Palette.Item)projectComponents.getItem(0);
    assertThat(item.getTagName()).isEqualTo(tag);
    assertThat(item.getIcon()).isEqualTo(StudioIcons.LayoutEditor.Palette.CUSTOM_VIEW);
    assertThat(item.getTitle()).isEqualTo("FakeCustomView");
    assertThat(item.getGradleCoordinateId()).isEmpty();
    assertThat(item.getXml()).isEqualTo("<com.example.FakeCustomView\n" +
                                        "    android:layout_width=\"wrap_content\"\n" +
                                        "    android:layout_height=\"wrap_content\" />\n");
    assertThat(item.getMetaTags()).isEmpty();
    assertThat(item.getParent()).isEqualTo(projectComponents);
  }

  @Test
  public void idsAreUnique() {
    checkIdsAreUniqueInPalette(NlLayoutType.LAYOUT);
    checkIdsAreUniqueInPalette(NlLayoutType.MENU);
    checkIdsAreUniqueInPalette(NlLayoutType.PREFERENCE_SCREEN);
  }

  private void checkIdsAreUniqueInPalette(@NotNull NlLayoutType layoutType) {
    Palette palette = model.getPalette(layoutType);
    Set<String> ids = new HashSet<>();
    palette.accept(item -> assertTrue("ID is not unique: " + item.getId() + " with layoutType: " + layoutType, ids.add(item.getId())));
    assertThat(ids).isNotEmpty();
  }

  @Nullable
  private Palette.Group getGroupByName(@NotNull String name) {
    Palette palette = model.getPalette(NlLayoutType.LAYOUT);
    List<Palette.BaseItem> groups = palette.getItems();
    return groups.stream()
      .filter(Palette.Group.class::isInstance)
      .map(Palette.Group.class::cast)
      .filter(g -> name.equals(g.getName()))
      .findFirst()
      .orElse(null);
  }

  private void registerFakeBaseViewHandler() {
    ViewHandlerManager manager = ViewHandlerManager.get(facet);
    ViewHandler handler = manager.getHandler(SdkConstants.VIEW);
    assertThat(handler).isNotNull();
    manager.registerHandler("com.example.FakeView", handler);
  }

  private void registerJavaClasses() {
    fixture.addClass("package android.view; public class View {}");
    fixture.addClass("package com.example; public class FakeCustomView extends android.view.View {}");
  }

  @Language("XML")
  @NotNull
  private static String getXml(@NotNull String tag) {
    return String.format("<%1$s></%1$s>", tag);
  }

  @Language("XML")
  @NotNull
  private static String getPreviewXml(@NotNull String tag) {
    return String.format("<%1$s><TextView text=\"2\"/></%1$s>", tag);
  }
}
