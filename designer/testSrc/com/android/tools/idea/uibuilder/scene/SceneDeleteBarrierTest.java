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

import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.intellij.openapi.command.WriteCommandAction;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;

import static com.android.SdkConstants.*;

/**
 * Test delete all constraints
 */
public class SceneDeleteBarrierTest extends SceneTest {

  public void testDelete() {
    SceneComponent layout = myScene.getSceneComponent("root");
    NlComponent layoutComponent = layout.getNlComponent();
    NlComponent toDelete = myScene.getSceneComponent("button").getNlComponent();
    layoutComponent.getModel().delete(Arrays.asList(toDelete));
    myScreen.get("@id/root")
      .expectXml("<android.support.constraint.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "                                            xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                 "  android:id=\"@id/root\"\n" +
                 "  android:layout_width=\"1000dp\"\n" +
                 "  android:layout_height=\"1000dp\"\n" +
                 "  android:padding=\"20dp\">\n" +
                 "\n" +
                 "    <android.support.constraint.ConstraintHelper\n" +
                 "    android:id=\"@id/barrier\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\" />\n" +
                 "\n" +
                 "</android.support.constraint.ConstraintLayout>");
  }

  @Override
  @NotNull
  public ModelBuilder createModel() {
    return model("constraint.xml",
                 component(CONSTRAINT_LAYOUT)
                   .id("@id/root")
                   .withBounds(0, 0, 2000, 2000)
                   .width("1000dp")
                   .height("1000dp")
                   .withAttribute("android:padding", "20dp")
                   .children(
                     component(TEXT_VIEW)
                       .id("@id/button")
                       .withBounds(900, 980, 200, 40)
                       .width("100dp")
                       .height("20dp")
                       .withAttribute("app:layout_constraintLeft_toLeftOf", "parent")
                       .withAttribute("app:layout_constraintRight_toRightOf", "parent")
                       .withAttribute("app:layout_constraintTop_toTopOf", "parent")
                       .withAttribute("app:layout_constraintBottom_toBottomOf", "parent"),
                     component(CLASS_CONSTRAINT_LAYOUT_HELPER)
                       .id("@id/barrier")
                       .withBounds(900, 1052, 200, 40)
                       .width("100dp")
                       .height("20dp")
                       .withAttribute("app:constraint_referenced_ids", "button")
                     )
                   );
  }
}
