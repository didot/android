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
package com.android.tools.idea.uibuilder.handlers.menu;

import com.android.ide.common.rendering.api.ViewType;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.fixtures.ComponentDescriptor;
import com.android.tools.idea.uibuilder.fixtures.ScreenFixture;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.scene.Scene;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.TemporarySceneComponent;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.intellij.openapi.command.WriteCommandAction;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static com.android.SdkConstants.*;

public final class GroupDragHandlerLayoutTest extends LayoutTestCase {
  public void testCommitConsecutiveOrders() {
    ComponentDescriptor menuDescriptor = component(TAG_MENU)
      .withBounds(576, 58, 192, 96)
      .children(
        item(2, 672),
        item(1, 576));

    NlModel model = model("menu.xml", menuDescriptor).build();
    NlComponent menuComponent = model.getComponents().get(0);
    NlComponent item = Mockito.mock(NlComponent.class);
    Mockito.when(item.getTagName()).thenReturn(TAG_ITEM);
    Mockito.when(item.getModel()).thenReturn(model);

    DragHandler handler = newGroupDragHandler(menuComponent, item);
    handler.update(350, 50, 0);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> handler.commit(700, 100, 0, InsertType.MOVE_INTO));

    Iterator<NlComponent> i = menuComponent.getChildren().iterator();

    assertEquals("3", i.next().getAndroidAttribute(ATTR_ORDER_IN_CATEGORY));
    assertEquals("1", i.next().getAndroidAttribute(ATTR_ORDER_IN_CATEGORY));

    Mockito.verify(item).setAndroidAttribute(ATTR_ORDER_IN_CATEGORY, "2");
  }

  public void testCommitNonconsecutiveOrders() {
    ComponentDescriptor menuDescriptor = component(TAG_MENU)
      .withBounds(480, 58, 288, 96)
      .children(
        item(30, 672),
        item(20, 576),
        item(10, 480));

    NlModel model = model("menu.xml", menuDescriptor).build();
    NlComponent menuComponent = model.getComponents().get(0);
    NlComponent item = Mockito.mock(NlComponent.class);
    Mockito.when(item.getTagName()).thenReturn(TAG_ITEM);
    Mockito.when(item.getModel()).thenReturn(model);

    DragHandler handler = newGroupDragHandler(menuComponent, item);
    handler.update(300, 50, 0);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> handler.commit(600, 100, 0, InsertType.MOVE_INTO));

    Iterator<NlComponent> i = menuComponent.getChildren().iterator();

    assertEquals("30", i.next().getAndroidAttribute(ATTR_ORDER_IN_CATEGORY));
    assertEquals("21", i.next().getAndroidAttribute(ATTR_ORDER_IN_CATEGORY));
    assertEquals("10", i.next().getAndroidAttribute(ATTR_ORDER_IN_CATEGORY));

    Mockito.verify(item).setAndroidAttribute(ATTR_ORDER_IN_CATEGORY, "20");
  }

  public void testCommitActiveItemIsSameAsDraggedItem() {
    ComponentDescriptor menuDescriptor = component(TAG_MENU)
      .withBounds(480, 58, 288, 96)
      .children(
        item(3, 672),
        item(2, 576),
        item(1, 480));

    NlComponent menuComponent = model("menu.xml", menuDescriptor).build().getComponents().get(0);

    NlComponent item = menuComponent.getChild(0);
    assert item != null;

    DragHandler handler = newGroupDragHandler(menuComponent, item);
    handler.update(740, 100, 16);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> handler.commit(740, 100, 16, InsertType.MOVE_INTO));

    Iterator<NlComponent> i = menuComponent.getChildren().iterator();

    assertEquals("3", i.next().getAndroidAttribute(ATTR_ORDER_IN_CATEGORY));
    assertEquals("2", i.next().getAndroidAttribute(ATTR_ORDER_IN_CATEGORY));
    assertEquals("1", i.next().getAndroidAttribute(ATTR_ORDER_IN_CATEGORY));
  }

  @NotNull
  private ComponentDescriptor item(int order, int x) {
    return component(TAG_ITEM)
      .withAttribute(ANDROID_URI, ATTR_ORDER_IN_CATEGORY, Integer.toString(order))
      .withAttribute(AUTO_URI, ATTR_SHOW_AS_ACTION, VALUE_ALWAYS)
      .withBounds(x, 58, 96, 96)
      .viewType(ViewType.ACTION_BAR_MENU);
  }

  @NotNull
  private DragHandler newGroupDragHandler(@NotNull NlComponent menu, @NotNull NlComponent item) {
    ScreenFixture screenFixture = surface().screen(menu.getModel()).withScale(1);
    Scene scene = Scene.createScene(menu.getModel(), screenFixture.getScreen());
    scene.buildDisplayList(new DisplayList(), 0);

    List<SceneComponent> itemAsList = Collections.singletonList(new TemporarySceneComponent(scene, item));
    ViewEditor editor = editor(screenFixture.getScreen());
    Mockito.when(editor.getModel()).thenReturn(Mockito.mock(NlModel.class));
    return new GroupDragHandler(editor, new ViewGroupHandler(), scene.getSceneComponent(menu), itemAsList,
                                DragType.MOVE);
  }
}
