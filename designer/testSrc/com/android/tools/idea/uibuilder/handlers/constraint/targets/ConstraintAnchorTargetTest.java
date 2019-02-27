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
package com.android.tools.idea.uibuilder.handlers.constraint.targets;

import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawCommand;
import com.android.tools.idea.common.scene.target.AnchorTarget;
import com.android.tools.idea.uibuilder.scene.SceneTest;
import java.util.Optional;

import static com.android.SdkConstants.BUTTON;
import static com.android.SdkConstants.CONSTRAINT_LAYOUT;

public class ConstraintAnchorTargetTest extends SceneTest {

  public void testCenteringComponentWithSibling() {
    myInteraction.select("button2", true);
    myInteraction.mouseDown("button2", AnchorTarget.Type.LEFT);
    myInteraction.mouseRelease("button1", AnchorTarget.Type.LEFT);
    myInteraction.mouseDown("button2", AnchorTarget.Type.RIGHT);
    myInteraction.mouseRelease("button1", AnchorTarget.Type.RIGHT);
    myScreen.get("@id/button2").expectXml("<Button\n" +
                                          "        android:id=\"@id/button2\"\n" +
                                          "        app:layout_constraintEnd_toEndOf=\"@+id/button1\"\n" +
                                          "        app:layout_constraintStart_toStartOf=\"@+id/button1\"\n" +
                                          "        tools:layout_editor_absoluteY=\"15dp\" />");
  }

  public void testRenderDuringDragging() {
    myInteraction.select("button2", true);
    myInteraction.mouseDown("button2", AnchorTarget.Type.LEFT);
    myInteraction.mouseDrag(100, 100);

    // The connection between mouse and AnchorTarget should be inside DisplayList.
    Optional<DrawCommand> command =
      myInteraction.getDisplayList().getCommands().stream().filter(it -> it instanceof DisplayList.Connection).findAny();
    assertTrue(command.isPresent());
  }

  public void testCancelAnchorWhenCreating() {
    myInteraction.select("button2", true);
    myInteraction.mouseDown("button2", AnchorTarget.Type.LEFT);
    myInteraction.mouseCancel(500, 500);

    myScreen.get("@id/button2").expectXml("<Button\n" +
                                          "    android:id=\"@id/button2\"/>");
  }

  public void testBaselineTargetPosition() {
    SceneComponent button2 = myScene.getSceneComponent("button2");
    AnchorTarget baselineTarget = AnchorTarget.findAnchorTarget(button2, AnchorTarget.Type.BASELINE);

    // Unit of SceneComponent is Integer and unit of Target is double. They may have up to 0.5d differences.
    assertEquals(button2.getCenterX(), baselineTarget.getCenterX(), 0.5);
    assertEquals(button2.getDrawY() + button2.getBaseline(), baselineTarget.getCenterY(), 0.5);
  }

  @Override
  public ModelBuilder createModel() {
    return model("model.xml", component(CONSTRAINT_LAYOUT.defaultName())
      .id("@+id/root")
      .withBounds(0, 0, 1000, 1000)
      .children(component(BUTTON)
                  .id("@+id/button1")
                  .withBounds(10, 10, 10, 10),
                component(BUTTON)
                  .id("@id/button2")
                  .withBounds(10, 30, 10, 10)));
  }
}