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

import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.fixtures.ComponentDescriptor;
import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.SdkConstants.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Basic tests for creating and updating a Scene out of a NlModel
 */
public class SceneCreationTest extends SceneTest {

  public void testBasicScene() {
    myScreen.get("@id/button")
      .expectXml("<TextView\n" +
                 "    android:id=\"@id/button\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"\n" +
                 "    tools:layout_editor_absoluteX=\"100dp\"\n" +
                 "    tools:layout_editor_absoluteY=\"200dp\"/>");

    assertTrue(myInteraction.getDisplayList().getCommands().size() > 0);
    SceneComponent component = myScene.getSceneComponent("button");
    assertEquals(component.getScene(), myScene);
    NlComponent nlComponent = component.getNlComponent();
    assertEquals(component, myScene.getSceneComponent(nlComponent));
  }

  public void testSceneCreation() {
    ModelBuilder builder = createModel();
    NlModel model = builder.build();
    LayoutlibSceneBuilder sceneBuilder = new LayoutlibSceneBuilder(model, myScreen.getScreen());
    Scene scene = sceneBuilder.build();
    scene.setAnimate(false);
    assertEquals(scene.getRoot().getChildren().size(), 1);
    ComponentDescriptor parent = builder.findByPath(CONSTRAINT_LAYOUT);
    ComponentDescriptor textView = builder.findByPath(CONSTRAINT_LAYOUT, TEXT_VIEW);
    ComponentDescriptor editText = parent.addChild(component(EDIT_TEXT)
                                                     .withBounds(220, 440, 400, 60)
                                                     .width("200dp")
                                                     .height("30dp"), textView);
    builder.updateModel(model, false);
    sceneBuilder.update();
    assertEquals(2, scene.getRoot().getChildren().size());
    List<SceneComponent> children = scene.getRoot().getChildren();
    SceneComponent sceneTextView = children.get(0);
    assertEquals(100, sceneTextView.getDrawX());
    assertEquals(200, sceneTextView.getDrawY());
    assertEquals(100, sceneTextView.getDrawWidth());
    assertEquals(20, sceneTextView.getDrawHeight());
    SceneComponent sceneEditText = children.get(1);
    assertEquals(110, sceneEditText.getDrawX());
    assertEquals(220, sceneEditText.getDrawY());
    assertEquals(200, sceneEditText.getDrawWidth());
    assertEquals(30, sceneEditText.getDrawHeight());
    parent.removeChild(textView);
    builder.updateModel(model, false);
    sceneBuilder.update();
    assertEquals(1, scene.getRoot().getChildren().size());
    sceneTextView = scene.getRoot().getChildren().get(0);
    assertEquals(110, sceneTextView.getDrawX());
    assertEquals(220, sceneTextView.getDrawY());
    assertEquals(200, sceneTextView.getDrawWidth());
    assertEquals(30, sceneTextView.getDrawHeight());
  }

  public void testSceneReparenting() {
    ModelBuilder builder = createModel();
    NlModel model = builder.build();
    LayoutlibSceneBuilder sceneBuilder = new LayoutlibSceneBuilder(model, myScreen.getScreen());
    Scene scene = sceneBuilder.build();
    scene.setAnimate(false);
    assertEquals(scene.getRoot().getChildren().size(), 1);
    ComponentDescriptor parent = builder.findByPath(CONSTRAINT_LAYOUT);
    parent.addChild(component(CONSTRAINT_LAYOUT)
                      .id("@id/layout")
                      .withBounds(200, 300, 200, 200)
                      .width("200dp")
                      .height("200dp"), null);
    builder.updateModel(model, false);
    sceneBuilder.update();
    assertEquals(2, scene.getRoot().getChildren().size());

    NlComponent textView = scene.getRoot().getChild(0).getNlComponent();
    NlComponent container = scene.getRoot().getChild(1).getNlComponent();
    scene.getRoot().getNlComponent().removeChild(textView);
    container.addChild(textView);
    sceneBuilder.update();
    assertEquals(1, scene.getRoot().getChildCount());
    SceneComponent layout = scene.getSceneComponent("layout");
    assertEquals(1, layout.getChildCount());
  }

  public static int pxToDp(@AndroidCoordinate int px, float dpiFactor) {
    return (int)(0.5f + px / dpiFactor);
  }

  public void testDeviceChange() {
    ModelBuilder builder = createModel();
    NlModel model = builder.build();
    Configuration config = model.getConfiguration();
    config.setDevice(config.getConfigurationManager().getDeviceById("Nexus 6P"), false);

    Scene scene = new LayoutlibSceneBuilder(model, myScreen.getScreen()).build();
    scene.setAnimate(false);

    ComponentDescriptor parent = builder.findByPath(CONSTRAINT_LAYOUT);
    ComponentDescriptor textView = builder.findByPath(CONSTRAINT_LAYOUT, TEXT_VIEW);
    SceneComponent sceneTextView = scene.getRoot().getChildren().get(0);

    float dpiFactor =  560 / 160f;
    assertEquals(pxToDp(200, dpiFactor), sceneTextView.getDrawX());
    assertEquals(pxToDp(400, dpiFactor), sceneTextView.getDrawY());
    assertEquals(pxToDp(200, dpiFactor), sceneTextView.getDrawWidth());
    assertEquals(pxToDp(40, dpiFactor), sceneTextView.getDrawHeight());

    config.setDevice(config.getConfigurationManager().getDeviceById("Nexus S"), false);
    dpiFactor = 240 / 160f;

    // Allow 1dp difference for rounding
    assertEquals(pxToDp(200, dpiFactor), sceneTextView.getDrawX(), 1);
    assertEquals(pxToDp(400, dpiFactor), sceneTextView.getDrawY(), 1);
    assertEquals(pxToDp(200, dpiFactor), sceneTextView.getDrawWidth(), 1);
    assertEquals(pxToDp(40, dpiFactor), sceneTextView.getDrawHeight(), 1);
  }


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
                                       .id("@id/button")
                                       .withBounds(200, 400, 200, 40)
                                       .width("100dp")
                                       .height("20dp")
                                       .withAttribute("tools:layout_editor_absoluteX", "100dp")
                                       .withAttribute("tools:layout_editor_absoluteY", "200dp")
                                   ));
    return builder;
  }
}