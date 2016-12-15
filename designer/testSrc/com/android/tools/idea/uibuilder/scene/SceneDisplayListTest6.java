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
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;

import static com.android.SdkConstants.CONSTRAINT_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;

/**
 * Check that connections to parent if referenced by an id still works, also check the display list sorted result.
 */
public class SceneDisplayListTest6 extends SceneTest {
  @Override
  @NotNull
  public ModelBuilder createModel() {
    return model("constraint.xml",
                 component(CONSTRAINT_LAYOUT)
                   .id("@+id/content_main")
                   .withBounds(0, 0, 1000, 1000)
                   .width("1000dp")
                   .height("1000dp")
                   .children(
                     component(TEXT_VIEW)
                       .id("@+id/textview")
                       .withBounds(450, 490, 100, 20)
                       .width("100dp")
                       .height("20dp")
                       .withAttribute("app:layout_constraintLeft_toLeftOf", "@id/content_main")
                       .withAttribute("app:layout_constraintRight_toRightOf", "@id/content_main")
                       .withAttribute("app:layout_constraintTop_toTopOf", "@+id/content_main")
                       .withAttribute("app:layout_constraintBottom_toBottomOf", "@+id/content_main")
                   ));
  }

  public void testBasicScene() {
    myScreen.get("@+id/textview")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textview\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"\n" +
                 "    app:layout_constraintLeft_toLeftOf=\"@id/content_main\"\n" +
                 "    app:layout_constraintRight_toRightOf=\"@id/content_main\"\n" +
                 "    app:layout_constraintTop_toTopOf=\"@+id/content_main\"\n" +
                 "    app:layout_constraintBottom_toBottomOf=\"@+id/content_main\"/>");

    String simpleList = "DrawComponentFrame,0,0,1000,1000,1\n" +
                        "Clip,0,0,1000,1000\n" +
                        "DrawComponentBackground,450,490,100,20,1\n" +
                        "DrawTextRegion,450,490,100,20,0,false,false,5,5,\"\"\n" +
                        "DrawComponentFrame,450,490,100,20,1\n" +
                        "DrawConnection,2,450x490x100x20,0,0x0x1000x1000,0,1,false,0,0,0.5\n" +
                        "DrawConnection,2,450x490x100x20,1,0x0x1000x1000,1,1,false,0,0,0.5\n" +
                        "DrawConnection,2,450x490x100x20,2,0x0x1000x1000,2,1,false,0,0,0.5\n" +
                        "DrawConnection,2,450x490x100x20,3,0x0x1000x1000,3,1,false,0,0,0.5\n" +
                        "UNClip\n";

    assertEquals(simpleList, myInteraction.getDisplayList().serialize());
    DisplayList disp = DisplayList.getDisplayList(simpleList);
    assertEquals(simpleList, DisplayList.getDisplayList(simpleList).serialize());
    //noinspection UndesirableClassUsage
    BufferedImage img = new BufferedImage(1000, 1000,BufferedImage.TYPE_INT_ARGB);
    disp.paint(img.createGraphics(), SceneContext.get());
    assertEquals(10, disp.getCommands().size());
    String result = disp.generateSortedDisplayList(SceneContext.get());
    String sorted = "DrawComponentFrame,0,0,1000,1000,1\n" +
                    "Clip,0,0,1000,1000\n" +
                    "DrawConnection,2,450x490x100x20,0,0x0x1000x1000,0,1,false,0,0,0.5\n" +
                    "DrawConnection,2,450x490x100x20,1,0x0x1000x1000,1,1,false,0,0,0.5\n" +
                    "DrawConnection,2,450x490x100x20,2,0x0x1000x1000,2,1,false,0,0,0.5\n" +
                    "DrawConnection,2,450x490x100x20,3,0x0x1000x1000,3,1,false,0,0,0.5\n" +
                    "DrawComponentBackground,450,490,100,20,1\n" +
                    "DrawComponentFrame,450,490,100,20,1\n" +
                    "DrawTextRegion,450,490,100,20,0,false,false,5,5,\"\"\n" +
                    "UNClip\n\n";
    assertEquals(sorted, result);
    disp.clear();
  }
}