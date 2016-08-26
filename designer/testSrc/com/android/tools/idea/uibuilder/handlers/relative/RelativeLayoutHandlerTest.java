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
package com.android.tools.idea.uibuilder.handlers.relative;

import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.LayoutTestUtilities;
import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.model.NlModel;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.LayoutTestUtilities.mockViewWithBaseline;
import static com.android.tools.idea.uibuilder.model.SegmentType.*;
import static java.awt.event.InputEvent.SHIFT_MASK;

public class RelativeLayoutHandlerTest extends LayoutTestCase {

  public void testResizeToNowhereWithModifierKeepsPosition() {
    surface().screen(createModel())
      .get("@id/checkbox")
      .resize(TOP, LEFT)
      .drag(0, 0)
      .modifiers(SHIFT_MASK)
      .release()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_marginLeft=\"100dp\"\n" +
                 "        android:layout_marginTop=\"100dp\"\n" +
                 "        android:layout_below=\"@+id/button\"\n" +
                 "        android:layout_toRightOf=\"@+id/button\" />");
  }

  public void testResizeTopRemovesVerticalConstraint() {
    surface().screen(createModel())
      .get("@id/checkbox")
      .resize(TOP)
      .drag(10, 10)
      .release()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_toRightOf=\"@id/button\"\n" +
                 "        android:layout_marginLeft=\"100dp\" />");
  }

  public void testResizeLeftRemovesHorizontalConstraint() {
    surface().screen(createModel())
      .get("@id/checkbox")
      .resize(LEFT)
      .drag(10, 10)
      .release()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_below=\"@id/button\"\n" +
                 "        android:layout_marginTop=\"100dp\" />");
  }

  public void testResizeTopLeftSnapToLeftOfButton() {
    surface().screen(createModel())
      .get("@id/checkbox")
      .resize(TOP, LEFT)
      .drag(-195, 10)
      .release()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_alignLeft=\"@+id/button\" />");
  }

  // Resize left, top: snap to top edge of button
  public void testResizeTopLeftSnapToTopOfButton() {
    surface().screen(createModel())
      .get("@id/checkbox")
      .resize(TOP, LEFT)
      .drag(100, -195)
      .release()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_alignTop=\"@+id/button\" />");
  }

  public void testResizeTopLeftWithModifierSnapToBottomLeftOfButton() {
    surface().screen(createModel())
      .get("@id/checkbox")
      .resize(TOP, LEFT)
      .modifiers(SHIFT_MASK)
      .drag(-195, -100)
      .release()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_below=\"@+id/button\"\n" +
                 "        android:layout_alignLeft=\"@+id/button\" />");
  }

  public void testResizeTopLeftWithModifierCloseToBottomLeftOfButton() {
    surface().screen(createModel())
      .get("@id/checkbox")
      .resize(TOP, LEFT)
      .modifiers(SHIFT_MASK)
      .drag(-175, -78)
      .release()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_marginLeft=\"26dp\"\n" +
                 "        android:layout_marginTop=\"22dp\"\n" +
                 "        android:layout_below=\"@+id/button\"\n" +
                 "        android:layout_alignLeft=\"@+id/button\" />");
  }

  public void testResizeBottomRightWithModifier() {
    surface().screen(createModel())
      .get("@id/checkbox")
      .resize(BOTTOM, RIGHT)
      .modifiers(SHIFT_MASK)
      .drag(10, 10)
      .release()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_below=\"@id/button\"\n" +
                 "        android:layout_toRightOf=\"@id/button\"\n" +
                 "        android:layout_marginLeft=\"100dp\"\n" +
                 "        android:layout_marginTop=\"100dp\"\n" +
                 "        android:layout_alignParentBottom=\"true\"\n" +
                 "        android:layout_alignParentRight=\"true\"\n" +
                 "        android:layout_marginRight=\"670dp\"\n" +
                 "        android:layout_marginBottom=\"670dp\" />");
  }

  public void testResizeBottomRightWithModifierSnapToBottomOfLayout() {
    surface().screen(createModel())
      .get("@id/checkbox")
      .resize(BOTTOM, RIGHT)
      .modifiers(SHIFT_MASK)
      .drag(670, 670)
      .release()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_below=\"@id/button\"\n" +
                 "        android:layout_toRightOf=\"@id/button\"\n" +
                 "        android:layout_marginLeft=\"100dp\"\n" +
                 "        android:layout_marginTop=\"100dp\"\n" +
                 "        android:layout_alignParentBottom=\"true\"\n" +
                 "        android:layout_alignParentRight=\"true\" />");
  }

  public void testResizeBottomRightWithModifierToBottomOfLayout() {
    surface().screen(createModel())
      .get("@id/checkbox")
      .resize(BOTTOM, RIGHT)
      .modifiers(SHIFT_MASK)
      .drag(580, 580)
      .release()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_below=\"@id/button\"\n" +
                 "        android:layout_toRightOf=\"@id/button\"\n" +
                 "        android:layout_marginLeft=\"100dp\"\n" +
                 "        android:layout_marginTop=\"100dp\"\n" +
                 "        android:layout_alignParentBottom=\"true\"\n" +
                 "        android:layout_alignParentRight=\"true\"\n" +
                 "        android:layout_marginRight=\"100dp\"\n" +
                 "        android:layout_marginBottom=\"100dp\" />");
  }

  public void testMoveToNowhere() {
    surface().screen(createModel())
      .get("@id/checkbox")
      .drag()
      .drag(0, 0)
      .release()
      .primary()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_marginLeft=\"100dp\"\n" +
                 "        android:layout_marginTop=\"100dp\"\n" +
                 "        android:layout_below=\"@+id/button\"\n" +
                 "        android:layout_toRightOf=\"@+id/button\" />");
  }

  public void testMoveSnapToTopOfButton() {
    surface().screen(createModel())
      .get("@id/checkbox")
      .drag()
      .drag(30, -200)
      .release()
      .primary()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_marginLeft=\"130dp\"\n" +
                 "        android:layout_alignTop=\"@+id/button\"\n" +
                 "        android:layout_toRightOf=\"@+id/button\" />");
  }

  public void testMoveCloseToTopOfButton() {
    surface().screen(createModel())
      .get("@id/checkbox")
      .drag()
      .drag(30, -179)
      .release()
      .primary()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_marginLeft=\"130dp\"\n" +
                 "        android:layout_marginTop=\"22dp\"\n" +
                 "        android:layout_alignTop=\"@+id/button\"\n" +
                 "        android:layout_toRightOf=\"@+id/button\" />");
  }

  public void testMoveSnapToBottomOfButton() {
    surface().screen(createModel())
      .get("@id/checkbox")
      .drag()
      .drag(150, -120)
      .release()
      .primary()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_marginLeft=\"250dp\"\n" +
                 "        android:layout_alignBottom=\"@+id/button\"\n" +
                 "        android:layout_toRightOf=\"@+id/button\" />");
  }

  public void testMoveCloseToBottomOfButton() {
    surface().screen(createModel())
      .get("@id/checkbox")
      .drag()
      .drag(150, -150)
      .release()
      .primary()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_marginLeft=\"250dp\"\n" +
                 "        android:layout_alignBottom=\"@+id/button\"\n" +
                 "        android:layout_toRightOf=\"@+id/button\"\n" +
                 "        android:layout_marginBottom=\"30dp\" />");
  }

  public void testMoveSnapToMiddleOfLayout() {
    surface().screen(createModel())
      .get("@id/checkbox")
      .drag()
      .drag(200, 200)
      .release()
      .primary()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_centerVertical=\"true\"\n" +
                 "        android:layout_centerHorizontal=\"true\" />");
  }

  public void testMoveWithModifier() {
    surface().screen(createModel())
      .get("@id/checkbox")
      .drag()
      .modifiers(SHIFT_MASK)
      .drag(30, 30)
      .release()
      .primary()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_below=\"@+id/button\"\n" +
                 "        android:layout_toRightOf=\"@+id/button\" />");
  }

  public void testMoveSnapToBaseline() {
    surface().screen(createModel())
      .get("@id/textView")
      .drag()
      .drag(0, -153)
      .release()
      .primary()
      .expectXml("<TextView\n" +
                 "        android:id=\"@id/textView\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"100dp\"\n" +
                 "        android:layout_marginLeft=\"80dp\"\n" +
                 "        android:layout_alignBaseline=\"@+id/checkbox\"\n" +
                 "        android:layout_alignBottom=\"@+id/checkbox\"\n" +
                 "        android:layout_toRightOf=\"@+id/checkbox\" />");
  }

  public void testMoveDoesNotReorderComponents() throws Exception {
    //noinspection XmlUnusedNamespaceDeclaration
    surface().screen(createModel())
      .get("@id/checkbox")
      .drag()
      .drag(10, 10)
      .release()
      .primary()
      .parent()
      .expectXml("<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                 "    android:layout_width=\"match_parent\"\n" +
                 "    android:layout_height=\"match_parent\">\n" +
                 "\n" +
                 "    <Button\n" +
                 "        android:id=\"@id/button\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"100dp\"\n" +
                 "        android:layout_alignParentTop=\"true\"\n" +
                 "        android:layout_alignParentLeft=\"true\"\n" +
                 "        android:layout_alignParentStart=\"true\"\n" +
                 "        android:layout_marginTop=\"100dp\"\n" +
                 "        android:layout_marginLeft=\"100dp\"\n" +
                 "        android:layout_marginStart=\"100dp\" />\n" +
                 "\n" +
                 "    <CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_marginLeft=\"110dp\"\n" +
                 "        android:layout_marginTop=\"110dp\"\n" +
                 "        android:layout_below=\"@+id/button\"\n" +
                 "        android:layout_toRightOf=\"@+id/button\" />\n" +
                 "\n" +
                 "    <TextView\n" +
                 "        android:id=\"@id/textView\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"100dp\"\n" +
                 "        android:layout_below=\"@id/checkbox\"\n" +
                 "        android:layout_toRightOf=\"@id/checkbox\"\n" +
                 "        android:layout_marginLeft=\"80dp\"\n" +
                 "        android:layout_marginTop=\"80dp\" />\n" +
                 "</RelativeLayout>");
  }

  @Override
  public void tearDown() throws Exception {
    try {
      // The dependency checker for Relative Layout maintains a static cache with wek references.
      // For tests this can be cleared
      DependencyGraph.clearCacheAfterTests();
    }
    finally {
      //noinspection ThrowFromFinallyBlock
      super.tearDown();
    }
  }

  @NotNull
  private NlModel createModel() {
    ModelBuilder builder = model("relative.xml",
                                 component(RELATIVE_LAYOUT)
                                   .withBounds(0, 0, 1000, 1000)
                                   .matchParentWidth()
                                   .matchParentHeight()
                                   .children(
                                     component(BUTTON)
                                       .withBounds(100, 100, 100, 100)
                                       .id("@id/button")
                                       .width("100dp")
                                       .height("100dp")
                                       .withAttribute("android:layout_alignParentTop", "true")
                                       .withAttribute("android:layout_alignParentLeft", "true")
                                       .withAttribute("android:layout_alignParentStart", "true")
                                       .withAttribute("android:layout_marginTop", "100dp")
                                       .withAttribute("android:layout_marginLeft", "100dp")
                                       .withAttribute("android:layout_marginStart", "100dp"),

                                     component(CHECK_BOX)
                                       .withBounds(300, 300, 20, 20)
                                       .viewObject(mockViewWithBaseline(17))
                                       .id("@id/checkbox")
                                       .width("20dp")
                                       .height("20dp")
                                       .withAttribute("android:layout_below", "@id/button")
                                       .withAttribute("android:layout_toRightOf", "@id/button")
                                       .withAttribute("android:layout_marginLeft", "100dp")
                                       .withAttribute("android:layout_marginTop", "100dp"),

                                     component(TEXT_VIEW)
                                       .withBounds(400, 400, 100, 100)
                                       .viewObject(mockViewWithBaseline(70))
                                       .id("@id/textView")
                                       .width("100dp")
                                       .height("100dp")
                                       .withAttribute("android:layout_below", "@id/checkbox")
                                       .withAttribute("android:layout_toRightOf", "@id/checkbox")
                                       .withAttribute("android:layout_marginLeft", "80dp")
                                       .withAttribute("android:layout_marginTop", "80dp")
                                   ));
    NlModel model = builder.build();
    assertEquals(1, model.getComponents().size());
    assertEquals("NlComponent{tag=<RelativeLayout>, bounds=[0,0:1000x1000}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,100:100x100}\n" +
                 "    NlComponent{tag=<CheckBox>, bounds=[300,300:20x20}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[400,400:100x100}",
                 LayoutTestUtilities.toTree(model.getComponents()));

    format(model.getFile());
    assertEquals("<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                 "    android:layout_width=\"match_parent\"\n" +
                 "    android:layout_height=\"match_parent\">\n" +
                 "\n" +
                 "    <Button\n" +
                 "        android:id=\"@id/button\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"100dp\"\n" +
                 "        android:layout_alignParentTop=\"true\"\n" +
                 "        android:layout_alignParentLeft=\"true\"\n" +
                 "        android:layout_alignParentStart=\"true\"\n" +
                 "        android:layout_marginTop=\"100dp\"\n" +
                 "        android:layout_marginLeft=\"100dp\"\n" +
                 "        android:layout_marginStart=\"100dp\" />\n" +
                 "\n" +
                 "    <CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_below=\"@id/button\"\n" +
                 "        android:layout_toRightOf=\"@id/button\"\n" +
                 "        android:layout_marginLeft=\"100dp\"\n" +
                 "        android:layout_marginTop=\"100dp\" />\n" +
                 "\n" +
                 "    <TextView\n" +
                 "        android:id=\"@id/textView\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"100dp\"\n" +
                 "        android:layout_below=\"@id/checkbox\"\n" +
                 "        android:layout_toRightOf=\"@id/checkbox\"\n" +
                 "        android:layout_marginLeft=\"80dp\"\n" +
                 "        android:layout_marginTop=\"80dp\" />\n" +
                 "</RelativeLayout>\n", model.getFile().getText());
    return model;
  }
}
