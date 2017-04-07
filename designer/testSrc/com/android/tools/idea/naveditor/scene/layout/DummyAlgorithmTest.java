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
package com.android.tools.idea.naveditor.scene.layout;

import com.android.tools.idea.naveditor.scene.NavSceneManager;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.SyncNlModel;
import com.android.tools.idea.uibuilder.scene.Scene;
import com.android.tools.idea.uibuilder.scene.SceneComponent;

/**
 * Tests for {@link DummyAlgorithm}
 */
public class DummyAlgorithmTest extends LayoutTestCase {
  public void testSimple() throws Exception {
    SyncNlModel model = navModel("nav.xml",
                                 component(NavSceneManager.TAG_NAVIGATION).unboundedChildren(
                                   component(NavSceneManager.TAG_FRAGMENT).id("@id/fragment1"),
                                   component(NavSceneManager.TAG_FRAGMENT).id("@id/fragment2"),
                                   component(NavSceneManager.TAG_FRAGMENT).id("@id/fragment3")
                                     .unboundedChildren(component(NavSceneManager.TAG_ACTION)),
                                   component(NavSceneManager.TAG_FRAGMENT).id("@id/fragment4"),
                                   component(NavSceneManager.TAG_FRAGMENT).id("@id/fragment5"))).build();
    Scene scene = model.getSurface().getScene();
    SceneComponent root = scene.getRoot();
    root.setSize(500, 500, false);
    DummyAlgorithm algorithm = new DummyAlgorithm();
    root.flatten().forEach(algorithm::layout);

    assertEquals(50, scene.getSceneComponent("fragment1").getDrawX());
    assertEquals(50, scene.getSceneComponent("fragment1").getDrawY());
    assertEquals(180, scene.getSceneComponent("fragment2").getDrawX());
    assertEquals(50, scene.getSceneComponent("fragment2").getDrawY());
    assertEquals(310, scene.getSceneComponent("fragment3").getDrawX());
    assertEquals(50, scene.getSceneComponent("fragment3").getDrawY());
    assertEquals(50, scene.getSceneComponent("fragment4").getDrawX());
    assertEquals(180, scene.getSceneComponent("fragment4").getDrawY());
    assertEquals(180, scene.getSceneComponent("fragment5").getDrawX());
    assertEquals(180, scene.getSceneComponent("fragment5").getDrawY());
  }

  public void testSkipOther() throws Exception {
    SyncNlModel model = navModel("nav.xml",
                                 component(NavSceneManager.TAG_NAVIGATION).unboundedChildren(
                                   component(NavSceneManager.TAG_FRAGMENT).id("@id/fragment1"),
                                   component(NavSceneManager.TAG_FRAGMENT).id("@id/fragment2"),
                                   component(NavSceneManager.TAG_FRAGMENT).id("@id/fragment3")
                                     .unboundedChildren(component(NavSceneManager.TAG_ACTION)),
                                   component(NavSceneManager.TAG_FRAGMENT).id("@id/fragment4"),
                                   component(NavSceneManager.TAG_FRAGMENT).id("@id/fragment5"))).build();
    Scene scene = model.getSurface().getScene();
    SceneComponent root = scene.getRoot();
    root.setSize(500, 500, false);
    DummyAlgorithm algorithm = new DummyAlgorithm();
    SceneComponent manual = scene.getSceneComponent("fragment1");
    manual.setPosition(200, 80);
    root.flatten().filter(c -> c != manual).forEach(algorithm::layout);

    assertEquals(200, scene.getSceneComponent("fragment1").getDrawX());
    assertEquals(80, scene.getSceneComponent("fragment1").getDrawY());
    assertEquals(50, scene.getSceneComponent("fragment2").getDrawX());
    assertEquals(50, scene.getSceneComponent("fragment2").getDrawY());
    assertEquals(310, scene.getSceneComponent("fragment3").getDrawX());
    assertEquals(50, scene.getSceneComponent("fragment3").getDrawY());
    assertEquals(50, scene.getSceneComponent("fragment4").getDrawX());
    assertEquals(180, scene.getSceneComponent("fragment4").getDrawY());
    assertEquals(180, scene.getSceneComponent("fragment5").getDrawX());
    assertEquals(180, scene.getSceneComponent("fragment5").getDrawY());
  }
}
