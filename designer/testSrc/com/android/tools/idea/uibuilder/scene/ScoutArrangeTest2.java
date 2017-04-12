/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.tools.idea.uibuilder.scout.Scout;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.SdkConstants.CONSTRAINT_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;

/**
 * Check that connections to parent if referenced by an id still works, also check the display list sorted result.
 */
public class ScoutArrangeTest2 extends SceneTest {
  @Override
  @NotNull
  public ModelBuilder createModel() {
    return model("constraint.xml",
                 component(CONSTRAINT_LAYOUT)
                   .id("@+id/content_main")
                   .withBounds(0, 0, 2000, 2000)
                   .width("1000dp")
                   .height("1000dp")
                   .children(
                     component(TEXT_VIEW)
                       .id("@+id/textview1")
                       .withBounds(100, 750, 200, 40)
                       .width("100dp")
                       .height("40dp"),
                     component(TEXT_VIEW)
                       .id("@+id/textview2")
                       .withBounds(400, 1050, 200, 30)
                       .width("200dp")
                       .height("30dp"),
                     component(TEXT_VIEW)
                       .id("@+id/textview3")
                       .withBounds(650, 1150, 200, 50)
                       .width("200dp")
                       .height("50dp")
                   ));
  }

  public void testAlignVerticallyMiddle() {
    myScreen.get("@+id/textview2")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textview2\"\n" +
                 "    android:layout_width=\"200dp\"\n" +
                 "    android:layout_height=\"30dp\"/>");
    List<NlComponent> list = myModel.getComponents().get(0).getChildren();
    Scout.arrangeWidgets(Scout.Arrange.AlignVerticallyMiddle, list,true);
    Scout.arrangeWidgets(Scout.Arrange.CenterHorizontally, list,true);
    myScreen.get("@+id/textview2")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textview2\"\n" +
                 "    android:layout_width=\"200dp\"\n" +
                 "    android:layout_height=\"30dp\"\n" +
                 "      app:layout_constraintTop_toTopOf=\"@+id/textview1\"\n" +
                 "      tools:layout_editor_absoluteY=\"494dp\"\n" +
                 "      app:layout_constraintBottom_toBottomOf=\"@+id/textview1\"\n" +
                 "      app:layout_constraintStart_toStartOf=\"parent\"\n" +
                 "      app:layout_constraintHorizontal_bias=\"0.5\"\n" +
                 "      app:layout_constraintEnd_toEndOf=\"parent\" />");

    buildScene();
    String simpleList = "DrawNlComponentFrame,0,0,1000,1000,1,1000,1000\n" +
                        "Clip,0,0,1000,1000\n" +
                        "DrawComponentBackground,50,375,100,20,1\n" +
                        "DrawTextRegion,50,375,100,20,0,false,false,5,5,28,1.0,\"\"\n" +
                        "DrawNlComponentFrame,50,375,100,20,1,40,40\n" +
                        "DrawConnection,2,50x375x100x20,0,0x0x1000x1000,0,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,50x375x100x20,1,0x0x1000x1000,1,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawComponentBackground,200,525,100,15,1\n" +
                        "DrawTextRegion,200,525,100,15,0,false,false,5,5,28,1.0,\"\"\n" +
                        "DrawNlComponentFrame,200,525,100,15,1,30,30\n" +
                        "DrawConnection,2,200x525x100x15,0,0x0x1000x1000,0,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,200x525x100x15,1,0x0x1000x1000,1,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,6,200x525x100x15,2,50x375x100x20,2,0,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,6,200x525x100x15,3,50x375x100x20,3,0,false,0,0,false,0.5,2,0,0\n" +
                        "DrawComponentBackground,325,575,100,25,1\n" +
                        "DrawTextRegion,325,575,100,25,0,false,false,5,5,28,1.0,\"\"\n" +
                        "DrawNlComponentFrame,325,575,100,25,1,50,50\n" +
                        "DrawConnection,2,325x575x100x25,0,0x0x1000x1000,0,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,325x575x100x25,1,0x0x1000x1000,1,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,6,325x575x100x25,2,200x525x100x15,2,0,true,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,6,325x575x100x25,3,200x525x100x15,3,0,true,0,0,false,0.5,2,0,0\n" +
                        "UNClip\n";

    assertEquals(simpleList, myInteraction.getDisplayList().serialize());
  }

  public void testAlignVerticallyBottom() {
    myScreen.get("@+id/textview2")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textview2\"\n" +
                 "    android:layout_width=\"200dp\"\n" +
                 "    android:layout_height=\"30dp\"/>");
    List<NlComponent> list = myModel.getComponents().get(0).getChildren();
    System.out.println("list size"+list.size());
    Scout.arrangeWidgets(Scout.Arrange.CenterHorizontally, list,true);
   Scout.arrangeWidgets(Scout.Arrange.AlignVerticallyBottom, list,true);
    myScreen.get("@+id/textview2")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textview2\"\n" +
                 "    android:layout_width=\"200dp\"\n" +
                 "    android:layout_height=\"30dp\"\n" +
                 "      app:layout_constraintStart_toStartOf=\"parent\"\n" +
                 "      app:layout_constraintHorizontal_bias=\"0.5\"\n" +
                 "      app:layout_constraintEnd_toEndOf=\"parent\"\n" +
                 "      tools:layout_editor_absoluteY=\"525dp\"\n" +
                 "      app:layout_constraintBottom_toBottomOf=\"@+id/textview1\" />");

    buildScene();
    String simpleList = "DrawNlComponentFrame,0,0,1000,1000,1,1000,1000\n" +
                        "Clip,0,0,1000,1000\n" +
                        "DrawComponentBackground,50,375,100,20,1\n" +
                        "DrawTextRegion,50,375,100,20,0,false,false,5,5,28,1.0,\"\"\n" +
                        "DrawNlComponentFrame,50,375,100,20,1,40,40\n" +
                        "DrawConnection,2,50x375x100x20,0,0x0x1000x1000,0,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,50x375x100x20,1,0x0x1000x1000,1,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawComponentBackground,200,525,100,15,1\n" +
                        "DrawTextRegion,200,525,100,15,0,false,false,5,5,28,1.0,\"\"\n" +
                        "DrawNlComponentFrame,200,525,100,15,1,30,30\n" +
                        "DrawConnection,2,200x525x100x15,0,0x0x1000x1000,0,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,200x525x100x15,1,0x0x1000x1000,1,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,1,200x525x100x15,3,50x375x100x20,3,0,false,0,0,false,0.5,2,0,0\n" +
                        "DrawComponentBackground,325,575,100,25,1\n" +
                        "DrawTextRegion,325,575,100,25,0,false,false,5,5,28,1.0,\"\"\n" +
                        "DrawNlComponentFrame,325,575,100,25,1,50,50\n" +
                        "DrawConnection,2,325x575x100x25,0,0x0x1000x1000,0,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,325x575x100x25,1,0x0x1000x1000,1,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,1,325x575x100x25,3,200x525x100x15,3,0,true,0,0,false,0.5,2,0,0\n" +
                        "UNClip\n";

    assertEquals(simpleList, myInteraction.getDisplayList().serialize());
  }

  public void testAlignVerticallyTop() {
    myScreen.get("@+id/textview2")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textview2\"\n" +
                 "    android:layout_width=\"200dp\"\n" +
                 "    android:layout_height=\"30dp\"/>");
    List<NlComponent> list = myModel.getComponents().get(0).getChildren();
    Scout.arrangeWidgets(Scout.Arrange.AlignVerticallyTop, list,true);
    Scout.arrangeWidgets(Scout.Arrange.CenterHorizontally, list,true);
    myScreen.get("@+id/textview2")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textview2\"\n" +
                 "    android:layout_width=\"200dp\"\n" +
                 "    android:layout_height=\"30dp\"\n" +
                 "      app:layout_constraintTop_toTopOf=\"@+id/textview1\"\n" +
                 "      tools:layout_editor_absoluteY=\"525dp\"\n" +
                 "      tools:layout_editor_absoluteX=\"200dp\"\n" +
                 "      app:layout_constraintStart_toStartOf=\"parent\"\n" +
                 "      app:layout_constraintHorizontal_bias=\"0.5\"\n" +
                 "      app:layout_constraintEnd_toEndOf=\"parent\" />");

    buildScene();
    String simpleList = "DrawNlComponentFrame,0,0,1000,1000,1,1000,1000\n" +
                        "Clip,0,0,1000,1000\n" +
                        "DrawComponentBackground,50,375,100,20,1\n" +
                        "DrawTextRegion,50,375,100,20,0,false,false,5,5,28,1.0,\"\"\n" +
                        "DrawNlComponentFrame,50,375,100,20,1,40,40\n" +
                        "DrawConnection,2,50x375x100x20,0,0x0x1000x1000,0,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,50x375x100x20,1,0x0x1000x1000,1,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawComponentBackground,200,525,100,15,1\n" +
                        "DrawTextRegion,200,525,100,15,0,false,false,5,5,28,1.0,\"\"\n" +
                        "DrawNlComponentFrame,200,525,100,15,1,30,30\n" +
                        "DrawConnection,2,200x525x100x15,0,0x0x1000x1000,0,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,200x525x100x15,1,0x0x1000x1000,1,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,1,200x525x100x15,2,50x375x100x20,2,0,false,0,0,false,0.5,2,0,0\n" +
                        "DrawComponentBackground,325,575,100,25,1\n" +
                        "DrawTextRegion,325,575,100,25,0,false,false,5,5,28,1.0,\"\"\n" +
                        "DrawNlComponentFrame,325,575,100,25,1,50,50\n" +
                        "DrawConnection,2,325x575x100x25,0,0x0x1000x1000,0,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,325x575x100x25,1,0x0x1000x1000,1,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,1,325x575x100x25,2,200x525x100x15,2,0,true,0,0,false,0.5,2,0,0\n" +
                        "UNClip\n";

    assertEquals(simpleList, myInteraction.getDisplayList().serialize());
  }

  public void testAlignHorizontallyCenter() {
    myScreen.get("@+id/textview2")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textview2\"\n" +
                 "    android:layout_width=\"200dp\"\n" +
                 "    android:layout_height=\"30dp\"/>");
    List<NlComponent> list = myModel.getComponents().get(0).getChildren();
    Scout.arrangeWidgets(Scout.Arrange.AlignHorizontallyCenter, list,true);
    Scout.arrangeWidgets(Scout.Arrange.CenterVertically, list,true);
    myScreen.get("@+id/textview2")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textview2\"\n" +
                 "    android:layout_width=\"200dp\"\n" +
                 "    android:layout_height=\"30dp\"\n" +
                 "      tools:layout_editor_absoluteX=\"191dp\"\n" +
                 "      app:layout_constraintStart_toStartOf=\"@+id/textview1\"\n" +
                 "      app:layout_constraintEnd_toEndOf=\"@+id/textview1\"\n" +
                 "      app:layout_constraintTop_toTopOf=\"parent\"\n" +
                 "      app:layout_constraintBottom_toBottomOf=\"parent\"\n" +
                 "      app:layout_constraintHorizontal_bias=\"0.5\" />");

    buildScene();
    String simpleList = "DrawNlComponentFrame,0,0,1000,1000,1,1000,1000\n" +
                        "Clip,0,0,1000,1000\n" +
                        "DrawComponentBackground,50,375,100,20,1\n" +
                        "DrawTextRegion,50,375,100,20,0,false,false,5,5,28,1.0,\"\"\n" +
                        "DrawNlComponentFrame,50,375,100,20,1,40,40\n" +
                        "DrawConnection,2,50x375x100x20,2,0x0x1000x1000,2,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,50x375x100x20,3,0x0x1000x1000,3,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawComponentBackground,200,525,100,15,1\n" +
                        "DrawTextRegion,200,525,100,15,0,false,false,5,5,28,1.0,\"\"\n" +
                        "DrawNlComponentFrame,200,525,100,15,1,30,30\n" +
                        "DrawConnection,6,200x525x100x15,0,50x375x100x20,0,0,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,6,200x525x100x15,1,50x375x100x20,1,0,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,200x525x100x15,2,0x0x1000x1000,2,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,200x525x100x15,3,0x0x1000x1000,3,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawComponentBackground,325,575,100,25,1\n" +
                        "DrawTextRegion,325,575,100,25,0,false,false,5,5,28,1.0,\"\"\n" +
                        "DrawNlComponentFrame,325,575,100,25,1,50,50\n" +
                        "DrawConnection,6,325x575x100x25,0,200x525x100x15,0,0,true,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,6,325x575x100x25,1,200x525x100x15,1,0,true,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,325x575x100x25,2,0x0x1000x1000,2,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,325x575x100x25,3,0x0x1000x1000,3,1,false,0,0,false,0.5,2,0,0\n" +
                        "UNClip\n";

    assertEquals(simpleList, myInteraction.getDisplayList().serialize());
  }

  public void testAlignHorizontallyLeft() {
    myScreen.get("@+id/textview2")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textview2\"\n" +
                 "    android:layout_width=\"200dp\"\n" +
                 "    android:layout_height=\"30dp\"/>");
    List<NlComponent> list = myModel.getComponents().get(0).getChildren();
    Scout.arrangeWidgets(Scout.Arrange.AlignHorizontallyLeft, list,true);
    Scout.arrangeWidgets(Scout.Arrange.CenterVertically, list,true);
    myScreen.get("@+id/textview2")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textview2\"\n" +
                 "    android:layout_width=\"200dp\"\n" +
                 "    android:layout_height=\"30dp\"\n" +
                 "      tools:layout_editor_absoluteY=\"525dp\"\n" +
                 "      tools:layout_editor_absoluteX=\"200dp\"\n" +
                 "      app:layout_constraintStart_toStartOf=\"@+id/textview1\"\n" +
                 "      app:layout_constraintTop_toTopOf=\"parent\"\n" +
                 "      app:layout_constraintBottom_toBottomOf=\"parent\"\n" +
                 "      app:layout_constraintHorizontal_bias=\"0.5\" />");

    buildScene();
    String simpleList = "DrawNlComponentFrame,0,0,1000,1000,1,1000,1000\n" +
                        "Clip,0,0,1000,1000\n" +
                        "DrawComponentBackground,50,375,100,20,1\n" +
                        "DrawTextRegion,50,375,100,20,0,false,false,5,5,28,1.0,\"\"\n" +
                        "DrawNlComponentFrame,50,375,100,20,1,40,40\n" +
                        "DrawConnection,2,50x375x100x20,2,0x0x1000x1000,2,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,50x375x100x20,3,0x0x1000x1000,3,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawComponentBackground,200,525,100,15,1\n" +
                        "DrawTextRegion,200,525,100,15,0,false,false,5,5,28,1.0,\"\"\n" +
                        "DrawNlComponentFrame,200,525,100,15,1,30,30\n" +
                        "DrawConnection,1,200x525x100x15,0,50x375x100x20,0,0,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,200x525x100x15,2,0x0x1000x1000,2,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,200x525x100x15,3,0x0x1000x1000,3,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawComponentBackground,325,575,100,25,1\n" +
                        "DrawTextRegion,325,575,100,25,0,false,false,5,5,28,1.0,\"\"\n" +
                        "DrawNlComponentFrame,325,575,100,25,1,50,50\n" +
                        "DrawConnection,1,325x575x100x25,0,200x525x100x15,0,0,true,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,325x575x100x25,2,0x0x1000x1000,2,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,325x575x100x25,3,0x0x1000x1000,3,1,false,0,0,false,0.5,2,0,0\n" +
                        "UNClip\n";

    assertEquals(simpleList, myInteraction.getDisplayList().serialize());
  }

  public void testAlignHorizontallyRight() {
    myScreen.get("@+id/textview2")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textview2\"\n" +
                 "    android:layout_width=\"200dp\"\n" +
                 "    android:layout_height=\"30dp\"/>");
    List<NlComponent> list = myModel.getComponents().get(0).getChildren();
    Scout.arrangeWidgets(Scout.Arrange.AlignHorizontallyRight, list,true);
    Scout.arrangeWidgets(Scout.Arrange.CenterVertically, list,true);
    myScreen.get("@+id/textview2")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textview2\"\n" +
                 "    android:layout_width=\"200dp\"\n" +
                 "    android:layout_height=\"30dp\"\n" +
                 "      tools:layout_editor_absoluteY=\"525dp\"\n" +
                 "      tools:layout_editor_absoluteX=\"200dp\"\n" +
                 "      app:layout_constraintEnd_toEndOf=\"@+id/textview1\"\n" +
                 "      app:layout_constraintTop_toTopOf=\"parent\"\n" +
                 "      app:layout_constraintBottom_toBottomOf=\"parent\"\n" +
                 "      app:layout_constraintHorizontal_bias=\"0.5\" />");

    buildScene();
    String simpleList = "DrawNlComponentFrame,0,0,1000,1000,1,1000,1000\n" +
                        "Clip,0,0,1000,1000\n" +
                        "DrawComponentBackground,50,375,100,20,1\n" +
                        "DrawTextRegion,50,375,100,20,0,false,false,5,5,28,1.0,\"\"\n" +
                        "DrawNlComponentFrame,50,375,100,20,1,40,40\n" +
                        "DrawConnection,2,50x375x100x20,2,0x0x1000x1000,2,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,50x375x100x20,3,0x0x1000x1000,3,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawComponentBackground,200,525,100,15,1\n" +
                        "DrawTextRegion,200,525,100,15,0,false,false,5,5,28,1.0,\"\"\n" +
                        "DrawNlComponentFrame,200,525,100,15,1,30,30\n" +
                        "DrawConnection,1,200x525x100x15,1,50x375x100x20,1,0,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,200x525x100x15,2,0x0x1000x1000,2,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,200x525x100x15,3,0x0x1000x1000,3,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawComponentBackground,325,575,100,25,1\n" +
                        "DrawTextRegion,325,575,100,25,0,false,false,5,5,28,1.0,\"\"\n" +
                        "DrawNlComponentFrame,325,575,100,25,1,50,50\n" +
                        "DrawConnection,1,325x575x100x25,1,200x525x100x15,1,0,true,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,325x575x100x25,2,0x0x1000x1000,2,1,false,0,0,false,0.5,2,0,0\n" +
                        "DrawConnection,2,325x575x100x25,3,0x0x1000x1000,3,1,false,0,0,false,0.5,2,0,0\n" +
                        "UNClip\n";

    assertEquals(simpleList, myInteraction.getDisplayList().serialize());
  }
}