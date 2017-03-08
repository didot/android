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
import com.android.tools.idea.uibuilder.api.DragType;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.fixtures.ComponentDescriptor;
import com.android.tools.idea.uibuilder.fixtures.ScreenFixture;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.AndroidDpCoordinate;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneBuilder;
import com.android.tools.idea.uibuilder.scene.Scene;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.mock;

public final class GroupDragHandlerTest extends LayoutTestCase {

  public void testUpdateUsingActionBarGroup() {
    NlModel model = model("model.xml", menu(572, 58, 196, 96)
      .children(
        menuItem(572, 58, 98, 96),
        group(670, 58, 98, 96).children(
          menuItem(670, 58, 98, 96))
      )).build();

    GroupDragHandler handler = getMenuHandler(model);
    handler.update(397, 0, 0);

    assertEquals(-1, handler.getInsertIndex());
  }

  public void testUpdateUsingOverflowGroup() {
    NlModel model = model("model.xml", menu(366, 162, 392, 288)
      .children(
        overflowItem(366, 162, 392, 96),
        overflowItem(366, 258, 392, 96),
        overflowItem(366, 354, 392, 96))).build();

    GroupDragHandler handler = getMenuHandler(model);
    @AndroidDpCoordinate int y = 45;

    y += 24;
    handler.update(0, y, 0);
    assertEquals(0, handler.getInsertIndex());

    y += 24;
    handler.update(0, y, 0);
    assertEquals(0, handler.getInsertIndex());

    y += 24;
    handler.update(0, y, 0);
    assertEquals(1, handler.getInsertIndex());

    y += 24;
    handler.update(0, y, 0);
    assertEquals(1, handler.getInsertIndex());

    y += 24;
    handler.update(0, y, 0);
    assertEquals(2, handler.getInsertIndex());

    y += 24;
    handler.update(0, y, 0);
    assertEquals(2, handler.getInsertIndex());

    y += 24;
    handler.update(0, y, 0);
    assertEquals(-1, handler.getInsertIndex());

    y += 24;
    handler.update(0, y, 0);
    assertEquals(-1, handler.getInsertIndex());
  }

  public void testUpdateUsingOverflowGroupEmptyGroupInFront() {
    NlModel model = model("model.xml", menu(366, 162, 392, 192)
      .children(
        group(368, 164, 1, 1).id("@+id/group"),
        overflowItem(366, 162, 392, 96),
        overflowItem(366, 258, 392, 96))).build();

    NlComponent group = model.find("group");
    group.setBounds(0, 0, -1, -1);
    GroupDragHandler handler = getMenuHandler(model);

    @AndroidDpCoordinate int y = 45;

    y += 24;
    handler.update(0, y, 0);
    assertEquals(0, handler.getInsertIndex());

    y += 24;
    handler.update(0, y, 0);
    assertEquals(1, handler.getInsertIndex());

    y += 24;
    handler.update(0, y, 0);
    assertEquals(2, handler.getInsertIndex());

    y += 24;
    handler.update(0, y, 0);
    assertEquals(2, handler.getInsertIndex());

    y += 24;
    handler.update(0, y, 0);
    assertEquals(-1, handler.getInsertIndex());

    y += 24;
    handler.update(0, y, 0);
    assertEquals(-1, handler.getInsertIndex());
  }

  public void testUpdateUsingOverflowGroupEmptyGroupInMiddle() {
    NlModel model = model("model.xml", menu(366, 162, 392, 192)
      .children(
        overflowItem(366, 162, 392, 96),
        group(368, 164, 1, 1).id("@+id/group"),
        overflowItem(366, 258, 392, 96))).build();

    NlComponent group = model.flattenComponents().filter(c -> "group".equals(c.getId())).findFirst().get();
    group.setBounds(0, 0, -1, -1);
    GroupDragHandler handler = getMenuHandler(model);

    @AndroidDpCoordinate int y = 45;

    y += 24;
    handler.update(0, y, 0);
    assertEquals(0, handler.getInsertIndex());

    y += 24;
    handler.update(0, y, 0);
    assertEquals(0, handler.getInsertIndex());

    y += 24;
    handler.update(0, y, 0);
    assertEquals(1, handler.getInsertIndex());

    y += 24;
    handler.update(0, y, 0);
    assertEquals(2, handler.getInsertIndex());

    y += 24;
    handler.update(0, y, 0);
    assertEquals(-1, handler.getInsertIndex());

    y += 24;
    handler.update(0, y, 0);
    assertEquals(-1, handler.getInsertIndex());
  }

  public void testUpdateUsingOverflowGroupEmptyGroupInBack() {
    NlModel model = model("model.xml", menu(366, 162, 392, 192)
      .children(
        overflowItem(366, 162, 392, 96),
        overflowItem(366, 258, 392, 96),
        group(368, 164, 1, 1).id("@+id/group"))).build();

    NlComponent group = model.flattenComponents().filter(c -> "group".equals(c.getId())).findFirst().get();
    group.setBounds(0, 0, -1, -1);
    GroupDragHandler handler = getMenuHandler(model);

    @AndroidDpCoordinate int y = 45;

    y += 24;
    handler.update(0, y, 0);
    assertEquals(0, handler.getInsertIndex());

    y += 24;
    handler.update(0, y, 0);
    assertEquals(0, handler.getInsertIndex());

    y += 24;
    handler.update(0, y, 0);
    assertEquals(1, handler.getInsertIndex());

    y += 24;
    handler.update(0, y, 0);
    assertEquals(1, handler.getInsertIndex());

    y += 24;
    handler.update(0, y, 0);
    assertEquals(2, handler.getInsertIndex());

    y += 24;
    handler.update(0, y, 0);
    assertEquals(-1, handler.getInsertIndex());
  }

  private ComponentDescriptor group(@AndroidCoordinate int x,
                                    @AndroidCoordinate int y,
                                    @AndroidCoordinate int width,
                                    @AndroidCoordinate int height) {
    return component("group").withBounds(x, y, width, height);
  }

  private ComponentDescriptor menuItem(@AndroidCoordinate int x,
                                       @AndroidCoordinate int y,
                                       @AndroidCoordinate int width,
                                       @AndroidCoordinate int height) {
    return component("item").withBounds(x, y, width, height).viewType(ViewType.ACTION_BAR_MENU);
  }

  private ComponentDescriptor overflowItem(@AndroidCoordinate int x,
                                           @AndroidCoordinate int y,
                                           @AndroidCoordinate int width,
                                           @AndroidCoordinate int height) {
    return component("item").withBounds(x, y, width, height).viewType(ViewType.ACTION_BAR_OVERFLOW_MENU);
  }

  private ComponentDescriptor menu(@AndroidCoordinate int x,
                                   @AndroidCoordinate int y,
                                   @AndroidCoordinate int width,
                                   @AndroidCoordinate int height) {
    return component("menu")
      .withBounds(x, y, width, height)
      .id("@+id/menu");
  }

  @NotNull
  private GroupDragHandler getMenuHandler(NlModel model) {
    ScreenFixture screenFixture = surface().screen(model).withScale(1);
    Scene scene = new LayoutlibSceneBuilder(model, screenFixture.getScreen()).build();
    scene.buildDisplayList(new DisplayList(), 0);

    List<SceneComponent> items = Collections.singletonList(mock(SceneComponent.class));
    return new GroupDragHandler(editor(screenFixture.getScreen()), new ViewGroupHandler(), scene.getSceneComponent("menu"), items,
                                DragType.CREATE);
  }
}
