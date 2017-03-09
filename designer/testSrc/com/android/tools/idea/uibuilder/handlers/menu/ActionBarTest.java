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
import com.android.tools.idea.uibuilder.SyncLayoutlibSceneManager;
import com.android.tools.idea.uibuilder.fixtures.ScreenFixture;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.scene.SceneComponent;

import java.util.Collections;

public final class ActionBarTest extends LayoutTestCase {
  public void testAddToItemsOrOverflowItemsItemWidthAndHeightAreNegativeOne() {
    NlModel model = model("model.xml", component("menu").unboundedChildren(component("item").viewType(ViewType.ACTION_BAR_MENU))).build();
    ScreenFixture screen = surface().screen(model);

    SceneComponent menu = new SyncLayoutlibSceneManager(model, screen.getScreen()).build().getRoot();
    SceneComponent item = menu.getChildren().get(0);
    item.setPosition(0, 0);
    item.setSize(-1, -1, false);

    ActionBar actionBar = new ActionBar(menu);

    assertEquals(Collections.emptyList(), actionBar.getItems());
    assertEquals(Collections.emptyList(), actionBar.getOverflowItems());
  }

  public void testAddToItemsOrOverflowItemsItemIsGroup() {
    NlModel model = model("model.xml",
                          component("menu").unboundedChildren(
                            component("group").unboundedChildren(
                              component("item").viewType(ViewType.ACTION_BAR_MENU)))).build();

    ScreenFixture screen = surface().screen(model);

    SceneComponent menu = new SyncLayoutlibSceneManager(model, screen.getScreen()).build().getRoot();
    SceneComponent group = menu.getChildren().get(0);
    group.getNlComponent().viewInfo = null;
    SceneComponent item = group.getChildren().get(0);

    ActionBar actionBar = new ActionBar(menu);

    assertEquals(Collections.singletonList(item), actionBar.getItems());
    assertEquals(Collections.emptyList(), actionBar.getOverflowItems());
  }

  public void testAddToItemsOrOverflowItemsItemViewTypeIsActionBarMenu() {
    NlModel model = model("model.xml", component("menu").unboundedChildren(component("item").viewType(ViewType.ACTION_BAR_MENU))).build();
    ScreenFixture screen = surface().screen(model);

    SceneComponent menu = new SyncLayoutlibSceneManager(model, screen.getScreen()).build().getRoot();
    SceneComponent item = menu.getChildren().get(0);

    ActionBar actionBar = new ActionBar(menu);

    assertEquals(Collections.singletonList(item), actionBar.getItems());
    assertEquals(Collections.emptyList(), actionBar.getOverflowItems());
  }

  public void testAddToItemsOrOverflowItemsItemViewTypeIsActionBarOverflowMenu() {
    NlModel model = model("model.xml", component("menu").unboundedChildren(component("item").viewType(ViewType.ACTION_BAR_OVERFLOW_MENU))).build();
    ScreenFixture screen = surface().screen(model);

    SceneComponent menu = new SyncLayoutlibSceneManager(model, screen.getScreen()).build().getRoot();
    SceneComponent item = menu.getChildren().get(0);

    ActionBar actionBar = new ActionBar(menu);

    assertEquals(Collections.emptyList(), actionBar.getItems());
    assertEquals(Collections.singletonList(item), actionBar.getOverflowItems());
  }
}
