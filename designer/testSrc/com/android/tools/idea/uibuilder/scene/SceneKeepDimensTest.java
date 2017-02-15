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
package com.android.tools.idea.uibuilder.scene;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.Density;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.SyncNlModel;
import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;

import static com.android.SdkConstants.CONSTRAINT_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;
import static org.mockito.Mockito.when;

/**
 * Test that we correctly maintains existing dimension references on margin
 */
public class SceneKeepDimensTest extends SceneTest {

  @Override
  @NotNull
  public ModelBuilder createModel() {
    ModelBuilder builder = model("constraint.xml",
                                 component(CONSTRAINT_LAYOUT)
                                   .id("@id/root")
                                   .withBounds(0, 0, 2000, 2000)
                                   .width("1000dp")
                                   .height("1000dp")
                                   .withAttribute("android:padding", "20dp")
                                   .children(
                                     component(TEXT_VIEW)
                                       .id("@+id/textView1")
                                       .withBounds(40, 400, 200, 40)
                                       .width("100dp")
                                       .height("20dp")
                                       .withAttribute("app:layout_constraintLeft_toLeftOf", "parent")
                                       .withAttribute("android:layout_marginLeft", "@dimen/testDimens")
                                       .withAttribute("tools:layout_editor_absoluteX", "20dp")
                                       .withAttribute("tools:layout_editor_absoluteY", "500dp"),
                                     component(TEXT_VIEW)
                                       .id("@+id/textView2")
                                       .withBounds(1000, 40, 200, 40)
                                       .width("100dp")
                                       .height("20dp")
                                       .withAttribute("app:layout_constraintTop_toTopOf", "parent")
                                       .withAttribute("android:layout_marginTop", "@dimen/testDimens")
                                       .withAttribute("tools:layout_editor_absoluteX", "500dp")
                                       .withAttribute("tools:layout_editor_absoluteY", "20dp"),
                                     component(TEXT_VIEW)
                                       .id("@+id/textView3")
                                       .withBounds(1760, 1000, 200, 40)
                                       .width("100dp")
                                       .height("20dp")
                                       .withAttribute("app:layout_constraintRight_toRightOf", "parent")
                                       .withAttribute("android:layout_marginRight", "@dimen/testDimens")
                                       .withAttribute("tools:layout_editor_absoluteX", "880dp")
                                       .withAttribute("tools:layout_editor_absoluteY", "500dp"),
                                     component(TEXT_VIEW)
                                       .id("@+id/textView4")
                                       .withBounds(1000, 1920, 200, 40)
                                       .width("100dp")
                                       .height("20dp")
                                       .withAttribute("app:layout_constraintBottom_toBottomOf", "parent")
                                       .withAttribute("android:layout_marginTop", "@dimen/testDimens")
                                       .withAttribute("tools:layout_editor_absoluteX", "500dp")
                                       .withAttribute("tools:layout_editor_absoluteY", "960dp")
                                   ));
    return builder;
  }

  private void setFakeResource(String dimension, String value) {
    Configuration configuration = Mockito.mock(Configuration.class);
    ResourceResolver resolver = Mockito.mock(ResourceResolver.class);
    ResourceValue resourceValue = Mockito.mock(ResourceValue.class);
    when(configuration.getResourceResolver()).thenReturn(resolver);
    when(configuration.getDensity()).thenReturn(Density.XHIGH);
    when(resolver.findResValue(dimension, false)).thenReturn(resourceValue);
    when(resolver.resolveResValue(resourceValue)).thenReturn(resourceValue);
    when(resourceValue.getValue()).thenReturn(value);
    ((SyncNlModel)myModel).setConfiguration(configuration);
  }

  public void testKeepDimensionLeft() {
    setFakeResource("@dimen/testDimens", "20dp");
    myInteraction.mouseDown("textView1");
    myInteraction.mouseRelease("textView1");
    myScreen.get("@+id/textView1")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textView1\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"\n" +
                 "    app:layout_constraintLeft_toLeftOf=\"parent\"\n" +
                 "    android:layout_marginLeft=\"@dimen/testDimens\"\n" +
                 "    tools:layout_editor_absoluteX=\"20dp\"\n" +
                 "    tools:layout_editor_absoluteY=\"500dp\"/>");

    myInteraction.mouseDown("textView1");
    myInteraction.mouseRelease("textView1", 200, 200);
    myScreen.get("@+id/textView1")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textView1\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"\n" +
                 "    app:layout_constraintLeft_toLeftOf=\"parent\"\n" +
                 "    android:layout_marginLeft=\"220dp\"\n" +
                 "      tools:layout_editor_absoluteY=\"400dp\"/>");
  }

  public void testKeepDimensionTop() {
    setFakeResource("@dimen/testDimens", "20dp");
    myInteraction.mouseDown("textView2");
    myInteraction.mouseRelease("textView2");
    myScreen.get("@+id/textView2")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textView2\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"\n" +
                 "    app:layout_constraintTop_toTopOf=\"parent\"\n" +
                 "    android:layout_marginTop=\"@dimen/testDimens\"\n" +
                 "    tools:layout_editor_absoluteX=\"500dp\"\n" +
                 "    tools:layout_editor_absoluteY=\"20dp\"/>");

    myInteraction.mouseDown("textView2");
    myInteraction.mouseRelease("textView2", 200, 200);
    myScreen.get("@+id/textView2")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textView2\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"\n" +
                 "    app:layout_constraintTop_toTopOf=\"parent\"\n" +
                 "    android:layout_marginTop=\"220dp\"\n" +
                 "    tools:layout_editor_absoluteX=\"700dp\" />");
  }

  public void testKeepDimensionRight() {
    setFakeResource("@dimen/testDimens", "20dp");
    myInteraction.mouseDown("textView3");
    myInteraction.mouseRelease("textView3");
    myScreen.get("@+id/textView3")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textView3\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"\n" +
                 "    app:layout_constraintRight_toRightOf=\"parent\"\n" +
                 "    android:layout_marginRight=\"@dimen/testDimens\"\n" +
                 "    tools:layout_editor_absoluteX=\"880dp\"\n" +
                 "    tools:layout_editor_absoluteY=\"500dp\"/>");

    myInteraction.mouseDown("textView3");
    myInteraction.mouseRelease("textView3", -200, 200);
    myScreen.get("@+id/textView3")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textView3\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"\n" +
                 "    app:layout_constraintRight_toRightOf=\"parent\"\n" +
                 "    android:layout_marginRight=\"220dp\"\n" +
                 "      tools:layout_editor_absoluteY=\"700dp\"/>");
  }

  public void testKeepDimensionBottom() {
    setFakeResource("@dimen/testDimens", "20dp");
    myInteraction.mouseDown("textView4");
    myInteraction.mouseRelease("textView4");
    myScreen.get("@+id/textView4")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textView4\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"\n" +
                 "    app:layout_constraintBottom_toBottomOf=\"parent\"\n" +
                 "    android:layout_marginTop=\"@dimen/testDimens\"\n" +
                 "    tools:layout_editor_absoluteX=\"500dp\"\n" +
                 "    tools:layout_editor_absoluteY=\"960dp\"/>");

    myInteraction.mouseDown("textView4");
    myInteraction.mouseRelease("textView4", 200, -200);
    myScreen.get("@+id/textView4")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textView4\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"\n" +
                 "    app:layout_constraintBottom_toBottomOf=\"parent\"\n" +
                 "      tools:layout_editor_absoluteX=\"700dp\"\n" +
                 "      android:layout_marginBottom=\"220dp\" />");
  }
}
