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

import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.scene.target.ActionTarget;
import com.android.tools.idea.uibuilder.scene.target.AnchorTarget;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.CONSTRAINT_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;

/**
 * Test basic connection interactions
 */
public class SceneBasicConnectionsTest extends SceneTest {

  public void testConnectLeft() {
    myInteraction.select("button", true);
    myInteraction.mouseDown("button", AnchorTarget.Type.LEFT);
    myInteraction.mouseRelease("root", AnchorTarget.Type.LEFT);
    myScreen.get("@id/button")
      .expectXml("<TextView\n" +
                 "    android:id=\"@id/button\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"\n" +
                 "      tools:layout_editor_absoluteY=\"200dp\"\n" +
                 "      android:layout_marginLeft=\"8dp\"\n" +
                 "      app:layout_constraintLeft_toLeftOf=\"parent\" />");
  }

  public void testConnectTop() {
    myInteraction.select("button", true);
    NlComponent button = myScene.getSceneComponent("button").getNlComponent();
    assertEquals(1, myScreen.getScreen().getSelectionModel().getSelection().size());
    assertEquals(button, myScreen.getScreen().getSelectionModel().getPrimary());
    myInteraction.mouseDown("button", AnchorTarget.Type.TOP);
    myInteraction.mouseRelease("root", AnchorTarget.Type.TOP);
    myScreen.get("@id/button")
      .expectXml("<TextView\n" +
                 "    android:id=\"@id/button\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"\n" +
                 "    tools:layout_editor_absoluteX=\"100dp\"\n" +
                 "      app:layout_constraintTop_toTopOf=\"parent\"\n" +
                 "      android:layout_marginTop=\"8dp\" />");
    assertEquals(button, myScreen.getScreen().getSelectionModel().getPrimary());
    assertEquals(1, myScreen.getScreen().getSelectionModel().getSelection().size());
  }

  public void testConnectRight() {
    myInteraction.select("button", true);
    myInteraction.mouseDown("button", AnchorTarget.Type.RIGHT);
    myInteraction.mouseRelease("root", AnchorTarget.Type.RIGHT);
    myScreen.get("@id/button")
      .expectXml("<TextView\n" +
                 "    android:id=\"@id/button\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"\n" +
                 "      tools:layout_editor_absoluteY=\"200dp\"\n" +
                 "      android:layout_marginRight=\"8dp\"\n" +
                 "      app:layout_constraintRight_toRightOf=\"parent\" />");
  }

  public void testConnectBottom() {
    myInteraction.select("button", true);
    myInteraction.mouseDown("button", AnchorTarget.Type.BOTTOM);
    myInteraction.mouseRelease("root", AnchorTarget.Type.BOTTOM);
    myScreen.get("@id/button")
      .expectXml("<TextView\n" +
                 "    android:id=\"@id/button\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"\n" +
                 "    tools:layout_editor_absoluteX=\"100dp\"\n" +
                 "      app:layout_constraintBottom_toBottomOf=\"parent\"\n" +
                 "      android:layout_marginBottom=\"8dp\" />");
  }

  public void testConnectBaseline() {
    myInteraction.select("button2", true);
    myInteraction.mouseDown("button2", ActionTarget.class, 1);
    myInteraction.mouseRelease("button2", ActionTarget.class, 1);
    myInteraction.mouseDown("button2", AnchorTarget.Type.BASELINE);
    myInteraction.mouseRelease("button", AnchorTarget.Type.BASELINE);
    myScreen.get("@id/button2")
      .expectXml("<TextView\n" +
                 "    android:id=\"@id/button2\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"\n" +
                 "    tools:layout_editor_absoluteX=\"300dp\"\n" +
                 "      app:layout_constraintBaseline_toBaselineOf=\"@+id/button\" />");
    myInteraction.select("button2", false);
    myInteraction.select("button2", true);
    myInteraction.mouseDown("button2", AnchorTarget.Type.LEFT);
    myInteraction.mouseRelease("button", AnchorTarget.Type.RIGHT);
    myScreen.get("@id/button2")
      .expectXml("<TextView\n" +
                 "    android:id=\"@id/button2\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"\n" +
                 "      app:layout_constraintBaseline_toBaselineOf=\"@+id/button\"\n" +
                 "      app:layout_constraintLeft_toRightOf=\"@+id/button\"\n" +
                 "      android:layout_marginLeft=\"8dp\" />");
  }

  @Override
  @NotNull
  public ModelBuilder createModel() {
    ModelBuilder builder = model("constraint.xml",
                                 component(CONSTRAINT_LAYOUT)
                                   .id("@id/root")
                                   .withBounds(0, 0, 1000, 1000)
                                   .width("1000dp")
                                   .height("1000dp")
                                   .withAttribute("android:padding", "20dp")
                                   .children(
                                     component(TEXT_VIEW)
                                       .id("@id/button")
                                       .withBounds(100, 200, 100, 20)
                                       .width("100dp")
                                       .height("20dp")
                                       .withAttribute("tools:layout_editor_absoluteX", "100dp")
                                       .withAttribute("tools:layout_editor_absoluteY", "200dp"),
                                     component(TEXT_VIEW)
                                       .id("@id/button2")
                                       .withBounds(300, 200, 100, 20)
                                       .width("100dp")
                                       .height("20dp")
                                       .withAttribute("tools:layout_editor_absoluteX", "300dp")
                                       .withAttribute("tools:layout_editor_absoluteY", "200dp")
                                   ));
    return builder;
  }
}
