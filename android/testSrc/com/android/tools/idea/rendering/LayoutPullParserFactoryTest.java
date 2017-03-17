/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.utils.SdkUtils;
import com.google.common.base.Joiner;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import org.w3c.dom.Element;

@SuppressWarnings("SpellCheckingInspection")
public class LayoutPullParserFactoryTest extends RenderTestBase {
  @SuppressWarnings("ConstantConditions")
  public void testIsSupported() throws Exception {
    VirtualFile layoutFile = myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout-land-v14/foo.xml");
    VirtualFile menuFile = myFixture.copyFileToProject("menus/menu1.xml", "res/menu/menu1.xml");
    VirtualFile drawableFile = myFixture.copyFileToProject("menus/menu1.xml", "res/menu/menu1.xml");

    PsiManager psiManager = PsiManager.getInstance(getProject());

    assertTrue(LayoutPullParserFactory.isSupported(psiManager.findFile(layoutFile)));
    assertTrue(LayoutPullParserFactory.isSupported(psiManager.findFile(menuFile)));
    assertTrue(LayoutPullParserFactory.isSupported(psiManager.findFile(drawableFile)));
  }

  public void testRenderDrawable() throws Exception {
    VirtualFile file = myFixture.copyFileToProject("drawables/progress_horizontal.xml", "res/drawable/progress_horizontal.xml");
    assertNotNull(file);
    RenderTask task = createRenderTask(file);
    assertNotNull(task);
    ILayoutPullParser parser = LayoutPullParserFactory.create(task);
    assertTrue(parser instanceof DomPullParser);
    Element root = ((DomPullParser)parser).getRoot();

    String actualLayout = XmlPrettyPrinter.prettyPrint(root, true);
    String expectedLayout = Joiner.on(SdkUtils.getLineSeparator()).join(
      "<ImageView",
      "xmlns:android=\"http://schemas.android.com/apk/res/android\"",
      "layout_width=\"fill_parent\"",
      "layout_height=\"fill_parent\"",
      "src=\"@drawable/progress_horizontal\" />",
      ""
    );

    assertEquals(expectedLayout, actualLayout);

    checkRendering(task, "drawable/progress_horizontal.png");
  }

  public void testRenderAdaptiveIcon() throws Exception {
    // TODO: Replace the drawable with an actual adaptive-icon (see TODO below)
    VirtualFile file = myFixture.copyFileToProject("drawables/progress_horizontal.xml", "res/mipmap/adaptive.xml");
    assertNotNull(file);
    RenderTask task = createRenderTask(file);
    assertNotNull(task);
    ILayoutPullParser parser = LayoutPullParserFactory.create(task);
    assertTrue(parser instanceof DomPullParser);
    Element root = ((DomPullParser)parser).getRoot();

    String actualLayout = XmlPrettyPrinter.prettyPrint(root, true);
    String expectedLayout = Joiner.on(SdkUtils.getLineSeparator()).join(
      "<ImageView",
      "xmlns:android=\"http://schemas.android.com/apk/res/android\"",
      "layout_width=\"fill_parent\"",
      "layout_height=\"fill_parent\"",
      "src=\"@mipmap/adaptive\" />",
      ""
    );

    assertEquals(expectedLayout, actualLayout);

    // TODO: Create the golden image once layoutlib adaptive-icon rendering is merged
    //checkRendering(task, "mipmap/adaptive.png");
  }


  public void testFontFamily() throws Exception {
    myFixture.copyFileToProject("fonts/customfont.ttf", "res/font/fonta.ttf");
    myFixture.copyFileToProject("fonts/customfont.ttf", "res/font/fontb.ttf");
    myFixture.copyFileToProject("fonts/my_font_family.xml", "res/font/my_font_family.xml");
    VirtualFile file = myFixture.copyFileToProject("fonts/my_font_family.xml", "res/font/my_font_family.xml");
    assertNotNull(file);
    RenderTask task = createRenderTask(file);
    assertNotNull(task);
    ILayoutPullParser parser = LayoutPullParserFactory.create(task);
    assertTrue(parser instanceof DomPullParser);
    Element root = ((DomPullParser)parser).getRoot();

    String actualLayout = XmlPrettyPrinter.prettyPrint(root, true);
    String expectedLayout = Joiner.on(SdkUtils.getLineSeparator()).join(
      "<LinearLayout",
      "xmlns:android=\"http://schemas.android.com/apk/res/android\"",
      "layout_width=\"fill_parent\"",
      "layout_height=\"wrap_content\"",
      "orientation=\"vertical\" >",
      "<TextView",
      "    layout_width=\"wrap_content\"",
      "    layout_height=\"wrap_content\"",
      "    fontFamily=\"@font/my_font_family\"",
      "    text=\"Lorem ipsum dolor sit amet.\"",
      "    textSize=\"40sp\"",
      "    textStyle=\"normal\" />",
      "<TextView",
      "    layout_width=\"wrap_content\"",
      "    layout_height=\"wrap_content\"",
      "    fontFamily=\"@font/my_font_family\"",
      "    text=\"Lorem ipsum dolor sit amet.\"",
      "    textSize=\"40sp\"",
      "    textStyle=\"italic\" />",
      "</LinearLayout>",
      "");

    assertEquals(expectedLayout, actualLayout);

    checkRendering(task, "fonts/fontFamily.png");
  }
}
