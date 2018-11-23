/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.rendering.parsers;

import static com.android.ide.common.rendering.api.ResourceNamespace.ANDROID;
import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;
import static com.android.tools.idea.util.FileExtensions.toVirtualFile;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.ide.common.fonts.FontDetail;
import com.android.ide.common.fonts.FontFamily;
import com.android.ide.common.fonts.FontProvider;
import com.android.ide.common.fonts.FontSource;
import com.android.ide.common.fonts.MutableFontDetail;
import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.resources.ResourceType;
import com.android.tools.idea.fonts.DownloadableFontCacheService;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.rendering.RenderTestUtil;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.UIUtil;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;

@SuppressWarnings("SpellCheckingInspection")
public class LayoutPullParsersTest extends AndroidTestCase {
  private DownloadableFontCacheService myFontCacheServiceMock;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    RenderTestUtil.beforeRenderTestCase();
    myFontCacheServiceMock = mock(DownloadableFontCacheService.class);
    registerApplicationComponent(DownloadableFontCacheService.class, myFontCacheServiceMock);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      RenderTestUtil.afterRenderTestCase();
    } finally {
      super.tearDown();
    }
  }

  private RenderTask createRenderTask(VirtualFile file) {
    return RenderTestUtil.createRenderTask(myFacet, file, RenderTestUtil.HOLO_THEME);
  }

  public void testIsSupported() {
    VirtualFile layoutFile = myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout-land-v14/foo.xml");
    VirtualFile menuFile = myFixture.copyFileToProject("menus/menu1.xml", "res/menu/menu1.xml");
    VirtualFile drawableFile = myFixture.copyFileToProject("menus/menu1.xml", "res/menu/menu1.xml");

    PsiManager psiManager = PsiManager.getInstance(getProject());

    assertTrue(LayoutPullParsers.isSupported(psiManager.findFile(layoutFile)));
    assertTrue(LayoutPullParsers.isSupported(psiManager.findFile(menuFile)));
    assertTrue(LayoutPullParsers.isSupported(psiManager.findFile(drawableFile)));
  }

  public void testRenderDrawable() throws Exception {
    VirtualFile file = myFixture.copyFileToProject("drawables/progress_horizontal.xml", "res/drawable/progress_horizontal.xml");
    assertNotNull(file);
    RenderTask task = createRenderTask(file);
    assertNotNull(task);
    ILayoutPullParser parser = LayoutPullParsers.create(task);
    assertTrue(parser instanceof DomPullParser);
    Element root = ((DomPullParser)parser).getRoot();

    String actualLayout = XmlPrettyPrinter.prettyPrint(root, true);
    String expectedLayout = Joiner.on(System.lineSeparator()).join(
      "<ImageView",
      "xmlns:android=\"http://schemas.android.com/apk/res/android\"",
      "layout_width=\"fill_parent\"",
      "layout_height=\"fill_parent\"",
      "src=\"" + file.getPath() + "\" />",
      ""
    );

    assertEquals(expectedLayout, actualLayout);

    RenderTestUtil.scaleAndCheckRendering(task, getTestDataPath() + "/render/thumbnails/drawable/progress_horizontal.png");
  }

  /**
   * Check that when two drawables with the same name exist in multiple densities, we select the intended one.
   */
  public void testRenderConflictingDrawables() {
    final String redBoxContent = "<shape xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                 "    android:shape=\"rectangle\">\n" +
                                 "    <solid android:color=\"#F00\"/>\n" +
                                 "</shape>";
    final String blueBoxContent = "<shape xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                  "    android:shape=\"rectangle\">\n" +
                                  "    <solid android:color=\"#00F\"/>\n" +
                                  "</shape>";
    VirtualFile redBox = myFixture.addFileToProject("res/drawable/box.xml", redBoxContent).getVirtualFile();
    VirtualFile blueBox = myFixture.addFileToProject("res/drawable-xxxhdpi/box.xml", blueBoxContent).getVirtualFile();

    RenderTask task = createRenderTask(redBox);
    assertNotNull(task);
    ILayoutPullParser parser = LayoutPullParsers.create(task);
    assertTrue(parser instanceof DomPullParser);
    Element root = ((DomPullParser)parser).getRoot();

    String actualLayout = XmlPrettyPrinter.prettyPrint(root, true);
    String expectedLayout = Joiner.on(System.lineSeparator()).join(
      "<ImageView",
      "xmlns:android=\"http://schemas.android.com/apk/res/android\"",
      "layout_width=\"fill_parent\"",
      "layout_height=\"fill_parent\"",
      "src=\"" + redBox.getPath() + "\" />",
      ""
    );

    assertEquals(expectedLayout, actualLayout);

    task = createRenderTask(blueBox);
    assertNotNull(task);
    parser = LayoutPullParsers.create(task);
    assertTrue(parser instanceof DomPullParser);
    root = ((DomPullParser)parser).getRoot();

    actualLayout = XmlPrettyPrinter.prettyPrint(root, true);
    expectedLayout = Joiner.on(System.lineSeparator()).join(
      "<ImageView",
      "xmlns:android=\"http://schemas.android.com/apk/res/android\"",
      "layout_width=\"fill_parent\"",
      "layout_height=\"fill_parent\"",
      "src=\"" + blueBox.getPath() + "\" />",
      ""
    );

    assertEquals(expectedLayout, actualLayout);
  }



  public void testRenderMenuWithShowInNavigationViewAttribute() {
    RenderTask task = createRenderTask(myFixture.copyFileToProject("menus/activity_main_drawer.xml",
                                                                   "res/menu/activity_main_drawer.xml"));

    DomPullParser parser = (DomPullParser)LayoutPullParsers.create(task);
    assert parser != null;

    assertFalse(task.getShowDecorations());

    String expected = String.format("<android.support.design.widget.NavigationView%n" +
                                    "xmlns:android=\"http://schemas.android.com/apk/res/android\"%n" +
                                    "xmlns:app=\"http://schemas.android.com/apk/res-auto\"%n" +
                                    "android:layout_width=\"wrap_content\"%n" +
                                    "android:layout_height=\"match_parent\"%n" +
                                    "app:menu=\"@menu/activity_main_drawer\" />%n");

    assertEquals(expected, XmlPrettyPrinter.prettyPrint(parser.getRoot(), true));
  }

  public void testRenderAdaptiveIcon() throws IOException {
    VirtualFile file = myFixture.copyFileToProject("drawables/adaptive.xml", "res/mipmap/adaptive.xml");
    myFixture.copyFileToProject("drawables/foreground.xml", "res/drawable/foreground.xml");
    myFixture.copyFileToProject("drawables/background.xml", "res/drawable/background.xml");
    assertNotNull(file);
    RenderTask task = createRenderTask(file);
    assertNotNull(task);
    ILayoutPullParser parser = LayoutPullParsers.create(task);
    assertTrue(parser instanceof DomPullParser);
    Element root = ((DomPullParser)parser).getRoot();

    String actualLayout = XmlPrettyPrinter.prettyPrint(root, true);
    String expectedLayout = Joiner.on(System.lineSeparator()).join(
      "<ImageView",
      "xmlns:android=\"http://schemas.android.com/apk/res/android\"",
      "layout_width=\"fill_parent\"",
      "layout_height=\"fill_parent\"",
      "src=\"" + file.getPath() + "\" />",
      ""
    );

    assertEquals(expectedLayout, actualLayout);

    RenderTestUtil.scaleAndCheckRendering(task, getTestDataPath() + "/render/thumbnails/mipmap/adaptive.png");
  }


  public void testFontFamily() throws Exception {
    myFixture.copyFileToProject("fonts/customfont.ttf", "res/font/fonta.ttf");
    myFixture.copyFileToProject("fonts/customfont.ttf", "res/font/fontb.ttf");
    VirtualFile file = myFixture.copyFileToProject("fonts/my_font_family.xml", "res/font/my_font_family.xml");
    assertNotNull(file);
    RenderTask task = createRenderTask(file);
    assertNotNull(task);
    ILayoutPullParser parser = LayoutPullParsers.create(task);
    assertTrue(parser instanceof DomPullParser);
    Element root = ((DomPullParser)parser).getRoot();

    String actualLayout = XmlPrettyPrinter.prettyPrint(root, true);
    String labelColor = "#" + ColorUtil.toHex(UIUtil.getLabelForeground());
    String expectedLayout = Joiner.on(System.lineSeparator()).join(
      "<LinearLayout",
      "xmlns:android=\"http://schemas.android.com/apk/res/android\"",
      "layout_width=\"fill_parent\"",
      "layout_height=\"wrap_content\"",
      "orientation=\"vertical\" >",
      "<TextView",
      "    layout_width=\"wrap_content\"",
      "    layout_height=\"wrap_content\"",
      "    fontFamily=\"@font/my_font_family\"",
      "    paddingBottom=\"20dp\"",
      "    text=\"Lorem ipsum dolor sit amet, consectetur adipisicing elit.\"",
      "    textColor=\"" + labelColor + "\"",
      "    textSize=\"30sp\"",
      "    textStyle=\"normal\" />",
      "<TextView",
      "    layout_width=\"wrap_content\"",
      "    layout_height=\"wrap_content\"",
      "    fontFamily=\"@font/my_font_family\"",
      "    paddingBottom=\"20dp\"",
      "    text=\"Lorem ipsum dolor sit amet, consectetur adipisicing elit.\"",
      "    textColor=\"" + labelColor + "\"",
      "    textSize=\"30sp\"",
      "    textStyle=\"italic\" />",
      "</LinearLayout>",
      "");

    assertEquals(expectedLayout, actualLayout);

    RenderTestUtil.scaleAndCheckRendering(task, getTestDataPath() + "/render/thumbnails/fonts/fontFamily.png");
  }

  private static FontFamily createRobotoFontFamily() {
    return new FontFamily(FontProvider.GOOGLE_PROVIDER, FontSource.DOWNLOADABLE, "Roboto", "", "", ImmutableList.of(
      new MutableFontDetail(700, 100, true, "https://fonts.google.com/roboto700i", "", false)));
  }

  @NotNull
  private FontFamily createMockFontFamily(boolean fileExists) {
    File fileMock = mock(File.class);
    when(fileMock.exists()).thenReturn(fileExists);
    FontFamily family = createRobotoFontFamily();
    when(myFontCacheServiceMock.getCachedFontFile(any(FontDetail.class))).thenReturn(fileMock);
    return family;
  }

  public void testDownloadedFontFamily() {
    FontFamily compoundFontFamily = createMockFontFamily(true);

    VirtualFile file = myFixture.copyFileToProject("fonts/roboto_bold.xml", "res/font/roboto_bold.xml");
    assertNotNull(file);

    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    ILayoutPullParser parser = LayoutPullParsers.createFontFamilyParser((XmlFile)psiFile, (name) -> compoundFontFamily);
    assertTrue(parser instanceof DomPullParser);
    Element root = ((DomPullParser)parser).getRoot();

    String actualLayout = XmlPrettyPrinter.prettyPrint(root, true);
    String labelColor = "#" + ColorUtil.toHex(UIUtil.getLabelForeground());
    String expectedLayout = Joiner.on(System.lineSeparator()).join(
      "<LinearLayout",
      "xmlns:android=\"http://schemas.android.com/apk/res/android\"",
      "layout_width=\"fill_parent\"",
      "layout_height=\"wrap_content\"",
      "orientation=\"vertical\" >",
      "<TextView",
      "    layout_width=\"wrap_content\"",
      "    layout_height=\"wrap_content\"",
      "    fontFamily=\"@font/roboto_bold\"",
      "    paddingBottom=\"20dp\"",
      "    text=\"Lorem ipsum dolor sit amet, consectetur adipisicing elit.\"",
      "    textColor=\"" + labelColor + "\"",
      "    textSize=\"30sp\"",
      "    textStyle=\"italic\" />",
      "</LinearLayout>",
      "");

    assertEquals(expectedLayout, actualLayout);
  }

  public void testDownloadableFontWithoutFile() {
    // This is a downloadable font that hasn't been cached yet
    FontFamily compoundFontFamily = createMockFontFamily(false);

    VirtualFile file = myFixture.copyFileToProject("fonts/my_downloadable_font_family.xml", "res/font/my_downloadable_font_family.xml");
    assertNotNull(file);

    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    assertNull(LayoutPullParsers.createFontFamilyParser((XmlFile)psiFile, (name) -> compoundFontFamily));
  }

  public void testNamespace() {
    // Project XML:
    VirtualFile layoutFile = myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout-land-v14/foo.xml");
    VirtualFile menuFile = myFixture.copyFileToProject("menus/menu1.xml", "res/menu/menu1.xml");
    VirtualFile drawableFile = myFixture.copyFileToProject("menus/menu1.xml", "res/menu/menu1.xml");

    assertEquals(RES_AUTO, LayoutPullParsers.create(createRenderTask(layoutFile)).getLayoutNamespace());
    assertEquals(RES_AUTO, LayoutPullParsers.create(createRenderTask(menuFile)).getLayoutNamespace());
    assertEquals(RES_AUTO, LayoutPullParsers.create(createRenderTask(drawableFile)).getLayoutNamespace());

    // Framework XML: API28 has two default app icons: res/drawable-watch/sym_def_app_icon.xml and res/drawable/sym_def_app_icon.xml
    List<ResourceItem> frameworkResourceItems = ResourceRepositoryManager.getOrCreateInstance(myFacet)
      .getFrameworkResources(false)
      .getResources(ANDROID, ResourceType.DRAWABLE, "sym_def_app_icon");

    for (ResourceItem frameworkResourceItem : frameworkResourceItems) {
      VirtualFile frameworkFile = toVirtualFile(frameworkResourceItem.getSource());
      assertEquals(ANDROID, LayoutPullParsers.create(createRenderTask(frameworkFile)).getLayoutNamespace());
    }
  }
}
